<template>
  <el-card class="page-card">
    <template #header><div class="header">我的考试</div></template>
    <el-button @click="load">刷新</el-button>
    <el-table :data="exams" border style="margin-top: 10px">
      <el-table-column label="课程" width="170">
        <template #default="{ row }">{{ row.subjectName || '--' }}</template>
      </el-table-column>
      <el-table-column prop="name" label="名称" />
      <el-table-column label="开始时间" width="190">
        <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" width="190">
        <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="submitted" label="已提交" width="90" />
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button :disabled="!canEnter(scope.row)" type="primary" size="small" @click="start(scope.row)">进入考试</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { studentExamsApi } from '../../api'
import { formatDateTime, parseDateTime } from '../../utils/datetime'

const router = useRouter()
const exams = ref([])
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
  router.push(`/student/exam/${row.examId}`)
}

onMounted(load)
</script>

<style scoped>
.header { font-size: 18px; font-weight: 700; }
</style>
