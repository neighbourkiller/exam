<template>
  <div class="proctoring-shell">
    <section class="proctoring-hero">
      <div>
        <p class="proctoring-hero__eyebrow">Exam Proctoring</p>
        <h1>监考中心</h1>
        <p class="proctoring-hero__desc">统一查看考试中的异常行为、快照健康度与考后风险报告，帮助老师快速定位高风险学生。</p>
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
        <el-select v-model="selectedExamId" placeholder="请选择考试" class="toolbar-select" filterable>
          <el-option
            v-for="exam in currentExamOptions"
            :key="exam.examId"
            :label="`${exam.name} (${exam.status})`"
            :value="String(exam.examId)"
          />
        </el-select>
        <el-button @click="refreshAll" :loading="loading">刷新</el-button>
      </div>
    </section>

    <section v-if="overview" class="summary-grid">
      <article class="summary-card">
        <span>应监考人数</span>
        <strong>{{ overview.totalStudents }}</strong>
      </article>
      <article class="summary-card">
        <span>当前在线</span>
        <strong>{{ overview.answeringStudents }}</strong>
      </article>
      <article class="summary-card summary-card--warn">
        <span>高风险人数</span>
        <strong>{{ overview.highRiskCount }}</strong>
      </article>
      <article class="summary-card">
        <span>快照异常</span>
        <strong>{{ overview.snapshotAlertCount }}</strong>
      </article>
    </section>

    <section v-if="overview" class="content-grid">
      <div class="content-panel">
        <div class="panel-header">
          <div>
            <h2>{{ activeTab === 'live' ? '学生风险列表' : '异常学生清单' }}</h2>
            <p>按风险分倒序，支持按班级、风险等级和姓名筛选。</p>
          </div>
          <div class="filter-row">
            <el-select v-model="classFilter" clearable placeholder="班级筛选" class="filter-item">
              <el-option v-for="name in classOptions" :key="name" :label="name" :value="name" />
            </el-select>
            <el-select v-model="riskFilter" clearable placeholder="风险等级" class="filter-item">
              <el-option label="LOW" value="LOW" />
              <el-option label="MEDIUM" value="MEDIUM" />
              <el-option label="HIGH" value="HIGH" />
            </el-select>
            <el-input v-model="keyword" clearable placeholder="搜索姓名或账号" class="filter-item" />
          </div>
        </div>

        <el-table :data="filteredStudents" border stripe>
          <el-table-column prop="studentName" label="学生" min-width="130" />
          <el-table-column label="班级" min-width="140">
            <template #default="{ row }">{{ formatClassNames(row.classNames) }}</template>
          </el-table-column>
          <el-table-column label="风险等级" width="110">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.riskLevel)">{{ row.riskLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="riskScore" label="风险分" width="90" />
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
              <el-tag size="small" :type="row.answering ? 'success' : 'info'">{{ row.answering ? '答题中' : '未在线' }}</el-tag>
              <el-tag v-if="row.snapshotAlert" size="small" type="warning" class="state-tag">快照异常</el-tag>
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
              <strong>{{ event.studentName }}</strong>
              <span>{{ formatDateTime(event.eventTime) }}</span>
            </div>
            <div class="event-item__body">
              <span>{{ formatEventType(event.eventType) }}</span>
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

    <el-drawer v-model="drawerVisible" title="学生监考详情" size="42%">
      <template v-if="timeline">
        <div class="timeline-summary">
          <div class="timeline-pill">
            <span>学生</span>
            <strong>{{ timeline.studentName }}</strong>
          </div>
          <div class="timeline-pill">
            <span>风险等级</span>
            <strong>{{ timeline.riskLevel }} / {{ timeline.riskScore }}</strong>
          </div>
          <div class="timeline-pill">
            <span>快照状态</span>
            <strong>{{ timeline.snapshotAlert ? '异常' : '正常' }}</strong>
          </div>
        </div>

        <div class="timeline-meta">
          <p>班级：{{ formatClassNames(timeline.classNames) }}</p>
          <p>累计离屏：{{ formatDuration(timeline.totalOffscreenDurationMs) }}</p>
          <p>最近快照：{{ formatDateTime(timeline.lastSnapshotTime) }}</p>
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
            <pre v-if="event.payload" class="timeline-item__payload">{{ formatPayload(event.payload) }}</pre>
          </div>
          <el-empty v-if="!timeline.events.length" description="暂无学生异常事件" />
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { proctoringOverviewApi, proctoringStudentsApi, proctoringTimelineApi, teacherExamsApi } from '../../api'
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
const keyword = ref('')

