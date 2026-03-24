<template>
  <el-card class="page-card">
    <template #header><div class="header">考试发布</div></template>

    <el-form :model="form" label-width="110px">
      <el-row :gutter="12">
        <el-col :span="8"><el-form-item label="考试名称"><el-input v-model="form.name" /></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="试卷ID"><el-input v-model.number="form.paperId" /></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="及格线"><el-input v-model.number="form.passScore" /></el-form-item></el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8"><el-form-item label="开考时间"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="结束时间"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="时长(分钟)"><el-input v-model.number="form.durationMinutes" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="目标班级ID">
        <el-input v-model="classIdsText" placeholder="例如: 3001,3002" />
      </el-form-item>
      <el-button type="primary" @click="createExam">创建考试</el-button>
    </el-form>

    <el-divider />

    <el-button @click="loadExams">刷新考试列表</el-button>
    <el-table :data="exams" border style="margin-top: 10px">
      <el-table-column prop="examId" label="考试ID" width="120" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="startTime" label="开始时间" width="190" />
      <el-table-column prop="endTime" label="结束时间" width="190" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button v-if="scope.row.status === 'DRAFT'" type="primary" size="small" @click="publish(scope.row.examId)">发布</el-button>
          <span v-else>已发布</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { createExamApi, publishExamApi, teacherExamsApi } from '../../api'

const now = new Date()
const later = new Date(now.getTime() + 3600 * 1000)

const form = reactive({
  name: 'Java阶段测验',
  paperId: null,
  startTime: now.toISOString().slice(0, 19),
  endTime: later.toISOString().slice(0, 19),
  durationMinutes: 60,
  passScore: 60
})

const classIdsText = ref('3001')
const exams = ref([])

const createExam = async () => {
  const targetClassIds = classIdsText.value.split(',').map((v) => Number(v.trim())).filter(Boolean)
  const payload = { ...form, targetClassIds }
  const id = await createExamApi(payload)
  ElMessage.success(`考试创建成功: ${id}`)
  await loadExams()
}

const publish = async (id) => {
  await publishExamApi(id)
  ElMessage.success('发布成功')
  await loadExams()
}

const loadExams = async () => {
  exams.value = await teacherExamsApi()
}

onMounted(loadExams)
</script>

<style scoped>
.header { font-size: 18px; font-weight: 700; }
</style>
