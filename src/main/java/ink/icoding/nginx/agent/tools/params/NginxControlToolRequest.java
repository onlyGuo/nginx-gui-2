package ink.icoding.nginx.agent.tools.params;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NginxControlToolRequest extends ToolParam {

    @Param(description = "操作类型: start(启动), stop(停止), reload(重载), test(仅校验配置), testAndReload(校验并重载), version(查看版本)")
    private String action;

    public NginxControlToolRequest() {
    }

    public NginxControlToolRequest(String action) {
        this.action = action;
    }
}
