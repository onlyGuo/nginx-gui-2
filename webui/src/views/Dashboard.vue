<template>
  <AppLayout>
    <PathGuard>
    <div class="dashboard">
      <!-- Left: Stats + Charts -->
      <div class="dashboard-left" ref="leftPanelRef">
        <div class="stats-row">
          <div class="stat-card" v-for="s in stats" :key="s.label">
            <div class="stat-icon" :style="{ background: s.bg }">
              <span v-html="s.icon"></span>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ s.value }}</div>
              <div class="stat-label">{{ s.label }}</div>
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
            <div class="card-header">请求速率 (RPS)</div>
            <div class="card-body chart-body">
              <div ref="rpsChartRef" class="chart"></div>
            </div>
          </div>
          <div class="card chart-card">
            <div class="card-header">磁盘分区使用</div>
            <div class="card-body chart-body">
              <div ref="diskChartRef" class="chart"></div>
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
            <div class="card-header">Nginx 状态</div>
            <div class="card-body nginx-status">
              <div class="status-grid">
                <div class="status-item" v-for="item in nginxStatus" :key="item.label">
                  <span class="status-label">{{ item.label }}</span>
                  <span class="status-val" :class="item.cls">{{ item.value }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Logs -->
      <div class="dashboard-right">
        <div class="card log-card">
          <div class="card-header">
            <span>日志查看</span>
            <div class="flex gap-sm">
              <BaseSelect v-model="logType" :options="logTypeOpts" class="log-select" />
              <button class="btn btn-sm" @click="clearLogs">清空</button>
            </div>
          </div>
          <div class="card-body log-body" ref="logRef">
            <div v-for="(line, i) in logs" :key="i" class="log-line">
              <span class="log-time">{{ line.time }}</span>
              <span class="log-text" :class="{ 'log-err': logType === 'error' }">{{ line.text }}</span>
            </div>
            <div v-if="logs.length === 0" class="log-empty">暂无日志</div>
          </div>
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

const logTypeOpts = [
  { value: 'access', label: 'access.log', description: '记录所有 HTTP 请求的访问日志' },
  { value: 'error', label: 'error.log', description: '记录 Nginx 运行时错误和异常信息' }
]

const themeStore = useThemeStore()

// ---- Read CSS variable from document ----
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

// ---- Stats ----
const stats = ref([
  { label: '运行时间', value: '15d 6h', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>', bg: 'var(--accent-bg)' },
  { label: '总请求数', value: '1,284,593', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>', bg: 'var(--success-bg)' },
  { label: '活跃连接', value: '342', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>', bg: 'var(--warning-bg)' },
  { label: 'Worker 进程', value: '4', icon: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="8" rx="2"/><rect x="2" y="14" width="20" height="8" rx="2"/><line x1="6" y1="6" x2="6.01" y2="6"/><line x1="6" y1="18" x2="6.01" y2="18"/></svg>', bg: 'var(--error-bg)' }
])

const nginxStatus = ref([
  { label: '版本', value: 'nginx/1.24.0', cls: '' },
  { label: '状态', value: '运行中', cls: 'text-success' },
  { label: '配置文件', value: '/etc/nginx/nginx.conf', cls: '' },
  { label: 'PID', value: '12345', cls: '' },
  { label: 'Worker 连接数', value: '1024', cls: '' },
  { label: 'Keepalive', value: '65', cls: '' }
])

// ---- Charts ----
const cpuChartRef = ref(null)
const memChartRef = ref(null)
const rpsChartRef = ref(null)
const diskChartRef = ref(null)
const connChartRef = ref(null)
const leftPanelRef = ref(null)

let charts = []
let chartData = { cpu: [], mem: [], rps: [], conn: [], labels: [] }
let timer = null

function makeLineOption(unit, color, tc) {
  return {
    grid: { top: 10, right: 12, bottom: 24, left: 42 },
    xAxis: {
      type: 'category', data: chartData.labels,
      axisLine: { lineStyle: { color: tc.grid } },
      axisLabel: { fontSize: 10, color: tc.text },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value', min: 0, max: 100,
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

function genTimeLabels(count) {
  const labels = []
  const now = new Date()
  for (let i = count - 1; i >= 0; i--) {
    const d = new Date(now - i * 2000)
    labels.push(d.toTimeString().slice(0, 8))
  }
  return labels
}

function genData(count, min, max) {
  return Array.from({ length: count }, () => Math.round(min + Math.random() * (max - min)))
}

function buildDiskOption(tc) {
  return {
    tooltip: {
      trigger: 'item', formatter: '{b}: {d}%',
      backgroundColor: tc.bg, borderColor: tc.border,
      textStyle: { fontSize: 11, color: tc.primary }
    },
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['50%', '50%'],
      data: [
        { value: 45, name: '/', itemStyle: { color: 'rgb(74, 158, 255)' } },
        { value: 20, name: '/home', itemStyle: { color: 'rgb(78, 184, 86)' } },
        { value: 15, name: '/var', itemStyle: { color: 'rgb(232, 168, 56)' } },
        { value: 20, name: '可用', itemStyle: { color: tc.grid } }
      ],
      label: { fontSize: 10, color: tc.text },
      labelLine: { lineStyle: { color: tc.grid } }
    }]
  }
}

function initCharts() {
  const tc = getThemeColors()
  const cpuColor = 'rgb(74, 158, 255)'
  const memColor = 'rgb(78, 184, 86)'
  const rpsColor = 'rgb(232, 168, 56)'
  const connColor = 'rgb(168, 130, 255)'

  chartData.labels = genTimeLabels(30)
  chartData.cpu = genData(30, 10, 65)
  chartData.mem = genData(30, 40, 75)
  chartData.rps = genData(30, 200, 3500)
  chartData.conn = genData(30, 100, 800)

  const cpuChart = echarts.init(cpuChartRef.value)
  const memChart = echarts.init(memChartRef.value)
  const rpsChart = echarts.init(rpsChartRef.value)
  const diskChart = echarts.init(diskChartRef.value)
  const connChart = echarts.init(connChartRef.value)

  const cpuOpt = makeLineOption('%', cpuColor, tc)
  cpuOpt.yAxis.max = 100
  cpuOpt.series[0].data = chartData.cpu
  cpuChart.setOption(cpuOpt)

  const memOpt = makeLineOption('%', memColor, tc)
  memOpt.yAxis.max = 100
  memOpt.series[0].data = chartData.mem
  memChart.setOption(memOpt)

  const rpsOpt = makeLineOption('', rpsColor, tc)
  rpsOpt.yAxis.max = 5000
  rpsOpt.series[0].data = chartData.rps
  rpsChart.setOption(rpsOpt)

  diskChart.setOption(buildDiskOption(tc))

  const connOpt = makeLineOption('', connColor, tc)
  connOpt.yAxis.max = 1500
  connOpt.series[0].data = chartData.conn
  connChart.setOption(connOpt)

  charts = [cpuChart, memChart, rpsChart, diskChart, connChart]

  // simulate real-time
  timer = setInterval(() => {
    const now = new Date().toTimeString().slice(0, 8)
    chartData.labels.push(now)
    chartData.cpu.push(Math.round(10 + Math.random() * 55))
    chartData.mem.push(Math.round(40 + Math.random() * 35))
    chartData.rps.push(Math.round(200 + Math.random() * 3300))
    chartData.conn.push(Math.round(100 + Math.random() * 700))

    if (chartData.labels.length > 30) {
      chartData.labels.shift()
      chartData.cpu.shift()
      chartData.mem.shift()
      chartData.rps.shift()
      chartData.conn.shift()
    }

    cpuChart.setOption({ xAxis: { data: chartData.labels }, series: [{ data: chartData.cpu }] })
    memChart.setOption({ xAxis: { data: chartData.labels }, series: [{ data: chartData.mem }] })
    rpsChart.setOption({ xAxis: { data: chartData.labels }, series: [{ data: chartData.rps }] })
    connChart.setOption({ xAxis: { data: chartData.labels }, series: [{ data: chartData.conn }] })
  }, 2000)
}

function applyThemeToCharts() {
  if (charts.length === 0) return
  const tc = getThemeColors()
  const [cpuChart, memChart, rpsChart, diskChart, connChart] = charts

  function patchLine(chart) {
    chart.setOption({
      xAxis: {
        axisLine: { lineStyle: { color: tc.grid } },
        axisLabel: { color: tc.text }
      },
      yAxis: {
        splitLine: { lineStyle: { color: tc.grid } },
        axisLabel: { color: tc.text }
      },
      tooltip: { backgroundColor: tc.bg, borderColor: tc.border, textStyle: { color: tc.primary } }
    })
  }

  patchLine(cpuChart)
  patchLine(memChart)
  patchLine(rpsChart)
  patchLine(connChart)
  diskChart.setOption(buildDiskOption(tc))

  charts.forEach(c => c.resize())
}

// ---- Logs ----
const logType = ref('access')
const logs = ref([])
const logRef = ref(null)

const accessTemplates = [
  'GET /api/status 200 3ms',
  'GET /index.html 200 12ms',
  'POST /api/login 200 45ms',
  'GET /static/js/app.js 304 1ms',
  'GET /api/config 200 8ms',
  'GET /favicon.ico 404 1ms',
  'GET /api/logs 200 120ms',
  'PUT /api/config 200 15ms',
  'DELETE /api/cache 200 3ms',
  'GET /health 200 1ms'
]
const errorTemplates = [
  'upstream timed out (110: Connection timed out)',
  'connect() failed (111: Connection refused)',
  'no live upstreams while connecting to upstream',
  'SSL_do_handshake() failed',
  'client intended to send too large body'
]

function addLog() {
  const templates = logType.value === 'access' ? accessTemplates : errorTemplates
  const text = templates[Math.floor(Math.random() * templates.length)]
  const time = new Date().toTimeString().slice(0, 8)
  logs.value.push({ time, text })
  if (logs.value.length > 200) logs.value.shift()
  nextTick(() => {
    if (logRef.value) logRef.value.scrollTop = logRef.value.scrollHeight
  })
}

function clearLogs() {
  logs.value = []
}

let logTimer = null
let resizeObserver = null

onMounted(() => {
  nextTick(() => initCharts())
  for (let i = 0; i < 15; i++) addLog()
  logTimer = setInterval(addLog, 3000)

  // watch theme changes
  watch(() => themeStore.theme, () => {
    nextTick(() => applyThemeToCharts())
  })

  // watch container resize
  if (leftPanelRef.value) {
    resizeObserver = new ResizeObserver(() => {
      charts.forEach(c => c.resize())
    })
    resizeObserver.observe(leftPanelRef.value)
  }
})

onBeforeUnmount(() => {
  clearInterval(timer)
  clearInterval(logTimer)
  charts.forEach(c => c.dispose())
  if (resizeObserver) resizeObserver.disconnect()
})
</script>

<style scoped>
.dashboard {
  display: flex;
  gap: var(--space-lg);
  height: 100%;
  min-height: 0;
}

/* Left: Stats + Charts */
.dashboard-left {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

/* Right: Logs */
.dashboard-right {
  width: 50%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
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
}

/* Log */
.log-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.log-select {
  width: 120px;
}
.log-body {
  flex: 1;
  overflow-y: auto;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  padding: var(--space-md) !important;
}
.log-line {
  display: flex;
  gap: var(--space-md);
  padding: 1px 0;
  line-height: 1.6;
}
.log-time {
  color: var(--text-tertiary);
  flex-shrink: 0;
}
.log-text {
  color: var(--text-secondary);
  word-break: break-all;
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
