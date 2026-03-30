<template>
  <el-card class="page-card">
    <template #header><div class="header">考试发布</div></template>

    <el-form :model="form" label-width="110px">
      <el-row :gutter="12">
        <el-col :span="8"><el-form-item label="考试名称"><el-input v-model="form.name" /></el-form-item></el-col>
        <el-col :span="8">
          <el-form-item label="试卷">
            <el-select
              v-model="form.paperId"
              filterable
              clearable
              placeholder="请选择试卷"
              style="width: 100%"
            >
              <el-option
                v-for="paper in paperOptions"
                :key="paper.id"
                :label="paperLabel(paper)"
                :value="paper.id"
              >
                <div class="paper-option-row">
                  <span class="paper-option-id">{{ paper.id }}</span>
                  <span>{{ paper.name }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8"><el-form-item label="及格线"><el-input v-model.number="form.passScore" /></el-form-item></el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="开考时间">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="结束时间">
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              disabled
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="时长(分钟)">
            <el-input-number v-model="form.durationMinutes" :min="1" :step="1" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="目标教学班">
        <el-select
          v-model="form.targetClassIds"
          multiple
          filterable
          clearable
          placeholder="请选择目标教学班"
          style="width: 100%"
        >
          <el-option
            v-for="item in teachingClassOptions"
            :key="item.id"
            :label="teachingClassLabel(item)"
            :value="item.id"
          >
            <div class="paper-option-row">
              <span class="paper-option-id">{{ item.id }}</span>
              <span>{{ item.name }}（{{ item.subjectName || '未知课程' }}）</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
      <el-button type="primary" @click="createExam">创建考试</el-button>
      <el-button @click="loadPaperOptions">刷新试卷选项</el-button>
      <el-button @click="loadTeachingClasses">刷新教学班选项</el-button>
    </el-form>

    <el-divider />

    <el-button @click="loadExams">刷新考试列表</el-button>
    <el-table :data="exams" border style="margin-top: 10px">
      <el-table-column prop="examId" label="考试ID" width="120" />
      <el-table-column prop="name" label="名称" />
      <el-table-column label="开始时间" width="190">
        <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" width="190">
        <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column label="操作" width="240">
        <template #default="scope">
          <div class="action-row">
            <el-button v-if="scope.row.status === 'DRAFT'" type="primary" size="small" @click="publish(scope.row.examId)">发布</el-button>
            <el-button
              v-if="scope.row.status === 'PUBLISHED' || scope.row.status === 'ONGOING'"
              type="danger"
              size="small"
              @click="terminate(scope.row.examId)"
            >
              终止
            </el-button>
            <el-button
              v-if="canOpenProctoring(scope.row.status)"
              type="primary"
              plain
              size="small"
              @click="openProctoring(scope.row.examId)"
            >
              监考
            </el-button>
            <span v-if="scope.row.status === 'TERMINATED'">已终止</span>
            <span v-else-if="scope.row.status === 'FINISHED'">已结束</span>
            <span
              v-else-if="scope.row.status !== 'DRAFT'
                && scope.row.status !== 'PUBLISHED'
                && scope.row.status !== 'ONGOING'"
            >
              --
            </span>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { createExamApi, examTeachingClassesApi, publishExamApi, queryPapersApi, teacherExamsApi, terminateExamApi } from '../../api'
import { addMinutesToDateTime, formatDateTime, normalizeDateTimeToMinute } from '../../utils/datetime'

const defaultStartTime = normalizeDateTimeToMinute(new Date())
const router = useRouter()

const form = reactive({
  name: 'Java阶段测验',
  paperId: null,
  startTime: defaultStartTime,
  endTime: addMinutesToDateTime(defaultStartTime, 60),
  durationMinutes: 60,
  passScore: 60,
  targetClassIds: []
})

const exams = ref([])
const paperOptions = ref([])
const teachingClassOptions = ref([])

const paperLabel = (paper) => `${paper.id} - ${paper.name}`
const teachingClassLabel = (item) => `${item.id} - ${item.name || ''} (${item.subjectName || '未知课程'})`
const canOpenProctoring = (status) => ['PUBLISHED', 'ONGOING'].includes(status)

const syncExamTimeRange = () => {
  const normalizedStart = normalizeDateTimeToMinute(form.startTime)
  if (!normalizedStart) {
    form.endTime = ''
    return
  }
  if (form.startTime !== normalizedStart) {
    form.startTime = normalizedStart
    return
  }
  const duration = Number(form.durationMinutes || 0)
  if (!Number.isFinite(duration) || duration <= 0) {
    form.endTime = ''
    return
  }
  form.endTime = addMinutesToDateTime(normalizedStart, duration)
}

watch(
  () => form.startTime,
  () => {
    syncExamTimeRange()
  }
)

watch(
  () => form.durationMinutes,
  () => {
    syncExamTimeRange()
  }
)

const loadPaperOptions = async () => {
  const data = await queryPapersApi({
    pageNum: 1,
    pageSize: 200,
    subjectId: null,
    name: null
  })
  paperOptions.value = data.records || []
}

const loadTeachingClasses = async () => {
  teachingClassOptions.value = await examTeachingClassesApi()
}

const createExam = async () => {
  syncExamTimeRange()
  if (!form.paperId) {
    ElMessage.warning('请选择试卷')
    return
  }
  if (!form.startTime || !form.endTime) {
    ElMessage.warning('请先选择开考时间并填写有效时长')
    return
  }
  if (!form.targetClassIds.length) {
    ElMessage.warning('请选择目标教学班')
    return
  }
  const payload = { ...form }
  const id = await createExamApi(payload)
  ElMessage.success(`考试创建成功: ${id}`)
  await loadExams()
}

const publish = async (id) => {
  await publishExamApi(id)
  ElMessage.success('发布成功')
  await loadExams()
}

const terminate = async (id) => {
  await ElMessageBox.confirm('确认终止该考试吗？终止后学生将无法开始或继续考试。', '提示')
  await terminateExamApi(id)
  ElMessage.success('终止成功')
  await loadExams()
}

const openProctoring = (examId) => {
  router.push({
    path: '/teacher/proctoring',
    query: {
      examId: String(examId)
    }
  })
}

const loadExams = async () => {
  exams.value = await teacherExamsApi()
}

onMounted(async () => {
  await loadPaperOptions()
  await loadTeachingClasses()
  await loadExams()
})
</script>

<style scoped>
.header {
  font-size: 18px;
  font-weight: 700;
}

.paper-option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.paper-option-id {
  color: #606266;
  min-width: 84px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
</style>
