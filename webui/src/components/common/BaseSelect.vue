<template>
  <div class="base-select" :class="{ open, disabled }" ref="rootRef">
    <div
      class="select-trigger"
      @click="toggle"
      @keydown="onKeydown"
      tabindex="0"
      role="combobox"
      :aria-expanded="open"
    >
      <span class="select-value" :class="{ placeholder: !hasValue }">
        {{ displayLabel }}
      </span>
      <svg class="select-arrow" width="10" height="6" viewBox="0 0 10 6">
        <path d="M0 0l5 6 5-6z" fill="currentColor"/>
      </svg>
    </div>

    <Teleport to="body">
      <div v-if="open" class="select-overlay" @mousedown.prevent></div>
      <div
        v-if="open"
        class="select-dropdown"
        :class="{ 'dropdown-above': dropAbove }"
        :style="dropdownStyle"
        ref="dropdownRef"
        role="listbox"
      >
        <div
          v-if="placeholder"
          class="select-option placeholder-opt"
          :class="{ highlighted: highlightIndex === -1 }"
          @mousedown.prevent="selectPlaceholder"
          @mouseenter="highlightIndex = -1"
          role="option"
        >
          <span class="opt-label">{{ placeholder }}</span>
        </div>
        <div
          v-for="(opt, i) in normalizedOptions"
          :key="opt.rawValue"
          class="select-option"
          :class="{
            selected: opt.rawValue == modelValue,
            highlighted: highlightIndex === i,
            'has-desc': !!opt.description
          }"
          @mousedown.prevent="selectOption(opt)"
          @mouseenter="highlightIndex = i"
          role="option"
          :aria-selected="opt.rawValue == modelValue"
        >
          <span class="opt-label">{{ opt.label }}</span>
          <span v-if="opt.description" class="opt-desc">{{ opt.description }}</span>
        </div>
        <div v-if="normalizedOptions.length === 0" class="select-empty">无选项</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  /**
   * options 支持三种格式：
   *   ['a', 'b']                          — 简单字符串
   *   [{ value: 'a', label: '显示名' }]    — 带标签
   *   [{ value: 'a', label: '名', description: '描述' }] — 带标签+描述
   */
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const rootRef = ref(null)
const dropdownRef = ref(null)
const open = ref(false)
const highlightIndex = ref(0)
const dropdownStyle = ref({})
const dropAbove = ref(false)

const normalizedOptions = computed(() =>
  props.options.map(opt => {
    if (typeof opt === 'string' || typeof opt === 'number') {
      return { rawValue: opt, value: String(opt), label: String(opt), description: '' }
    }
    return {
      rawValue: opt.value,
      value: String(opt.value),
      label: opt.label ?? String(opt.value),
      description: opt.description || ''
    }
  })
)

const hasValue = computed(() => {
  const mv = props.modelValue
  if (mv === '' || mv === null || mv === undefined) return false
  return normalizedOptions.value.some(o => o.rawValue == mv)
})

const displayLabel = computed(() => {
  const found = normalizedOptions.value.find(o => o.rawValue == props.modelValue)
  if (found) return found.label
  if (props.placeholder) return props.placeholder
  return String(props.modelValue ?? '')
})

function toggle() {
  if (props.disabled) return
  open.value ? close() : open_()
}

function open_() {
  open.value = true
  const idx = normalizedOptions.value.findIndex(o => o.rawValue == props.modelValue)
  highlightIndex.value = idx >= 0 ? idx : -1
  nextTick(() => {
    positionDropdown()
    scrollToHighlighted()
  })
}

function close() {
  open.value = false
}

function selectOption(opt) {
  emit('update:modelValue', opt.rawValue)
  close()
}

function selectPlaceholder() {
  emit('update:modelValue', '')
  close()
}

