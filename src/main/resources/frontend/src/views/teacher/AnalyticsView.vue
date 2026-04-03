<template>
  <div class="analytics-dashboard" v-loading="loading">
    <!-- Header Section -->
    <header class="dashboard-header">
      <div class="header-left">
        <div class="title-wrapper">
          <div class="title-icon">
            <el-icon><DataLine /></el-icon>
          </div>
          <div>
            <h1 class="dashboard-title">考试数据分析看板</h1>
            <p class="dashboard-subtitle">
              <span v-if="selectedExam" class="exam-name">
                {{ selectedExam.name }} 
                <span class="exam-id">#{{ selectedExam.examId }}</span>
              </span>
              <span v-else>请选择一场考试以查看多维数据分析报告</span>
            </p>
          </div>
        </div>
      </div>
      <div class="header-actions">
        <el-select v-model="selectedExamId" filterable placeholder="检索或选择分析考试" class="exam-selector" size="large">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <el-option v-for="exam in examOptions" :key="exam.examId" :label="exam.name" :value="String(exam.examId)">
            <span class="id-tag">ID:{{ exam.examId }}</span> {{ exam.name }}
          </el-option>
        </el-select>
        <el-button-group class="action-group">
          <el-button @click="loadAll" :icon="Refresh" size="large" class="refresh-btn">刷新</el-button>
          <el-dropdown @command="handleExport">
            <el-button type="primary" size="large" class="export-btn">
              数据导出<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu class="modern-dropdown">
                <el-dropdown-item command="csv">CSV 原始数据</el-dropdown-item>
                <el-dropdown-item divided command="dist">成绩分布报告</el-dropdown-item>
                <el-dropdown-item command="trend">班级趋势报告</el-dropdown-item>
                <el-dropdown-item command="wrong">错题统计报告</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-button-group>
      </div>
    </header>

    <div v-if="!examOptions.length" class="empty-state">
      <el-empty description="暂无已结束或进行中的考试数据" image-size="200" />
    </div>

    <main v-else class="dashboard-content">
      <!-- Metric Cards Grid -->
      <section class="metrics-grid">
        <div v-for="item in overviewCards" :key="item.key" class="metric-card">
          <div class="metric-header">
            <div class="metric-icon" :style="{ color: item.color, backgroundColor: item.bgColor }">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <span class="metric-label">{{ item.label }}</span>
          </div>
          <div class="metric-body">
            <div class="metric-value">
              {{ item.value }}<span class="metric-suffix">{{ item.suffix }}</span>
            </div>
            <!-- Optional sparkline or trend indicator could go here -->
          </div>
        </div>
      </section>

      <!-- Charts Section -->
      <section class="charts-layout">
        <div class="chart-container distribution-chart">
          <div class="chart-header">
            <div class="chart-title-group">
              <h3>成绩段分布</h3>
              <span class="chart-tip">展示不同分值区间的学生人数比例</span>
            </div>
          </div>
          <div ref="distRef" class="echart-view"></div>
        </div>

        <div class="chart-container trend-chart">
          <div class="chart-header">
            <div class="chart-title-group">
              <h3>班级均分趋势</h3>
              <span class="chart-tip">不同教学班级的横向对比分析</span>
            </div>
          </div>
          <div ref="trendRef" class="echart-view"></div>
        </div>

        <div class="chart-container wrong-rate-chart full-width">
          <div class="chart-header">
            <div class="chart-title-group">
              <h3>高频错题榜单</h3>
              <span class="chart-tip">按错题率从高到低排序，辅助靶向教学</span>
            </div>
            <div class="chart-header-actions">
              <span class="filter-label">显示数量:</span>
              <el-radio-group v-model="topN" size="small" class="modern-radio">
                <el-radio-button :label="5">Top 5</el-radio-button>
                <el-radio-button :label="10">Top 10</el-radio-button>
                <el-radio-button :label="20">Top 20</el-radio-button>
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
import { ArrowDown, Refresh, User, Trophy, DataLine, Histogram, CircleCheck, Warning, Search } from '@element-plus/icons-vue'
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
  { key: 'total', label: '参考总人数', value: overview.value.totalStudents ?? 0, suffix: '人', icon: markRaw(User), color: '#3b82f6', bgColor: 'rgba(59, 130, 246, 0.1)' },
  { key: 'avg', label: '全级平均分', value: formatNum(overview.value.avgScore), suffix: '分', icon: markRaw(DataLine), color: '#8b5cf6', bgColor: 'rgba(139, 92, 246, 0.1)' },
  { key: 'passRate', label: '整体及格率', value: formatNum(overview.value.passRate), suffix: '%', icon: markRaw(CircleCheck), color: '#10b981', bgColor: 'rgba(16, 185, 129, 0.1)' },
  { key: 'passCount', label: '达标及格数', value: overview.value.passCount ?? 0, suffix: '人', icon: markRaw(Trophy), color: '#f59e0b', bgColor: 'rgba(245, 158, 11, 0.1)' },
  { key: 'max', label: '最高得分', value: overview.value.maxScore ?? '-', suffix: '分', icon: markRaw(Histogram), color: '#ef4444', bgColor: 'rgba(239, 68, 68, 0.1)' },
  { key: 'min', label: '最低得分', value: overview.value.minScore ?? '-', suffix: '分', icon: markRaw(Warning), color: '#64748b', bgColor: 'rgba(100, 116, 139, 0.1)' }
])

