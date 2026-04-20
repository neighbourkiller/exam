<template>
  <div class="exam-shell">
    <header class="exam-header">
      <div class="exam-header__meta">
        <p class="exam-header__eyebrow">在线考试</p>
        <h1>{{ state.examName || '考试进行中' }}</h1>
        <p class="exam-header__hint">答题过程中系统会自动保存快照，离开页面或失焦会记录防作弊事件。</p>
      </div>
      <div class="exam-header__status">
        <div class="status-pill">
          <span>剩余时间</span>
          <strong>{{ formattedTimeLeft }}</strong>
        </div>
        <div class="status-pill">
          <span>已答题数</span>
          <strong>{{ answeredCount }}/{{ state.questions.length }}</strong>
        </div>
      </div>
    </header>

    <div class="exam-banner">
      <el-alert
        :type="bannerState.type"
        show-icon
        :closable="false"
        :title="bannerState.title"
        :description="bannerState.description"
      />
    </div>

    <div class="exam-body">
      <aside class="exam-outline">
        <div class="outline-card">
          <div class="outline-toolbar">
            <el-switch
              v-model="showMarkedOnly"
              size="small"
              active-text="只看标记"
            />
          </div>
          <div class="outline-grid">
            <button
              v-for="q in outlineQuestions"
              :key="q.questionId"
              :class="[
                  'outline-item',
                {
                  'outline-item--done': isQuestionAnswered(q),
                  'outline-item--marked': isQuestionMarked(q),
                  'outline-item--active': isCurrentQuestion(q)
                }
              ]"
              type="button"
              @click="goToQuestion(q.questionId)"
            >
              <span class="outline-item__marker" aria-hidden="true"></span>
              <span class="outline-item__number">{{ q.sortOrder }}</span>
            </button>
          </div>
          <el-empty
            v-if="showMarkedOnly && !outlineQuestions.length"
            description="暂无标记题"
            :image-size="72"
          />
        </div>
      </aside>

      <section class="question-list">
        <article
          v-if="currentQuestion"
          :id="questionAnchorId(currentQuestion.questionId)"
          :key="currentQuestion.questionId"
          class="q-item"
        >
          <div class="q-item__head">
            <div>
              <div class="q-item__index">第 {{ currentQuestion.sortOrder }} 题</div>
              <div class="q-item__meta">{{ typeLabelMap[currentQuestion.type] || currentQuestion.type || '题目' }} · {{ currentQuestion.score }} 分</div>
            </div>
            <div class="q-item__actions">
              <el-button
                :type="isQuestionMarked(currentQuestion) ? 'warning' : 'info'"
                :plain="!isQuestionMarked(currentQuestion)"
                size="small"
                @click="toggleQuestionMark(currentQuestion)"
              >
                {{ isQuestionMarked(currentQuestion) ? '取消标记' : '标记' }}
              </el-button>
              <div :class="['q-item__status', { 'q-item__status--done': isQuestionAnswered(currentQuestion) }]">
                {{ isQuestionAnswered(currentQuestion) ? '已作答' : '待作答' }}
              </div>
            </div>
          </div>

          <div class="q-content">{{ currentQuestion.content }}</div>

          <div v-if="questionImages(currentQuestion).length" class="q-image-list">
            <figure
              v-for="asset in questionImages(currentQuestion)"
              :key="asset.assetId || asset.url"
              class="q-image-frame"
            >
              <el-image
                :src="asset.url"
                :preview-src-list="questionImages(currentQuestion).map(item => item.url)"
                fit="contain"
                class="q-image"
              />
            </figure>
          </div>

          <div class="q-answer">
            <template v-if="currentQuestion.type === 'MULTI'">
              <el-checkbox-group v-model="answers[currentQuestion.questionId]" class="option-list">
                <el-checkbox
                  v-for="opt in parseOptions(currentQuestion)"
                  :key="`${currentQuestion.questionId}-${opt.label}`"
                  :label="opt.label"
                  class="option-item"
                >
                  <span class="option-marker">{{ opt.label }}</span>
                  <span>{{ displayOptionText(currentQuestion, opt) }}</span>
                </el-checkbox>
              </el-checkbox-group>
            </template>

            <template v-else-if="currentQuestion.type === 'SINGLE' || currentQuestion.type === 'JUDGE'">
              <el-radio-group v-model="answers[currentQuestion.questionId]" class="option-list">
                <el-radio
                  v-for="opt in parseOptions(currentQuestion)"
                  :key="`${currentQuestion.questionId}-${opt.label}`"
                  :label="opt.label"
                  class="option-item"
                >
                  <span class="option-marker">{{ opt.label }}</span>
                  <span>{{ displayOptionText(currentQuestion, opt) }}</span>
                </el-radio>
              </el-radio-group>
            </template>

            <template v-else>
              <el-input
                v-model="answers[currentQuestion.questionId]"
                type="textarea"
                :rows="3"
                resize="none"
                placeholder="请输入答案"
              />
            </template>
          </div>

          <div class="q-navigation">
            <el-button size="large" :disabled="isFirstVisibleQuestion" @click="goToPreviousQuestion">
              上一题
            </el-button>
            <span class="q-navigation__progress">{{ currentQuestionPosition }}/{{ visibleQuestions.length }}</span>
            <el-button size="large" type="primary" :disabled="isLastVisibleQuestion" @click="goToNextQuestion">
              下一题
            </el-button>
          </div>
        </article>
        <el-empty
          v-else-if="showMarkedOnly && !visibleQuestions.length"
          description="暂无标记题"
        />
      </section>
    </div>

    <footer class="exam-footer">
      <div class="exam-footer__summary">
        <span>共 {{ state.questions.length }} 题</span>
        <span>已完成 {{ answeredCount }} 题</span>
        <span>已标记 {{ markedCount }} 题</span>
      </div>
      <el-button type="primary" size="large" @click="submit">交卷</el-button>
    </footer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { antiCheatApi, snapshotApi, startExamApi, submitExamApi } from '../../api'
