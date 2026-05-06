<template>
  <div class="path-guard-wrapper">
    <slot />
    <div v-if="!valid && !checking" class="path-guard-overlay">
      <div class="path-guard-card">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--warning)" stroke-width="1.5">
          <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
          <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
        </svg>
        <div class="path-guard-title">Nginx 路径未配置或无效</div>
        <div class="path-guard-desc">请先配置有效的 Nginx 可执行文件路径和 nginx.conf 路径</div>
        <button class="btn btn-primary" @click="$router.push('/basic-config')">前往配置</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { usePathValidation } from '../../composables/usePathValidation'

const { valid, checking, check } = usePathValidation()

onMounted(() => check())
</script>

<style scoped>
.path-guard-wrapper {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.path-guard-overlay {
  position: absolute;
  inset: 0;
  z-index: 100;
  background: var(--bg-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.path-guard-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-lg);
  padding: var(--space-xxl);
  background: var(--bg-card);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  text-align: center;
  max-width: 360px;
}
.path-guard-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
}
.path-guard-desc {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  line-height: 1.5;
}
</style>
