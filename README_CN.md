<p align="center">
  <b>中文</b> | <a href="./README.md">English</a>
</p>

# nginx-gui-2

一个现代化的 Web 端 Nginx 配置管理工具。通过浏览器可视化管理 Server、Upstream、Location、全局配置、防火墙规则，并实时监控系统资源状态。

![Example](./doc/example.gif)

## 功能特性

- **仪表盘** — 实时系统监控（CPU、内存、磁盘、连接数），Nginx 状态，SSE 流式推送访问/错误日志，历史趋势图表
- **Nginx 配置管理** — 对 `conf.d/*.conf` 文件进行增删改查，支持结构化编辑（Server、Upstream、Location、SSL、代理）和 Monaco Editor 原始文本编辑（带语法高亮）
- **全局配置** — 结构化编辑 main/events/http 级别指令（worker_processes、keepalive、gzip、SSL、log_format 等）
- **防火墙管理** — 自动检测防火墙工具（firewalld、iptables、nftables、ufw、pfctl），开关防火墙、增删端口规则
- **文件浏览器** — 浏览本地或远程文件系统，用于选择 SSL 证书、网站根目录等
- **双模式运行** — `local` 模式在容器内运行 Nginx；`remote` 模式通过 SSH 管理远程主机上的 Nginx
- **配置校验** — 自动校验配置，失败时自动回滚

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 4.0, H2 数据库, JSch (SSH) |
| 前端 | Vue 3, Pinia, Vite, Monaco Editor, ECharts |
| 基础设施 | Docker（多平台 amd64/arm64）, docker-compose |

## 快速开始

### 1. 拉取镜像直接运行（推荐）

```bash
# 本地 Nginx + 宿主机监控（Host 网络模式）
docker run -d --network host \
  -v nginx-gui-data:/app/data \
  -e NGINX_MODE=local \
  -e SSH_HOST=localhost \
  -e SSH_USERNAME=<宿主机用户名> \
  -e SSH_PASSWORD=<宿主机密码> \
  --name nginx-gui guoshengkai/nginx-gui-2:latest

# 远程 Nginx 模式
docker run -d -p 8080:80 \
  -v nginx-gui-data:/app/data \
  -e NGINX_MODE=remote \
  -e SSH_HOST=<远程主机IP> \
  -e SSH_USERNAME=<用户名> \
  -e SSH_PASSWORD=<密码> \
  --name nginx-gui guoshengkai/nginx-gui-2:latest
```

访问 `http://localhost:8080`

### 2. 自行构建镜像

```bash
# 克隆项目
git clone https://github.com/guoshengkai/nginx-gui-2.git
cd nginx-gui-2

# 构建镜像
docker build -t nginx-gui-2 .

# 运行
docker run -d --network host \
  -v nginx-gui-data:/app/data \
  -e NGINX_MODE=local \
  -e SSH_HOST=localhost \
  -e SSH_USERNAME=<宿主机用户名> \
  -e SSH_PASSWORD=<宿主机密码> \
  --name nginx-gui nginx-gui-2

# 多平台构建（需要 buildx）
docker buildx build --platform linux/amd64,linux/arm64 -t nginx-gui-2 .
```

### 3. Docker Compose

```bash
docker compose up -d
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `NGINX_MODE` | `local` | `local` = 容器内 Nginx，`remote` = 通过 SSH 管理远程 Nginx |
| `SSH_HOST` | （空） | 远程 Nginx 的 SSH 地址（`NGINX_MODE=remote` 时使用） |
| `SSH_PORT` | `22` | 远程 Nginx SSH 端口 |
| `SSH_USERNAME` | `root` | 远程 Nginx SSH 用户名 |
| `SSH_PASSWORD` | （空） | 远程 Nginx SSH 密码 |
| `SSH_SYSTEM_HOST` | （空） | 宿主机系统信息的 SSH 地址（仪表盘使用） |
| `SSH_SYSTEM_PORT` | `22` | 宿主机 SSH 端口 |
| `SSH_SYSTEM_USERNAME` | `root` | 宿主机 SSH 用户名 |
| `SSH_SYSTEM_PASSWORD` | （空） | 宿主机 SSH 密码 |

## 运行模式

- **本地模式**（`NGINX_MODE=local`）：Nginx 在容器内运行于 80 端口，提供 Vue SPA 静态文件服务并将 `/api/` 代理到 Spring Boot。应用通过本地 CLI 管理容器内的 Nginx。
- **远程模式**（`NGINX_MODE=remote`）：容器内不运行 Nginx。应用通过 SSH 连接远程服务器管理 Nginx。
- 两种模式均支持 `SSH_SYSTEM_*` 环境变量，仪表盘通过 SSH 连接宿主机采集系统指标（CPU、内存、磁盘）。

## 项目结构

```
nginx-gui-2/
├── src/main/java/ink/icoding/nginx/
│   ├── config/          # SSH、路径、自动配置
│   ├── core/            # NginxClient — Nginx 核心操作
│   ├── utils/           # CommandUtil、FileUtil（本地/SSH 抽象）
│   └── web/             # REST 控制器（仪表盘、配置、防火墙、文件）
├── webui/               # Vue 3 SPA 前端
│   └── src/
│       ├── views/       # Dashboard、NginxConfig、BasicConfig、Firewall、Login
│       └── components/  # MonacoEditor、LogPanel、Layout
├── docker/              # nginx.conf、entrypoint.sh
├── Dockerfile           # 多阶段构建（Node → Maven → Runtime）
└── docker-compose.yml
```

## 开源协议

NPL
