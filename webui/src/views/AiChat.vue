<template>
  <AppLayout>
    <div class="ai-chat-page">
      <!-- Left: Chat Area -->
      <div class="chat-main">
        <!-- Messages -->
        <div class="chat-messages" ref="messagesRef">
          <div v-if="messages.length === 0" class="chat-empty">
            <div class="chat-empty-icon">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <div class="chat-empty-title">AI 自助配置</div>
            <div class="chat-empty-desc">描述你的需求或指令，AI 将帮助你完成 Nginx 配置或操作Nginx</div>
          </div>
          <div v-for="(msg, mi) in messages" :key="mi" class="chat-msg" :class="'chat-msg-' + msg.role">
            <div class="chat-avatar">
              <svg v-if="msg.role === 'user'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
              </svg>
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2a2 2 0 0 1 2 2c0 .74-.4 1.39-1 1.73V7h1a7 7 0 0 1 7 7h1a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-1.07A7.001 7.001 0 0 1 7.07 19H6a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1h1a7 7 0 0 1 7-7h1V5.73c-.6-.34-1-.99-1-1.73a2 2 0 0 1 2-2z"/>
                <circle cx="9.5" cy="15.5" r="1"/><circle cx="14.5" cy="15.5" r="1"/>
              </svg>
            </div>
            <div class="chat-bubble">
              <!-- User message: plain text -->
              <div v-if="msg.role === 'user'" class="chat-text">{{ msg.content }}</div>
              <!-- AI message: render blocks -->
              <template v-else>
                <template v-for="(block, bi) in msg.blocks" :key="bi">
                  <!-- Think block -->
                  <div v-if="block.type === 'think'" class="block-think">
                    <div class="block-think-label">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>
                      </svg>
                      思考过程
                    </div>
                    <div class="block-think-content">{{ block.content }}</div>
                  </div>
                  <!-- Message block -->
                  <div v-else-if="block.type === 'message'" class="block-message" v-html="renderMarkdown(block.content)"></div>
                  <!-- Tool block -->
                  <div v-else-if="block.type === 'tool'" class="block-tool">
                    <div class="block-tool-header">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
                      </svg>
                      <span>{{ block.data?.name || '工具调用' }}</span>
                      <span class="block-tool-status" :class="block.data?.status || 'running'">
                        {{ block.data?.status === 'done' ? '完成' : '执行中...' }}
                      </span>
                    </div>
                    <div v-if="block.data?.input" class="block-tool-detail">
                      <div class="block-tool-label">输入</div>
                      <pre class="block-tool-code">{{ formatJson(block.data.input) }}</pre>
                    </div>
                    <div v-if="block.data?.output" class="block-tool-detail">
                      <div class="block-tool-label">输出</div>
                      <pre class="block-tool-code">{{ formatJson(block.data.output) }}</pre>
                    </div>
                  </div>
                  <!-- Plans block -->
                  <div v-else-if="block.type === 'plans'" class="block-plans">
                    <div class="block-plans-header">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
                      </svg>
                      执行计划
                    </div>
                    <div v-for="(plan, pi) in block.data?.items || []" :key="pi" class="block-plan-item">
                      <span class="plan-status-icon" :class="plan.status">
                        <svg v-if="plan.status === 'done'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                        <svg v-else-if="plan.status === 'running'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                        <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/></svg>
                      </span>
                      <span class="plan-item-name" :class="'plan-' + plan.status">{{ plan.name }}</span>
                    </div>
                  </div>
                </template>
                <!-- Streaming indicator -->
                <div v-if="msg.streaming" class="streaming-cursor"></div>
              </template>
            </div>
          </div>
        </div>

        <!-- Input Area -->
        <div class="chat-input-area">
          <div class="chat-toolbar">
            <button class="btn btn-ghost btn-icon btn-sm" @click="showConfig = true" title="LLM 配置">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
              </svg>
            </button>
            <button class="btn btn-ghost btn-icon btn-sm" @click="clearMessages" title="清空消息">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
            </button>
          </div>
          <div class="chat-input-wrap">
            <textarea
              ref="inputRef"
              v-model="inputText"
              @keydown="handleKeydown"
              placeholder="描述你的需求，例如：帮我配置一个反向代理..."
              rows="1"
            ></textarea>
            <button class="btn btn-primary btn-send" @click="sendMessage" :disabled="!inputText.trim() || isStreaming">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </button>
          </div>
        </div>
      </div>

      <!-- Right: Plan Panel -->
      <div class="plan-panel">
        <div class="plan-panel-header">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
          </svg>
          计划列表
        </div>
        <div class="plan-panel-body">
          <div v-if="plans.length === 0" class="plan-empty">暂无计划</div>
          <div v-for="(plan, pi) in plans" :key="pi" class="plan-group">
            <div class="plan-group-title">{{ plan.name }}</div>
            <div v-for="(item, ii) in plan.items" :key="ii" class="plan-list-item">
              <span class="plan-status-dot" :class="item.status"></span>
              <span class="plan-list-name" :class="'plan-' + item.status">{{ item.name }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Config Modal -->
      <Teleport to="body">
        <div v-if="showConfig" class="modal-overlay" @click.self="showConfig = false">
          <div class="modal-dialog">
            <div class="modal-header">
              <span>LLM 配置</span>
              <button class="btn btn-ghost btn-icon btn-sm" @click="showConfig = false">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
            <div class="modal-body">
              <div class="form-group">
                <label class="form-label">Base URL</label>
                <BaseCombo v-model="llmConfig.baseUrl" :options="baseUrlOptions" placeholder="输入或选择 Base URL" />
              </div>
              <div class="form-group">
                <label class="form-label">模型名称</label>
                <input v-model="llmConfig.modelName" type="text" placeholder="mimo" />
              </div>
              <div class="form-group">
                <label class="form-label">API Key</label>
                <input v-model="llmConfig.apiKey" type="password" placeholder="输入 API Key" />
              </div>
              <div class="form-group">
                <label class="form-label">接口协议</label>
                <BaseSelect v-model="llmConfig.protocol" :options="protocolOptions" placeholder="选择接口协议" />
              </div>
            </div>
            <div class="modal-footer">
              <button class="btn" @click="showConfig = false">取消</button>
              <button class="btn btn-primary" @click="saveConfig">保存</button>
            </div>
          </div>
        </div>
      </Teleport>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { marked } from 'marked'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import AppLayout from '../components/layout/AppLayout.vue'
import BaseCombo from '../components/common/BaseCombo.vue'
import BaseSelect from '../components/common/BaseSelect.vue'
import { api, sseUrl } from '../utils/api'

const STORAGE_KEY = 'ai-chat-messages'
const MAX_MESSAGES = 20

marked.setOptions({
  breaks: true,
  gfm: true
})

const messages = ref([])
const plans = ref([])
const inputText = ref('')
const isStreaming = ref(false)
const showConfig = ref(false)
const messagesRef = ref(null)
const inputRef = ref(null)

const llmConfig = reactive({
  baseUrl: '',
  apiKey: '',
  modelName: '',
  protocol: 'OpenAI'
})
const protocolOptions = ref([])
const baseUrlOptions = [
  { value: 'https://api.xiaomimimo.com', label: 'MiMo', description: 'https://api.xiaomimimo.com' },
  { value: 'https://token-plan-cn.xiaomimimo.com', label: 'MiMo(Token Plan)', description: 'https://token-plan-cn.xiaomimimo.com' },
  { value: 'https://0101.run', label: '0101', description: 'https://0101.run' }
]

// ---- Config API ----
async function loadConfig() {
  try {
    const [configRes, protocolsRes] = await Promise.all([
      api('/api/v1/chat/config'),
      api('/api/v1/chat/protocols')
    ])
    const configJson = await configRes.json()
    const protocolsJson = await protocolsRes.json()
    if (configJson.code === 200 && configJson.data) {
      Object.assign(llmConfig, configJson.data)
    }
    if (protocolsJson.code === 200 && protocolsJson.data) {
      protocolOptions.value = protocolsJson.data
    }
  } catch (e) {
    console.error('加载配置失败:', e)
  }
}

async function saveConfig() {
  try {
    await api('/api/v1/chat/config', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(llmConfig)
    })
    showConfig.value = false
  } catch (e) {
    console.error('保存配置失败:', e)
  }
}

