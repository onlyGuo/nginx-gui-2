<template>
  <AppLayout>
    <PathGuard>
    <div class="nginx-config">
      <!-- Left: Config File List -->
      <div class="config-list-panel card">
        <div class="card-header">
          <span>配置文件</span>
          <button class="btn btn-sm btn-icon" @click="addConfig" title="新建配置">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          </button>
        </div>
        <div class="config-list">
          <div
            v-for="f in configFiles"
            :key="f.name"
            class="config-item"
            :class="{ active: activeFile === f.name }"
            @click="activeFile = f.name"
          >
            <div class="config-item-row">
              <span class="config-item-name">{{ f.name }}</span>
              <label class="switch" @click.stop>
                <input type="checkbox" v-model="f.enabled" /><span class="switch-slider"></span>
              </label>
            </div>
            <div class="config-item-meta">
              <span class="text-xs text-tertiary">{{ f.desc }}</span>
              <span class="text-xs text-tertiary">{{ f.time }}</span>
            </div>
          </div>
          <div v-if="configFiles.length === 0" class="config-empty">暂无配置文件</div>
        </div>
      </div>

      <!-- Right: Detail -->
      <div class="config-detail-panel">
        <!-- Top: Common + Upstream -->
        <div class="detail-top">
          <!-- Common Config -->
          <div class="card common-card">
            <div class="card-header">公共配置</div>
            <div class="card-body">
              <div class="form-grid">
                <div class="form-group">
                  <label class="form-label">默认MIME类型(default_type)</label>
                  <BaseSelect v-model="common.defaultType" :options="defaultTypeOpts" />
                </div>
                <div class="form-group">
                  <label class="form-label">字符集(charset)</label>
                  <input v-model="common.charset" placeholder="utf-8" />
                </div>
                <div class="form-group">
                  <label class="form-label">DNS解析器(resolver)</label>
                  <input v-model="common.resolver" placeholder="8.8.8.8 8.8.4.4" />
                </div>
                <div class="form-group">
                  <label class="form-label">代理连接超时(proxy_connect_timeout)</label>
                  <input v-model="common.proxyConnectTimeout" placeholder="30s" />
                </div>
                <div class="form-group">
                  <label class="form-label">代理读取超时(proxy_read_timeout)</label>
                  <input v-model="common.proxyReadTimeout" placeholder="60s" />
                </div>
                <div class="form-group">
                  <label class="form-label">代理发送超时(proxy_send_timeout)</label>
                  <input v-model="common.proxySendTimeout" placeholder="60s" />
                </div>
                <div class="form-group">
                  <label class="form-label">代理缓冲(proxy_buffering)</label>
                  <label class="switch"><input type="checkbox" v-model="common.proxyBuffering" /><span class="switch-slider"></span></label>
                </div>
                <div class="form-group">
                  <label class="form-label">代理缓冲区(proxy_buffer_size)</label>
                  <input v-model="common.proxyBufferSize" placeholder="4k" />
                </div>
              </div>
            </div>
          </div>

          <!-- Upstream -->
          <div class="card upstream-card">
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
                      <input v-model="srv.weight" placeholder="weight" style="width:70px" />
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
                  <input v-model="currentServer.charset" placeholder="utf-8" />
                </div>
                <div class="form-group">
                  <label class="form-label">根目录(root)</label>
                  <PathSelector v-model="currentServer.root" type="dir" placeholder="/var/www/html" />
                </div>
                <div class="form-group">
                  <label class="form-label">默认索引(index)</label>
                  <input v-model="currentServer.index" placeholder="index.html index.htm" />
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
                  <label class="form-label">HTTP跳转HTTPS(ssl_redirect)</label>
                  <label class="switch"><input type="checkbox" v-model="currentServer.sslRedirect" :disabled="!currentServer.ssl" /><span class="switch-slider"></span></label>
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
                  <label class="form-label">最小压缩长度(gzip_min_length)</label>
                  <input v-model="currentServer.gzip.minLength" placeholder="1024" :disabled="!currentServer.gzip.on" />
                </div>
                <div class="form-group">
                  <label class="form-label">压缩级别(gzip_comp_level)</label>
                  <BaseSelect v-model="currentServer.gzip.compLevel" :options="compLevelOpts" :disabled="!currentServer.gzip.on" />
                </div>
                <div class="form-group">
                  <label class="form-label">压缩类型(gzip_types)</label>
                  <input v-model="currentServer.gzip.types" placeholder="text/plain application/json ..." :disabled="!currentServer.gzip.on" />
                </div>
                <div class="form-group">
                  <label class="form-label">Vary头(gzip_vary)</label>
                  <label class="switch"><input type="checkbox" v-model="currentServer.gzip.vary" :disabled="!currentServer.gzip.on" /><span class="switch-slider"></span></label>
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

              <!-- Extra -->
              <div class="section-title">其他配置</div>
              <div class="form-grid">
                <div class="form-group">
                  <label class="form-label">目录浏览(autoindex)</label>
                  <label class="switch"><input type="checkbox" v-model="currentServer.autoindex" /><span class="switch-slider"></span></label>
                </div>
                <div class="form-group">
                  <label class="form-label">代理缓冲(proxy_buffering)</label>
                  <label class="switch"><input type="checkbox" v-model="currentServer.proxyBuffering" /><span class="switch-slider"></span></label>
                </div>
                <div class="form-group">
                  <label class="form-label">响应头(add_header)</label>
                  <input v-model="currentServer.addHeader" placeholder="X-Frame-Options SAMEORIGIN" />
                </div>
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
                      <div class="form-group">
                        <label class="form-label">代理目标-Upstream(proxy_pass)</label>
                        <BaseSelect v-model="loc.proxyPass" :options="proxyPassOpts" placeholder="不代理" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">代理目标-自定义(proxy_pass)</label>
                        <input v-model="loc.proxyPassCustom" placeholder="http://127.0.0.1:3000" />
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
    </PathGuard>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import AppLayout from '../components/layout/AppLayout.vue'
