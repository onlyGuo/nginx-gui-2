#!/bin/bash
set -e

# ============================================================
# Environment Variables:
#   NGINX_MODE           - "local" (manage container nginx, default) or "remote" (manage remote nginx via SSH)
#   SSH_SYSTEM_HOST      - SSH host for host system info (dashboard)
#   SSH_SYSTEM_PORT      - SSH port (default: 22)
#   SSH_SYSTEM_USERNAME  - SSH username (default: root)
#   SSH_SYSTEM_PASSWORD  - SSH password
#   SSH_HOST             - Remote nginx SSH host (when NGINX_MODE=remote)
#   SSH_PORT             - Remote nginx SSH port (default: 22)
#   SSH_USERNAME         - Remote nginx SSH username (default: root)
#   SSH_PASSWORD         - Remote nginx SSH password
# ============================================================

NGINX_MODE="${NGINX_MODE:-local}"

# ---- SSH client config (for host system info) ----
mkdir -p /root/.ssh
chmod 700 /root/.ssh

if [ -n "${SSH_SYSTEM_HOST}" ]; then
    cat > /root/.ssh/config_system <<EOF
Host system-host
    HostName ${SSH_SYSTEM_HOST}
    Port ${SSH_SYSTEM_PORT:-22}
    User ${SSH_SYSTEM_USERNAME:-root}
    StrictHostKeyChecking no
    UserKnownHostsFile /dev/null
    LogLevel ERROR
EOF
    if [ -n "${SSH_SYSTEM_PASSWORD}" ]; then
        export SSHPASS="${SSH_SYSTEM_PASSWORD}"
    fi
    echo "SSH host system info: ${SSH_SYSTEM_USERNAME:-root}@${SSH_SYSTEM_HOST}:${SSH_SYSTEM_PORT:-22}"
fi

# ---- Start nginx (local mode only) ----
if [ "${NGINX_MODE}" = "local" ]; then
    cp /etc/nginx/nginx-gui.conf /etc/nginx/conf.d/default.conf
    nginx
    echo "Nginx started in local mode"
fi

# ---- Launch Spring Boot ----
exec java \
    -Duser.timezone=Asia/Shanghai \
    -Dfile.encoding=UTF-8 \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -jar /app/app.jar
