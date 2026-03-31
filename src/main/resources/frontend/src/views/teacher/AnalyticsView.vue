<template>
  <div class="analytics-dashboard" v-loading="loading">
    <!-- Header Section -->
    <header class="dashboard-header">
      <div class="header-left">
        <p class="dashboard-eyebrow">Analytics Dashboard</p>
        <h1 class="dashboard-title">考试数据分析看板</h1>
        <p class="dashboard-subtitle" v-if="selectedExam">
          {{ selectedExam.name }} <span class="exam-id">#{{ selectedExam.examId }}</span>
        </p>
      </div>
      <div class="header-actions">
        <el-select v-model="selectedExamId" filterable placeholder="选择分析考试" class="exam-selector">
          <el-option v-for="exam in examOptions" :key="exam.examId" :label="exam.name" :value="String(exam.examId)">
            <span class="id-tag">ID:{{ exam.examId }}</span> {{ exam.name }}
          </el-option>
        </el-select>
        <el-button-group class="action-group">
          <el-button @click="loadAll" :icon="Refresh">刷新</el-button>
          <el-dropdown @command="handleExport">
            <el-button type="primary">
              数据导出<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="csv">CSV 原始数据</el-dropdown-item>
                <el-dropdown-item divided command="dist">成绩分布图</el-dropdown-item>
                <el-dropdown-item command="trend">班级趋势图</el-dropdown-item>
                <el-dropdown-item command="wrong">错题率图表</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-button-group>
      </div>
    </header>

    <div v-if="!examOptions.length" class="empty-state">
      <el-empty description="暂无已结束或进行中的考试数据" />
    </div>

    <main v-else class="dashboard-content">
      <!-- Metric Cards Grid -->
      <section class="metrics-grid">
        <div v-for="item in overviewCards" :key="item.key" class="metric-card">
          <div class="metric-icon" :style="{ color: item.color, backgroundColor: item.bgColor }">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div class="metric-info">
            <div class="metric-label">{{ item.label }}</div>
            <div class="metric-value">
              {{ item.value }}<span class="metric-suffix">{{ item.suffix }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Charts Section -->
      <section class="charts-layout">
        <div class="chart-container distribution-chart">
          <div class="chart-header">
            <h3>成绩段分布</h3>
            <span class="chart-tip">展示不同分值区间的学生人数</span>
          </div>
          <div ref="distRef" class="echart-view"></div>
        </div>

        <div class="chart-container trend-chart">
          <div class="chart-header">
            <h3>班级均分趋势</h3>
            <span class="chart-tip">不同教学班级的横向对比</span>
          </div>
          <div ref="trendRef" class="echart-view"></div>
        </div>

        <div class="chart-container wrong-rate-chart full-width">
          <div class="chart-header">
            <h3>知识点错题榜</h3>
            <div class="chart-header-actions">
              <span class="chart-tip">按错题率从高到低排序 (Top {{ topN }})</span>
              <el-radio-group v-model="topN" size="small" style="margin-left: 16px">
                <el-radio-button :label="5">5</el-radio-button>
                <el-radio-button :label="10">10</el-radio-button>
                <el-radio-button :label="20">20</el-radio-button>
              </el-radio-group>
            </div>
          </div>
          <div ref="wrongRef" class="echart-view wide"></div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, markRaw } from 'vue'
import * as echarts from 'echarts'
import { ArrowDown, Refresh, User, Trophy, DataLine, Histogram, CircleCheck, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { analyticsOverviewApi, classTrendApi, scoreDistributionApi, teacherExamsApi, wrongTopicsApi } from '../../api'

const selectedExamId = ref('')
const examOptions = ref([])
const topN = ref(10)
const loading = ref(false)

const overview = ref({ totalStudents: 0, passCount: 0, passRate: 0, avgScore: 0, maxScore: null, minScore: null })
const distData = ref([])
const trendData = ref([])
const wrongData = ref([])

const distRef = ref(null)
const trendRef = ref(null)
const wrongRef = ref(null)
let distChart, trendChart, wrongChart

const overviewCards = computed(() => [
  { key: 'total', label: '参考人数', value: overview.value.totalStudents ?? 0, suffix: '人', icon: markRaw(User), color: '#3b82f6', bgColor: '#eff6ff' },
  { key: 'avg', label: '平均分数', value: formatNum(overview.value.avgScore), suffix: '分', icon: markRaw(DataLine), color: '#8b5cf6', bgColor: '#f5f3ff' },
  { key: 'passRate', label: '及格率', value: formatNum(overview.value.passRate), suffix: '%', icon: markRaw(CircleCheck), color: '#10b981', bgColor: '#ecfdf5' },
  { key: 'passCount', label: '及格人数', value: overview.value.passCount ?? 0, suffix: '人', icon: markRaw(Trophy), color: '#f59e0b', bgColor: '#fffbeb' },
  { key: 'max', label: '最高分', value: overview.value.maxScore ?? '-', suffix: '分', icon: markRaw(Histogram), color: '#ef4444', bgColor: '#fef2f2' },
  { key: 'min', label: '最低分', value: overview.value.minScore ?? '-', suffix: '分', icon: markRaw(Warning), color: '#64748b', bgColor: '#f8fafc' }
])

const selectedExam = computed(() => examOptions.value.find(e => String(e.examId) === String(selectedExamId.value)))

const formatNum = (v) => v != null ? (Number.isInteger(v) ? v : v.toFixed(1)) : '0'

const renderCharts = () => {
  if (!distChart || !trendChart || !wrongChart) return

  distChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: distData.value.map(d => d.range), axisTick: { alignWithLabel: true } },
    yAxis: { type: 'value', splitLine: { lineStyle: { type: 'dashed' } } },
    series: [{
      type: 'bar',
      data: distData.value.map(d => d.count),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#3b82f6' },
          { offset: 1, color: '#1d4ed8' }
        ]),
        borderRadius: [4, 4, 0, 0]
      },
      barWidth: '40%',
      showBackground: true,
      backgroundStyle: { color: 'rgba(180, 180, 180, 0.1)', borderRadius: [4, 4, 0, 0] }
    }]
  })

  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: trendData.value.map(d => d.className), boundaryGap: false },
    yAxis: { type: 'value', max: 100 },
    series: [{
      type: 'line',
      data: trendData.value.map(d => d.avgScore),
      smooth: true,
      itemStyle: { color: '#8b5cf6' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(139, 92, 246, 0.4)' },
          { offset: 1, color: 'rgba(139, 92, 246, 0)' }
        ])
      },
      symbolSize: 8,
      lineStyle: { width: 3 }
    }]
  })

  wrongChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (p) => {
        const d = wrongData.value[p[0].dataIndex];
        return `<div style="max-width:300px; white-space:pre-wrap;">题目: ${d.questionContent}<br/>错题率: <b style="color:#ef4444">${d.wrongRate}%</b><br/>错误: ${d.wrongCount} / 总计: ${d.totalCount}</div>`
      }
    },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    yAxis: { type: 'category', data: wrongData.value.map(d => `Q-${d.questionId}`), inverse: true },
    series: [{
      type: 'bar',
      data: wrongData.value.map(d => d.wrongRate),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#f59e0b' },
          { offset: 1, color: '#d97706' }
        ]),
        borderRadius: [0, 4, 4, 0]
      },
      label: { show: true, position: 'right', formatter: '{c}%', color: '#475569' }
    }]
  })
}

