<template>
  <AppLayout>
    <div class="basic-config">
      <!-- Path Config -->
      <div class="card">
        <div class="card-header">路径配置</div>
        <div class="card-body">
          <div class="path-grid">
            <div class="form-group">
              <label class="form-label">Nginx 可执行文件路径</label>
              <PathSelector v-model="paths.nginxBin" type="file" placeholder="/usr/sbin/nginx" hint="nginx 可执行文件的绝对路径" />
            </div>
            <div class="form-group">
              <label class="form-label">nginx.conf 路径</label>
              <PathSelector v-model="paths.nginxConf" type="file" placeholder="/etc/nginx/nginx.conf" hint="主配置文件路径" />
            </div>
            <div class="form-group">
              <label class="form-label">conf.d 目录路径（可选）</label>
              <PathSelector v-model="paths.confDir" type="dir" placeholder="/etc/nginx/conf.d" hint="子配置目录，留空则使用 nginx.conf 同级 conf.d" />
            </div>
          </div>
        </div>
      </div>

      <!-- Global Config -->
      <div class="card" v-if="valid">
        <div class="card-header">全局配置（nginx.conf）</div>
          <div class="card-body global-form">
            <!-- Main -->
            <div class="section-title">基本设置</div>
            <div class="form-grid">
              <div class="form-group">
                <label class="form-label">工作进程数(worker_processes)</label>
                <input v-model="global.workerProcesses" placeholder="auto" />
              </div>
              <div class="form-group">
                <label class="form-label">单进程连接数(worker_connections)</label>
                <input v-model="global.workerConnections" type="number" placeholder="1024" />
              </div>
              <div class="form-group">
                <label class="form-label">PID文件路径(pid)</label>
                <PathSelector v-model="global.pid" type="file" placeholder="/run/nginx.pid" />
              </div>
              <div class="form-group">
                <label class="form-label">错误日志路径(error_log)</label>
                <PathSelector v-model="global.errorLog" type="file" placeholder="/var/log/nginx/error.log" />
              </div>
              <div class="form-group">
                <label class="form-label">错误日志级别(error_log level)</label>
                <BaseSelect v-model="global.errorLogLevel" :options="logLevelOpts" />
              </div>
              <div class="form-group">
                <label class="form-label">运行用户(user)</label>
                <input v-model="global.user" placeholder="www-data" />
              </div>
            </div>

            <!-- HTTP -->
            <div class="section-title">HTTP 设置</div>
            <div class="form-grid">
              <div class="form-group">
                <label class="form-label">零拷贝传输(sendfile)</label>
                <label class="switch"><input type="checkbox" v-model="global.sendfile" /><span class="switch-slider"></span></label>
              </div>
              <div class="form-group">
                <label class="form-label">TCP推送(tcp_nopush)</label>
                <label class="switch"><input type="checkbox" v-model="global.tcpNopush" /><span class="switch-slider"></span></label>
              </div>
              <div class="form-group">
                <label class="form-label">TCP延迟(tcp_nodelay)</label>
                <label class="switch"><input type="checkbox" v-model="global.tcpNodelay" /><span class="switch-slider"></span></label>
              </div>
              <div class="form-group">
                <label class="form-label">超时时间(keepalive_timeout)</label>
                <input v-model="global.keepaliveTimeout" type="number" placeholder="65" />
              </div>
              <div class="form-group">
                <label class="form-label">最大请求数(keepalive_requests)</label>
                <input v-model="global.keepaliveRequests" type="number" placeholder="1000" />
              </div>
              <div class="form-group">
                <label class="form-label">最大请求体(client_max_body_size)</label>
                <input v-model="global.clientMaxBodySize" placeholder="1m" />
              </div>
              <div class="form-group">
                <label class="form-label">请求体超时(client_body_timeout)</label>
                <input v-model="global.clientBodyTimeout" type="number" placeholder="60" />
              </div>
              <div class="form-group">
                <label class="form-label">请求头超时(client_header_timeout)</label>
                <input v-model="global.clientHeaderTimeout" type="number" placeholder="60" />
              </div>
              <div class="form-group">
                <label class="form-label">MIME哈希表大小(types_hash_max_size)</label>
                <input v-model="global.typesHashMaxSize" type="number" placeholder="2048" />
              </div>
              <div class="form-group">
                <label class="form-label">隐藏版本号(server_tokens)</label>
                <label class="switch"><input type="checkbox" v-model="global.serverTokens" /><span class="switch-slider"></span></label>
              </div>
            </div>

            <!-- Gzip -->
            <div class="section-title">Gzip 压缩</div>
            <div class="form-grid">
              <div class="form-group">
                <label class="form-label">启用压缩(gzip)</label>
                <label class="switch"><input type="checkbox" v-model="global.gzip" /><span class="switch-slider"></span></label>
              </div>
              <div class="form-group">
                <label class="form-label">最小压缩长度(gzip_min_length)</label>
                <input v-model="global.gzipMinLength" placeholder="1024" />
              </div>
              <div class="form-group">
                <label class="form-label">压缩级别(gzip_comp_level)</label>
                <BaseSelect v-model="global.gzipCompLevel" :options="compLevelOpts" />
              </div>
              <div class="form-group">
                <label class="form-label">压缩类型(gzip_types)</label>
                <BaseCombo v-model="global.gzipTypes" :options="gzipTypesOpts" placeholder="text/plain application/json ..." />
              </div>
              <div class="form-group">
                <label class="form-label">Vary头(gzip_vary)</label>
                <label class="switch"><input type="checkbox" v-model="global.gzipVary" /><span class="switch-slider"></span></label>
              </div>
              <div class="form-group">
                <label class="form-label">代理压缩(gzip_proxied)</label>
                <BaseSelect v-model="global.gzipProxied" :options="gzipProxiedOpts" />
              </div>
            </div>

            <!-- SSL -->
            <div class="section-title">SSL 设置</div>
            <div class="form-grid">
              <div class="form-group">
                <label class="form-label">协议版本(ssl_protocols)</label>
                <BaseCombo v-model="global.sslProtocols" :options="sslProtocolsOpts" placeholder="TLSv1.2 TLSv1.3" />
              </div>
              <div class="form-group">
                <label class="form-label">加密算法(ssl_ciphers)</label>
                <BaseCombo v-model="global.sslCiphers" :options="sslCiphersOpts" placeholder="HIGH:!aNULL:!MD5" />
              </div>
              <div class="form-group">
                <label class="form-label">服务端算法优先(ssl_prefer_server_ciphers)</label>
                <label class="switch"><input type="checkbox" v-model="global.sslPreferServerCiphers" /><span class="switch-slider"></span></label>
              </div>
              <div class="form-group">
                <label class="form-label">会话超时(ssl_session_timeout)</label>
                <input v-model="global.sslSessionTimeout" placeholder="1d" />
              </div>
              <div class="form-group">
                <label class="form-label">会话缓存(ssl_session_cache)</label>
                <BaseCombo v-model="global.sslSessionCache" :options="sslSessionCacheOpts" placeholder="shared:SSL:10m" />
              </div>
            </div>

            <!-- Logging -->
            <div class="section-title">日志设置</div>
            <div class="form-grid">
              <div class="form-group">
                <label class="form-label">访问日志路径(access_log)</label>
                <PathSelector v-model="global.accessLog" type="file" placeholder="/var/log/nginx/access.log" />
              </div>
              <div class="form-group">
                <label class="form-label">访问日志格式(access_log format)</label>
                <BaseCombo v-model="global.accessLogFormat" :options="accessLogFormatOpts" placeholder="combined" />
              </div>
              <div class="form-group">
                <label class="form-label">日志格式名称(log_format name)</label>
                <input v-model="global.logFormatName" placeholder="main" />
              </div>
              <div class="form-group">
                <label class="form-label">日志格式定义(log_format)</label>
                <BaseCombo v-model="global.logFormatDef" :options="logFormatDefOpts" placeholder='$remote_addr - $remote_user ...' />
              </div>
            </div>

            <!-- Events -->
            <div class="section-title">Events 设置</div>
            <div class="form-grid">
              <div class="form-group">
                <label class="form-label">事件模型(use)</label>
                <BaseSelect v-model="global.use" :options="eventModelOpts" />
              </div>
              <div class="form-group">
                <label class="form-label">多路接受(multi_accept)</label>
                <label class="switch"><input type="checkbox" v-model="global.multiAccept" /><span class="switch-slider"></span></label>
              </div>
            </div>
          </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import AppLayout from '../components/layout/AppLayout.vue'
