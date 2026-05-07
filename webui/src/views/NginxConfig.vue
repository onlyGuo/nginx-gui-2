<template>
  <AppLayout>
    <PathGuard>
    <div class="nginx-config-layout">
    <div class="nginx-config-main">
    <div class="nginx-config">
      <!-- Left: Config File List -->
      <div class="config-list-panel card">
        <div class="card-header">
          <span>配置文件</span>
          <button class="btn btn-sm btn-icon" @click="showNewFileDialog = true" title="新建配置">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          </button>
        </div>
        <div class="config-list">
          <!-- nginx.conf special entry -->
          <div
            v-if="hasMainBlocks"
            class="config-item config-item-main"
            :class="{ active: activeFile === mainFileName }"
            @click="selectFile(mainFileName)"
          >
            <div class="config-item-row">
              <span class="config-item-name" style="color: var(--warning)">nginx.conf</span>
            </div>
            <div class="config-item-meta">
              <span class="text-xs" style="color: var(--warning)">不建议在此配置代理</span>
            </div>
            <button class="btn btn-sm btn-ghost" style="margin-top:4px;font-size:11px;color:var(--danger)" @click.stop="clearMainBlocks">
              一键清除代理配置
            </button>
          </div>

          <!-- conf.d files -->
          <div
            v-for="f in configFiles"
            :key="f.name"
            class="config-item"
            :class="{ active: activeFile === f.name, disabled: !f.enabled }"
            @click="selectFile(f.name)"
          >
            <div class="config-item-row">
              <span class="config-item-name">{{ f.name }}</span>
              <div class="config-item-actions">
                <label class="switch" @click.stop>
                  <input type="checkbox" :checked="f.enabled" @change="toggleFile(f)" /><span class="switch-slider"></span>
                </label>
                <button class="btn btn-sm btn-ghost" @click.stop="deleteFile(f)" title="删除">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                </button>
              </div>
            </div>
            <div class="config-item-meta">
              <span class="text-xs text-tertiary">{{ f.enabled ? '已启用' : '已停用' }}</span>
              <span class="text-xs text-tertiary">{{ f.time }}</span>
            </div>
          </div>

          <div v-if="!hasMainBlocks && configFiles.length === 0" class="config-empty">暂无配置文件</div>
        </div>
      </div>

      <!-- Right: Detail -->
      <div class="config-detail-panel">
        <!-- Top: Upstream (full width) -->
        <div class="detail-top">
          <!-- Upstream -->
          <div class="card upstream-card-full">
            <div class="card-header">
              <span>Upstream 列表</span>
              <button class="btn btn-sm btn-icon" @click="addUpstream" title="新增 Upstream">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              </button>
            </div>
            <div class="card-body upstream-body">
              <div v-if="upstreams.length === 0" class="empty-hint">暂无 Upstream</div>
              <div v-for="(up, ui) in upstreams" :key="ui" class="upstream-item">
                <div class="upstream-header" @click="up._open = !up._open">
                  <div class="flex items-center gap-sm">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :style="{ transform: up._open ? 'rotate(90deg)' : '' }"><polyline points="9 18 15 12 9 6"/></svg>
                    <span class="upstream-name">{{ up.name || 'unnamed' }}</span>
                  </div>
                  <button class="btn btn-sm btn-ghost" @click.stop="removeUpstream(ui)" title="删除">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                  </button>
                </div>
                <div v-if="up._open" class="upstream-detail">
                  <div class="form-group">
                    <label class="form-label">名称</label>
                    <input v-model="up.name" placeholder="backend" />
                  </div>
                  <div class="form-group">
                    <label class="form-label">负载策略(upstream strategy)</label>
                    <BaseSelect v-model="up.strategy" :options="upstreamStrategyOpts" />
                  </div>
                  <div class="form-group">
                    <label class="form-label">长连接数(keepalive)</label>
                    <input v-model="up.keepalive" type="number" placeholder="32" />
                  </div>
                  <div class="upstream-servers">
                    <div class="flex items-center justify-between" style="margin-bottom:4px">
                      <label class="form-label">后端服务器</label>
                      <button class="btn btn-sm" @click="addUpstreamServer(up)">+ 添加</button>
                    </div>
                    <div v-for="(srv, si) in up.servers" :key="si" class="upstream-srv-row">
                      <input v-model="srv.addr" placeholder="127.0.0.1:8080" />
                      <input v-model="srv.weight" placeholder="weight(权重值)" style="width:70px" />
                      <BaseSelect v-model="srv.state" :options="srvStateOpts" style="width:90px" />
                      <button class="btn btn-sm btn-ghost" @click="up.servers.splice(si, 1)">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Bottom: Server + Location -->
        <div class="detail-bottom">
          <!-- Server List -->
          <div class="card server-list-card">
            <div class="card-header">
              <span>Server 列表</span>
              <button class="btn btn-sm btn-icon" @click="addServer" title="新增 Server">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              </button>
            </div>
            <div class="server-list">
              <div
                v-for="(srv, si) in servers"
                :key="si"
                class="server-item"
                :class="{ active: activeServer === si }"
                @click="activeServer = si"
              >
                <div class="server-item-name">{{ srv.serverName || 'unnamed' }}:{{ srv.listen }}</div>
                <div class="flex items-center gap-xs">
                  <span class="badge" :class="srv.ssl ? 'badge-success' : 'badge-warning'">{{ srv.ssl ? 'SSL' : 'HTTP' }}</span>
                  <button class="btn btn-sm btn-ghost" @click.stop="removeServer(si)" title="删除">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                  </button>
                </div>
              </div>
              <div v-if="servers.length === 0" class="config-empty">暂无 Server</div>
            </div>
          </div>

          <!-- Server Detail -->
          <div class="card server-detail-card" v-if="currentServer">
            <div class="card-header">Server 配置</div>
            <div class="card-body server-detail-body">
              <!-- Basic -->
              <div class="form-grid">
                <div class="form-group">
                  <label class="form-label">监听端口(listen)</label>
                  <input v-model="currentServer.listen" placeholder="80" />
                </div>
                <div class="form-group">
                  <label class="form-label">域名(server_name)</label>
                  <input v-model="currentServer.serverName" placeholder="example.com" />
                </div>
                <div class="form-group">
                  <label class="form-label">字符集(charset)</label>
                  <BaseCombo v-model="currentServer.charset" :options="charsetOpts" placeholder="utf-8" />
                </div>
                <div class="form-group">
                  <label class="form-label">根目录(root)</label>
                  <PathSelector v-model="currentServer.root" type="dir" placeholder="/var/www/html" />
                </div>
                <div class="form-group">
                  <label class="form-label">默认索引(index)</label>
                  <BaseCombo v-model="currentServer.index" :options="indexOpts" placeholder="index.html index.htm" />
                </div>
                <div class="form-group">
                  <label class="form-label">访问日志(access_log)</label>
                  <PathSelector v-model="currentServer.accessLog" type="file" placeholder="/var/log/nginx/access.log" />
                </div>
                <div class="form-group">
                  <label class="form-label">错误日志(error_log)</label>
                  <PathSelector v-model="currentServer.errorLog" type="file" placeholder="/var/log/nginx/error.log" />
                </div>
                <div class="form-group">
                  <label class="form-label">最大请求体(client_max_body_size)</label>
                  <input v-model="currentServer.clientMaxBodySize" placeholder="10m" />
                </div>
              </div>

              <!-- SSL -->
              <div class="section-title">SSL 配置</div>
              <div class="form-grid">
                <div class="form-group">
                  <label class="form-label">启用 SSL</label>
                  <label class="switch"><input type="checkbox" v-model="currentServer.ssl" /><span class="switch-slider"></span></label>
                </div>
                <div class="form-group">
                  <label class="form-label">HTTP跳转HTTPS(ssl_redirect)</label>
                  <label class="switch"><input type="checkbox" v-model="currentServer.sslRedirect" :disabled="currentServer.ssl" /><span class="switch-slider"></span></label>
                  <span class="text-xs text-tertiary" v-if="currentServer.ssl">仅对HTTP生效, 已启用SSL时无需跳转</span>
                </div>
                <div class="form-group">
                  <label class="form-label">证书文件(ssl_certificate)</label>
                  <PathSelector v-model="currentServer.sslCert" type="file" placeholder="/etc/ssl/cert.pem" :disabled="!currentServer.ssl" />
                </div>
                <div class="form-group">
                  <label class="form-label">私钥文件(ssl_certificate_key)</label>
                  <PathSelector v-model="currentServer.sslKey" type="file" placeholder="/etc/ssl/key.pem" :disabled="!currentServer.ssl" />
                </div>
                <div class="form-group">
                  <label class="form-label">协议版本(ssl_protocols)</label>
                  <BaseCombo v-model="currentServer.sslProtocols" :options="sslProtocolsOpts" placeholder="TLSv1.2 TLSv1.3" :disabled="!currentServer.ssl" />
                </div>
                <div class="form-group">
                  <label class="form-label">加密套件(ssl_ciphers)</label>
                  <BaseCombo v-model="currentServer.sslCiphers" :options="sslCiphersOpts" placeholder="HIGH:!aNULL:!MD5" :disabled="!currentServer.ssl" />
                </div>
                <div class="form-group">
                  <label class="form-label">会话超时(ssl_session_timeout)</label>
                  <input v-model="currentServer.sslSessionTimeout" placeholder="1d" :disabled="!currentServer.ssl" />
                </div>
                <div class="form-group">
                  <label class="form-label">会话缓存(ssl_session_cache)</label>
                  <BaseCombo v-model="currentServer.sslSessionCache" :options="sslSessionCacheOpts" placeholder="shared:SSL:10m" :disabled="!currentServer.ssl" />
                </div>
                <div class="form-group">
                  <label class="form-label">优先使用服务端算法(ssl_prefer_server_ciphers)</label>
                  <label class="switch"><input type="checkbox" v-model="currentServer.sslPreferServerCiphers" :disabled="!currentServer.ssl" /><span class="switch-slider"></span></label>
                </div>
              </div>

              <!-- Gzip -->
              <div class="section-title">Gzip 压缩</div>
              <div class="form-grid">
                <div class="form-group">
                  <label class="form-label">启用压缩(gzip)</label>
                  <label class="switch"><input type="checkbox" v-model="currentServer.gzip.on" /><span class="switch-slider"></span></label>
                </div>
                <div class="form-group">
                  <label class="form-label">Vary头(gzip_vary)</label>
                  <label class="switch"><input type="checkbox" v-model="currentServer.gzip.vary" :disabled="!currentServer.gzip.on" /><span class="switch-slider"></span></label>
                </div>
                <div class="form-group">
                  <label class="form-label">最小压缩长度(gzip_min_length)</label>
                  <input v-model="currentServer.gzip.minLength" placeholder="1024" :disabled="!currentServer.gzip.on" />
                </div>
                <div class="form-group">
                  <label class="form-label">压缩级别(gzip_comp_level)</label>
                  <BaseSelect v-model="currentServer.gzip.compLevel" :options="compLevelOpts" :disabled="!currentServer.gzip.on" />
                </div>
                <div class="form-group">
                  <label class="form-label">压缩类型(gzip_types)</label>
                  <BaseCombo v-model="currentServer.gzip.types" :options="gzipTypesOpts" placeholder="text/plain application/json ..." :disabled="!currentServer.gzip.on" />
                </div>
                <div class="form-group">
                  <label class="form-label">代理压缩(gzip_proxied)</label>
                  <BaseSelect v-model="currentServer.gzip.proxied" :options="gzipProxiedOpts" :disabled="!currentServer.gzip.on" />
                </div>
                <div class="form-group">
                  <label class="form-label">缓冲区(gzip_buffers)</label>
                  <div class="form-row-inline">
                    <input v-model="currentServer.gzip.buffersNum" placeholder="4" :disabled="!currentServer.gzip.on" />
                    <span class="form-row-sep">x</span>
                    <BaseSelect style="width: 100%" v-model="currentServer.gzip.buffersSize" :options="buffersSizeOpts" :disabled="!currentServer.gzip.on" />
                  </div>
                </div>
                <div class="form-group">
                  <label class="form-label">最低HTTP版本(gzip_http_version)</label>
                  <BaseSelect v-model="currentServer.gzip.httpVersion" :options="httpVersionOpts" :disabled="!currentServer.gzip.on" />
                </div>
              </div>

              <!-- Proxy Overrides -->
              <div class="section-title">代理配置（可覆盖全局默认值）</div>
              <div class="form-grid">
                <div class="form-group">
                  <label class="form-label">代理缓冲(proxy_buffering)</label>
                  <label class="switch"><input type="checkbox" v-model="currentServer.proxyBuffering" /><span class="switch-slider"></span></label>
                </div>
                <div class="form-group">
                  <label class="form-label">字符集(charset)</label>
                  <BaseCombo v-model="currentServer.charset" :options="charsetOpts" placeholder="utf-8（留空继承全局）" />
                </div>
                <div class="form-group">
                  <label class="form-label">代理连接超时(proxy_connect_timeout)</label>
                  <input v-model="currentServer.proxyConnectTimeout" placeholder="30s（留空继承全局）" />
                </div>
                <div class="form-group">
                  <label class="form-label">代理读取超时(proxy_read_timeout)</label>
                  <input v-model="currentServer.proxyReadTimeout" placeholder="60s（留空继承全局）" />
                </div>
                <div class="form-group">
                  <label class="form-label">代理发送超时(proxy_send_timeout)</label>
                  <input v-model="currentServer.proxySendTimeout" placeholder="60s（留空继承全局）" />
                </div>
                <div class="form-group">
                  <label class="form-label">代理缓冲区(proxy_buffer_size)</label>
                  <input v-model="currentServer.proxyBufferSize" placeholder="4k（留空继承全局）" />
                </div>
              </div>

              <!-- HTTP Overrides -->
              <div class="section-title">HTTP 配置（可覆盖全局默认值）</div>
              <div class="form-grid">
                <div class="form-group">
                  <label class="form-label">零拷贝传输(sendfile)</label>
                  <BaseSelect v-model="currentServer.sendfile" :options="onOffOpts" placeholder="留空继承全局" />
                </div>
                <div class="form-group">
                  <label class="form-label">TCP推送(tcp_nopush)</label>
                  <BaseSelect v-model="currentServer.tcpNopush" :options="onOffOpts" placeholder="留空继承全局" />
                </div>
                <div class="form-group">
                  <label class="form-label">TCP无延迟(tcp_nodelay)</label>
                  <BaseSelect v-model="currentServer.tcpNodelay" :options="onOffOpts" placeholder="留空继承全局" />
                </div>
                <div class="form-group">
                  <label class="form-label">保持连接超时(keepalive_timeout)</label>
                  <input v-model="currentServer.keepaliveTimeout" placeholder="65（留空继承全局）" />
                </div>
                <div class="form-group">
                  <label class="form-label">最大请求数(keepalive_requests)</label>
                  <input v-model="currentServer.keepaliveRequests" type="number" placeholder="1000（留空继承全局）" />
                </div>
                <div class="form-group">
                  <label class="form-label">请求体超时(client_body_timeout)</label>
                  <input v-model="currentServer.clientBodyTimeout" type="number" placeholder="60（留空继承全局）" />
                </div>
                <div class="form-group">
                  <label class="form-label">请求头超时(client_header_timeout)</label>
                  <input v-model="currentServer.clientHeaderTimeout" type="number" placeholder="60（留空继承全局）" />
                </div>
                <div class="form-group">
                  <label class="form-label">哈希表大小(types_hash_max_size)</label>
                  <input v-model="currentServer.typesHashMaxSize" type="number" placeholder="2048（留空继承全局）" />
                </div>
                <div class="form-group">
                  <label class="form-label">版本信息(server_tokens)</label>
                  <BaseSelect v-model="currentServer.serverTokens" :options="onOffOpts" placeholder="留空继承全局" />
                </div>
              </div>

              <!-- Extra -->
              <div class="section-title">其他配置</div>
              <div class="form-grid">
                <div class="form-group">
                  <label class="form-label">目录浏览(autoindex)</label>
                  <label class="switch"><input type="checkbox" v-model="currentServer.autoindex" /><span class="switch-slider"></span></label>
                </div>
              </div>

              <!-- Response Headers -->
              <div class="section-title">
                <span>响应头(add_header)</span>
                <button class="btn btn-sm btn-icon" @click="addResponseHeader" title="新增响应头">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                </button>
              </div>
              <div v-if="currentServer.addHeaders.length === 0" class="empty-hint">暂无自定义响应头</div>
              <div v-for="(hdr, hi) in currentServer.addHeaders" :key="hi" class="upstream-srv-row" style="margin-bottom:4px">
                <BaseCombo v-model="currentServer.addHeaders[hi]" :options="addHeaderOpts" placeholder="X-Frame-Options SAMEORIGIN" style="flex:1" />
                <button class="btn btn-sm btn-ghost" @click="currentServer.addHeaders.splice(hi, 1)">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                </button>
              </div>

              <!-- Locations -->
              <div class="section-title">
                <span>Location 列表</span>
                <button class="btn btn-sm btn-icon" @click="addLocation" title="新增 Location">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                </button>
              </div>
              <div class="location-list">
                <div v-if="currentServer.locations.length === 0" class="empty-hint">暂无 Location</div>
                <div v-for="(loc, li) in currentServer.locations" :key="li" class="location-item">
                  <div class="location-header" @click="loc._open = !loc._open">
                    <div class="flex items-center gap-sm">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :style="{ transform: loc._open ? 'rotate(90deg)' : '' }"><polyline points="9 18 15 12 9 6"/></svg>
                      <span class="location-path">{{ loc.path || '/' }}</span>
                      <span class="badge" :class="locTypeBadge(loc.type)">{{ loc.type }}</span>
                    </div>
                    <button class="btn btn-sm btn-ghost" @click.stop="removeLocation(li)" title="删除">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                    </button>
                  </div>
                  <div v-if="loc._open" class="location-detail">
                    <div class="form-grid">
                      <div class="form-group">
                        <label class="form-label">路径(path)</label>
                        <input v-model="loc.path" placeholder="/" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">类型</label>
                        <BaseSelect v-model="loc.type" :options="locTypeOpts" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">根目录/别名(root/alias)</label>
                        <PathSelector v-model="loc.root" type="dir" placeholder="/var/www/html" />
                      </div>
                      <div class="form-group" style="grid-column: 1 / -1">
                        <label class="form-label">代理目标(proxy_pass)</label>
                        <BaseCombo v-model="loc.proxyPass" :options="proxyPassOpts" placeholder="http://127.0.0.1:3000 或选择 Upstream" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">文件尝试(try_files)</label>
                        <input v-model="loc.tryFiles" placeholder="$uri $uri/ /index.html" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">返回/重定向(return)</label>
                        <input v-model="loc.returnCode" placeholder="301 https://..." />
                      </div>
                      <div class="form-group">
                        <label class="form-label">URL重写(rewrite)</label>
                        <input v-model="loc.rewrite" placeholder="^/old/(.*)$ /new/$1 permanent" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">默认索引(index)</label>
                        <input v-model="loc.index" placeholder="index.html" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">缓存过期(expires)</label>
                        <input v-model="loc.expires" placeholder="30d" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">拒绝访问(deny)</label>
                        <input v-model="loc.deny" placeholder="all" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">允许访问(allow)</label>
                        <input v-model="loc.allow" placeholder="192.168.1.0/24" />
                      </div>
                    </div>
                    <!-- Proxy Headers -->
                    <div class="section-title" style="font-size:11px;margin-top:8px">代理 Header</div>
                    <div class="form-grid">
                      <div class="form-group">
                        <label class="form-label">代理Host头(proxy_set_header Host)</label>
                        <input v-model="loc.proxyHost" placeholder="$host" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">真实IP头(proxy_set_header X-Real-IP)</label>
                        <input v-model="loc.proxyRealIp" placeholder="$remote_addr" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">转发IP链(proxy_set_header X-Forwarded-For)</label>
                        <input v-model="loc.proxyXff" placeholder="$proxy_add_x_forwarded_for" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">转发协议头(proxy_set_header X-Forwarded-Proto)</label>
                        <input v-model="loc.proxyProto" placeholder="$scheme" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="card server-detail-card server-empty-card">
            <div class="card-body flex items-center justify-center" style="height:100%">
              <span class="text-tertiary">请从左侧选择一个 Server</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    </div>
    <LogPanel :logs="logs" :status="saveStatus" @clear="logs.splice(0)" />
    </div>

    <!-- New File Dialog -->
    <div v-if="showNewFileDialog" class="modal-overlay" @click.self="showNewFileDialog = false">
      <div class="modal-card card">
        <div class="card-header">
          <span>新建配置文件</span>
          <button class="btn btn-sm btn-ghost" @click="showNewFileDialog = false">&times;</button>
        </div>
        <div class="card-body">
          <div class="form-grid">
            <div class="form-group">
              <label class="form-label">文件名称</label>
              <input v-model="newFile.name" placeholder="my-app" />
              <span class="text-xs text-tertiary">.conf 后缀会自动添加</span>
            </div>
            <div class="form-group">
              <label class="form-label">端口</label>
              <input v-model="newFile.port" placeholder="80" />
            </div>
            <div class="form-group" style="grid-column: 1 / -1">
              <label class="form-label">域名</label>
              <input v-model="newFile.domain" placeholder="example.com (可选)" />
            </div>
          </div>
        </div>
        <div style="padding: 0 var(--space-md) var(--space-md); display:flex; justify-content:flex-end; gap:var(--space-sm)">
          <button class="btn btn-ghost" @click="showNewFileDialog = false">取消</button>
          <button class="btn btn-primary" @click="createFile">创建</button>
        </div>
      </div>
    </div>
    </PathGuard>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import AppLayout from '../components/layout/AppLayout.vue'
