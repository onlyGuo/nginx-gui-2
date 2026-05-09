package ink.icoding.nginx.web;

import ink.icoding.nginx.core.NginxException;
import ink.icoding.nginx.utils.CommandResult;
import ink.icoding.nginx.utils.CommandUtil;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/firewall")
public class FirewallController {

    private static final Pattern LS_DATE_PATTERN = Pattern.compile(
            "[A-Z][a-z]{2}\\s+\\d{1,2}\\s+(\\d{2}:\\d{2}|\\d{4})\\s+");

    private volatile String cachedTool = null;

    // ==================== Status ====================

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        String tool = detectTool();
        boolean enabled = isEnabled(tool);
        String version = getVersion(tool);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", tool);
        result.put("enabled", enabled);
        result.put("version", version);
        return ApiResponse.ok(result);
    }

    // ==================== Toggle ====================

    @PutMapping("/toggle")
    public ApiResponse<Void> toggle(@RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        String tool = detectTool();
        switch (tool) {
            case "firewalld" -> {
                CommandResult r = CommandUtil.execute("systemctl " + (enabled ? "start" : "stop") + " firewalld");
                if (!r.isSuccess()) throw new NginxException("切换防火墙状态失败: " + r.getStderr());
            }
            case "ufw" -> {
                String confirm = enabled ? "y" : "y";
                CommandResult r = CommandUtil.execute("echo " + confirm + " | ufw " + (enabled ? "enable" : "disable"));
                if (!r.isSuccess()) throw new NginxException("切换防火墙状态失败: " + r.getStderr());
            }
            case "iptables" -> {
                String policy = enabled ? "DROP" : "ACCEPT";
                CommandResult r = CommandUtil.execute("iptables -P INPUT " + policy);
                if (!r.isSuccess()) throw new NginxException("切换防火墙状态失败: " + r.getStderr());
                CommandUtil.execute("iptables -P FORWARD " + policy);
            }
            case "nftables" -> {
                // nftables 没有简单的 enable/disable，通过 flush ruleset 模拟
                if (!enabled) {
                    CommandUtil.execute("nft flush ruleset");
                }
            }
            case "pfctl" -> {
                String flag = enabled ? "-e" : "-d";
                CommandResult r = CommandUtil.execute("pfctl " + flag);
                if (!r.isSuccess()) throw new NginxException("切换防火墙状态失败: " + r.getStderr());
            }
            default -> throw new NginxException("不支持的防火墙工具: " + tool);
        }
        return ApiResponse.ok();
    }

    // ==================== List Rules ====================

    @GetMapping("/rules")
    public ApiResponse<List<Map<String, Object>>> rules() {
        String tool = detectTool();
        List<Map<String, Object>> rules = listRules(tool);
        return ApiResponse.ok(rules);
    }

    // ==================== Add Rule ====================

    @PostMapping("/rules")
    public ApiResponse<Void> addRule(@RequestBody Map<String, Object> body) {
        String tool = detectTool();
        String port = String.valueOf(body.get("port"));
        String protocol = body.get("protocol") != null ? String.valueOf(body.get("protocol")) : "tcp";
        String action = body.get("action") != null ? String.valueOf(body.get("action")) : "allow";
        String source = body.get("source") != null ? String.valueOf(body.get("source")) : "";

        String cmd = buildAddCommand(tool, port, protocol, action, source);
        CommandResult r = CommandUtil.execute(cmd);
        if (!r.isSuccess()) {
            throw new NginxException("添加规则失败: " + r.getStderr() + " " + r.getStdout());
        }
        reload(tool);
        return ApiResponse.ok();
    }

    // ==================== Delete Rule ====================

    @DeleteMapping("/rules")
    public ApiResponse<Void> deleteRule(@RequestParam String id,
                                        @RequestParam(defaultValue = "tcp") String protocol) {
        String tool = detectTool();
        String cmd = buildDeleteCommand(tool, id, protocol);
        CommandResult r = CommandUtil.execute(cmd);
        if (!r.isSuccess()) {
            throw new NginxException("删除规则失败: " + r.getStderr() + " " + r.getStdout());
        }
        reload(tool);
        return ApiResponse.ok();
    }

    // ==================== Tool Detection ====================

    private String detectTool() {
        if (cachedTool != null) return cachedTool;
        String[] tools = {"firewall-cmd", "iptables", "nft", "ufw", "pfctl"};
        for (String tool : tools) {
            CommandResult r = CommandUtil.execute("which " + tool);
            if (r.isSuccess() && !r.getStdout().trim().isEmpty()) {
                cachedTool = switch (tool) {
                    case "firewall-cmd" -> "firewalld";
                    default -> tool;
                };
                return cachedTool;
            }
        }
        throw new NginxException("未检测到防火墙工具 (firewalld/iptables/nftables/ufw/pfctl)");
    }

    private boolean isEnabled(String tool) {
        CommandResult r = switch (tool) {
            case "firewalld" -> CommandUtil.execute("firewall-cmd --state");
            case "iptables" -> {
                CommandResult lr = CommandUtil.execute("iptables -L INPUT -n");
                yield lr;
            }
            case "nftables" -> CommandUtil.execute("nft list ruleset");
            case "ufw" -> CommandUtil.execute("ufw status");
            case "pfctl" -> CommandUtil.execute("pfctl -s info");
            default -> CommandResult.error("unknown");
        };
        String output = r.getStdout() + r.getStderr();
        return switch (tool) {
            case "firewalld" -> output.contains("running");
            case "iptables" -> {
                // 检查 INPUT policy 是否为 DROP
                CommandResult pr = CommandUtil.execute("iptables -L INPUT -n | head -1");
                yield pr.getStdout().contains("DROP");
            }
            case "nftables" -> output.contains("table") || output.contains("chain");
            case "ufw" -> output.contains("Status: active");
            case "pfctl" -> output.contains("Status: Active");
            default -> false;
        };
    }

    private String getVersion(String tool) {
        CommandResult r = switch (tool) {
            case "firewalld" -> CommandUtil.execute("firewall-cmd --version");
            case "iptables" -> CommandUtil.execute("iptables --version");
            case "nftables" -> CommandUtil.execute("nft --version");
            case "ufw" -> CommandUtil.execute("ufw version");
            case "pfctl" -> CommandUtil.execute("pfctl -v");
            default -> CommandResult.error("unknown");
        };
        String output = (r.getStdout() + " " + r.getStderr()).trim();
        // 提取第一行
        int nl = output.indexOf('\n');
        return nl > 0 ? output.substring(0, nl).trim() : output;
    }

    // ==================== List Rules ====================

    private List<Map<String, Object>> listRules(String tool) {
        return switch (tool) {
            case "firewalld" -> listFirewalldRules();
            case "iptables" -> listIptablesRules();
            case "nftables" -> listNftablesRules();
            case "ufw" -> listUfwRules();
            case "pfctl" -> listPfctlRules();
            default -> List.of();
        };
    }

    private List<Map<String, Object>> listFirewalldRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        // 永久规则
        CommandResult r = CommandUtil.execute("firewall-cmd --permanent --list-all");
        if (!r.isSuccess()) return rules;
        String output = r.getStdout();
        // 解析 ports 行
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.startsWith("ports:")) {
                String portsStr = line.substring(6).trim();
                if (!portsStr.isEmpty()) {
                    for (String portEntry : portsStr.split("\\s+")) {
                        // 格式: 80/tcp
                        String[] parts = portEntry.split("/");
                        Map<String, Object> rule = new LinkedHashMap<>();
                        rule.put("id", portEntry);
                        rule.put("port", parts[0]);
                        rule.put("protocol", parts.length > 1 ? parts[1] : "tcp");
                        rule.put("action", "allow");
                        rule.put("source", "");
                        rules.add(rule);
                    }
                }
            }
        }
        return rules;
    }

    private List<Map<String, Object>> listIptablesRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        CommandResult r = CommandUtil.execute("iptables -L INPUT -n --line-numbers");
        if (!r.isSuccess()) return rules;
        // 跳过前两行（Chain INPUT ... 和 表头）
        String[] lines = r.getStdout().split("\n");
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            // 格式: num  target  prot  opt  source  destination  [extra]
            String[] parts = line.split("\\s+");
            if (parts.length < 4) continue;
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("id", parts[0]);
            rule.put("action", parts[1].toLowerCase());
            rule.put("protocol", parts[2].equals("all") ? "all" : parts[2]);
            rule.put("source", parts[3]);
            // 尝试提取端口
            String port = extractIptablesPort(line);
            rule.put("port", port);
            rules.add(rule);
        }
        return rules;
    }

    private String extractIptablesPort(String line) {
        // 匹配 dpt:80 或 dpts:80:443
        Matcher m = Pattern.compile("dpts?:(\\d[^\\s]*)").matcher(line);
        if (m.find()) return m.group(1);
        return "";
    }

    private List<Map<String, Object>> listNftablesRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        CommandResult r = CommandUtil.execute("nft list ruleset");
        if (!r.isSuccess()) return rules;
        int idx = 0;
        for (String line : r.getStdout().split("\n")) {
            line = line.trim();
            if (line.contains("accept") || line.contains("drop") || line.contains("reject")) {
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("id", String.valueOf(++idx));
                rule.put("action", line.contains("accept") ? "accept" : line.contains("drop") ? "drop" : "reject");
                // 提取端口
                Matcher m = Pattern.compile("dport\\s+(\\d+)").matcher(line);
                rule.put("port", m.find() ? m.group(1) : "");
                // 提取协议
                Matcher pm = Pattern.compile("(tcp|udp)").matcher(line);
                rule.put("protocol", pm.find() ? pm.group(1) : "all");
                rule.put("source", "");
                rules.add(rule);
            }
        }
        return rules;
    }

    private List<Map<String, Object>> listUfwRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        CommandResult r = CommandUtil.execute("ufw status numbered");
        if (!r.isSuccess()) return rules;
        for (String line : r.getStdout().split("\n")) {
            line = line.trim();
            // 格式: [ 1] 80/tcp  ALLOW IN  Anywhere
            Matcher m = Pattern.compile("\\[\\s*(\\d+)\\]\\s+(\\S+)\\s+(ALLOW|DENY|REJECT)\\s+(\\S+).*").matcher(line);
            if (m.find()) {
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("id", m.group(1));
                String portProto = m.group(2);
                String[] pp = portProto.split("/");
                rule.put("port", pp[0]);
                rule.put("protocol", pp.length > 1 ? pp[1] : "all");
                rule.put("action", m.group(3).toLowerCase());
                rule.put("source", m.group(4));
                rules.add(rule);
            }
        }
        return rules;
    }

    private List<Map<String, Object>> listPfctlRules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        CommandResult r = CommandUtil.execute("pfctl -sr");
        if (!r.isSuccess()) return rules;
        int idx = 0;
        for (String line : r.getStdout().split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("id", String.valueOf(++idx));
            rule.put("action", line.contains("pass") ? "pass" : "block");
            Matcher pm = Pattern.compile("proto\\s+(tcp|udp)").matcher(line);
            rule.put("protocol", pm.find() ? pm.group(1) : "all");
            Matcher dpm = Pattern.compile("port\\s+(\\d+)").matcher(line);
            rule.put("port", dpm.find() ? dpm.group(1) : "");
            rule.put("source", "");
            rules.add(rule);
        }
        return rules;
    }

    // ==================== Build Commands ====================

    private String buildAddCommand(String tool, String port, String protocol, String action, String source) {
        return switch (tool) {
            case "firewalld" -> "firewall-cmd --permanent --add-port=" + port + "/" + protocol;
            case "iptables" -> {
                String src = (source != null && !source.isEmpty()) ? " -s " + source : "";
                yield "iptables -A INPUT -p " + protocol + " --dport " + port + src + " -j " + action.toUpperCase();
            }
            case "nftables" -> "nft add rule inet filter input " + protocol + " dport " + port + " " + action;
            case "ufw" -> "ufw allow " + port + "/" + protocol;
            case "pfctl" -> {
                // 写入 anchor 文件
                String rule = "pass in proto " + protocol + " from any to any port " + port;
                yield "echo '" + rule + "' >> /etc/pf.anchors/custom && pfctl -f /etc/pf.conf";
            }
            default -> throw new NginxException("不支持的防火墙工具: " + tool);
        };
    }

    private String buildDeleteCommand(String tool, String id, String protocol) {
        return switch (tool) {
            case "firewalld" -> "firewall-cmd --permanent --remove-port=" + id;
            case "iptables" -> "iptables -D INPUT " + id;
            case "nftables" -> {
                // 需要 handle，先获取
                CommandResult r = CommandUtil.execute("nft -a list ruleset");
                Matcher m = Pattern.compile("# handle (\\d+)").matcher(r.getStdout());
                int handleIdx = 0;
                String handle = null;
                while (m.find()) {
                    handleIdx++;
                    if (handleIdx == Integer.parseInt(id)) {
                        handle = m.group(1);
                        break;
                    }
                }
                if (handle == null) throw new NginxException("找不到规则 handle");
                yield "nft delete rule inet filter input handle " + handle;
            }
            case "ufw" -> "ufw delete " + id;
            case "pfctl" -> {
                // pfctl 不支持按行号删除，用 flush 代替
                yield "pfctl -f /etc/pf.conf";
            }
            default -> throw new NginxException("不支持的防火墙工具: " + tool);
        };
    }

    private void reload(String tool) {
        switch (tool) {
            case "firewalld" -> CommandUtil.execute("firewall-cmd --reload");
            case "pfctl" -> CommandUtil.execute("pfctl -f /etc/pf.conf");
            // iptables/nftables/ufw 无需 reload
        }
    }
}