import { useAuthStore } from '../../stores/auth'
import { clearDraft, loadDraft, saveDraft } from '../../utils/examDraftStore'
import { parseDateTime } from '../../utils/datetime'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const examId = String(route.params.id || '')

const state = reactive({
  examId,
  examName: '',
  questions: []
})
const answers = reactive({})
const markedQuestionIds = ref([])
const showMarkedOnly = ref(false)
const currentQuestionId = ref('')
const secondsLeft = ref(null)
const examEndAt = ref(null)
const blurStartedAt = ref(null)
const hiddenStartedAt = ref(null)
const offlineStartedAt = ref(null)
const bannerState = reactive({
  type: 'warning',
  title: '考试页面处于监考模式',
  description: '切屏、退出全屏、离线、复制粘贴等异常行为会被记录，但不会阻断你当前答题。'
})
const typeLabelMap = {
  SINGLE: '单选题',
  MULTI: '多选题',
  JUDGE: '判断题',
  BLANK: '填空题',
  SHORT: '简答题'
}
let timer = null
let snapshotTimer = null
let bannerResetTimer = null
let draftSaveTimer = null
let pendingDraftDirty = false
const recentEventTimes = new Map()
const syncState = reactive({
  userId: null,
  initialized: false,
  dirty: false,
  syncing: false,
  updatedAt: 0,
  lastSyncedAt: null
})

const answeredCount = computed(() =>
  state.questions.filter((q) => isQuestionAnswered(q)).length
)

const markedQuestionIdSet = computed(() => new Set(markedQuestionIds.value))
const markedCount = computed(() => markedQuestionIds.value.length)
const visibleQuestions = computed(() =>
  showMarkedOnly.value
    ? state.questions.filter((q) => isQuestionMarked(q))
    : state.questions
)
const outlineQuestions = computed(() => visibleQuestions.value)
const currentQuestionIndex = computed(() =>
  visibleQuestions.value.findIndex((question) => toStoredQuestionId(question.questionId) === currentQuestionId.value)
)
const normalizedCurrentQuestionIndex = computed(() => {
  if (!visibleQuestions.value.length) {
    return -1
  }
  return currentQuestionIndex.value >= 0 ? currentQuestionIndex.value : 0
})
const currentQuestion = computed(() =>
  normalizedCurrentQuestionIndex.value < 0
    ? null
    : visibleQuestions.value[normalizedCurrentQuestionIndex.value]
)
const currentQuestionPosition = computed(() =>
  normalizedCurrentQuestionIndex.value < 0 ? 0 : normalizedCurrentQuestionIndex.value + 1
)
const isFirstVisibleQuestion = computed(() => normalizedCurrentQuestionIndex.value <= 0)
const isLastVisibleQuestion = computed(() =>
  normalizedCurrentQuestionIndex.value < 0
    || normalizedCurrentQuestionIndex.value >= visibleQuestions.value.length - 1
)

const formattedTimeLeft = computed(() => {
  if (!Number.isFinite(secondsLeft.value)) {
    return '--:--:--'
  }
  const total = Math.max(0, secondsLeft.value)
  const hours = String(Math.floor(total / 3600)).padStart(2, '0')
  const minutes = String(Math.floor((total % 3600) / 60)).padStart(2, '0')
  const seconds = String(total % 60).padStart(2, '0')
  return `${hours}:${minutes}:${seconds}`
})

const questionAnchorId = (questionId) => `question-${questionId}`

const judgeFallbackOptions = [
  { label: 'A', value: 'true' },
  { label: 'B', value: 'false' }
]

