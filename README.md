<p align="center">
  <a href="./README_CN.md">中文</a> | <b>English</b>
</p>

<p align="center">
  <img src="https://img.shields.io/github/license/onlyGuo/nginx-gui-2?style=flat-square&color=green" alt="License" />
  <img src="https://img.shields.io/github/last-commit/onlyGuo/nginx-gui-2?style=flat-square&logo=github&color=purple" alt="Last Commit" />
  <br/>
  <img src="https://img.shields.io/docker/pulls/guoshengkai/nginx-gui-2?style=flat-square&logo=docker&color=0db7ed" alt="Docker Pulls" />
  <img src="https://img.shields.io/docker/image-size/guoshengkai/nginx-gui-2/latest?style=flat-square&logo=docker&color=0db7ed" alt="Docker Image Size" />
  <img src="https://img.shields.io/docker/v/guoshengkai/nginx-gui-2/latest?style=flat-square&logo=docker&color=0db7ed" alt="Docker Version" />
  <br/>
  <img src="https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0-green?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 4.0" />
  <img src="https://img.shields.io/badge/Vue-3-brightgreen?style=flat-square&logo=vuedotjs&logoColor=white" alt="Vue 3" />
  <img src="https://img.shields.io/badge/Monaco%20Editor-Yes-blue?style=flat-square&logo=visualstudiocode&logoColor=white" alt="Monaco Editor" />
  <img src="https://img.shields.io/badge/Platform-linux%2Famd64%20%7C%20linux%2Farm64-lightgrey?style=flat-square" alt="Platform" />
</p>

<h1 align="center">Nginx GUI 2</h1>

<p align="center">
  <b>A True Nginx Configuration Manager — Not Just Another Config Generator</b>
</p>

<p align="center">
  Deep-parsing Nginx configs with UI / Code dual-mode editing.<br/>
  Parse, modify, and manage any valid Nginx configuration without breaking existing content.
</p>

<p align="center">
  <a href="https://github.com/onlyGuo/nginx-gui-2/stargazers"><img src="https://img.shields.io/github/stars/onlyGuo/nginx-gui-2?style=social" alt="GitHub Stars" /></a>
  &nbsp;
  <a href="https://github.com/onlyGuo/nginx-gui-2/network/members"><img src="https://img.shields.io/github/forks/onlyGuo/nginx-gui-2?style=social" alt="GitHub Forks" /></a>
  &nbsp;
  <a href="https://github.com/onlyGuo/nginx-gui-2/watchers"><img src="https://img.shields.io/github/watchers/onlyGuo/nginx-gui-2?style=social" alt="GitHub Watchers" /></a>
</p>

---

<p align="center">
  <img src="./doc/example.gif" alt="Nginx GUI 2 Demo" width="90%" />
</p>

## Why Nginx GUI 2?

Traditional Nginx GUI tools are **config generators** — they let you fill in a form and generate a configuration file. But what happens when you already have an existing, complex Nginx config? They can't parse it. They can't modify it. They force you to start from scratch.

**Nginx GUI 2 is fundamentally different.** It is a true **configuration manager** that deeply understands Nginx syntax:

| | Traditional GUI Tools | Nginx GUI 2 |
|---|---|---|
| Generate config from scratch | Yes | Yes |
| **Parse existing config files** | No | **Yes** |
| **Edit existing configs without losing content** | No | **Yes** |
| **UI mode <-> Code mode free switching** | No | **Yes** |
| **Preserve comments, formatting, custom directives** | No | **Yes** |
| Manage any valid Nginx config | No | **Yes** |

**In short:** If you have a 500-line `nginx.conf` with custom comments, unusual directives, and complex nesting — Nginx GUI 2 can load it, display it visually, let you edit it in the UI or in a code editor, and save it back **without destroying any original content**.

> It's not a config generator. It's a **deep-compatible, visual-enabled Nginx editor** that gives you absolute freedom to control Nginx.

---

## Features

| Feature | Description |
|---------|-------------|
| **Dashboard** | Real-time system monitoring (CPU, memory, disk, connections), Nginx status, SSE streaming access/error logs with historical charts |
| **Nginx Config Management** | Deep-parse `conf.d/*.conf` files. Structured editing (Server, Upstream, Location, SSL, Proxy) + raw text editing via Monaco Editor with syntax highlighting |
| **Global Config** | Structured editing of main/events/http-level directives (worker_processes, keepalive, gzip, SSL, log_format, etc.) |
| **Dual Mode (UI / Code)** | Switch freely between visual UI mode and raw Code mode at any time. Changes sync bidirectionally |
| **Firewall Management** | Auto-detects firewall tool (firewalld, iptables, nftables, ufw, pfctl), toggle on/off, list/add/delete port rules |
| **File Browser** | Browse local or remote filesystem for selecting SSL certs, document roots, etc. |
| **Dual Deployment** | `local` mode runs Nginx inside the container; `remote` mode manages Nginx on a remote host via SSH |
| **Config Validation** | Automatic syntax validation with rollback on failure |
| **Existing Config Compatible** | Drop in your current `nginx.conf` — it just works. No migration, no rewriting |

---