import PathGuard from '../components/common/PathGuard.vue'
import PathSelector from '../components/common/PathSelector.vue'
import BaseSelect from '../components/common/BaseSelect.vue'
import BaseCombo from '../components/common/BaseCombo.vue'
import LogPanel from '../components/common/LogPanel.vue'

const upstreamStrategyOpts = [
  { value: '', label: '默认加权轮询', description: '默认策略，按 server 的 weight 参数加权分配请求' },
  { value: 'ip_hash', label: 'ip_hash', description: '按客户端 IP 哈希分配，保持会话一致性' },
  { value: 'least_conn', label: 'least_conn', description: '优先分配给当前连接数最少的服务器' },
  { value: 'random', label: 'random', description: '随机分配请求' }
]
const srvStateOpts = [
  { value: '', label: '正常', description: '正常接收请求' },
  { value: 'down', label: 'down', description: '标记为不可用，不接收请求' },
  { value: 'backup', label: 'backup', description: '备用服务器，仅在主服务器不可用时启用' }
]
const gzipProxiedOpts = [
  { value: 'off', label: 'off', description: '不对代理请求进行压缩' },
  { value: 'expired', label: 'expired', description: '对过期头的响应进行压缩' },
  { value: 'no-cache', label: 'no-cache', description: '对 Cache-Control: no-cache 的响应压缩' },
  { value: 'no-store', label: 'no-store', description: '对禁止缓存的响应进行压缩' },
  { value: 'private', label: 'private', description: '对私有缓存的响应进行压缩' },
  { value: 'no_last_modified', label: 'no_last_modified', description: '无 Last-Modified 头时不压缩' },
  { value: 'no_etag', label: 'no_etag', description: '无 ETag 头时不压缩' },
  { value: 'auth', label: 'auth', description: '对带 Authorization 头的响应压缩' },
  { value: 'any', label: 'any', description: '对所有代理请求进行压缩' }
]
const buffersSizeOpts = [
  { value: '4k', label: '4k', description: '最小缓冲区，内存占用最低' },
  { value: '8k', label: '8k', description: '较小缓冲区，适合轻量响应' },
  { value: '16k', label: '16k', description: '中等缓冲区，通用场景' },
  { value: '32k', label: '32k', description: '较大缓冲区，适合较大响应' },
  { value: '64k', label: '64k', description: '最大缓冲区，适合大文件传输' }
]
const httpVersionOpts = [
  { value: '1.0', label: '1.0', description: 'HTTP/1.0 协议，兼容性最好' },
  { value: '1.1', label: '1.1', description: 'HTTP/1.1 协议，支持持久连接和分块传输' }
]
const compLevelOpts = [
  { value: '1', label: '1', description: '压缩速度最快，压缩率最低' },
  { value: '2', label: '2', description: '速度较快，压缩率较低' },
  { value: '3', label: '3', description: '速度与压缩率均衡偏速度' },
  { value: '4', label: '4', description: '速度与压缩率较均衡' },
  { value: '5', label: '5', description: '均衡模式，推荐默认值' },
  { value: '6', label: '6', description: '均衡偏压缩率，常用默认值' },
  { value: '7', label: '7', description: '压缩率较高，速度稍慢' },
  { value: '8', label: '8', description: '高压缩率，CPU 消耗较大' },
  { value: '9', label: '9', description: '最高压缩率，CPU 消耗最大' }
]
const locTypeOpts = [
  { value: 'prefix', label: '前缀匹配', description: '匹配 URI 前缀，如 /api/' },
  { value: 'exact', label: '精确匹配 (=)', description: '完全匹配 URI，优先级最高' },
  { value: 'regex', label: '正则匹配 (~)', description: '正则表达式匹配，区分大小写' },
  { value: 'regexNocase', label: '正则不区分大小写 (~*)', description: '正则表达式匹配，不区分大小写' }
]
const sslProtocolsOpts = [
  { value: 'TLSv1.2 TLSv1.3', label: 'TLSv1.2 + TLSv1.3', description: '推荐配置，兼容性与安全性兼顾' },
  { value: 'TLSv1.3', label: '仅 TLSv1.3', description: '最高安全性，较旧客户端不支持' },
  { value: 'TLSv1.1 TLSv1.2 TLSv1.3', label: 'TLSv1.1 ~ TLSv1.3', description: '兼容旧客户端，安全性较低' }
]
const sslCiphersOpts = [
  { value: 'HIGH:!aNULL:!MD5', label: 'HIGH:!aNULL:!MD5', description: '推荐配置，排除不安全算法' },
  { value: 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384', label: 'ECDHE+AESGCM', description: '现代浏览器推荐的强加密套件' },
  { value: 'EECDH+AESGCM:EDH+AESGCM', label: 'EECDH+AESGCM', description: '前向保密 + AEAD 加密' },
  { value: 'ALL:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!MD5:!PSK:!SRP', label: 'ALL 排除弱算法', description: '允许所有算法，仅排除已知弱算法' }
]
const sslSessionCacheOpts = [
  { value: 'shared:SSL:10m', label: 'shared:SSL:10m', description: '共享缓存 10MB（推荐）' },
  { value: 'shared:SSL:20m', label: 'shared:SSL:20m', description: '共享缓存 20MB，高并发场景' },
  { value: 'shared:SSL:50m', label: 'shared:SSL:50m', description: '共享缓存 50MB，大规模部署' },
  { value: 'none', label: 'none', description: '禁用会话缓存' }
]
const onOffOpts = [
  { value: 'on', label: '开启' },
  { value: 'off', label: '关闭' }
]
const addHeaderOpts = [
  { value: 'X-Frame-Options SAMEORIGIN', label: 'X-Frame-Options SAMEORIGIN', description: '防止页面被嵌入 iframe（点击劫持防护）' },
  { value: 'X-Content-Type-Options nosniff', label: 'X-Content-Type-Options nosniff', description: '禁止浏览器 MIME 类型嗅探' },
  { value: 'X-XSS-Protection "1; mode=block"', label: 'X-XSS-Protection "1; mode=block"', description: '启用浏览器 XSS 过滤器' },
  { value: 'Strict-Transport-Security "max-age=31536000; includeSubDomains"', label: 'HSTS (1年)', description: '强制浏览器使用 HTTPS 访问' },
  { value: 'Referrer-Policy "strict-origin-when-cross-origin"', label: 'Referrer-Policy', description: '跨域时仅发送源信息作为引用来源' },
  { value: 'Permissions-Policy "camera=(), microphone=(), geolocation=()"', label: 'Permissions-Policy', description: '禁止网页调用摄像头、麦克风、定位' },
  { value: 'Content-Security-Policy "default-src \'self\'"', label: 'CSP 基础策略', description: '仅允许加载同源资源' },
  { value: 'Cache-Control "no-cache, no-store, must-revalidate"', label: '禁用缓存', description: '禁止浏览器和代理缓存响应' },
  { value: 'Access-Control-Allow-Origin *', label: 'CORS 全开放', description: '允许所有域名跨域访问（慎用）' },
  { value: 'Access-Control-Allow-Origin $http_origin', label: 'CORS 动态来源', description: '回显请求 Origin 头作为允许来源' }
]
const charsetOpts = [
  { value: 'utf-8', label: 'utf-8', description: '通用 Unicode 编码（推荐）' },
  { value: 'gbk', label: 'gbk', description: '简体中文编码，兼容旧系统' },
  { value: 'gb2312', label: 'gb2312', description: '简体中文国标编码' },
  { value: 'big5', label: 'big5', description: '繁体中文编码' },
  { value: 'iso-8859-1', label: 'iso-8859-1', description: '西欧语言编码' },
  { value: 'utf-16', label: 'utf-16', description: 'Unicode 双字节编码' }
]
const indexOpts = [
  { value: 'index.html index.htm', label: 'index.html index.htm', description: '静态网站默认配置' },
  { value: 'index.php index.html index.htm', label: 'index.php index.html', description: 'PHP 网站' },
  { value: 'index.jsp index.html index.htm', label: 'index.jsp index.html', description: 'Java Web 网站' },
  { value: 'index.html index.htm index.nginx-debian.html', label: 'Debian 默认', description: 'Nginx Debian 包默认配置' }
]
const gzipTypesOpts = [
  { value: 'text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript image/svg+xml', label: '常用类型', description: 'CSS/JS/JSON/XML/SVG 等常见 Web 资源' },
  { value: '*/*', label: '所有类型', description: '对所有 MIME 类型启用压缩' },
  { value: 'text/plain text/css application/json', label: '最小集合', description: '仅 CSS/JSON 纯文本' },
  { value: 'text/plain text/css application/json application/javascript text/html', label: '含 HTML', description: '常见 Web 资源含 HTML 页面' }
]

// ---- Log Panel ----
const logs = reactive([])
function addLog(success, message) {
  logs.push({ time: new Date(), success, message })
  if (logs.length > 200) logs.splice(0, logs.length - 200)
}

const saveStatus = reactive({ state: 'idle', message: '', time: null })

let saveTimer = null
function debounce(fn, ms) {
  return (...args) => {
    clearTimeout(saveTimer)
    saveTimer = setTimeout(() => fn(...args), ms)
  }
}

// ---- State ----
const configFiles = reactive([])
const activeFile = ref('')
const hasMainBlocks = ref(false)
const mainFileName = ref('nginx.conf')

const upstreams = reactive([])
const servers = reactive([])

const activeServer = ref(0)
const currentServer = computed(() => servers[activeServer.value] || null)

const proxyPassOpts = computed(() =>
  upstreams.map(up => ({ value: 'http://' + up.name, label: up.name, description: '代理到 upstream: ' + up.name + ' (含 ' + up.servers.length + ' 台服务器)' }))
)

const showNewFileDialog = ref(false)
const newFile = reactive({ name: '', port: '80', domain: '' })

let populating = false
let loaded = false

// 用于 watcher 的脏检查：忽略 _open 等 UI 状态，过滤空 server 行
function serializeForSave(arr) {
  return JSON.stringify(arr.map(item => {
    const { _open, ...rest } = item
    if (rest.locations) {
      rest.locations = rest.locations.map(loc => { const { _open, ...r } = loc; return r })
    }
    if (rest.servers) {
      rest.servers = rest.servers.filter(s => s.addr && s.addr.trim()).map(s => { const { _open, ...r } = s; return r })
    }
    if (rest.addHeaders) {
      rest.addHeaders = rest.addHeaders.filter(h => h && h.trim())
    }
    return rest
  }))
}

// ---- Helpers ----
function defaultServer() {
  return {
    sourceFile: '',
    listen: '80', serverName: '', charset: '', root: '', index: '',
    accessLog: '', errorLog: '', clientMaxBodySize: '',
    ssl: false, sslCert: '', sslKey: '', sslProtocols: 'TLSv1.2 TLSv1.3', sslRedirect: false,
    sslCiphers: '', sslPreferServerCiphers: false, sslSessionTimeout: '', sslSessionCache: '',
    autoindex: false,
    gzip: { on: false, minLength: '1024', compLevel: '6', types: 'text/plain text/css application/json application/javascript', vary: false, proxied: 'off', buffersNum: '4', buffersSize: '8k', httpVersion: '1.1' },
    proxyBuffering: true, proxyConnectTimeout: '', proxyReadTimeout: '', proxySendTimeout: '', proxyBufferSize: '',
    sendfile: '', tcpNopush: '', tcpNodelay: '', keepaliveTimeout: '', keepaliveRequests: '',
    clientBodyTimeout: '', clientHeaderTimeout: '', typesHashMaxSize: '', serverTokens: '',
    addHeaders: [],
    locations: []
  }
}

function defaultLocation() {
  return {
    _open: true, path: '/', type: 'prefix', root: '', alias: '',
    proxyPass: '', tryFiles: '', returnCode: '', rewrite: '',
    index: '', expires: '', deny: '', allow: '',
    proxyHost: '', proxyRealIp: '', proxyXff: '', proxyProto: ''
  }
}

function populateUpstreamsServers(data) {
  populating = true

  // 保存当前 UI 状态（展开/收起）
  const openUpstreams = new Set(upstreams.filter(u => u._open).map(u => u.name))
  const openLocations = new Map()
  for (const srv of servers) {
    const open = (srv.locations || []).filter(l => l._open).map(l => l.path)
    if (open.length) openLocations.set(srv.serverName + ':' + srv.listen, new Set(open))
  }

  upstreams.splice(0)
  if (data.upstreams) {
    for (const up of data.upstreams) {
      upstreams.push({
        _open: openUpstreams.has(up.name),
        name: up.name || '',
        strategy: up.strategy || '',
        keepalive: up.keepalive || '',
        sourceFile: up.sourceFile || '',
        servers: (up.servers || []).map(s => ({ addr: s.addr || '', weight: s.weight || '', state: s.state || '' }))
      })
    }
  }

  servers.splice(0)
  if (data.servers) {
    for (const srv of data.servers) {
      servers.push({
        sourceFile: srv.sourceFile || '',
        listen: srv.listen || '80',
        serverName: srv.serverName || '',
        charset: srv.charset || '',
        root: srv.root || '',
        index: srv.index || '',
        accessLog: srv.accessLog || '',
        errorLog: srv.errorLog || '',
        clientMaxBodySize: srv.clientMaxBodySize || '',
        ssl: srv.ssl || false,
        sslCert: srv.sslCert || '',
        sslKey: srv.sslKey || '',
        sslProtocols: srv.sslProtocols || 'TLSv1.2 TLSv1.3',
        sslCiphers: srv.sslCiphers || '',
        sslPreferServerCiphers: srv.sslPreferServerCiphers || false,
        sslSessionTimeout: srv.sslSessionTimeout || '',
        sslSessionCache: srv.sslSessionCache || '',
        sslRedirect: srv.sslRedirect || false,
        autoindex: srv.autoindex || false,
        gzip: srv.gzip ? { ...srv.gzip, types: srv.gzip.types || '' } : { on: false, minLength: '1024', compLevel: '6', types: '', vary: false, proxied: 'off', buffersNum: '4', buffersSize: '8k', httpVersion: '1.1' },
        proxyBuffering: srv.proxyBuffering !== false,
        proxyConnectTimeout: srv.proxyConnectTimeout || '',
        proxyReadTimeout: srv.proxyReadTimeout || '',
        proxySendTimeout: srv.proxySendTimeout || '',
        proxyBufferSize: srv.proxyBufferSize || '',
        sendfile: srv.sendfile || '',
        tcpNopush: srv.tcpNopush || '',
        tcpNodelay: srv.tcpNodelay || '',
        keepaliveTimeout: srv.keepaliveTimeout || '',
        keepaliveRequests: srv.keepaliveRequests || '',
        clientBodyTimeout: srv.clientBodyTimeout || '',
        clientHeaderTimeout: srv.clientHeaderTimeout || '',
        typesHashMaxSize: srv.typesHashMaxSize || '',
        serverTokens: srv.serverTokens || '',
        addHeaders: srv.addHeaders || [],
        locations: (srv.locations || []).map(loc => {
          const srvKey = (srv.serverName || '') + ':' + (srv.listen || '')
          const openSet = openLocations.get(srvKey)
          return {
          _open: openSet ? openSet.has(loc.path) : false,
          path: loc.path || '/',
          type: loc.type || 'prefix',
          root: loc.root || '',
          alias: loc.alias || '',
          proxyPass: loc.proxyPass || '',
          tryFiles: loc.tryFiles || '',
          returnCode: loc.returnCode || '',
          rewrite: loc.rewrite || '',
          index: loc.index || '',
          expires: loc.expires || '',
          deny: loc.deny || '',
          allow: loc.allow || '',
          proxyHost: loc.proxyHost || '',
          proxyRealIp: loc.proxyRealIp || '',
          proxyXff: loc.proxyXff || '',
          proxyProto: loc.proxyProto || ''
          }
        })
      })
    }
  }
  if (activeServer.value >= servers.length) activeServer.value = Math.max(0, servers.length - 1)

  nextTick(() => {
    // 更新快照必须在 populating=false 之前，否则 watcher 会用旧快照比较
    lastUpstreamsJson = serializeForSave(upstreams)
    lastServersJson = serializeForSave(servers)
    populating = false
    loaded = true
  })
}

// ---- Data Loading ----
async function fetchConfig() {
  try {
    const res = await fetch('/api/v1/nginx/config')
    const json = await res.json()
    if (json.code === 200 && json.data) {
      const data = json.data
      populating = true

      configFiles.splice(0)
      if (data.configFiles) {
        for (const f of data.configFiles) configFiles.push(f)
      }

      hasMainBlocks.value = data.hasMainBlocks || false

      // Auto-select first file
      if (activeFile.value) {
        // Verify activeFile still exists
        const exists = configFiles.some(f => f.name === activeFile.value) ||
                       (hasMainBlocks.value && activeFile.value === mainFileName.value)
        if (!exists) activeFile.value = ''
      }
      if (!activeFile.value) {
        if (hasMainBlocks.value) {
          activeFile.value = mainFileName.value
        } else if (configFiles.length > 0) {
          activeFile.value = configFiles[0].name
        }
      }

      nextTick(() => {
        populating = false
        loaded = true
      })

      // Load file data for active file
      if (activeFile.value) {
        await fetchFileData(activeFile.value)
      }
    }
  } catch (e) {
    console.error('加载配置失败:', e)
    addLog(false, '加载配置失败: ' + e.message)
  }
}

async function fetchFileData(name) {
  if (!name) return
  try {
    const res = await fetch('/api/v1/nginx/config/file?name=' + encodeURIComponent(name))
    const json = await res.json()
    if (json.code === 200 && json.data) {
      populateUpstreamsServers(json.data)
    }
  } catch (e) {
    console.error('加载文件数据失败:', e)
    addLog(false, '加载文件数据失败: ' + e.message)
  }
}

function selectFile(name) {
  if (activeFile.value === name) return
  activeFile.value = name
  activeServer.value = 0
  fetchFileData(name)
}

onMounted(fetchConfig)

// ---- Save (Per-file Upstreams + Servers) ----
async function saveFileBlocks() {
  if (!loaded || populating || !activeFile.value) return
  saveStatus.state = 'pending'
  try {
    const body = {
      name: activeFile.value,
      upstreams: upstreams.map(up => ({
        name: up.name, strategy: up.strategy, keepalive: up.keepalive,
        sourceFile: up.sourceFile, servers: up.servers.filter(s => s.addr && s.addr.trim())
      })),
      servers: servers.map(srv => ({
        ...srv,
        locations: srv.locations.map(loc => {
          const { _open, ...rest } = loc
          return rest
        })
      }))
    }
    const res = await fetch('/api/v1/nginx/config/file', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    const json = await res.json()
    if (json.code === 200) {
      saveStatus.state = 'success'
      saveStatus.message = ''
      saveStatus.time = new Date()
      addLog(true, '[' + activeFile.value + '] 配置已保存')
      await fetchConfig()
    } else {
      saveStatus.state = 'error'
      saveStatus.message = json.message
      saveStatus.time = new Date()
      addLog(false, '保存失败: ' + json.message)
    }
  } catch (e) {
    saveStatus.state = 'error'
    saveStatus.message = e.message
    saveStatus.time = new Date()
    addLog(false, '保存异常: ' + e.message)
  }
}

const saveFileBlocksDebounced = debounce(saveFileBlocks, 1500)

// 用序列化做脏检查，忽略 _open 等 UI 状态
let lastUpstreamsJson = serializeForSave(upstreams)
let lastServersJson = serializeForSave(servers)

watch(upstreams, () => {
  if (!populating && loaded) {
    const json = serializeForSave(upstreams)
    if (json !== lastUpstreamsJson) {
      lastUpstreamsJson = json
      saveFileBlocksDebounced()
    }
  }
}, { deep: true })

watch(servers, () => {
  if (!populating && loaded) {
    const json = serializeForSave(servers)
    if (json !== lastServersJson) {
      lastServersJson = json
      saveFileBlocksDebounced()
    }
  }
}, { deep: true })

// 开启 SSL 时自动关闭 ssl_redirect，避免 HTTPS→HTTPS 死循环
watch(() => currentServer.value?.ssl, (val) => {
  if (val && currentServer.value?.sslRedirect) {
    currentServer.value.sslRedirect = false
  }
})

// ---- Actions ----
async function toggleFile(f) {
  try {
    const res = await fetch('/api/v1/nginx/config/file/toggle', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: f.name })
    })
    const json = await res.json()
    if (json.code === 200) {
      addLog(true, f.name + ' 状态已切换')
      const wasActive = activeFile.value === f.name
      await fetchConfig()
      if (wasActive) {
        // Select the renamed file
        const newName = f.name.endsWith('.conf')
          ? f.name.replace(/\.conf$/, '.conf_off')
          : f.name.replace(/\.conf_off$/, '.conf')
        activeFile.value = newName
        fetchFileData(newName)
      }
    } else {
      addLog(false, '切换失败: ' + json.message)
    }
  } catch (e) {
    addLog(false, '切换异常: ' + e.message)
  }
}

