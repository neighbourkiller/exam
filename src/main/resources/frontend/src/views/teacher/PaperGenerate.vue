<template>
  <el-card class="page-card">
    <template #header><div class="header">组卷管理</div></template>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>智能组卷</template>
          <el-form :model="autoForm" label-width="100px">
            <el-form-item label="试卷名称"><el-input v-model="autoForm.name" /></el-form-item>
            <el-form-item label="课程">
              <el-select
                v-model="autoForm.subjectId"
                filterable
                clearable
                placeholder="请选择课程"
                style="width: 100%"
              >
                <el-option
                  v-for="course in subjectOptions"
                  :key="course.id"
                  :label="courseLabel(course)"
                  :value="course.id"
                >
                  <div class="course-option-row">
                    <span class="course-option-id">{{ course.id }}</span>
                    <span>{{ course.name }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
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
            <el-table-column label="数量" width="120">
              <template #default="scope"><el-input-number v-model="scope.row.count" :min="1" size="small" /></template>
            </el-table-column>
            <el-table-column label="分值" width="120">
              <template #default="scope"><el-input-number v-model="scope.row.score" :min="1" size="small" /></template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="scope">
                <el-button type="danger" text @click="removeRule(scope.$index)">删除</el-button>
              </template>
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
          <el-input v-model="paperId" placeholder="输入试卷ID" class="mb-12" />
          <el-button type="primary" @click="loadPaper">查询</el-button>
          <div v-if="paper.id" class="paper-detail">
            <h3>{{ paper.name }} (总分 {{ paper.totalScore }})</h3>
            <p class="paper-meta">课程：{{ paper.subjectName || paper.subjectId }} | 出卷人：{{ paper.teacherId || '-' }}</p>
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

    <el-divider />

    <el-card>
      <template #header>
        <div class="list-header">
          <span>试卷列表</span>
          <el-button type="success" @click="openCreateManualDialog">手动组卷</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="paperQuery" class="mb-12">
        <el-form-item label="课程">
          <el-select
            v-model="paperQuery.subjectId"
            filterable
            clearable
            placeholder="请选择课程"
            style="width: 240px"
          >
            <el-option
              v-for="course in subjectOptions"
              :key="course.id"
              :label="courseLabel(course)"
              :value="course.id"
            >
              <div class="course-option-row">
                <span class="course-option-id">{{ course.id }}</span>
                <span>{{ course.name }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="试卷名">
          <el-input v-model="paperQuery.name" clearable placeholder="输入试卷名称" style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchPaperList">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="paperList" border>
        <el-table-column prop="id" label="试卷ID" width="140" />
        <el-table-column prop="name" label="试卷名称" min-width="220" />
        <el-table-column label="课程" width="180">
          <template #default="scope">{{ scope.row.subjectName || scope.row.subjectId }}</template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="90" />
        <el-table-column prop="teacherId" label="出卷人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="190" />
        <el-table-column label="操作" width="240">
          <template #default="scope">
            <el-button size="small" @click="viewPaper(scope.row)">查看</el-button>
            <el-button
              size="small"
              type="primary"
              :disabled="scope.row.canManage === false"
              @click="openEditManualDialog(scope.row)"
            >
              修改
            </el-button>
            <el-button
              size="small"
              type="danger"
              :disabled="scope.row.canManage === false"
              @click="deletePaper(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <el-pagination
          v-model:current-page="paperQuery.pageNum"
          v-model:page-size="paperQuery.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="paperTotal"
          layout="total, sizes, prev, pager, next"
          @size-change="loadPaperList"
          @current-change="loadPaperList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="manualDialogVisible"
      :title="manualDialogTitle"
      width="1100px"
      destroy-on-close
    >
      <el-form :model="manualForm" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="试卷名称"><el-input v-model="manualForm.name" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="课程">
              <el-select
                v-model="manualForm.subjectId"
                filterable
                clearable
                placeholder="请选择课程"
                style="width: 100%"
              >
                <el-option
                  v-for="course in subjectOptions"
                  :key="course.id"
                  :label="courseLabel(course)"
                  :value="course.id"
                >
                  <div class="course-option-row">
                    <span class="course-option-id">{{ course.id }}</span>
                    <span>{{ course.name }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="描述"><el-input v-model="manualForm.description" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider>题库选择</el-divider>

      <el-form :inline="true" :model="manualQuestionQuery" class="mb-12">
        <el-form-item label="关键字">
          <el-input v-model="manualQuestionQuery.keyword" clearable placeholder="题干关键字" style="width: 220px" />
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="manualQuestionQuery.type" clearable style="width: 120px">
            <el-option value="SINGLE" label="单选" />
            <el-option value="MULTI" label="多选" />
            <el-option value="JUDGE" label="判断" />
            <el-option value="BLANK" label="填空" />
            <el-option value="SHORT" label="简答" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="manualQuestionQuery.difficulty" clearable style="width: 120px">
            <el-option value="EASY" label="简单" />
            <el-option value="MEDIUM" label="中等" />
            <el-option value="HARD" label="困难" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchManualQuestions">查询题目</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="candidateQuestions" border max-height="250">
        <el-table-column prop="id" label="题目ID" width="120" />
        <el-table-column prop="type" label="题型" width="90" />
        <el-table-column prop="difficulty" label="难度" width="90" />
        <el-table-column prop="content" label="题目" min-width="360" show-overflow-tooltip />
        <el-table-column prop="defaultScore" label="默认分值" width="100" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button
              size="small"
              type="primary"
              :disabled="selectedQuestionIdSet.has(scope.row.id)"
              @click="addQuestionToManual(scope.row)"
            >
              添加
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-divider>已选题目</el-divider>

      <el-table :data="selectedQuestions" border max-height="260">
        <el-table-column prop="sortOrder" label="序号" width="70" />
        <el-table-column prop="questionId" label="题目ID" width="120" />
        <el-table-column prop="type" label="题型" width="90" />
        <el-table-column prop="content" label="题目" min-width="360" show-overflow-tooltip />
        <el-table-column label="分值" width="130">
          <template #default="scope">
            <el-input-number v-model="scope.row.score" :min="1" :max="100" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="scope">
            <el-button text :disabled="scope.$index === 0" @click="moveQuestion(scope.$index, -1)">上移</el-button>
            <el-button text :disabled="scope.$index === selectedQuestions.length - 1" @click="moveQuestion(scope.$index, 1)">下移</el-button>
            <el-button text type="danger" @click="removeSelectedQuestion(scope.$index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitManualPaper">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAutoPaperApi,
  createManualPaperApi,
  deletePaperApi,
  listQuestionSubjectsApi,
  paperDetailApi,
  queryPapersApi,
  queryQuestionsApi,
  updatePaperApi
} from '../../api'

const autoForm = reactive({
  name: 'Java 自动组卷',
  subjectId: null,
  description: '自动生成',
  rules: [{ type: 'SINGLE', difficulty: 'EASY', count: 5, score: 2 }]
})

const paperId = ref('')
const paper = reactive({
  id: null,
  name: '',
  subjectId: null,
  subjectName: '',
  description: '',
  totalScore: 0,
  teacherId: null,
  questions: []
})

const subjectOptions = ref([])

const paperQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  subjectId: null,
  name: ''
})
const paperList = ref([])
const paperTotal = ref(0)

