package ink.icoding.nginx.web;

import ink.icoding.nginx.core.NginxClient.BadRequestException;
import ink.icoding.nginx.core.NginxClient.NotFoundException;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam String path,
                                                  @RequestParam(defaultValue = "file") String type) {
        Path normalized = Paths.get(path).normalize().toAbsolutePath();
        File dir = normalized.toFile();

        if (!dir.exists()) {
            throw new NotFoundException("目录不存在: " + path);
        }
        if (!dir.isDirectory()) {
            throw new BadRequestException("不是目录: " + path);
        }

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
                if (f.isFile()) {
                    item.put("size", formatSize(f.length()));
                }
                items.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", normalized.toString());
        result.put("items", items);
        result.put("parent", normalized.getParent() != null ? normalized.getParent().toString() : null);
        return ApiResponse.ok(result);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
