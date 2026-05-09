# ============================================================
# Multi-stage Dockerfile for nginx-gui-2
# Supports: linux/amd64, linux/arm64
#
# Build:
#   docker buildx build --platform linux/amd64,linux/arm64 -t nginx-gui-2 .
#
# Run (local nginx mode):
#   docker run -d -p 8080:80 \
#     -v nginx-gui-data:/app/data \
#     --name nginx-gui nginx-gui-2
#
# Run (local nginx + host system info via SSH):
#   docker run -d -p 8080:80 \
#     -v nginx-gui-data:/app/data \
#     -e SSH_SYSTEM_HOST=host.docker.internal \
#     -e SSH_SYSTEM_USERNAME=root \
#     -e SSH_SYSTEM_PASSWORD=yourpassword \
#     --add-host=host.docker.internal:host-gateway \
#     --name nginx-gui nginx-gui-2
#
# Run (remote nginx mode):
#   docker run -d -p 8080:80 \
#     -v nginx-gui-data:/app/data \
#     -e NGINX_MODE=remote \
#     -e SSH_HOST=192.168.1.100 \
#     -e SSH_USERNAME=root \
#     -e SSH_PASSWORD=yourpassword \
#     -e SSH_SYSTEM_HOST=host.docker.internal \
#     -e SSH_SYSTEM_USERNAME=root \
#     -e SSH_SYSTEM_PASSWORD=yourpassword \
#     --add-host=host.docker.internal:host-gateway \
#     --name nginx-gui nginx-gui-2
# ============================================================

# ---- Stage 1: Build frontend ----
FROM node:20-bookworm-slim AS frontend-builder

WORKDIR /build/webui
COPY webui/package.json webui/package-lock.json* ./
RUN npm install --registry=https://registry.npmmirror.com
COPY webui/ ./
RUN npm run build

# ---- Stage 2: Build backend ----
FROM maven:3.9-eclipse-temurin-17 AS backend-builder

WORKDIR /build
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw && mvn dependency:go-offline -B
COPY src ./src
COPY --from=frontend-builder /build/webui/dist ./src/main/resources/static
RUN mvn clean package -DskipTests -B

# ---- Stage 3: Runtime image ----
FROM nginx:1.29.8

RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources \
    && sed -i 's|security.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources \
    && apt-get update && apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
        openssh-client \
        sshpass \
        tzdata \
        procps \
    && rm -rf /var/lib/apt/lists/* \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

WORKDIR /app

# Copy backend JAR
COPY --from=backend-builder /build/target/nginx-gui-2-0.0.1-SNAPSHOT.jar /app/app.jar

# Copy frontend build output for nginx to serve directly
COPY --from=frontend-builder /build/webui/dist /app/static

# Copy nginx config for serving frontend + proxying API
COPY docker/nginx.conf /etc/nginx/nginx-gui.conf

# Copy entrypoint
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Remove default nginx config (will be set up by entrypoint if in local mode)
RUN rm -f /etc/nginx/conf.d/default.conf

RUN rm -f /var/log/nginx/access.log /var/log/nginx/error.log

# Data volume (H2 database)
VOLUME /app/data

EXPOSE 80

ENTRYPOINT ["/app/entrypoint.sh"]