const parseOptions = (question) => {
  if (!question?.optionsJson) {
    return question?.type === 'JUDGE' ? judgeFallbackOptions : []
  }
  try {
    const parsed = JSON.parse(question.optionsJson)
    if (!Array.isArray(parsed)) {
      return []
    }
    const normalized = parsed.map((item, index) => ({
      label: item?.label || String.fromCharCode(65 + index),
      value: item?.value == null ? '' : String(item.value)
    }))
    return normalized.filter((item) => item.value)
  } catch {
    return question?.type === 'JUDGE' ? judgeFallbackOptions : []
  }
}

const displayOptionText = (question, option) => {
  if (question?.type !== 'JUDGE') {
    return option.value
  }
  if (option.value === 'true') {
    return '正确'
  }
  if (option.value === 'false') {
    return '错误'
  }
  return option.value
}

const isImageAsset = (asset) => {
  if (!asset?.url) {
    return false
  }
  const fileType = String(asset.fileType || '').toUpperCase()
  if (fileType === 'IMAGE') {
    return true
  }
  return /\.(png|jpe?g|gif|webp|bmp|svg)(?:[?#].*)?$/i.test(String(asset.url))
}

const questionImages = (question) =>
  Array.isArray(question?.assets)
    ? question.assets.filter(isImageAsset)
    : []

const toStoredQuestionId = (questionId) => String(questionId)

const buildValidQuestionIdSet = () =>
  new Set(state.questions.map((question) => toStoredQuestionId(question.questionId)))

const normalizeMarkedQuestionIds = (ids = []) => {
  const validQuestionIds = buildValidQuestionIdSet()
  if (!Array.isArray(ids) || !validQuestionIds.size) {
    return []
  }
  return [...new Set(ids.map((item) => String(item)).filter((item) => validQuestionIds.has(item)))]
}

const normalizeAnswerInputValue = (question, value) => {
  if (question?.type === 'MULTI') {
    if (Array.isArray(value)) {
      return value.map((item) => String(item)).filter((item) => item)
    }
    if (typeof value === 'string' && value) {
      return value.split(',').map((item) => item.trim()).filter((item) => item)
    }
    return []
  }
  if (value == null) {
    return ''
  }
  return String(value)
}

const isQuestionAnswered = (question) => {
  const value = answers[question.questionId]
  if (Array.isArray(value)) {
    return value.length > 0
  }
  return String(value || '').trim().length > 0
}

const isQuestionMarked = (question) =>
  markedQuestionIdSet.value.has(toStoredQuestionId(question?.questionId))

const isCurrentQuestion = (question) =>
  toStoredQuestionId(question?.questionId) === toStoredQuestionId(currentQuestion.value?.questionId)

const applyMarkedQuestionIds = (ids = []) => {
  markedQuestionIds.value = normalizeMarkedQuestionIds(ids)
  if (showMarkedOnly.value && !markedQuestionIds.value.length) {
    showMarkedOnly.value = false
  }
}

const buildMarkedQuestionIdsFromState = () => normalizeMarkedQuestionIds(markedQuestionIds.value)

const toggleQuestionMark = (question) => {
  const questionId = toStoredQuestionId(question?.questionId)
  if (!questionId) {
    return
  }
  if (markedQuestionIdSet.value.has(questionId)) {
    markedQuestionIds.value = markedQuestionIds.value.filter((item) => item !== questionId)
  } else {
    markedQuestionIds.value = [...markedQuestionIds.value, questionId]
  }
  if (showMarkedOnly.value && !markedQuestionIds.value.length) {
    showMarkedOnly.value = false
  }
  scheduleDraftSave({ dirty: false })
}

const goToQuestion = (questionId) => {
  const normalizedQuestionId = toStoredQuestionId(questionId)
  if (!visibleQuestions.value.some((question) => toStoredQuestionId(question.questionId) === normalizedQuestionId)) {
    return
  }
  currentQuestionId.value = normalizedQuestionId
}

const goToPreviousQuestion = () => {
  if (isFirstVisibleQuestion.value) {
    return
  }
  const previousQuestion = visibleQuestions.value[normalizedCurrentQuestionIndex.value - 1]
  if (previousQuestion) {
    goToQuestion(previousQuestion.questionId)
  }
}

const goToNextQuestion = () => {
  if (isLastVisibleQuestion.value) {
    return
  }
  const nextQuestion = visibleQuestions.value[normalizedCurrentQuestionIndex.value + 1]
  if (nextQuestion) {
    goToQuestion(nextQuestion.questionId)
  }
}

const normalizeAnswer = (q) => {
  const val = answers[q.questionId]
  if (Array.isArray(val)) {
    return val.join(',')
  }
  return val || ''
}

const buildAnswerMapFromState = () => {
  const map = {}
  state.questions.forEach((question) => {
    map[toStoredQuestionId(question.questionId)] = normalizeAnswerInputValue(question, answers[question.questionId])
  })
  return map
}

const buildAnswerMapFromQuestions = (questions = []) => {
  const map = {}
  questions.forEach((question) => {
    map[toStoredQuestionId(question.questionId)] = normalizeAnswerInputValue(question, question.currentAnswer)
  })
  return map
}

const applyAnswerMap = (answerMap = {}) => {
  state.questions.forEach((question) => {
    const rawValue = answerMap?.[question.questionId] ?? answerMap?.[toStoredQuestionId(question.questionId)]
    answers[question.questionId] = normalizeAnswerInputValue(question, rawValue)
  })
}

const buildSubmitPayload = () => ({
  answers: state.questions.map((q) => ({
    questionId: q.questionId,
    answerText: normalizeAnswer(q)
  }))
})

const hasDraftContent = (answerMap = {}) =>
  state.questions.some((question) => {
    const value = answerMap?.[question.questionId] ?? answerMap?.[toStoredQuestionId(question.questionId)]
    if (Array.isArray(value)) {
      return value.length > 0
    }
    return String(value || '').trim().length > 0
  })

const resetBanner = () => {
  bannerState.type = 'warning'
  bannerState.title = '考试页面处于监考模式'
  bannerState.description = '切屏、退出全屏、离线、复制粘贴等异常行为会被记录，但不会阻断你当前答题。'
}

const showBanner = (type, title, description, sticky = false) => {
  bannerState.type = type
  bannerState.title = title
  bannerState.description = description
  if (bannerResetTimer) {
    clearTimeout(bannerResetTimer)
    bannerResetTimer = null
  }
  if (!sticky) {
    bannerResetTimer = setTimeout(() => {
      resetBanner()
      bannerResetTimer = null
    }, 8000)
  }
}

const showRecoveryBanner = (mode) => {
  if (mode === 'local') {
    showBanner('success', '已恢复本地草稿', '检测到本机保存的较新答题进度，系统已优先恢复，并会在联网后自动同步。')
    return
  }
  if (mode === 'server') {
    showBanner('success', '已恢复服务端草稿', '检测到服务器上的答题进度，系统已自动恢复到你上次保存的位置。')
    return
  }
  if (mode === 'resumed') {
    showBanner('info', '已恢复考试会话', '你之前已经进入过本场考试，当前会话已继续，可直接接着作答。')
    return
  }
  showBanner('info', '已开始答题', '本地草稿保存已开启。网络中断时会继续在本机保存，恢复网络后自动同步。')
}

const formatDuration = (durationMs) => {
  if (!durationMs || durationMs <= 0) {
    return '不足1秒'
  }
  const totalSeconds = Math.ceil(durationMs / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (!minutes) {
    return `${seconds}秒`
  }
  return `${minutes}分${seconds}秒`
}

const recordRecentEvent = (eventType) => {
  const now = Date.now()
  const recent = recentEventTimes.get(eventType) || []
  const filtered = recent.filter((time) => now - time < 10 * 60 * 1000)
  filtered.push(now)
  recentEventTimes.set(eventType, filtered)
  return filtered.length
}

const buildAntiCheatPayload = (extra = {}) => JSON.stringify({
  path: window.location.pathname,
  visibilityState: document.visibilityState,
  online: navigator.onLine,
  fullscreen: Boolean(document.fullscreenElement),
  timestamp: Date.now(),
  ...extra
})

const warningMessageForEvent = (eventType, durationMs) => {
  switch (eventType) {
    case 'WINDOW_BLUR':
      return `检测到窗口离开 ${formatDuration(durationMs)}，系统已记录。`
    case 'TAB_HIDDEN':
      return `检测到页面隐藏 ${formatDuration(durationMs)}，系统已记录。`
    case 'FULLSCREEN_EXIT':
      return '检测到你已退出全屏模式，请尽快恢复。'
    case 'COPY_ATTEMPT':
      return '检测到复制操作，系统已记录。'
    case 'PASTE_ATTEMPT':
      return '检测到粘贴操作，系统已记录。'
    case 'CUT_ATTEMPT':
      return '检测到剪切操作，系统已记录。'
    case 'CONTEXT_MENU':
      return '检测到右键菜单操作，系统已记录。'
    case 'NETWORK_OFFLINE':
      return `网络中断 ${formatDuration(durationMs)}，系统已记录。`
    default:
      return '检测到异常考试行为，系统已记录。'
  }
}

const reportAntiCheatEvent = async (eventType, durationMs = 0, extraPayload = {}) => {
  const eventCount = recordRecentEvent(eventType)
  const escalated = eventCount >= 3
    || durationMs > 30_000
    || eventType === 'FULLSCREEN_EXIT'
    || (eventType === 'NETWORK_OFFLINE' && durationMs > 10_000)
  const message = warningMessageForEvent(eventType, durationMs)
  showBanner(
    escalated ? 'error' : 'warning',
    escalated ? '监考风险已升级' : '监考事件已记录',
    `${message}${escalated ? ' 当前异常频率较高，请立即恢复正常答题状态。' : ''}`
  )
  if (escalated) {
    ElMessage.error(message)
  } else {
    ElMessage.warning(message)
  }
  try {
    await antiCheatApi(examId, {
      eventType,
      durationMs,
      payload: buildAntiCheatPayload({
        eventCount,
        ...extraPayload
      })
    })
  } catch {
    // ignore
  }
}

const resolveExamEndTime = (data) => {
  const deadline = parseDateTime(data?.deadlineTime)
  if (deadline) {
    return deadline
  }

  const explicitEnd = parseDateTime(data?.endTime)
  if (explicitEnd) {
    return explicitEnd
  }

  const start = parseDateTime(data?.startTime)
  const durationMinutes = Number(data?.durationMinutes)
  if (start && Number.isFinite(durationMinutes) && durationMinutes > 0) {
    return new Date(start.getTime() + durationMinutes * 60 * 1000)
  }

  return null
}

const syncCountdown = () => {
  if (!(examEndAt.value instanceof Date) || Number.isNaN(examEndAt.value.getTime())) {
    secondsLeft.value = null
    return
  }
  secondsLeft.value = Math.max(0, Math.floor((examEndAt.value.getTime() - Date.now()) / 1000))
}

const persistDraftState = async ({
  updatedAt = Date.now(),
  lastSyncedAt = syncState.lastSyncedAt,
  dirty = true,
  answersMap = buildAnswerMapFromState(),
  markedIds = buildMarkedQuestionIdsFromState()
} = {}) => {
  if (!syncState.userId) {
    return null
  }
  syncState.updatedAt = updatedAt
  syncState.lastSyncedAt = lastSyncedAt ?? null
  syncState.dirty = Boolean(dirty)
  return saveDraft({
    userId: syncState.userId,
    examId,
    answers: answersMap,
    markedQuestionIds: markedIds,
    updatedAt: syncState.updatedAt,
    lastSyncedAt: syncState.lastSyncedAt,
    dirty: syncState.dirty
  })
}

const scheduleDraftSave = ({ dirty = true } = {}) => {
  if (!syncState.userId) {
    return
  }
  pendingDraftDirty = pendingDraftDirty || dirty
  if (draftSaveTimer) {
    clearTimeout(draftSaveTimer)
  }
  draftSaveTimer = setTimeout(() => {
    draftSaveTimer = null
    void persistDraftState({
      updatedAt: Date.now(),
      lastSyncedAt: syncState.lastSyncedAt,
      dirty: pendingDraftDirty ? true : syncState.dirty
    })
    pendingDraftDirty = false
  }, 400)
}

const flushDraftSave = () => {
  if (!draftSaveTimer) {
    return
  }
  clearTimeout(draftSaveTimer)
  draftSaveTimer = null
  void persistDraftState({
    updatedAt: Date.now(),
    lastSyncedAt: syncState.lastSyncedAt,
    dirty: pendingDraftDirty ? true : syncState.dirty
  })
  pendingDraftDirty = false
}

const syncDirtyDraft = async ({ force = false, notify = false } = {}) => {
  if (!syncState.userId || !syncState.initialized || syncState.syncing) {
    return false
  }
  if (!navigator.onLine) {
    return false
  }
  if (!force && !syncState.dirty) {
    return false
  }

  syncState.syncing = true
  const syncVersion = syncState.updatedAt || Date.now()
  try {
    await snapshotApi(examId, {
      ...buildSubmitPayload(),
      clientTimestamp: syncVersion
    })

    const latestUpdatedAt = syncState.updatedAt || syncVersion
    await persistDraftState({
      updatedAt: latestUpdatedAt,
      lastSyncedAt: syncVersion,
      dirty: latestUpdatedAt > syncVersion
    })

    if (notify) {
      ElMessage.success('答题进度已同步到服务器')
    }
    return true
  } catch {
    return false
  } finally {
    syncState.syncing = false
  }
}

const submit = async (needConfirm = true) => {
  if (!navigator.onLine) {
    await persistDraftState({
      updatedAt: Date.now(),
      lastSyncedAt: syncState.lastSyncedAt,
      dirty: true
    })
    ElMessage.warning('当前网络已断开，答案已保存在本地草稿。请恢复网络后再交卷。')
    return
  }
  if (needConfirm) {
    await ElMessageBox.confirm('确认提交试卷？提交后不可修改。', '提示')
  }
  await submitExamApi(examId, buildSubmitPayload())
  if (draftSaveTimer) {
    clearTimeout(draftSaveTimer)
    draftSaveTimer = null
  }
  if (syncState.userId) {
    await clearDraft(syncState.userId, examId)
  }
  syncState.dirty = false
  try {
    await ElMessageBox.confirm('试卷已提交，系统正在处理成绩。是否前往考试结果中心查看成绩状态？', '提交成功', {
      confirmButtonText: '查看考试结果',
      cancelButtonText: '返回我的考试',
      type: 'success',
      distinguishCancelAndClose: true,
      closeOnClickModal: false,
      closeOnPressEscape: false,
      showClose: false
    })
    router.push('/student/results')
  } catch (action) {
    if (action === 'cancel') {
      router.push('/student/exams')
    }
  }
}

const tryEnterFullscreen = async () => {
  if (!document.fullscreenEnabled || document.fullscreenElement) {
    return
  }
  try {
    await document.documentElement.requestFullscreen()
    showBanner('success', '已进入考试全屏模式', '建议保持全屏完成答题，退出全屏会被记录为监考异常。')
  } catch {
    showBanner('info', '浏览器未进入全屏', '已继续考试，建议你手动进入全屏以减少误触和切屏风险。')
  }
}

const onBlur = () => {
  if (blurStartedAt.value == null) {
    blurStartedAt.value = Date.now()
  }
}

const onFocus = () => {
  if (blurStartedAt.value == null) {
    return
  }
  const durationMs = Date.now() - blurStartedAt.value
  blurStartedAt.value = null
  reportAntiCheatEvent('WINDOW_BLUR', durationMs, { recovered: true })
}

const onVisibility = () => {
  if (document.hidden) {
    if (hiddenStartedAt.value == null) {
      hiddenStartedAt.value = Date.now()
    }
    return
  }
  if (hiddenStartedAt.value == null) {
    return
  }
  const durationMs = Date.now() - hiddenStartedAt.value
  hiddenStartedAt.value = null
  reportAntiCheatEvent('TAB_HIDDEN', durationMs, { recovered: true })
}

const onFullscreenChange = () => {
  if (!document.fullscreenElement) {
    reportAntiCheatEvent('FULLSCREEN_EXIT', 0, { mode: 'browser-fullscreen' })
  }
}

const onCopy = (event) => {
  reportAntiCheatEvent('COPY_ATTEMPT', 0, { targetTag: event.target?.tagName || null })
}

const onPaste = (event) => {
  reportAntiCheatEvent('PASTE_ATTEMPT', 0, { targetTag: event.target?.tagName || null })
}

const onCut = (event) => {
  reportAntiCheatEvent('CUT_ATTEMPT', 0, { targetTag: event.target?.tagName || null })
}

const onContextMenu = (event) => {
  reportAntiCheatEvent('CONTEXT_MENU', 0, { targetTag: event.target?.tagName || null })
}

const onOffline = () => {
  if (offlineStartedAt.value == null) {
    offlineStartedAt.value = Date.now()
  }
  showBanner('warning', '网络连接已断开', '请尽快恢复网络。页面会尽量保留当前内容，恢复后会记录本次离线时长。', true)
}

const onOnline = () => {
  if (offlineStartedAt.value == null) {
    resetBanner()
    return
  }
  const durationMs = Date.now() - offlineStartedAt.value
  offlineStartedAt.value = null
  reportAntiCheatEvent('NETWORK_OFFLINE', durationMs, { recovered: true })
  const shouldNotify = syncState.dirty
  void syncDirtyDraft({ force: shouldNotify, notify: shouldNotify })
}

const onBeforeUnload = () => {
  flushDraftSave()
}

const bootstrapExam = async () => {
  const data = await startExamApi(examId)
  state.examId = String(data?.examId || examId)
  state.examName = data?.examName || ''
  state.questions = Array.isArray(data?.questions) ? data.questions : []

  syncState.userId = auth.userId == null ? null : String(auth.userId)
  syncState.initialized = false

  const serverAnswerMap = buildAnswerMapFromQuestions(state.questions)
  const serverDraftTime = parseDateTime(data?.draftUpdatedAt)?.getTime() || 0
  const hasServerDraft = serverDraftTime > 0 || hasDraftContent(serverAnswerMap)
  const localDraft = syncState.userId ? await loadDraft(syncState.userId, examId) : null

  let selectedAnswers = serverAnswerMap
  let selectedMode = 'new'
  let draftUpdatedAt = serverDraftTime || Date.now()
  let lastSyncedAt = serverDraftTime || Date.now()
  let dirty = false
  let selectedMarkedQuestionIds = []

  if (localDraft && Number(localDraft.updatedAt || 0) > serverDraftTime) {
    selectedAnswers = localDraft.answers || {}
    selectedMarkedQuestionIds = localDraft.markedQuestionIds || []
    selectedMode = 'local'
    draftUpdatedAt = Number(localDraft.updatedAt) || Date.now()
    lastSyncedAt = Number(localDraft.lastSyncedAt || 0) || null
    dirty = Boolean(localDraft.dirty) || !lastSyncedAt || draftUpdatedAt > lastSyncedAt
  } else if (hasServerDraft) {
    selectedMode = 'server'
    draftUpdatedAt = serverDraftTime || Date.now()
    lastSyncedAt = serverDraftTime || Date.now()
  } else if (data?.resumed) {
    selectedMode = 'resumed'
    draftUpdatedAt = Date.now()
    lastSyncedAt = Date.now()
  }

  applyAnswerMap(selectedAnswers)
  applyMarkedQuestionIds(selectedMarkedQuestionIds)
  await persistDraftState({
    updatedAt: draftUpdatedAt,
    lastSyncedAt,
    dirty
  })
  syncState.initialized = true

  examEndAt.value = resolveExamEndTime(data)
  syncCountdown()
  timer = setInterval(() => {
    syncCountdown()
    if (Number.isFinite(secondsLeft.value) && secondsLeft.value <= 0) {
      clearInterval(timer)
      submit(false)
    }
  }, 1000)

  snapshotTimer = setInterval(() => {
    void syncDirtyDraft()
  }, 15000)
  window.addEventListener('blur', onBlur)
  window.addEventListener('focus', onFocus)
  window.addEventListener('offline', onOffline)
  window.addEventListener('online', onOnline)
  window.addEventListener('beforeunload', onBeforeUnload)
  document.addEventListener('visibilitychange', onVisibility)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('copy', onCopy)
  document.addEventListener('paste', onPaste)
  document.addEventListener('cut', onCut)
  document.addEventListener('contextmenu', onContextMenu)

  showRecoveryBanner(selectedMode)
  if (selectedMode === 'local' && navigator.onLine) {
    void syncDirtyDraft({ force: true })
  }
  await tryEnterFullscreen()
}

watch(answers, () => {
  if (!syncState.initialized || !state.questions.length) {
    return
  }
  scheduleDraftSave({ dirty: true })
}, { deep: true })

watch(visibleQuestions, (questions) => {
  if (!questions.length) {
    currentQuestionId.value = ''
    return
  }
  const hasCurrentQuestion = questions.some((question) =>
    toStoredQuestionId(question.questionId) === currentQuestionId.value
  )
  if (!hasCurrentQuestion) {
    currentQuestionId.value = toStoredQuestionId(questions[0].questionId)
  }
}, { immediate: true })

onMounted(async () => {
  await bootstrapExam()
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (snapshotTimer) clearInterval(snapshotTimer)
  if (bannerResetTimer) clearTimeout(bannerResetTimer)
  flushDraftSave()
  window.removeEventListener('blur', onBlur)
  window.removeEventListener('focus', onFocus)
  window.removeEventListener('offline', onOffline)
  window.removeEventListener('online', onOnline)
  window.removeEventListener('beforeunload', onBeforeUnload)
  document.removeEventListener('visibilitychange', onVisibility)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('copy', onCopy)
  document.removeEventListener('paste', onPaste)
  document.removeEventListener('cut', onCut)
  document.removeEventListener('contextmenu', onContextMenu)
})
</script>

<style scoped>
.exam-shell {
  height: 100%;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-main);
}

.exam-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  padding: 24px 32px 18px;
  color: white;
  background: linear-gradient(135deg, var(--brand) 0%, var(--brand-hover) 100%);
  box-shadow: var(--shadow-soft);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.exam-header__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.8);
}

