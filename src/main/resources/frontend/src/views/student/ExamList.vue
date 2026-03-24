<template>
  <el-card class="page-card">
    <template #header><div class="header">我的考试</div></template>
    <el-button @click="load">刷新</el-button>
    <el-table :data="exams" border style="margin-top: 10px">
      <el-table-column prop="examId" label="考试ID" width="120" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="startTime" label="开始时间" width="180" />
      <el-table-column prop="endTime" label="结束时间" width="180" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="submitted" label="已提交" width="90" />
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button :disabled="scope.row.submitted" type="primary" size="small" @click="start(scope.row.examId)">进入考试</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { studentExamsApi } from '../../api'

const router = useRouter()
const exams = ref([])

const load = async () => {
  exams.value = await studentExamsApi()
}

const start = (id) => {
  router.push(`/student/exam/${id}`)
}

onMounted(load)
</script>

<style scoped>
.header { font-size: 18px; font-weight: 700; }
</style>
