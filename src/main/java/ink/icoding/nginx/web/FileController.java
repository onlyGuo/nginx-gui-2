package ink.icoding.nginx.web;

import ink.icoding.nginx.core.BadRequestException;
import ink.icoding.nginx.core.NotFoundException;
import ink.icoding.nginx.utils.CommandResult;
import ink.icoding.nginx.utils.CommandUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam String path,
                                                  @RequestParam(defaultValue = "file") String type) {
        // SSH 模式下路径属于远程服务器，不能经过本地 Path 解析（Windows 会加盘符、去前导 /）
        if (CommandUtil.isSshEnabled() && !CommandUtil.isLocalNginx()) {
            return ApiResponse.ok(listRemote(path, type));
        }

        Path normalized = Paths.get(path).normalize().toAbsolutePath();
        return ApiResponse.ok(listLocal(normalized, type));
    }

    private Map<String, Object> listLocal(Path normalized, String type) {
        File dir = normalized.toFile();
        if (!dir.exists()) throw new NotFoundException("目录不存在: " + normalized);
        if (!dir.isDirectory()) throw new BadRequestException("不是目录: " + normalized);

        File[] files = dir.listFiles();
        List<Map<String, Object>> items = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : files) {
                if (f.isHidden()) continue;
                if ("dir".equals(type) && !f.isDirectory()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", f.getName());
                item.put("dir", f.isDirectory());
                if (f.isFile()) item.put("size", formatSize(f.length()));
                items.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", normalized.toString());
        result.put("items", items);
        result.put("parent", normalized.getParent() != null ? normalized.getParent().toString() : null);
        return result;
    }

    private Map<String, Object> listRemote(String absPath, String type) {
        // 检查路径是否存在且是目录
        CommandResult check = CommandUtil.execute("test -d " + absPath + " && echo OK");
        if (!check.isSuccess() || !check.getStdout().trim().equals("OK")) {
            CommandResult exists = CommandUtil.execute("test -e " + absPath + " && echo E");
            if (!exists.isSuccess() || !exists.getStdout().trim().equals("E")) {
                throw new NotFoundException("目录不存在: " + absPath);
            }
            throw new BadRequestException("不是目录: " + absPath);
        }

        // 用 ls -la 获取文件信息（文件名、类型、大小）
        CommandResult r = CommandUtil.execute("LC_ALL=C ls -la " + absPath);
        if (!r.isSuccess()) throw new BadRequestException("无法读取目录: " + absPath);

        List<Map<String, Object>> items = parseLsOutput(r.getStdout(), type);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", absPath);
        result.put("items", items);
        int lastSlash = absPath.lastIndexOf('/');
        result.put("parent", lastSlash > 0 ? absPath.substring(0, lastSlash) : "/");
        return result;
    }

    // 匹配 C locale 下 ls -la 的日期: "Mon DD HH:MM" 或 "Mon DD  YYYY"
    private static final Pattern LS_DATE_PATTERN = Pattern.compile(
            "[A-Z][a-z]{2}\\s+\\d{1,2}\\s+(\\d{2}:\\d{2}|\\d{4})\\s+");

    private List<Map<String, Object>> parseLsOutput(String output, String type) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String line : output.split("\n")) {
            if (line.isEmpty() || line.startsWith("total")) continue;
            if (!line.matches("[d\\-].*")) continue;

            boolean isDir = line.startsWith("d");
            if ("dir".equals(type) && !isDir) continue;

            // 通过日期模式定位文件名位置（跨平台兼容）
            Matcher m = LS_DATE_PATTERN.matcher(line);
            if (!m.find()) continue;
            String name = line.substring(m.end());
            if (name.equals(".") || name.equals("..") || name.startsWith(".")) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("dir", isDir);

            if (!isDir) {
                String meta = line.substring(0, m.start());
                String[] parts = meta.trim().split("\\s+");
                // permissions links owner group size
                if (parts.length >= 5) {
                    try {
                        item.put("size", formatSize(Long.parseLong(parts[4])));
                    } catch (NumberFormatException ignored) {
                    }
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

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