async function deleteFile(f) {
  if (!confirm('确定删除 ' + f.name + ' ?')) return
  try {
    const res = await fetch('/api/v1/nginx/config/file?name=' + encodeURIComponent(f.name), {
      method: 'DELETE'
    })
    const json = await res.json()
    if (json.code === 200) {
      addLog(true, f.name + ' 已删除')
      if (activeFile.value === f.name) activeFile.value = ''
      await fetchConfig()
    } else {
      addLog(false, '删除失败: ' + json.message)
    }
  } catch (e) {
    addLog(false, '删除异常: ' + e.message)
  }
}

async function createFile() {
  if (!newFile.name.trim()) {
    addLog(false, '文件名不能为空')
    return
  }
  try {
    const res = await fetch('/api/v1/nginx/config/file', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: newFile.name, port: newFile.port, domain: newFile.domain })
    })
    const json = await res.json()
    if (json.code === 200) {
      addLog(true, newFile.name + ' 已创建')
      showNewFileDialog.value = false
      newFile.name = ''
      newFile.port = '80'
      newFile.domain = ''
      await fetchConfig()
    } else {
      addLog(false, '创建失败: ' + json.message)
    }
  } catch (e) {
    addLog(false, '创建异常: ' + e.message)
  }
}

async function clearMainBlocks() {
  if (!confirm('确定清除 nginx.conf 中的 Server 和 Upstream 配置？')) return
  try {
    const res = await fetch('/api/v1/nginx/config/clear-main-blocks', { method: 'POST' })
    const json = await res.json()
    if (json.code === 200) {
      addLog(true, 'nginx.conf 代理配置已清除')
      activeFile.value = ''
      await fetchConfig()
    } else {
      addLog(false, '清除失败: ' + json.message)
    }
  } catch (e) {
    addLog(false, '清除异常: ' + e.message)
  }
}

