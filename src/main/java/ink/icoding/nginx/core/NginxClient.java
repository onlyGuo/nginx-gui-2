package ink.icoding.nginx.core;

import ink.icoding.nginx.utils.CommandUtil;
import ink.icoding.nginx.utils.CommandUtil.CommandResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Nginx 配置管理客户端
 * <p>
 * 通过调用 nginx 命令行工具，实现配置文件的读取、更新、校验和重载。
 * 更新操作遵循「备份 → 写入 → 校验 → 回退/提交」的安全流程。
 */
public class NginxClient {

    private String nginxPath;
    private Path configPath;
    private Path confDirPath;

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
        this.configPath = Paths.get(configPath).toAbsolutePath().normalize();
        if (confDir != null && !confDir.isBlank()) {
            this.confDirPath = Paths.get(confDir).toAbsolutePath().normalize();
        } else {
            this.confDirPath = this.configPath.getParent().resolve("conf.d");
        }
    }

    private boolean isValidFile(String path) {
        if (path == null || path.isBlank()) return false;
        return Files.exists(Path.of(path)) && Files.isRegularFile(Path.of(path));
    }

    // ==================== 主配置文件 ====================

    public String readMainConfig() {
        return readFile(configPath);
    }

    public void updateMainConfig(String content) {
        if (!safeUpdate(configPath, content)) {
            throw new NginxException("配置校验失败，已回滚");
        }
    }

    // ==================== conf.d 目录 ====================

    public List<String> listConfD() {
        if (!Files.isDirectory(confDirPath)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(confDirPath)) {
            return stream
                    .filter(p -> p.toString().endsWith(".conf"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new NginxException("列出 conf.d 目录失败: " + e.getMessage(), e);
        }
    }

    public String readConfD(String filename) {
        return readFile(confDirPath.resolve(filename));
    }

    public boolean updateConfD(String filename, String content) {
        return safeUpdate(confDirPath.resolve(filename), content);
    }

    public boolean deleteConfD(String filename) {
        Path target = confDirPath.resolve(filename);
        if (!Files.exists(target)) {
            throw new NginxException("文件不存在: " + target);
        }
        Path backup = backupPath(target);
        try {
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new NginxException("备份文件失败: " + e.getMessage(), e);
        }
        try {
            Files.delete(target);
        } catch (IOException e) {
            silentDelete(backup);
            throw new NginxException("删除文件失败: " + e.getMessage(), e);
        }
        try {
            validateConfig();
            silentDelete(backup);
            return true;
        }catch (NginxException e) {
            //  删除后校验失败，尝试回退
            try {
                Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new NginxException("校验失败且回退文件失败，请手动恢复: " + target
                        + "，备份位于: " + backup, ex);
            }
            return false;
        }
    }

    // ==================== 校验与重载 ====================

    public void validateConfig() {
        CommandResult result = CommandUtil.execute(nginxPath, "-t", "-c", configPath.toString());
        if (!result.isSuccess()){
            throw new NginxException("校验失败 (exitCode=" + result.getExitCode() + "): " + result.getStderr() + ", " + result.getErrorMessage());
        }
        if (result.getErrorMessage() != null) {
            throw new NginxException("执行 nginx -t 异常: " + result.getErrorMessage());
        }
    }


    public void reload() {
        CommandResult result = CommandUtil.execute(nginxPath, "-s", "reload", "-c", configPath.toString());
        requireSuccess(result, "nginx -s reload");
    }

    public void testAndReload() {
        validateConfig();
        reload();
    }

    public void start() {
        CommandResult result = CommandUtil.execute(nginxPath, "-c", configPath.toString());
        requireSuccess(result, "nginx");
    }

    public void stop() {
        CommandResult result = CommandUtil.execute(nginxPath, "-s", "stop", "-c", configPath.toString());
        requireSuccess(result, "nginx -s stop");
    }

    public String version() {
        CommandResult result = CommandUtil.execute(nginxPath, "-v");
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

    public Path getConfigPath() {
        return configPath;
    }

    public Path getConfDirPath() {
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

    private boolean safeUpdate(Path target, String content) {
        ensureParentDir(target);

        Path backup = backupPath(target);
        boolean hasBackup = false;

        if (Files.exists(target)) {
            try {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
                hasBackup = true;
            } catch (IOException e) {
                throw new NginxException("备份文件失败: " + target, e);
            }
        }

        try {
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            silentDelete(backup);
            throw new NginxException("写入文件失败: " + target, e);
        }

        try {
            validateConfig();
            silentDelete(backup);
            return true;
        } catch (NginxException e) {
            if (hasBackup) {
                try {
                    Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e1) {
                    throw new NginxException("校验失败且回退文件失败，请手动恢复: " + target
                            + "，备份位于: " + backup, e1);
                }
            } else {
                silentDelete(target);
            }
            return false;
        }
    }

    private String readFile(Path path) {
        if (!Files.exists(path)) {
            throw new NginxException("文件不存在: " + path);
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NginxException("读取文件失败: " + path, e);
        }
    }

    private void ensureParentDir(Path file) {
        Path parent = file.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new NginxException("创建目录失败: " + parent, e);
            }
        }
    }

    private Path backupPath(Path original) {
        return original.getParent().resolve(original.getFileName() + ".bak");
    }

    private void silentDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    // ==================== 异常类 ====================

    public static class NginxException extends RuntimeException {
        public NginxException(String message) {
            super(message);
        }

        public NginxException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
