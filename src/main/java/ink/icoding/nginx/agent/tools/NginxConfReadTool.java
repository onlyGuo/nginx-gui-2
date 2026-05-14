package ink.icoding.nginx.agent.tools;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.nginx.agent.tools.params.NginxConfReadToolRequest;
import ink.icoding.nginx.config.NginxClientAutoConfiguration;
import ink.icoding.nginx.core.NginxClient;

import java.util.List;

/**
 * NginxConfReadTool is a tool that allows an agent to read the content of Nginx configuration files.
 * It can read the main configuration file (nginx.conf) or specific files in the conf.d directory.
 * If no file name is provided, it lists all configuration files in the conf.d directory.
 * The content is returned with line numbers for easier reference.
 * @author guoshengkai
 */
@ToolInfo(name = "read_nginx_conf", description = "读取 Nginx 配置文件内容，返回带行号的内容。" +
        "传 nginx.conf 读主配置; 传 conf.d/xxx.conf 读指定文件; 不传 fileName 则列出 conf.d 目录下所有配置文件。")
public class NginxConfReadTool implements Tool<NginxConfReadToolRequest> {

    @Override
    public String execute(NginxConfReadToolRequest param) {
        try {
            NginxClient nginxClient = NginxClientAutoConfiguration.getNginxClient();
            if (!nginxClient.isInitialized()) {
                return "Error: Nginx client is not initialized";
            }

            String fileName = param.getFileName();

            // No fileName — list conf.d files
            if (fileName == null || fileName.isBlank() || fileName.endsWith("conf.d/")) {
                List<String> files = nginxClient.listConfD();
                if (files.isEmpty()) {
                    return "conf.d 目录下没有配置文件";
                }
                StringBuilder sb = new StringBuilder();
                sb.append("conf.d 目录下共 ").append(files.size()).append(" 个配置文件:\n");
                for (String f : files) {
                    sb.append("  - conf.d/").append(f).append("\n");
                }
                return sb.toString();
            }

            // Read file content
            String content;
            if ("nginx.conf".equals(fileName.trim())) {
                content = nginxClient.readMainConfig();
            } else if (fileName.startsWith("conf.d/")) {
                content = nginxClient.readConfD(fileName.substring("conf.d/".length()));
            } else {
                return "Error: 文件名必须是 nginx.conf 或 conf.d/xxx.conf 格式";
            }

            return addLineNumbers(content);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    private String addLineNumbers(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%4d | %s", i + 1, lines[i]));
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