.exam-header h1 {
  margin: 0;
  font-size: clamp(24px, 3vw, 36px);
  line-height: 1.1;
  font-weight: 800;
}

.exam-header__hint {
  max-width: 760px;
  margin: 10px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
}

.exam-header__status {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.status-pill {
  min-width: 140px;
  padding: 12px 20px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 100px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.status-pill span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
}

.status-pill strong {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 1px;
}

.exam-banner {
  padding: 20px 32px 0;
}

.exam-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 24px;
  padding: 20px 32px 32px;
}

.exam-outline {
  position: sticky;
  top: 130px;
  align-self: start;
}

.outline-card {
  padding: 12px 10px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: var(--shadow-soft);
}

.outline-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: 13px;
}

.outline-grid {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 7px;
  max-height: min(54vh, 520px);
  overflow-y: auto;
  padding: 2px 4px 2px 0;
}

.outline-item {
  position: relative;
  width: 100%;
  height: 36px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #64748b;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.outline-item:hover {
  color: var(--brand);
  background: rgba(13, 148, 136, 0.06);
}

.outline-item__marker {
  position: absolute;
  top: 0;
  left: calc(50% - 16px);
  z-index: 1;
  width: 9px;
  height: 13px;
  border-radius: 2px 2px 1px 1px;
  background: #f59e0b;
  opacity: 0;
}