const manualDialogVisible = ref(false)
const manualMode = ref('create')
const editingPaperId = ref(null)

const manualForm = reactive({
  name: '',
  subjectId: null,
  description: ''
})

const manualQuestionQuery = reactive({
  keyword: '',
  type: null,
  difficulty: null
})

const candidateQuestions = ref([])
const selectedQuestions = ref([])

const manualDialogTitle = computed(() => (manualMode.value === 'edit' ? '修改试卷' : '手动组卷'))
const selectedQuestionIdSet = computed(() => new Set(selectedQuestions.value.map(item => item.questionId)))

const courseLabel = (course) => `${course.id} - ${course.name}`

const loadSubjectOptions = async () => {
  subjectOptions.value = await listQuestionSubjectsApi()
}

const addRule = () => {
  autoForm.rules.push({ type: 'SINGLE', difficulty: 'EASY', count: 1, score: 2 })
}

const removeRule = (index) => {
  if (autoForm.rules.length <= 1) {
    ElMessage.warning('至少保留1条规则')
    return
  }
  autoForm.rules.splice(index, 1)
}

const autoCreate = async () => {
  if (!autoForm.subjectId) {
    ElMessage.warning('请选择课程')
    return
  }
  const id = await createAutoPaperApi(autoForm)
  paperId.value = id
  ElMessage.success(`组卷成功，试卷ID=${id}`)
  await loadPaper()
  await loadPaperList()
}

const loadPaper = async () => {
  if (!paperId.value) {
    return
  }
  const data = await paperDetailApi(paperId.value)
  Object.assign(paper, data)
}

const loadPaperList = async () => {
  const payload = {
    pageNum: paperQuery.pageNum,
    pageSize: paperQuery.pageSize,
    subjectId: paperQuery.subjectId || null,
    name: (paperQuery.name || '').trim() || null
  }
  const data = await queryPapersApi(payload)
  paperList.value = data.records || []
  paperTotal.value = data.total || 0
}

const searchPaperList = async () => {
  paperQuery.pageNum = 1
  await loadPaperList()
}

const viewPaper = async (row) => {
  paperId.value = row.id
  await loadPaper()
}

const resetManualDialog = () => {
  manualMode.value = 'create'
  editingPaperId.value = null
  manualForm.name = ''
  manualForm.subjectId = null
  manualForm.description = ''
  manualQuestionQuery.keyword = ''
  manualQuestionQuery.type = null
  manualQuestionQuery.difficulty = null
  candidateQuestions.value = []
  selectedQuestions.value = []
}

const openCreateManualDialog = () => {
  resetManualDialog()
  manualDialogVisible.value = true
}