import PathSelector from '../components/common/PathSelector.vue'
import BaseSelect from '../components/common/BaseSelect.vue'
import BaseCombo from '../components/common/BaseCombo.vue'
import { usePathValidation } from '../composables/usePathValidation'

const { valid, checking, check } = usePathValidation()

let saveTimer = null
function debounce(fn, ms) {
  return (...args) => {
    clearTimeout(saveTimer)
    saveTimer = setTimeout(() => fn(...args), ms)
  }
}

const logLevelOpts = [
  { value: 'debug', label: 'debug', description: '调试信息，输出最详细的日志' },
  { value: 'info', label: 'info', description: '一般信息，记录请求处理详情' },
  { value: 'notice', label: 'notice', description: '正常但值得注意的事件' },
  { value: 'warn', label: 'warn', description: '警告信息，可能存在潜在问题' },
  { value: 'error', label: 'error', description: '错误信息，请求处理失败' },
  { value: 'crit', label: 'crit', description: '严重错误，影响系统关键功能' },
  { value: 'alert', label: 'alert', description: '需要立即处理的紧急错误' },
  { value: 'emerg', label: 'emerg', description: '系统不可用，最严重的错误级别' }
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
const accessLogFormatOpts = [
  { value: 'combined', label: 'combined', description: '标准组合格式，包含 Referer 和 User-Agent' },
  { value: 'common', label: 'common', description: '通用日志格式，仅包含基本请求信息' },
  { value: 'main', label: 'main', description: '自定义 main 格式，在 log_format 中定义' }
]
const eventModelOpts = [
  { value: '', label: 'auto', description: '自动选择最优事件模型' },
  { value: 'epoll', label: 'epoll', description: 'Linux 高性能事件驱动模型' },
  { value: 'kqueue', label: 'kqueue', description: 'BSD/macOS 高性能事件模型' },
  { value: 'select', label: 'select', description: '通用跨平台模型，性能较低' },
  { value: 'poll', label: 'poll', description: '通用轮询模型，性能一般' }
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
const gzipTypesOpts = [
  { value: 'text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript image/svg+xml', label: '常用类型', description: 'CSS/JS/JSON/XML/SVG 等常见 Web 资源' },
  { value: '*/*', label: '所有类型', description: '对所有 MIME 类型启用压缩' },
  { value: 'text/plain text/css application/json', label: '最小集合', description: '仅 CSS/JSON 纯文本' }
]
const logFormatDefOpts = [
  { value: '$remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent "$http_referer" "$http_user_agent"', label: 'combined 格式', description: '标准 combined 日志格式' },
  { value: '$remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent', label: 'common 格式', description: '通用日志格式，不含 Referer 和 UA' },
  { value: '$remote_addr - $remote_user [$time_local] "$request" $status $body_bytes_sent "$http_referer" "$http_user_agent" $request_time $upstream_response_time', label: '带响应时间', description: '在 combined 基础上增加请求耗时和上游耗时' }
]

// ---- Path Config ----
const paths = reactive({
  nginxBin: '/usr/sbin/nginx',
  nginxConf: '/etc/nginx/nginx.conf',
  confDir: ''
})
let pathsPopulating = false

const savePaths = debounce(async () => {
  if (pathsPopulating) return
  try {
    await fetch('/api/v1/paths', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nginxBin: paths.nginxBin, nginxConf: paths.nginxConf, confDir: paths.confDir })
    })
    await check()
    if (valid.value) await fetchGlobalConfig()
  } catch (e) {
    console.error('保存路径配置失败:', e)
  }
}, 1000)

