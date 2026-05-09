<template>
  <AppLayout>
    <PathGuard>
    <div class="dashboard">
      <!-- Left: Stats + Charts -->
      <div class="dashboard-left" ref="leftPanelRef">
        <div class="stats-row">
          <div class="stat-card">
            <div class="stat-icon" style="background: var(--accent-bg)">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="8" rx="2"/><rect x="2" y="14" width="20" height="8" rx="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ status.workerCount || 0 }}</div>
              <div class="stat-label">Worker 进程</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon" style="background: var(--success-bg)">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ status.activeConnections || 0 }}</div>
              <div class="stat-label">活跃连接</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon" style="background: var(--warning-bg)">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ status.uptime || '-' }}</div>
              <div class="stat-label">运行时间</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon" :style="{ background: status.running ? 'var(--success-bg)' : 'var(--error-bg)' }">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
            </div>
            <div class="stat-info">
              <div class="stat-value" :class="status.running ? 'text-success' : 'text-error'">{{ status.running ? '运行中' : '已停止' }}</div>
              <div class="stat-label">Nginx 状态</div>
            </div>
          </div>
        </div>

        <div class="charts-row">
          <div class="card chart-card" style="grid-column: 1 / -1; min-height: 110px;">
            <div class="card-header">Nginx 信息</div>
            <div class="card-body nginx-status">
              <div class="status-grid">
                <div class="status-item">
                  <span class="status-label">版本</span>
                  <span class="status-val">{{ status.version || '-' }}</span>
                </div>
                <div class="status-item">
                  <span class="status-label">PID</span>
                  <span class="status-val">{{ status.pid || '-' }}</span>
                </div>
                <div class="status-item">
                  <span class="status-label">配置文件</span>
                  <span class="status-val">{{ status.configPath || '-' }}</span>
                </div>
                <div class="status-item">
                  <span class="status-label">CPU</span>
                  <span class="status-val">{{ status.cpu != null ? status.cpu + '%' : '-' }}</span>
                </div>
                <!--                <div class="status-item">-->
                <!--                  <span class="status-label">内存</span>-->
                <!--                  <span class="status-val">{{ status.memory?.percent != null ? status.memory.percent + '%' : '-' }}</span>-->
                <!--                </div>-->
                <!--                <div class="status-item">-->
                <!--                  <span class="status-label">内存总量</span>-->
                <!--                  <span class="status-val">{{ status.memory?.total || '-' }}</span>-->
                <!--                </div>-->
              </div>
            </div>
          </div>
        </div>

        <div class="charts-row">
          <div class="card chart-card">
            <div class="card-header">CPU 使用率</div>
            <div class="card-body chart-body">
              <div ref="cpuChartRef" class="chart"></div>
            </div>
          </div>
          <div class="card chart-card">
            <div class="card-header">内存使用率</div>
            <div class="card-body chart-body">
              <div ref="memChartRef" class="chart"></div>
            </div>
          </div>
        </div>

        <div class="charts-row">
          <div class="card chart-card">
            <div class="card-header">活跃连接数</div>
            <div class="card-body chart-body">
              <div ref="connChartRef" class="chart"></div>
            </div>
          </div>
          <div class="card chart-card">
            <div class="card-header">磁盘分区使用</div>
            <div class="card-body chart-body">
              <div ref="diskChartRef" class="chart"></div>
            </div>
          </div>
        </div>
      </div>

    <!-- Bottom: Logs -->
    <div class="dashboard-log-panel" :style="{ height: logHeight + 'px' }">
      <div class="dashboard-log-resize" @mousedown.prevent="onLogResizeStart"></div>
      <div class="card-header">
        <span>日志查看</span>
        <div class="flex gap-sm">
          <BaseSelect v-model="logType" :options="logTypeOpts" class="log-select" />
          <button class="btn btn-sm" :class="{ 'btn-active': autoScroll }" @click="autoScroll = !autoScroll" :title="autoScroll ? '自动滚动：开' : '自动滚动：关'">
            {{ autoScroll ? '⬇ 自动' : '⏸ 暂停' }}
          </button>
          <button class="btn btn-sm" @click="connectLogSSE">刷新</button>
        </div>
      </div>
      <div class="log-body" ref="logRef" @scroll="onLogScroll">
        <div v-for="(line, i) in logs" :key="i" class="log-line">
          <span class="log-text" :class="{ 'log-err': logType === 'error' }">{{ line.text }}</span>
        </div>
        <div v-if="logs.length === 0" class="log-empty">暂无日志</div>
      </div>
    </div>
    </div>
    </PathGuard>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import AppLayout from '../components/layout/AppLayout.vue'
