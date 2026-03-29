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
          <div class="outline-card__title">答题进度</div>
          <div class="outline-grid">
            <button
              v-for="q in state.questions"
              :key="q.questionId"
              :class="['outline-item', { 'outline-item--done': isQuestionAnswered(q) }]"
              type="button"
              @click="scrollToQuestion(q.questionId)"
            >
              {{ q.sortOrder }}
            </button>
          </div>
          <p class="outline-tip">绿色表示已作答，可点击题号快速定位。</p>
        </div>
      </aside>

      <section class="question-list">
        <article
          v-for="q in state.questions"
          :id="questionAnchorId(q.questionId)"
          :key="q.questionId"
          class="q-item"
        >
          <div class="q-item__head">
            <div>
              <div class="q-item__index">第 {{ q.sortOrder }} 题</div>
              <div class="q-item__meta">{{ typeLabelMap[q.type] || q.type || '题目' }} · {{ q.score }} 分</div>
            </div>
            <div :class="['q-item__status', { 'q-item__status--done': isQuestionAnswered(q) }]">
              {{ isQuestionAnswered(q) ? '已作答' : '待作答' }}
            </div>
          </div>

          <div class="q-content">{{ q.content }}</div>

          <div v-if="questionImages(q).length" class="q-image-list">
            <figure
              v-for="asset in questionImages(q)"
              :key="asset.assetId || asset.url"
              class="q-image-frame"
            >
              <el-image
                :src="asset.url"
                :preview-src-list="questionImages(q).map(item => item.url)"
                fit="contain"
                class="q-image"
              />
            </figure>
          </div>

          <div class="q-answer">
            <template v-if="q.type === 'MULTI'">
              <el-checkbox-group v-model="answers[q.questionId]" class="option-list">
                <el-checkbox
                  v-for="opt in parseOptions(q)"
                  :key="`${q.questionId}-${opt.label}`"
                  :label="opt.label"
                  class="option-item"
                >
                  <span class="option-marker">{{ opt.label }}</span>
                  <span>{{ displayOptionText(q, opt) }}</span>
                </el-checkbox>
              </el-checkbox-group>
            </template>

            <template v-else-if="q.type === 'SINGLE' || q.type === 'JUDGE'">
              <el-radio-group v-model="answers[q.questionId]" class="option-list">
                <el-radio
                  v-for="opt in parseOptions(q)"
                  :key="`${q.questionId}-${opt.label}`"
                  :label="opt.label"
                  class="option-item"
                >
                  <span class="option-marker">{{ opt.label }}</span>
                  <span>{{ displayOptionText(q, opt) }}</span>
                </el-radio>
              </el-radio-group>
            </template>

            <template v-else>
              <el-input
                v-model="answers[q.questionId]"
                type="textarea"
                :rows="3"
                resize="none"
                placeholder="请输入答案"
              />
            </template>
          </div>
        </article>
      </section>
    </div>

    <footer class="exam-footer">
      <div class="exam-footer__summary">
        <span>共 {{ state.questions.length }} 题</span>
        <span>已完成 {{ answeredCount }} 题</span>
      </div>
      <el-button type="primary" size="large" @click="submit">交卷</el-button>
    </footer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { antiCheatApi, snapshotApi, startExamApi, submitExamApi } from '../../api'
import { parseDateTime } from '../../utils/datetime'

const route = useRoute()
const router = useRouter()
const examId = String(route.params.id || '')

const state = reactive({
  examId,
  examName: '',
  questions: []
})
const answers = reactive({})
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
const recentEventTimes = new Map()

