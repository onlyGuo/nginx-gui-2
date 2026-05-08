<template>
  <div class="monaco-container" ref="containerRef"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, shallowRef } from 'vue'
import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import { useThemeStore } from '../../stores/theme'

const NGINX_LANGUAGE_ID = 'nginx'

const NGINX_DIRECTIVES = [
  'user', 'worker_processes', 'worker_cpu_affinity', 'worker_rlimit_nofile', 'pid', 'error_log',
  'events', 'use', 'worker_connections', 'multi_accept',
  'http', 'include', 'default_type', 'charset', 'server_tokens', 'sendfile', 'tcp_nopush', 'tcp_nodelay',
  'keepalive_timeout', 'keepalive_requests', 'client_body_timeout', 'client_header_timeout',
  'types_hash_max_size', 'client_max_body_size', 'server_names_hash_bucket_size', 'types', 'log_format',
  'access_log', 'gzip', 'gzip_min_length', 'gzip_comp_level', 'gzip_types', 'gzip_vary', 'gzip_proxied',
  'gzip_buffers', 'gzip_http_version', 'map', 'geo', 'upstream', 'zone', 'keepalive', 'least_conn',
  'ip_hash', 'random', 'server', 'listen', 'server_name', 'root', 'alias', 'index', 'try_files',
  'location', 'proxy_pass', 'proxy_redirect', 'proxy_buffering', 'proxy_buffer_size', 'proxy_buffers',
  'proxy_busy_buffers_size', 'proxy_connect_timeout', 'proxy_read_timeout', 'proxy_send_timeout',
  'proxy_set_header', 'proxy_http_version', 'proxy_cache', 'proxy_cache_valid', 'proxy_cache_key',
  'add_header', 'expires', 'return', 'rewrite', 'if', 'set', 'break', 'deny', 'allow', 'autoindex',
  'resolver', 'ssl', 'ssl_certificate', 'ssl_certificate_key', 'ssl_protocols', 'ssl_ciphers',
  'ssl_prefer_server_ciphers', 'ssl_session_timeout', 'ssl_session_cache', 'rewrite_log'
]

const NGINX_BLOCK_DIRECTIVES = new Set(['events', 'http', 'server', 'location', 'upstream', 'map', 'geo', 'if'])

const NGINX_VARIABLES = [
  '$host', '$hostname', '$http_host', '$remote_addr', '$remote_port', '$server_addr', '$server_port',
  '$server_name', '$scheme', '$https', '$request_uri', '$uri', '$document_uri', '$document_root',
  '$request_method', '$request_filename', '$query_string', '$args', '$is_args', '$arg_name', '$body_bytes_sent',
  '$bytes_sent', '$content_length', '$content_type', '$http_referer', '$http_user_agent', '$http_cookie',
  '$request_time', '$upstream_addr', '$upstream_response_time', '$upstream_connect_time',
  '$upstream_status', '$status', '$proxy_add_x_forwarded_for', '$request_id'
]

const NGINX_SNIPPETS = [
  {
    label: 'server block',
    insertText: ['server {', '\tlisten ${1:80};', '\tserver_name ${2:example.com};', '', '\tlocation / {', '\t\troot ${3:/var/www/html};', '\t\tindex index.html index.htm;', '\t}', '}'].join('\n'),
    documentation: 'Insert a basic server block'
  },
  {
    label: 'location block',
    insertText: ['location ${1:/} {', '\t${2:proxy_pass http://127.0.0.1:8080;}', '}'].join('\n'),
    documentation: 'Insert a location block'
  },
  {
    label: 'proxy headers',
    insertText: ['proxy_set_header Host $host;', 'proxy_set_header X-Real-IP $remote_addr;', 'proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;', 'proxy_set_header X-Forwarded-Proto $scheme;'].join('\n'),
    documentation: 'Insert common proxy_set_header directives'
  },
  {
    label: 'upstream block',
    insertText: ['upstream ${1:backend} {', '\tserver ${2:127.0.0.1:8080};', '}'].join('\n'),
    documentation: 'Insert an upstream block'
  }
]

