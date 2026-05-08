package ink.icoding.nginx.utils;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import ink.icoding.nginx.config.SshSessionManager;
import ink.icoding.nginx.utils.CommandUtil.CommandResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SSH 感知的文件操作工具类。
 * <p>
 * SSH 启用时通过远程命令执行，否则使用本地 Java NIO。
 */
public final class FileUtil {

    // 匹配 C locale 下 ls -la 的日期: "Mon DD HH:MM" 或 "Mon DD  YYYY"
    private static final Pattern LS_DATE_PATTERN = Pattern.compile(
            "[A-Z][a-z]{2}\\s+\\d{1,2}\\s+(\\d{2}:\\d{2}|\\d{4})\\s+");

    private FileUtil() {
    }

    // ==================== 读取 ====================
    static Map<String, String> fileCache = new ConcurrentHashMap<>();
    public static String readFile(String path) {
        if (fileCache.containsKey(path)) {
            return fileCache.get(path);
        }
        synchronized (FileUtil.class) {
             // 读取文件时加锁，避免 SSH 模式下的并发问题（如多个线程同时写入导致读取失败）
            if (CommandUtil.isSshEnabled()) {
                String content = withSftp("读取文件: " + path, channel -> {
                    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        channel.get(path, out);
                        return out.toString(StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException("读取文件失败: " + path, e);
                    }
                });
                fileCache.put(path, content);
            }else{
                try {
                    fileCache.put(path, Files.readString(Path.of(path), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException("读取文件失败: " + path, e);
                }
            }
            return fileCache.get(path);
        }
    }

    // ==================== 写入 ====================

    public static void writeFile(String path, String content) {
        if (CommandUtil.isSshEnabled()) {
            synchronized (FileUtil.class) {
                createDirectories(parentOf(path));
                withSftp("写入文件: " + path, channel -> {
                    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                    channel.put(new ByteArrayInputStream(bytes), path, ChannelSftp.OVERWRITE);
                    return null;
                });
                fileCache.put(path, content);
                return;
            }
        }
        try {
            Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("写入文件失败: " + path, e);
        }
        fileCache.put(path, content);
    }

    // ==================== 存在性 / 类型判断 ====================

    public static boolean exists(String path) {
        if (CommandUtil.isSshEnabled()) {
            return withSftp("检查存在: " + path, channel -> statOrNull(channel, path) != null);
        }
        return Files.exists(Path.of(path));
    }

    public static boolean isRegularFile(String path) {
        if (CommandUtil.isSshEnabled()) {
            return withSftp("检查普通文件: " + path, channel -> {
                SftpATTRS attrs = statOrNull(channel, path);
                return attrs != null && !attrs.isDir();
            });
        }
        return Files.isRegularFile(Path.of(path));
    }

    public static boolean isDirectory(String path) {
        if (CommandUtil.isSshEnabled()) {
            return withSftp("检查目录: " + path, channel -> {
                SftpATTRS attrs = statOrNull(channel, path);
                return attrs != null && attrs.isDir();
            });
        }
        return Files.isDirectory(Path.of(path));
    }

    // ==================== 目录操作 ====================

    public static void createDirectories(String path) {
        if (CommandUtil.isSshEnabled()) {
            withSftp("创建目录: " + path, channel -> {
                createDirectoriesRemote(channel, path);
                return null;
            });
            return;
        }
        try {
            Files.createDirectories(Path.of(path));
        } catch (IOException e) {
            throw new RuntimeException("创建目录失败: " + path, e);
        }
    }

    // ==================== 删除 ====================

    public static boolean deleteIfExists(String path) {
        if (CommandUtil.isSshEnabled()) {
            invalidateCache(path);
            return withSftp("删除路径: " + path, channel -> {
                SftpATTRS attrs = statOrNull(channel, path);
                if (attrs == null) {
                    return false;
                }
                if (attrs.isDir()) {
                    channel.rmdir(path);
                } else {
                    channel.rm(path);
                }
                return true;
            });
        }
        try {
            return Files.deleteIfExists(Path.of(path));
        } catch (IOException e) {
            return false;
        }
    }

    // ==================== 复制 / 移动 ====================

    public static void copy(String source, String target, boolean replaceExisting) {
        if (CommandUtil.isSshEnabled()) {
            String flag = replaceExisting ? "-f" : "-n";
            CommandResult r = CommandUtil.execute("cp " + flag + " " + source + " " + target);
            if (!r.isSuccess()) {
                throw new RuntimeException("复制文件失败: " + source + " → " + target);
            }
            return;
        }
        try {
            CopyOption[] opts = replaceExisting
                    ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING}
                    : new CopyOption[0];
            Files.copy(Path.of(source), Path.of(target), opts);
        } catch (IOException e) {
            throw new RuntimeException("复制文件失败: " + source + " → " + target, e);
        }
    }

    public static void move(String source, String target, boolean replaceExisting) {
        if (CommandUtil.isSshEnabled()) {
            withSftp("移动路径: " + source + " → " + target, channel -> {
                SftpATTRS sourceAttrs = statOrNull(channel, source);
                if (sourceAttrs == null) {
                    throw new RuntimeException("移动文件失败: 源文件不存在: " + source);
                }
                SftpATTRS targetAttrs = statOrNull(channel, target);
                if (targetAttrs != null) {
                    if (!replaceExisting) {
                        throw new RuntimeException("移动文件失败: 目标已存在: " + target);
                    }
                    if (targetAttrs.isDir()) {
                        channel.rmdir(target);
                    } else {
                        channel.rm(target);
                    }
                } else {
                    createDirectoriesRemote(channel, parentOf(target));
                }
                channel.rename(source, target);
                return null;
            });
            invalidateCache(source, target);
            return;
        }
        try {
            CopyOption[] opts = replaceExisting
                    ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING}
                    : new CopyOption[]{StandardCopyOption.ATOMIC_MOVE};
            Files.move(Path.of(source), Path.of(target), opts);
        } catch (IOException e) {
            throw new RuntimeException("移动文件失败: " + source + " → " + target, e);
        }
    }

