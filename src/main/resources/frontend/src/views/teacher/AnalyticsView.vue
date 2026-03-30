<template>
  <el-card v-loading="loading" class="page-card analytics-page">
    <template #header>
      <div class="header">数据看板 (ECharts)</div>
    </template>

    <div class="toolbar">
      <el-select
        v-model="selectedExamId"
        filterable
        clearable
        placeholder="请选择考试"
        class="toolbar-control"
      >
        <el-option
          v-for="exam in examOptions"
          :key="exam.examId"
          :label="examLabel(exam)"
          :value="String(exam.examId)"
        >
          <div class="exam-option-row">
            <span class="exam-option-id">{{ exam.examId }}</span>
            <span>{{ exam.name }}</span>
          </div>
        </el-option>
      </el-select>

      <el-select v-model="topN" class="toolbar-short" placeholder="TopN">
        <el-option v-for="item in topNOptions" :key="item" :label="`Top ${item}`" :value="item" />
      </el-select>

      <el-button type="primary" :loading="loading" @click="loadAll">加载统计</el-button>
      <el-dropdown @command="handleExport">
        <el-button>
          导出
          <el-icon class="el-icon--right"><arrow-down /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="csv">导出 CSV</el-dropdown-item>
            <el-dropdown-item command="dist">导出成绩分布图 PNG</el-dropdown-item>
            <el-dropdown-item command="trend">导出班级趋势图 PNG</el-dropdown-item>
            <el-dropdown-item command="wrong">导出错题榜图 PNG</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <div v-if="selectedExam" class="exam-meta">
      <span>状态：{{ selectedExam.status || '-' }}</span>
      <span>开始：{{ formatDateTime(selectedExam.startTime) }}</span>
      <span>结束：{{ formatDateTime(selectedExam.endTime) }}</span>
    </div>

    <el-empty v-if="!examOptions.length" description="暂无可分析考试" />

    <template v-else>
      <div class="overview-grid">
        <div v-for="item in overviewCards" :key="item.key" class="overview-card">
          <div class="overview-label">{{ item.label }}</div>
          <div class="overview-value">{{ item.value }}<span class="overview-suffix">{{ item.suffix }}</span></div>
        </div>
      </div>

      <div class="charts">
        <div class="chart-wrap">
          <div ref="distRef" class="chart"></div>
        </div>
        <div class="chart-wrap">
          <div ref="trendRef" class="chart"></div>
        </div>
        <div class="chart-wrap chart-wrap-wide">
          <div ref="wrongRef" class="chart"></div>
        </div>
      </div>

      <el-empty v-if="hasLoaded && !hasAnyData" description="当前考试暂无可展示统计数据" />
    </template>
  </el-card>
</template>

<script setup>
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import {
  analyticsOverviewApi,
  classTrendApi,
  scoreDistributionApi,
  teacherExamsApi,
  wrongTopicsApi
} from '../../api'
import { formatDateTime } from '../../utils/datetime'

const topNOptions = [5, 10, 20, 30]
const selectedExamId = ref('')
const examOptions = ref([])
const topN = ref(10)
const loading = ref(false)
const hasLoaded = ref(false)

const overview = ref({
  totalStudents: 0,
  passCount: 0,
  passRate: 0,
  avgScore: 0,
  maxScore: null,
  minScore: null
})
const distData = ref([])
const trendData = ref([])
const wrongData = ref([])

const distRef = ref(null)
const trendRef = ref(null)
const wrongRef = ref(null)
let distChart
let trendChart
let wrongChart

const selectedExam = computed(() =>
  examOptions.value.find((item) => String(item.examId) === String(selectedExamId.value)) || null
)

const hasAnyData = computed(() =>
  distData.value.length > 0 || trendData.value.length > 0 || wrongData.value.length > 0
)

const overviewCards = computed(() => [
  { key: 'totalStudents', label: '参考人数', value: overview.value.totalStudents ?? 0, suffix: '人' },
  { key: 'avgScore', label: '平均分', value: formatNumber(overview.value.avgScore), suffix: '分' },
  { key: 'passRate', label: '及格率', value: formatNumber(overview.value.passRate), suffix: '%' },
  { key: 'passCount', label: '及格人数', value: overview.value.passCount ?? 0, suffix: '人' },
  { key: 'maxScore', label: '最高分', value: overview.value.maxScore ?? '-', suffix: '分' },
  { key: 'minScore', label: '最低分', value: overview.value.minScore ?? '-', suffix: '分' }
])

