<template>
  <div class="grading-page">
    <section class="panel group-panel">
      <div class="panel-header">
        <div>
          <h2>主观题阅卷</h2>
          <p>按考试和题目连续批改待阅答案</p>
        </div>
        <el-button @click="reloadGroups">刷新题目</el-button>
      </div>

      <el-table
        ref="groupTableRef"
        v-loading="groupLoading"
        :data="groups"
        row-key="key"
        highlight-current-row
        class="group-table"
        @current-change="handleGroupChange"
      >
        <el-table-column prop="examName" label="考试" min-width="140" />
        <el-table-column label="题目" min-width="220">
          <template #default="{ row }">
            <div class="question-preview">
              <div class="question-text">{{ row.questionContent || '未命名题目' }}</div>
              <div class="question-meta">第 {{ row.sortOrder || '-' }} 题</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="defaultScore" label="分值" width="80" />
        <el-table-column prop="pendingCount" label="待批" width="80" />
      </el-table>

      <el-empty v-if="!groupLoading && !groups.length" description="当前无待批题目" />
    </section>

    <section class="panel detail-panel">
      <div class="panel-header">
        <div>
          <h3>{{ currentGroup ? currentGroup.examName : '请选择题目' }}</h3>
          <p v-if="currentGroup">
            第 {{ currentGroup.sortOrder || '-' }} 题 · 满分 {{ currentGroup.defaultScore ?? 0 }} 分 · 待批 {{ currentGroup.pendingCount }} 份
          </p>
          <p v-else>从左侧选择一题后开始连续批改</p>
        </div>
        <div class="header-actions">
          <el-button :disabled="!currentGroup" @click="refreshCurrentQuestion">刷新当前题</el-button>
          <el-button :disabled="!hasNextQuestion" @click="goToNextQuestion">下一题</el-button>
        </div>
      </div>

      <template v-if="currentGroup">
        <div class="question-info">
          <div class="info-block">
            <div class="info-label">题目</div>
            <div class="info-content">{{ currentGroup.questionContent || '--' }}</div>
          </div>
          <div class="info-grid">
            <div class="info-block">
              <div class="info-label">参考答案</div>
              <div class="info-content multiline">{{ currentGroup.referenceAnswer || '暂无参考答案' }}</div>
            </div>
            <div class="info-block">
              <div class="info-label">解析</div>
              <div class="info-content multiline">{{ currentGroup.analysis || '暂无解析' }}</div>
            </div>
          </div>
        </div>

        <div class="toolbar">
          <span class="selected-count">已选 {{ selectedAnswerIds.length }} 份</span>
          <el-input-number
            v-model="batchScore"
            :min="0"
            :max="currentMaxScore"
            :precision="0"
            controls-position="right"
            placeholder="统一分数"
          />
          <el-input
            v-model="batchComment"
            placeholder="可选评语"
            clearable
            maxlength="255"
            show-word-limit
          />
          <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="applyScoreToSelected">
            应用到已选
          </el-button>
        </div>

        <el-table
          ref="answerTableRef"
          v-loading="answerLoading"
          :data="answers"
          row-key="submissionAnswerId"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column prop="studentName" label="学生" width="140" />
          <el-table-column prop="submittedAt" label="提交时间" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.submittedAt, '--') }}
            </template>
          </el-table-column>
          <el-table-column label="学生答案" min-width="360">
            <template #default="{ row }">
              <div class="answer-text">{{ row.answerText || '未作答' }}</div>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!answerLoading && !answers.length" description="当前题已无待批答案" />
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  gradingPendingQuestionAnswersApi,
  gradingPendingQuestionsApi,
  gradingQuestionBatchScoreApi
} from '../../api'
import { formatDateTime } from '../../utils/datetime'

const groups = ref([])
const answers = ref([])
const currentGroup = ref(null)
const selectedAnswerIds = ref([])
const batchScore = ref(null)
const batchComment = ref('')
const groupLoading = ref(false)
const answerLoading = ref(false)
const submitting = ref(false)
const groupTableRef = ref()
const answerTableRef = ref()

const currentGroupKey = computed(() => (currentGroup.value ? buildGroupKey(currentGroup.value) : ''))
const currentMaxScore = computed(() => Number(currentGroup.value?.defaultScore ?? 0))
const currentGroupIndex = computed(() => groups.value.findIndex((item) => item.key === currentGroupKey.value))
const hasNextQuestion = computed(() => currentGroupIndex.value >= 0 && currentGroupIndex.value < groups.value.length - 1)
const canSubmit = computed(() => {
  const score = Number(batchScore.value)
  return currentGroup.value
    && selectedAnswerIds.value.length > 0
    && Number.isInteger(score)
    && score >= 0
    && score <= currentMaxScore.value
})

