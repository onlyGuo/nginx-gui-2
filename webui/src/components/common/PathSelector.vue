<template>
  <div class="path-selector" ref="rootRef">
    <div class="path-input-wrap" :class="{ focused, disabled }">
      <svg class="path-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path v-if="type === 'file'" d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline v-if="type === 'file'" points="14 2 14 8 20 8"/>
        <path v-else d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
      </svg>
      <input
        type="text"
        ref="inputRef"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        @input="onInput"
        @focus="onFocus"
        @blur="onBlur"
        @keydown="onKeydown"
      />
      <button v-if="modelValue && !disabled" class="path-clear" @click="clear" tabindex="-1">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
      <button class="path-browse" @click="toggleBrowser" :disabled="disabled" tabindex="-1" title="浏览">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
      </button>
    </div>

    <!-- Autocomplete Dropdown -->
    <Teleport to="body">
      <div
        v-if="showSuggest && suggestItems.length > 0"
        class="suggest-dropdown"
        :style="dropdownStyle"
      >
        <div
          v-for="(item, i) in suggestItems"
          :key="item.name"
          class="suggest-item"
          :class="{ active: activeIndex === i, 'is-dir': item.dir }"
          @mousedown.prevent="selectItem(item)"
          @mouseenter="activeIndex = i"
        >
          <svg v-if="item.dir" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="item-icon dir-icon">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
          </svg>
          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="item-icon">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
          </svg>
          <span class="item-name">{{ item.name }}</span>
          <span class="item-size" v-if="!item.dir && item.size">{{ item.size }}</span>
        </div>
      </div>
    </Teleport>

    <!-- Browser Popup -->
    <Teleport to="body">
      <div v-if="showBrowser" class="browser-overlay" @mousedown.self="showBrowser = false">
        <div class="browser-popup" :style="popupStyle" ref="popupRef">
          <div class="browser-header">
            <div class="browser-breadcrumb">
              <span v-for="(seg, i) in breadcrumbs" :key="i" class="breadcrumb-seg" @click="navigateTo(i)">{{ seg }}</span>
            </div>
            <button class="btn btn-sm btn-ghost" @click="showBrowser = false">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
          <div class="browser-item parent-dir" @click="goUp" v-if="parentDir">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
            <span class="item-name">..</span>
            <span class="item-type text-tertiary">上级目录</span>
          </div>
          <div v-if="loading" class="browser-empty">加载中...</div>
          <div v-else-if="loadError" class="browser-empty" style="color: var(--error)">{{ loadError }}</div>
          <div v-else class="browser-list" ref="listRef">
            <div v-for="item in currentItems" :key="item.name" class="browser-item" :class="{ selected: selectedName === item.name, 'is-dir': item.dir }" @click="onItemClick(item)" @dblclick="onItemDblClick(item)">
              <svg v-if="item.dir" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="item-icon dir-icon"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
              <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="item-icon"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
              <span class="item-name">{{ item.name }}</span>
              <span class="item-size" v-if="!item.dir">{{ item.size }}</span>
            </div>
            <div v-if="currentItems.length === 0" class="browser-empty">空目录</div>
          </div>
          <div class="browser-footer">
            <div class="browser-selected-path font-mono text-xs text-secondary truncate">{{ displayPath }}</div>
            <div class="flex gap-sm">
              <button class="btn btn-sm" @click="showBrowser = false">取消</button>
              <button class="btn btn-sm btn-primary" @click="confirmSelect" :disabled="!canConfirm">选择</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <div class="path-hint" v-if="hint">{{ hint }}</div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'
import { api } from '../../utils/api'

