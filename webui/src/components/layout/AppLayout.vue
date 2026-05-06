<template>
  <div class="app-layout">
    <Sidebar />
    <main class="app-main">
      <header class="app-header">
        <div class="header-title">{{ title }}</div>
        <div class="header-actions">
          <span class="header-user">{{ auth.username }}</span>
          <button class="btn btn-ghost btn-sm" @click="handleLogout">退出</button>
        </div>
      </header>
      <section class="app-content">
        <slot />
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import Sidebar from './Sidebar.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const titles = {
  '/dashboard': '仪表页面',
  '/basic-config': '基础配置',
  '/nginx-config': 'Nginx 配置'
}

const title = computed(() => titles[route.path] || 'Nginx GUI')

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}
.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 var(--space-lg);
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-secondary);
  flex-shrink: 0;
}
.header-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.header-user {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}
.app-content {
  flex: 1;
  overflow: auto;
  padding: var(--space-lg);
  background: var(--bg-primary);
}
</style>