const buildGroupKey = (group) => `${group.examId}-${group.questionId}`

const normalizeGroups = (data) => (data || []).map((item) => ({
  ...item,
  key: buildGroupKey(item)
}))

const setCurrentGroupRow = async (group) => {
  await nextTick()
  if (groupTableRef.value) {
    groupTableRef.value.setCurrentRow(group || null)
  }
}

const resetBatchForm = () => {
  selectedAnswerIds.value = []
  batchScore.value = null
  batchComment.value = ''
}

const clearCurrentQuestion = async () => {
  currentGroup.value = null
  answers.value = []
  resetBatchForm()
  await setCurrentGroupRow(null)
}

const loadQuestionAnswers = async (group) => {
  if (!group) {
    await clearCurrentQuestion()
    return
  }
  answerLoading.value = true
  try {
    const data = await gradingPendingQuestionAnswersApi(group.questionId, group.examId)
    currentGroup.value = group
    answers.value = data || []
    resetBatchForm()
    await nextTick()
    answerTableRef.value?.clearSelection()
  } finally {
    answerLoading.value = false
  }
}

const loadGroups = async ({ preferredKey = '', fallbackIndex = 0, notifyWhenEmpty = false } = {}) => {
  groupLoading.value = true
  try {
    const data = await gradingPendingQuestionsApi()
    groups.value = normalizeGroups(data)

    if (!groups.value.length) {
      await clearCurrentQuestion()
      if (notifyWhenEmpty) {
        ElMessage.success('当前无待批题目')
      }
      return
    }

    const preferredGroup = preferredKey
      ? groups.value.find((item) => item.key === preferredKey)
      : null
    const fallbackGroup = groups.value[fallbackIndex] || null
    const nextGroup = preferredGroup || fallbackGroup || groups.value[0]

    await loadQuestionAnswers(nextGroup)
    await setCurrentGroupRow(nextGroup)
  } finally {
    groupLoading.value = false
  }
}

const reloadGroups = async () => {
  await loadGroups({
    preferredKey: currentGroupKey.value,
    fallbackIndex: Math.max(currentGroupIndex.value, 0)
  })
}

const handleGroupChange = async (group) => {
  if (!group || group.key === currentGroupKey.value) {
    return
  }
  await loadQuestionAnswers(group)
}

const handleSelectionChange = (selection) => {
  selectedAnswerIds.value = (selection || []).map((item) => item.submissionAnswerId)
}

const refreshCurrentQuestion = async () => {
  if (!currentGroup.value) {
    return
  }
  await loadQuestionAnswers(currentGroup.value)
}

const goToNextQuestion = async () => {
  if (!hasNextQuestion.value) {
    ElMessage.info('已经是最后一题')
    return
  }
  const nextGroup = groups.value[currentGroupIndex.value + 1]
  await loadQuestionAnswers(nextGroup)
  await setCurrentGroupRow(nextGroup)
}

const applyScoreToSelected = async () => {
  if (!canSubmit.value || !currentGroup.value) {
    return
  }
  submitting.value = true
  const preferredKey = currentGroupKey.value
  const fallbackIndex = Math.max(currentGroupIndex.value, 0)
  try {
    await gradingQuestionBatchScoreApi(currentGroup.value.questionId, {
      examId: currentGroup.value.examId,
      submissionAnswerIds: selectedAnswerIds.value,
      score: Number(batchScore.value),
      comment: batchComment.value?.trim() || ''
    })
    ElMessage.success('评分成功')
    await loadGroups({
      preferredKey,
      fallbackIndex,
      notifyWhenEmpty: true
    })
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadGroups()
})
</script>

<style scoped>
.grading-page {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 16px;
}

.panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-header h2,
.panel-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.panel-header p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.group-table {
  width: 100%;
}

.question-preview {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.question-text {
  color: #111827;
  line-height: 1.5;
}

.question-meta {
  color: #6b7280;
  font-size: 12px;
}

.question-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.info-block {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.info-label {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 6px;
}

.info-content {
  color: #111827;
  line-height: 1.6;
}

.multiline {
  white-space: pre-wrap;
  word-break: break-word;
}

.toolbar {
  display: grid;
  grid-template-columns: auto 160px minmax(220px, 1fr) auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.selected-count {
  color: #374151;
  font-size: 14px;
}

.answer-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

@media (max-width: 1080px) {
  .grading-page {
    grid-template-columns: 1fr;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .toolbar {
    grid-template-columns: 1fr;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