import PathGuard from '../components/common/PathGuard.vue'
import PathSelector from '../components/common/PathSelector.vue'
import BaseSelect from '../components/common/BaseSelect.vue'
import BaseCombo from '../components/common/BaseCombo.vue'

const defaultTypeOpts = [
  { value: 'application/octet-stream', label: 'application/octet-stream', description: '二进制流，通用默认类型' },
  { value: 'text/plain', label: 'text/plain', description: '纯文本格式' },
  { value: 'text/html', label: 'text/html', description: 'HTML 网页格式' }
]
const upstreamStrategyOpts = [
  { value: '', label: '默认轮询', description: '按顺序依次将请求分配到后端服务器' },
  { value: 'weight', label: '权重 (weight)', description: '按权重比例分配请求，权重越高分配越多' },
  { value: 'ip_hash', label: 'ip_hash', description: '按客户端 IP 哈希分配，保持会话一致性' },
  { value: 'least_conn', label: 'least_conn', description: '优先分配给当前连接数最少的服务器' }
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
  { value: 'regexNocase', label: '正则不区分大小写 (~*)', description: '正则表达式匹配，不区分大小写' },
  { value: 'proxy', label: '反向代理', description: '将请求代理到后端 upstream 服务器' },
  { value: 'static', label: '静态文件', description: '直接返回静态文件资源' },
  { value: 'redirect', label: '重定向', description: '返回 301/302 重定向响应' }
]
const sslProtocolsOpts = [
  { value: 'TLSv1.2 TLSv1.3', label: 'TLSv1.2 + TLSv1.3', description: '推荐配置，兼容性与安全性兼顾' },
  { value: 'TLSv1.3', label: '仅 TLSv1.3', description: '最高安全性，较旧客户端不支持' },
  { value: 'TLSv1.1 TLSv1.2 TLSv1.3', label: 'TLSv1.1 ~ TLSv1.3', description: '兼容旧客户端，安全性较低' }
]

const proxyPassOpts = computed(() =>
  upstreams.map(up => ({ value: 'http://' + up.name, label: up.name, description: '代理到 upstream: ' + up.name + ' (含 ' + up.servers.length + ' 台服务器)' }))
)

// ---- Config Files ----
const configFiles = reactive([
  { name: 'default.conf', desc: '默认站点配置', time: '2024-01-15 10:30', enabled: true },
  { name: 'proxy.conf', desc: '反向代理配置', time: '2024-01-14 16:20', enabled: true },
  { name: 'ssl.conf', desc: 'HTTPS 配置', time: '2024-01-13 09:00', enabled: false }
])
const activeFile = ref('default.conf')

function addConfig() {
  const name = 'new_' + (configFiles.length + 1) + '.conf'
  configFiles.push({ name, desc: '新建配置', time: new Date().toLocaleString(), enabled: true })
  activeFile.value = name
}

// ---- Common Config ----
const common = reactive({
  defaultType: 'application/octet-stream',
  charset: 'utf-8',
  resolver: '8.8.8.8 8.8.4.4',
  proxyConnectTimeout: '30s',
  proxyReadTimeout: '60s',
  proxySendTimeout: '60s',
  proxyBuffering: true,
  proxyBufferSize: '4k'
})

// ---- Upstream ----
const upstreams = reactive([
  {
    _open: false,
    name: 'backend',
    strategy: 'least_conn',
    keepalive: '32',
    servers: [
      { addr: '127.0.0.1:8080', weight: '', state: '' },
      { addr: '127.0.0.1:8081', weight: '', state: '' }
    ]
  }
])

