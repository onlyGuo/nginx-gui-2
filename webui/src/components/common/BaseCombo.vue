<template>
  <div class="base-combo" :class="{ open, disabled }" ref="rootRef">
    <div class="combo-input-wrap">
      <input
        ref="inputRef"
        class="combo-input"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        @input="onInput"
        @focus="open_"
        @keydown="onKeydown"
      />
      <svg class="combo-arrow" width="10" height="6" viewBox="0 0 10 6" @mousedown.prevent="toggle">
        <path d="M0 0l5 6 5-6z" fill="currentColor"/>
      </svg>
    </div>

    <Teleport to="body">
      <div v-if="open" class="combo-overlay" @mousedown.prevent></div>
      <div
        v-if="open && filteredOptions.length > 0"
        class="combo-dropdown"
        :class="{ 'dropdown-above': dropAbove }"
        :style="dropdownStyle"
        ref="dropdownRef"
      >
        <div
          v-for="(opt, i) in filteredOptions"
          :key="opt.rawValue"
          class="combo-option"
          :class="{
            selected: opt.rawValue === modelValue,
            highlighted: highlightIndex === i,
            'has-desc': !!opt.description
          }"
          @mousedown.prevent="selectOption(opt)"
          @mouseenter="highlightIndex = i"
        >
          <span class="opt-label">{{ opt.label }}</span>
          <span v-if="opt.description" class="opt-desc">{{ opt.description }}</span>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  /**
   * options: ['a', 'b'] 或 [{ value, label }] 或 [{ value, label, description }]
   */
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const rootRef = ref(null)
const inputRef = ref(null)
const dropdownRef = ref(null)
const open = ref(false)
const highlightIndex = ref(0)
const dropdownStyle = ref({})
const dropAbove = ref(false)

const normalizedOptions = computed(() =>
  props.options.map(opt => {
    if (typeof opt === 'string' || typeof opt === 'number') {
      return { rawValue: String(opt), label: String(opt), description: '' }
    }
    return {
      rawValue: String(opt.value),
      label: opt.label ?? String(opt.value),
      description: opt.description || ''
    }
  })
)

const filteredOptions = computed(() => {
  // const val = props.modelValue.toLowerCase()
  // if (!val) return normalizedOptions.value
  // return normalizedOptions.value.filter(o =>
  //   o.label.toLowerCase().includes(val) || o.rawValue.toLowerCase().includes(val)
  // )
  return normalizedOptions.value
})

function toggle() {
  if (props.disabled) return
  open.value ? close() : open_()
}

function open_() {
  if (props.disabled) return
  open.value = true
  highlightIndex.value = 0
  nextTick(positionDropdown)
}

function close() {
  open.value = false
}

function onInput(e) {
  emit('update:modelValue', e.target.value)
  if (!open.value) open_()
  highlightIndex.value = 0
}

function selectOption(opt) {
  emit('update:modelValue', opt.rawValue)
  close()
  nextTick(() => inputRef.value?.focus())
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

function onKeydown(e) {
  const opts = filteredOptions.value
  const total = opts.length

  switch (e.key) {
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
      if (highlightIndex.value > 0) {
        highlightIndex.value--
        nextTick(scrollToHighlighted)
      }
      break
    case 'Enter':
      e.preventDefault()
      if (open.value && highlightIndex.value >= 0 && highlightIndex.value < total) {
        selectOption(opts[highlightIndex.value])
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

function scrollToHighlighted() {
  if (!dropdownRef.value) return
  const el = dropdownRef.value.querySelector('.combo-option.highlighted')
  if (el) el.scrollIntoView({ block: 'nearest' })
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
.base-combo {
  position: relative;
}
.combo-input-wrap {
  display: flex;
  align-items: center;
  height: 28px;
  background: var(--bg-input);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
}
.combo-input-wrap:focus-within {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 1px var(--border-focus);
}
.base-combo.disabled .combo-input-wrap {
  opacity: 0.6;
}
.combo-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0 8px;
  background: transparent;
  border: none;
  outline: none;
  font-size: var(--font-size-base);
  color: var(--text-primary);
}
.combo-input::placeholder {
  color: var(--text-tertiary);
}
.combo-input:focus {
  box-shadow: none;
}
.combo-arrow {
  flex-shrink: 0;
  margin-right: 8px;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: transform var(--transition-fast);
}
.base-combo.open .combo-arrow {
  transform: rotate(180deg);
}
</style>

<style>
/* Dropdown (unscoped — lives in Teleport) */
.combo-overlay {
  position: fixed;
  inset: 0;
  z-index: 299;
}
.combo-dropdown {
  background: var(--bg-elevated);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  overflow-y: auto;
  padding: var(--space-xs);
  z-index: 300;
}
.combo-option {
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
.combo-option.has-desc {
  padding: var(--space-sm) var(--space-md);
}
.combo-option:hover,
.combo-option.highlighted {
  background: var(--bg-hover);
}
.combo-option.selected {
  background: var(--accent-bg);
}
.combo-option.selected .opt-label {
  color: var(--accent);
}
.combo-option .opt-label {
  font-size: var(--font-size-base);
  line-height: 1.4;
  white-space: nowrap;
}
.combo-option .opt-desc {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  line-height: 1.3;
  white-space: nowrap;
}
</style>