function addUpstream() {
  upstreams.push({ _open: true, name: '', strategy: '', keepalive: '', sourceFile: activeFile.value, servers: [] })
  nextTick(() => {
    const el = document.querySelector('.upstream-body')
    if (el) el.scrollTop = el.scrollHeight
  })
}
function removeUpstream(i) {
  upstreams.splice(i, 1)
}
function addUpstreamServer(up) {
  up.servers.push({ addr: '', weight: '', state: '' })
  nextTick(() => {
    const el = document.querySelector('.upstream-body')
    if (el) el.scrollTop = el.scrollHeight
  })
}

function addServer() {
  const srv = defaultServer()
  srv.sourceFile = activeFile.value
  servers.push(srv)
  activeServer.value = servers.length - 1
}
function removeServer(i) {
  servers.splice(i, 1)
  if (activeServer.value >= servers.length) activeServer.value = Math.max(0, servers.length - 1)
}

function addResponseHeader() {
  if (!currentServer.value) return
  currentServer.value.addHeaders.push('')
}

function addLocation() {
  if (!currentServer.value) return
  currentServer.value.locations.push(defaultLocation())
}
function removeLocation(i) {
  currentServer.value.locations.splice(i, 1)
}

function locTypeBadge(type) {
  const map = { prefix: '', exact: 'badge-success', regex: 'badge-warning', regexNocase: 'badge-warning' }
  return map[type] || ''
}
</script>

