<template>
  <el-card class="page-card">
    <template #header><div class="header">组卷管理</div></template>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>智能组卷</template>
          <el-form :model="autoForm" label-width="100px">
            <el-form-item label="试卷名称"><el-input v-model="autoForm.name" /></el-form-item>
            <el-form-item label="科目ID"><el-input v-model.number="autoForm.subjectId" /></el-form-item>
            <el-form-item label="描述"><el-input v-model="autoForm.description" /></el-form-item>
          </el-form>
          <el-table :data="autoForm.rules" border>
            <el-table-column label="题型">
              <template #default="scope">
                <el-select v-model="scope.row.type" size="small">
                  <el-option value="SINGLE" label="单选" />
                  <el-option value="MULTI" label="多选" />
                  <el-option value="JUDGE" label="判断" />
                  <el-option value="BLANK" label="填空" />
                  <el-option value="SHORT" label="简答" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="难度">
              <template #default="scope">
                <el-select v-model="scope.row.difficulty" size="small">
                  <el-option value="EASY" label="简单" />
                  <el-option value="MEDIUM" label="中等" />
                  <el-option value="HARD" label="困难" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量">
              <template #default="scope"><el-input v-model.number="scope.row.count" size="small" /></template>
            </el-table-column>
            <el-table-column label="分值">
              <template #default="scope"><el-input v-model.number="scope.row.score" size="small" /></template>
            </el-table-column>
          </el-table>
          <div class="actions">
            <el-button @click="addRule">新增规则</el-button>
            <el-button type="primary" @click="autoCreate">生成试卷</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>试卷详情</template>
          <el-input v-model.number="paperId" placeholder="输入试卷ID" class="mb-12" />
          <el-button type="primary" @click="loadPaper">查询</el-button>
          <div v-if="paper.id" class="paper-detail">
            <h3>{{ paper.name }} (总分 {{ paper.totalScore }})</h3>
            <el-table :data="paper.questions" border>
              <el-table-column prop="sortOrder" label="序号" width="70" />
              <el-table-column prop="type" label="题型" width="80" />
              <el-table-column prop="content" label="题目" />
              <el-table-column prop="score" label="分值" width="80" />
            </el-table>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createAutoPaperApi, paperDetailApi } from '../../api'

const autoForm = reactive({
  name: 'Java 自动组卷',
  subjectId: 5001,
  description: '自动生成',
  rules: [{ type: 'SINGLE', difficulty: 'EASY', count: 5, score: 2 }]
})

const paperId = ref('')
const paper = reactive({ id: null, name: '', totalScore: 0, questions: [] })

const addRule = () => {
  autoForm.rules.push({ type: 'SINGLE', difficulty: 'EASY', count: 1, score: 2 })
}

const autoCreate = async () => {
  const id = await createAutoPaperApi(autoForm)
  paperId.value = id
  ElMessage.success(`组卷成功，试卷ID=${id}`)
  await loadPaper()
}

const loadPaper = async () => {
  if (!paperId.value) return
  const data = await paperDetailApi(paperId.value)
  Object.assign(paper, data)
}
</script>

<style scoped>
.header { font-size: 18px; font-weight: 700; }
.actions { margin-top: 10px; display: flex; gap: 8px; }
.mb-12 { margin-bottom: 12px; }
.paper-detail { margin-top: 12px; }
</style>
