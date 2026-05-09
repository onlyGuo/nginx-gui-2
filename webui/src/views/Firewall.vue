<template>
  <AppLayout>
    <PathGuard>
      <div class="firewall-page">
        <!-- Status Bar -->
        <div class="card status-bar">
          <div class="status-left">
            <div class="status-icon" :class="status.enabled ? 'status-on' : 'status-off'">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              </svg>
            </div>
            <div class="status-info">
              <span class="status-tool">{{ status.tool || '检测中...' }}</span>
              <span class="status-version">{{ status.version || '' }}</span>
            </div>
          </div>
          <div class="status-right">
            <span class="status-label-text" :class="status.enabled ? 'text-success' : 'text-muted'">
              {{ status.enabled ? '已启用' : '已禁用' }}
            </span>
            <label class="toggle">
              <input type="checkbox" :checked="status.enabled" @change="toggleFirewall" />
              <span class="toggle-slider"></span>
            </label>
            <button class="btn btn-sm" @click="fetchRules" title="刷新规则">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
            </button>
          </div>
        </div>

        <!-- Add Rule Form -->
        <div class="card">
          <div class="card-header">添加规则</div>
          <div class="card-body">
            <div class="form-grid">
              <div class="form-group">
                <label class="form-label">端口</label>
                <input v-model="newRule.port" type="text" placeholder="80" />
              </div>
              <div class="form-group">
                <label class="form-label">协议</label>
                <select v-model="newRule.protocol">
                  <option value="tcp">TCP</option>
                  <option value="udp">UDP</option>
                </select>
              </div>
              <div class="form-group">
                <label class="form-label">动作</label>
                <select v-model="newRule.action">
                  <option value="allow">允许</option>
                  <option value="deny">拒绝</option>
                </select>
              </div>
              <div class="form-group">
                <label class="form-label">来源 IP</label>
                <input v-model="newRule.source" type="text" placeholder="Any" />
              </div>
            </div>
            <div style="margin-top: var(--space-md); display: flex; justify-content: flex-end">
              <button class="btn btn-primary" @click="addRule" :disabled="!newRule.port">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                添加
              </button>
            </div>
          </div>
        </div>

        <!-- Rules Table -->
        <div class="card rules-card">
          <div class="card-header">
            <span>规则列表</span>
            <span class="rules-count">{{ rules.length }} 条规则</span>
          </div>
          <div class="card-body rules-body">
            <table class="rules-table" v-if="rules.length > 0">
              <thead>
                <tr>
                  <th>#</th>
                  <th>端口</th>
                  <th>协议</th>
                  <th>动作</th>
                  <th>来源</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(rule, i) in rules" :key="rule.id">
                  <td>{{ i + 1 }}</td>
                  <td><code>{{ rule.port || '-' }}</code></td>
                  <td>{{ rule.protocol || '-' }}</td>
                  <td>
                    <span class="action-badge" :class="'action-' + (rule.action || '').toLowerCase()">
                      {{ rule.action || '-' }}
                    </span>
                  </td>
                  <td>{{ rule.source || 'any' }}</td>
                  <td>
                    <button class="btn btn-ghost btn-sm btn-danger" @click="deleteRule(rule)" title="删除规则">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="rules-empty">暂无防火墙规则</div>
          </div>
        </div>

      </div>
    </PathGuard>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import AppLayout from '../components/layout/AppLayout.vue'
import PathGuard from '../components/common/PathGuard.vue'
import { api } from '../utils/api'

const status = reactive({ tool: '', enabled: false, version: '' })
const rules = ref([])

const newRule = reactive({ port: '', protocol: 'tcp', action: 'allow', source: '' })

async function fetchStatus() {
  try {
    const res = await api('/api/v1/firewall/status')
    const json = await res.json()
    if (json.code === 200 && json.data) {
      Object.assign(status, json.data)
    }
  } catch (e) {
    console.error('获取防火墙状态失败:', e)
  }
}