const normalizeSelectedQuestions = (questions) => {
  selectedQuestions.value = (questions || []).map((item, index) => ({
    sortOrder: index + 1,
    questionId: item.questionId,
    type: item.type,
    difficulty: item.difficulty,
    content: item.content,
    score: item.score || 1
  }))
}

const openEditManualDialog = async (row) => {
  if (row.canManage === false) {
    ElMessage.warning('无权限修改该试卷')
    return
  }

  resetManualDialog()
  manualMode.value = 'edit'
  editingPaperId.value = row.id

  const detail = await paperDetailApi(row.id)
  manualForm.name = detail.name || ''
  manualForm.subjectId = detail.subjectId || null
  manualForm.description = detail.description || ''
  normalizeSelectedQuestions((detail.questions || []).slice().sort((a, b) => a.sortOrder - b.sortOrder))

  manualDialogVisible.value = true
  await searchManualQuestions()
}

const searchManualQuestions = async () => {
  if (!manualForm.subjectId) {
    ElMessage.warning('请先选择课程')
    return
  }
  const payload = {
    pageNum: 1,
    pageSize: 100,
    subjectId: manualForm.subjectId,
    keyword: (manualQuestionQuery.keyword || '').trim() || null,
    type: manualQuestionQuery.type || null,
    difficulty: manualQuestionQuery.difficulty || null
  }
  const data = await queryQuestionsApi(payload)
  candidateQuestions.value = data.records || []
}

const addQuestionToManual = (question) => {
  if (selectedQuestionIdSet.value.has(question.id)) {
    return
  }
  selectedQuestions.value.push({
    sortOrder: selectedQuestions.value.length + 1,
    questionId: question.id,
    type: question.type,
    difficulty: question.difficulty,
    content: question.content,
    score: question.defaultScore || 1
  })
}

const renumberSelectedQuestions = () => {
  selectedQuestions.value = selectedQuestions.value.map((item, index) => ({
    ...item,
    sortOrder: index + 1
  }))
}

const removeSelectedQuestion = (index) => {
  selectedQuestions.value.splice(index, 1)
  renumberSelectedQuestions()
}

const moveQuestion = (index, delta) => {
  const target = index + delta
  if (target < 0 || target >= selectedQuestions.value.length) {
    return
  }
  const list = [...selectedQuestions.value]
  const current = list[index]
  list[index] = list[target]
  list[target] = current
  selectedQuestions.value = list
  renumberSelectedQuestions()
}

const buildManualPayload = () => {
  if (!manualForm.name.trim()) {
    ElMessage.warning('请输入试卷名称')
    return null
  }
  if (!manualForm.subjectId) {
    ElMessage.warning('请选择课程')
    return null
  }
  if (!selectedQuestions.value.length) {
    ElMessage.warning('请至少添加一道题目')
    return null
  }

  const questions = selectedQuestions.value.map((item, index) => ({
    questionId: item.questionId,
    score: Number(item.score),
    sortOrder: index + 1
  }))

  if (questions.some(item => !item.score || item.score < 1)) {
    ElMessage.warning('每道题分值必须大于0')
    return null
  }

  return {
    name: manualForm.name.trim(),
    subjectId: manualForm.subjectId,
    description: (manualForm.description || '').trim() || null,
    questions
  }
}

const submitManualPaper = async () => {
  const payload = buildManualPayload()
  if (!payload) {
    return
  }

  if (manualMode.value === 'edit') {
    await updatePaperApi(editingPaperId.value, payload)
    ElMessage.success('试卷更新成功')
    paperId.value = editingPaperId.value
  } else {
    const id = await createManualPaperApi(payload)
    ElMessage.success(`组卷成功，试卷ID=${id}`)
    paperId.value = id
  }

  manualDialogVisible.value = false
  await loadPaper()
  await loadPaperList()
}

const deletePaper = async (row) => {
  if (row.canManage === false) {
    ElMessage.warning('无权限删除该试卷')
    return
  }

  await ElMessageBox.confirm('确认删除该试卷吗？若已被考试引用将无法删除。', '提示')
  try {
    await deletePaperApi(row.id)
    ElMessage.success('删除成功')
    if (paper.id === row.id) {
      Object.assign(paper, {
        id: null,
        name: '',
        subjectId: null,
        subjectName: '',
        description: '',
        totalScore: 0,
        teacherId: null,
        questions: []
      })
      paperId.value = ''
    }
    await loadPaperList()
  } catch (error) {
    if (error?.code === 'PAPER_REFERENCED_BY_EXAM') {
      return
    }
  }
}

onMounted(async () => {
  await loadSubjectOptions()
  await loadPaperList()
})
</script>

<style scoped>
.header {
  font-size: 18px;
  font-weight: 700;
}

.actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.mb-12 {
  margin-bottom: 12px;
}

.paper-detail {
  margin-top: 12px;
}

.paper-meta {
  margin: 0 0 10px;
  color: #606266;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pager-row {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.course-option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.course-option-id {
  color: #606266;
  min-width: 64px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}
</style>