## Tech Stack

<table>
  <tr>
    <th>Layer</th>
    <th>Stack</th>
  </tr>
  <tr>
    <td><img src="https://img.shields.io/badge/-Backend-orange?style=flat-square" alt="Backend" /></td>
    <td>Java 17, Spring Boot 4.0, H2 Database, JSch (SSH)</td>
  </tr>
  <tr>
    <td><img src="https://img.shields.io/badge/-Frontend-brightgreen?style=flat-square" alt="Frontend" /></td>
    <td>Vue 3, Pinia, Vite, Monaco Editor, ECharts</td>
  </tr>
  <tr>
    <td><img src="https://img.shields.io/badge/-Infra-lightgrey?style=flat-square" alt="Infra" /></td>
    <td>Docker (multi-platform amd64/arm64), docker-compose</td>
  </tr>
</table>

---

## Quick Start

### 1. Pull and Run (Recommended)

```bash
# Local Nginx + Host monitoring (Host network mode)
docker run -d --network host \
  -v nginx-gui-data:/app/data \
  -e NGINX_MODE=local \
  -e SSH_HOST=localhost \
  -e SSH_USERNAME=<host-user> \
  -e SSH_PASSWORD=<host-password> \
  --name nginx-gui guoshengkai/nginx-gui-2:latest

# Remote Nginx mode
docker run -d -p 8080:80 \
  -v nginx-gui-data:/app/data \
  -e NGINX_MODE=remote \
  -e SSH_HOST=<remote-ip> \
  -e SSH_USERNAME=<user> \
  -e SSH_PASSWORD=<password> \
  --name nginx-gui guoshengkai/nginx-gui-2:latest
```

Access at **http://localhost:8080** (default credentials: `admin` / `admin`)

### 2. Docker Compose

```bash
git clone https://github.com/onlyGuo/nginx-gui-2.git
cd nginx-gui-2
docker compose up -d
```

### 3. Build from Source

```bash
git clone https://github.com/onlyGuo/nginx-gui-2.git
cd nginx-gui-2

# Build
docker build -t nginx-gui-2 .

# Run
docker run -d --network host \
  -v nginx-gui-data:/app/data \
  -e NGINX_MODE=local \
  -e SSH_HOST=localhost \
  -e SSH_USERNAME=<host-user> \
  -e SSH_PASSWORD=<host-password> \
  --name nginx-gui nginx-gui-2

# Multi-platform build (requires buildx)
docker buildx build --platform linux/amd64,linux/arm64 -t nginx-gui-2 .
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `NGINX_MODE` | `local` | `local` = container Nginx, `remote` = remote Nginx via SSH |
| `SSH_HOST` | (empty) | Remote Nginx SSH host (when `NGINX_MODE=remote`) |
| `SSH_PORT` | `22` | Remote Nginx SSH port |
| `SSH_USERNAME` | `root` | Remote Nginx SSH username |
| `SSH_PASSWORD` | (empty) | Remote Nginx SSH password |
| `SSH_SYSTEM_HOST` | (empty) | SSH host for host system info (dashboard) |
| `SSH_SYSTEM_PORT` | `22` | SSH port for host system |
| `SSH_SYSTEM_USERNAME` | `root` | SSH username for host system |
| `SSH_SYSTEM_PASSWORD` | (empty) | SSH password for host system |

---

## Architecture

<p align="center">
  <b>Local Mode</b> &nbsp;&nbsp;&nbsp; | &nbsp;&nbsp;&nbsp; <b>Remote Mode</b>
</p>

- **Local mode** (`NGINX_MODE=local`): Nginx runs inside the container on port 80, serves the Vue SPA and proxies `/api/` to Spring Boot. The app manages container Nginx via local CLI.
- **Remote mode** (`NGINX_MODE=remote`): No Nginx in container. The app SSHes to the remote server to manage Nginx there.
- Both modes support `SSH_SYSTEM_*` variables for the dashboard to SSH to the host and gather system metrics (CPU, memory, disk).

---

## Project Structure

```
nginx-gui-2/
├── src/main/java/ink/icoding/nginx/
│   ├── config/          # SSH, Path, Auto-configuration
│   ├── core/            # NginxClient -- core nginx parser & operations
│   ├── utils/           # CommandUtil, FileUtil (local/SSH abstraction)
│   └── web/             # REST controllers (Dashboard, Config, Firewall, Files)
├── webui/               # Vue 3 SPA frontend
│   └── src/
│       ├── views/       # Dashboard, NginxConfig, BasicConfig, Firewall, Login
│       └── components/  # MonacoEditor, LogPanel, Layout
├── docker/              # nginx.conf, entrypoint.sh
├── Dockerfile           # Multi-stage build (Node -> Maven -> Runtime)
└── docker-compose.yml
```

---

## Contributing

Contributions are welcome! Feel free to open issues and pull requests.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

[![GPL](https://img.shields.io/github/license/onlyGuo/nginx-gui-2?style=flat-square)](./LICENSE)

---

<p align="center">
  If you find this project helpful, please give it a <a href="https://github.com/onlyGuo/nginx-gui-2/stargazers">Star</a>!
</p>
