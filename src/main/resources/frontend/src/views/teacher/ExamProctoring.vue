<template>
  <div class="proctoring-shell">
    <section class="proctoring-hero">
      <div>
        <p class="proctoring-hero__eyebrow">监考工作台</p>
        <h1>监考中心</h1>
        <p class="proctoring-hero__desc">集中处理考试实时异常、考后风险复核与证据核查。</p>
      </div>
      <div class="proctoring-hero__meta">
        <div class="meta-pill">
          <span>当前模式</span>
          <strong>{{ activeTab === 'live' ? '考试中看板' : '考后报告' }}</strong>
        </div>
        <div class="meta-pill" v-if="selectedExam">
          <span>当前考试</span>
          <strong>{{ selectedExam.name }}</strong>
        </div>
      </div>
    </section>

    <section class="proctoring-toolbar">
      <el-tabs v-model="activeTab" class="proctoring-tabs">
        <el-tab-pane label="考试中看板" name="live" />
        <el-tab-pane label="考后报告" name="report" />
      </el-tabs>

      <div class="toolbar-row">
        <div class="exam-picker">
          <span>考试</span>
          <el-select v-model="selectedExamId" placeholder="请选择考试" class="toolbar-select" filterable>
            <el-option
              v-for="exam in currentExamOptions"
              :key="exam.examId"
              :label="`${exam.name} (${exam.status})`"
              :value="String(exam.examId)"
            />
          </el-select>
        </div>
        <el-button type="primary" plain @click="refreshAll" :loading="loading">刷新数据</el-button>
      </div>
    </section>

    <section v-if="overview" class="summary-grid">
      <article class="summary-card summary-card--total">
        <span>应监考人数</span>
        <strong>{{ overview.totalStudents }}</strong>
        <small>本场考试考生总数</small>
      </article>
      <article class="summary-card summary-card--online">
        <span>当前在线</span>
        <strong>{{ overview.answeringStudents }}</strong>
        <small>在线率 {{ onlineRate }}%</small>
      </article>
      <article class="summary-card summary-card--warn">
        <span>高风险人数</span>
        <strong>{{ overview.highRiskCount }}</strong>
        <small>优先核查对象</small>
      </article>
      <article class="summary-card summary-card--snapshot">
        <span>快照异常</span>
        <strong>{{ overview.snapshotAlertCount }}</strong>
        <small>含缺失或延迟快照</small>
      </article>
      <article class="summary-card summary-card--review">
        <span>待核查</span>
        <strong>{{ overview.pendingReviewDispositionCount || 0 }}</strong>
        <small>需要教师处置</small>
      </article>
      <article class="summary-card summary-card--confirmed">
        <span>已确认</span>
        <strong>{{ overview.confirmedDispositionCount || 0 }}</strong>
        <small>确认违规或异常</small>
      </article>
      <article class="summary-card summary-card--ok">
        <span>误报</span>
        <strong>{{ overview.falsePositiveDispositionCount || 0 }}</strong>
        <small>已排除风险</small>
      </article>
      <article class="summary-card summary-card--closed">
        <span>已关闭</span>
        <strong>{{ overview.closedDispositionCount || 0 }}</strong>
        <small>处置流程结束</small>
      </article>
    </section>

    <section v-if="overview" class="content-grid">
      <div class="content-panel">
        <div class="panel-header">
          <div>
            <h2>{{ activeTab === 'live' ? '学生风险列表' : '异常学生清单' }}</h2>
            <p>共 {{ students.length }} 人，当前筛选 {{ filteredStudents.length }} 人。</p>
          </div>
          <div class="filter-row">
            <el-select v-model="classFilter" clearable placeholder="班级筛选" class="filter-item">
              <el-option v-for="name in classOptions" :key="name" :label="name" :value="name" />
            </el-select>
            <el-select v-model="riskFilter" clearable placeholder="风险等级" class="filter-item">
              <el-option label="低风险" value="LOW" />
              <el-option label="中风险" value="MEDIUM" />
              <el-option label="高风险" value="HIGH" />
            </el-select>
            <el-select v-model="dispositionFilter" clearable placeholder="处置状态" class="filter-item">
              <el-option
                v-for="item in DISPOSITION_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-input v-model="keyword" clearable placeholder="搜索姓名或账号" class="filter-item" />
          </div>
        </div>

        <el-table :data="filteredStudents" stripe :row-class-name="studentRowClassName" class="student-table">
          <el-table-column label="学生" min-width="150">
            <template #default="{ row }">
              <div class="student-cell">
                <strong>{{ row.studentName || '--' }}</strong>
                <span>{{ row.username || '账号未返回' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="班级" min-width="140">
            <template #default="{ row }">{{ formatClassNames(row.classNames) }}</template>
          </el-table-column>
          <el-table-column label="风险等级" width="110">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.riskLevel)" effect="light">{{ riskLevelLabel(row.riskLevel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="风险分" width="150">
            <template #default="{ row }">
              <div class="risk-score-cell">
                <strong>{{ row.riskScore || 0 }}</strong>
                <el-progress
                  :percentage="riskScorePercent(row.riskScore)"
                  :stroke-width="6"
                  :show-text="false"
                  :status="riskProgressStatus(row.riskLevel)"
                />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="处置状态" width="110">
            <template #default="{ row }">
              <el-tag :type="dispositionTagType(row.disposition?.status)" size="small">
                {{ dispositionLabel(row.disposition?.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="eventCount" label="异常次数" width="100" />
          <el-table-column label="最近事件" min-width="140">
            <template #default="{ row }">{{ formatEventType(row.latestEventType) }}</template>
          </el-table-column>
          <el-table-column label="最后异常时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.lastEventTime) }}</template>
          </el-table-column>
          <el-table-column label="最后快照" width="180">
            <template #default="{ row }">{{ formatDateTime(row.lastSnapshotTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <div class="state-tags">
                <el-tag size="small" :type="row.answering ? 'success' : 'info'">{{ row.answering ? '答题中' : '未在线' }}</el-tag>
                <el-tag v-if="row.snapshotAlert" size="small" type="warning">快照异常</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button type="primary" link @click="openTimeline(row)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="content-panel side-panel">
        <div class="panel-header">
          <div>
            <h2>{{ activeTab === 'live' ? '最近异常' : '异常分布' }}</h2>
            <p>{{ activeTab === 'live' ? '显示最近 10 条监考事件。' : '用于快速定位考试中最常见的异常类型。' }}</p>
          </div>
        </div>

        <div v-if="activeTab === 'live'" class="event-list">
          <div v-for="event in overview.recentEvents" :key="`${event.studentId}-${event.eventType}-${event.eventTime}`" class="event-item">
            <div class="event-item__head">
              <div class="event-title">
                <i :class="eventSeverityClass(event.eventType)" />
                <strong>{{ event.studentName }}</strong>
              </div>
              <span>{{ formatDateTime(event.eventTime) }}</span>
            </div>
            <div class="event-item__body">
              <el-tag size="small" :type="eventTagType(event.eventType)" effect="light">{{ formatEventType(event.eventType) }}</el-tag>
              <span>{{ event.durationMs ? formatDuration(event.durationMs) : '瞬时事件' }}</span>
            </div>
          </div>
          <el-empty v-if="!overview.recentEvents.length" description="暂无监考异常事件" />
        </div>

        <div v-else class="report-grid">
          <div class="report-section">
            <h3>事件类型分布</h3>
            <div v-for="item in overview.eventTypeStats" :key="item.eventType" class="stat-row">
              <span>{{ formatEventType(item.eventType) }}</span>
              <strong>{{ item.count }}</strong>
            </div>
          </div>

          <div class="report-section">
            <h3>高风险学生</h3>
            <div v-for="item in highRiskStudents" :key="item.studentId" class="report-row">
              <span>{{ item.studentName }}</span>
              <strong>{{ item.riskScore }}</strong>
            </div>
            <el-empty v-if="!highRiskStudents.length" description="暂无高风险学生" />
          </div>

          <div class="report-section">
            <h3>长时离屏名单</h3>
            <div v-for="item in longOffscreenStudents" :key="item.studentId" class="report-row">
              <span>{{ item.studentName }}</span>
              <strong>{{ formatDuration(item.totalOffscreenDurationMs) }}</strong>
            </div>
            <el-empty v-if="!longOffscreenStudents.length" description="暂无长时离屏学生" />
          </div>

          <div class="report-section">
            <h3>快照异常名单</h3>
            <div v-for="item in snapshotAlertStudents" :key="item.studentId" class="report-row">
              <span>{{ item.studentName }}</span>
              <strong>{{ formatDateTime(item.lastSnapshotTime) }}</strong>
            </div>
            <el-empty v-if="!snapshotAlertStudents.length" description="暂无快照异常学生" />
          </div>
        </div>
      </div>
    </section>

    <el-empty v-else description="请选择考试以查看监考信息" />

    <el-drawer v-model="drawerVisible" size="560px" class="proctoring-drawer">
      <template #header>
        <div class="drawer-title">
          <span>学生监考详情</span>
          <strong v-if="timeline">{{ timeline.studentName }}</strong>
        </div>
      </template>
      <template v-if="timeline">
        <div v-loading="timelineLoading" class="drawer-content">
        <div class="timeline-summary">
          <div class="timeline-pill">
            <span>学生</span>
            <strong>{{ timeline.studentName }}</strong>
          </div>
          <div class="timeline-pill">
            <span>风险等级</span>
            <strong>{{ riskLevelLabel(timeline.riskLevel) }} / {{ timeline.riskScore }}</strong>
          </div>
          <div class="timeline-pill">
            <span>快照状态</span>
            <strong>{{ timeline.snapshotAlert ? '异常' : '正常' }}</strong>
          </div>
          <div class="timeline-pill">
            <span>处置状态</span>
            <strong>{{ dispositionLabel(timeline.disposition?.status) }}</strong>
          </div>
        </div>

        <div class="timeline-meta">
          <p>班级：{{ formatClassNames(timeline.classNames) }}</p>
          <p>累计离屏：{{ formatDuration(timeline.totalOffscreenDurationMs) }}</p>
          <p>最近服务端同步：{{ formatDateTime(timeline.lastSnapshotTime) }}</p>
          <p>最长离线：{{ formatDuration(timelineOfflineDurationMs) }}</p>
          <p>重放事件：{{ timelineHasReplayedEvents ? '存在' : '无' }}</p>
        </div>

        <div class="disposition-panel">
          <div class="disposition-panel__header">
            <div>
              <h3>处置记录</h3>
              <p>
                最近处置：
                {{ timeline.disposition?.handledByName || '--' }}
                /
                {{ formatDateTime(timeline.disposition?.handledAt) }}
              </p>
            </div>
            <el-button type="primary" :loading="dispositionSaving" @click="saveDisposition">保存处置</el-button>
          </div>
          <el-form label-position="top" class="disposition-form">
            <el-form-item label="处置状态">
              <el-select v-model="dispositionForm.status" style="width: 100%">
                <el-option
                  v-for="item in DISPOSITION_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="处置备注">
              <el-input
                v-model="dispositionForm.remark"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="记录核查结论、误报原因或后续处理说明"
              />
            </el-form-item>
          </el-form>
        </div>

        <div class="timeline-stats">
          <div v-for="item in timeline.eventTypeStats" :key="item.eventType" class="stat-row">
            <span>{{ formatEventType(item.eventType) }}</span>
            <strong>{{ item.count }}</strong>
          </div>
        </div>

        <div class="timeline-list">
          <div v-for="event in timeline.events" :key="`${event.eventType}-${event.eventTime}-${event.durationMs}`" class="timeline-item">
            <div class="timeline-item__head">
              <strong>{{ formatEventType(event.eventType) }}</strong>
              <span>{{ formatDateTime(event.eventTime) }}</span>
            </div>
            <p class="timeline-item__duration">{{ event.durationMs ? formatDuration(event.durationMs) : '瞬时事件' }}</p>
            <p v-if="formatEventContext(event)" class="timeline-item__context">{{ formatEventContext(event) }}</p>
            <div v-if="parseEvidence(event.evidenceJson).length" class="timeline-evidence">
              <figure
                v-for="item in parseEvidence(event.evidenceJson)"
                :key="item.objectKey || item.url"
                class="timeline-evidence__item"
              >
                <el-tag size="small" effect="light">{{ evidenceSourceLabel(item.source) }}</el-tag>
                <el-image
                  :src="item.url"
                  :preview-src-list="parseEvidence(event.evidenceJson).map(evidence => evidence.url)"
                  fit="cover"
                  class="timeline-evidence__image"
                />
              </figure>
            </div>
            <pre v-if="event.payload" class="timeline-item__payload">{{ formatPayload(event.payload) }}</pre>
          </div>
          <el-empty v-if="!timeline.events.length" description="暂无学生异常事件" />
        </div>
        </div>
      </template>
      <template v-else>
        <div v-loading="timelineLoading" class="drawer-content drawer-content--empty">
          <el-empty v-if="!timelineLoading" description="学生监考详情加载失败，请重新打开" />
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { proctoringOverviewApi, proctoringStudentsApi, proctoringTimelineApi, teacherExamsApi, updateProctoringDispositionApi } from '../../api'
import { formatDateTime } from '../../utils/datetime'

const LIVE_STATUSES = new Set(['PUBLISHED', 'ONGOING'])
const REPORT_STATUSES = new Set(['FINISHED', 'TERMINATED'])

const route = useRoute()
const router = useRouter()

const activeTab = ref('live')
const exams = ref([])
const selectedExamId = ref('')
const overview = ref(null)
const students = ref([])
const timeline = ref(null)
const drawerVisible = ref(false)
const loading = ref(false)
const timelineLoading = ref(false)
const classFilter = ref('')
const riskFilter = ref('')
const dispositionFilter = ref('')
const keyword = ref('')
const dispositionSaving = ref(false)
const dispositionForm = reactive({
  status: 'PENDING_REVIEW',
  remark: ''
})

let refreshTimer = null
let timelineLoadSeq = 0

const DISPOSITION_OPTIONS = [
  { label: '待核查', value: 'PENDING_REVIEW' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '误报', value: 'FALSE_POSITIVE' },
  { label: '已关闭', value: 'CLOSED' }
]

const currentExamOptions = computed(() =>
  exams.value.filter((exam) => (activeTab.value === 'live' ? LIVE_STATUSES.has(exam.status) : REPORT_STATUSES.has(exam.status)))
)

const selectedExam = computed(() =>
  exams.value.find((exam) => String(exam.examId) === String(selectedExamId.value)) || null
)

const classOptions = computed(() =>
  [...new Set(students.value.flatMap((item) => item.classNames || []))]
)

const filteredStudents = computed(() =>
  students.value.filter((item) => {
    if (classFilter.value && !(item.classNames || []).includes(classFilter.value)) {
      return false
    }
    if (riskFilter.value && item.riskLevel !== riskFilter.value) {
      return false
    }
    if (dispositionFilter.value && dispositionStatus(item) !== dispositionFilter.value) {
      return false
    }
    if (!keyword.value) {
      return true
    }
    const text = `${item.studentName || ''} ${item.username || ''}`.toLowerCase()
    return text.includes(keyword.value.toLowerCase())
  })
)

const highRiskStudents = computed(() => students.value.filter((item) => item.riskLevel === 'HIGH'))
const longOffscreenStudents = computed(() => students.value.filter((item) => item.longOffscreen))
const snapshotAlertStudents = computed(() => students.value.filter((item) => item.snapshotAlert))
const onlineRate = computed(() => {
  const total = Number(overview.value?.totalStudents || 0)
  if (!total) {
    return 0
  }
  return Math.round((Number(overview.value?.answeringStudents || 0) / total) * 100)
})
const timelineHasReplayedEvents = computed(() =>
  Boolean(timeline.value?.events?.some((event) => parsePayloadObject(event.payload)?.replayed))
)
const timelineOfflineDurationMs = computed(() => {
  const durations = timeline.value?.events
    ?.filter((event) => event.eventType === 'NETWORK_OFFLINE')
    ?.map((event) => Number(event.durationMs || parsePayloadObject(event.payload)?.offlineDurationMs || 0))
    ?.filter((value) => Number.isFinite(value) && value > 0) || []
  return durations.length ? Math.max(...durations) : 0
})

const EVENT_TYPE_MAP = {
  WINDOW_BLUR: '窗口失去焦点',
  TAB_HIDDEN: '切换标签页/隐藏',
  FULLSCREEN_EXIT: '退出全屏',
  COPY_ATTEMPT: '复制操作',
  PASTE_ATTEMPT: '粘贴操作',
  CUT_ATTEMPT: '剪切操作',
  CONTEXT_MENU: '右键菜单',
  NETWORK_OFFLINE: '网络离线',
  LONG_INACTIVITY: '长时间无操作',
  CAMERA_START_FAILED: '摄像头启动失败',
  CAMERA_STREAM_ENDED: '摄像头画面中断',
  CAMERA_TRACK_MUTED: '摄像头画面暂停',
  CAMERA_FRAME_DARK: '摄像头画面异常',
  MULTI_MONITOR_DETECTED: '检测到多个显示器',
  SCREEN_CHECK_UNAVAILABLE: '无法检测显示器',
  SCREEN_SHARE_START_FAILED: '屏幕共享启动失败',
  SCREEN_SHARE_ENDED: '屏幕共享中断',
  NAVIGATION_LEAVE_ATTEMPT: '尝试离开考试页'
}

const HIGH_RISK_EVENTS = new Set([
  'FULLSCREEN_EXIT',
  'CAMERA_START_FAILED',
  'CAMERA_STREAM_ENDED',
  'MULTI_MONITOR_DETECTED',
  'SCREEN_CHECK_UNAVAILABLE',
  'SCREEN_SHARE_START_FAILED',
  'SCREEN_SHARE_ENDED',
  'NAVIGATION_LEAVE_ATTEMPT'
])

const MEDIUM_RISK_EVENTS = new Set([
  'WINDOW_BLUR',
  'TAB_HIDDEN',
  'COPY_ATTEMPT',
  'PASTE_ATTEMPT',
  'CUT_ATTEMPT',
  'CONTEXT_MENU',
  'NETWORK_OFFLINE',
  'LONG_INACTIVITY',
  'CAMERA_TRACK_MUTED',
  'CAMERA_FRAME_DARK'
])

const formatEventType = (type) => EVENT_TYPE_MAP[type] || type || '--'

const dispositionStatus = (item) => item?.disposition?.status || 'PENDING_REVIEW'
const dispositionLabel = (status) =>
  DISPOSITION_OPTIONS.find((item) => item.value === (status || 'PENDING_REVIEW'))?.label || status || '待核查'
const dispositionTagType = (status) => {
  if (status === 'CONFIRMED') return 'danger'
  if (status === 'FALSE_POSITIVE') return 'success'
  if (status === 'CLOSED') return 'info'
  return 'warning'
}

const riskTagType = (level) => {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'success'
}

const riskLevelLabel = (level) => {
  if (level === 'HIGH') return '高风险'
  if (level === 'MEDIUM') return '中风险'
  if (level === 'LOW') return '低风险'
  return level || '--'
}

const riskProgressStatus = (level) => {
  if (level === 'HIGH') return 'exception'
  if (level === 'MEDIUM') return 'warning'
  return 'success'
}

const riskScorePercent = (score) => Math.max(0, Math.min(100, Number(score || 0)))

const eventTagType = (eventType) => {
  if (HIGH_RISK_EVENTS.has(eventType)) return 'danger'
  if (MEDIUM_RISK_EVENTS.has(eventType)) return 'warning'
  return 'info'
}

const eventSeverityClass = (eventType) => `event-dot event-dot--${eventTagType(eventType)}`

const studentRowClassName = ({ row }) => {
  if (row.riskLevel === 'HIGH') return 'student-row--high'
  if (row.riskLevel === 'MEDIUM') return 'student-row--medium'
  return ''
}

const formatClassNames = (value) => (Array.isArray(value) && value.length ? value.join('、') : '--')

const formatDuration = (durationMs) => {
  if (!durationMs || durationMs <= 0) {
    return '0秒'
  }
  const totalSeconds = Math.ceil(durationMs / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (!minutes) {
    return `${seconds}秒`
  }
  return `${minutes}分${seconds}秒`
}

const parsePayloadObject = (payload) => {
  if (!payload) {
    return null
  }
  try {
    const parsed = JSON.parse(payload)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

const formatPayload = (payload) => {
  if (!payload) {
    return ''
  }
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

const formatPayloadTime = (value) => {
  if (!value) {
    return ''
  }
  const numberValue = Number(value)
  if (Number.isFinite(numberValue) && numberValue > 0) {
    return formatDateTime(new Date(numberValue).toISOString())
  }
  return formatDateTime(value)
}

const formatEventContext = (event) => {
  const payload = parsePayloadObject(event.payload)
  if (!payload) {
    return ''
  }
  const parts = []
  if (payload.replayed) {
    parts.push('恢复联网后重放')
  }
  if (payload.occurredAt) {
    parts.push(`原始发生：${formatPayloadTime(payload.occurredAt)}`)
  }
  const offlineDurationMs = Number(payload.offlineDurationMs || 0)
  if (Number.isFinite(offlineDurationMs) && offlineDurationMs > 0) {
    parts.push(`离线约 ${formatDuration(offlineDurationMs)}`)
  }
  if (event.eventType === 'NETWORK_OFFLINE' && event.durationMs) {
    parts.push(`断线 ${formatDuration(event.durationMs)}`)
  }
  return parts.join('，')
}

const parseEvidence = (evidenceJson) => {
  if (!evidenceJson) {
    return []
  }
  try {
    const parsed = JSON.parse(evidenceJson)
    return Array.isArray(parsed)
      ? parsed.filter((item) => item?.url)
      : []
  } catch {
    return []
  }
}

const evidenceSourceLabel = (source) => {
  if (source === 'SCREEN') return '屏幕截图'
  if (source === 'CAMERA') return '摄像头画面'
  return source || '证据图片'
}

const syncRouteQuery = () => {
  router.replace({
    query: {
      ...route.query,
      examId: selectedExamId.value || undefined
    }
  })
}

const clearRefreshTimer = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

const startPollingIfNeeded = () => {
  clearRefreshTimer()
  if (activeTab.value !== 'live' || !selectedExamId.value) {
    return
  }
  refreshTimer = setInterval(() => {
    loadDashboard(false)
  }, 10_000)
}

const ensureSelectedExam = () => {
  const options = currentExamOptions.value
  if (!options.length) {
    selectedExamId.value = ''
    overview.value = null
    students.value = []
    clearRefreshTimer()
    return
  }
  if (!options.some((exam) => String(exam.examId) === String(selectedExamId.value))) {
    selectedExamId.value = String(options[0].examId)
  }
}

const loadExams = async () => {
  exams.value = await teacherExamsApi()
  const queriedExam = route.query.examId ? exams.value.find((item) => String(item.examId) === String(route.query.examId)) : null
  if (queriedExam) {
    activeTab.value = REPORT_STATUSES.has(queriedExam.status) ? 'report' : 'live'
    selectedExamId.value = String(queriedExam.examId)
  }
  ensureSelectedExam()
}

const loadDashboard = async (withLoading = true) => {
  if (!selectedExamId.value) {
    overview.value = null
    students.value = []
    return
  }
  if (withLoading) {
    loading.value = true
  }
  try {
    const examId = selectedExamId.value
    const [overviewData, studentsData] = await Promise.all([
      proctoringOverviewApi(examId),
      proctoringStudentsApi(examId)
    ])
    overview.value = overviewData
    students.value = studentsData || []
  } catch (error) {
    ElMessage.error(error?.message || '加载监考信息失败')
  } finally {
    if (withLoading) {
      loading.value = false
    }
  }
}

const refreshAll = async () => {
  await loadExams()
  await loadDashboard()
  startPollingIfNeeded()
}

const hydrateDispositionForm = () => {
  dispositionForm.status = timeline.value?.disposition?.status || 'PENDING_REVIEW'
  dispositionForm.remark = timeline.value?.disposition?.remark || ''
}

const resetTimelineState = () => {
  timeline.value = null
  hydrateDispositionForm()
}

const loadTimeline = async (studentId, loadSeq = timelineLoadSeq) => {
  const data = await proctoringTimelineApi(selectedExamId.value, studentId)
  if (loadSeq !== timelineLoadSeq) {
    return false
  }
  timeline.value = data
  hydrateDispositionForm()
  return true
}

const openTimeline = async (row) => {
  if (!selectedExamId.value) {
    return
  }
  const loadSeq = ++timelineLoadSeq
  drawerVisible.value = true
  timelineLoading.value = true
  resetTimelineState()
  try {
    await loadTimeline(row.studentId, loadSeq)
  } catch (error) {
    if (loadSeq === timelineLoadSeq) {
      resetTimelineState()
      ElMessage.error(error?.message || '加载学生详情失败')
    }
  } finally {
    if (loadSeq === timelineLoadSeq) {
      timelineLoading.value = false
    }
  }
}

const saveDisposition = async () => {
  const currentTimeline = timeline.value
  if (!selectedExamId.value || !currentTimeline?.studentId) {
    return
  }
  dispositionSaving.value = true
  const studentId = currentTimeline.studentId
  const loadSeq = timelineLoadSeq
  try {
    await updateProctoringDispositionApi(selectedExamId.value, studentId, {
      status: dispositionForm.status,
      remark: dispositionForm.remark?.trim() || ''
    })
    ElMessage.success('处置记录已保存')
    await Promise.all([
      loadDashboard(false),
      loadTimeline(studentId, loadSeq)
    ])
  } catch (error) {
    ElMessage.error(error?.message || '保存处置记录失败')
  } finally {
    dispositionSaving.value = false
  }
}

watch(activeTab, async () => {
  timelineLoadSeq += 1
  drawerVisible.value = false
  resetTimelineState()
  ensureSelectedExam()
  syncRouteQuery()
  await loadDashboard()
  startPollingIfNeeded()
})

watch(selectedExamId, async () => {
  timelineLoadSeq += 1
  drawerVisible.value = false
  resetTimelineState()
  syncRouteQuery()
  await loadDashboard()
  startPollingIfNeeded()
})

onMounted(async () => {
  await refreshAll()
})

onBeforeUnmount(() => {
  clearRefreshTimer()
})
</script>

<style scoped>
.proctoring-shell {
  display: grid;
  gap: 16px;
  color: #0f172a;
}

.proctoring-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 24px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 18px;
  background: linear-gradient(135deg, #f8fafc 0%, #eef6f4 52%, #fff7ed 100%);
  box-shadow: 0 16px 42px rgba(15, 23, 42, 0.06);
}

.proctoring-hero__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #0f766e;
}

.proctoring-hero h1 {
  margin: 0;
  font-size: 30px;
  letter-spacing: 0;
}

.proctoring-hero__desc {
  max-width: 720px;
  margin: 8px 0 0;
  color: #475569;
}

.proctoring-hero__meta {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  max-width: 520px;
  flex-wrap: wrap;
}

.meta-pill,
.summary-card {
  padding: 14px 16px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
}

.meta-pill span,
.summary-card span {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.meta-pill strong,
.summary-card strong {
  display: block;
  margin-top: 6px;
  font-size: 24px;
  color: #0f172a;
}

.meta-pill {
  min-width: 150px;
}

.meta-pill strong {
  max-width: 260px;
  overflow: hidden;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.proctoring-toolbar,
.content-panel {
  padding: 18px 20px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.05);
}

.toolbar-row,
.panel-header,
.filter-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}

.proctoring-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.proctoring-tabs {
  min-width: 260px;
}

.exam-picker {
  display: flex;
  align-items: center;
  gap: 10px;
}

.exam-picker span {
  color: #64748b;
  font-size: 13px;
}

.toolbar-select {
  width: 360px;
  max-width: 100%;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  position: relative;
  overflow: hidden;
  min-height: 112px;
  background: #ffffff;
}

.summary-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  content: "";
  background: #94a3b8;
}

.summary-card small {
  display: block;
  margin-top: 8px;
  color: #94a3b8;
  font-size: 12px;
}

.summary-card--online::before,
.summary-card--ok::before {
  background: #16a34a;
}

.summary-card--warn::before,
.summary-card--confirmed::before {
  background: #dc2626;
}

.summary-card--snapshot::before,
.summary-card--review::before {
  background: #d97706;
}

.summary-card--warn strong,
.summary-card--confirmed strong {
  color: #b91c1c;
}

.summary-card--review strong,
.summary-card--snapshot strong {
  color: #b45309;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(340px, 0.85fr);
  gap: 16px;
}

.panel-header h2,
.report-section h3 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.panel-header p {
  margin: 6px 0 0;
  color: #64748b;
}

.filter-item {
  width: 160px;
}

.student-cell {
  display: grid;
  gap: 2px;
}

.student-cell strong {
  color: #0f172a;
}

.student-cell span {
  color: #64748b;
  font-size: 12px;
}

.risk-score-cell {
  display: grid;
  gap: 6px;
}

.state-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.event-list,
.report-grid,
.timeline-list,
.timeline-stats {
  display: grid;
  gap: 12px;
}

.event-item,
.report-section,
.timeline-item {
  padding: 14px 16px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.event-item__head,
.event-item__body,
.timeline-item__head,
.report-row,
.stat-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.event-title {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
}

.event-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  flex: 0 0 auto;
}

.event-dot--danger {
  background: #dc2626;
  box-shadow: 0 0 0 4px rgba(220, 38, 38, 0.12);
}

.event-dot--warning {
  background: #d97706;
  box-shadow: 0 0 0 4px rgba(217, 119, 6, 0.14);
}

.event-dot--info {
  background: #64748b;
  box-shadow: 0 0 0 4px rgba(100, 116, 139, 0.12);
}

.event-item__body,
.timeline-item__duration,
.timeline-item__context,
.timeline-meta p,
.report-row span,
.stat-row span {
  color: #64748b;
}

.report-grid {
  align-content: start;
  grid-template-columns: 1fr;
}

.drawer-content {
  min-height: 260px;
}

.drawer-title {
  display: grid;
  gap: 2px;
}

.drawer-title span {
  color: #64748b;
  font-size: 12px;
}

.drawer-title strong {
  color: #0f172a;
  font-size: 18px;
}

.timeline-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.timeline-pill {
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.timeline-pill span {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.timeline-pill strong {
  display: block;
  margin-top: 6px;
  font-size: 18px;
}

.timeline-meta {
  display: grid;
  gap: 6px;
  margin-bottom: 16px;
}

.timeline-meta p {
  margin: 0;
}

.timeline-item__context {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.45;
}

.disposition-panel {
  display: grid;
  gap: 14px;
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 14px;
  background: #f8fafc;
}

.disposition-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.disposition-panel__header h3 {
  margin: 0;
  color: #0f172a;
}

.disposition-panel__header p {
  margin: 6px 0 0;
  color: #64748b;
}

.disposition-form {
  display: grid;
  gap: 4px;
}

.timeline-item__payload {
  margin: 10px 0 0;
  padding: 12px;
  overflow: auto;
  border-radius: 14px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
}

.timeline-evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}

.timeline-evidence__item {
  display: grid;
  gap: 6px;
  width: 160px;
  margin: 0;
}

.timeline-evidence__image {
  width: 160px;
  height: 96px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 8px;
  background: #f8fafc;
}

:deep(.student-row--high td.el-table__cell) {
  background: #fff7f7;
}

:deep(.student-row--medium td.el-table__cell) {
  background: #fffbeb;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .proctoring-hero {
    align-items: stretch;
    flex-direction: column;
  }

  .proctoring-toolbar,
  .toolbar-row,
  .exam-picker {
    align-items: stretch;
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-item,
  .toolbar-select {
    width: 100%;
  }
}

@media (max-width: 520px) {
  .summary-grid,
  .timeline-summary {
    grid-template-columns: 1fr;
  }
}

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

:deep(.proctoring-tabs .el-tabs__header) {
  margin-bottom: 0;
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
