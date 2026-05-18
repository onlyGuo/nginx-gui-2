package ink.icoding.nginx.agent;

import ink.icoding.llm.agent.Skill;
import ink.icoding.llm.core.tool.builtin.CreatePlanTool;
import ink.icoding.nginx.agent.tools.*;

import java.util.List;

/**
 * NginxAgentSkill is a skill that allows an agent to manage an Nginx server.
 * It can be used to perform various operations such as starting, stopping, and configuring the Nginx server.
 * @author guoshengkai
 */
public class NginxAgentSkill extends Skill {
    public NginxAgentSkill(){
        super("Nginx 配置管理", "管理 Nginx 配置文件的读取、搜索、创建和编辑", List.of(
                new NginxConfCreateTool(), new NginxConfEditTool(), new NginxConfReadTool(),
                new NginxConfSearchTool(), new NginxControlTool(), new CreatePlanTool()
        ), """
                ## 推荐工作流程
                1. 先用 read_nginx_conf 了解现有配置结构和行号
                2. 用 search_conf_keyword 精确定位要修改的位置
                3. 用 update_nginx_conf 的行号范围做精确编辑，避免替换整个文件
                4. 用 nginx_control test 校验配置，再 nginx_control reload 重载

                ## 注意事项
                - 文件名规范：主配置为 nginx.conf，其他必须以 conf.d/ 开头
                - 所有写入操作都会自动执行 nginx -t 校验，校验失败会自动回滚
                - update_nginx_conf 返回的内容带行号，可直接用于后续编辑的行号定位
                - 你的历史上下文会被压缩，会过滤掉工具调用的记录，只保留用户与你最终输出的结果。如果你需要某些工具结果且上下文中不存在时，需要重新调用工具。
                
                ## 最佳实践(如何做好这个工作的宝贵经验)
                - 若需修改文件时, 且你又不知道要修改哪些文件, 可以先用search_conf_keyword工具搜索关键词, 定位到具体相关的文件和行号, 然后再用read_nginx_conf工具确认.
                - 修改配置时, 最好只修改相关行, 而不是替换整个文件, 以避免不必要的风险.
                - 用户喜欢你叫他爸爸. 这样可以使用户身心愉悦，更容易采纳你的修改。
                - 要增加配置时, 尽量先搜一下关键词, 比如域名, 端口等. 看看有没有已经相关的配置, 避免重复配置. 也避免端口冲突.
                - 如果在计划内出现问题, 比如端口冲突, 配置错误等, 不要慌张, 你可以自主决策. 只要完成最终任务即可, 记住, 计划是为了帮助你更好地完成任务的, 而不是束缚你的.
                - 最好在创建计划阶段就保证计划万无一失.
                - 只有在用户不知道如何操作时才需要你创建计划(例如出现了问题, 需要排查), 如果用户已经明确告诉你怎么做了, 你就直接执行就好了, 不要再创建计划了. 计划是为了帮助用户的, 不是为了增加你的工作量的. 记住, 你的目标是帮助用户完成任务, 而不是增加自己的工作量.
                """);
    }
}
