<template>
  <el-card class="page-card">
    <template #header><div class="header">数据看板 (ECharts)</div></template>
    <div class="toolbar">
      <el-input v-model.number="examId" placeholder="输入考试ID" style="width: 200px" />
      <el-button type="primary" @click="loadAll">加载统计</el-button>
    </div>

    <div class="charts">
      <div ref="distRef" class="chart"></div>
      <div ref="trendRef" class="chart"></div>
      <div ref="wrongRef" class="chart"></div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { classTrendApi, scoreDistributionApi, wrongTopicsApi } from '../../api'

const examId = ref(null)
const distRef = ref(null)
const trendRef = ref(null)
const wrongRef = ref(null)
let distChart
let trendChart
let wrongChart

const resize = () => {
  distChart?.resize()
  trendChart?.resize()
  wrongChart?.resize()
}

const loadAll = async () => {
  if (!examId.value) return
  const dist = await scoreDistributionApi(examId.value)
  const trend = await classTrendApi(examId.value)
  const wrong = await wrongTopicsApi(examId.value)

  distChart.setOption({
    title: { text: '成绩分布柱状图' },
    tooltip: {},
    xAxis: { type: 'category', data: dist.map((i) => i.range) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: dist.map((i) => i.count), itemStyle: { color: '#0f766e' } }]
  })

  trendChart.setOption({
    title: { text: '班级均分趋势' },
    tooltip: {},
    xAxis: { type: 'category', data: trend.map((i) => i.className) },
    yAxis: { type: 'value' },
    series: [{ type: 'line', data: trend.map((i) => i.avgScore), smooth: true, itemStyle: { color: '#ea580c' } }]
  })

  wrongChart.setOption({
    title: { text: '错题率Top10' },
    tooltip: {},
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: wrong.map((i) => String(i.questionId)).reverse() },
    series: [{ type: 'bar', data: wrong.map((i) => i.wrongRate).reverse(), itemStyle: { color: '#2563eb' } }]
  })
}

onMounted(() => {
  distChart = echarts.init(distRef.value)
  trendChart = echarts.init(trendRef.value)
  wrongChart = echarts.init(wrongRef.value)
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  distChart?.dispose()
  trendChart?.dispose()
  wrongChart?.dispose()
})
</script>

<style scoped>
.header { font-size: 18px; font-weight: 700; }
.toolbar { display: flex; gap: 10px; margin-bottom: 12px; }
.charts { display: grid; grid-template-columns: 1fr; gap: 12px; }
.chart { height: 320px; border: 1px solid #e5e7eb; border-radius: 10px; }
</style>
