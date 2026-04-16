<template>
  <div class="results-page">
    <section class="results-hero glass-card">
      <div class="hero-content">
        <h1 class="results-title">
          <span class="title-icon">📊</span> 考试结果中心
        </h1>
        <p class="results-subtitle">查看全部考试与成绩状态，包含待阅卷与未交卷记录。</p>
      </div>
      <el-button type="primary" round @click="loadResults" class="refresh-btn">刷新数据</el-button>
    </section>

    <section class="metrics-row">
      <article class="metric-item glass-card hover-lift" style="animation-delay: 0.1s">
        <div class="metric-label">总考试数</div>
        <div class="metric-value text-brand">{{ metrics.totalCount }}</div>
      </article>
      <article class="metric-item glass-card hover-lift" style="animation-delay: 0.2s">
        <div class="metric-label">已交卷</div>
        <div class="metric-value text-blue">{{ metrics.submittedCount }}</div>
      </article>
      <article class="metric-item glass-card hover-lift" style="animation-delay: 0.3s">
        <div class="metric-label">已出分</div>
        <div class="metric-value text-green">{{ metrics.gradedCount }}</div>
      </article>
      <article class="metric-item glass-card hover-lift" style="animation-delay: 0.4s">
        <div class="metric-label">已通过</div>
        <div class="metric-value text-orange">{{ metrics.passCount }}</div>
      </article>
    </section>

    <section class="table-section glass-card">
      <el-table :data="rows" class="custom-table" style="width: 100%"
        :header-cell-style="{ background: 'transparent', color: 'var(--brand)', fontWeight: '600', borderBottom: '1px solid rgba(13, 148, 136, 0.2)' }"
        :row-style="{ background: 'transparent' }">
        <el-table-column label="课程" width="170">
          <template #default="{ row }"><span class="fw-semibold">{{ row.subjectName || '--' }}</span></template>
        </el-table-column>
        <el-table-column prop="name" label="考试名称" min-width="180" />
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }"><span class="text-muted">{{ formatDateTime(row.startTime) }}</span></template>
        </el-table-column>
        <el-table-column label="结束时间" width="160">
          <template #default="{ row }"><span class="text-muted">{{ formatDateTime(row.endTime) }}</span></template>
        </el-table-column>
        <el-table-column label="考试状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="examStatusTagType(row.examStatus)" round effect="light">{{ examStatusText(row.examStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="submissionStatusTagType(row)" round effect="light">{{ submissionStatusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="主客观分" width="140">
          <template #default="{ row }">
            <div class="score-split">
              <span>客: {{ formatScore(row.objectiveScore) }}</span>
              <span class="divider">|</span>
              <span>主: {{ formatScore(row.subjectiveScore) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="总分" width="90">
          <template #default="{ row }">
            <strong class="total-score" :class="{ 'text-green': row.passFlag, 'text-red': row.passFlag === false }">
              {{ formatScore(row.totalScore) }}
            </strong>
          </template>
        </el-table-column>
        <el-table-column label="是否通过" width="90">
          <template #default="{ row }">
            <span :class="{ 'pass-text': row.passFlag, 'fail-text': row.passFlag === false }">
              {{ passText(row.passFlag) }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { studentExamResultsApi } from '../../api'
import { formatDateTime } from '../../utils/datetime'

const rows = ref([])

const metrics = computed(() => {
  const list = rows.value || []
  return {
    totalCount: list.length,
    submittedCount: list.filter(item => item.submitted).length,
    gradedCount: list.filter(item => item.submissionStatus === 'GRADED').length,
    passCount: list.filter(item => item.passFlag === true).length
  }
})

const examStatusTextMap = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ONGOING: '进行中',
  FINISHED: '已结束',
  TERMINATED: '已终止'
}

const examStatusText = (status) => examStatusTextMap[status] || status || '--'

const examStatusTagType = (status) => {
  if (status === 'ONGOING') return 'success'
  if (status === 'PUBLISHED') return 'warning'
  if (status === 'TERMINATED') return 'danger'
  return 'info'
}

const submissionStatusText = (row) => {
  if (!row || !row.submissionId) return '未交卷'
  if (row.submissionStatus === 'IN_PROGRESS') return '作答中'
  if (row.submissionStatus === 'PROCESSING') return '系统判分中'
  if (row.submissionStatus === 'SUBMITTED') return '待阅卷'
  if (row.submissionStatus === 'GRADED') return '已出分'
  return row.submissionStatus || '--'
}

const submissionStatusTagType = (row) => {
  const text = submissionStatusText(row)
  if (text === '已出分') return 'success'
  if (text === '系统判分中') return 'warning'
  if (text === '待阅卷') return 'warning'
  if (text === '作答中') return 'danger'
  return 'info'
}

const formatScore = (score) => (score == null ? '--' : score)

const passText = (passFlag) => {
  if (passFlag == null) return '--'
  return passFlag ? '通过' : '未通过'
}

const loadResults = async () => {
  rows.value = await studentExamResultsApi()
}

onMounted(loadResults)
</script>

<style scoped>
.results-page {
  display: grid;
  gap: 20px;
  padding: 10px;
}

.results-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 32px;
  background: linear-gradient(135deg, rgba(204, 251, 241, 0.7) 0%, rgba(255, 255, 255, 0.4) 100%);
  border-left: 4px solid var(--brand);
}

.hero-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.results-title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--brand);
  display: flex;
  align-items: center;
  gap: 10px;
}

.results-subtitle {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}

.refresh-btn {
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.2);
}

.metrics-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
}

.metric-item {
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  animation: fadeUp 0.6s ease-out backwards;
}

.metric-label {
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 500;
}

.metric-value {
  font-size: 36px;
  font-weight: 800;
  line-height: 1;
}

.text-brand { color: var(--brand); }
.text-blue { color: #3b82f6; }
.text-green { color: #10b981; }
.text-orange { color: #f59e0b; }
.text-red { color: #ef4444; }

.table-section {
  padding: 20px;
}

.score-split {
  display: flex;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);
}
.divider { color: #cbd5e1; }

.total-score {
  font-size: 16px;
}

.pass-text {
  color: #10b981;
  font-weight: 600;
}
.fail-text {
  color: #ef4444;
  font-weight: 600;
}
.fw-semibold { font-weight: 600; }
.text-muted { color: var(--text-muted); font-size: 13px; }

/* Make table transparent for glassmorphism */
:deep(.el-table), :deep(.el-table__expanded-cell) {
  background-color: transparent !important;
}
:deep(.el-table tr), :deep(.el-table td.el-table__cell) {
  background-color: transparent !important;
  border-bottom: 1px solid rgba(13, 148, 136, 0.08);
}
:deep(.el-table::before) { display: none; }

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 1100px) {
  .metrics-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 720px) {
  .results-hero {
    flex-direction: column;
    align-items: flex-start;
  }
  .metrics-row { grid-template-columns: 1fr; }
}
</style>