const props = defineProps({
  modelValue: { type: String, default: '' },
  type: { type: String, default: 'file' },
  placeholder: { type: String, default: '' },
  hint: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const focused = ref(false)
const inputRef = ref(null)
const rootRef = ref(null)

// ---- Autocomplete ----
const showSuggest = ref(false)
const suggestItems = ref([])
const activeIndex = ref(-1)
const dropdownStyle = ref({})
let suggestTimer = null

function parsePath(val) {
  if (!val) return { dir: '/', partial: '' }
  const lastSlash = val.lastIndexOf('/')
  if (lastSlash < 0) return { dir: '/', partial: val }
  return {
    dir: val.substring(0, lastSlash) || '/',
    partial: val.substring(lastSlash + 1)
  }
}

function positionDropdown() {
  if (!rootRef.value) return
  const rect = rootRef.value.getBoundingClientRect()
  dropdownStyle.value = {
    position: 'fixed',
    top: (rect.bottom + 2) + 'px',
    left: rect.left + 'px',
    width: rect.width + 'px'
  }
}

async function fetchSuggestions(val) {
  const { dir } = parsePath(val)
  try {
    const res = await api('/api/v1/files?path=' + encodeURIComponent(dir))
    const json = await res.json()
    if (json.code === 200 && json.data?.items) {
      const { partial } = parsePath(val)
      const items = json.data.items
      const filtered = partial
        ? items.filter(it => it.name.toLowerCase().startsWith(partial.toLowerCase()))
        : items
      suggestItems.value = filtered
      activeIndex.value = filtered.length > 0 ? 0 : -1
      showSuggest.value = filtered.length > 0
    } else {
      suggestItems.value = []
      showSuggest.value = false
    }
  } catch {
    suggestItems.value = []
    showSuggest.value = false
  }
}

function onInput(e) {
  const val = e.target.value
  emit('update:modelValue', val)
  clearTimeout(suggestTimer)
  if (!val) {
    showSuggest.value = false
    suggestItems.value = []
    return
  }
  suggestTimer = setTimeout(() => {
    positionDropdown()
    fetchSuggestions(val)
  }, 200)
}

function onFocus() {
  focused.value = true
  if (props.modelValue) {
    clearTimeout(suggestTimer)
    suggestTimer = setTimeout(() => {
      positionDropdown()
      fetchSuggestions(props.modelValue)
    }, 200)
  }
}

function onBlur() {
  setTimeout(() => {
    focused.value = false
    showSuggest.value = false
  }, 150)
}

function selectItem(item) {
  const { dir } = parsePath(props.modelValue)
  const sep = dir === '/' ? '' : '/'
  const newPath = dir + sep + item.name + (item.dir ? '/' : '')
  emit('update:modelValue', newPath)
  showSuggest.value = false
  if (item.dir) {
    nextTick(() => {
      positionDropdown()
      fetchSuggestions(newPath)
    })
  }
  nextTick(() => inputRef.value?.focus())
}

function onKeydown(e) {
  if (!showSuggest.value || suggestItems.value.length === 0) {
    if (e.key === 'Escape') showSuggest.value = false
    return
  }
  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault()
      activeIndex.value = (activeIndex.value + 1) % suggestItems.value.length
      scrollActiveIntoView()
      break
    case 'ArrowUp':
      e.preventDefault()
      activeIndex.value = (activeIndex.value - 1 + suggestItems.value.length) % suggestItems.value.length
      scrollActiveIntoView()
      break
    case 'Enter':
      e.preventDefault()
      if (activeIndex.value >= 0) selectItem(suggestItems.value[activeIndex.value])
      break
    case 'Escape':
      showSuggest.value = false
      break
    case 'Tab':
      if (activeIndex.value >= 0) {
        e.preventDefault()
        selectItem(suggestItems.value[activeIndex.value])
      }
      break
  }
}

function scrollActiveIntoView() {
  nextTick(() => {
    const el = document.querySelector('.suggest-item.active')
    el?.scrollIntoView({ block: 'nearest' })
  })
}

function clear() {
  emit('update:modelValue', '')
  showSuggest.value = false
  suggestItems.value = []
  inputRef.value?.focus()
}

onBeforeUnmount(() => clearTimeout(suggestTimer))

// ---- Browser Popup ----
const showBrowser = ref(false)
const popupRef = ref(null)
const listRef = ref(null)
const currentDir = ref('/')
const selectedName = ref('')
const popupStyle = ref({})
const currentItems = ref([])
const parentDir = ref(null)
const loading = ref(false)
const loadError = ref('')

async function fetchDir(dirPath) {
  loading.value = true
  loadError.value = ''
  try {
    const typeParam = props.type === 'dir' ? 'dir' : 'file'
    const res = await api('/api/v1/files?path=' + encodeURIComponent(dirPath) + '&type=' + typeParam)
    const json = await res.json()
    if (json.code === 200 && json.data) {
      currentDir.value = json.data.path
      currentItems.value = json.data.items || []
      parentDir.value = json.data.parent
    } else {
      loadError.value = json.message || '加载失败'
      currentItems.value = []
      parentDir.value = null
    }
  } catch (e) {
    loadError.value = '请求失败: ' + e.message
    currentItems.value = []
    parentDir.value = null
  } finally {
    loading.value = false
  }
}

const breadcrumbs = computed(() => {
  if (currentDir.value === '/') return ['/']
  const parts = currentDir.value.split('/').filter(Boolean)
  return ['/', ...parts]
})

const displayPath = computed(() => {
  if (!selectedName.value) return currentDir.value
  if (currentDir.value === '/') return '/' + selectedName.value
  return currentDir.value + '/' + selectedName.value
})

const canConfirm = computed(() => {
  if (props.type === 'dir') return true
  return !!selectedName.value
})

function navigateTo(breadcrumbIndex) {
  let target
  if (breadcrumbIndex === 0) {
    target = '/'
  } else {
    const parts = currentDir.value.split('/').filter(Boolean)
    target = '/' + parts.slice(0, breadcrumbIndex).join('/')
  }
  selectedName.value = ''
  fetchDir(target)
}

function goUp() {
  if (parentDir.value) {
    selectedName.value = ''
    fetchDir(parentDir.value)
  }
}

function onItemClick(item) {
  selectedName.value = item.name
}

function onItemDblClick(item) {
  if (item.dir) {
    const sep = currentDir.value === '/' ? '' : '/'
    selectedName.value = ''
    fetchDir(currentDir.value + sep + item.name)
  }
}

function confirmSelect() {
  const sep = currentDir.value === '/' ? '' : '/'
  let path
  if (props.type === 'dir') {
    path = selectedName.value
      ? currentDir.value + sep + selectedName.value
      : currentDir.value
  } else {
    path = currentDir.value + sep + selectedName.value
  }
  emit('update:modelValue', path)
  showBrowser.value = false
}

function toggleBrowser() {
  if (showBrowser.value) {
    showBrowser.value = false
    return
  }
  showSuggest.value = false
  const val = props.modelValue
  if (val) {
    const dir = props.type === 'file' ? val.substring(0, val.lastIndexOf('/')) || '/' : val
    fetchDir(dir)
  } else {
    fetchDir('/')
  }
  selectedName.value = ''
  showBrowser.value = true
  nextTick(() => {
    if (!rootRef.value) return
    const rect = rootRef.value.getBoundingClientRect()
    popupStyle.value = {
      position: 'fixed',
      top: (rect.bottom + 4) + 'px',
      left: rect.left + 'px',
      width: Math.max(rect.width, 380) + 'px'
    }
  })
}
</script>

<style scoped>
.path-selector {
  display: flex;
  flex-direction: column;
  gap: 3px;
  position: relative;
}
.path-input-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 4px 0 8px;
  background: var(--bg-input);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
}
.path-input-wrap.focused {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 1px var(--border-focus);
}
.path-input-wrap.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.path-icon {
  flex-shrink: 0;
  color: var(--text-tertiary);
}
.path-input-wrap input {
  flex: 1;
  border: none !important;
  background: transparent;
  padding: 0;
  height: 100%;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  outline: none;
  box-shadow: none !important;
  min-width: 0;
  font-family: var(--font-mono);
}
.path-input-wrap input::placeholder {
  color: var(--text-tertiary);
}
.path-input-wrap input:disabled {
  cursor: not-allowed;
}
.path-clear, .path-browse {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}
.path-clear:hover, .path-browse:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}
.path-browse {
  color: var(--text-secondary);
}
.path-hint {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  padding-left: 2px;
}

