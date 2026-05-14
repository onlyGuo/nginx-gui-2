package ink.icoding.nginx.agent.tools.params;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import lombok.Getter;
import lombok.Setter;

/**
 * NginxConfReadToolRequest is a request object that contains the necessary information to read the content of a specified Nginx configuration file.
 * It includes the file name to be read. This class is used as a parameter for the NginxConfReadTool.
 * @author guoshengkai
 */
@Getter
@Setter
public class NginxConfReadToolRequest extends ToolParam {

    @Param(description = "配置文件名. nginx.conf 读主配置; conf.d/xxx.conf 读指定文件; 不填则列出 conf.d 目录下所有文件")
    private String fileName;

    public NginxConfReadToolRequest() {
    }

    public NginxConfReadToolRequest(String fileName) {
        this.fileName = fileName;
    }
}
