<template>
  <el-card class="page-card">
    <template #header>
      <div class="header">题库管理</div>
    </template>

    <el-form :inline="true" :model="query" class="mb-12">
      <el-form-item label="关键字">
        <el-input v-model="query.keyword" placeholder="题干关键字" clearable />
      </el-form-item>
      <el-form-item label="题型">
        <el-select v-model="query.type" clearable style="width: 120px">
          <el-option label="单选" value="SINGLE" />
          <el-option label="多选" value="MULTI" />
          <el-option label="判断" value="JUDGE" />
          <el-option label="填空" value="BLANK" />
          <el-option label="简答" value="SHORT" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadQuestions">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tableData" border>
      <el-table-column prop="id" label="ID" width="120" />
      <el-table-column prop="type" label="题型" width="90" />
      <el-table-column prop="difficulty" label="难度" width="90" />
      <el-table-column prop="content" label="题目" />
      <el-table-column prop="defaultScore" label="默认分值" width="100" />
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button type="danger" size="small" @click="del(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="section-title">新增题目</div>
    <el-form :model="form" label-width="90px">
      <el-row :gutter="10">
        <el-col :span="6"><el-form-item label="科目ID"><el-input v-model.number="form.subjectId" /></el-form-item></el-col>
        <el-col :span="6"><el-form-item label="题型"><el-select v-model="form.type"><el-option label="单选" value="SINGLE" /><el-option label="多选" value="MULTI" /><el-option label="判断" value="JUDGE" /><el-option label="填空" value="BLANK" /><el-option label="简答" value="SHORT" /></el-select></el-form-item></el-col>
        <el-col :span="6"><el-form-item label="难度"><el-select v-model="form.difficulty"><el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="困难" value="HARD" /></el-select></el-form-item></el-col>
        <el-col :span="6"><el-form-item label="分值"><el-input v-model.number="form.defaultScore" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="题目"><el-input v-model="form.content" type="textarea" :rows="2" /></el-form-item>
      <el-form-item label="选项JSON"><el-input v-model="form.optionsJson" placeholder='[{"label":"A","value":"..."}]' /></el-form-item>
      <el-form-item label="答案"><el-input v-model="form.answer" /></el-form-item>
      <el-form-item label="解析"><el-input v-model="form.analysis" type="textarea" :rows="2" /></el-form-item>
      <el-button type="success" @click="create">新增题目</el-button>
    </el-form>
  </el-card>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createQuestionApi, deleteQuestionApi, queryQuestionsApi } from '../../api'

const tableData = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 20,
  subjectId: 5001,
  keyword: '',
  type: '',
  difficulty: ''
})

const form = reactive({
  subjectId: 5001,
  type: 'SINGLE',
  difficulty: 'EASY',
  content: '',
  optionsJson: '',
  answer: '',
  analysis: '',
  defaultScore: 5
})

const loadQuestions = async () => {
  const payload = {
    ...query,
    subjectId: query.subjectId || 5001
  }
  const data = await queryQuestionsApi(payload)
  tableData.value = data.records || []
}

const create = async () => {
  await createQuestionApi(form)
  ElMessage.success('新增成功')
  form.content = ''
  form.optionsJson = ''
  form.answer = ''
  form.analysis = ''
  await loadQuestions()
}

const del = async (id) => {
  await ElMessageBox.confirm('确认删除该题目吗？', '提示')
  await deleteQuestionApi(id)
  ElMessage.success('删除成功')
  await loadQuestions()
}

onMounted(loadQuestions)
</script>

<style scoped>
.header {
  font-size: 18px;
  font-weight: 700;
}

.mb-12 {
  margin-bottom: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 16px 0 8px;
}
</style>
