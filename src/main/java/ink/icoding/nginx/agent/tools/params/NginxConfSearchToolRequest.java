package ink.icoding.nginx.agent.tools.params;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import lombok.Getter;
import lombok.Setter;

/**
 * NginxConfSearchToolRequest is a request object that contains the necessary information to search for specific keywords in the Nginx configuration files.
 * It includes the keyword to be searched. This class is used as a parameter for the NginxConfSearchTool.
 * @author guoshengkai
 */
@Getter
@Setter
public class NginxConfSearchToolRequest extends ToolParam {

    @Param(description = "要搜索的关键词")
    private String keyword;

    public NginxConfSearchToolRequest() {
    }

    public NginxConfSearchToolRequest(String keyword) {
        this.keyword = keyword;
    }

}