let refreshTimer = null

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

const EVENT_TYPE_MAP = {
  WINDOW_BLUR: '窗口失去焦点',
  TAB_HIDDEN: '切换标签页/隐藏',
  FULLSCREEN_EXIT: '退出全屏',
  COPY_ATTEMPT: '复制操作',
  PASTE_ATTEMPT: '粘贴操作',
  CUT_ATTEMPT: '剪切操作',
  CONTEXT_MENU: '右键菜单',
  NETWORK_OFFLINE: '网络离线'
}

const formatEventType = (type) => EVENT_TYPE_MAP[type] || type || '--'

const riskTagType = (level) => {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'success'
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

const openTimeline = async (row) => {
  if (!selectedExamId.value) {
    return
  }
  drawerVisible.value = true
  timelineLoading.value = true
  try {
    timeline.value = await proctoringTimelineApi(selectedExamId.value, row.studentId)
  } catch (error) {
    ElMessage.error(error?.message || '加载学生详情失败')
  } finally {
    timelineLoading.value = false
  }
}

watch(activeTab, async () => {
  ensureSelectedExam()
  syncRouteQuery()
  await loadDashboard()
  startPollingIfNeeded()
})

watch(selectedExamId, async () => {
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
  gap: 18px;
}

.proctoring-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px;
  border-radius: 28px;
  color: #f8fffd;
  background:
    radial-gradient(circle at top right, rgba(45, 212, 191, 0.28), transparent 30%),
    linear-gradient(135deg, #083344 0%, #115e59 46%, #164e63 100%);
}

.proctoring-hero__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: rgba(248, 255, 253, 0.72);
}

.proctoring-hero h1 {
  margin: 0;
  font-size: 36px;
}

.proctoring-hero__desc {
  max-width: 720px;
  margin: 10px 0 0;
  color: rgba(248, 255, 253, 0.84);
}

.proctoring-hero__meta {
  display: grid;
  gap: 12px;
  min-width: 240px;
}

.meta-pill,
.summary-card {
  padding: 16px 18px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(12px);
}

.meta-pill span,
.summary-card span {
  display: block;
  font-size: 12px;
  color: rgba(248, 255, 253, 0.7);
}

.meta-pill strong,
.summary-card strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.proctoring-toolbar,
.content-panel {
  padding: 20px 22px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.06);
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

.toolbar-select {
  width: 360px;
  max-width: 100%;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  border-color: rgba(15, 118, 110, 0.12);
  background: linear-gradient(180deg, #ffffff 0%, #f5fbfa 100%);
}

.summary-card--warn strong {
  color: #b91c1c;
}

.summary-card span {
  color: #64748b;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(320px, 0.9fr);
  gap: 18px;
}

.panel-header h2,
.report-section h3 {
  margin: 0;
  font-size: 20px;
  color: #0f172a;
}

.panel-header p {
  margin: 6px 0 0;
  color: #64748b;
}

.filter-item {
  width: 180px;
}

.state-tag {
  margin-left: 6px;
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
  border-radius: 18px;
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

.event-item__body,
.timeline-item__duration,
.timeline-meta p,
.report-row span,
.stat-row span {
  color: #64748b;
}

.report-grid {
  align-content: start;
}

.timeline-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.timeline-pill {
  padding: 14px 16px;
  border-radius: 18px;
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

.timeline-item__payload {
  margin: 10px 0 0;
  padding: 12px;
  overflow: auto;
  border-radius: 14px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .summary-grid,
  .timeline-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .proctoring-hero {
    flex-direction: column;
  }

  .summary-grid,
  .timeline-summary {
    grid-template-columns: 1fr;
  }

  .filter-item,
  .toolbar-select {
    width: 100%;
  }
}
</style>
