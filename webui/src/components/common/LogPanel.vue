<template>
  <div class="log-panel" :style="{ height: height + 'px' }">
    <div class="log-resize-handle" @mousedown.prevent="onResizeStart"></div>
    <div class="log-header">
      <span class="log-title">操作日志</span>
      <button v-if="logs.length" class="log-clear" @mousedown.prevent="$emit('clear')" title="清空日志">
        <svg width="14" height="14" viewBox="0 0 16 16"><path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
      </button>
    </div>
    <div class="log-body" ref="bodyRef">
      <div v-if="logs.length === 0" class="log-empty">暂无操作记录</div>
      <div
        v-for="(entry, i) in logs"
        :key="i"
        class="log-entry"
        :class="entry.success ? 'log-success' : 'log-fail'"
      >
        <span class="log-time">{{ formatTime(entry.time) }}</span>
        <span class="log-icon">{{ entry.success ? '✓' : '✗' }}</span>
        <span class="log-msg">{{ entry.message }}</span>
      </div>
    </div>
    <div class="log-status-bar">
      <span v-if="status.state === 'idle'" class="status-idle">就绪</span>
      <span v-else-if="status.state === 'pending'" class="status-pending">
        <span class="status-dot status-dot-pending"></span>待保存...
      </span>
      <span v-else-if="status.state === 'success'" class="status-success">
        <span class="status-dot status-dot-success"></span>保存成功
        <span class="status-time">{{ formatTime(status.time) }}</span>
      </span>
      <span v-else-if="status.state === 'error'" class="status-error">
        <span class="status-dot status-dot-error"></span>保存失败: {{ status.message }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'

const props = defineProps({
  logs: { type: Array, default: () => [] },
  status: { type: Object, default: () => ({ state: 'idle', message: '', time: null }) }
})

defineEmits(['clear'])

const bodyRef = ref(null)
const height = ref(200)

let resizing = false
let startY = 0
let startHeight = 0

function onResizeStart(e) {
  resizing = true
  startY = e.clientY
  startHeight = height.value
  document.addEventListener('mousemove', onResizeMove)
  document.addEventListener('mouseup', onResizeEnd)
  document.body.style.cursor = 'row-resize'
  document.body.style.userSelect = 'none'
}

function onResizeMove(e) {
  if (!resizing) return
  const delta = startY - e.clientY
  height.value = Math.max(80, Math.min(600, startHeight + delta))
}

function onResizeEnd() {
  resizing = false
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', onResizeEnd)
})

watch(() => props.logs.length, () => {
  nextTick(() => {
    if (bodyRef.value) {
      bodyRef.value.scrollTop = bodyRef.value.scrollHeight
    }
  })
})

function formatTime(date) {
  if (!date) return ''
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${h}:${m}:${s}`
}
</script>

<style scoped>
.log-panel {
  display: flex;
  flex-direction: column;
  min-height: 80px;
  max-height: 600px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border-primary);
  position: relative;
}
.log-resize-handle {
  position: absolute;
  top: -3px;
  left: 0;
  right: 0;
  height: 6px;
  cursor: row-resize;
  z-index: 10;
}
.log-resize-handle:hover,
.log-resize-handle:active {
  background: var(--border-focus);
}
.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-md);
  height: 30px;
  min-height: 30px;
  border-bottom: 1px solid var(--border-primary);
  background: var(--bg-primary);
}
.log-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.log-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.log-clear:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.log-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xs) 0;
  font-family: var(--font-mono, 'JetBrains Mono', 'Fira Code', 'Consolas', monospace);
  font-size: 12px;
  line-height: 1.6;
}
.log-empty {
  padding: var(--space-lg) var(--space-md);
  text-align: center;
  color: var(--text-tertiary);
  font-family: var(--font-sans, sans-serif);
  font-size: var(--font-size-sm);
}
.log-entry {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  padding: 2px var(--space-md);
  border-bottom: 1px solid var(--border-secondary);
}
.log-entry:last-child {
  border-bottom: none;
}
.log-time {
  flex-shrink: 0;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
.log-icon {
  flex-shrink: 0;
  width: 14px;
  text-align: center;
  font-weight: 700;
}
.log-msg {
  flex: 1;
  min-width: 0;
  word-break: break-all;
  white-space: pre-wrap;
}
.log-success .log-icon {
  color: var(--color-success, #4caf50);
}
.log-success .log-msg {
  color: var(--text-secondary);
}
.log-fail .log-icon {
  color: var(--color-error, #ef5350);
}
.log-fail .log-msg {
  color: var(--color-error, #ef5350);
}

/* Status Bar */
.log-status-bar {
  display: flex;
  align-items: center;
  height: 24px;
  min-height: 24px;
  padding: 0 var(--space-md);
  border-top: 1px solid var(--border-primary);
  background: var(--bg-primary);
  font-size: 12px;
}
.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 4px;
}
.status-idle {
  color: var(--text-tertiary);
}
.status-pending {
  color: var(--color-warning, #ff9800);
}
.status-dot-pending {
  background: var(--color-warning, #ff9800);
  animation: pulse 1s infinite;
}
.status-success {
  color: var(--color-success, #4caf50);
}
.status-dot-success {
  background: var(--color-success, #4caf50);
}
.status-error {
  color: var(--color-error, #ef5350);
}
.status-dot-error {
  background: var(--color-error, #ef5350);
}
.status-time {
  margin-left: 8px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
