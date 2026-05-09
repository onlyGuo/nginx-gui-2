package ink.icoding.nginx.web;

import ink.icoding.nginx.config.PathConfig;
import ink.icoding.nginx.config.PathConfigRepository;
import ink.icoding.nginx.core.NginxClient;
import ink.icoding.nginx.utils.CommandUtil;
import ink.icoding.nginx.utils.CommandUtil.CommandResult;
import ink.icoding.nginx.utils.CommandUtil.CommandStream;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private static final Long CONFIG_ID = 1L;

    private final NginxClient client;
    private final PathConfigRepository repository;

    // 缓存：避免前端每次刷新都重新采集
    private volatile Map<String, Object> cachedStatus;
    private volatile long statusCacheTime = 0;
    private static final long CACHE_TTL_MS = 5000; // 5秒缓存

    // 图表历史数据（滚动窗口，保留最近 30 个点）
    private static final int HISTORY_SIZE = 30;
    private final List<Map<String, Object>> history = new java.util.concurrent.CopyOnWriteArrayList<>();

    // 日志缓存
    private final Map<String, List<Map<String, String>>> logCache = new ConcurrentHashMap<>();
    private final Map<String, Long> logCacheTime = new ConcurrentHashMap<>();
    private static final long LOG_CACHE_TTL_MS = 3000;

    public DashboardController(PathConfigRepository repository, NginxClient client) {
        this.repository = repository;
        this.client = client;
    }

    @PreDestroy
    public void destroy() {
        sshStreams.values().forEach(CommandStream::stop);
        sshStreams.clear();
        sseScheduler.shutdownNow();
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        long now = System.currentTimeMillis();
        if (cachedStatus != null && (now - statusCacheTime) < CACHE_TTL_MS) {
            return ApiResponse.ok(cachedStatus);
        }

        Map<String, Object> data = new LinkedHashMap<>();

        // Nginx 基本信息
        PathConfig config = loadConfig();
        String nginxBin = config.getNginxBin();
        String nginxConf = config.getNginxConf();

        // 版本
        String version = "";
        try {
            version = client.version();
            version = version.replace("nginx version: ", "").trim();
        } catch (Exception e) {
            version = "未知";
        }
        data.put("version", version);

        // 进程信息
        Map<String, Object> process = getNginxProcess(nginxBin);
        data.put("running", process.get("running"));
        data.put("pid", process.get("pid"));
        data.put("uptime", process.get("uptime"));
        data.put("workerCount", process.get("workerCount"));

        // 系统指标
        double cpu = getCpuUsage();
        Map<String, Object> mem = getMemoryUsage();
        data.put("cpu", cpu);
        data.put("memory", mem);
        data.put("disk", getDiskUsage());
        data.put("configPath", nginxConf);
        int conns = getActiveConnections(nginxBin);
        data.put("activeConnections", conns);

        // 追加到历史数据
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("time", java.time.LocalTime.now().withNano(0).toString());
        point.put("cpu", cpu);
        point.put("mem", mem.getOrDefault("percent", 0));
        point.put("conn", conns);
        history.add(point);
        while (history.size() > HISTORY_SIZE) {
            history.remove(0);
        }

        data.put("history", history);

        cachedStatus = data;
        statusCacheTime = now;
        return ApiResponse.ok(data);
    }

    @GetMapping("/logs")
    public ApiResponse<List<Map<String, String>>> logs(@RequestParam(defaultValue = "access") String type,
                                                        @RequestParam(defaultValue = "100") int lines) {
        String logPath = getLogPath(type);
        if (logPath == null || logPath.isBlank()) {
            return ApiResponse.ok(List.of());
        }

        long now = System.currentTimeMillis();
        String cacheKey = type + ":" + lines;
        Long cached = logCacheTime.get(cacheKey);
        if (cached != null && (now - cached) < LOG_CACHE_TTL_MS) {
            List<Map<String, String>> cachedLogs = logCache.get(cacheKey);
            if (cachedLogs != null) return ApiResponse.ok(cachedLogs);
        }

        List<Map<String, String>> result = CommandUtil.isSshEnabled() && !CommandUtil.isLocalNginx()
                ? readLogsViaSsh(logPath, lines)
                : readLogsLocal(logPath, lines);

        logCache.put(cacheKey, result);
        logCacheTime.put(cacheKey, now);
        return ApiResponse.ok(result);
    }

    private List<Map<String, String>> readLogsLocal(String logPath, int lines) {
        List<Map<String, String>> result = new ArrayList<>();
        Path path = Path.of(logPath);
        if (!Files.exists(path)) return result;
        try {
            List<String> allLines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int start = Math.max(0, allLines.size() - lines);
            for (int i = start; i < allLines.size(); i++) {
                String line = allLines.get(i).trim();
                if (line.isEmpty()) continue;
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("text", line);
                result.add(entry);
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    private List<Map<String, String>> readLogsViaSsh(String logPath, int lines) {
        List<Map<String, String>> result = new ArrayList<>();
        CommandResult r = CommandUtil.execute("tail -n " + lines + " " + logPath);
        if (!r.isSuccess()) return result;
        for (String line : r.getStdout().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("text", trimmed);
            result.add(entry);
        }
        return result;
    }

    // ==================== SSE 流式日志 ====================

    private final ScheduledExecutorService sseScheduler = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r, "sse-log-tail");
        t.setDaemon(true);
        return t;
    });

    // SSH 流式命令跟踪，用于 SSE 关闭时清理
    private final ConcurrentHashMap<String, CommandStream> sshStreams = new ConcurrentHashMap<>();

    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@RequestParam(defaultValue = "access") String type) {
        SseEmitter emitter = new SseEmitter(0L);
        String logPath = getLogPath(type);

        if (logPath == null || logPath.isBlank()) {
            completeSseQuietly(emitter);
            return emitter;
        }

        if (CommandUtil.isSshEnabled() && !CommandUtil.isLocalNginx()) {
            streamLogsViaSsh(emitter, logPath, type);
        } else {
            streamLogsLocal(emitter, logPath, type);
        }
        return emitter;
    }

    // ==================== 本地 SSE 流式日志 ====================

    private final ConcurrentHashMap<String, Long> filePositions = new ConcurrentHashMap<>();

    private void streamLogsLocal(SseEmitter emitter, String logPath, String type) {
        Path path = Path.of(logPath);
        if (!Files.exists(path)) {
            completeSseQuietly(emitter);
            return;
        }

        try {
            List<String> allLines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int start = Math.max(0, allLines.size() - 100);
            emitter.send(SseEmitter.event().name("init").data(allLines.subList(start, allLines.size())));
            long pos = Files.size(path);
            String key = type + ":" + emitter.hashCode();
            filePositions.put(key, pos);

            ScheduledFuture<?> task = sseScheduler.scheduleAtFixedRate(
                    () -> tailLocal(emitter, path, key),
                    500, 500, TimeUnit.MILLISECONDS);

            Runnable cleanup = () -> {
                task.cancel(false);
                filePositions.remove(key);
            };
            emitter.onCompletion(cleanup);
            emitter.onTimeout(cleanup);
            emitter.onError(e -> cleanup.run());
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void tailLocal(SseEmitter emitter, Path path, String key) {
        try {
            if (!Files.exists(path)) return;
            long size = Files.size(path);
            long lastPos = filePositions.getOrDefault(key, size);
            if (size > lastPos) {
                try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                    raf.seek(lastPos);
                    List<String> newLines = new ArrayList<>();
                    String line;
                    while ((line = raf.readLine()) != null) {
                        newLines.add(new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
                    }
                    if (!newLines.isEmpty()) {
                        emitter.send(SseEmitter.event().name("log").data(newLines));
                    }
                    filePositions.put(key, raf.getFilePointer());
                }
            } else if (size < lastPos) {
                filePositions.put(key, 0L);
            }
        } catch (Exception e) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    // ==================== SSH SSE 流式日志 ====================

    private void streamLogsViaSsh(SseEmitter emitter, String logPath, String type) {
        // 初始发送最近 100 行
        List<Map<String, String>> initial = readLogsViaSsh(logPath, 100);
        List<String> initialLines = initial.stream()
                .map(m -> m.get("text"))
                .toList();
        try {
            emitter.send(SseEmitter.event().name("init").data(initialLines));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return;
        }

        // 启动 tail -f 流式命令
        String cmd = "tail -f -n 0 " + logPath;
        List<String> lineBuffer = new CopyOnWriteArrayList<>();
        CommandStream sshStream;
        try {
            sshStream = CommandUtil.stream(cmd, lineBuffer::add);
        } catch (Exception e) {
            // tail -f 启动失败（文件不存在等），仅发送初始数据
            emitter.complete();
            return;
        }

        String streamKey = type + ":" + emitter.hashCode();
        sshStreams.put(streamKey, sshStream);

        Runnable cleanup = () -> {
            sshStreams.remove(streamKey);
            sshStream.stop();
        };

        // 定时将缓冲区中的新行发送给客户端
        AtomicBoolean sending = new AtomicBoolean(true);
        ScheduledFuture<?> task = sseScheduler.scheduleAtFixedRate(() -> {
            if (!sending.get()) return;
            try {
                if (!sshStream.isRunning() && lineBuffer.isEmpty()) {
                    cleanup.run();
                    emitter.complete();
                    return;
                }
                if (!lineBuffer.isEmpty()) {
                    List<String> batch = new ArrayList<>(lineBuffer);
                    lineBuffer.clear();
                    emitter.send(SseEmitter.event().name("log").data(batch));
                }
            } catch (Exception e) {
                sending.set(false);
                cleanup.run();
            }
        }, 500, 500, TimeUnit.MILLISECONDS);

        emitter.onCompletion(() -> {
            sending.set(false);
            task.cancel(false);
            cleanup.run();
        });
        emitter.onTimeout(() -> {
            sending.set(false);
            task.cancel(false);
            cleanup.run();
        });
        emitter.onError(e -> {
            sending.set(false);
            task.cancel(false);
            cleanup.run();
        });
    }

    private void completeSseQuietly(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("log").data("[]"));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }

    // ==================== 系统指标采集 ====================

    private double getCpuUsage() {
        // macOS: 使用 top 采集 CPU 使用率
        CommandResult r = CommandUtil.execute("/bin/sh", "-c",
                "top -l 1 -n 0 | grep 'CPU usage'");
        if (r.isSuccess() && !r.getStdout().isEmpty()) {
            // 输出格式: "CPU usage: 12.5% user, 5.2% sys, 82.3% idle"
            String out = r.getStdout();
            try {
                // 提取 idle 百分比
                double idle = parsePercent(out, "idle");
                return Math.round((100.0 - idle) * 10.0) / 10.0;
            } catch (Exception e) {
                // fallback
            }
        }
        // Linux fallback
        CommandResult r2 = CommandUtil.execute("/bin/sh", "-c",
                "grep 'cpu ' /proc/stat | awk '{usage=($2+$4)*100/($2+$4+$5)} END {print usage}'");
        if (r2.isSuccess() && !r2.getStdout().isBlank()) {
            try {
                return Double.parseDouble(r2.getStdout().trim());
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private Map<String, Object> getMemoryUsage() {
        Map<String, Object> mem = new LinkedHashMap<>();
        // macOS
        CommandResult totalR = CommandUtil.execute("/bin/sh", "-c", "sysctl -n hw.memsize");
        if (totalR.isSuccess() && !totalR.getStdout().isBlank()) {
            try {
                long totalBytes = Long.parseLong(totalR.getStdout().trim());
                mem.put("total", formatBytes(totalBytes));

                // 使用 vm_stat 获取页面大小和各类页面数
                CommandResult vmR = CommandUtil.execute("/bin/sh", "-c", "vm_stat");
                if (vmR.isSuccess()) {
                    String vmOut = vmR.getStdout();
                    long pageSize = 16384L; // 默认 16KB
                    if (vmOut.contains("page size of")) {
                        try {
                            String ps = vmOut.substring(vmOut.indexOf("page size of") + 13);
                            ps = ps.substring(0, ps.indexOf(" bytes")).trim();
                            pageSize = Long.parseLong(ps);
                        } catch (Exception ignored) {
                        }
                    }
                    long freePages = parseVmStatPages(vmOut, "Pages free");
                    long activePages = parseVmStatPages(vmOut, "Pages active");
                    long inactivePages = parseVmStatPages(vmOut, "Pages inactive");
                    long wiredPages = parseVmStatPages(vmOut, "Pages wired down");

                    long usedBytes = (activePages + wiredPages) * pageSize;
                    long freeBytes = (freePages + inactivePages) * pageSize;
                    double percent = Math.round(usedBytes * 1000.0 / totalBytes) / 10.0;

                    mem.put("used", formatBytes(usedBytes));
                    mem.put("free", formatBytes(freeBytes));
                    mem.put("percent", percent);
                }
                return mem;
            } catch (Exception ignored) {
            }
        }
        // Linux fallback
        CommandResult r = CommandUtil.execute("/bin/sh", "-c", "free -b | grep Mem");
        if (r.isSuccess() && !r.getStdout().isBlank()) {
            String[] parts = r.getStdout().trim().split("\\s+");
            if (parts.length >= 3) {
                try {
                    long total = Long.parseLong(parts[1]);
                    long used = Long.parseLong(parts[2]);
                    mem.put("total", formatBytes(total));
                    mem.put("used", formatBytes(used));
                    mem.put("free", formatBytes(total - used));
                    mem.put("percent", Math.round(used * 1000.0 / total) / 10.0);
                } catch (Exception ignored) {
                }
            }
        }
        return mem;
    }

    private List<Map<String, Object>> getDiskUsage() {
        List<Map<String, Object>> disks = new ArrayList<>();
        CommandResult r = CommandUtil.execute("/bin/sh", "-c", "df -h | grep -E '^/dev/'");
        if (r.isSuccess()) {
            for (String line : r.getStdout().split("\n")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 6) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("device", parts[0]);
                    d.put("size", parts[1]);
                    d.put("used", parts[2]);
                    d.put("avail", parts[3]);
                    String percentStr = parts[4].replace("%", "");
                    try {
                        d.put("percent", Integer.parseInt(percentStr));
                    } catch (Exception e) {
                        d.put("percent", 0);
                    }
                    d.put("mount", parts[parts.length - 1]);
                    disks.add(d);
                }
            }
        }
        return disks;
    }

    private Map<String, Object> getNginxProcess(String nginxBin) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", false);
        result.put("pid", "-");
        result.put("uptime", "-");
        result.put("workerCount", 0);

        // 查找 nginx master 进程
        CommandResult r = CommandUtil.execute(true, "/bin/sh", "-c",
                "ps aux | grep '[n]ginx: master' | head -1");
        if (r.isSuccess() && !r.getStdout().isBlank()) {
            result.put("running", true);
            String line = r.getStdout().trim();
            String[] parts = line.split("\\s+");
            if (parts.length >= 11) {
                result.put("pid", parts[1]);
                // etime 格式: "  1-02:30:45" 或 "02:30:45"
                result.put("uptime", parts[9]); // START 列
            }
        }

        // 获取更精确的 uptime（通过 ps -o etime）
        if ((Boolean) result.get("running")) {
            String pid = (String) result.get("pid");
            CommandResult uptimeR = CommandUtil.execute("/bin/sh", "-c",
                    "ps -o etime= -p " + pid);
            if (uptimeR.isSuccess() && !uptimeR.getStdout().isBlank()) {
                result.put("uptime", uptimeR.getStdout().trim());
            }
        }

        // Worker 进程数
        CommandResult wc = CommandUtil.execute("/bin/sh", "-c",
                "ps aux | grep '[n]ginx: worker' | wc -l");
        if (wc.isSuccess() && !wc.getStdout().isBlank()) {
            try {
                result.put("workerCount", Integer.parseInt(wc.getStdout().trim()));
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private int getActiveConnections(String nginxBin) {
        // 尝试通过 lsof 统计 nginx 的 ESTABLISHED 连接数
        CommandResult r = CommandUtil.execute("/bin/sh", "-c",
                "lsof -i -nP 2>/dev/null | grep nginx | grep ESTABLISHED | wc -l");
        if (r.isSuccess() && !r.getStdout().isBlank()) {
            try {
                return Integer.parseInt(r.getStdout().trim());
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    // ==================== 日志路径 ====================

    private String getLogPath(String type) {
        // 从 nginx.conf 解析日志路径
        try {
            String conf = client.readMainConfig();
            String directive = type.equals("error") ? "error_log" : "access_log";
            // 简单解析: 查找 "error_log /path/to/file" 或 "access_log /path/to/file"
            for (String line : conf.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith(directive) && !trimmed.contains("syslog")) {
                    // 提取路径部分
                    String rest = trimmed.substring(directive.length()).trim();
                    // 去掉末尾分号
                    if (rest.endsWith(";")) rest = rest.substring(0, rest.length() - 1).trim();
                    // 去掉可能的 log_format 名称 (access_log 格式)
                    String[] parts = rest.split("\\s+");
                    for (String p : parts) {
                        if (p.startsWith("/") && !p.equals("off")) {
                            return p;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // 默认路径
        return type.equals("error") ? "/var/log/nginx/error.log" : "/var/log/nginx/access.log";
    }

    // ==================== 工具方法 ====================

    private PathConfig loadConfig() {
        return repository.findById(CONFIG_ID).orElseGet(() -> {
            PathConfig defaults = new PathConfig();
            defaults.setId(CONFIG_ID);
            return repository.save(defaults);
        });
    }

    private double parsePercent(String text, String keyword) {
        int idx = text.indexOf(keyword);
        if (idx < 0) throw new IllegalArgumentException("keyword not found");
        // 向前找百分比数字
        String before = text.substring(0, idx).trim();
        int lastSpace = before.lastIndexOf(' ');
        if (lastSpace < 0) throw new IllegalArgumentException("no percent");
        String numStr = before.substring(lastSpace + 1).replace("%", "").trim();
        return Double.parseDouble(numStr);
    }

    private long parseVmStatPages(String vmOut, String label) {
        for (String line : vmOut.split("\n")) {
            if (line.contains(label)) {
                String num = line.replaceAll("[^0-9.]", "").trim();
                if(num.endsWith(".")){
                    num = num.substring(0, num.length() - 1);
                }
                if (!num.isEmpty()) {
                    return Long.parseLong(num);
                }
            }
        }
        return 0;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        return String.format("%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
    }
}