onMounted(async () => {
  try {
    const res = await fetch('/api/v1/paths')
    const json = await res.json()
    if (json.code === 200 && json.data) {
      pathsPopulating = true
      paths.nginxBin = json.data.nginxBin ?? ''
      paths.nginxConf = json.data.nginxConf ?? ''
      paths.confDir = json.data.confDir ?? ''
      pathsPopulating = false
    }
    await check()
    if (valid.value) await fetchGlobalConfig()
  } catch (e) {
    console.error('加载路径配置失败:', e)
  }
})

watch(paths, savePaths, { deep: true })

// ---- Global Config ----
// 表单字段 → API 配置路径的映射
const fieldMap = [
  { field: 'workerProcesses',        path: 'worker_processes',        toApi: v => v,        fromApi: v => v || 'auto' },
  { field: 'user',                   path: 'user',                    toApi: v => v,        fromApi: v => v || '' },
  { field: 'pid',                    path: 'pid',                     toApi: v => v,        fromApi: v => v || '' },
  { field: 'errorLog',               path: 'error_log',              toApi: v => v,        fromApi: v => v || '' },
  { field: 'errorLogLevel',          path: 'error_log_level',        toApi: v => v,        fromApi: v => v || 'error' },
  { field: 'workerConnections',      path: 'events.worker_connections', toApi: v => v,      fromApi: v => v || '1024' },
  { field: 'use',                    path: 'events.use',             toApi: v => v,        fromApi: v => v || '' },
  { field: 'multiAccept',            path: 'events.multi_accept',    toApi: v => v ? 'on' : 'off', fromApi: v => v === 'on' },
  { field: 'sendfile',               path: 'http.sendfile',          toApi: v => v ? 'on' : 'off', fromApi: v => v === 'on' },
  { field: 'tcpNopush',              path: 'http.tcp_nopush',        toApi: v => v ? 'on' : 'off', fromApi: v => v === 'on' },
  { field: 'tcpNodelay',             path: 'http.tcp_nodelay',       toApi: v => v ? 'on' : 'off', fromApi: v => v === 'on' },
  { field: 'keepaliveTimeout',       path: 'http.keepalive_timeout', toApi: v => v,        fromApi: v => v || '65' },
  { field: 'keepaliveRequests',      path: 'http.keepalive_requests',toApi: v => v,        fromApi: v => v || '1000' },
  { field: 'clientMaxBodySize',      path: 'http.client_max_body_size', toApi: v => v,     fromApi: v => v || '1m' },
  { field: 'clientBodyTimeout',      path: 'http.client_body_timeout',  toApi: v => v,     fromApi: v => v || '60' },
  { field: 'clientHeaderTimeout',    path: 'http.client_header_timeout',toApi: v => v,     fromApi: v => v || '60' },
  { field: 'typesHashMaxSize',       path: 'http.types_hash_max_size',  toApi: v => v,     fromApi: v => v || '2048' },
  { field: 'serverTokens',           path: 'http.server_tokens',     toApi: v => v ? 'on' : 'off', fromApi: v => v === 'on' },
  { field: 'gzip',                   path: 'http.gzip',              toApi: v => v ? 'on' : 'off', fromApi: v => v === 'on' },
  { field: 'gzipMinLength',          path: 'http.gzip_min_length',   toApi: v => v,        fromApi: v => v || '1024' },
  { field: 'gzipCompLevel',          path: 'http.gzip_comp_level',   toApi: v => v,        fromApi: v => v || '6' },
  { field: 'gzipTypes',              path: 'http.gzip_types',        toApi: v => v,        fromApi: v => v || '' },
  { field: 'gzipVary',               path: 'http.gzip_vary',         toApi: v => v ? 'on' : 'off', fromApi: v => v === 'on' },
  { field: 'gzipProxied',            path: 'http.gzip_proxied',      toApi: v => v,        fromApi: v => v || 'off' },
  { field: 'sslProtocols',           path: 'http.ssl_protocols',     toApi: v => v,        fromApi: v => v || '' },
  { field: 'sslCiphers',             path: 'http.ssl_ciphers',       toApi: v => v,        fromApi: v => v || '' },
  { field: 'sslPreferServerCiphers', path: 'http.ssl_prefer_server_ciphers', toApi: v => v ? 'on' : 'off', fromApi: v => v === 'on' },
  { field: 'sslSessionTimeout',      path: 'http.ssl_session_timeout', toApi: v => v,      fromApi: v => v || '1d' },
  { field: 'sslSessionCache',        path: 'http.ssl_session_cache', toApi: v => v,        fromApi: v => v || '' },
  { field: 'accessLog',              path: 'http.access_log',        toApi: v => v,        fromApi: v => v || '' },
  { field: 'accessLogFormat',        path: 'http.access_log_format', toApi: v => v,        fromApi: v => v || 'combined' },
  { field: 'logFormatName',          path: 'http.log_format_name',   toApi: v => v,        fromApi: v => v || '' },
  { field: 'logFormatDef',           path: 'http.log_format_def',    toApi: v => v,        fromApi: v => v || '' },
]