const selectedExam = computed(() => examOptions.value.find(e => String(e.examId) === String(selectedExamId.value)))

const formatNum = (v) => v != null ? (Number.isInteger(v) ? v : v.toFixed(1)) : '0'

// 现代化的通用 ECharts 提示框配置
const modernTooltip = {
  backgroundColor: 'rgba(255, 255, 255, 0.95)',
  borderColor: 'rgba(226, 232, 240, 1)',
  borderWidth: 1,
  padding: [12, 16],
  textStyle: { color: '#1e293b', fontSize: 13 },
  extraCssText: 'box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05); border-radius: 8px; backdrop-filter: blur(8px);'
}

const renderCharts = () => {
  if (!distChart || !trendChart || !wrongChart) return

  distChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'none' }, ...modernTooltip },
    grid: { left: '2%', right: '2%', bottom: '2%', top: '15%', containLabel: true },
    xAxis: { 
      type: 'category', 
      data: distData.value.map(d => d.range), 
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#64748b', margin: 12 }
    },
    yAxis: { 
      type: 'value', 
      splitLine: { lineStyle: { type: 'dashed', color: '#f1f5f9' } },
      axisLabel: { color: '#94a3b8' }
    },
    series: [{
      name: '人数',
      type: 'bar',
      data: distData.value.map(d => d.count),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#60a5fa' },
          { offset: 1, color: '#2563eb' }
        ]),
        borderRadius: [6, 6, 0, 0]
      },
      barWidth: '32%',
      showBackground: true,
      backgroundStyle: { color: '#f8fafc', borderRadius: [6, 6, 0, 0] }
    }]
  })

  trendChart.setOption({
    tooltip: { trigger: 'axis', ...modernTooltip },
    grid: { left: '2%', right: '4%', bottom: '2%', top: '15%', containLabel: true },
    xAxis: { 
      type: 'category', 
      data: trendData.value.map(d => d.className), 
      boundaryGap: false,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#64748b', margin: 12 }
    },
    yAxis: { 
      type: 'value', 
      max: 100,
      splitLine: { lineStyle: { type: 'dashed', color: '#f1f5f9' } },
      axisLabel: { color: '#94a3b8' }
    },
    series: [{
      name: '平均分',
      type: 'line',
      data: trendData.value.map(d => d.avgScore),
      smooth: 0.4,
      itemStyle: { color: '#8b5cf6', borderWidth: 2, borderColor: '#fff' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(139, 92, 246, 0.3)' },
          { offset: 1, color: 'rgba(139, 92, 246, 0.02)' }
        ])
      },
      symbolSize: 10,
      showSymbol: false,
      lineStyle: { width: 3, shadowColor: 'rgba(139, 92, 246, 0.2)', shadowBlur: 10 }
    }]
  })

  wrongChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'none' },
      ...modernTooltip,
      formatter: (p) => {
        const d = wrongData.value[p[0].dataIndex];
        return `
          <div style="max-width:320px; white-space:normal; line-height: 1.6;">
            <div style="color:#64748b; font-size:12px; margin-bottom:8px;">题目 ID: ${d.questionId}</div>
            <div style="font-weight:600; color:#1e293b; margin-bottom:12px; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden;">${d.questionContent}</div>
            <div style="display:flex; justify-content:space-between; border-top:1px solid #f1f5f9; padding-top:8px;">
              <span style="color:#64748b;">错题率</span>
              <b style="color:#ef4444; font-size:16px;">${d.wrongRate}%</b>
            </div>
            <div style="display:flex; justify-content:space-between; margin-top:4px;">
              <span style="color:#64748b;">错误人次 / 总作答</span>
              <span style="color:#334155; font-weight:500;">${d.wrongCount} / ${d.totalCount}</span>
            </div>
          </div>
        `
      }
    },
    grid: { left: '2%', right: '6%', bottom: '2%', top: '5%', containLabel: true },
    xAxis: { 
      type: 'value', 
      max: 100, 
      splitLine: { lineStyle: { type: 'dashed', color: '#f1f5f9' } },
      axisLabel: { color: '#94a3b8', formatter: '{value}%' }
    },
    yAxis: { 
      type: 'category', 
      data: wrongData.value.map(d => `Q-${d.questionId}`), 
      inverse: true,
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '#64748b', fontWeight: 500, margin: 16 }
    },
    series: [{
      name: '错题率',
      type: 'bar',
      data: wrongData.value.map(d => d.wrongRate),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
          { offset: 0, color: '#f43f5e' },
          { offset: 1, color: '#fb923c' }
        ]),
        borderRadius: [0, 4, 4, 0]
      },
      barWidth: '45%',
      showBackground: true,
      backgroundStyle: { color: '#f8fafc', borderRadius: [0, 4, 4, 0] },
      label: { 
        show: true, 
        position: 'right', 
        formatter: '{c}%', 
        color: '#ef4444',
        fontWeight: 600,
        distance: 10
      }
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
  padding: 32px;
  background-color: #f1f5f9;
  min-height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

