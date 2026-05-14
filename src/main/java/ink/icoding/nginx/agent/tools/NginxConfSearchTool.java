package ink.icoding.nginx.agent.tools;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.nginx.agent.tools.params.NginxConfSearchToolRequest;
import ink.icoding.nginx.config.NginxClientAutoConfiguration;
import ink.icoding.nginx.core.NginxClient;
import ink.icoding.nginx.core.NginxException;

import java.util.ArrayList;
import java.util.List;

/**
 * NginxConfSearchTool is a tool that allows an agent to search for specific keywords in the Nginx configuration files.
 * It takes a keyword as input and returns the lines containing the keyword along with their line numbers.
 * @author guoshengkai
 */
@ToolInfo(name = "search_conf_keyword", description = "通过关键词搜索配置文件中的内容，返回包含关键词的行以及行号。搜索范围包括主配置文件 nginx.conf 和 conf.d 目录下的所有配置文件。")
public class NginxConfSearchTool implements Tool<NginxConfSearchToolRequest> {

    @Override
    public String execute(NginxConfSearchToolRequest param) {
        String keyword = param.getKeyword();
        if (keyword == null || keyword.isBlank()) {
            return "Error: keyword is required";
        }

        NginxClient nginxClient = NginxClientAutoConfiguration.getNginxClient();
        if (!nginxClient.isInitialized()) {
            return "Error: Nginx client is not initialized";
        }

        List<SearchResult> results = new ArrayList<>();

        // Search main config
        try {
            String mainConfig = nginxClient.readMainConfig();
            searchInContent("nginx.conf", mainConfig, keyword, results);
        } catch (NginxException e) {
            // main config not found, skip
        }

        // Search conf.d files
        List<String> confFiles = nginxClient.listConfD();
        for (String filename : confFiles) {
            try {
                String content = nginxClient.readConfD(filename);
                searchInContent("conf.d/" + filename, content, keyword, results);
            } catch (NginxException e) {
                // skip unreadable files
            }
        }

        if (results.isEmpty()) {
            return "No results found for keyword: " + keyword;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size()).append(" match(es) for \"").append(keyword).append("\":\n\n");
        for (SearchResult r : results) {
            sb.append(r.file).append(":").append(r.lineNumber).append("  ").append(r.line).append("\n");
        }
        return sb.toString();
    }

    private void searchInContent(String filename, String content, String keyword, List<SearchResult> results) {
        String[] lines = content.split("\n");
        String lowerKeyword = keyword.toLowerCase();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toLowerCase().contains(lowerKeyword)) {
                results.add(new SearchResult(filename, i + 1, lines[i].trim()));
            }
        }
    }

    private static class SearchResult {
        final String file;
        final int lineNumber;
        final String line;

        SearchResult(String file, int lineNumber, String line) {
            this.file = file;
            this.lineNumber = lineNumber;
            this.line = line;
        }
    }
}
