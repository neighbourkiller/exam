<template>
  <div class="environment-check-page">
    <section class="environment-check-hero">
      <div>
        <p class="environment-check-hero__eyebrow">学生自检</p>
        <h1>考试环境检测</h1>
        <p class="environment-check-hero__meta">{{ summaryText }}</p>
      </div>
      <div class="environment-check-hero__actions">
        <el-tag :type="summaryTagType" effect="light">{{ summaryBadge }}</el-tag>
        <el-button type="primary" size="large" :loading="running" @click="runChecks">
          {{ finished ? '重新检测' : '开始检测' }}
        </el-button>
      </div>
    </section>

    <section class="environment-check-content">
      <EnvironmentCheckPanel
        :results="results"
        :running="running"
        :finished="finished"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { releaseMediaStreams, runPreExamCheck } from '../../utils/preExamCheck'
import EnvironmentCheckPanel from './EnvironmentCheckPanel.vue'

const running = ref(false)
const finished = ref(false)
const results = ref([])
let runSeq = 0

const blockingResults = computed(() =>
  results.value.filter((item) => item.blocking && item.status === 'failed')
)
const warningResults = computed(() =>
  results.value.filter((item) => item.status === 'warning')
)
const summaryBadge = computed(() => {
  if (running.value) return '检测中'
  if (!finished.value) return '待检测'
  if (blockingResults.value.length) return '未通过'
  if (warningResults.value.length) return '有提醒'
  return '已通过'
})
const summaryTagType = computed(() => {
  if (running.value) return 'warning'
  if (!finished.value) return 'info'
  if (blockingResults.value.length) return 'danger'
  if (warningResults.value.length) return 'warning'
  return 'success'
})
const summaryText = computed(() => {
  if (running.value) return '正在检测浏览器、网络、显示器、摄像头和麦克风。'
  if (!finished.value) return '开始检测前请关闭副屏，并准备授权摄像头与麦克风。'
  if (blockingResults.value.length) return `有 ${blockingResults.value.length} 项未通过，请处理后重新检测。`
  if (warningResults.value.length) return `检测完成，有 ${warningResults.value.length} 项提醒。`
  return '检测完成，当前环境符合考试要求。'
})

const runChecks = async () => {
  if (running.value) {
    return
  }
  const currentSeq = ++runSeq
  running.value = true
  finished.value = false
  results.value = []
  try {
    const nextResults = await runPreExamCheck()
    if (currentSeq !== runSeq) {
      return
    }
    results.value = nextResults
    finished.value = true
  } catch (error) {
    if (currentSeq !== runSeq) {
      return
    }
    results.value = [{
      key: 'flow',
      label: '检测流程',
      status: 'failed',
      detail: error?.message || '环境检测失败，请刷新页面后重试。',
      blocking: true
    }]
    finished.value = true
    ElMessage.error('环境检测失败')
  } finally {
    if (currentSeq === runSeq) {
      running.value = false
    }
    releaseMediaStreams()
  }
}

onBeforeUnmount(() => {
  runSeq += 1
  releaseMediaStreams()
})
</script>

<style scoped>
.environment-check-page {
  display: grid;
  gap: 22px;
}

.environment-check-hero,
.environment-check-content {
  border: 1px solid var(--student-line, #e7dfd3);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-soft);
}

.environment-check-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 34px 36px;
}

.environment-check-hero__eyebrow {
  margin: 0 0 8px;
  color: var(--student-accent, #d97757);
  font-size: 13px;
  font-weight: 700;
}

.environment-check-hero h1 {
  margin: 0;
  color: var(--text-main);
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: clamp(34px, 4vw, 52px);
  font-weight: 500;
  line-height: 1.08;
}

.environment-check-hero__meta {
  max-width: 560px;
  margin: 14px 0 0;
  color: var(--text-muted);
  font-size: 15px;
  line-height: 1.7;
}

.environment-check-hero__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.environment-check-content {
  padding: 26px;
}

.environment-check-page :deep(.el-button--primary) {
  --el-button-bg-color: var(--student-accent, #d97757);
  --el-button-border-color: var(--student-accent, #d97757);
  --el-button-hover-bg-color: var(--student-accent-dark, #a9472d);
  --el-button-hover-border-color: var(--student-accent-dark, #a9472d);
}

@media (max-width: 768px) {
  .environment-check-hero {
    align-items: stretch;
    flex-direction: column;
    padding: 22px;
  }

  .environment-check-hero__actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