async function fetchRules() {
  try {
    const res = await api('/api/v1/firewall/rules')
    const json = await res.json()
    if (json.code === 200) {
      rules.value = json.data || []
    }
  } catch (e) {
    console.error('获取规则失败:', e)
  }
}

async function toggleFirewall() {
  const enabled = !status.enabled
  try {
    const res = await api('/api/v1/firewall/toggle', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled })
    })
    const json = await res.json()
    if (json.code === 200) {
      status.enabled = enabled
    }
  } catch (e) {
    console.error('切换防火墙状态失败:', e)
  }
}

async function addRule() {
  if (!newRule.port) return
  try {
    const res = await api('/api/v1/firewall/rules', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        port: newRule.port,
        protocol: newRule.protocol,
        action: newRule.action,
        source: newRule.source || ''
      })
    })
    const json = await res.json()
    if (json.code === 200) {
      newRule.port = ''
      newRule.source = ''
      fetchRules()
    }
  } catch (e) {
    console.error('添加规则失败:', e)
  }
}

async function deleteRule(rule) {
  try {
    const params = new URLSearchParams({ id: rule.id, protocol: rule.protocol || 'tcp' })
    const res = await api('/api/v1/firewall/rules?' + params, { method: 'DELETE' })
    const json = await res.json()
    if (json.code === 200) {
      fetchRules()
    }
  } catch (e) {
    console.error('删除规则失败:', e)
  }
}

onMounted(async () => {
  await fetchStatus()
  await fetchRules()
})
</script>

<style scoped>
.firewall-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

/* Status Bar */
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
}
.status-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.status-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
}
.status-on {
  background: var(--success-bg, rgba(78, 184, 86, 0.12));
  color: var(--color-success, #4caf50);
}
.status-off {
  background: var(--error-bg, rgba(239, 83, 80, 0.12));
  color: var(--color-error, #ef5350);
}
.status-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.status-tool {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
}
.status-version {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  font-family: var(--font-mono);
}
.status-right {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.status-label-text {
  font-size: var(--font-size-xs);
  font-weight: 500;
}
.text-success { color: var(--color-success, #4caf50); }
.text-muted { color: var(--text-tertiary); }

/* Toggle */
.toggle {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
  cursor: pointer;
}
.toggle input { opacity: 0; width: 0; height: 0; }
.toggle-slider {
  position: absolute;
  inset: 0;
  background: var(--bg-tertiary);
  border-radius: 10px;
  transition: background 0.2s;
}
.toggle-slider::before {
  content: '';
  position: absolute;
  width: 16px;
  height: 16px;
  left: 2px;
  top: 2px;
  background: white;
  border-radius: 50%;
  transition: transform 0.2s;
}
.toggle input:checked + .toggle-slider {
  background: var(--accent);
}
.toggle input:checked + .toggle-slider::before {
  transform: translateX(16px);
}

/* Form Grid */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  gap: var(--space-sm) var(--space-lg);
}

/* Rules Table */
.rules-card {
  flex: 1;
  min-height: 0;
}
.rules-body {
  padding: 0 !important;
  overflow-x: auto;
}
.rules-count {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  font-weight: normal;
}
.rules-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
}
.rules-table th {
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
.rules-table td {
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--border-secondary);
  color: var(--text-primary);
}
.rules-table tr:last-child td {
  border-bottom: none;
}
.rules-table tr:hover td {
  background: var(--bg-hover);
}
.rules-table code {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  background: var(--bg-tertiary);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}
.rules-empty {
  text-align: center;
  padding: var(--space-xxl);
  color: var(--text-tertiary);
}

/* Action Badge */
.action-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  font-weight: 600;
  text-transform: uppercase;
}
.action-allow, .action-pass {
  background: var(--success-bg, rgba(78, 184, 86, 0.12));
  color: var(--color-success, #4caf50);
}
.action-deny, .action-drop, .action-reject, .action-block {
  background: var(--error-bg, rgba(239, 83, 80, 0.12));
  color: var(--color-error, #ef5350);
}

/* Buttons */
.btn-danger:hover {
  color: var(--color-error, #ef5350) !important;
}
</style>