.outline-item__marker::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -4px;
  border-top: 4px solid #f59e0b;
  border-right: 4px solid transparent;
}

.outline-item__number {
  width: 30px;
  height: 30px;
  border: 1px solid rgba(13, 148, 136, 0.24);
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.7);
  font-variant-numeric: tabular-nums;
  color: inherit;
  transition: all var(--transition-fast);
}

.outline-item--done {
  background: transparent;
  color: white;
}

.outline-item--done .outline-item__number {
  border-color: transparent;
  background: linear-gradient(135deg, var(--brand), var(--brand-hover));
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.18);
}

.outline-item--marked {
  background: transparent;
}

.outline-item--marked .outline-item__marker {
  opacity: 1;
}

.outline-item--done.outline-item--marked {
  box-shadow: none;
}

.outline-item--marked .outline-item__number {
  border-color: #f59e0b;
}

.outline-item--active .outline-item__number {
  outline: 3px solid rgba(15, 118, 110, 0.18);
}

.question-list {
  min-width: 0;
  display: grid;
  gap: 24px;
  align-content: start;
  padding-right: 4px;
}

.q-item {
  padding: 32px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: var(--shadow-soft);
  transition: transform var(--transition-smooth), box-shadow var(--transition-smooth);
}

.q-item:hover {
  box-shadow: var(--shadow-hover);
  transform: translateY(-2px);
}