const defaultValues = Object.fromEntries(fieldMap.map(m => [m.field, m.fromApi(undefined)]))
const global = reactive({ ...defaultValues })
const globalAnchors = ref({})
const globalConfig = ref({})
let globalLoaded = false
let populating = false

function populateForm(config) {
  populating = true
  for (const m of fieldMap) {
    const keys = m.path.split('.')
    let section = keys.length > 1 ? config[keys[0]] : config.main
    const key = keys.length > 1 ? keys[1] : keys[0]
    const raw = section?.[key]
    global[m.field] = m.fromApi(raw)
  }
  populating = false
}

async function fetchGlobalConfig() {
  try {
    const res = await fetch('/api/v1/nginx/global-config')
    const json = await res.json()
    if (json.code === 200 && json.data) {
      globalConfig.value = json.data.config
      globalAnchors.value = json.data.anchors
      populateForm(json.data.config)
      globalLoaded = true
    }
  } catch (e) {
    console.error('加载全局配置失败:', e)
  }
}

function getRawValue(configPath) {
  const keys = configPath.split('.')
  const section = keys.length > 1 ? globalConfig.value[keys[0]] : globalConfig.value.main
  return section?.[keys.length > 1 ? keys[1] : keys[0]] ?? ''
}

function buildPatches() {
  if (!globalLoaded) return []
  const patches = []

  // 复合指令：多个字段共享同一 itemIndex，需要合并为一个 patch
  const compounds = [
    { paths: ['error_log', 'error_log_level'], combine: (vals) => vals.error_log + (vals.error_log_level ? ' ' + vals.error_log_level : '') },
    { paths: ['http.access_log', 'http.access_log_format'], combine: (vals) => vals['http.access_log'] + (vals['http.access_log_format'] ? ' ' + vals['http.access_log_format'] : '') },
    { paths: ['http.log_format_name', 'http.log_format_def'], combine: (vals) => vals['http.log_format_name'] + " '" + (vals['http.log_format_def'] || '') + "'" },
  ]
  const compoundPaths = new Set(compounds.flatMap(c => c.paths))

  for (const compound of compounds) {
    const anchor = globalAnchors.value[compound.paths[0]]
    const fieldValues = {}
    let changed = false
    for (const cp of compound.paths) {
      const m = fieldMap.find(f => f.path === cp)
      if (!m) continue
      fieldValues[cp] = m.toApi(global[m.field])
      if (fieldValues[cp] !== getRawValue(cp)) changed = true
    }
    if (changed) {
      patches.push({ path: compound.paths[0], itemIndex: anchor?.itemIndex ?? -1, value: compound.combine(fieldValues) })
    }
  }

  // 普通指令
  for (const m of fieldMap) {
    if (compoundPaths.has(m.path)) continue
    const newValue = m.toApi(global[m.field])
    if (newValue !== getRawValue(m.path)) {
      const anchor = globalAnchors.value[m.path]
      patches.push({ path: m.path, itemIndex: anchor?.itemIndex ?? -1, value: newValue })
    }
  }
  return patches
}

let saving = false

const saveGlobalConfig = debounce(async () => {
  const patches = buildPatches()
  if (patches.length === 0) return
  saving = true
  try {
    const res = await fetch('/api/v1/nginx/global-config', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ patches })
    })
    const json = await res.json()
    if (json.code === 200) {
      await fetchGlobalConfig()
    } else {
      console.error('保存全局配置失败:', json.message)
    }
  } catch (e) {
    console.error('保存全局配置失败:', e)
  } finally {
    saving = false
  }
}, 1500)

// 监听 global 表单变化，自动保存（populateForm 或保存周期中跳过）
watch(global, () => {
  if (!populating && !saving && globalLoaded) saveGlobalConfig()
}, { deep: true })
</script>

<style scoped>
.basic-config {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}
.path-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-md) var(--space-xl);
}
.path-config-card {
  position: sticky;
  top: 0;
  z-index: 101;
}
.path-grid .form-group:first-child {
  grid-column: 1 / -1;
}
.global-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.section-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--accent);
  padding: var(--space-md) 0 var(--space-xs);
  border-bottom: 1px solid var(--border-secondary);
  margin-top: var(--space-sm);
}
.section-title:first-child {
  margin-top: 0;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-md) var(--space-xl);
}
</style>
