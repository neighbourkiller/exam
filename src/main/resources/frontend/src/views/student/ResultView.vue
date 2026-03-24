<template>
  <el-card class="page-card">
    <template #header><div class="header">交卷结果</div></template>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="提交ID">{{ result.submissionId }}</el-descriptions-item>
      <el-descriptions-item label="客观题得分">{{ result.objectiveScore }}</el-descriptions-item>
      <el-descriptions-item label="主观题得分">{{ result.subjectiveScore }}</el-descriptions-item>
      <el-descriptions-item label="总分">{{ result.totalScore }}</el-descriptions-item>
      <el-descriptions-item label="是否通过">{{ result.passFlag ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ result.status }}</el-descriptions-item>
    </el-descriptions>
    <el-button style="margin-top: 12px" type="primary" @click="goList">返回考试列表</el-button>
  </el-card>
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
.header { font-size: 18px; font-weight: 700; }
</style>
