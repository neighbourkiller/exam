<template>
  <div class="results-page">
    <section class="results-hero">
      <div>
        <h1 class="results-title">考试结果中心</h1>
        <p class="results-subtitle">查看全部考试与成绩状态，包含待阅卷与未交卷记录。</p>
      </div>
      <el-button type="primary" plain @click="loadResults">刷新数据</el-button>
    </section>

    <section class="metrics-row">
      <article class="metric-item">
        <div class="metric-label">总考试数</div>
        <div class="metric-value">{{ metrics.totalCount }}</div>
      </article>
      <article class="metric-item">
        <div class="metric-label">已交卷</div>
        <div class="metric-value">{{ metrics.submittedCount }}</div>
      </article>
      <article class="metric-item">
        <div class="metric-label">已出分</div>
        <div class="metric-value">{{ metrics.gradedCount }}</div>
      </article>
      <article class="metric-item">
        <div class="metric-label">已通过</div>
        <div class="metric-value">{{ metrics.passCount }}</div>
      </article>
    </section>

    <section class="table-section">
      <el-table :data="rows" border stripe>
        <el-table-column label="课程" width="170">
          <template #default="{ row }">{{ row.subjectName || '--' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="考试名称" min-width="180" />
        <el-table-column label="开始时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="考试状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="examStatusTagType(row.examStatus)">{{ examStatusText(row.examStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="submissionStatusTagType(row)">{{ submissionStatusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="客观分" width="90">
          <template #default="{ row }">{{ formatScore(row.objectiveScore) }}</template>
        </el-table-column>
        <el-table-column label="主观分" width="90">
          <template #default="{ row }">{{ formatScore(row.subjectiveScore) }}</template>
        </el-table-column>
        <el-table-column label="总分" width="90">
          <template #default="{ row }">{{ formatScore(row.totalScore) }}</template>
        </el-table-column>
        <el-table-column label="是否通过" width="100">
          <template #default="{ row }">{{ passText(row.passFlag) }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
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
  if (row.submissionStatus === 'SUBMITTED') return '待阅卷'
  if (row.submissionStatus === 'GRADED') return '已出分'
  return row.submissionStatus || '--'
}

const submissionStatusTagType = (row) => {
  const text = submissionStatusText(row)
  if (text === '已出分') return 'success'
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
  gap: 16px;
}

.results-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 18px 20px;
  border-radius: 12px;
  border: 1px solid #d9ebe7;
  background: linear-gradient(120deg, #ecf9f6 0%, #f6fbfa 100%);
}

.results-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
  color: #0f3f38;
}

.results-subtitle {
  margin: 8px 0 0;
  color: #4b6b67;
}

.metrics-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-item {
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid #e4ecea;
  background: #ffffff;
}

.metric-label {
  color: #6b7280;
  font-size: 13px;
}

.metric-value {
  margin-top: 6px;
  color: #0f172a;
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}

.table-section {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px;
}

@media (max-width: 1100px) {
  .metrics-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .results-hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .metrics-row {
    grid-template-columns: 1fr;
  }
}
</style>
