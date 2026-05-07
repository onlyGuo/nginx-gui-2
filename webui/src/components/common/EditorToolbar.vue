<template>
  <div class="editor-toolbar">
    <div class="toolbar-left">
      <span class="toolbar-filename">{{ fileName }}</span>
      <span class="toolbar-status" :class="'status-' + status.state">
        <span v-if="status.state === 'pending'" class="status-dot status-dot-pending"></span>
        <span v-else-if="status.state === 'success'" class="status-dot status-dot-success"></span>
        <span v-else-if="status.state === 'error'" class="status-dot status-dot-error"></span>
        <span v-if="status.state === 'idle'">就绪</span>
        <span v-else-if="status.state === 'pending'">保存中...</span>
        <span v-else-if="status.state === 'success'">已保存</span>
        <span v-else-if="status.state === 'error'">保存失败: {{ status.message }}</span>
      </span>
    </div>
    <div class="toolbar-right">
      <span class="toolbar-hint">{{ isMac ? '⌘S' : 'Ctrl+S' }} 保存</span>
      <button class="btn btn-sm btn-ghost toolbar-btn" @click="$emit('reload')" title="重新加载">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/>
          <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
        </svg>
      </button>
      <button class="btn btn-sm btn-primary toolbar-btn" @click="$emit('save')" title="保存">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
          <polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/>
        </svg>
        保存
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  fileName: { type: String, default: '' },
  status: { type: Object, default: () => ({ state: 'idle', message: '' }) }
})

defineEmits(['reload', 'save'])

const isMac = typeof navigator !== 'undefined' && /Mac|iPod|iPhone|iPad/.test(navigator.platform)
</script>

<style scoped>
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 32px;
  min-height: 32px;
  padding: 0 var(--space-md);
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-secondary);
  font-size: var(--font-size-sm);
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  min-width: 0;
  flex: 1;
}
.toolbar-filename {
  font-family: var(--font-mono);
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.toolbar-status {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  font-size: var(--font-size-xs);
}
.status-idle { color: var(--text-tertiary); }
.status-pending { color: var(--warning, #ff9800); }
.status-success { color: var(--success, #4caf50); }
.status-error { color: var(--error, #ef5350); }

.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.status-dot-pending {
  background: var(--warning, #ff9800);
  animation: pulse 1s infinite;
}
.status-dot-success {
  background: var(--success, #4caf50);
}
.status-dot-error {
  background: var(--error, #ef5350);
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}
.toolbar-hint {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin-right: var(--space-xs);
}
.toolbar-btn {
  height: 24px;
  padding: 0 8px;
  font-size: var(--font-size-xs);
  gap: 3px;
}
</style>
