package ink.icoding.nginx.agent.tools.params;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import lombok.Getter;
import lombok.Setter;

/**
 * NginxConfToolRequest is a request object that contains the necessary information to create a file with specified content at a given path.
 * It includes the file path and the content to be written to the file. This class is used as a parameter for the FileCreateTool.
 * @author guoshengkai
 */
@Getter
@Setter
public class NginxConfToolRequest extends ToolParam {

    @Param(description = "配置文件名, 只含名称不含路径. 如果是主文件(nginx.conf)则直接写 nginx.conf, 如果是 conf.d 目录下的文件则写 conf.d/xxx.conf")
    private String fileName;
    @Param(description = "要写入的内容")
    private String content;
    @Param(required = false, description = "起始行号(1-based, 包含). 与 endLine 配合使用: 两者都有则替换该范围; 只有 startLine 则在该行前插入; 都没有则替换整个文件")
    private Integer startLine;
    @Param(required = false, description = "结束行号(1-based, 包含). 与 startLine 配合使用: 两者都有则替换该范围; 只有 endLine 则在该行后追加; 都没有则替换整个文件")
    private Integer endLine;

    public NginxConfToolRequest() {
    }

    public NginxConfToolRequest(String fileName, String content) {
        this.fileName = fileName;
        this.content = content;
    }

    public NginxConfToolRequest(String fileName, String content, Integer startLine, Integer endLine) {
        this.fileName = fileName;
        this.content = content;
        this.startLine = startLine;
        this.endLine = endLine;
    }
}
