package ink.icoding.nginx.utils;

import ink.icoding.nginx.utils.CommandUtil.CommandResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
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
    static Map<String, String> fileCache = new HashMap<>();
    public static String readFile(String path) {
        if (fileCache.containsKey(path)) {
            return fileCache.get(path);
        }
        synchronized (FileUtil.class) {
             // 读取文件时加锁，避免 SSH 模式下的并发问题（如多个线程同时写入导致读取失败）
            if (CommandUtil.isSshEnabled()) {
                CommandResult r = CommandUtil.execute("cat " + path);
                if (!r.isSuccess()) {
                    throw new RuntimeException("读取文件失败: " + path);
                }
                fileCache.put(path, r.getStdout());
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
            // 用 heredoc 写入，避免转义问题
            synchronized (FileUtil.class) {
                // SSH 模式下的写入在某些环境（如 BusyBox）可能存在并发问题，添加同步锁
                String escaped = content.replace("\\", "\\\\").replace("'", "'\\''");
                CommandResult r = CommandUtil.execute("cat > " + path + " << 'NGINX_GUI_EOF'\n" + content + "\nNGINX_GUI_EOF");
                if (!r.isSuccess()) {
                    throw new RuntimeException("写入文件失败: " + path + ": " + r.getStderr());
                }
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
            synchronized (FileUtil.class) {
                // SSH 模式下的 test -e 在某些环境（如 BusyBox）可能存在并发问题，添加同步锁
                return CommandUtil.execute("test -e " + path).isSuccess();
            }
        }
        return Files.exists(Path.of(path));
    }

    public static boolean isRegularFile(String path) {
        if (CommandUtil.isSshEnabled()) {
            synchronized (FileUtil.class) {
                // SSH 模式下的 test -f 在某些环境（如 BusyBox）可能存在并发问题，添加同步锁
                return CommandUtil.execute("test -f " + path).isSuccess();
            }
        }
        return Files.isRegularFile(Path.of(path));
    }

    public static boolean isDirectory(String path) {
        if (CommandUtil.isSshEnabled()) {
            return CommandUtil.execute("test -d " + path).isSuccess();
        }
        return Files.isDirectory(Path.of(path));
    }

    // ==================== 目录操作 ====================

    public static void createDirectories(String path) {
        if (CommandUtil.isSshEnabled()) {
            CommandResult r = CommandUtil.execute("mkdir -p " + path);
            if (!r.isSuccess()) {
                throw new RuntimeException("创建目录失败: " + path);
            }
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
            CommandUtil.execute("rm -f " + path);
            return true;
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
            String flag = replaceExisting ? "-f" : "-n";
            CommandResult r = CommandUtil.execute("mv " + flag + " " + source + " " + target);
            if (!r.isSuccess()) {
                throw new RuntimeException("移动文件失败: " + source + " → " + target);
            }
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
        List<Map<String, Object>> items = new ArrayList<>();
        CommandResult r = CommandUtil.execute("LC_ALL=C ls -la " + dirPath);
        if (!r.isSuccess()) return items;

        for (String line : r.getStdout().split("\n")) {
            if (line.isEmpty() || line.startsWith("total") || !line.matches("[d\\-].*")) continue;

            boolean isDir = line.startsWith("d");
            Matcher m = LS_DATE_PATTERN.matcher(line);
            if (!m.find()) continue;
            String name = line.substring(m.end());
            if (name.equals(".") || name.equals("..") || name.startsWith(".")) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("dir", isDir);

            String meta = line.substring(0, m.start());
            String[] parts = meta.trim().split("\\s+");
            if (parts.length >= 5 && !isDir) {
                try {
                    item.put("size", Long.parseLong(parts[4]));
                } catch (NumberFormatException ignored) {
                }
            }
            items.add(item);
        }
        items.sort((a, b) -> {
            boolean aDir = (Boolean) a.get("dir");
            boolean bDir = (Boolean) b.get("dir");
            if (aDir != bDir) return aDir ? -1 : 1;
            return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });
        return items;
    }
}