    // ==================== 目录列表 ====================

    public static List<Map<String, Object>> listDirectory(String dirPath) {
        if (CommandUtil.isSshEnabled()) {
            return listDirectoryRemote(dirPath);
        }
        return listDirectoryLocal(dirPath);
    }

    private static List<Map<String, Object>> listDirectoryLocal(String dirPath) {
        List<Map<String, Object>> items = new ArrayList<>();
        try (Stream<Path> stream = Files.list(Path.of(dirPath))) {
            stream.sorted().forEach(p -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", p.getFileName().toString());
                item.put("dir", Files.isDirectory(p));
                try {
                    item.put("time", Files.getLastModifiedTime(p).toMillis());
                } catch (IOException ignored) {
                }
                if (Files.isRegularFile(p)) {
                    try {
                        item.put("size", Files.size(p));
                    } catch (IOException ignored) {
                    }
                }
                items.add(item);
            });
        } catch (IOException e) {
            throw new RuntimeException("列出目录失败: " + dirPath, e);
        }
        return items;
    }

    private static List<Map<String, Object>> listDirectoryRemote(String dirPath) {
        List<Map<String, Object>> items = withSftp("列出目录: " + dirPath, channel -> {
            List<Map<String, Object>> result = new ArrayList<>();
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channel.ls(dirPath);
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (name.equals(".") || name.equals("..") || name.startsWith(".")) continue;

                SftpATTRS attrs = entry.getAttrs();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", name);
                item.put("dir", attrs.isDir());
                item.put("time", attrs.getMTime() * 1000L);
                if (!attrs.isDir()) {
                    item.put("size", attrs.getSize());
                }
                result.add(item);
            }
            return result;
        });
        items.sort((a, b) -> {
            boolean aDir = (Boolean) a.get("dir");
            boolean bDir = (Boolean) b.get("dir");
            if (aDir != bDir) return aDir ? -1 : 1;
            return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });
        return items;
    }

    private static <T> T withSftp(String action, SshSessionManager.SftpCallback<T> callback) {
        SshSessionManager manager = CommandUtil.getSshSessionManager();
        if (manager == null) {
            throw new IllegalStateException("SSH 未启用，无法执行 SFTP 操作: " + action);
        }
        return manager.executeSftp(action, callback);
    }

    private static SftpATTRS statOrNull(ChannelSftp channel, String path) throws SftpException {
        try {
            return channel.stat(path);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return null;
            }
            throw e;
        }
    }

    private static void createDirectoriesRemote(ChannelSftp channel, String path) throws SftpException {
        String normalized = normalizeRemotePath(path);
        if (normalized.isEmpty() || ".".equals(normalized) || "/".equals(normalized)) {
            return;
        }

        boolean absolute = normalized.startsWith("/");
        String[] parts = normalized.split("/+");
        StringBuilder current = new StringBuilder();
        if (absolute) {
            current.append('/');
        }

        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (current.length() > 0 && current.charAt(current.length() - 1) != '/') {
                current.append('/');
            }
            current.append(part);
            String currentPath = current.toString();
            SftpATTRS attrs = statOrNull(channel, currentPath);
            if (attrs == null) {
                channel.mkdir(currentPath);
            } else if (!attrs.isDir()) {
                throw new RuntimeException("创建目录失败，存在同名文件: " + currentPath);
            }
        }
    }

    private static String normalizeRemotePath(String path) {
        return path == null ? "" : path.replace('\\', '/').trim();
    }

    private static String parentOf(String path) {
        if (path == null || path.isBlank()) return ".";
        String normalized = normalizeRemotePath(path);
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash < 0) return ".";
        if (lastSlash == 0) return "/";
        return normalized.substring(0, lastSlash);
    }

    private static void invalidateCache(String... paths) {
        for (String path : paths) {
            if (path != null) {
                fileCache.remove(path);
            }
        }
    }
}
