<template>
  <AppLayout>
    <PathGuard>
      <div class="ssl-page">
        <div class="card acme-card">
          <div class="card-header">
            <span>ACME 客户端</span>
            <span class="status-badge" :class="acme.installed ? 'status-issued' : 'status-failed'">
              {{ acme.installed ? '可用' : '不可用' }}
            </span>
          </div>
          <div class="card-body acme-body">
            <div class="acme-main">
              <div class="acme-icon" :class="acme.installed ? 'acme-ok' : 'acme-missing'">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                  <path v-if="acme.installed" d="M9 12l2 2 4-4" />
                  <path v-else d="M12 8v5M12 17h.01" />
                </svg>
              </div>
              <div class="acme-info">
                <div class="acme-title">{{ acme.installed ? '内置 ACME 可用' : 'ACME 客户端不可用' }}</div>
                <div class="acme-desc">
                  {{ acme.installed ? '证书申请通过内置 ACME HTTP API 执行 DNS-01 验证，再同步到 Nginx 证书目录。' : '当前内置 ACME 客户端不可用，请检查后端依赖。' }}
                </div>
                <div class="acme-meta" v-if="acme.installed">
                  <code>{{ acme.path }}</code>
                  <span v-if="acme.version">{{ acme.version }}</span>
                </div>
                <div class="acme-msg" v-if="acmeMessage" :class="acmeMessageType === 'ok' ? 'msg-ok' : 'msg-err'">{{ acmeMessage }}</div>
              </div>
            </div>
            <div class="acme-actions">
              <button class="btn btn-sm" @click="fetchAcmeStatus" :disabled="acmeLoading">检测</button>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <span>添加证书</span>
            <span class="header-tip">Let's Encrypt</span>
          </div>
          <div class="card-body">
            <div class="form-grid">
              <div class="form-group">
                <label class="form-label">发行方</label>
                <BaseCombo v-model="form.issuer" :options="issuerOptions" placeholder="Let's Encrypt" />
              </div>
              <div class="form-group">
                <label class="form-label">账户名称</label>
                <input v-model.trim="form.accountName" type="email" placeholder="admin@your-domain.com" />
              </div>
              <div class="form-group">
                <label class="form-label">绑定域名</label>
                <input v-model.trim="form.domains" type="text" placeholder="example.com, www.example.com" />
              </div>
              <div class="form-group">
                <label class="form-label">存储基础目录</label>
                <PathSelector v-model="form.storagePath" type="dir" placeholder="/etc/nginx/ssl" hint="后端会按 域名_时间戳 自动创建证书子目录" />
              </div>
              <div class="form-group">
                <label class="form-label">DNS 服务商</label>
                <BaseCombo v-model="form.dnsProvider" :options="dnsProviderOptions" placeholder="aliyun" />
              </div>
              <div class="form-group" v-if="form.dnsProvider === 'cloudflare'">
                <label class="form-label">API Token</label>
                <input v-model.trim="form.dnsCredentialToken" type="password" placeholder="Cloudflare API Token" />
              </div>
              <template v-else>
                <div class="form-group">
                  <label class="form-label">AccessKey ID</label>
                  <input v-model.trim="form.dnsCredentialId" type="password" placeholder="DNS AccessKey ID" />
                </div>
                <div class="form-group">
                  <label class="form-label">AccessKey Secret</label>
                  <input v-model.trim="form.dnsCredentialSecret" type="password" placeholder="DNS AccessKey Secret" />
                </div>
              </template>
              <div class="form-group form-group-wide">
                <label class="form-label">备注</label>
                <input v-model.trim="form.remark" type="text" placeholder="可选，例如：官网证书" />
              </div>
              <div class="form-group renew-field">
                <label class="form-label">是否自动续签</label>
                <label class="switch">
                  <input type="checkbox" v-model="form.autoRenew" />
                  <span class="switch-slider"></span>
                </label>
              </div>
            </div>
            <div class="form-actions">
              <button class="btn btn-primary" @click="createCertificate" :disabled="creating || !canCreate || !acme.installed">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                {{ creating ? '申请中...' : '创建并申请' }}
              </button>
            </div>
            <div v-if="message" class="msg" :class="messageType === 'ok' ? 'msg-ok' : 'msg-err'">{{ message }}</div>
          </div>
        </div>

        <div class="card list-card">
          <div class="card-header">
            <span>证书列表</span>
            <div class="header-actions">
              <span class="cert-count">共 {{ total }} 张证书</span>
              <button class="btn btn-sm" @click="fetchCertificates" :disabled="loading" title="刷新列表">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
              </button>
            </div>
          </div>
          <div class="card-body cert-body">
            <table class="cert-table" v-if="certificates.length > 0">
              <thead>
                <tr>
                  <th>发行方</th>
                  <th>账户名称</th>
                  <th>绑定域名</th>
                  <th>实际存储目录</th>
                  <th>DNS</th>
                  <th>备注</th>
                  <th>证书信息</th>
                  <th>自动续签</th>
                  <th>签发时间</th>
                  <th>到期时间</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="cert in certificates" :key="cert.id">
                  <td>{{ cert.issuer }}</td>
                  <td><code>{{ cert.accountName }}</code></td>
                  <td class="domain-cell">{{ cert.domains }}</td>
                  <td><code>{{ cert.storagePath }}</code></td>
                  <td>{{ dnsProviderLabel(cert.dnsProvider) }}</td>
                  <td class="muted-cell">{{ cert.remark || '-' }}</td>
                  <td class="info-cell">{{ cert.certificateInfo || '-' }}</td>
                  <td>
                    <label class="switch" title="切换自动续签">
                      <input type="checkbox" :checked="cert.autoRenew" @change="toggleAutoRenew(cert, $event)" />
                      <span class="switch-slider"></span>
                    </label>
                  </td>
                  <td>{{ formatDate(cert.issuedAt) }}</td>
                  <td>{{ formatDate(cert.expiresAt) }}</td>
                  <td>
                    <span class="status-badge" :class="statusClass(cert)" :title="cert.lastMessage || ''">
                      {{ statusText(cert) }}
                    </span>
                  </td>
                  <td>
                    <button class="btn btn-ghost btn-sm" @click="renewCertificate(cert)" :disabled="renewingId === cert.id" title="立即续签">
                      {{ renewingId === cert.id ? '续签中...' : '续签' }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="empty-state">{{ loading ? '加载中...' : '暂无 SSL 证书' }}</div>
          </div>
          <div class="pagination" v-if="totalPages > 1">
            <button class="btn btn-sm" @click="changePage(page - 1)" :disabled="page <= 1">上一页</button>
            <span class="page-info">第 {{ page }} / {{ totalPages }} 页</span>
            <button class="btn btn-sm" @click="changePage(page + 1)" :disabled="page >= totalPages">下一页</button>
          </div>
        </div>
      </div>
    </PathGuard>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppLayout from '../components/layout/AppLayout.vue'
import PathGuard from '../components/common/PathGuard.vue'
import BaseCombo from '../components/common/BaseCombo.vue'
import PathSelector from '../components/common/PathSelector.vue'
import { api } from '../utils/api'

const issuerOptions = [
  { value: "Let's Encrypt", label: "Let's Encrypt", description: 'ACME 免费证书' }
]

const dnsProviderOptions = [
  { value: 'aliyun', label: '阿里云 DNS', description: 'Ali_Key / Ali_Secret' },
  { value: 'cloudflare', label: 'Cloudflare', description: 'API Token' },
  { value: 'tencent', label: '腾讯云 DNS', description: 'Tencent_SecretId / Tencent_SecretKey' }
]

const form = reactive({
  issuer: "Let's Encrypt",
  accountName: '',
  remark: '',
  domains: '',
  storagePath: '',
  dnsProvider: 'aliyun',
  dnsCredentialId: '',
  dnsCredentialSecret: '',
  dnsCredentialToken: '',
  autoRenew: true
})

const certificates = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const creating = ref(false)
const renewingId = ref(null)
const acmeLoading = ref(false)
const message = ref('')
const messageType = ref('ok')
const acmeMessage = ref('')
const acmeMessageType = ref('ok')

const acme = reactive({ installed: false, path: '', version: '', message: '' })

const canCreate = computed(() => {
  const basicReady = form.accountName && form.domains && form.storagePath && form.dnsProvider
  if (!basicReady) return false
  if (form.dnsProvider === 'cloudflare') return !!form.dnsCredentialToken
  return !!form.dnsCredentialId && !!form.dnsCredentialSecret
})

async function fetchCertificates() {
  loading.value = true
  try {
    const res = await api(`/api/v1/certificates?page=${page.value}&size=${size.value}`)
    const json = await res.json()
    if (json.code === 200 && json.data) {
      certificates.value = json.data.items || []
      total.value = json.data.total || 0
      totalPages.value = json.data.totalPages || 0
      if (page.value > totalPages.value && totalPages.value > 0) {
        page.value = totalPages.value
        await fetchCertificates()
      }
    }
  } catch (e) {
    console.error('获取证书列表失败:', e)
  } finally {
    loading.value = false
  }
}

async function fetchAcmeStatus() {
  acmeLoading.value = true
  try {
    const res = await api('/api/v1/certificates/acme/status')
    const json = await res.json()
    if (json.code === 200 && json.data) {
      Object.assign(acme, json.data)
      acmeMessage.value = acme.installed ? '检测完成，内置 ACME 可用' : '内置 ACME 客户端不可用'
      acmeMessageType.value = acme.installed ? 'ok' : 'err'
    }
  } catch (e) {
    acmeMessage.value = '检测失败: ' + e.message
    acmeMessageType.value = 'err'
  } finally {
    acmeLoading.value = false
  }
}

async function createCertificate() {
  if (!canCreate.value) return
  if (!acme.installed) {
    message.value = '内置 ACME 客户端不可用'
    messageType.value = 'err'
    return
  }
  message.value = ''
  creating.value = true
  try {
    const res = await api('/api/v1/certificates', {
      method: 'POST',
      body: JSON.stringify({ ...form })
    })
    const json = await res.json()
    if (json.code === 200) {
      message.value = '证书申请成功，文件已保存到指定位置'
      messageType.value = 'ok'
      form.accountName = ''
      form.remark = ''
      form.domains = ''
      form.storagePath = ''
      form.dnsCredentialId = ''
      form.dnsCredentialSecret = ''
      form.dnsCredentialToken = ''
      form.autoRenew = true
      page.value = 1
      fetchCertificates()
    } else {
      message.value = json.message || '创建失败'
      messageType.value = 'err'
    }
  } catch (e) {
    message.value = '请求失败: ' + e.message
    messageType.value = 'err'
  } finally {
    creating.value = false
  }
}

async function toggleAutoRenew(cert, event) {
  const nextValue = event.target.checked
  const oldValue = cert.autoRenew
  cert.autoRenew = nextValue
  try {
    const res = await api(`/api/v1/certificates/${cert.id}/auto-renew`, {
      method: 'PUT',
      body: JSON.stringify({ autoRenew: nextValue })
    })
    const json = await res.json()
    if (json.code !== 200) {
      cert.autoRenew = oldValue
      alert(json.message || '更新失败')
    }
  } catch (e) {
    cert.autoRenew = oldValue
    alert('请求失败: ' + e.message)
  }
}

async function renewCertificate(cert) {
  renewingId.value = cert.id
  try {
    const res = await api(`/api/v1/certificates/${cert.id}/renew`, { method: 'POST' })
    const json = await res.json()
    if (json.code === 200) {
      await fetchCertificates()
    } else {
      alert(json.message || '续签失败')
    }
  } catch (e) {
    alert('请求失败: ' + e.message)
  } finally {
    renewingId.value = null
  }
}

function changePage(nextPage) {
  if (nextPage < 1 || nextPage > totalPages.value) return
  page.value = nextPage
  fetchCertificates()
}

function statusText(cert) {
  if (cert.status === 'FAILED') return '申请失败'
  if (cert.status === 'PENDING') return '申请中'
  if (isExpired(cert)) return '已过期'
  return '已签发'
}

function statusClass(cert) {
  if (cert.status === 'FAILED') return 'status-failed'
  if (cert.status === 'PENDING') return 'status-pending'
  return isExpired(cert) ? 'status-expired' : 'status-issued'
}

function dnsProviderLabel(provider) {
  const item = dnsProviderOptions.find(opt => opt.value === provider)
  return item ? item.label : (provider || '-')
}

function isExpired(cert) {
  return cert.status === 'EXPIRED' || (cert.expiresAt && new Date(cert.expiresAt).getTime() < Date.now())
}

function formatDate(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(fetchCertificates)
onMounted(fetchAcmeStatus)
</script>

<style scoped>
.ssl-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.acme-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
}
.acme-main {
  display: flex;
  align-items: flex-start;
  gap: var(--space-md);
  min-width: 0;
}
.acme-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
}
.acme-ok {
  background: var(--success-bg);
  color: var(--success);
}
.acme-missing {
  background: var(--warning-bg);
  color: var(--warning);
}
.acme-info {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  min-width: 0;
}
.acme-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
}
.acme-desc,
.acme-meta {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}
.acme-meta {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.acme-meta code {
  font-family: var(--font-mono);
  background: var(--bg-tertiary);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}
.acme-msg {
  font-size: var(--font-size-xs);
}
.acme-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr));
  gap: var(--space-sm) var(--space-lg);
}
.form-group-wide {
  grid-column: span 2;
}
.renew-field {
  align-items: flex-start;
}
.form-actions {
  margin-top: var(--space-md);
  display: flex;
  justify-content: flex-end;
}
.header-tip,
.cert-count,
.page-info {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  font-weight: normal;
  text-transform: none;
  letter-spacing: normal;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.cert-body {
  padding: 0 !important;
  overflow-x: auto;
}
.cert-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
  min-width: 1260px;
}
.cert-table th {
  text-align: left;
  padding: var(--space-sm) var(--space-md);
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-secondary);
  white-space: nowrap;
  font-size: var(--font-size-xs);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.cert-table td {
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--border-secondary);
  color: var(--text-primary);
  vertical-align: middle;
}
.cert-table tr:last-child td {
  border-bottom: none;
}
.cert-table tr:hover td {
  background: var(--bg-hover);
}
.cert-table code {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  background: var(--bg-tertiary);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}
.domain-cell,
.info-cell {
  max-width: 220px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.muted-cell {
  color: var(--text-tertiary) !important;
}
.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  font-weight: 500;
  white-space: nowrap;
}
.status-issued {
  background: var(--success-bg);
  color: var(--success);
}
.status-expired {
  background: var(--error-bg);
  color: var(--error);
}
.status-failed {
  background: var(--error-bg);
  color: var(--error);
}
.status-pending {
  background: var(--warning-bg);
  color: var(--warning);
}
.empty-state {
  text-align: center;
  padding: var(--space-xxl);
  color: var(--text-tertiary);
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-sm);
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid var(--border-secondary);
}
.msg {
  margin-top: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  font-size: var(--font-size-xs);
}
.msg-ok {
  background: var(--accent-bg);
  color: var(--accent);
}
.msg-err {
  background: var(--error-bg);
  color: var(--error);
}

@media (max-width: 900px) {
  .acme-body {
    align-items: flex-start;
    flex-direction: column;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
  .form-group-wide {
    grid-column: auto;
  }
}
</style>