const loadAll = async () => {
  if (!selectedExamId.value) return
  loading.value = true
  try {
    const id = selectedExamId.value
    const [ov, dist, tr, wr] = await Promise.all([
      analyticsOverviewApi(id),
      scoreDistributionApi(id),
      classTrendApi(id),
      wrongTopicsApi(id, topN.value)
    ])
    overview.value = ov || { totalStudents: 0, passCount: 0, passRate: 0, avgScore: 0, maxScore: null, minScore: null }
    distData.value = dist || []
    trendData.value = tr || []
    wrongData.value = wr || []
    nextTick(renderCharts)
  } catch (e) {
    ElMessage.error('加载分析数据失败')
  } finally {
    loading.value = false
  }
}

const handleExport = (command) => {
  ElMessage.info(`正在导出: ${command} (功能开发中)`)
}

const resizeCharts = () => {
  distChart?.resize()
  trendChart?.resize()
  wrongChart?.resize()
}

watch(selectedExamId, loadAll)
watch(topN, loadAll)

onMounted(async () => {
  examOptions.value = await teacherExamsApi()
  if (examOptions.value.length) {
    if (!selectedExamId.value) {
      selectedExamId.value = String(examOptions.value[0].examId)
    }
    distChart = echarts.init(distRef.value)
    trendChart = echarts.init(trendRef.value)
    wrongChart = echarts.init(wrongRef.value)
    window.addEventListener('resize', resizeCharts)
    await loadAll()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  distChart?.dispose()
  trendChart?.dispose()
  wrongChart?.dispose()
})
</script>

<style scoped>
.analytics-dashboard {
  padding: 24px;
  background-color: #f8fafc;
  min-height: 100%;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
  padding: 24px 28px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
}

.dashboard-eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #64748b;
  font-weight: 600;
}

.dashboard-title {
  margin: 0;
  font-size: 28px;
  color: #0f172a;
  font-weight: 800;
}

.dashboard-subtitle {
  margin: 8px 0 0;
  color: #475569;
  font-size: 15px;
}

.exam-id {
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: #64748b;
  margin-left: 4px;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.exam-selector {
  width: 320px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.metric-card {
  display: flex;
  align-items: center;
  padding: 24px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.metric-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
}

.metric-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  margin-right: 20px;
}

.metric-label {
  font-size: 14px;
  color: #64748b;
  font-weight: 600;
}

.metric-value {
  font-size: 26px;
  font-weight: 800;
  color: #0f172a;
  margin-top: 4px;
}

.metric-suffix {
  font-size: 14px;
  margin-left: 4px;
  color: #94a3b8;
  font-weight: 500;
}

.charts-layout {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.chart-container {
  background: #fff;
  padding: 28px;
  border-radius: 20px;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
}

.full-width {
  grid-column: span 2;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.chart-header h3 {
  margin: 0;
  font-size: 18px;
  color: #1e293b;
  font-weight: 700;
}

.chart-tip {
  font-size: 13px;
  color: #94a3b8;
  margin-top: 4px;
  display: block;
}

.echart-view {
  height: 360px;
  width: 100%;
}

.id-tag {
  color: #94a3b8;
  font-size: 11px;
  margin-right: 8px;
  font-family: monospace;
}

@media (max-width: 1280px) {
  .charts-layout {
    grid-template-columns: 1fr;
  }
  .full-width {
    grid-column: span 1;
  }
}

@media (max-width: 768px) {
  .analytics-dashboard {
    padding: 16px;
  }
  .dashboard-header {
    flex-direction: column;
    gap: 20px;
  }
  .header-actions {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }
  .exam-selector {
    width: 100%;
  }
}
</style>
