package ink.icoding.nginx.web;

import ink.icoding.nginx.config.PathConfig;
import ink.icoding.nginx.config.PathConfigRepository;
import ink.icoding.nginx.core.NginxConfig;
import ink.icoding.nginx.core.BadRequestException;
import ink.icoding.nginx.core.NginxClient;
import ink.icoding.nginx.core.NginxException;
import ink.icoding.nginx.entity.*;
import ink.icoding.nginx.utils.FileUtil;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/nginx/global-config")
public class NginxGlobalConfigController {

    private static final Long CONFIG_ID = 1L;

    /**
     * 配置路径 → nginx 指令名映射。
     * 大部分路径去掉前缀后就是指令名，只有少数例外。
     */
    private static final Map<String, String> DIRECTIVE_MAP = Map.ofEntries(
            Map.entry("error_log_level", "error_log"),
            Map.entry("http.access_log_format", "access_log"),
            Map.entry("http.log_format", "log_format")
    );

    private final PathConfigRepository pathConfigRepository;

    public NginxGlobalConfigController(PathConfigRepository pathConfigRepository) {
        this.pathConfigRepository = pathConfigRepository;
    }

    // ==================== GET ====================

    @GetMapping
    public ApiResponse<Map<String, Object>> get() {
        PathConfig pc = loadPathConfig();
        String content = readFile(pc.getNginxConf());
        NginxConfig config = NginxConfig.parse(content);

        List<NginxConfItem> items = config.getItems();

        // 分类收集
        List<NginxInlineConfItem> mainDirectives = new ArrayList<>();
        NginxEventsConfItem eventsBlock = null;
        NginxHttpConfItem httpBlock = null;

        for (int i = 0; i < items.size(); i++) {
            NginxConfItem item = items.get(i);
            if (item instanceof NginxInlineConfItem inline) {
                mainDirectives.add(inline);
            } else if (item instanceof NginxEventsConfItem events) {
                eventsBlock = events;
            } else if (item instanceof NginxHttpConfItem http) {
                httpBlock = http;
            }
        }

        // 构建 main 配置
        Map<String, Object> mainValues = new LinkedHashMap<>();
        Map<String, Object> mainAnchors = new LinkedHashMap<>();
        buildMainSection(items, mainDirectives, mainValues, mainAnchors);

        // 构建 events 配置
        Map<String, Object> eventsValues = new LinkedHashMap<>();
        Map<String, Object> eventsAnchors = new LinkedHashMap<>();
        if (eventsBlock != null) {
            buildEventsSection(items, eventsBlock, eventsValues, eventsAnchors);
        }

        // 构建 http 配置
        Map<String, Object> httpValues = new LinkedHashMap<>();
        Map<String, Object> httpAnchors = new LinkedHashMap<>();
        if (httpBlock != null) {
            buildHttpSection(items, httpBlock, httpValues, httpAnchors);
        }

        // 补充缺失指令的默认 anchor（itemIndex=-1 表示待创建）
        fillDefaultAnchors(mainAnchors, "user", "worker_processes", "error_log", "error_log_level", "pid");
        fillDefaultAnchors(eventsAnchors, "events.worker_connections", "events.use", "events.multi_accept");
        fillDefaultAnchors(httpAnchors,
                "http.sendfile", "http.tcp_nopush", "http.tcp_nodelay",
                "http.keepalive_timeout", "http.keepalive_requests",
                "http.client_max_body_size", "http.client_body_timeout", "http.client_header_timeout",
                "http.types_hash_max_size", "http.server_tokens",
                "http.default_type", "http.resolver",
                "http.gzip", "http.gzip_min_length", "http.gzip_comp_level", "http.gzip_types", "http.gzip_vary", "http.gzip_proxied",
                "http.ssl_protocols", "http.ssl_ciphers", "http.ssl_prefer_server_ciphers", "http.ssl_session_timeout", "http.ssl_session_cache",
                "http.access_log", "http.access_log_format",
                "http.log_format");

        // 组装响应
        Map<String, Object> configMap = new LinkedHashMap<>();
        configMap.put("main", mainValues);
        configMap.put("events", eventsValues);
        configMap.put("http", httpValues);

        Map<String, Object> anchors = new LinkedHashMap<>();
        anchors.putAll(mainAnchors);
        anchors.putAll(eventsAnchors);
        anchors.putAll(httpAnchors);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("config", configMap);
        result.put("anchors", anchors);

        return ApiResponse.ok(result);
    }