.q-item__head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(13, 148, 136, 0.1);
}

.q-item__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.q-item__index {
  font-size: 22px;
  font-weight: 800;
  color: var(--brand);
}

.q-item__meta {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-muted);
  background: rgba(13, 148, 136, 0.1);
  padding: 4px 10px;
  border-radius: 100px;
  display: inline-block;
}

.q-item__status {
  padding: 6px 14px;
  border-radius: 100px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid rgba(194, 65, 12, 0.2);
}

.q-item__status--done {
  background: #ecfdf5;
  color: #047857;
  border-color: rgba(4, 120, 87, 0.2);
}

.q-content {
  font-size: 16px;
  line-height: 1.8;
  color: var(--text-main);
}

.q-image-list {
  margin-top: 20px;
  display: grid;
  gap: 16px;
}

.q-image-frame {
  margin: 0;
  padding: 12px;
  border: 1px solid rgba(13, 148, 136, 0.1);
  border-radius: var(--radius-md);
  background: white;
}

.q-image {
  width: 100%;
  height: clamp(220px, 34vw, 420px);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.q-answer {
  margin-top: 28px;
}

.q-navigation {
  margin-top: 34px;
  padding-top: 22px;
  border-top: 1px solid rgba(13, 148, 136, 0.1);
  display: grid;
  grid-template-columns: minmax(120px, auto) 1fr minmax(120px, auto);
  align-items: center;
  gap: 16px;
}

.q-navigation__progress {
  justify-self: center;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.option-list {
  width: 100%;
  display: grid;
  gap: 14px;
}

.option-item {
  margin: 0 !important;
  padding: 16px 20px;
  border: 2px solid transparent;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.6);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02);
}

