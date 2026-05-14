package ink.icoding.nginx.agent.tools.params;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import lombok.Getter;
import lombok.Setter;

/**
 * NginxConfCreateToolRequest is a request object that contains the necessary information to create a file with specified content at a given path.
 * It includes the file path and the content to be written to the file. This class is used as a parameter for the FileCreateTool.
 * @author guoshengkai
 */
@Getter
@Setter
public class NginxConfCreateToolRequest extends ToolParam {

    @Param(description = "conf.d目录下的文件名, 比如:conf.d/xxx.conf")
    private String fileName;
    @Param(description = "文件内容")
    private String content;

    public NginxConfCreateToolRequest() {
    }

    public NginxConfCreateToolRequest(String fileName, String content) {
        this.fileName = fileName;
        this.content = content;
    }
}