// ---- Message persistence (localStorage for display) ----
function loadMessages() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) messages.value = JSON.parse(raw)
  } catch {}
}

function saveMessages() {
  const toSave = messages.value.slice(-MAX_MESSAGES)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(toSave))
}

// ---- Markdown renderer ----
function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}

function formatJson(obj) {
  if (typeof obj === 'string') {
    try { return JSON.stringify(JSON.parse(obj), null, 2) } catch { return obj }
  }
  try { return JSON.stringify(obj, null, 2) } catch { return String(obj) }
}

// ---- Scroll ----
function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// ---- Input ----
function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

async function clearMessages() {
  messages.value = []
  plans.value = []
  localStorage.removeItem(STORAGE_KEY)
  try {
    await api('/api/v1/chat/clear', { method: 'POST' })
  } catch (e) {
    console.error('清除会话失败:', e)
  }
}

// ---- SSE Chat ----
async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  saveMessages()
  scrollToBottom()

  isStreaming.value = true
  plans.value = []
  messages.value.push({ role: 'assistant', blocks: [], streaming: true })
  const aiMsg = messages.value[messages.value.length - 1]
  scrollToBottom()

  const token = sessionStorage.getItem('nginx-gui-token')

  try {
    await fetchEventSource(sseUrl('/api/v1/chat/send'), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': 'Bearer ' + token } : {})
      },
      body: JSON.stringify({ message: text }),
      onmessage(event) {
        handleSseEvent(event.event, event.data, aiMsg)
      },
      onerror(err) {
        aiMsg.blocks.push({ type: 'message', content: '连接错误: ' + err.message })
        throw err
      }
    })
  } catch (e) {
    console.error('请求失败:', e)
  }

  aiMsg.streaming = false
  isStreaming.value = false
  saveMessages()
  scrollToBottom()
}