/* Header Styles */
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  padding: 28px 32px;
  background: #ffffff;
  border-radius: 20px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.03);
}

.title-wrapper {
  display: flex;
  align-items: center;
  gap: 20px;
}

.title-icon {
  width: 54px;
  height: 54px;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  color: #2563eb;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  box-shadow: inset 0 2px 4px rgba(255, 255, 255, 0.8), 0 4px 8px rgba(37, 99, 235, 0.1);
}

.dashboard-title {
  margin: 0 0 6px;
  font-size: 24px;
  color: #0f172a;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.dashboard-subtitle {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.exam-name {
  font-weight: 600;
  color: #334155;
}

.exam-id {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: #64748b;
  margin-left: 6px;
  border: 1px solid #e2e8f0;
}

.header-actions {
  display: flex;
  gap: 16px;
  align-items: center;
}

.exam-selector {
  width: 340px;
}

:deep(.exam-selector .el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background-color: #f8fafc;
}

:deep(.exam-selector .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #bfdbfe inset, 0 0 0 1px #3b82f6 inset;
  background-color: #ffffff;
}

.refresh-btn, .export-btn {
  border-radius: 10px;
  font-weight: 500;
}

.action-group .el-button {
  height: 40px;
}

/* Empty State */
.empty-state {
  background: #ffffff;
  border-radius: 20px;
  padding: 80px 0;
  border: 1px dashed #e2e8f0;
}

/* Metric Cards */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.metric-card {
  padding: 24px;
  background: #ffffff;
  border-radius: 20px;
  border: 1px solid rgba(226, 232, 240, 0.6);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.02), 0 2px 4px -1px rgba(0, 0, 0, 0.02);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metric-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.01);
  border-color: rgba(226, 232, 240, 1);
}

.metric-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.metric-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.metric-label {
  font-size: 14px;
  color: #64748b;
  font-weight: 500;
}

.metric-value {
  font-size: 32px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1;
  font-feature-settings: "tnum";
  font-variant-numeric: tabular-nums;
}

.metric-suffix {
  font-size: 15px;
  margin-left: 6px;
  color: #94a3b8;
  font-weight: 500;
}

/* Charts Area */
.charts-layout {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.chart-container {
  background: #ffffff;
  padding: 28px 32px;
  border-radius: 24px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.03);
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

.chart-title-group h3 {
  margin: 0 0 4px;
  font-size: 18px;
  color: #1e293b;
  font-weight: 700;
}

.chart-tip {
  font-size: 13px;
  color: #64748b;
}

.chart-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.echart-view {
  height: 380px;
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
    align-items: flex-start;
    padding: 20px;
    gap: 24px;
  }
  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }
  .exam-selector {
    width: 100%;
  }
  .chart-container {
    padding: 20px;
  }
}
</style>