<style scoped>
.nginx-config-layout {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 36px);
  margin: calc(-1 * var(--space-lg));
}
.nginx-config-main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.nginx-config {
  display: flex;
  gap: var(--space-md);
  min-width: 0;
  padding: var(--space-lg);
}

/* Left Panel */
.config-list-panel {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.config-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xs);
}
.config-item {
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
  margin-bottom: 1px;
}
.config-item:hover {
  background: var(--bg-hover);
}
.config-item.active {
  background: var(--accent-bg);
}
.config-item.disabled {
  opacity: 0.5;
}
.config-item-main {
  border: 1px solid var(--warning);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-sm);
}
.config-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}
.config-item-name {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.config-item-actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}
.config-item-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 2px;
}
.config-empty {
  text-align: center;
  padding: var(--space-xxl);
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}

/* Right Panel */
.config-detail-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  min-width: 0;
  min-height: 0;
}

/* Top Section */
.detail-top {
  display: flex;
  gap: var(--space-md);
  flex: 0 0 auto;
}
.common-card {
  flex: 1;
  min-width: 0;
}
.upstream-card-full {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.upstream-body {
  overflow-y: auto;
  max-height: 280px;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  align-items: flex-start;
}
.upstream-body .upstream-item {
  flex: 1 1 320px;
  max-width: 100%;
  margin-bottom: 0;
}

/* Upstream Item */
.upstream-item {
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-sm);
  overflow: hidden;
}
.upstream-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md);
  cursor: pointer;
  background: var(--bg-secondary);
}
.upstream-header:hover {
  background: var(--bg-hover);
}
.upstream-name {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-primary);
  font-family: var(--font-mono);
}
.upstream-detail {
  padding: var(--space-md);
  border-top: 1px solid var(--border-secondary);
}
.upstream-servers {
  margin-top: var(--space-sm);
}
.upstream-srv-row {
  display: flex;
  gap: var(--space-xs);
  margin-bottom: var(--space-xs);
  align-items: center;
}
.upstream-srv-row input {
  flex: 1;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}