function handleSseEvent(event, data, aiMsg) {
  const lastIdx = aiMsg.blocks.length - 1
  switch (event) {
    case 'think':
      if (data) {
        const text = JSON.parse(data)
        const last = aiMsg.blocks[lastIdx]
        if (last && last.type === 'think') {
          aiMsg.blocks.splice(lastIdx, 1, { type: 'think', content: last.content + text })
        } else {
          aiMsg.blocks.push({ type: 'think', content: text })
        }
      }
      break
    case 'message':
      if (data) {
        const text = JSON.parse(data)
        const last = aiMsg.blocks[lastIdx]
        if (last && last.type === 'message') {
          aiMsg.blocks.splice(lastIdx, 1, { type: 'message', content: last.content + text })
        } else {
          aiMsg.blocks.push({ type: 'message', content: text })
        }
      }
      break
    case 'tool':
      try {
        const toolData = JSON.parse(data)
        const last = aiMsg.blocks[lastIdx]
        if (last && last.type === 'tool' && last.data?.name === toolData.name && last.data?.status === 'running') {
          aiMsg.blocks.splice(lastIdx, 1, { type: 'tool', data: { ...last.data, ...toolData } })
        } else {
          aiMsg.blocks.push({ type: 'tool', data: toolData })
        }
      } catch { aiMsg.blocks.push({ type: 'tool', data: { name: data, status: 'running' } }) }
      break
    case 'plans':
      try {
        const planData = JSON.parse(data)
        aiMsg.blocks.push({ type: 'plans', data: planData })
        plans.value = [{ name: planData.name, items: planData.items.map(it => ({ ...it })) }]
      } catch {}
      break
    case 'plan_update':
      try {
        const updateData = JSON.parse(data)
        const planIdx = aiMsg.blocks.findIndex(b => b.type === 'plans')
        if (planIdx >= 0) {
          aiMsg.blocks.splice(planIdx, 1, { type: 'plans', data: updateData })
          plans.value = [{ name: updateData.name, items: updateData.items.map(it => ({ ...it })) }]
        }
      } catch {}
      break
    case 'error':
      try { aiMsg.blocks.push({ type: 'message', content: '错误: ' + JSON.parse(data) }) } catch { aiMsg.blocks.push({ type: 'message', content: '错误: ' + data }) }
      break
    case 'done':
      plans.value = plans.value.map(p => ({
        ...p,
        items: p.items.map(it => ({ ...it, status: 'done' }))
      }))
      aiMsg.blocks.forEach((block, i) => {
        if (block.type === 'plans') {
          aiMsg.blocks.splice(i, 1, {
            type: 'plans',
            data: { name: block.data.name, items: block.data.items.map(it => ({ ...it, status: 'done' })) }
          })
        }
      })
      break
  }
  scrollToBottom()
}

