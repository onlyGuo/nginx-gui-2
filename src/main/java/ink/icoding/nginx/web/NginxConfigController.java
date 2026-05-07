package ink.icoding.nginx.web;

import ink.icoding.nginx.config.PathConfig;
import ink.icoding.nginx.config.PathConfigRepository;
import ink.icoding.nginx.core.NginxClient;
import ink.icoding.nginx.core.NginxClient.NginxException;
import ink.icoding.nginx.core.NginxConfig;
import ink.icoding.nginx.entity.*;
import ink.icoding.nginx.utils.FileUtil;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Nginx 配置页面 API：per-file 管理 conf.d 文件。
 */
@RestController
@RequestMapping("/api/v1/nginx/config")
public class NginxConfigController {

    private static final Long CONFIG_ID = 1L;

    private final PathConfigRepository pathConfigRepository;
    private final NginxClient nginxClient;

    public NginxConfigController(PathConfigRepository pathConfigRepository, NginxClient nginxClient) {
        this.pathConfigRepository = pathConfigRepository;
        this.nginxClient = nginxClient;
    }

    // ==================== GET /api/v1/nginx/config ====================

    @GetMapping
    public ApiResponse<Map<String, Object>> get() {
        PathConfig pc = loadPathConfig();
        String confPath = pc.getNginxConf();
        String confDir = resolveConfDir(pc);

        NginxConfig mainConfig = NginxConfig.parse(FileUtil.readFile(confPath));
        NginxHttpConfItem httpBlock = findHttpBlock(mainConfig);

        Map<String, Object> common = new LinkedHashMap<>();
        if (httpBlock != null) {
            common = buildCommon(httpBlock);
        }

        boolean hasMainBlocks = false;
        if (httpBlock != null) {
            hasMainBlocks = !httpBlock.getServers().isEmpty() || !httpBlock.getUpstreams().isEmpty();
        }

        List<Map<String, Object>> configFiles = new ArrayList<>();
        if (FileUtil.isDirectory(confDir)) {
            List<Map<String, Object>> entries = FileUtil.listDirectory(confDir);
            for (Map<String, Object> entry : entries) {
                String fileName = (String) entry.get("name");
                if (!fileName.endsWith(".conf") && !fileName.endsWith(".conf_off")) continue;
                boolean enabled = fileName.endsWith(".conf");
                Map<String, Object> fileInfo = new LinkedHashMap<>();
                fileInfo.put("name", fileName);
                fileInfo.put("enabled", enabled);
                Object time = entry.get("time");
                fileInfo.put("time", time instanceof Long ? formatTime((Long) time) : "");
                configFiles.add(fileInfo);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configFiles", configFiles);
        result.put("common", common);
        result.put("hasMainBlocks", hasMainBlocks);
        return ApiResponse.ok(result);
    }

    // ==================== GET /api/v1/nginx/config/file?name=xxx ====================

    @GetMapping("/file")
    public ApiResponse<Map<String, Object>> getFile(@RequestParam String name) {
        PathConfig pc = loadPathConfig();
        String confPath = pc.getNginxConf();
        String confDir = resolveConfDir(pc);
        String mainFileName = Path.of(confPath).getFileName().toString();

        List<Map<String, Object>> upstreams = new ArrayList<>();
        List<Map<String, Object>> servers = new ArrayList<>();

        if (name.equals(mainFileName)) {
            NginxConfig mainConfig = NginxConfig.parse(FileUtil.readFile(confPath));
            NginxHttpConfItem httpBlock = findHttpBlock(mainConfig);
            if (httpBlock != null) {
                for (NginxUpstreamConfItem up : httpBlock.getUpstreams()) {
                    upstreams.add(buildUpstream(up, mainFileName));
                }
                for (NginxServerConfItem srv : httpBlock.getServers()) {
                    servers.add(buildServer(srv, mainFileName));
                }
            }
        } else {
            String filePath = confDir + "/" + name;
            if (FileUtil.exists(filePath)) {
                NginxConfig fileConfig = NginxConfig.parse(FileUtil.readFile(filePath));
                for (NginxConfItem item : fileConfig.getItems()) {
                    if (item instanceof NginxUpstreamConfItem up) {
                        upstreams.add(buildUpstream(up, name));
                    } else if (item instanceof NginxServerConfItem srv) {
                        servers.add(buildServer(srv, name));
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("upstreams", upstreams);
        result.put("servers", servers);
        return ApiResponse.ok(result);
    }

    // ==================== PUT /api/v1/nginx/config/common ====================

    @PutMapping("/common")
    public ApiResponse<Void> updateCommon(@RequestBody Map<String, Object> body) {
        // 公共配置已全部迁移：default_type/resolver → BasicConfig，charset/proxy_* → Server
        return ApiResponse.ok();
    }

    // ==================== PUT /api/v1/nginx/config/file ====================

    @SuppressWarnings("unchecked")
    @PutMapping("/file")
    public ApiResponse<Void> updateFile(@RequestBody Map<String, Object> body) {
        String name = str(body.get("name"));
        if (name.isEmpty()) {
            return ApiResponse.error(400, "缺少文件名");
        }

        PathConfig pc = loadPathConfig();
        String confPath = pc.getNginxConf();
        String confDir = resolveConfDir(pc);
        String mainFileName = Path.of(confPath).getFileName().toString();

        List<Map<String, Object>> upstreams = (List<Map<String, Object>>) body.get("upstreams");
        List<Map<String, Object>> servers = (List<Map<String, Object>>) body.get("servers");

        if (name.equals(mainFileName)) {
            NginxConfig mainConfig = NginxConfig.parse(FileUtil.readFile(confPath));
            NginxHttpConfItem http = findHttpBlock(mainConfig);
            if (http == null) {
                http = new NginxHttpConfItem("http {\n}");
                mainConfig.getItems().add(http);
            }
            List<NginxConfItem> sub = http.listSubItems();
            sub.removeIf(item -> item instanceof NginxUpstreamConfItem || item instanceof NginxServerConfItem);

            if (upstreams != null) {
                for (Map<String, Object> upData : upstreams) {
                    sub.add(buildUpstreamBlock(upData));
                }
            }
            if (servers != null) {
                for (Map<String, Object> srvData : servers) {
                    sub.add(buildServerBlock(srvData));
                }
            }
            nginxClient.updateMainConfig(mainConfig.toString());
        } else {
            String filePath = confDir + "/" + name;
            NginxConfig fileConfig;
            if (FileUtil.exists(filePath)) {
                fileConfig = NginxConfig.parse(FileUtil.readFile(filePath));
            } else {
                fileConfig = new NginxConfig(new ArrayList<>());
            }

            List<NginxConfItem> items = fileConfig.getItems();
            items.removeIf(item -> item instanceof NginxUpstreamConfItem || item instanceof NginxServerConfItem);

            if (upstreams != null) {
                for (Map<String, Object> upData : upstreams) {
                    items.add(buildUpstreamBlock(upData));
                }
            }
            if (servers != null) {
                for (Map<String, Object> srvData : servers) {
                    items.add(buildServerBlock(srvData));
                }
            }

            if (items.isEmpty()) {
                FileUtil.deleteIfExists(filePath);
            } else {
                if (!FileUtil.isDirectory(confDir)) {
                    FileUtil.createDirectories(confDir);
                }
                FileUtil.writeFile(filePath, fileConfig.toString());
            }
        }

        nginxClient.validateConfig();
        safeReload();
        return ApiResponse.ok();
    }

    // ==================== POST /api/v1/nginx/config/file ====================

    @PostMapping("/file")
    public ApiResponse<Void> createFile(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String port = body.getOrDefault("port", "80");
        String domain = body.getOrDefault("domain", "");

        if (name == null || name.isBlank()) {
            return ApiResponse.error(400, "文件名不能为空");
        }

        name = name.replaceAll("[^a-zA-Z0-9._-]", "");
        if (!name.endsWith(".conf")) {
            name = name + ".conf";
        }

        PathConfig pc = loadPathConfig();
        String confDir = resolveConfDir(pc);
        if (!FileUtil.isDirectory(confDir)) {
            FileUtil.createDirectories(confDir);
        }

        String filePath = confDir + "/" + name;
        if (FileUtil.exists(filePath)) {
            return ApiResponse.error(409, "文件已存在: " + name);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("server {\n");
        sb.append("    listen ").append(port).append(";\n");
        if (!domain.isEmpty()) {
            sb.append("    server_name ").append(domain).append(";\n");
        }
        sb.append("    location / {\n");
        sb.append("        root /var/www/html;\n");
        sb.append("    }\n");
        sb.append("}\n");

        FileUtil.writeFile(filePath, sb.toString());

        nginxClient.validateConfig();
        safeReload();
        return ApiResponse.ok();
    }

    // ==================== PUT /api/v1/nginx/config/file/toggle ====================

    @PutMapping("/file/toggle")
    public ApiResponse<Void> toggleFile(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ApiResponse.error(400, "文件名不能为空");
        }

        PathConfig pc = loadPathConfig();
        String confDir = resolveConfDir(pc);
        String filePath = confDir + "/" + name;

        if (!FileUtil.exists(filePath)) {
            return ApiResponse.error(404, "文件不存在: " + name);
        }

        String newName;
        if (name.endsWith(".conf")) {
            newName = name.substring(0, name.length() - 5) + ".conf_off";
        } else if (name.endsWith(".conf_off")) {
            newName = name.substring(0, name.length() - 9) + ".conf";
        } else {
            return ApiResponse.error(400, "不支持的文件后缀: " + name);
        }

        String newPath = confDir + "/" + newName;
        FileUtil.move(filePath, newPath, false);

        nginxClient.validateConfig();
        safeReload();
        return ApiResponse.ok();
    }

    // ==================== DELETE /api/v1/nginx/config/file?name=xxx ====================

    @DeleteMapping("/file")
    public ApiResponse<Void> deleteFile(@RequestParam String name) {
        if (name == null || name.isBlank()) {
            return ApiResponse.error(400, "文件名不能为空");
        }

        PathConfig pc = loadPathConfig();
        String confDir = resolveConfDir(pc);
        String filePath = confDir + "/" + name;

        if (!FileUtil.exists(filePath)) {
            return ApiResponse.error(404, "文件不存在: " + name);
        }

        FileUtil.deleteIfExists(filePath);
        nginxClient.validateConfig();
        safeReload();
        return ApiResponse.ok();
    }

    // ==================== GET /api/v1/nginx/config/raw?name=xxx ====================

    @GetMapping("/raw")
    public ApiResponse<Map<String, String>> getRaw(@RequestParam String name) {
        PathConfig pc = loadPathConfig();
        String confPath = pc.getNginxConf();
        String mainFileName = Path.of(confPath).getFileName().toString();

        String filePath;
        if (name.equals(mainFileName)) {
            filePath = confPath;
        } else {
            filePath = resolveConfDir(pc) + "/" + name;
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("content", FileUtil.readFile(filePath));
        return ApiResponse.ok(result);
    }

    // ==================== PUT /api/v1/nginx/config/raw ====================

    @PutMapping("/raw")
    public ApiResponse<Void> updateRaw(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String content = body.get("content");
        if (name == null || name.isBlank()) {
            return ApiResponse.error(400, "缺少文件名");
        }
        if (content == null) {
            return ApiResponse.error(400, "缺少内容");
        }

        PathConfig pc = loadPathConfig();
        String confPath = pc.getNginxConf();
        String mainFileName = Path.of(confPath).getFileName().toString();

        String filePath;
        if (name.equals(mainFileName)) {
            filePath = confPath;
        } else {
            filePath = resolveConfDir(pc) + "/" + name;
        }

        // 备份 → 写入 → 校验 → 失败则回滚
        String backup = filePath + ".bak";
        boolean hasBackup = false;
        if (FileUtil.exists(filePath)) {
            FileUtil.copy(filePath, backup, true);
            hasBackup = true;
        }

        FileUtil.writeFile(filePath, content);

        try {
            nginxClient.validateConfig();
            FileUtil.deleteIfExists(backup);
        } catch (NginxException e) {
            if (hasBackup) {
                FileUtil.move(backup, filePath, true);
            } else {
                FileUtil.deleteIfExists(filePath);
            }
            return ApiResponse.error(400, "配置校验失败，已回滚: " + e.getMessage());
        }

        safeReload();
        return ApiResponse.ok();
    }

    // ==================== POST /api/v1/nginx/config/clear-main-blocks ====================

    @PostMapping("/clear-main-blocks")
    public ApiResponse<Void> clearMainBlocks() {
        PathConfig pc = loadPathConfig();
        String confPath = pc.getNginxConf();

        NginxConfig mainConfig = NginxConfig.parse(FileUtil.readFile(confPath));
        NginxHttpConfItem http = findHttpBlock(mainConfig);
        if (http == null) {
            return ApiResponse.ok();
        }

        List<NginxConfItem> sub = http.listSubItems();
        sub.removeIf(item -> item instanceof NginxServerConfItem || item instanceof NginxUpstreamConfItem);

        nginxClient.updateMainConfig(mainConfig.toString());
        nginxClient.validateConfig();
        safeReload();
        return ApiResponse.ok();
    }

    // ==================== Common 构建 ====================

    private Map<String, Object> buildCommon(NginxHttpConfItem http) {
        Map<String, Object> m = new LinkedHashMap<>();
        // default_type 和 resolver 不可覆盖，已移至 BasicConfig (global-config API)
        // charset, proxy_* 可在 server 级别覆盖，已移至 Server 配置
        return m;
    }

    // ==================== Upstream/Server 构建 ====================

    private Map<String, Object> buildUpstream(NginxUpstreamConfItem up, String sourceFile) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", up.getFirstValue() != null ? up.getFirstValue() : up.getName());
        m.put("strategy", up.getLoadBalancingMethod() != null ? up.getLoadBalancingMethod() : "");
        m.put("sourceFile", sourceFile);

        NginxConfItem ka = up.getItem("keepalive");
        m.put("keepalive", ka instanceof NginxInlineConfItem ? ((NginxInlineConfItem) ka).getValue() : "");

        // 清理无效的独立 weight 项（weight 只能作为 server 的参数，不能独立存在）
        up.listSubItems().removeIf(item ->
                item instanceof NginxInlineConfItem inline && "weight".equals(inline.getName()));

        List<Map<String, String>> srvs = new ArrayList<>();
        for (String addr : up.getServerAddresses()) {
            Map<String, String> s = new LinkedHashMap<>();
            String[] parts = addr.split("\\s+");
            s.put("addr", parts[0]);
            s.put("weight", "");
            s.put("state", "");
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].startsWith("weight=")) s.put("weight", parts[i].substring(7));
                else if (parts[i].equals("down") || parts[i].equals("backup")) s.put("state", parts[i]);
            }
            srvs.add(s);
        }
        m.put("servers", srvs);
        return m;
    }

    private Map<String, Object> buildServer(NginxServerConfItem srv, String sourceFile) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceFile", sourceFile);

        List<String> listenPorts = srv.getListenPorts();
        boolean ssl = srv.isSslEnabled();
        String listenStr = "";
        if (!listenPorts.isEmpty()) {
            listenStr = listenPorts.get(0).replaceAll("\\s+.*", "");
        }
        m.put("listen", listenStr);
        List<String> names = srv.getServerNames();
        m.put("serverName", names.isEmpty() ? "" : names.get(0));
        m.put("charset", getInlineValue(srv, "charset", ""));
        m.put("root", srv.getRoot() != null ? srv.getRoot() : "");
        m.put("index", getInlineValue(srv, "index", ""));
        m.put("accessLog", getInlineValue(srv, "access_log", ""));
        m.put("errorLog", getInlineValue(srv, "error_log", ""));
        m.put("clientMaxBodySize", getInlineValue(srv, "client_max_body_size", ""));
        m.put("ssl", ssl);
        m.put("sslCert", srv.getSslCertificate() != null ? srv.getSslCertificate() : "");
        m.put("sslKey", srv.getSslCertificateKey() != null ? srv.getSslCertificateKey() : "");
        m.put("sslProtocols", getInlineValue(srv, "ssl_protocols", "TLSv1.2 TLSv1.3"));
        m.put("sslCiphers", getInlineValue(srv, "ssl_ciphers", ""));
        m.put("sslPreferServerCiphers", "on".equalsIgnoreCase(getInlineValue(srv, "ssl_prefer_server_ciphers", "off")));
        m.put("sslSessionTimeout", getInlineValue(srv, "ssl_session_timeout", ""));
        m.put("sslSessionCache", getInlineValue(srv, "ssl_session_cache", ""));

        boolean sslRedirect = false;
        for (NginxConfItem item : srv.listSubItems()) {
            if (item instanceof NginxInlineConfItem inline && "return".equals(inline.getName())) {
                if (inline.getValue().contains("https://")) {
                    sslRedirect = true;
                    break;
                }
            }
        }
        m.put("sslRedirect", sslRedirect);

        m.put("autoindex", "on".equalsIgnoreCase(getInlineValue(srv, "autoindex", "off")));
        m.put("proxyBuffering", !"off".equalsIgnoreCase(getInlineValue(srv, "proxy_buffering", "on")));
        m.put("proxyConnectTimeout", getInlineValue(srv, "proxy_connect_timeout", ""));
        m.put("proxyReadTimeout", getInlineValue(srv, "proxy_read_timeout", ""));
        m.put("proxySendTimeout", getInlineValue(srv, "proxy_send_timeout", ""));
        m.put("proxyBufferSize", getInlineValue(srv, "proxy_buffer_size", ""));
        List<String> addHeaders = new java.util.ArrayList<>();
        for (NginxConfItem item : srv.listSubItems()) {
            if (item instanceof NginxInlineConfItem inline && "add_header".equals(inline.getName())) {
                addHeaders.add(inline.getValue());
            }
        }
        m.put("addHeaders", addHeaders);

        // HTTP 级别可覆盖字段（留空则继承全局）
        m.put("sendfile", getInlineValue(srv, "sendfile", ""));
        m.put("tcpNopush", getInlineValue(srv, "tcp_nopush", ""));
        m.put("tcpNodelay", getInlineValue(srv, "tcp_nodelay", ""));
        m.put("keepaliveTimeout", getInlineValue(srv, "keepalive_timeout", ""));
        m.put("keepaliveRequests", getInlineValue(srv, "keepalive_requests", ""));
        m.put("clientBodyTimeout", getInlineValue(srv, "client_body_timeout", ""));
        m.put("clientHeaderTimeout", getInlineValue(srv, "client_header_timeout", ""));
        m.put("typesHashMaxSize", getInlineValue(srv, "types_hash_max_size", ""));
        m.put("serverTokens", getInlineValue(srv, "server_tokens", ""));

        Map<String, Object> gzip = new LinkedHashMap<>();
        NginxConfItem gzItem = srv.getItem("gzip");
        boolean gzOn = gzItem instanceof NginxInlineConfItem && "on".equalsIgnoreCase(((NginxInlineConfItem) gzItem).getValue());
        gzip.put("on", gzOn);
        gzip.put("minLength", getInlineValue(srv, "gzip_min_length", "1024"));
        gzip.put("compLevel", getInlineValue(srv, "gzip_comp_level", "6"));
        gzip.put("types", getInlineValue(srv, "gzip_types", ""));
        gzip.put("vary", "on".equalsIgnoreCase(getInlineValue(srv, "gzip_vary", "off")));
        gzip.put("proxied", getInlineValue(srv, "gzip_proxied", "any"));
        String buffers = getInlineValue(srv, "gzip_buffers", "");
        if (!buffers.isEmpty()) {
            String[] bp = buffers.split("\\s+", 2);
            gzip.put("buffersNum", bp[0]);
            gzip.put("buffersSize", bp.length > 1 ? bp[1] : "8k");
        } else {
            gzip.put("buffersNum", "4");
            gzip.put("buffersSize", "8k");
        }
        gzip.put("httpVersion", getInlineValue(srv, "gzip_http_version", "1.1"));
        m.put("gzip", gzip);

        List<Map<String, Object>> locations = new ArrayList<>();
        for (NginxLocationConfItem loc : srv.getLocations()) {
            locations.add(buildLocation(loc));
        }
        m.put("locations", locations);
        return m;
    }

    private Map<String, Object> buildLocation(NginxLocationConfItem loc) {
        Map<String, Object> m = new LinkedHashMap<>();

        String modifier = loc.getModifier();
        if ("=".equals(modifier)) m.put("type", "exact");
        else if ("~".equals(modifier)) m.put("type", "regex");
        else if ("~*".equals(modifier)) m.put("type", "regexNocase");
        else m.put("type", "prefix");

        m.put("path", loc.getPath() != null ? loc.getPath() : "/");
        m.put("root", getInlineValue(loc, "root", ""));
        m.put("alias", getInlineValue(loc, "alias", ""));

        m.put("proxyPass", getInlineValue(loc, "proxy_pass", ""));

        m.put("tryFiles", getInlineValue(loc, "try_files", ""));
        m.put("returnCode", getInlineValue(loc, "return", ""));
        m.put("rewrite", getInlineValue(loc, "rewrite", ""));
        m.put("index", getInlineValue(loc, "index", ""));
        m.put("expires", getInlineValue(loc, "expires", ""));
        m.put("deny", getInlineValue(loc, "deny", ""));
        m.put("allow", getInlineValue(loc, "allow", ""));

        for (NginxConfItem item : loc.findAll("proxy_set_header")) {
            if (item instanceof NginxInlineConfItem inline) {
                String val = inline.getValue().trim();
                if (val.startsWith("Host")) m.put("proxyHost", val.substring(4).trim());
                else if (val.startsWith("X-Real-IP")) m.put("proxyRealIp", val.substring(9).trim());
                else if (val.startsWith("X-Forwarded-For")) m.put("proxyXff", val.substring(15).trim());
                else if (val.startsWith("X-Forwarded-Proto")) m.put("proxyProto", val.substring(17).trim());
            }
        }
        m.putIfAbsent("proxyHost", "");
        m.putIfAbsent("proxyRealIp", "");
        m.putIfAbsent("proxyXff", "");
        m.putIfAbsent("proxyProto", "");

        return m;
    }

    // ==================== Block 构建 ====================

    @SuppressWarnings("unchecked")
    private NginxUpstreamConfItem buildUpstreamBlock(Map<String, Object> data) {
        String name = str(data.get("name"));
        NginxUpstreamConfItem up = new NginxUpstreamConfItem("upstream " + name + " {\n}");

        String strategy = str(data.get("strategy"));
        // 只接受合法的负载均衡策略，忽略无效值（如 "weight"）
        if (!strategy.isEmpty() && isStrategyValid(strategy)) {
            up.setLoadBalancingMethod(strategy);
        }

        String keepalive = str(data.get("keepalive"));
        if (!keepalive.isEmpty()) {
            up.addItem(new NginxInlineConfItem("keepalive " + keepalive + ";"));
        }

        List<Map<String, Object>> servers = (List<Map<String, Object>>) data.get("servers");
        if (servers != null) {
            for (Map<String, Object> srv : servers) {
                String addr = str(srv.get("addr")).trim();
                if (addr.isEmpty()) continue;
                StringBuilder sb = new StringBuilder(addr);
                String weight = str(srv.get("weight")).trim();
                if (!weight.isEmpty()) sb.append(" weight=").append(weight);
                String state = str(srv.get("state")).trim();
                if (!state.isEmpty()) sb.append(" ").append(state);
                up.addServer(sb.toString());
            }
        }
        return up;
    }

    @SuppressWarnings("unchecked")
    private NginxServerConfItem buildServerBlock(Map<String, Object> data) {
        NginxServerConfItem srv = new NginxServerConfItem("server {\n}");

        String listen = str(data.get("listen"));
        boolean ssl = bool(data.get("ssl"), false);
        if (!listen.isEmpty()) {
            if (ssl) {
                srv.addListenPort(listen, "ssl");
            } else {
                srv.addListenPort(listen);
            }
        }

        String serverName = str(data.get("serverName"));
        if (!serverName.isEmpty()) {
            srv.setServerNames(serverName);
        }

        List<NginxConfItem> sub = srv.listSubItems();

        setOrAdd(sub, "charset", str(data.get("charset")));
        String root = str(data.get("root"));
        if (!root.isEmpty()) srv.setRoot(root);
        setOrAdd(sub, "index", str(data.get("index")));
        setOrAdd(sub, "access_log", str(data.get("accessLog")));
        setOrAdd(sub, "error_log", str(data.get("errorLog")));
        setOrAdd(sub, "client_max_body_size", str(data.get("clientMaxBodySize")));

        if (ssl) {
            setOrAdd(sub, "ssl_certificate", str(data.get("sslCert")));
            setOrAdd(sub, "ssl_certificate_key", str(data.get("sslKey")));
            setOrAdd(sub, "ssl_protocols", str(data.get("sslProtocols")));
            setOrAdd(sub, "ssl_ciphers", str(data.get("sslCiphers")));
            setOrAdd(sub, "ssl_prefer_server_ciphers", bool(data.get("sslPreferServerCiphers"), false) ? "on" : "off");
            setOrAdd(sub, "ssl_session_timeout", str(data.get("sslSessionTimeout")));
            setOrAdd(sub, "ssl_session_cache", str(data.get("sslSessionCache")));
            if (bool(data.get("sslRedirect"), false)) {
                boolean hasReturn = sub.stream().anyMatch(i ->
                        i instanceof NginxInlineConfItem ci && "return".equals(ci.getName()));
                if (!hasReturn) {
                    sub.add(new NginxInlineConfItem("return 301 https://$host$request_uri;"));
                }
            }
        }

        setOrAdd(sub, "autoindex", bool(data.get("autoindex"), false) ? "on" : "off");
        setOrAdd(sub, "proxy_buffering", bool(data.get("proxyBuffering"), true) ? "on" : "off");
        setOrAdd(sub, "proxy_connect_timeout", str(data.get("proxyConnectTimeout")));
        setOrAdd(sub, "proxy_read_timeout", str(data.get("proxyReadTimeout")));
        setOrAdd(sub, "proxy_send_timeout", str(data.get("proxySendTimeout")));
        setOrAdd(sub, "proxy_buffer_size", str(data.get("proxyBufferSize")));
        sub.removeIf(item -> item instanceof NginxInlineConfItem inline && "add_header".equals(inline.getName()));
        List<String> addHeaders = (List<String>) data.get("addHeaders");
        if (addHeaders != null) {
            for (String h : addHeaders) {
                if (h != null && !h.isBlank()) {
                    sub.add(new NginxInlineConfItem("add_header " + h + ";"));
                }
            }
        }

        // HTTP 级别可覆盖字段
        String sendfile = str(data.get("sendfile"));
        if (!sendfile.isEmpty()) setOrAdd(sub, "sendfile", sendfile);
        String tcpNopush = str(data.get("tcpNopush"));
        if (!tcpNopush.isEmpty()) setOrAdd(sub, "tcp_nopush", tcpNopush);
        String tcpNodelay = str(data.get("tcpNodelay"));
        if (!tcpNodelay.isEmpty()) setOrAdd(sub, "tcp_nodelay", tcpNodelay);
        String keepaliveTimeout = str(data.get("keepaliveTimeout"));
        if (!keepaliveTimeout.isEmpty()) setOrAdd(sub, "keepalive_timeout", keepaliveTimeout);
        String keepaliveRequests = str(data.get("keepaliveRequests"));
        if (!keepaliveRequests.isEmpty()) setOrAdd(sub, "keepalive_requests", keepaliveRequests);
        String clientBodyTimeout = str(data.get("clientBodyTimeout"));
        if (!clientBodyTimeout.isEmpty()) setOrAdd(sub, "client_body_timeout", clientBodyTimeout);
        String clientHeaderTimeout = str(data.get("clientHeaderTimeout"));
        if (!clientHeaderTimeout.isEmpty()) setOrAdd(sub, "client_header_timeout", clientHeaderTimeout);
        String typesHashMaxSize = str(data.get("typesHashMaxSize"));
        if (!typesHashMaxSize.isEmpty()) setOrAdd(sub, "types_hash_max_size", typesHashMaxSize);
        String serverTokens = str(data.get("serverTokens"));
        if (!serverTokens.isEmpty()) setOrAdd(sub, "server_tokens", serverTokens);

        Map<String, Object> gzip = (Map<String, Object>) data.get("gzip");
        if (gzip != null) {
            setOrAdd(sub, "gzip", bool(gzip.get("on"), false) ? "on" : "off");
            if (bool(gzip.get("on"), false)) {
                setOrAdd(sub, "gzip_min_length", str(gzip.get("minLength")));
                setOrAdd(sub, "gzip_comp_level", str(gzip.get("compLevel")));
                setOrAdd(sub, "gzip_types", str(gzip.get("types")));
                setOrAdd(sub, "gzip_vary", bool(gzip.get("vary"), false) ? "on" : "off");
                setOrAdd(sub, "gzip_proxied", str(gzip.get("proxied")));
                String bNum = str(gzip.get("buffersNum"));
                String bSize = str(gzip.get("buffersSize"));
                if (!bNum.isEmpty() && !bSize.isEmpty()) {
                    setOrAdd(sub, "gzip_buffers", bNum + " " + bSize);
                }
                setOrAdd(sub, "gzip_http_version", str(gzip.get("httpVersion")));
            }
        }

        List<Map<String, Object>> locations = (List<Map<String, Object>>) data.get("locations");
        if (locations != null) {
            for (Map<String, Object> locData : locations) {
                srv.addItem(buildLocationBlock(locData));
            }
        }

        return srv;
    }

    @SuppressWarnings("unchecked")
    private NginxLocationConfItem buildLocationBlock(Map<String, Object> data) {
        String type = str(data.get("type"));
        String path = str(data.get("path"));
        if (path.isEmpty()) path = "/";

        String modifier = "";
        switch (type) {
            case "exact" -> modifier = "=";
            case "regex" -> modifier = "~";
            case "regexNocase" -> modifier = "~*";
        }

        NginxLocationConfItem loc = new NginxLocationConfItem(
                "location " + (modifier.isEmpty() ? "" : modifier + " ") + path + " {\n}");

        List<NginxConfItem> sub = loc.listSubItems();

        String root = str(data.get("root"));
        if (!root.isEmpty()) setOrAdd(sub, "root", root);
        String alias = str(data.get("alias"));
        if (!alias.isEmpty()) setOrAdd(sub, "alias", alias);

        String proxyPass = str(data.get("proxyPass"));
        if (!proxyPass.isEmpty()) {
            setOrAdd(sub, "proxy_pass", proxyPass);
        }

        setOrAdd(sub, "try_files", str(data.get("tryFiles")));
        setOrAdd(sub, "return", str(data.get("returnCode")));
        setOrAdd(sub, "rewrite", str(data.get("rewrite")));
        setOrAdd(sub, "index", str(data.get("index")));
        setOrAdd(sub, "expires", str(data.get("expires")));
        setOrAdd(sub, "deny", str(data.get("deny")));
        setOrAdd(sub, "allow", str(data.get("allow")));

        addProxyHeaderIfPresent(sub, "Host", str(data.get("proxyHost")));
        addProxyHeaderIfPresent(sub, "X-Real-IP", str(data.get("proxyRealIp")));
        addProxyHeaderIfPresent(sub, "X-Forwarded-For", str(data.get("proxyXff")));
        addProxyHeaderIfPresent(sub, "X-Forwarded-Proto", str(data.get("proxyProto")));

        return loc;
    }

    // ==================== 工具方法 ====================

    private NginxHttpConfItem findHttpBlock(NginxConfig config) {
        for (NginxConfItem item : config.getItems()) {
            if (item instanceof NginxHttpConfItem http) return http;
        }
        return null;
    }

    private PathConfig loadPathConfig() {
        return pathConfigRepository.findById(CONFIG_ID).orElseGet(() -> {
            PathConfig defaults = new PathConfig();
            defaults.setId(CONFIG_ID);
            return pathConfigRepository.save(defaults);
        });
    }

    private String resolveConfDir(PathConfig pc) {
        String confDir = pc.getConfDir();
        if (confDir != null && !confDir.isBlank()) {
            return confDir;
        }
        String nginxConf = pc.getNginxConf();
        int lastSlash = Math.max(nginxConf.lastIndexOf('/'), nginxConf.lastIndexOf('\\'));
        return lastSlash > 0 ? nginxConf.substring(0, lastSlash) + "/conf.d" : "conf.d";
    }

    private String getInlineValue(NginxConfItem block, String name, String defaultVal) {
        NginxConfItem item = block instanceof NginxBlockConfItem b ? b.getItem(name) : null;
        if (item instanceof NginxInlineConfItem inline) {
            return inline.getValue();
        }
        return defaultVal;
    }

    private void addProxyHeaderIfPresent(List<NginxConfItem> items, String headerName, String value) {
        if (value == null || value.isEmpty()) return;
        items.add(new NginxInlineConfItem("proxy_set_header " + headerName + " " + value + ";"));
    }

    private void setOrAdd(List<NginxConfItem> items, String name, String value) {
        if (value == null || value.isEmpty()) return;
        if ("proxy_set_header".equals(name)) {
            items.add(new NginxInlineConfItem(name + " " + value + ";"));
            return;
        }
        for (NginxConfItem item : items) {
            if (item instanceof NginxInlineConfItem inline && name.equals(inline.getName())) {
                inline.setValue(value);
                return;
            }
        }
        items.add(new NginxInlineConfItem(name + " " + value + ";"));
    }

    private void safeReload() {
        try {
            nginxClient.reload();
        } catch (NginxException e) {
            nginxClient.start();
        }
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private boolean bool(Object o, boolean defaultVal) {
        if (o == null) return defaultVal;
        if (o instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(o)) || "on".equalsIgnoreCase(String.valueOf(o));
    }

    private boolean isStrategyValid(String strategy) {
        return "ip_hash".equals(strategy) || "least_conn".equals(strategy) || "random".equals(strategy);
    }

    private String formatTime(long millis) {
        var dt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault());
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