import PathGuard from '../components/common/PathGuard.vue'
import BaseSelect from '../components/common/BaseSelect.vue'
import { useThemeStore } from '../stores/theme'
import { api, sseUrl } from '../utils/api'

const logTypeOpts = [
  { value: 'access', label: 'access.log', description: '记录所有 HTTP 请求的访问日志' },
  { value: 'error', label: 'error.log', description: '记录 Nginx 运行时错误和异常信息' }
]

const themeStore = useThemeStore()

function getCssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function getThemeColors() {
  return {
    grid: getCssVar('--chart-grid') || '#313337',
    text: getCssVar('--chart-text') || '#9da1ab',
    bg: getCssVar('--bg-card') || '#2b2d30',
    border: getCssVar('--border-primary') || '#43454a',
    primary: getCssVar('--text-primary') || '#dfe1e5'
  }
}

// ---- Status ----
const status = reactive({
  running: false, version: '', pid: '-', uptime: '-',
  workerCount: 0, activeConnections: 0, cpu: null,
  memory: {}, disk: [], configPath: ''
})

// ---- Charts ----
const cpuChartRef = ref(null)
const memChartRef = ref(null)
const diskChartRef = ref(null)
const connChartRef = ref(null)
const leftPanelRef = ref(null)

let charts = []
const MAX_POINTS = 30
const chartData = reactive({
  labels: [],
  cpu: [],
  mem: [],
  conn: []
})

let pollTimer = null

function makeLineOption(unit, color, tc, max) {
  return {
    grid: { top: 10, right: 12, bottom: 24, left: 42 },
    xAxis: {
      type: 'category', data: chartData.labels,
      axisLine: { lineStyle: { color: tc.grid } },
      axisLabel: { fontSize: 10, color: tc.text },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value', min: 0, max: max || 100,
      splitLine: { lineStyle: { color: tc.grid } },
      axisLabel: { fontSize: 10, color: tc.text, formatter: '{value}' + unit }
    },
    series: [{
      type: 'line', data: [], smooth: true, symbol: 'none',
      lineStyle: { width: 1.5, color },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: color.replace(')', ', 0.25)').replace('rgb', 'rgba') },
          { offset: 1, color: 'transparent' }
        ])
      }
    }],
    tooltip: {
      trigger: 'axis',
      formatter: '{b}<br/>{c}' + unit,
      backgroundColor: tc.bg,
      borderColor: tc.border,
      textStyle: { fontSize: 11, color: tc.primary }
    }
  }
}

function buildDiskOption(tc, diskData) {
  const labelColor = themeStore.theme === 'dark' ? '#fff' : '#000'
  const disks = diskData || []
  const mounts = disks.map(d => d.mount || d.device)
  const used = disks.map(d => d.percent || 0)
  const avail = disks.map(d => 100 - (d.percent || 0))

  return {
    grid: { top: 10, right: 40, bottom: 10, left: 60 },
    tooltip: {
      trigger: 'axis',
      formatter: function(params) {
        const idx = params[0].dataIndex
        const d = disks[idx]
        return d.mount + '<br/>已用: ' + d.used + ' (' + d.percent + '%)<br/>可用: ' + d.avail
      },
      backgroundColor: tc.bg, borderColor: tc.border,
      textStyle: { fontSize: 11, color: tc.primary }
    },
    xAxis: {
      type: 'value', min: 0, max: 100,
      splitLine: { lineStyle: { color: tc.grid } },
      axisLabel: { fontSize: 10, color: tc.text, formatter: '{value}%' },
      show: false
    },
    yAxis: {
      type: 'category', data: mounts,
      axisLine: { show: false },
      axisLabel: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        name: '已用', type: 'bar', stack: 'disk', data: used,
        itemStyle: { color: 'rgb(74, 158, 255)' },
        label: {
          show: true, position: 'insideLeft', fontSize: 10, color: labelColor,
          distance: 5,
          formatter: function(p) { return mounts[p.dataIndex] + ' ' + p.value + '%' }
        }
      },
      {
        name: '可用', type: 'bar', stack: 'disk', data: avail,
        itemStyle: { color: tc.grid, borderRadius: [0, 3, 3, 0] }
      }
    ]
  }
}

function initCharts() {
  const tc = getThemeColors()
  const cpuColor = 'rgb(74, 158, 255)'
  const memColor = 'rgb(78, 184, 86)'
  const connColor = 'rgb(168, 130, 255)'

  const cpuChart = echarts.init(cpuChartRef.value)
  const memChart = echarts.init(memChartRef.value)
  const diskChart = echarts.init(diskChartRef.value)
  const connChart = echarts.init(connChartRef.value)

  const cpuOpt = makeLineOption('%', cpuColor, tc, 100)
  cpuOpt.series[0].data = chartData.cpu
  cpuChart.setOption(cpuOpt)

  const memOpt = makeLineOption('%', memColor, tc, 100)
  memOpt.series[0].data = chartData.mem
  memChart.setOption(memOpt)

  diskChart.setOption(buildDiskOption(tc, status.disk))

  const connOpt = makeLineOption('', connColor, tc, Math.max(100, ...chartData.conn, 10))
  connOpt.series[0].data = chartData.conn
  connChart.setOption(connOpt)

  charts = [cpuChart, memChart, diskChart, connChart]
}