onMounted(() => {
  loadConfig()
  loadMessages()
})
</script>

<style scoped>
.ai-chat-page {
  display: flex;
  height: 100%;
  gap: 1px;
  margin: calc(-1 * var(--space-lg));
  background: var(--border-secondary);
}

/* ---- Chat Main ---- */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--bg-primary);
}

/* Messages */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: var(--space-md);
  color: var(--text-tertiary);
}
.chat-empty-icon {
  opacity: 0.3;
}
.chat-empty-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-secondary);
}
.chat-empty-desc {
  font-size: var(--font-size-sm);
}

/* Message Row */
.chat-msg {
  display: flex;
  gap: var(--space-md);
  max-width: 85%;
}
.chat-msg-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}
.chat-msg-assistant {
  align-self: flex-start;
}

.chat-avatar {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}
.chat-msg-user .chat-avatar {
  background: var(--accent-bg);
  color: var(--accent);
}

.chat-bubble {
  border-radius: var(--radius-md);
  padding: var(--space-md) var(--space-lg);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  min-width: 0;
}
.chat-msg-user .chat-bubble {
  background: var(--accent);
  color: #fff;
}
.chat-msg-assistant .chat-bubble {
  background: var(--bg-card);
  border: 1px solid var(--border-secondary);
  color: var(--text-primary);
  width: 100%;
}