const answeredCount = computed(() =>
  state.questions.filter((q) => isQuestionAnswered(q)).length
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

const isQuestionAnswered = (question) => {
  const value = answers[question.questionId]
  if (Array.isArray(value)) {
    return value.length > 0
  }
  return String(value || '').trim().length > 0
}

const scrollToQuestion = (questionId) => {
  const target = document.getElementById(questionAnchorId(questionId))
  if (target) {
    target.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const normalizeAnswer = (q) => {
  const val = answers[q.questionId]
  if (Array.isArray(val)) {
    return val.join(',')
  }
  return val || ''
}

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
  await submitExamApi(examId, payload)
  try {
    await ElMessageBox.confirm('交卷成功，是否前往考试结果中心查看成绩状态？', '提交成功', {
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
}

onMounted(async () => {
  const data = await startExamApi(examId)
  Object.assign(state, data)
  state.questions.forEach((q) => {
    answers[q.questionId] = q.currentAnswer || (q.type === 'MULTI' ? [] : '')
    if (q.type === 'MULTI' && typeof answers[q.questionId] === 'string' && answers[q.questionId]) {
      answers[q.questionId] = answers[q.questionId].split(',').filter((item) => item)
    }
  })

  examEndAt.value = resolveExamEndTime(data)
  syncCountdown()
  timer = setInterval(() => {
    syncCountdown()
    if (Number.isFinite(secondsLeft.value) && secondsLeft.value <= 0) {
      clearInterval(timer)
      submit()
    }
  }, 1000)

  snapshotTimer = setInterval(sendSnapshot, 15000)
  window.addEventListener('blur', onBlur)
  window.addEventListener('focus', onFocus)
  window.addEventListener('offline', onOffline)
  window.addEventListener('online', onOnline)
  document.addEventListener('visibilitychange', onVisibility)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('copy', onCopy)
  document.addEventListener('paste', onPaste)
  document.addEventListener('cut', onCut)
  document.addEventListener('contextmenu', onContextMenu)
  await tryEnterFullscreen()
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (snapshotTimer) clearInterval(snapshotTimer)
  if (bannerResetTimer) clearTimeout(bannerResetTimer)
  window.removeEventListener('blur', onBlur)
  window.removeEventListener('focus', onFocus)
  window.removeEventListener('offline', onOffline)
  window.removeEventListener('online', onOnline)
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
  background:
    radial-gradient(circle at top left, rgba(13, 148, 136, 0.16), transparent 30%),
    linear-gradient(180deg, #f4fbf9 0%, #eef5f4 100%);
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
  color: #f8fffd;
  background: linear-gradient(135deg, #073b3a 0%, #0f766e 55%, #115e59 100%);
  box-shadow: 0 18px 40px rgba(7, 59, 58, 0.2);
}

.exam-header__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: rgba(248, 255, 253, 0.72);
}

.exam-header h1 {
  margin: 0;
  font-size: clamp(28px, 3vw, 42px);
  line-height: 1.06;
}

.exam-header__hint {
  max-width: 760px;
  margin: 10px 0 0;
  color: rgba(248, 255, 253, 0.8);
}

.exam-header__status {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.status-pill {
  min-width: 170px;
  padding: 14px 16px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(14px);
}

.status-pill span {
  display: block;
  font-size: 12px;
  color: rgba(248, 255, 253, 0.76);
}

.status-pill strong {
  display: block;
  margin-top: 6px;
  font-size: 24px;
  letter-spacing: 0.04em;
}

.exam-banner {
  padding: 16px 32px 0;
}

.exam-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 24px;
  padding: 18px 32px 24px;
}

.exam-outline {
  position: sticky;
  top: 144px;
  align-self: start;
}

.outline-card {
  padding: 18px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.08);
}

.outline-card__title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.outline-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.outline-item {
  height: 40px;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  background: #fff;
  color: #334155;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, background-color 0.2s ease;
}

.outline-item:hover {
  transform: translateY(-1px);
  border-color: #14b8a6;
}

.outline-item--done {
  border-color: #0f766e;
  background: #0f766e;
  color: #fff;
}

.outline-tip {
  margin: 14px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.question-list {
  min-width: 0;
  display: grid;
  gap: 18px;
  align-content: start;
  overflow: auto;
  padding-right: 8px;
}

.q-item {
  padding: 24px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 24px 48px rgba(15, 23, 42, 0.08);
}

.q-item__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.q-item__index {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.q-item__meta {
  margin-top: 6px;
  color: #64748b;
}

.q-item__status {
  padding: 8px 12px;
  border-radius: 999px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 13px;
  font-weight: 700;
}

.q-item__status--done {
  background: #ecfdf5;
  color: #047857;
}

.q-content {
  margin: 18px 0 0;
  font-size: 17px;
  line-height: 1.8;
  color: #111827;
}

.q-image-list {
  margin-top: 18px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

.q-image-frame {
  margin: 0;
  padding: 14px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: 24px;
  background:
    linear-gradient(180deg, rgba(240, 249, 255, 0.92) 0%, rgba(255, 255, 255, 0.98) 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.q-image {
  width: 100%;
  height: clamp(220px, 34vw, 420px);
  border-radius: 18px;
  overflow: hidden;
  background:
    radial-gradient(circle at top, rgba(191, 219, 254, 0.28), transparent 38%),
    linear-gradient(180deg, #f8fbff 0%, #edf6f4 100%);
}

.q-answer {
  margin-top: 22px;
}

.option-list {
  width: 100%;
  display: grid;
  gap: 12px;
}

.option-item {
  margin-right: 0;
  padding: 14px 16px;
  border: 1px solid #dbe4e3;
  border-radius: 18px;
  background: #f8fbfb;
  transition: border-color 0.2s ease, background-color 0.2s ease, transform 0.2s ease;
}

.option-item:hover {
  transform: translateY(-1px);
  border-color: #14b8a6;
  background: #ffffff;
}

.option-marker {
  display: inline-flex;
  width: 28px;
  height: 28px;
  margin-right: 12px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-weight: 700;
}

.exam-footer {
  position: sticky;
  bottom: 0;
  z-index: 15;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 32px 24px;
  border-top: 1px solid rgba(15, 118, 110, 0.08);
  background: rgba(244, 251, 249, 0.92);
  backdrop-filter: blur(12px);
}

.exam-footer__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  color: #475569;
  font-weight: 600;
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

  .exam-header {
    padding-top: 18px;
  }

  .status-pill {
    min-width: 0;
    flex: 1;
  }

  .q-item {
    padding: 18px;
    border-radius: 20px;
  }

  .q-image {
    height: 240px;
  }

  .exam-footer {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
