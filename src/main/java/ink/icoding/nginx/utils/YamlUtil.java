package ink.icoding.nginx.utils;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

public final class YamlUtil {

    private static final Yaml LOAD_YAML;
    private static final Yaml SAVE_YAML;

    static {
        // 加载用：SafeConstructor 忽略所有类标签
        LOAD_YAML = new Yaml(new SafeConstructor(new LoaderOptions()));

        // 保存用：标准 Yaml，只 dump Map，不会写类标签
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setIndent(2);
        SAVE_YAML = new Yaml(opts);
    }

    private YamlUtil() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> load(String path) {
        Path p = Paths.get(path);
        if (!Files.exists(p)) {
            return new LinkedHashMap<>();
        }
        try (InputStream is = Files.newInputStream(p)) {
            Object obj = LOAD_YAML.load(is);
            if (obj instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) obj);
            }
            return new LinkedHashMap<>();
        } catch (IOException e) {
            throw new RuntimeException("读取 YAML 文件失败: " + path, e);
        }
    }

    public static void save(String path, Object data) {
        Path p = Paths.get(path);
        try {
            Path parent = p.getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                Files.createDirectories(parent);
            }
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                SAVE_YAML.dump(data, w);
            }
        } catch (IOException e) {
            throw new RuntimeException("写入 YAML 文件失败: " + path, e);
        }
    }

    public static void saveIfAbsent(String path, Object defaultData) {
        if (!Files.exists(Paths.get(path))) {
            save(path, defaultData);
        }
    }
}
