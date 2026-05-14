package ink.icoding.nginx.agent.tools;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.nginx.agent.tools.params.NginxControlToolRequest;
import ink.icoding.nginx.config.NginxClientAutoConfiguration;
import ink.icoding.nginx.core.NginxClient;

@ToolInfo(name = "nginx_control", description = "控制 Nginx 服务：启动、停止、重载配置、校验配置、查看版本。" +
        "修改配置文件后建议先用 test 校验，再用 reload 重载。")
public class NginxControlTool implements Tool<NginxControlToolRequest> {

    @Override
    public String execute(NginxControlToolRequest param) {
        String action = param.getAction();
        if (action == null || action.isBlank()) {
            return "Error: action is required. Available: start, stop, reload, test, testAndReload, version";
        }

        try {
            NginxClient nginxClient = NginxClientAutoConfiguration.getNginxClient();
            if (!nginxClient.isInitialized()) {
                return "Error: Nginx client is not initialized";
            }

            return switch (action) {
                case "start" -> {
                    nginxClient.start();
                    yield "Nginx started successfully.";
                }
                case "stop" -> {
                    nginxClient.stop();
                    yield "Nginx stopped successfully.";
                }
                case "reload" -> {
                    nginxClient.reload();
                    yield "Nginx reloaded successfully.";
                }
                case "test" -> {
                    nginxClient.validateConfig();
                    yield "Configuration test passed.";
                }
                case "testAndReload" -> {
                    nginxClient.testAndReload();
                    yield "Configuration test passed and Nginx reloaded.";
                }
                case "version" -> nginxClient.version();
                default -> "Error: unknown action '" + action + "'. Available: start, stop, reload, test, testAndReload, version";
            };
        } catch (Exception e) {
            return "Error executing [" + action + "]: " + e.getMessage();
        }
    }
}