function updateCharts() {
  if (charts.length === 0) return
  const [cpuChart, memChart, diskChart, connChart] = charts
  const tc = getThemeColors()

  cpuChart.setOption({ xAxis: { data: chartData.labels }, series: [{ data: chartData.cpu }] })
  memChart.setOption({ xAxis: { data: chartData.labels }, series: [{ data: chartData.mem }] })
  diskChart.setOption(buildDiskOption(tc, status.disk))
  connChart.setOption({
    xAxis: { data: chartData.labels },
    yAxis: { max: Math.max(100, ...chartData.conn, 10) },
    series: [{ data: chartData.conn }]
  })
}

// ---- Data Fetching ----
let chartsInited = false

async function fetchStatus() {
  try {
    const res = await api('/api/v1/dashboard/status')
    const json = await res.json()
    if (json.code === 200 && json.data) {
      const d = json.data
      status.running = d.running ?? false
      status.version = d.version ?? ''
      status.pid = d.pid ?? '-'
      status.uptime = d.uptime ?? '-'
      status.workerCount = d.workerCount ?? 0
      status.activeConnections = d.activeConnections ?? 0
      status.cpu = d.cpu ?? null
      status.memory = d.memory ?? {}
      status.disk = d.disk ?? []
      status.configPath = d.configPath ?? ''

      // 首次加载：用后端历史数据填充图表
      if (!chartsInited && d.history && d.history.length > 0) {
        chartData.labels.splice(0)
        chartData.cpu.splice(0)
        chartData.mem.splice(0)
        chartData.conn.splice(0)
        for (const p of d.history) {
          chartData.labels.push(p.time || '')
          chartData.cpu.push(p.cpu ?? 0)
          chartData.mem.push(p.mem ?? 0)
          chartData.conn.push(p.conn ?? 0)
        }
        chartsInited = true
      }

      // 追加最新数据点
      const now = new Date().toTimeString().slice(0, 8)
      // 避免重复追加同一个时间点
      if (chartData.labels.length === 0 || chartData.labels[chartData.labels.length - 1] !== now) {
        chartData.labels.push(now)
        chartData.cpu.push(d.cpu ?? 0)
        chartData.mem.push(d.memory?.percent ?? 0)
        chartData.conn.push(d.activeConnections ?? 0)

        if (chartData.labels.length > MAX_POINTS) {
          chartData.labels.shift()
          chartData.cpu.shift()
          chartData.mem.shift()
          chartData.conn.shift()
        }
      }

      updateCharts()
    }
  } catch (e) {
    console.error('获取仪表盘数据失败:', e)
  }
}

// ---- Logs ----
const logType = ref('access')
const logs = ref([])
const logRef = ref(null)
const logHeight = ref(200)
const autoScroll = ref(true)
let logSSE = null

let logResizing = false
let logStartY = 0
let logStartHeight = 0

function onLogResizeStart(e) {
  logResizing = true
  logStartY = e.clientY
  logStartHeight = logHeight.value
  document.addEventListener('mousemove', onLogResizeMove)
  document.addEventListener('mouseup', onLogResizeEnd)
  document.body.style.cursor = 'row-resize'
  document.body.style.userSelect = 'none'
}

function onLogResizeMove(e) {
  if (!logResizing) return
  logHeight.value = Math.max(80, Math.min(600, logStartHeight + (logStartY - e.clientY)))
}

function onLogResizeEnd() {
  logResizing = false
  document.removeEventListener('mousemove', onLogResizeMove)
  document.removeEventListener('mouseup', onLogResizeEnd)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  nextTick(() => { if (charts.length) charts.forEach(c => c.resize()) })
}

function onLogScroll() {
  if (!logRef.value) return
  const { scrollTop, scrollHeight, clientHeight } = logRef.value
  autoScroll.value = scrollHeight - scrollTop - clientHeight < 30
}

function scrollLogToBottom() {
  nextTick(() => {
    if (logRef.value && autoScroll.value) {
      logRef.value.scrollTop = logRef.value.scrollHeight
    }
  })
}

