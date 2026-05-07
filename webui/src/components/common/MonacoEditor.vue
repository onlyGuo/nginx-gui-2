<template>
  <div class="monaco-container" ref="containerRef"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, shallowRef } from 'vue'
import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import { useThemeStore } from '../../stores/theme'

// Configure Monaco to only use the editor worker (skip json/html/css/ts workers)
self.MonacoEnvironment = {
  getWorker() {
    return new editorWorker()
  }
}

const props = defineProps({
  modelValue: { type: String, default: '' },
  language: { type: String, default: 'plaintext' },
  readOnly: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'save'])

const containerRef = ref(null)
const editor = shallowRef(null)
const themeStore = useThemeStore()

function defineNginxThemes() {
  monaco.editor.defineTheme('nginx-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: 'comment', foreground: '6A9955', fontStyle: 'italic' },
      { token: 'keyword', foreground: '569CD6' },
      { token: 'string', foreground: 'CE9178' },
      { token: 'number', foreground: 'B5CEA8' },
      { token: 'delimiter', foreground: 'D4D4D4' },
    ],
    colors: {
      'editor.background': '#1e1f22',
      'editor.foreground': '#dfe1e5',
      'editor.lineHighlightBackground': '#2b2d30',
      'editorLineNumber.foreground': '#6b707a',
      'editorCursor.foreground': '#dfe1e5',
      'editor.selectionBackground': '#214283',
      'editor.inactiveSelectionBackground': '#3a3d41',
    }
  })

  monaco.editor.defineTheme('nginx-light', {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'comment', foreground: '008000', fontStyle: 'italic' },
      { token: 'keyword', foreground: '0000FF' },
      { token: 'string', foreground: 'A31515' },
      { token: 'number', foreground: '098658' },
      { token: 'delimiter', foreground: '1f2328' },
    ],
    colors: {
      'editor.background': '#ffffff',
      'editor.foreground': '#1f2328',
      'editor.lineHighlightBackground': '#f5f6f8',
      'editorLineNumber.foreground': '#8b8f9a',
      'editorCursor.foreground': '#1f2328',
      'editor.selectionBackground': '#add6ff',
      'editor.inactiveSelectionBackground': '#e5ebf1',
    }
  })
}

function getTheme() {
  return themeStore.theme === 'dark' ? 'nginx-dark' : 'nginx-light'
}

onMounted(() => {
  defineNginxThemes()

  editor.value = monaco.editor.create(containerRef.value, {
    value: props.modelValue,
    language: props.language,
    theme: getTheme(),
    readOnly: props.readOnly,
    automaticLayout: false,
    minimap: { enabled: false },
    fontSize: 13,
    fontFamily: "'JetBrains Mono', 'Fira Code', 'SF Mono', 'Consolas', monospace",
    fontLigatures: true,
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    tabSize: 4,
    renderWhitespace: 'selection',
    bracketPairColorization: { enabled: true },
    padding: { top: 8, bottom: 8 },
  })

  editor.value.onDidChangeModelContent(() => {
    emit('update:modelValue', editor.value.getValue())
  })

  editor.value.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
    emit('save')
  })

  const ro = new ResizeObserver(() => editor.value?.layout())
  ro.observe(containerRef.value)
  onBeforeUnmount(() => ro.disconnect())
})

watch(() => props.modelValue, (val) => {
  if (editor.value && val !== editor.value.getValue()) {
    editor.value.setValue(val)
  }
})

watch(() => themeStore.theme, () => {
  if (editor.value) editor.value.updateOptions({ theme: getTheme() })
})

watch(() => props.readOnly, (val) => {
  if (editor.value) editor.value.updateOptions({ readOnly: val })
})

onBeforeUnmount(() => {
  if (editor.value) {
    editor.value.dispose()
    editor.value = null
  }
})

defineExpose({ focus: () => editor.value?.focus() })
</script>

<style scoped>
.monaco-container {
  width: 100%;
  height: 100%;
  min-height: 200px;
}
</style>
