package ink.icoding.nginx.web;

import ink.icoding.nginx.config.PathConfig;
import ink.icoding.nginx.config.PathConfigRepository;
import ink.icoding.nginx.core.NginxClient;
import ink.icoding.nginx.core.NginxConfig;
import ink.icoding.nginx.entity.*;
import ink.icoding.nginx.utils.FileUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/paths")
public class PathConfigController {

    private static final Long CONFIG_ID = 1L;

    private final NginxClient client;

    private final PathConfigRepository repository;

    public PathConfigController(PathConfigRepository repository, NginxClient client) {
        this.repository = repository;
        this.client = client;
    }

    @GetMapping
    public ApiResponse<PathConfig> get() {
        return ApiResponse.ok(loadConfig());
    }

    @PostConstruct
    protected void init() {
        PathConfig config = loadConfig();
        client.reinit(config.getNginxBin(), config.getNginxConf(), config.getConfDir());
        initConfD(config);
    }

    @GetMapping("/validate")
    public ApiResponse<Map<String, Object>> validate() {
        PathConfig config = loadConfig();
        boolean binValid = isValidFile(config.getNginxBin());
        boolean confValid = isValidFile(config.getNginxConf());
        if (!confValid){
            System.out.println("nginxConf invalid: " + config.getNginxConf());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nginxBin", binValid);
        result.put("nginxConf", confValid);
        result.put("valid", binValid && confValid);
        return ApiResponse.ok(result);
    }

    @PutMapping
    public ApiResponse<Void> update(@RequestBody PathConfig config) {
        PathConfig existing = loadConfig();
        existing.setNginxBin(config.getNginxBin());
        existing.setNginxConf(config.getNginxConf());
        existing.setConfDir(config.getConfDir());

        client.reinit(existing.getNginxBin(), existing.getNginxConf(), existing.getConfDir());
        client.validateConfig();
        repository.save(existing);
        return ApiResponse.ok();
    }

    // ==================== initConfD ====================

    private void initConfD(PathConfig config) {
        String confPath = config.getNginxConf();
        Path confFilePath = Path.of(confPath);
        if (!isValidFile(confPath)) {
            return;
        }
        String content = readFile(confPath);
        NginxConfig nginxConfig = NginxConfig.parse(content);
        NginxHttpConfItem httpBlock = null;
        for (NginxConfItem item : nginxConfig.getItems()) {
            if (item instanceof NginxHttpConfItem http) {
                httpBlock = http;
                break;
            }
        }
        if (httpBlock == null) {
            httpBlock = new NginxHttpConfItem("http {\n}");
            nginxConfig.getItems().add(httpBlock);
        }

        String confDir = config.getConfDir();
        if (confDir == null || confDir.isBlank()) {
            confDir = confFilePath.getParent().resolve("conf.d").toString();
        }
        if (!confDir.endsWith("/")){
            confDir += "/";
        }
        String includePattern = confDir + "*.conf";
        String includeAbsPath = (StringUtils.hasText(config.getConfDir()) ? confDir : "conf.d");
        if (!includeAbsPath.endsWith("/")) {
            includeAbsPath += "/";
        }
        includeAbsPath += "*.conf";

        List<NginxConfItem> subItems = httpBlock.listSubItems();
        for (NginxConfItem sub : subItems) {
            if (sub instanceof NginxInlineConfItem inline
                    && "include".equals(inline.getName())
                    && inline.getValue().equals(includePattern)) {
                return;
            }
            if (sub instanceof NginxInlineConfItem inline2
                    && "include".equals(inline2.getName())
                    && inline2.getValue().equals(includeAbsPath)) {
                return;
            }
        }

        if (!FileUtil.isDirectory(confDir)) {
            FileUtil.createDirectories(confDir);
        }

        subItems.add(new NginxInlineConfItem("#Nginx GUI Auto Insert: Include conf.d/*.conf"));
        subItems.add(new NginxInlineConfItem("include " + includePattern + ";"));

        // 使用单例 NginxClient 走安全更新流程
        client.updateMainConfig(nginxConfig.toString());

        config.setConfDir(confDir);
        repository.save(config);

    }

    // ==================== 内部方法 ====================

    private PathConfig loadConfig() {
        return repository.findById(CONFIG_ID).orElseGet(() -> {
            PathConfig defaults = new PathConfig();
            defaults.setId(CONFIG_ID);
            return repository.save(defaults);
        });
    }

    private boolean isValidFile(String path) {
        if (path == null || path.isBlank()) return false;
        return FileUtil.isRegularFile(path);
    }

    private String readFile(String path) {
        return FileUtil.readFile(path);
    }
}