function connectLogSSE() {
  if (logSSE) {
    logSSE.close()
    logSSE = null
  }
  logSSE = new EventSource(sseUrl('/api/v1/dashboard/logs/stream?type=' + logType.value))

  logSSE.addEventListener('init', (e) => {
    const lines = JSON.parse(e.data)
    logs.value = lines.map(text => ({ text }))
    scrollLogToBottom()
  })

  logSSE.addEventListener('log', (e) => {
    const lines = JSON.parse(e.data)
    for (const text of lines) {
      logs.value.push({ text })
    }
    // 限制最大行数
    if (logs.value.length > 500) {
      logs.value.splice(0, logs.value.length - 500)
    }
    scrollLogToBottom()
  })

  logSSE.onerror = () => {
    logSSE.close()
    logSSE = null
    // 3 秒后重连
    setTimeout(connectLogSSE, 3000)
  }
}

function fetchLogs() {
  connectLogSSE()
}

watch(logType, () => {
  logs.value = []
  connectLogSSE()
})

let resizeObserver = null

onMounted(async () => {
  // 计算日志面板默认高度：剩余可视空间
  const dashboardHeight = window.innerHeight - 36
  const chartsHeight = 670
  const gap = 32 // var(--space-lg) * 2
  logHeight.value = Math.max(120, dashboardHeight - chartsHeight - gap)
  await fetchStatus()
  nextTick(() => {
    initCharts()
    updateCharts()
    connectLogSSE()
  })

  // 轮询
  pollTimer = setInterval(() => {
    fetchStatus()
    fetchLogs()
  }, 5000)

  watch(() => themeStore.theme, () => {
    nextTick(() => {
      if (charts.length > 0) {
        const tc = getThemeColors()
        charts.forEach(c => {
          c.setOption({
            xAxis: { axisLine: { lineStyle: { color: tc.grid } }, axisLabel: { color: tc.text } },
            yAxis: { splitLine: { lineStyle: { color: tc.grid } }, axisLabel: { color: tc.text } },
            tooltip: { backgroundColor: tc.bg, borderColor: tc.border, textStyle: { color: tc.primary } }
          })
          c.resize()
        })
      }
    })
  })

  if (leftPanelRef.value) {
    resizeObserver = new ResizeObserver(() => charts.forEach(c => c.resize()))
    resizeObserver.observe(leftPanelRef.value)
  }
})

onBeforeUnmount(() => {
  clearInterval(pollTimer)
  charts.forEach(c => c.dispose())
  if (resizeObserver) resizeObserver.disconnect()
  if (logSSE) { logSSE.close(); logSSE = null }
  document.removeEventListener('mousemove', onLogResizeMove)
  document.removeEventListener('mouseup', onLogResizeEnd)
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 36px);
  margin: calc(-1 * var(--space-lg));
}

/* Top: Stats + Charts */
.dashboard-left {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  padding: var(--space-lg);
}

/* Stats */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-md);
  flex-shrink: 0;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  background: var(--bg-card);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-md);
  padding: var(--space-lg);
}
.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
}
.stat-icon span {
  display: flex;
  color: var(--accent);
}
.stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat-value {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
.stat-label {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

/* Charts */
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-md);
  flex-shrink: 0;
}
.chart-card {
  min-height: 220px;
}
.chart-body {
  padding: var(--space-md) !important;
}
.chart {
  width: 100%;
  height: 180px;
}

/* Nginx Status */
.nginx-status {
  display: flex;
  align-items: center;
  padding: var(--space-lg) !important;
}
.status-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-md) var(--space-xxl);
  width: 100%;
}
.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-md);
}
.status-label {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}
.status-val {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  font-family: var(--font-mono);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

/* Log Bottom Panel */
.dashboard-log-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 80px;
  max-height: 600px;
  border-top: 1px solid var(--border-primary);
  background: var(--bg-secondary);
  flex-shrink: 0;
}
.dashboard-log-resize {
  position: absolute;
  top: -3px;
  left: 0;
  right: 0;
  height: 6px;
  cursor: row-resize;
  z-index: 10;
}
.dashboard-log-resize:hover,
.dashboard-log-resize:active {
  background: var(--border-focus);
}
.log-select {
  width: 120px;
}
.btn-active {
  background: var(--accent-bg);
  color: var(--accent);
}
.log-body {
  flex: 1;
  overflow-y: auto;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  padding: var(--space-md);
}
.log-line {
  display: flex;
  gap: var(--space-md);
  padding: 1px 0;
  line-height: 1.6;
}
.log-text {
  color: var(--text-secondary);
  word-break: break-all;
  white-space: pre-wrap;
}
.log-text.log-err {
  color: var(--error);
}
.log-empty {
  color: var(--text-tertiary);
  text-align: center;
  padding: var(--space-xxl);
}
</style>
