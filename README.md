<p align="center">
  <a href="./README_CN.md">中文</a> | <b>English</b>
</p>

# nginx-gui-2

A modern, web-based GUI for managing Nginx configurations. Visually manage server blocks, upstreams, locations, global settings, firewall rules, and monitor system metrics — all from your browser.

![Example](./doc/example.gif)

## Features

- **Dashboard** — Real-time system monitoring (CPU, memory, disk, connections), Nginx status, SSE streaming access/error logs with historical charts
- **Nginx Config Management** — CRUD for `conf.d/*.conf` files with structured editing (server blocks, upstreams, locations, SSL, proxy) and raw text editing via Monaco Editor with syntax highlighting
- **Global Config** — Structured editing of main/events/http-level directives (worker_processes, keepalive, gzip, SSL, log_format, etc.)
- **Firewall Management** — Auto-detects firewall tool (firewalld, iptables, nftables, ufw, pfctl), toggle on/off, list/add/delete port rules
- **File Browser** — Browse local or remote filesystem for selecting SSL certs, document roots, etc.
- **Dual Mode** — `local` mode runs Nginx inside the container; `remote` mode manages Nginx on a remote host via SSH
- **Config Validation** — Automatic validation with rollback on failure

## Tech Stack

| Layer    | Stack |
|----------|-------|
| Backend  | Java 17, Spring Boot 4.0, H2 Database, JSch (SSH) |
| Frontend | Vue 3, Pinia, Vite, Monaco Editor, ECharts |
| Infra    | Docker (multi-platform amd64/arm64), docker-compose |

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

Access at `http://localhost:8080`

### 2. Build from Source

```bash
# Clone
git clone https://github.com/guoshengkai/nginx-gui-2.git
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

### 3. Docker Compose

```bash
docker compose up -d
```

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

## Architecture

- **Local mode** (`NGINX_MODE=local`): Nginx runs inside the container on port 80, serves the Vue SPA and proxies `/api/` to Spring Boot. The app manages container Nginx via local CLI.
- **Remote mode** (`NGINX_MODE=remote`): No Nginx in container. The app SSHes to the remote server to manage Nginx there.
- Both modes support `SSH_SYSTEM_*` variables for the dashboard to SSH to the host and gather system metrics (CPU, memory, disk).

## Project Structure

```
nginx-gui-2/
├── src/main/java/ink/icoding/nginx/
│   ├── config/          # SSH, Path, Auto-configuration
│   ├── core/            # NginxClient — core nginx operations
│   ├── utils/           # CommandUtil, FileUtil (local/SSH abstraction)
│   └── web/             # REST controllers (Dashboard, Config, Firewall, Files)
├── webui/               # Vue 3 SPA frontend
│   └── src/
│       ├── views/       # Dashboard, NginxConfig, BasicConfig, Firewall, Login
│       └── components/  # MonacoEditor, LogPanel, Layout
├── docker/              # nginx.conf, entrypoint.sh
├── Dockerfile           # Multi-stage build (Node → Maven → Runtime)
└── docker-compose.yml
```

## License

NPL
