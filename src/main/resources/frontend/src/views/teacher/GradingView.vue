<template>
  <el-card class="page-card">
    <template #header><div class="header">主观题阅卷</div></template>
    <el-button @click="load">刷新待批阅</el-button>
    <el-table :data="rows" border style="margin-top: 10px">
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
.header { font-size: 18px; font-weight: 700; }
</style>