    // ==================== PUT ====================

    @PutMapping
    public ApiResponse<Void> update(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patches = (List<Map<String, Object>>) body.get("patches");
        if (patches == null || patches.isEmpty()) {
            throw new BadRequestException("缺少 patches");
        }

        PathConfig pc = loadPathConfig();
        String confPath = pc.getNginxConf();
        String content = readFile(confPath);
        NginxConfig config = NginxConfig.parse(content);
        List<NginxConfItem> items = config.getItems();

        // 查找 events 和 http 块
        NginxEventsConfItem eventsBlock = null;
        NginxHttpConfItem httpBlock = null;
        for (NginxConfItem item : items) {
            if (item instanceof NginxEventsConfItem e) eventsBlock = e;
            if (item instanceof NginxHttpConfItem h) httpBlock = h;
        }

        for (Map<String, Object> patch : patches) {
            String path = (String) patch.get("path");

            // log_format 是列表字段，整体替换
            if ("http.log_format".equals(path)) {
                if (httpBlock == null) throw new NginxException("未找到 http 块");
                replaceLogFormats(httpBlock.listSubItems(), patch.get("value"));
                continue;
            }

            String value = String.valueOf(patch.get("value"));
            Number indexNum = (Number) patch.get("itemIndex");
            if (path == null || indexNum == null) {
                throw new NginxException("patch 缺少 path 或 itemIndex");
            }
            int itemIndex = indexNum.intValue();

            String directiveName = DIRECTIVE_MAP.getOrDefault(path, stripPrefix(path));

            if (path.startsWith("events.")) {
                if (eventsBlock == null) throw new NginxException("未找到 events 块");
                replaceItem(eventsBlock.listSubItems(), itemIndex, directiveName, value);

            } else if (path.startsWith("http.")) {
                if (httpBlock == null) throw new NginxException("未找到 http 块");
                replaceItem(httpBlock.listSubItems(), itemIndex, directiveName, value);

            } else {
                replaceMainItem(items, itemIndex, directiveName, value);
            }
        }

        // 保存：校验 → 写入
        NginxClient client = new NginxClient(pc.getNginxBin(), pc.getNginxConf());
        client.updateMainConfig(config.toString());

        return ApiResponse.ok();
    }

    // ==================== Section 构建 ====================

    private void buildMainSection(List<NginxConfItem> allItems,
                                  List<NginxInlineConfItem> mainDirectives,
                                  Map<String, Object> values,
                                  Map<String, Object> anchors) {
        for (NginxInlineConfItem item : mainDirectives) {
            String name = item.getName();
            int index = allItems.indexOf(item);

            switch (name) {
                case "user" -> {
                    values.put("user", item.getValue());
                    anchors.put("user", makeAnchor(index, item));
                }
                case "worker_processes" -> {
                    values.put("worker_processes", item.getValue());
                    anchors.put("worker_processes", makeAnchor(index, item));
                }
                case "error_log" -> {
                    String val = item.getValue();
                    String[] parts = val.split("\\s+", 2);
                    // 第一个 error_log 用于表单展示
                    if (!values.containsKey("error_log")) {
                        values.put("error_log", parts[0]);
                        anchors.put("error_log", makeAnchor(index, item));
                        if (parts.length > 1) {
                            values.put("error_log_level", parts[1]);
                            anchors.put("error_log_level", makeAnchor(index, item));
                        }
                    }
                }
                case "pid" -> {
                    values.put("pid", item.getValue());
                    anchors.put("pid", makeAnchor(index, item));
                }
            }
        }
    }