let nginxRegistered = false

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

function registerNginxLanguage() {
  if (nginxRegistered) return
  nginxRegistered = true

  monaco.languages.register({ id: NGINX_LANGUAGE_ID, extensions: ['.conf', '.nginx'], aliases: ['Nginx', 'nginx'] })

  monaco.languages.setLanguageConfiguration(NGINX_LANGUAGE_ID, {
    comments: { lineComment: '#' },
    brackets: [
      ['{', '}'],
      ['[', ']'],
      ['(', ')']
    ],
    autoClosingPairs: [
      { open: '{', close: '}' },
      { open: '[', close: ']' },
      { open: '(', close: ')' },
      { open: '"', close: '"' },
      { open: "'", close: "'" }
    ],
    surroundingPairs: [
      { open: '{', close: '}' },
      { open: '[', close: ']' },
      { open: '(', close: ')' },
      { open: '"', close: '"' },
      { open: "'", close: "'" }
    ],
    folding: { offSide: false, markers: { start: /^\s*#region\b/, end: /^\s*#endregion\b/ } }
  })

  monaco.languages.setMonarchTokensProvider(NGINX_LANGUAGE_ID, {
    defaultToken: '',
    tokenPostfix: '.nginx',
    keywords: NGINX_DIRECTIVES,
    variables: NGINX_VARIABLES,
    tokenizer: {
      root: [
        [/\s+/, 'white'],
        [/#.*$/, 'comment'],
        [/\b(?:on|off|any|all|none|debug|info|notice|warn|error|crit|alert|emerg|backup|down)\b/, 'keyword'],
        [/\$[a-zA-Z0-9_]+/, 'variable'],
        [/[{}]/, 'delimiter.bracket'],
        [/;/, 'delimiter'],
        [/\b\d+(?:ms|s|m|h|d|w|k|K|m|M|g|G)?\b/, 'number'],
        [/"([^"\\]|\\.)*"/, 'string'],
        [/'([^'\\]|\\.)*'/, 'string'],
        [/[a-zA-Z_][\w-]*/, {
          cases: {
            '@keywords': 'keyword',
            '@default': 'identifier'
          }
        }],
        [/[~:=^$*+|?.,\/\-]+/, 'operator']
      ]
    }
  })

  monaco.languages.registerCompletionItemProvider(NGINX_LANGUAGE_ID, {
    triggerCharacters: ['$', ' ', '/'],
    provideCompletionItems(model, position) {
      const word = model.getWordUntilPosition(position)
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn
      }

      const directives = NGINX_DIRECTIVES.map(label => ({
        label,
        kind: NGINX_BLOCK_DIRECTIVES.has(label)
          ? monaco.languages.CompletionItemKind.Module
          : monaco.languages.CompletionItemKind.Keyword,
        insertText: NGINX_BLOCK_DIRECTIVES.has(label) ? `${label} {\n\t$0\n}` : `${label} `,
        insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
        range
      }))

      const variables = NGINX_VARIABLES.map(label => ({
        label,
        kind: monaco.languages.CompletionItemKind.Variable,
        insertText: label,
        range
      }))

      const snippets = NGINX_SNIPPETS.map(({ label, insertText, documentation }) => ({
        label,
        kind: monaco.languages.CompletionItemKind.Snippet,
        insertText,
        insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
        documentation,
        range
      }))

      return { suggestions: [...directives, ...variables, ...snippets] }
    }
  })
}

function getTheme() {
  return themeStore.theme === 'dark' ? 'nginx-dark' : 'nginx-light'
}

onMounted(() => {
  registerNginxLanguage()
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
  monaco.editor.setTheme(getTheme())
})

watch(() => props.readOnly, (val) => {
  if (editor.value) editor.value.updateOptions({ readOnly: val })
})

watch(() => props.language, (val) => {
  const model = editor.value?.getModel()
  if (model) {
    monaco.editor.setModelLanguage(model, val || 'plaintext')
  }
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