function addUpstream() {
  upstreams.push({ _open: true, name: '', strategy: '', keepalive: '', servers: [] })
}
function removeUpstream(i) {
  upstreams.splice(i, 1)
}
function addUpstreamServer(up) {
  up.servers.push({ addr: '', weight: '', state: '' })
}

// ---- Servers ----
const servers = reactive([
  {
    listen: '80',
    serverName: 'example.com',
    charset: 'utf-8',
    root: '/var/www/html',
    index: 'index.html',
    accessLog: '/var/log/nginx/example.access.log',
    errorLog: '/var/log/nginx/example.error.log',
    clientMaxBodySize: '10m',
    ssl: false,
    sslCert: '',
    sslKey: '',
    sslProtocols: 'TLSv1.2 TLSv1.3',
    sslRedirect: false,
    autoindex: false,
    gzip: { on: true, minLength: '1024', compLevel: '6', types: 'text/plain application/json application/javascript text/css', vary: true, proxied: 'any', buffersNum: '4', buffersSize: '8k', httpVersion: '1.1' },
    proxyBuffering: true,
    addHeader: '',
    locations: [
      {
        _open: false,
        path: '/',
        type: 'static',
        root: '/var/www/html',
        proxyPass: '',
        proxyPassCustom: '',
        tryFiles: '$uri $uri/ /index.html',
        returnCode: '',
        rewrite: '',
        index: 'index.html',
        expires: '',
        deny: '',
        allow: '',
        proxyHost: '',
        proxyRealIp: '',
        proxyXff: '',
        proxyProto: ''
      }
    ]
  },
  {
    listen: '443',
    serverName: 'api.example.com',
    charset: 'utf-8',
    root: '',
    index: '',
    accessLog: '/var/log/nginx/api.access.log',
    errorLog: '/var/log/nginx/api.error.log',
    clientMaxBodySize: '50m',
    ssl: true,
    sslCert: '/etc/ssl/certs/api.pem',
    sslKey: '/etc/ssl/private/api.key',
    sslProtocols: 'TLSv1.2 TLSv1.3',
    sslRedirect: true,
    autoindex: false,
    gzip: { on: true, minLength: '256', compLevel: '5', types: 'text/plain application/json text/css application/javascript', vary: true, proxied: 'any', buffersNum: '4', buffersSize: '8k', httpVersion: '1.1' },
    proxyBuffering: true,
    addHeader: 'X-Frame-Options SAMEORIGIN',
    locations: [
      {
        _open: false,
        path: '/',
        type: 'proxy',
        root: '',
        proxyPass: 'http://backend',
        proxyPassCustom: '',
        tryFiles: '',
        returnCode: '',
        rewrite: '',
        index: '',
        expires: '',
        deny: '',
        allow: '',
        proxyHost: '$host',
        proxyRealIp: '$remote_addr',
        proxyXff: '$proxy_add_x_forwarded_for',
        proxyProto: '$scheme'
      }
    ]
  }
])

const activeServer = ref(0)
const currentServer = computed(() => servers[activeServer.value] || null)

function addServer() {
  servers.push({
    listen: '80',
    serverName: '',
    charset: '',
    root: '',
    index: '',
    accessLog: '',
    errorLog: '',
    clientMaxBodySize: '',
    ssl: false,
    sslCert: '',
    sslKey: '',
    sslProtocols: '',
    sslRedirect: false,
    autoindex: false,
    gzip: { on: false, minLength: '1024', compLevel: '6', types: 'text/plain application/json application/javascript text/css', vary: true, proxied: 'any', buffersNum: '4', buffersSize: '8k', httpVersion: '1.1' },
    proxyBuffering: true,
    addHeader: '',
    locations: []
  })
  activeServer.value = servers.length - 1
}
function removeServer(i) {
  servers.splice(i, 1)
  if (activeServer.value >= servers.length) activeServer.value = Math.max(0, servers.length - 1)
}

function addLocation() {
  if (!currentServer.value) return
  currentServer.value.locations.push({
    _open: true,
    path: '/',
    type: 'prefix',
    root: '',
    proxyPass: '',
    proxyPassCustom: '',
    tryFiles: '',
    returnCode: '',
    rewrite: '',
    index: '',
    expires: '',
    deny: '',
    allow: '',
    proxyHost: '',
    proxyRealIp: '',
    proxyXff: '',
    proxyProto: ''
  })
}
function removeLocation(i) {
  currentServer.value.locations.splice(i, 1)
}

function locTypeBadge(type) {
  const map = { prefix: '', exact: 'badge-success', regex: 'badge-warning', regexNocase: 'badge-warning', proxy: 'badge-error', static: 'badge-success', redirect: '' }
  return map[type] || ''
}
</script>

<style scoped>
.nginx-config {
  display: flex;
  gap: var(--space-md);
  height: calc(100vh - 36px - var(--space-lg) * 2);
  min-height: 0;
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
.upstream-card {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.upstream-body {
  overflow-y: auto;
  max-height: 280px;
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
</style>
