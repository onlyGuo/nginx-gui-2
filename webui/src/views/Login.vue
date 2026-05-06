<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <svg class="login-logo" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="2" y="3" width="20" height="18" rx="2"/>
          <path d="M2 8h20M8 8v13"/>
        </svg>
        <h1 class="login-title">Nginx GUI</h1>
        <p class="login-subtitle">Nginx 可视化管理平台</p>
      </div>
      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label class="form-label">用户名</label>
          <input
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            autocomplete="username"
            autofocus
          />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>
        <div v-if="error" class="login-error">{{ error }}</div>
        <button type="submit" class="btn btn-primary w-full" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
        <p class="login-hint">默认账号: admin / admin</p>
      </form>
    </div>
    <div class="login-theme-toggle">
      <ThemeToggle />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import ThemeToggle from '../components/common/ThemeToggle.vue'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

function handleLogin() {
  error.value = ''
  loading.value = true
  setTimeout(() => {
    if (auth.login(username.value, password.value)) {
      router.push('/dashboard')
    } else {
      error.value = '用户名或密码错误'
    }
    loading.value = false
  }, 300)
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--bg-primary);
  position: relative;
}
.login-card {
  width: 340px;
  background: var(--bg-card);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-lg);
  padding: var(--space-xxl);
  box-shadow: var(--shadow-lg);
}
.login-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: var(--space-xxl);
}
.login-logo {
  color: var(--accent);
  margin-bottom: var(--space-md);
}
.login-title {
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: var(--text-primary);
}
.login-subtitle {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  margin-top: var(--space-xs);
}
.login-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.login-error {
  font-size: var(--font-size-sm);
  color: var(--error);
  padding: var(--space-sm) var(--space-md);
  background: var(--error-bg);
  border-radius: var(--radius-md);
}
.login-hint {
  text-align: center;
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin-top: var(--space-sm);
}
.login-theme-toggle {
  position: absolute;
  top: var(--space-lg);
  right: var(--space-lg);
}
</style>
