<template>
  <div class="result-container">
    <el-card class="page-card glass-card hover-lift" style="border: none;">
      <template #header>
        <div class="header">
          <span class="header-icon">🏁</span>
          <span>交卷结果</span>
        </div>
      </template>

      <div class="result-summary">
        <div class="score-circle" :class="{ 'is-passed': result.passFlag, 'is-failed': result.passFlag === false }">
          <span class="score-label">总分</span>
          <span class="score-value">{{ result.totalScore != null ? result.totalScore : '--' }}</span>
        </div>
      </div>

      <el-descriptions :column="1" border class="result-desc" :label-style="{ background: 'rgba(13, 148, 136, 0.05)', color: 'var(--brand)', fontWeight: '600', width: '120px' }">
        <el-descriptions-item label="提交ID">{{ result.submissionId || '--' }}</el-descriptions-item>
        <el-descriptions-item label="客观题得分">{{ result.objectiveScore != null ? result.objectiveScore : '--' }}</el-descriptions-item>
        <el-descriptions-item label="主观题得分">{{ result.subjectiveScore != null ? result.subjectiveScore : '--' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="result.status === 'GRADED' ? 'success' : 'warning'" round effect="light">{{ result.status || '--' }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div class="action-footer">
        <el-button type="primary" round size="large" @click="goList">返回我的考试</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const result = computed(() => {
  try {
    return JSON.parse(route.query.result || '{}')
  } catch {
    return {}
  }
})

const goList = () => router.push('/student/exams')
</script>

<style scoped>
.result-container {
  padding: 20px;
  max-width: 600px;
  margin: 0 auto;
}

.header {
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--brand);
}

.result-summary {
  display: flex;
  justify-content: center;
  margin-bottom: 30px;
  margin-top: 10px;
}

.score-circle {
  width: 140px;
  height: 140px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: white;
  border: 6px solid #e2e8f0;
  box-shadow: 0 8px 24px rgba(0,0,0,0.06);
}

.score-circle.is-passed {
  border-color: #10b981;
  color: #10b981;
  box-shadow: 0 8px 24px rgba(16, 185, 129, 0.15);
}

.score-circle.is-failed {
  border-color: #ef4444;
  color: #ef4444;
  box-shadow: 0 8px 24px rgba(239, 68, 68, 0.15);
}

.score-label {
  font-size: 14px;
  color: var(--text-muted);
}

.score-value {
  font-size: 42px;
  font-weight: 800;
  line-height: 1.1;
  color: inherit;
}

.action-footer {
  margin-top: 30px;
  display: flex;
  justify-content: center;
}

:deep(.el-descriptions__body) {
  background: transparent !important;
}
:deep(.el-descriptions__cell) {
  background: rgba(255,255,255,0.4) !important;
}
</style>