/* Bottom Section */
.detail-bottom {
  flex: 1;
  display: flex;
  gap: var(--space-md);
  min-height: 0;
}
.server-list-card {
  width: 200px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.server-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xs);
}
.server-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
  gap: var(--space-sm);
}
.server-item:hover {
  background: var(--bg-hover);
}
.server-item.active {
  background: var(--accent-bg);
}
.server-item-name {
  font-size: var(--font-size-xs);
  font-family: var(--font-mono);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

/* Server Detail */
.server-detail-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}
.server-empty-card {
  flex: 1;
}
.server-detail-body {
  overflow-y: auto;
}

/* Section Title */
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--accent);
  padding: var(--space-md) 0 var(--space-xs);
  border-bottom: 1px solid var(--border-secondary);
  margin-top: var(--space-md);
  margin-bottom: var(--space-md);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.section-title:first-child {
  margin-top: 0;
}

/* Form Grid */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-sm) var(--space-lg);
}
.form-row-inline {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}
.form-row-sep {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  flex-shrink: 0;
}
.form-row-inline select {
  flex: 1;
}

/* Location */
.location-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}
.location-item {
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.location-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md);
  cursor: pointer;
  background: var(--bg-secondary);
}
.location-header:hover {
  background: var(--bg-hover);
}
.location-path {
  font-size: var(--font-size-sm);
  font-family: var(--font-mono);
  color: var(--text-primary);
}
.location-detail {
  padding: var(--space-md);
  border-top: 1px solid var(--border-secondary);
}

.empty-hint {
  text-align: center;
  padding: var(--space-lg);
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal-card {
  width: 420px;
  max-width: 90vw;
}
</style>
