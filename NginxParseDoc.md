[English](README.md) | 中文

# Nginx Config Analysis

一个强大的 Nginx 配置文件解析库，可以将 Nginx 配置文件解析为 Java 对象，支持读取、修改和重新序列化。

[![Maven Central](https://img.shields.io/maven-central/v/ink.icoding/nginx-analysis.svg)](https://central.sonatype.com/artifact/ink.icoding/nginx-analysis)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

## 特性

- 完整解析 Nginx 配置文件所有元素
- 支持所有 Nginx 块类型（http, server, location, upstream 等）
- 保留原始格式（注释、空行、缩进）
- 支持修改配置后重新序列化
- 类型安全的 API，针对不同块类型提供专用方法
- 零依赖，纯 Java 实现

## 环境要求

- Java 17 或更高版本

## 安装

### Maven

```xml
<dependency>
    <groupId>ink.icoding</groupId>
    <artifactId>nginx-analysis</artifactId>
    <version>1.0.2</version>
</dependency>
```

### Gradle

```groovy
implementation 'ink.icoding:nginx-analysis:1.0.2'
```

## 快速开始

### 1. 解析配置文件

```java
import ink.icoding.nginx.core.NginxConfig;
import ink.icoding.nginx.entity.*;

// 从字符串解析
String nginxConf = """
    http {
        server {
            listen 80;
            server_name example.com;
            location / {
                root /var/www/html;
            }
        }
    }
    """;
NginxConfig config = NginxConfig.parse(nginxConf);

// 从文件解析
String content = Files.readString(Path.of("/etc/nginx/nginx.conf"));
NginxConfig config = NginxConfig.parse(content);
```

### 2. 遍历配置项

```java
for (NginxConfItem item : config.getItems()) {
    System.out.println(item.getName());
}
```

### 3. 序列化回字符串

```java
String output = config.toString();
System.out.println(output);
```

## 实体类结构

```
NginxConfItem (接口)
├── NginxInlineConfItem      # 行内配置 (listen 80;)
├── NginxCommentsConfItem    # 注释 (# comment)
├── NginxEmptyLineConfItem   # 空行
└── NginxBlockConfItem       # 块配置 (父类)
    ├── NginxHttpConfItem    # http { ... }
    ├── NginxServerConfItem  # server { ... }
    ├── NginxLocationConfItem # location /path { ... }
    ├── NginxUpstreamConfItem # upstream backend { ... }
    ├── NginxEventsConfItem  # events { ... }
    ├── NginxStreamConfItem  # stream { ... }
    ├── NginxMapConfItem     # map $var $name { ... }
    ├── NginxGeoConfItem     # geo $var { ... }
    ├── NginxIfConfItem      # if ($condition) { ... }
    ├── NginxTypesConfItem   # types { ... }
    └── NginxLimitExceptConfItem # limit_except GET { ... }
```

## 使用示例

### 示例 1：获取所有 Server 配置

```java
NginxConfig config = NginxConfig.parse(nginxConf);

// 遍历顶级配置
for (NginxConfItem item : config.getItems()) {
    if (item instanceof NginxHttpConfItem) {
        NginxHttpConfItem http = (NginxHttpConfItem) item;

        // 获取所有 server
        for (NginxServerConfItem server : http.getServers()) {
            System.out.println("Server: " + server.getServerNames());
            System.out.println("Ports: " + server.getListenPorts());
            System.out.println("SSL: " + server.isSslEnabled());
        }
    }
}
```

### 示例 2：修改 Server 配置

```java
NginxConfig config = NginxConfig.parse(nginxConf);

// 获取第一个 http 块
NginxHttpConfItem http = (NginxHttpConfItem) config.getItems().get(0);

// 获取第一个 server
NginxServerConfItem server = http.getServers().get(0);

// 修改 server_name
server.setServerNames("new-domain.com", "www.new-domain.com");

// 添加新的 listen 端口
server.addListenPort("8080");

// 输出修改后的配置
System.out.println(config.toString());
```

### 示例 3：处理 Location 配置

```java
NginxServerConfItem server = ...;

// 获取所有 location
for (NginxLocationConfItem location : server.getLocations()) {
    System.out.println("Path: " + location.getPath());
    System.out.println("Modifier: " + location.getModifier());
    System.out.println("Is Regex: " + location.isRegex());
    System.out.println("Is Exact: " + location.isExact());
}
```

### 示例 4：配置 Upstream

```java
NginxHttpConfItem http = ...;

// 获取所有 upstream
for (NginxUpstreamConfItem upstream : http.getUpstreams()) {
    System.out.println("Name: " + upstream.getName());
    System.out.println("Method: " + upstream.getLoadBalancingMethod());
    System.out.println("Servers: " + upstream.getServerAddresses());
}

// 创建新的 upstream
NginxUpstreamConfItem newUpstream = new NginxUpstreamConfItem("upstream new_backend {\n}");
newUpstream.addServer("192.168.1.10:8080", "weight=3");
newUpstream.addServer("192.168.1.11:8080");
newUpstream.setLoadBalancingMethod("least_conn");
http.addItem(newUpstream);
```

### 示例 5：使用 Map 配置

```java
NginxHttpConfItem http = ...;

for (NginxConfItem item : http.getItems()) {
    if (item instanceof NginxMapConfItem) {
        NginxMapConfItem map = (NginxMapConfItem) item;
        System.out.println("Source: " + map.getSourceVariable());
        System.out.println("Target: " + map.getTargetVariable());
        System.out.println("Entries: " + map.getMapEntries());
    }
}
```

### 示例 6：条件判断 (if)

```java
NginxServerConfItem server = ...;

for (NginxConfItem item : server.listSubItems()) {
    if (item instanceof NginxIfConfItem) {
        NginxIfConfItem ifBlock = (NginxIfConfItem) item;
        System.out.println("Condition: " + ifBlock.getCondition());
        System.out.println("Variable: " + ifBlock.getConditionVariable());
        System.out.println("Operator: " + ifBlock.getConditionOperator());
        System.out.println("Value: " + ifBlock.getConditionValue());
    }
}
```

### 示例 7：查找特定配置项

```java
NginxBlockConfItem block = ...;

// 查找单个配置项
NginxConfItem listen = block.getItem("listen");
if (listen instanceof NginxInlineConfItem) {
    System.out.println("Listen: " + ((NginxInlineConfItem) listen).getValue());
}

// 查找所有同名配置项
List<NginxConfItem> serverNames = block.getItems("server_name");
for (NginxConfItem item : serverNames) {
    System.out.println("Server Name: " + ((NginxInlineConfItem) item).getValue());
}
```

### 示例 8：添加和删除配置项

```java
NginxServerConfItem server = ...;

// 添加新的配置项
server.addItem(new NginxInlineConfItem("worker_connections 1024;"));

// 删除配置项
server.removeItem(server.getItem("old_setting"));

// 添加注释
server.addItem(new NginxCommentsConfItem("# This is a comment"));
```

### 示例 9：Stream 配置（TCP/UDP 代理）

```java
NginxConfig config = NginxConfig.parse(streamConf);

for (NginxConfItem item : config.getItems()) {
    if (item instanceof NginxStreamConfItem) {
        NginxStreamConfItem stream = (NginxStreamConfItem) item;

        for (NginxServerConfItem server : stream.getServers()) {
            System.out.println("Listen: " + server.getListenPorts());
        }

        for (NginxUpstreamConfItem upstream : stream.getUpstreams()) {
            System.out.println("Upstream: " + upstream.getName());
            System.out.println("Servers: " + upstream.getServerAddresses());
        }
    }
}
```

## 完整示例：读取、修改、保存

```java
import ink.icoding.nginx.core.NginxConfig;
import ink.icoding.nginx.entity.*;

import java.io.IOException;
import java.nio.file.*;

public class NginxConfigModifier {
    public static void main(String[] args) throws IOException {
        // 1. 读取配置文件
        String content = Files.readString(Path.of("/etc/nginx/nginx.conf"));
        NginxConfig config = NginxConfig.parse(content);

        // 2. 修改配置
        for (NginxConfItem item : config.getItems()) {
            if (item instanceof NginxHttpConfItem) {
                modifyHttpBlock((NginxHttpConfItem) item);
            }
        }

        // 3. 保存修改后的配置
        Files.writeString(Path.of("/etc/nginx/nginx.conf.new"), config.toString());
    }

    private static void modifyHttpBlock(NginxHttpConfItem http) {
        for (NginxServerConfItem server : http.getServers()) {
            // 修改 server_name
            List<String> names = server.getServerNames();
            if (names.contains("old-domain.com")) {
                server.setServerNames("new-domain.com");
            }

            // 添加安全头
            server.addItem(new NginxInlineConfItem(
                "add_header X-Frame-Options \"SAMEORIGIN\" always;"));
        }
    }
}
```

## API 参考

### NginxConfig

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| `parse(String content)` | `NginxConfig` | 静态方法，解析配置内容 |
| `getItems()` | `List<NginxConfItem>` | 获取所有顶级配置项 |
| `toString()` | `String` | 序列化为配置字符串 |

### NginxBlockConfItem

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| `getName()` | `String` | 获取块名称 |
| `getValues()` | `List<String>` | 获取块参数 |
| `getFirstValue()` | `String` | 获取第一个参数 |
| `listSubItems()` | `List<NginxConfItem>` | 获取子配置项 |
| `getItem(String name)` | `NginxConfItem` | 按名称查找子项 |
| `getItems(String name)` | `List<NginxConfItem>` | 按名称查找所有子项 |
| `addItem(NginxConfItem)` | `void` | 添加子配置项 |
| `removeItem(NginxConfItem)` | `boolean` | 删除子配置项 |

### NginxServerConfItem

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| `getListenPorts()` | `List<String>` | 获取监听端口 |
| `getServerNames()` | `List<String>` | 获取服务器名称 |
| `setServerNames(String...)` | `void` | 设置服务器名称 |
| `getLocations()` | `List<NginxLocationConfItem>` | 获取所有 location |
| `isSslEnabled()` | `boolean` | 是否启用 SSL |
| `getRoot()` | `String` | 获取根目录 |
| `setRoot(String)` | `void` | 设置根目录 |

### NginxLocationConfItem

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| `getPath()` | `String` | 获取匹配路径 |
| `getModifier()` | `String` | 获取匹配修饰符 |
| `isRegex()` | `boolean` | 是否为正则匹配 |
| `isExact()` | `boolean` | 是否为精确匹配 |
| `isNamed()` | `boolean` | 是否为命名位置 |

### NginxUpstreamConfItem

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| `getServerAddresses()` | `List<String>` | 获取所有服务器地址 |
| `addServer(String, String...)` | `void` | 添加服务器 |
| `removeServer(String)` | `boolean` | 删除服务器 |
| `getLoadBalancingMethod()` | `String` | 获取负载均衡方法 |
| `setLoadBalancingMethod(String)` | `void` | 设置负载均衡方法 |

### NginxHttpConfItem

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| `getServers()` | `List<NginxServerConfItem>` | 获取所有 server |
| `getUpstreams()` | `List<NginxUpstreamConfItem>` | 获取所有 upstream |
| `getTypes()` | `NginxTypesConfItem` | 获取 types 块 |
| `isGzipEnabled()` | `boolean` | 是否启用 gzip |

## 支持的 Nginx 配置元素

| 元素 | 实体类 | 示例 |
|------|--------|------|
| http | `NginxHttpConfItem` | `http { ... }` |
| server | `NginxServerConfItem` | `server { ... }` |
| location | `NginxLocationConfItem` | `location /api/ { ... }` |
| upstream | `NginxUpstreamConfItem` | `upstream backend { ... }` |
| events | `NginxEventsConfItem` | `events { ... }` |
| stream | `NginxStreamConfItem` | `stream { ... }` |
| map | `NginxMapConfItem` | `map $uri $new { ... }` |
| geo | `NginxGeoConfItem` | `geo $geo { ... }` |
| if | `NginxIfConfItem` | `if ($host = 'example.com') { ... }` |
| types | `NginxTypesConfItem` | `types { ... }` |
| limit_except | `NginxLimitExceptConfItem` | `limit_except GET { ... }` |
| 注释 | `NginxCommentsConfItem` | `# comment` |
| 空行 | `NginxEmptyLineConfItem` | `` |
| 行内配置 | `NginxInlineConfItem` | `listen 80;` |

## 许可证

本项目使用 [GNU General Public License v3.0](LICENSE) 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 作者

- guoshengkai (719348277@qq.com)
