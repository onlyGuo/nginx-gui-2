package ink.icoding.nginx.core;

import ink.icoding.nginx.utils.CommandResult;
import ink.icoding.nginx.utils.CommandUtil;
import ink.icoding.nginx.utils.FileUtil;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Nginx 配置管理客户端
 * <p>
 * 通过调用 nginx 命令行工具，实现配置文件的读取、更新、校验和重载。
 * 更新操作遵循「备份 → 写入 → 校验 → 回退/提交」的安全流程。
 */
public class NginxClient {

    private String nginxPath;
    private String configPath;
    private String confDirPath;

    /**
     * 无参构造，供 Spring 自动装配使用。
     * 创建后必须调用 {@link #reinit} 初始化路径。
     */
    public NginxClient() {
    }

    public NginxClient(String nginxPath, String configPath) {
        this(nginxPath, configPath, null);
    }

    public NginxClient(String nginxPath, String configPath, String confDir) {
        reinit(nginxPath, configPath, confDir);
    }

    /**
     * 初始化/重新初始化路径配置。
     *
     * @param nginxPath  nginx 可执行文件路径
     * @param configPath nginx.conf 路径
     * @param confDir    conf.d 目录路径（可选）
     * @throws NginxException 路径无效时抛出
     */
    public void reinit(String nginxPath, String configPath, String confDir) {
        if (!isValidFile(nginxPath)) {
//            throw new NginxException("nginx 可执行文件无效: " + nginxPath);
        }
        if (!isValidFile(configPath)) {
//            throw new NginxException("nginx.conf 文件无效: " + configPath);
        }
        this.nginxPath = nginxPath;
        // SSH 模式下路径属于远程服务器，存储原始字符串，避免本地 Path 转换破坏路径
        // （Windows 的 Paths.get 会加盘符、toString 会用反斜杠）
        if (CommandUtil.isSshEnabled() && !CommandUtil.isLocalNginx()) {
            this.configPath = configPath;
        } else {
            this.configPath = Paths.get(configPath).toAbsolutePath().normalize().toString();
        }
        if (confDir != null && !confDir.isBlank()) {
            if (CommandUtil.isSshEnabled() && !CommandUtil.isLocalNginx()) {
                this.confDirPath = confDir;
            } else {
                this.confDirPath = Paths.get(confDir).toAbsolutePath().normalize().toString();
            }
        } else {
            int lastSlash = Math.max(this.configPath.lastIndexOf('/'), this.configPath.lastIndexOf('\\'));
            this.confDirPath = lastSlash > 0
                    ? this.configPath.substring(0, lastSlash) + "/conf.d"
                    : "conf.d";
        }
    }

    private boolean isValidFile(String path) {
        if (path == null || path.isBlank()) return false;
        return FileUtil.isRegularFile(path);
    }

    // ==================== 主配置文件 ====================

    public String readMainConfig() {
        return readFile(configPath);
    }

    public void updateMainConfig(String content) {
        safeUpdate(configPath, content);
    }

    // ==================== conf.d 目录 ====================

    public List<String> listConfD() {
        if (!FileUtil.isDirectory(confDirPath)) {
            return List.of();
        }
        return FileUtil.listDirectory(confDirPath).stream()
                .map(item -> (String) item.get("name"))
                .filter(name -> name.endsWith(".conf"))
                .sorted()
                .collect(Collectors.toList());
    }

    public String readConfD(String filename) {
        return readFile(confDirPath + "/" + filename);
    }

    public boolean updateConfD(String filename, String content) {
        return safeUpdate(confDirPath + "/" + filename, content);
    }

    public boolean deleteConfD(String filename) {
        String target = confDirPath + "/" + filename;
        if (!FileUtil.exists(target)) {
            throw new NginxException("文件不存在: " + target);
        }
        String backupContent = FileUtil.readFile(target);
        FileUtil.deleteIfExists(target);
        try {
            validateConfig();
            return true;
        } catch (NginxException e) {
            FileUtil.writeFile(target, backupContent);
            throw new NginxException("配置校验失败，已回滚: " + e.getMessage(), e);
        }
    }

    // ==================== 校验与重载 ====================

    public void validateConfig() {
        CommandResult result = CommandUtil.execute(true, nginxPath, "-t", "-c", configPath);
        if (!result.isSuccess()){
            // 输出内容方便用户排查问题
            throw new NginxException("校验失败 (exitCode=" + result.getExitCode() + "): " + result.getStderr() + ", " + result.getErrorMessage());
        }
        if (result.getErrorMessage() != null) {
            throw new NginxException("执行 nginx -t 异常: " + result.getErrorMessage());
        }
    }


    public void reload() {
        CommandResult result = CommandUtil.execute(true, nginxPath, "-s", "reload", "-c", configPath);
        requireSuccess(result, "nginx -s reload");
    }

    public void testAndReload() {
        validateConfig();
        reload();
    }

    public void start() {
        CommandResult result = CommandUtil.execute(true, nginxPath, "-c", configPath);
        requireSuccess(result, "nginx");
    }

    public void stop() {
        CommandResult result = CommandUtil.execute(true, nginxPath, "-s", "stop", "-c", configPath);
        requireSuccess(result, "nginx -s stop");
    }

    public String version() {
        CommandResult result = CommandUtil.execute(true, nginxPath, "-v");
        if (result.getErrorMessage() != null) {
            throw new NginxException("获取 nginx 版本失败: " + result.getErrorMessage());
        }
        String output = result.getStderr().isEmpty() ? result.getStdout() : result.getStderr();
        return output.trim();
    }

    // ==================== 访问器 ====================

    public String getNginxPath() {
        return nginxPath;
    }

    public String getConfigPath() {
        return configPath;
    }

    public String getConfDirPath() {
        return confDirPath;
    }

    public boolean isInitialized() {
        return nginxPath != null && configPath != null;
    }

    // ==================== 内部方法 ====================

    private void requireSuccess(CommandResult result, String command) {
        if (result.getErrorMessage() != null) {
            throw new NginxException("执行命令异常 [" + command + "]: " + result.getErrorMessage());
        }
        if (!result.isSuccess()) {
            throw new NginxException("命令执行失败 [" + command + "] (exitCode=" + result.getExitCode() + "): " + result.getStderr());
        }
    }

    private boolean safeUpdate(String target, String content) {
        int lastSlash = Math.max(target.lastIndexOf('/'), target.lastIndexOf('\\'));
        String parentDir = lastSlash > 0 ? target.substring(0, lastSlash) : ".";
        FileUtil.createDirectories(parentDir);

        boolean hasBackup = false;
        String backupContent = null;

        if (FileUtil.exists(target)) {
            backupContent = FileUtil.readFile(target);
            hasBackup = true;
        }

        FileUtil.writeFile(target, content);

        try {
            validateConfig();
            return true;
        } catch (NginxException e) {
            if (hasBackup) {
                FileUtil.writeFile(target, backupContent);
            } else {
                FileUtil.deleteIfExists(target);
            }
            throw new NginxException("配置校验失败，已回滚: " + e.getMessage(), e);
        }
    }

    private String readFile(String path) {
        if (!FileUtil.exists(path)) {
            throw new NginxException("文件不存在: " + path);
        }
        return FileUtil.readFile(path);
    }

}