const examLabel = (exam) => `${exam.examId} - ${exam.name || '未命名考试'}`

const formatNumber = (value) => {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    return '-'
  }
  return Number.isInteger(numeric) ? String(numeric) : numeric.toFixed(2)
}

const resize = () => {
  distChart?.resize()
  trendChart?.resize()
  wrongChart?.resize()
}

const safeList = (value) => (Array.isArray(value) ? value : [])

const initCharts = () => {
  if (!distChart && distRef.value) {
    distChart = echarts.init(distRef.value)
  }
  if (!trendChart && trendRef.value) {
    trendChart = echarts.init(trendRef.value)
  }
  if (!wrongChart && wrongRef.value) {
    wrongChart = echarts.init(wrongRef.value)
  }
}

const loadExamOptions = async () => {
  examOptions.value = safeList(await teacherExamsApi())
  if (!examOptions.value.length) {
    selectedExamId.value = ''
    return
  }
  if (!selectedExamId.value || !examOptions.value.some((item) => String(item.examId) === String(selectedExamId.value))) {
    selectedExamId.value = String(examOptions.value[0].examId)
  }
}

const clearOverview = () => {
  overview.value = {
    totalStudents: 0,
    passCount: 0,
    passRate: 0,
    avgScore: 0,
    maxScore: null,
    minScore: null
  }
}

const renderCharts = () => {
  if (!distChart || !trendChart || !wrongChart) {
    return
  }

  distChart.setOption({
    title: { text: '成绩分布' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: distData.value.map((item) => item.range) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        data: distData.value.map((item) => item.count || 0),
        itemStyle: { color: '#0f766e' },
        barMaxWidth: 42
      }
    ]
  })

  trendChart.setOption({
    title: { text: '班级均分趋势' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: trendData.value.map((item) => item.className || '-') },
    yAxis: { type: 'value', max: 100 },
    series: [
      {
        type: 'line',
        data: trendData.value.map((item) => item.avgScore || 0),
        smooth: true,
        itemStyle: { color: '#ea580c' },
        areaStyle: { color: 'rgba(234, 88, 12, 0.12)' }
      }
    ]
  })

  wrongChart.setOption({
    title: { text: `错题率 Top${topN.value}` },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const row = params?.[0]
        if (!row) {
          return ''
        }
        const source = wrongData.value[row.dataIndex]
        return [
          `题目ID: ${source?.questionId ?? '-'}`,
          `错题率: ${formatNumber(source?.wrongRate)}%`,
          `错误次数: ${source?.wrongCount ?? 0}`,
          `作答总数: ${source?.totalCount ?? 0}`,
          `题干: ${source?.questionContent || '-'}`
        ].join('<br/>')
      }
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%' }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: wrongData.value.map((item) => String(item.questionId))
    },
    series: [
      {
        type: 'bar',
        data: wrongData.value.map((item) => item.wrongRate || 0),
        itemStyle: { color: '#2563eb' },
        barMaxWidth: 28
      }
    ]
  })
}

const loadAll = async () => {
  if (!selectedExamId.value) {
    clearOverview()
    distData.value = []
    trendData.value = []
    wrongData.value = []
    renderCharts()
    return
  }

  loading.value = true
  try {
    const examId = selectedExamId.value
    const [overviewResp, distResp, trendResp, wrongResp] = await Promise.all([
      analyticsOverviewApi(examId),
      scoreDistributionApi(examId),
      classTrendApi(examId),
      wrongTopicsApi(examId, topN.value)
    ])

    overview.value = overviewResp || overview.value
    distData.value = safeList(distResp)
    trendData.value = safeList(trendResp)
    wrongData.value = safeList(wrongResp)
    renderCharts()
    hasLoaded.value = true
  } catch (error) {
    ElMessage.error(error?.message || '加载统计失败')
  } finally {
    loading.value = false
  }
}

