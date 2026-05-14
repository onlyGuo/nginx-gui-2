package ink.icoding.nginx.agent.tools;


import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.nginx.agent.tools.params.NginxConfToolRequest;
import ink.icoding.nginx.config.NginxClientAutoConfiguration;
import ink.icoding.nginx.core.NginxClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * NginxConfEditTool allows an agent to update Nginx configuration files.
 * Supports full file replacement, line-range replacement, insertion before a line,
 * and appending after a line, controlled by startLine/endLine parameters.
 * @author guoshengkai
 */
@ToolInfo(name = "update_nginx_conf", description = "更新 Nginx 配置文件。支持三种模式：" +
        "1) 指定 startLine+endLine 替换指定行范围; " +
        "2) 只指定 startLine 在该行前插入; " +
        "3) 只指定 endLine 在该行后追加; " +
        "4) 都不指定则替换整个文件。")
public class NginxConfEditTool implements Tool<NginxConfToolRequest> {

    @Override
    public String execute(NginxConfToolRequest param) {
        try {
            NginxClient nginxClient = NginxClientAutoConfiguration.getNginxClient();
            String fileName = param.getFileName();
            boolean isMainConfig = "nginx.conf".equals(fileName);

            if (!isMainConfig && !fileName.startsWith("conf.d/")) {
                throw new IllegalArgumentException("文件名必须以 conf.d/ 开头，表示 conf.d 目录下的配置文件");
            }

            Integer startLine = param.getStartLine();
            Integer endLine = param.getEndLine();

            // No line range specified — full file replacement (original behavior)
            if (startLine == null && endLine == null) {
                return writeFull(nginxClient, fileName, isMainConfig, param.getContent());
            }

            // Read existing content
            String existing = isMainConfig
                    ? nginxClient.readMainConfig()
                    : nginxClient.readConfD(fileName.substring("conf.d/".length()));
            List<String> lines = new ArrayList<>(Arrays.asList(existing.split("\n", -1)));

            // Validate line numbers
            if (startLine != null && startLine < 1) {
                return "Error: startLine must be >= 1";
            }
            if (endLine != null && endLine < 1) {
                return "Error: endLine must be >= 1";
            }
            if (startLine != null && endLine != null && startLine > endLine) {
                return "Error: startLine must be <= endLine";
            }

            List<String> newLines = Arrays.asList(param.getContent().split("\n", -1));
            List<String> result;

            if (startLine != null && endLine != null) {
                // Replace range [startLine, endLine]
                int start = Math.min(startLine - 1, lines.size());
                int end = Math.min(endLine, lines.size());
                if (start >= end) {
                    return "Error: startLine (" + startLine + ") is beyond file length (" + lines.size() + " lines)";
                }
                result = new ArrayList<>(lines.subList(0, start));
                result.addAll(newLines);
                result.addAll(lines.subList(end, lines.size()));
            } else if (startLine != null) {
                // Insert before startLine
                int pos = Math.min(startLine - 1, lines.size());
                result = new ArrayList<>(lines.subList(0, pos));
                result.addAll(newLines);
                result.addAll(lines.subList(pos, lines.size()));
            } else {
                // Append after endLine
                int pos = Math.min(endLine, lines.size());
                result = new ArrayList<>(lines.subList(0, pos));
                result.addAll(newLines);
                result.addAll(lines.subList(pos, lines.size()));
            }

            String merged = String.join("\n", result);
            return writeFull(nginxClient, fileName, isMainConfig, merged);
        } catch (Exception e) {
            return "Error updating file: " + e.getMessage();
        }
    }

    private String writeFull(NginxClient nginxClient, String fileName, boolean isMainConfig, String content) {
        boolean isSuccess;
        if (isMainConfig) {
            nginxClient.updateMainConfig(content);
            isSuccess = true;
        } else {
            String name = fileName.substring("conf.d/".length());
            isSuccess = nginxClient.updateConfD(name, content);
        }
        return "File " + fileName + (isSuccess ? " updated successfully." : " update failed.");
    }
}
