package ink.icoding.nginx.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.agent.*;
import ink.icoding.llm.core.entity.ModelType;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolStatus;
import ink.icoding.nginx.agent.NginxAgentSkill;
import ink.icoding.nginx.config.AiChatSession;
import ink.icoding.nginx.config.AiChatSessionRepository;
import ink.icoding.nginx.config.AiConfig;
import ink.icoding.nginx.config.AiConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Long CONFIG_ID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiConfigRepository aiConfigRepository;
    private final AiChatSessionRepository sessionRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<Long, AgentClientSession> sessionCache = new ConcurrentHashMap<>();

    public ChatController(AiConfigRepository aiConfigRepository, AiChatSessionRepository sessionRepository) {
        this.aiConfigRepository = aiConfigRepository;
        this.sessionRepository = sessionRepository;
    }

    @GetMapping("/protocols")
    public ApiResponse<List<Map<String, String>>> getProtocols() {
        List<Map<String, String>> list = new ArrayList<>();
        for (ModelType type : ModelType.values()) {
            list.add(Map.of("value", type.name(), "label", type.name()));
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, String>> getConfig() {
        AiConfig config = loadConfig();
        return ApiResponse.ok(Map.of(
                "baseUrl", config.getBaseUrl() != null ? config.getBaseUrl() : "",
                "apiKey", config.getApiKey() != null ? config.getApiKey() : "",
                "modelName", config.getModelName() != null ? config.getModelName() : "",
                "protocol", config.getProtocol() != null ? config.getProtocol() : "OpenAI"
        ));
    }

    @PutMapping("/config")
    public ApiResponse<Void> updateConfig(@RequestBody Map<String, String> body) {
        AiConfig config = loadConfig();
        if (body.containsKey("baseUrl")) config.setBaseUrl(body.get("baseUrl"));
        if (body.containsKey("apiKey")) config.setApiKey(body.get("apiKey"));
        if (body.containsKey("modelName")) config.setModelName(body.get("modelName"));
        if (body.containsKey("protocol")) config.setProtocol(body.get("protocol"));
        aiConfigRepository.save(config);
        return ApiResponse.ok();
    }

    // ==================== Clear Session ====================

    @PostMapping("/clear")
    public ApiResponse<Void> clearSession(HttpServletRequest request) {
        Long userId = getUserId(request);
        sessionCache.remove(userId);
        sessionRepository.deleteById(userId);
        return ApiResponse.ok();
    }

    // ==================== SSE Chat ====================

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String userMessage = body.get("message");
        Long userId = getUserId(request);
        SseEmitter emitter = new SseEmitter(300_000L);
        executor.execute(() -> {
            try {
                AiConfig config = loadConfig();
                if (config.getBaseUrl() == null || config.getBaseUrl().isBlank() ||
                        config.getApiKey() == null || config.getApiKey().isBlank() ||
                        config.getModelName() == null || config.getModelName().isBlank()) {
                    sendSse(emitter, "error", "AI 配置不完整，请先在设置中配置好 AI 接入信息");
                    emitter.complete();
                    return;
                }
                AgentClient agent = buildAgent(config);
                AgentClientSession session = buildSession(agent, userId);

                AgentSessionResult result = session.command(userMessage);
                AgentSessionResult chained = result.then(new AgentResultHandler() {
                    @Override
                    public void onMessage(String msg) {
                        try { sendSse(emitter, "message", MAPPER.writeValueAsString(msg)); } catch (Exception e) { log.error("Failed to serialize message", e); }
                    }

                    @Override
                    public void onThink(String think) {
                        try { sendSse(emitter, "think", MAPPER.writeValueAsString(think)); } catch (Exception e) { log.error("Failed to serialize think", e); }
                    }

                    @Override
                    public void onTool(ToolDescriptor tool, ToolStatus status) {
                        try {
                            Map<String, Object> data = Map.of(
                                    "name", tool.getName() != null ? tool.getName() : "",
                                    "status", mapToolStatus(status),
                                    "input", tool.getInputParams() != null ? tool.getInputParams() : ""
                            );
                            sendSse(emitter, "tool", MAPPER.writeValueAsString(data));
                        } catch (Exception e) {
                            log.error("Failed to send tool event", e);
                        }
                    }

                    @Override
                    public void onPlanCreated(Plan plan) {
                        try {
                            sendSse(emitter, "plans", MAPPER.writeValueAsString(buildPlanData(plan, -1)));
                        } catch (Exception e) {
                            log.error("Failed to send plan event", e);
                        }
                    }

                    @Override
                    public void onPlanStepStart(Plan plan, int current, int total, String step) {
                        try {
                            sendSse(emitter, "plan_update", MAPPER.writeValueAsString(buildPlanData(plan, current)));
                        } catch (Exception e) {
                            log.error("Failed to send plan step event", e);
                        }
                    }

                    @Override
                    public void onPlanStepComplete(Plan plan, int current, int total, String step, String stepResult) {
                        try {
                            sendSse(emitter, "plan_update", MAPPER.writeValueAsString(buildPlanData(plan, current)));
                        } catch (Exception e) {
                            log.error("Failed to send plan step complete event", e);
                        }
                    }
                }).error(e -> {
                    log.error("Chat error", e);
                    try { sendSse(emitter, "error", MAPPER.writeValueAsString(e.getMessage())); } catch (Exception ignored) {}
                });

                chained.execute();

                saveSession(userId, session);

                sendSse(emitter, "done", "");
                emitter.complete();
            } catch (Exception e) {
                log.error("Chat execution failed", e);
                try { sendSse(emitter, "error", MAPPER.writeValueAsString(e.getMessage())); } catch (Exception ignored) {}
                emitter.complete();
            }
        });

        return emitter;
    }

    // ==================== Helpers ====================

    private AgentClient buildAgent(AiConfig config) {
        AgentClient agent = new AgentClient();
        agent.setName("Nginx AI 助手");
        agent.setDescription("""
                在此场景下, 你是一个专业的 Nginx 配置管理助手, 负责帮助用户操作和管理Nginx.
                用户可以通过自然语言指令让你完成各种Nginx相关的任务, 包括但不限于: 解析和修改配置文件、提供优化建议、排查问题等。
                如果用户给你的任务需要读写文件, 那么你应当拒绝一切非Nginx相关的文件操作请求, 以免误伤用户系统中的其他文件。
                若不涉及到读写文件, 则不受此限制。
                你应当充分利用你内置的技能来完成用户的指令。
                """);

        ModelType modelType = ModelType.valueOf(config.getProtocol());
        LLMModel model = LLMModel.create(
                modelType,
                config.getBaseUrl(),
                config.getModelName(),
                config.getApiKey()
        );
        agent.setModel(model);

        agent.getSkills().add(new NginxAgentSkill());
        return agent;
    }

    private AgentClientSession buildSession(AgentClient agent, Long userId) {
        // Try in-memory cache first
        AgentClientSession cached = sessionCache.get(userId);
        if (cached != null) return cached;

        // Try database
        AiChatSession entity = sessionRepository.findById(userId).orElse(null);
        if (entity != null && entity.getSessionData() != null && !entity.getSessionData().isBlank()) {
            try {
                AgentClientSession session = agent.getSessionFromSerialization(entity.getSessionData());
                sessionCache.put(userId, session);
                return session;
            } catch (Exception e) {
                log.warn("Failed to restore session for user {}, creating new one", userId, e);
            }
        }

        AgentClientSession session = agent.createSession();
        sessionCache.put(userId, session);
        return session;
    }

    private void saveSession(Long userId, AgentClientSession session) {
        try {
            String data = session.serialization();
            AiChatSession entity = sessionRepository.findById(userId).orElseGet(() -> {
                AiChatSession s = new AiChatSession();
                s.setUserId(userId);
                return s;
            });
            entity.setSessionData(data);
            sessionRepository.save(entity);
            sessionCache.put(userId, session);
        } catch (Exception e) {
            log.error("Failed to save session for user {}", userId, e);
        }
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long id) return id;
        throw new IllegalStateException("未获取到用户信息");
    }

    private AiConfig loadConfig() {
        return aiConfigRepository.findById(CONFIG_ID).orElseGet(() -> {
            AiConfig defaults = new AiConfig();
            defaults.setId(CONFIG_ID);
            return aiConfigRepository.save(defaults);
        });
    }

    private String mapToolStatus(ToolStatus status) {
        return switch (status) {
            case PREPARING, CALLING -> "running";
            case COMPLETED -> "done";
        };
    }

    private Map<String, Object> buildPlanData(Plan plan, int currentStep) {
        List<Map<String, String>> items = new ArrayList<>();
        for (int i = 0; i < plan.getSteps().size(); i++) {
            String status;
            if (currentStep < 0) {
                status = "pending";
            } else if (i < currentStep - 1) {
                status = "done";
            } else if (i == currentStep - 1) {
                status = "running";
            } else {
                status = "pending";
            }
            items.add(Map.of("name", plan.getSteps().get(i), "status", status));
        }
        return Map.of(
                "name", plan.getName() != null ? plan.getName() : "执行计划",
                "items", items
        );
    }

    private void sendSse(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IllegalStateException e) {
            log.debug("SSE send failed (emitter already completed): {}", e.getMessage());
        } catch (IOException e) {
            log.debug("SSE send failed (client disconnected): {}", e.getMessage());
        }
    }
}