function positionDropdown() {
  if (!rootRef.value) return
  const rect = rootRef.value.getBoundingClientRect()
  const gap = 2
  const maxH = 240
  const spaceBelow = window.innerHeight - rect.bottom - gap
  const spaceAbove = rect.top - gap

  if (spaceBelow < maxH && spaceAbove > spaceBelow) {
    dropAbove.value = true
    dropdownStyle.value = {
      position: 'fixed',
      bottom: (window.innerHeight - rect.top + gap) + 'px',
      left: rect.left + 'px',
      minWidth: rect.width + 'px',
      maxHeight: Math.min(maxH, spaceAbove) + 'px'
    }
  } else {
    dropAbove.value = false
    dropdownStyle.value = {
      position: 'fixed',
      top: (rect.bottom + gap) + 'px',
      left: rect.left + 'px',
      minWidth: rect.width + 'px',
      maxHeight: Math.min(maxH, spaceBelow) + 'px'
    }
  }
}

function scrollToHighlighted() {
  if (!dropdownRef.value) return
  const el = dropdownRef.value.querySelector('.select-option.highlighted')
  if (el) el.scrollIntoView({ block: 'nearest' })
}

function onKeydown(e) {
  if (props.disabled) return
  const opts = normalizedOptions.value
  const total = opts.length

  switch (e.key) {
    case 'Enter':
    case ' ':
      e.preventDefault()
      if (!open.value) {
        open_()
      } else if (highlightIndex.value >= 0 && highlightIndex.value < total) {
        selectOption(opts[highlightIndex.value])
      } else if (highlightIndex.value === -1 && props.placeholder) {
        selectPlaceholder()
      }
      break
    case 'ArrowDown':
      e.preventDefault()
      if (!open.value) { open_(); return }
      if (highlightIndex.value < total - 1) {
        highlightIndex.value++
        nextTick(scrollToHighlighted)
      }
      break
    case 'ArrowUp':
      e.preventDefault()
      if (!open.value) { open_(); return }
      if (highlightIndex.value > (props.placeholder ? -1 : 0)) {
        highlightIndex.value--
        nextTick(scrollToHighlighted)
      }
      break
    case 'Escape':
      e.preventDefault()
      close()
      break
    case 'Tab':
      close()
      break
  }
}

function onDocMousedown(e) {
  if (!open.value) return
  if (rootRef.value && rootRef.value.contains(e.target)) return
  close()
}

document.addEventListener('mousedown', onDocMousedown)
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocMousedown))
</script>

<style scoped>
.base-select {
  position: relative;
}
.select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  height: 28px;
  padding: 0 8px;
  background: var(--bg-input);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  outline: none;
  user-select: none;
}
.select-trigger:focus {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 1px var(--border-focus);
}
.base-select.disabled .select-trigger {
  opacity: 0.6;
  cursor: not-allowed;
}
.base-select.open .select-trigger {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 1px var(--border-focus);
}
.select-value {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-base);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.select-value.placeholder {
  color: var(--text-tertiary);
}
.select-arrow {
  flex-shrink: 0;
  color: var(--text-tertiary);
  transition: transform var(--transition-fast);
}
.base-select.open .select-arrow {
  transform: rotate(180deg);
}
</style>

<style>
/* Dropdown (unscoped — lives in Teleport) */
.select-overlay {
  position: fixed;
  inset: 0;
  z-index: 299;
}
.select-dropdown {
  background: var(--bg-elevated);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  overflow-y: auto;
  padding: var(--space-xs);
  z-index: 300;
}
.select-dropdown.dropdown-above {
  box-shadow: var(--shadow-lg);
}
.select-option {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: var(--space-xs) var(--space-md);
  font-size: var(--font-size-base);
  color: var(--text-primary);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition-fast);
}
.select-option.has-desc {
  padding: var(--space-sm) var(--space-md);
}
.select-option:hover,
.select-option.highlighted {
  background: var(--bg-hover);
}
.select-option.selected {
  background: var(--accent-bg);
}
.select-option.selected .opt-label {
  color: var(--accent);
}
.opt-label {
  font-size: var(--font-size-base);
  line-height: 1.4;
  white-space: nowrap;
}
.opt-desc {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  line-height: 1.3;
  white-space: nowrap;
}
.select-option.placeholder-opt .opt-label {
  color: var(--text-tertiary);
}
.select-option.placeholder-opt.highlighted .opt-label {
  color: var(--text-primary);
}
.select-empty {
  padding: var(--space-md);
  text-align: center;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
