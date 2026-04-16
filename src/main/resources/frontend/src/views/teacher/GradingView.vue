<template>
  <el-card class="page-card">
    <template #header><div class="header">主观题阅卷</div></template>
    <el-button @click="load">刷新待批阅</el-button>
    <el-table :data="rows" style="margin-top: 10px">
      <el-table-column prop="submissionId" label="提交ID" width="120" />
      <el-table-column prop="examName" label="考试" width="160" />
      <el-table-column prop="studentName" label="学生" width="120" />
      <el-table-column prop="questionContent" label="题目" />
      <el-table-column prop="answerText" label="学生答案" />
      <el-table-column label="评分" width="180">
        <template #default="scope">
          <el-input v-model.number="scope.row.tempScore" type="number" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button type="primary" size="small" @click="score(scope.row)">提交评分</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { gradingPendingApi, gradingScoreApi } from '../../api'

const rows = ref([])

const load = async () => {
  const data = await gradingPendingApi()
  rows.value = (data || []).map((item) => ({ ...item, tempScore: 0 }))
}

const score = async (row) => {
  await gradingScoreApi(row.submissionId, {
    scores: [{ submissionAnswerId: row.submissionAnswerId, score: Number(row.tempScore || 0), comment: '' }]
  })
  ElMessage.success('评分成功')
  await load()
}

onMounted(load)
</script>

<style scoped>


.page-card {
  border: none;
  border-radius: 16px;
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.02);
  background-color: #ffffff;
}

:deep(.el-card__header) {
  border-bottom: none;
  padding-bottom: 0;
}

.header { 
  font-size: 20px; 
  font-weight: 700; 
  color: #1e293b;
}

:deep(.el-table) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-table th.el-table__cell) {
  background-color: #f8fafc;
  color: #475569;
  font-weight: 600;
  border-bottom: 1px solid #f1f5f9;
}

:deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid #f1f5f9;
  padding: 12px 0;
}

:deep(.el-table--enable-row-hover .el-table__body tr:hover > td.el-table__cell) {
  background-color: #f8fafc;
}

:deep(.el-input__wrapper), :deep(.el-select__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background-color: #f8fafc;
  transition: all 0.2s ease;
}

:deep(.el-input__wrapper.is-focus), :deep(.el-select__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #bfdbfe inset, 0 0 0 1px #3b82f6 inset;
  background-color: #ffffff;
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
}

:deep(.el-dialog) {
  border-radius: 16px;
}

</style>
