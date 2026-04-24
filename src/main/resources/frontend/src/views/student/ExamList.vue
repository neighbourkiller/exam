<template>
  <div class="exam-list-container">
    <el-card class="page-card hover-lift" style="border: none;">
      <template #header>
        <div class="header-content">
          <div class="header">
            <span class="header-icon">📚</span>
            <span>我的考试</span>
          </div>
          <el-button type="primary" plain round @click="load">刷新列表</el-button>
        </div>
      </template>
      
      <el-table :data="exams" class="custom-table" style="width: 100%;"
        :header-cell-style="{ background: 'var(--bg-main)', color: 'var(--brand)', fontWeight: '600' }"
        :row-style="{ background: 'transparent' }">
        <el-table-column label="课程" width="170">
          <template #default="{ row }"><span class="course-name">{{ row.subjectName || '--' }}</span></template>
        </el-table-column>
        <el-table-column prop="name" label="名称" />
        <el-table-column label="开始时间" width="190">
          <template #default="{ row }"><span class="time-text">{{ formatDateTime(row.startTime) }}</span></template>
        </el-table-column>
        <el-table-column label="结束时间" width="190">
          <template #default="{ row }"><span class="time-text">{{ formatDateTime(row.endTime) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" effect="light" round>{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已提交" width="90">
          <template #default="{ row }">
            <el-tag :type="row.submitted ? 'success' : 'info'" size="small" round>{{ row.submitted ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button :disabled="!canEnter(scope.row)" type="primary" round size="small" @click="start(scope.row)">
              进入考试
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <PreExamCheckDialog
      v-model="checkVisible"
      :exam="pendingExam"
      @passed="enterPendingExam"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { studentExamsApi } from '../../api'
import { formatDateTime, parseDateTime } from '../../utils/datetime'
import PreExamCheckDialog from './PreExamCheckDialog.vue'

const router = useRouter()
const exams = ref([])
const checkVisible = ref(false)
const pendingExam = ref(null)
const disallowedStatuses = new Set(['FINISHED', 'TERMINATED'])

const load = async () => {
  exams.value = await studentExamsApi()
}

const canEnter = (exam) => {
  if (!exam) return false
  if (exam.submitted) return false
  if (disallowedStatuses.has(exam.status)) return false
  const endTime = parseDateTime(exam.endTime)
  if (endTime && endTime.getTime() <= Date.now()) return false
  return true
}

const start = (row) => {
  if (!canEnter(row)) return
  pendingExam.value = row
  checkVisible.value = true
}

const enterPendingExam = async (handoff) => {
  if (!canEnter(pendingExam.value)) {
    handoff?.reject?.()
    ElMessage.warning('当前考试已不可进入，请刷新考试列表后重试。')
    return
  }
  const target = `/student/exam/${pendingExam.value.examId}`
  handoff?.accept?.()
  try {
    const failure = await router.push(target)
    if (failure) {
      handoff?.reject?.()
    }
  } catch {
    handoff?.reject?.()
  }
}

const getStatusType = (status) => {
  switch (status) {
    case 'ONGOING': return 'success'
    case 'PUBLISHED': return 'warning'
    case 'FINISHED': return 'info'
    case 'TERMINATED': return 'danger'
    default: return 'info'
  }
}

onMounted(load)
</script>

<style scoped>
.exam-list-container {
  padding: 10px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header {
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--brand);
}

.course-name {
  font-weight: 600;
  color: var(--text-main);
}

.time-text {
  color: var(--text-muted);
  font-size: 13px;
}

.custom-table {
  border-radius: var(--radius-sm);
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0,0,0,0.02);
}

/* Make table transparent for glassmorphism */
:deep(.el-table), :deep(.el-table__expanded-cell) {
  background-color: transparent !important;
}
:deep(.el-table tr), :deep(.el-table td.el-table__cell) {
  background-color: transparent !important;
}
</style>