.option-item:hover {
  transform: translateX(4px);
  background: white;
  border-color: rgba(13, 148, 136, 0.3);
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.08);
}

:deep(.el-radio.is-checked),
:deep(.el-checkbox.is-checked) {
  background: var(--brand-light) !important;
  border-color: var(--brand) !important;
  transform: translateX(4px);
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.15);
}

.option-marker {
  display: inline-flex;
  width: 32px;
  height: 32px;
  margin-right: 16px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(13, 148, 136, 0.1);
  color: var(--brand);
  font-weight: 800;
  transition: all 0.2s;
}

:deep(.is-checked) .option-marker {
  background: var(--brand);
  color: white;
}

.exam-footer {
  position: sticky;
  bottom: 0;
  z-index: 15;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px 32px;
  border-top: 1px solid rgba(13, 148, 136, 0.1);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow: 0 -10px 30px rgba(13, 148, 136, 0.05);
}

.exam-footer__summary {
  display: flex;
  gap: 20px;
  color: var(--text-muted);
  font-size: 15px;
}

@media (max-width: 1100px) {
  .exam-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .exam-body {
    grid-template-columns: 1fr;
  }
  .exam-outline {
    position: static;
  }
}

@media (max-width: 768px) {
  .exam-header,
  .exam-banner,
  .exam-body,
  .exam-footer {
    padding-left: 16px;
    padding-right: 16px;
  }
  .status-pill {
    min-width: 0;
    flex: 1;
  }
  .q-item {
    padding: 20px;
  }
  .q-item__head,
  .q-item__actions {
    align-items: flex-start;
    flex-direction: column;
  }
  .q-navigation {
    grid-template-columns: 1fr;
  }
  .q-navigation__progress {
    order: -1;
  }
}
</style>