.chat-text {
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---- Blocks ---- */
.block-think {
  background: var(--bg-secondary);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
  margin-bottom: var(--space-sm);
}
.block-think-label {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin-bottom: var(--space-xs);
}
.block-think-content {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  line-height: 1.5;
}

.block-message {
  margin-bottom: var(--space-sm);
}
.block-message :deep(p) { margin: var(--space-xs) 0; }
.block-message :deep(h2) { font-size: var(--font-size-md); margin: var(--space-md) 0 var(--space-xs); }
.block-message :deep(h3) { font-size: var(--font-size-base); margin: var(--space-md) 0 var(--space-xs); }
.block-message :deep(h4) { font-size: var(--font-size-sm); margin: var(--space-sm) 0 var(--space-xs); }
.block-message :deep(strong) { font-weight: 600; }
.block-message :deep(pre) {
  background: var(--bg-secondary);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  margin: var(--space-sm) 0;
  overflow-x: auto;
}
.block-message :deep(pre code) {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  background: transparent;
  padding: 0;
  border-radius: 0;
}
.block-message :deep(code) {
  background: var(--bg-tertiary);
  padding: 1px 4px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 0.9em;
}
.block-message :deep(ul),
.block-message :deep(ol) {
  padding-left: var(--space-xl);
  margin: var(--space-xs) 0;
}
.block-message :deep(li) {
  margin: 2px 0;
}

/* Tool block */
.block-tool {
  background: var(--bg-secondary);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-sm);
  overflow: hidden;
}
.block-tool-header {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-secondary);
}
.block-tool-status {
  margin-left: auto;
  font-weight: 400;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
}
.block-tool-status.done {
  background: var(--success-bg);
  color: var(--success);
}
.block-tool-status.running {
  background: var(--warning-bg);
  color: var(--warning);
}
.block-tool-detail {
  padding: var(--space-sm) var(--space-md);
}
.block-tool-label {
  font-size: 10px;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: var(--space-xs);
}
.block-tool-code {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  background: var(--bg-primary);
  border-radius: var(--radius-sm);
  padding: var(--space-sm);
  overflow-x: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

/* Plans block */
.block-plans {
  background: var(--bg-secondary);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
  margin-bottom: var(--space-sm);
}
.block-plans-header {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: var(--space-sm);
}
.block-plan-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 2px 0;
}
.plan-status-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.plan-status-icon.done { color: var(--success); }
.plan-status-icon.running { color: var(--warning); }
.plan-status-icon.pending { color: var(--text-tertiary); }
.plan-item-name {
  font-size: var(--font-size-xs);
  color: var(--text-primary);
}
.plan-item-name.plan-done { color: var(--text-tertiary); }
.plan-item-name.plan-running { color: var(--text-primary); font-weight: 500; }
.plan-item-name.plan-pending { color: var(--text-tertiary); }

/* Streaming cursor */
.streaming-cursor {
  display: inline-block;
  width: 6px;
  height: 14px;
  background: var(--accent);
  animation: blink 0.8s step-end infinite;
  vertical-align: text-bottom;
  margin-left: 2px;
}
@keyframes blink {
  50% { opacity: 0; }
}

/* ---- Input Area ---- */
.chat-input-area {
  border-top: 1px solid var(--border-secondary);
  padding: var(--space-md);
  flex-shrink: 0;
}
.chat-toolbar {
  display: flex;
  gap: var(--space-xs);
  margin-bottom: var(--space-sm);
}
.chat-input-wrap {
  display: flex;
  gap: var(--space-sm);
  align-items: flex-end;
}
.chat-input-wrap textarea {
  flex: 1;
  height: 36px;
  min-height: 36px;
  max-height: 120px;
  resize: none;
  padding: var(--space-sm) var(--space-md);
  font-size: var(--font-size-sm);
  line-height: 20px;
}
.btn-send {
  width: 36px;
  height: 36px;
  padding: 0;
  flex-shrink: 0;
}

/* ---- Plan Panel ---- */
.plan-panel {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
}
.plan-panel-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-md) var(--space-lg);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-secondary);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.plan-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-md);
}
.plan-empty {
  text-align: center;
  padding: var(--space-xxl);
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
}
.plan-group {
  margin-bottom: var(--space-lg);
}
.plan-group-title {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: var(--space-sm);
  padding-bottom: var(--space-xs);
  border-bottom: 1px solid var(--border-secondary);
}
.plan-list-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) 0;
}
.plan-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.plan-status-dot.done { background: var(--success); }
.plan-status-dot.running { background: var(--warning); }
.plan-status-dot.pending { background: var(--text-tertiary); }
.plan-list-name {
  font-size: var(--font-size-xs);
}
.plan-list-name.plan-done { color: var(--text-tertiary); text-decoration: line-through; }
.plan-list-name.plan-running { color: var(--text-primary); font-weight: 500; }
.plan-list-name.plan-pending { color: var(--text-tertiary); }

/* ---- Modal ---- */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal);
}
.modal-dialog {
  background: var(--bg-card);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-lg);
  width: 400px;
  box-shadow: var(--shadow-lg);
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--border-secondary);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
}
.modal-body {
  padding: var(--space-lg);
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid var(--border-secondary);
}
</style>