/* Autocomplete Dropdown */
.suggest-dropdown {
  background: var(--bg-elevated);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  max-height: 260px;
  overflow-y: auto;
  z-index: 300;
  padding: var(--space-xs);
}
.suggest-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) var(--space-sm);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: var(--font-size-sm);
  font-family: var(--font-mono);
  transition: background var(--transition-fast);
}
.suggest-item:hover,
.suggest-item.active {
  background: var(--bg-hover);
}
.suggest-item.active {
  background: var(--accent-bg);
}
.suggest-item .item-icon {
  flex-shrink: 0;
  color: var(--text-tertiary);
}
.suggest-item .dir-icon {
  color: var(--accent);
}
.suggest-item .item-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
}
.suggest-item.is-dir .item-name {
  color: var(--accent);
}
.suggest-item .item-size {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  flex-shrink: 0;
}

/* Browser Popup */
.browser-overlay {
  position: fixed;
  inset: 0;
  z-index: 299;
}
.browser-popup {
  background: var(--bg-elevated);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  max-height: 400px;
  z-index: 300;
  overflow: hidden;
}
.browser-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--border-secondary);
  background: var(--bg-secondary);
  gap: var(--space-sm);
  flex-shrink: 0;
}
.browser-breadcrumb {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
}
.breadcrumb-seg {
  font-size: var(--font-size-xs);
  font-family: var(--font-mono);
  color: var(--text-secondary);
  cursor: pointer;
  padding: 2px 4px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
  flex-shrink: 0;
}
.breadcrumb-seg:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.breadcrumb-seg + .breadcrumb-seg::before {
  content: '/';
  color: var(--text-tertiary);
  margin-right: 4px;
  cursor: default;
}
.breadcrumb-seg + .breadcrumb-seg:hover::before {
  background: transparent;
  color: var(--text-tertiary);
}
.browser-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xs);
}
.browser-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) var(--space-sm);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition-fast);
  font-size: var(--font-size-sm);
}
.browser-item:hover {
  background: var(--bg-hover);
}
.browser-item.selected {
  background: var(--accent-bg);
}
.browser-item.parent-dir {
  margin: 0 var(--space-xs);
  border-bottom: 1px solid var(--border-secondary);
  border-radius: 0;
  padding: var(--space-sm);
}
.item-icon {
  flex-shrink: 0;
  color: var(--text-tertiary);
}
.dir-icon {
  color: var(--accent);
}
.item-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
  font-family: var(--font-mono);
}
.browser-item.is-dir .item-name {
  color: var(--accent);
}
.item-size {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  flex-shrink: 0;
  font-family: var(--font-mono);
}
.item-type {
  font-size: var(--font-size-xs);
  flex-shrink: 0;
}
.browser-empty {
  text-align: center;
  padding: var(--space-xxl);
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}
.browser-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md);
  border-top: 1px solid var(--border-secondary);
  background: var(--bg-secondary);
  gap: var(--space-md);
  flex-shrink: 0;
}
.browser-selected-path {
  flex: 1;
  min-width: 0;
}
</style>
