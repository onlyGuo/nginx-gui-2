# nginx-gui-2

Nginx Gui Manager 2.0

Now in development stage.
## Example

![Example](./doc/example.gif)

Changes Made

Frontend: Hash Routing

- webui/src/router/index.js - Changed createWebHistory() to createWebHashHistory()
- webui/vite.config.js - Added base: './' for correct asset paths in hash mode

Docker Files (new)

- Dockerfile - Multi-stage build (Node → Maven → Runtime with nginx + JRE 17 + SSH)
- docker/nginx.conf - Serves frontend from /app/static, proxies /api/ to Spring Boot on port 8080
- docker/entrypoint.sh - Handles SSH config, starts nginx (local mode), launches Spring Boot
- .dockerignore - Excludes node_modules, target, .git, etc.
- docker-compose.yml - For easier usage

Environment Variables

┌─────────────────────┬─────────┬──────────────────────────────────────────────────────────────────────┐
│      Variable       │ Default │                             Description                              │
├─────────────────────┼─────────┼──────────────────────────────────────────────────────────────────────┤
│ NGINX_MODE          │ local   │ local = manage container nginx, remote = manage remote nginx via SSH │
├─────────────────────┼─────────┼──────────────────────────────────────────────────────────────────────┤
│ SSH_HOST            │ (empty) │ Remote nginx SSH host (when NGINX_MODE=remote)                       │
├─────────────────────┼─────────┼──────────────────────────────────────────────────────────────────────┤
│ SSH_PORT            │ 22      │ Remote nginx SSH port                                                │
├─────────────────────┼─────────┼──────────────────────────────────────────────────────────────────────┤
│ SSH_USERNAME        │ root    │ Remote nginx SSH username                                            │
├─────────────────────┼─────────┼──────────────────────────────────────────────────────────────────────┤
│ SSH_PASSWORD        │ (empty) │ Remote nginx SSH password                                            │
├─────────────────────┼─────────┼──────────────────────────────────────────────────────────────────────┤
│ SSH_SYSTEM_HOST     │ (empty) │ SSH host for host system info (dashboard)                            │
├─────────────────────┼─────────┼──────────────────────────────────────────────────────────────────────┤
│ SSH_SYSTEM_PORT     │ 22      │ SSH port for host system                                             │
├─────────────────────┼─────────┼──────────────────────────────────────────────────────────────────────┤
│ SSH_SYSTEM_USERNAME │ root    │ SSH username for host system                                         │
├─────────────────────┼─────────┼──────────────────────────────────────────────────────────────────────┤
│ SSH_SYSTEM_PASSWORD │ (empty) │ SSH password for host system                                         │
└─────────────────────┴─────────┴──────────────────────────────────────────────────────────────────────┘

Build & Run

# Build (single platform)
docker build -t nginx-gui-2 .

# Build (multi-platform, requires buildx)
docker buildx build --platform linux/amd64,linux/arm64 -t nginx-gui-2 .

# Run (local nginx mode)
docker run -d -p 8080:80 -v nginx-gui-data:/app/data --name nginx-gui nginx-gui-2

# Run (local nginx + host system info via SSH)
docker run -d -p 8080:80 \
-v nginx-gui-data:/app/data \
-e SSH_SYSTEM_HOST=host.docker.internal \
-e SSH_SYSTEM_USERNAME=root \
-e SSH_SYSTEM_PASSWORD=yourpassword \
--add-host=host.docker.internal:host-gateway \
--name nginx-gui nginx-gui-2

# Run (remote nginx mode)
docker run -d -p 8080:80 \
-v nginx-gui-data:/app/data \
-e NGINX_MODE=remote \
-e SSH_HOST=192.168.1.100 \
-e SSH_USERNAME=root \
-e SSH_PASSWORD=yourpassword \
--name nginx-gui nginx-gui-2

# Or use docker-compose
docker compose up -d

Architecture

- Local mode (NGINX_MODE=local): Nginx runs inside the container on port 80, serves the Vue SPA and proxies /api/ to
  Spring Boot. The app manages container nginx via local CLI.
- Remote mode (NGINX_MODE=remote): No nginx in container. The app SSHes to the remote server to manage nginx there.
- Both modes support SSH_SYSTEM_* variables for the dashboard to SSH to the host and gather system metrics (CPU,
  memory, disk).

Note: The ink.icoding:nginx-analysis:1.0.2 dependency must be available in Maven Central or a configured repository
for the Docker build to succeed.