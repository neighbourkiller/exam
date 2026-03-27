<template>
  <el-card class="page-card">
    <template #header>
      <div class="exam-header">
        <div>{{ state.examName }}</div>
        <div class="countdown">剩余: {{ secondsLeft }} 秒</div>
      </div>
    </template>

    <el-alert type="warning" show-icon title="切屏/失焦将被记录为防作弊告警" />

    <div class="question-list">
      <el-card v-for="q in state.questions" :key="q.questionId" class="q-item">
        <template #header>
          <div>第 {{ q.sortOrder }} 题 [{{ q.type }}] ({{ q.score }}分)</div>
        </template>
        <div class="q-content">{{ q.content }}</div>

        <div v-if="q.type === 'SINGLE' || q.type === 'MULTI'">
          <el-checkbox-group v-if="q.type === 'MULTI'" v-model="answers[q.questionId]">
            <el-checkbox v-for="opt in parseOptions(q.optionsJson)" :key="opt.label" :label="opt.label">{{ opt.label }}. {{ opt.value }}</el-checkbox>
          </el-checkbox-group>
          <el-radio-group v-else v-model="answers[q.questionId]">
            <el-radio v-for="opt in parseOptions(q.optionsJson)" :key="opt.label" :label="opt.label">{{ opt.label }}. {{ opt.value }}</el-radio>
          </el-radio-group>
        </div>

        <div v-else>
          <el-input v-model="answers[q.questionId]" type="textarea" :rows="2" placeholder="请输入答案" />
        </div>
      </el-card>
    </div>

    <el-button type="primary" size="large" @click="submit">交卷</el-button>
  </el-card>
</template>

<script setup>
import { onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { antiCheatApi, snapshotApi, startExamApi, submitExamApi } from '../../api'

const route = useRoute()
const router = useRouter()
const examId = String(route.params.id || '')

const state = reactive({
  examId,
  examName: '',
  questions: []
})
const answers = reactive({})
const secondsLeft = ref(0)
let timer = null
let snapshotTimer = null

const parseOptions = (json) => {
  if (!json) return []
  try {
    return JSON.parse(json)
  } catch {
    return []
  }
}

const normalizeAnswer = (q) => {
  const val = answers[q.questionId]
  if (Array.isArray(val)) {
    return val.join(',')
  }
  return val || ''
}

const sendSnapshot = async () => {
  const payload = {
    answers: state.questions.map((q) => ({
      questionId: q.questionId,
      answerText: normalizeAnswer(q)
    })),
    clientTimestamp: Date.now()
  }
  await snapshotApi(examId, payload)
}

const submit = async () => {
  await ElMessageBox.confirm('确认提交试卷？提交后不可修改。', '提示')
  const payload = {
    answers: state.questions.map((q) => ({
      questionId: q.questionId,
      answerText: normalizeAnswer(q)
    }))
  }
  const result = await submitExamApi(examId, payload)
  router.push({ path: '/student/result', query: { result: JSON.stringify(result) } })
}

const antiCheat = async (eventType, durationMs = 0) => {
  try {
    await antiCheatApi(examId, { eventType, durationMs, payload: '' })
  } catch {
    // ignore
  }
}

const onBlur = () => antiCheat('WINDOW_BLUR', 0)
const onVisibility = () => {
  if (document.hidden) antiCheat('TAB_HIDDEN', 0)
}

onMounted(async () => {
  const data = await startExamApi(examId)
  Object.assign(state, data)
  state.questions.forEach((q) => {
    answers[q.questionId] = q.currentAnswer || (q.type === 'MULTI' ? [] : '')
    if (q.type === 'MULTI' && typeof answers[q.questionId] === 'string' && answers[q.questionId]) {
      answers[q.questionId] = answers[q.questionId].split(',')
    }
  })

  const end = new Date(data.endTime).getTime()
  timer = setInterval(() => {
    secondsLeft.value = Math.max(0, Math.floor((end - Date.now()) / 1000))
    if (secondsLeft.value <= 0) {
      clearInterval(timer)
      submit()
    }
  }, 1000)

  snapshotTimer = setInterval(sendSnapshot, 15000)
  window.addEventListener('blur', onBlur)
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (snapshotTimer) clearInterval(snapshotTimer)
  window.removeEventListener('blur', onBlur)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<style scoped>
.exam-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
}

.countdown {
  color: #b91c1c;
}

.question-list {
  margin: 12px 0;
  display: grid;
  gap: 10px;
}

.q-item {
  border-radius: 10px;
}

.q-content {
  margin-bottom: 10px;
}
</style>
