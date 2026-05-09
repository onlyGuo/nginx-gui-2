<template>
  <AppLayout>
    <div class="user-page">

      <!-- Current User Info + Change Password -->
      <div class="card">
        <div class="card-header">个人信息</div>
        <div class="card-body">
          <div class="info-row">
            <span class="info-label">用户名</span>
            <span class="info-value">{{ currentInfo.username }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">角色</span>
            <span class="role-badge" :class="currentInfo.role === 'ADMIN' ? 'role-admin' : 'role-manager'">
              {{ currentInfo.role === 'ADMIN' ? '超级管理员' : '管理员' }}
            </span>
          </div>
          <div class="info-row">
            <span class="info-label">创建时间</span>
            <span class="info-value">{{ currentInfo.createdAt }}</span>
          </div>
          <div class="divider"></div>
          <div class="section-title">修改密码</div>
          <div class="form-grid">
            <div class="form-group">
              <label class="form-label">原密码</label>
              <input v-model="pwForm.oldPassword" type="password" placeholder="请输入原密码" />
            </div>
            <div class="form-group">
              <label class="form-label">新密码</label>
              <input v-model="pwForm.newPassword" type="password" placeholder="请输入新密码" />
            </div>
          </div>
          <div style="margin-top: var(--space-md); display: flex; justify-content: flex-end">
            <button class="btn btn-primary" @click="changeMyPassword" :disabled="!pwForm.oldPassword || !pwForm.newPassword">
              修改密码
            </button>
          </div>
          <div v-if="pwMsg" class="msg" :class="pwMsgType === 'ok' ? 'msg-ok' : 'msg-err'">{{ pwMsg }}</div>
        </div>
      </div>

      <!-- Admin: User List + Create -->
      <template v-if="auth.isAdmin">
        <div class="card">
          <div class="card-header">
            <span>创建用户</span>
          </div>
          <div class="card-body">
            <div class="form-grid">
              <div class="form-group">
                <label class="form-label">用户名</label>
                <input v-model="newUser.username" type="text" placeholder="请输入用户名" />
              </div>
              <div class="form-group">
                <label class="form-label">密码</label>
                <input v-model="newUser.password" type="password" placeholder="请输入密码" />
              </div>
            </div>
            <div style="margin-top: var(--space-md); display: flex; justify-content: flex-end">
              <button class="btn btn-primary" @click="createUser" :disabled="!newUser.username || !newUser.password">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                创建
              </button>
            </div>
          </div>
          <div v-if="createMsg" class="msg" :class="createMsgType === 'ok' ? 'msg-ok' : 'msg-err'">{{ createMsg }}</div>
        </div>

        <div class="card">
          <div class="card-header">
            <span>用户列表</span>
            <span class="user-count">{{ users.length }} 个用户</span>
          </div>
          <div class="card-body users-body">
            <table class="users-table" v-if="users.length > 0">
              <thead>
                <tr>
                  <th>#</th>
                  <th>用户名</th>
                  <th>角色</th>
                  <th>创建时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(u, i) in users" :key="u.id">
                  <td>{{ i + 1 }}</td>
                  <td><code>{{ u.username }}</code></td>
                  <td>
                    <span class="role-badge" :class="u.role === 'ADMIN' ? 'role-admin' : 'role-manager'">
                      {{ u.role === 'ADMIN' ? '超级管理员' : '管理员' }}
                    </span>
                  </td>
                  <td>{{ u.createdAt }}</td>
                  <td>
                    <div class="actions-cell">
                      <button class="btn btn-ghost btn-sm" @click="openResetPw(u)" title="重置密码">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                        重置密码
                      </button>
                      <button v-if="u.username !== 'admin'" class="btn btn-ghost btn-sm btn-danger" @click="deleteUser(u)" title="删除用户">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                        删除
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="rules-empty">暂无用户</div>
          </div>
        </div>
      </template>

      <!-- Reset Password Modal -->
      <div v-if="resetTarget" class="modal-overlay" @click.self="resetTarget = null">
        <div class="modal-card card">
          <div class="card-header">
            <span>重置密码 — {{ resetTarget.username }}</span>
            <button class="btn btn-sm btn-ghost" @click="resetTarget = null">&times;</button>
          </div>
          <div class="card-body">
            <div class="form-grid">
              <div class="form-group" style="grid-column: 1 / -1">
                <label class="form-label">新密码</label>
                <input v-model="resetNewPw" type="password" placeholder="请输入新密码" autofocus />
              </div>
            </div>
            <div v-if="resetMsg" class="msg" :class="resetMsgType === 'ok' ? 'msg-ok' : 'msg-err'">{{ resetMsg }}</div>
          </div>
          <div style="padding: 0 var(--space-md) var(--space-md); display:flex; justify-content:flex-end; gap:var(--space-sm)">
            <button class="btn btn-ghost" @click="resetTarget = null">取消</button>
            <button class="btn btn-primary" @click="doResetPw" :disabled="!resetNewPw">确认重置</button>
          </div>
        </div>
      </div>

    </div>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import AppLayout from '../components/layout/AppLayout.vue'
import { useAuthStore } from '../stores/auth'
import { api } from '../utils/api'

const auth = useAuthStore()

const currentInfo = reactive({ username: '', role: '', createdAt: '' })
const users = ref([])

// Change own password
const pwForm = reactive({ oldPassword: '', newPassword: '' })
const pwMsg = ref('')
const pwMsgType = ref('ok')

// Create user
const newUser = reactive({ username: '', password: '' })
const createMsg = ref('')
const createMsgType = ref('ok')

// Reset password modal
const resetTarget = ref(null)
const resetNewPw = ref('')
const resetMsg = ref('')
const resetMsgType = ref('ok')

async function fetchMe() {
  try {
    const res = await api('/api/v1/users/me')
    const json = await res.json()
    if (json.code === 200) Object.assign(currentInfo, json.data)
  } catch (e) {
    console.error('获取用户信息失败:', e)
  }
}

async function fetchUsers() {
  if (!auth.isAdmin) return
  try {
    const res = await api('/api/v1/users')
    const json = await res.json()
    if (json.code === 200) users.value = json.data || []
  } catch (e) {
    console.error('获取用户列表失败:', e)
  }
}

async function changeMyPassword() {
  pwMsg.value = ''
  try {
    const res = await api('/api/v1/users/' + currentInfo.id + '/password', {
      method: 'PUT',
      body: JSON.stringify({ oldPassword: pwForm.oldPassword, newPassword: pwForm.newPassword })
    })
    const json = await res.json()
    if (json.code === 200) {
      pwMsg.value = '密码修改成功，请重新登录'
      pwMsgType.value = 'ok'
      pwForm.oldPassword = ''
      pwForm.newPassword = ''
      // Token has been invalidated — force re-login
      setTimeout(() => { auth.logout() }, 1500)
    } else {
      pwMsg.value = json.message || '修改失败'
      pwMsgType.value = 'err'
    }
  } catch (e) {
    pwMsg.value = '请求失败: ' + e.message
    pwMsgType.value = 'err'
  }
}

async function createUser() {
  createMsg.value = ''
  try {
    const res = await api('/api/v1/users', {
      method: 'POST',
      body: JSON.stringify({ username: newUser.username, password: newUser.password })
    })
    const json = await res.json()
    if (json.code === 200) {
      createMsg.value = '用户 "' + newUser.username + '" 创建成功'
      createMsgType.value = 'ok'
      newUser.username = ''
      newUser.password = ''
      fetchUsers()
    } else {
      createMsg.value = json.message || '创建失败'
      createMsgType.value = 'err'
    }
  } catch (e) {
    createMsg.value = '请求失败: ' + e.message
    createMsgType.value = 'err'
  }
}

function openResetPw(user) {
  resetTarget.value = user
  resetNewPw.value = ''
  resetMsg.value = ''
}

async function doResetPw() {
  resetMsg.value = ''
  try {
    const res = await api('/api/v1/users/' + resetTarget.value.id + '/reset-password', {
      method: 'PUT',
      body: JSON.stringify({ newPassword: resetNewPw.value })
    })
    const json = await res.json()
    if (json.code === 200) {
      resetMsg.value = '密码已重置，该用户的登录已失效'
      resetMsgType.value = 'ok'
      setTimeout(() => { resetTarget.value = null }, 1200)
    } else {
      resetMsg.value = json.message || '重置失败'
      resetMsgType.value = 'err'
    }
  } catch (e) {
    resetMsg.value = '请求失败: ' + e.message
    resetMsgType.value = 'err'
  }
}

async function deleteUser(user) {
  if (!confirm('确定删除用户 "' + user.username + '" 吗？该用户的登录将立即失效。')) return
  try {
    const res = await api('/api/v1/users/' + user.id, { method: 'DELETE' })
    const json = await res.json()
    if (json.code === 200) {
      fetchUsers()
    } else {
      alert(json.message || '删除失败')
    }
  } catch (e) {
    alert('请求失败: ' + e.message)
  }
}

onMounted(() => {
  fetchMe()
  fetchUsers()
})
</script>

<style scoped>
.user-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.info-row {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  padding: var(--space-sm) 0;
}
.info-label {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  min-width: 64px;
}
.info-value {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
}
.divider {
  height: 1px;
  background: var(--border-secondary);
  margin: var(--space-md) 0;
}

/* Section Title */
.section-title {
  display: flex;
  align-items: center;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--accent);
  padding: var(--space-md) 0 var(--space-xs);
  border-bottom: 1px solid var(--border-secondary);
  margin-bottom: var(--space-md);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

/* Form Grid */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-sm) var(--space-lg);
}

/* Role Badge */
.role-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  font-weight: 500;
}
.role-admin {
  background: var(--accent-bg);
  color: var(--accent);
}
.role-manager {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}
.user-count {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  font-weight: normal;
}

/* Users Table */
.users-body {
  padding: 0 !important;
  overflow-x: auto;
}
.users-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
}
.users-table th {
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
.users-table td {
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--border-secondary);
  color: var(--text-primary);
}
.users-table tr:last-child td {
  border-bottom: none;
}
.users-table tr:hover td {
  background: var(--bg-hover);
}
.users-table code {
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
.actions-cell {
  display: flex;
  gap: var(--space-sm);
}

/* Messages */
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

/* Buttons */
.btn-danger:hover {
  color: var(--color-error, #ef5350) !important;
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