const escapeCsv = (value) => {
  const text = value == null ? '' : String(value)
  if (text.includes(',') || text.includes('"') || text.includes('\n')) {
    return `"${text.replaceAll('"', '""')}"`
  }
  return text
}

const buildTimestamp = () => {
  const date = new Date()
  const pad = (val) => String(val).padStart(2, '0')
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
    pad(date.getHours()),
    pad(date.getMinutes()),
    pad(date.getSeconds())
  ].join('')
}

const triggerDownload = (url, fileName) => {
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
}

const exportCsv = () => {
  if (!selectedExamId.value || !hasLoaded.value) {
    ElMessage.warning('请先加载统计数据')
    return
  }

  const lines = []
  lines.push(`考试ID,${escapeCsv(selectedExamId.value)}`)
  lines.push('')
  lines.push('成绩分布')
  lines.push('分数区间,人数')
  distData.value.forEach((item) => {
    lines.push(`${escapeCsv(item.range)},${escapeCsv(item.count ?? 0)}`)
  })
  lines.push('')
  lines.push('班级趋势')
  lines.push('班级ID,班级名称,均分')
  trendData.value.forEach((item) => {
    lines.push(`${escapeCsv(item.classId)},${escapeCsv(item.className)},${escapeCsv(item.avgScore)}`)
  })
  lines.push('')
  lines.push(`错题榜 Top${topN.value}`)
  lines.push('题目ID,题干,错题率(%),错误次数,作答总数')
  wrongData.value.forEach((item) => {
    lines.push([
      escapeCsv(item.questionId),
      escapeCsv(item.questionContent),
      escapeCsv(item.wrongRate),
      escapeCsv(item.wrongCount),
      escapeCsv(item.totalCount)
    ].join(','))
  })

  const content = `\uFEFF${lines.join('\n')}`
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  triggerDownload(url, `analytics_exam_${selectedExamId.value}_${buildTimestamp()}.csv`)
  URL.revokeObjectURL(url)
}

const exportChart = (chart, suffix) => {
  if (!selectedExamId.value || !hasLoaded.value || !chart) {
    ElMessage.warning('请先加载统计数据')
    return
  }
  const url = chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#ffffff' })
  triggerDownload(url, `analytics_exam_${selectedExamId.value}_${suffix}_${buildTimestamp()}.png`)
}

const handleExport = (command) => {
  if (command === 'csv') {
    exportCsv()
    return
  }
  if (command === 'dist') {
    exportChart(distChart, 'score_distribution')
    return
  }
  if (command === 'trend') {
    exportChart(trendChart, 'class_trend')
    return
  }
  if (command === 'wrong') {
    exportChart(wrongChart, 'wrong_topics')
  }
}

watch(topN, () => {
  if (hasLoaded.value && selectedExamId.value) {
    loadAll()
  }
})

watch(selectedExamId, (newValue, oldValue) => {
  if (!newValue || newValue === oldValue) {
    return
  }
  loadAll()
})

onMounted(async () => {
  await loadExamOptions()
  await nextTick()
  initCharts()
  window.addEventListener('resize', resize)
  if (selectedExamId.value) {
    await loadAll()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  distChart?.dispose()
  trendChart?.dispose()
  wrongChart?.dispose()
})
</script>

<style scoped>
.header {
  font-size: 18px;
  font-weight: 700;
}

.analytics-page {
  min-height: 420px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.toolbar-control {
  width: min(420px, 100%);
}

.toolbar-short {
  width: 120px;
}

.exam-option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.exam-option-id {
  color: #606266;
  min-width: 60px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.exam-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 12px;
  color: #4b5563;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.overview-card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 12px;
  background: #ffffff;
}

.overview-label {
  font-size: 13px;
  color: #6b7280;
}

.overview-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.overview-suffix {
  margin-left: 3px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
}

.charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.chart-wrap {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
}

.chart-wrap-wide {
  grid-column: 1 / -1;
}

.chart {
  height: 320px;
}

@media (max-width: 1200px) {
  .overview-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .toolbar-control,
  .toolbar-short {
    width: 100%;
  }

  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .charts {
    grid-template-columns: 1fr;
  }

  .chart-wrap-wide {
    grid-column: auto;
  }
}
</style>