    private void buildEventsSection(List<NginxConfItem> allItems,
                                    NginxEventsConfItem events,
                                    Map<String, Object> values,
                                    Map<String, Object> anchors) {
        int blockIndex = allItems.indexOf(events);
        List<NginxConfItem> subItems = events.listSubItems();

        for (int i = 0; i < subItems.size(); i++) {
            NginxConfItem sub = subItems.get(i);
            if (sub instanceof NginxInlineConfItem inline) {
                switch (inline.getName()) {
                    case "worker_connections" -> {
                        values.put("worker_connections", inline.getValue());
                        anchors.put("events.worker_connections", makeSubAnchor(blockIndex, i, inline));
                    }
                    case "use" -> {
                        values.put("use", inline.getValue());
                        anchors.put("events.use", makeSubAnchor(blockIndex, i, inline));
                    }
                    case "multi_accept" -> {
                        values.put("multi_accept", inline.getValue());
                        anchors.put("events.multi_accept", makeSubAnchor(blockIndex, i, inline));
                    }
                }
            }
        }
    }

    private void buildHttpSection(List<NginxConfItem> allItems,
                                  NginxHttpConfItem http,
                                  Map<String, Object> values,
                                  Map<String, Object> anchors) {
        int blockIndex = allItems.indexOf(http);
        List<NginxConfItem> subItems = http.listSubItems();

        // 收集 http 块内的 server 和 upstream 名称（用于过滤非指令项）
        Set<NginxConfItem> structuralItems = new HashSet<>(http.getServers());
        structuralItems.addAll(http.getUpstreams());

        for (int i = 0; i < subItems.size(); i++) {
            NginxConfItem sub = subItems.get(i);
            // 跳过结构化块（server、upstream 等）
            if (structuralItems.contains(sub)) continue;
            if (!(sub instanceof NginxInlineConfItem inline)) continue;

            String name = inline.getName();
            String key = "http." + name;

            switch (name) {
                case "sendfile" -> { values.put("sendfile", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "tcp_nopush" -> { values.put("tcp_nopush", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "tcp_nodelay" -> { values.put("tcp_nodelay", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "keepalive_timeout" -> { values.put("keepalive_timeout", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "keepalive_requests" -> { values.put("keepalive_requests", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "client_max_body_size" -> { values.put("client_max_body_size", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "client_body_timeout" -> { values.put("client_body_timeout", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "client_header_timeout" -> { values.put("client_header_timeout", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "types_hash_max_size" -> { values.put("types_hash_max_size", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "server_tokens" -> { values.put("server_tokens", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "default_type" -> { values.put("default_type", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "resolver" -> { values.put("resolver", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "gzip" -> { values.put("gzip", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "gzip_min_length" -> { values.put("gzip_min_length", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "gzip_comp_level" -> { values.put("gzip_comp_level", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "gzip_types" -> { values.put("gzip_types", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "gzip_vary" -> { values.put("gzip_vary", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "gzip_proxied" -> { values.put("gzip_proxied", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "ssl_protocols" -> { values.put("ssl_protocols", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "ssl_ciphers" -> { values.put("ssl_ciphers", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "ssl_prefer_server_ciphers" -> { values.put("ssl_prefer_server_ciphers", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "ssl_session_timeout" -> { values.put("ssl_session_timeout", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "ssl_session_cache" -> { values.put("ssl_session_cache", inline.getValue()); anchors.put(key, makeSubAnchor(blockIndex, i, inline)); }
                case "access_log" -> {
                    String val = inline.getValue();
                    String[] parts = val.split("\\s+", 2);
                    values.put("access_log", parts[0]);
                    anchors.put("http.access_log", makeSubAnchor(blockIndex, i, inline));
                    if (parts.length > 1) {
                        values.put("access_log_format", parts[1]);
                        anchors.put("http.access_log_format", makeSubAnchor(blockIndex, i, inline));
                    }
                }
                case "log_format" -> {
                    String val = inline.getValue();
                    String[] parts = val.split("\\s+", 2);
                    if (parts.length > 1) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> formats = (List<Map<String, String>>) values.computeIfAbsent("log_format", k -> new ArrayList<>());
                        Map<String, String> entry = new LinkedHashMap<>();
                        entry.put("name", parts[0]);
                        entry.put("def", stripQuotes(parts[1]));
                        formats.add(entry);
                        anchors.put("http.log_format", makeSubAnchor(blockIndex, i, inline));
                    }
                }
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 整体替换 log_format 列表：删除所有已有 log_format 条目，插入新列表。
     */
    @SuppressWarnings("unchecked")
    private void replaceLogFormats(List<NginxConfItem> items, Object value) {
        List<Map<String, String>> entries;
        if (value instanceof List<?> rawList) {
            entries = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("name", String.valueOf(map.get("name")));
                    entry.put("def", String.valueOf(map.get("def")));
                    entries.add(entry);
                }
            }
        } else {
            throw new NginxException("log_format 值必须是列表");
        }

        // 从后往前删除所有 log_format 条目
        for (int i = items.size() - 1; i >= 0; i--) {
            NginxConfItem item = items.get(i);
            if (item instanceof NginxInlineConfItem inline && "log_format".equals(inline.getName())) {
                items.remove(i);
            }
        }

        // 找到 access_log 的位置，在它之前插入 log_format（nginx 要求 log_format 在 access_log 之前定义）
        int insertPos = items.size();
        for (int i = 0; i < items.size(); i++) {
            NginxConfItem item = items.get(i);
            if (item instanceof NginxInlineConfItem inline && "access_log".equals(inline.getName())) {
                insertPos = i;
                break;
            }
        }

        // 在 access_log 之前插入新条目
        for (Map<String, String> entry : entries) {
            String name = entry.get("name");
            String def = entry.get("def");
            items.add(insertPos, new NginxInlineConfItem("log_format " + name + " '" + def + "';"));
            insertPos++;
        }
    }

    /**
     * 替换或插入 main 级别指令。index=-1 时插入到第一个块之前。
     */
    private void replaceMainItem(List<NginxConfItem> items, int index, String expectedName, String newValue) {
        NginxInlineConfItem newItem = new NginxInlineConfItem(expectedName + " " + newValue + ";");
        if (index == -1) {
            int insertPos = 0;
            for (int i = 0; i < items.size(); i++) {
                if (!(items.get(i) instanceof NginxInlineConfItem)) {
                    insertPos = i;
                    break;
                }
                insertPos = i + 1;
            }
            items.add(insertPos, newItem);
            return;
        }
        replaceExistingItem(items, index, expectedName, newItem);
    }

    /**
     * 替换或插入 sub 级别指令（events/http 块内）。index=-1 时追加到末尾。
     */
    private void replaceItem(List<NginxConfItem> items, int index, String expectedName, String newValue) {
        NginxInlineConfItem newItem = new NginxInlineConfItem(expectedName + " " + newValue + ";");
        if (index == -1) {
            items.add(newItem);
            return;
        }
        replaceExistingItem(items, index, expectedName, newItem);
    }

    private void replaceExistingItem(List<NginxConfItem> items, int index, String expectedName, NginxInlineConfItem newItem) {
        if (index < 0 || index >= items.size()) {
            throw new NginxException("itemIndex 越界: " + index);
        }
        NginxConfItem old = items.get(index);
        if (!(old instanceof NginxInlineConfItem inline)) {
            throw new NginxException("索引 " + index + " 处不是行内指令");
        }
        if (!expectedName.equals(inline.getName())) {
            throw new NginxException("索引 " + index + " 处指令名不匹配，期望: "
                    + expectedName + ", 实际: " + inline.getName());
        }
        items.remove(index);
        items.add(index, newItem);
    }

    private void fillDefaultAnchors(Map<String, Object> anchors, String... keys) {
        for (String key : keys) {
            anchors.putIfAbsent(key, makeAnchor(-1, new NginxInlineConfItem(key + " placeholder;")));
        }
    }

    private String stripPrefix(String path) {
        int dot = path.indexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private Map<String, Object> makeAnchor(int itemIndex, NginxInlineConfItem item) {
        Map<String, Object> anchor = new LinkedHashMap<>();
        anchor.put("itemIndex", itemIndex);
        anchor.put("name", item.getName());
        return anchor;
    }

    private Map<String, Object> makeSubAnchor(int blockIndex, int subIndex, NginxInlineConfItem item) {
        Map<String, Object> anchor = new LinkedHashMap<>();
        anchor.put("blockIndex", blockIndex);
        anchor.put("itemIndex", subIndex);
        anchor.put("name", item.getName());
        return anchor;
    }

    private PathConfig loadPathConfig() {
        return pathConfigRepository.findById(CONFIG_ID).orElseGet(() -> {
            PathConfig defaults = new PathConfig();
            defaults.setId(CONFIG_ID);
            return pathConfigRepository.save(defaults);
        });
    }

    private String readFile(String path) {
        return FileUtil.readFile(path);
    }

    private String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
