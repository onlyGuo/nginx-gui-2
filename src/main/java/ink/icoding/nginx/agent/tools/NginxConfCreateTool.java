package ink.icoding.nginx.agent.tools;


import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.nginx.agent.tools.params.NginxConfCreateToolRequest;
import ink.icoding.nginx.config.NginxClientAutoConfiguration;
import ink.icoding.nginx.core.NginxClient;

/**
 * FileCreateTool is a tool that allows an agent to create a file with specified content at a given path.
 * It takes a FileCreateToolRequest as input, which contains the file path and content, and creates the file accordingly.
 * @author guoshengkai
 */
@ToolInfo(name = "create_nginx_conf", description = "Create a file with specified content at the given path")
public class NginxConfCreateTool implements Tool<NginxConfCreateToolRequest> {

    @Override
    public String execute(NginxConfCreateToolRequest param) {
        try {
            NginxClient nginxClient = NginxClientAutoConfiguration.getNginxClient();
            String fileName = param.getFileName();
            if (!fileName.startsWith("conf.d/")){
                throw new IllegalArgumentException("文件名必须以 conf.d/ 开头，表示 conf.d 目录下的配置文件");
            }
            fileName = fileName.substring("conf.d/".length());
            boolean isSuccess = nginxClient.updateConfD(fileName, param.getContent());
            return "File " + param.getFileName() + (isSuccess ? " created successfully." : " created failed.");
        }catch (Exception e){
            return "Error creating file: " + e.getMessage();
        }

    }
}
