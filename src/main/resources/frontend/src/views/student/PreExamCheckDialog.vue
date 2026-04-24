<template>
  <el-dialog
    v-model="visible"
    width="680px"
    class="pre-exam-dialog"
    :close-on-click-modal="!running"
    :close-on-press-escape="!running"
    @closed="onClosed"
  >
    <template #header>
      <div class="pre-exam-dialog__header">
        <div>
          <p>考前检测</p>
          <h2>{{ exam?.name || '在线考试' }}</h2>
        </div>
        <el-tag :type="summaryTagType" effect="light">{{ summaryText }}</el-tag>
      </div>
    </template>

    <EnvironmentCheckPanel
      :results="results"
      :running="running"
      :finished="finished"
    />

    <template #footer>
      <div class="pre-exam-dialog__footer">
        <el-button :loading="running" @click="runChecks">{{ finished ? '重新检测' : '开始检测' }}</el-button>
        <el-button type="primary" :disabled="!canEnter" @click="confirmEnter">
          进入考试
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { releaseMediaStreams, runPreExamCheck } from '../../utils/preExamCheck'
import EnvironmentCheckPanel from './EnvironmentCheckPanel.vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  exam: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'passed'])

const running = ref(false)
const finished = ref(false)
const results = ref([])
let runSeq = 0
let passedToExam = false

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const blockingResults = computed(() =>
  results.value.filter((item) => item.blocking && item.status === 'failed')
)
const canEnter = computed(() => finished.value && !running.value && !blockingResults.value.length)
const summaryText = computed(() => {
  if (running.value) {
    return '检测中'
  }
  if (!finished.value) {
    return '待检测'
  }
  if (blockingResults.value.length) {
    return '未通过'
  }
  return '可进入'
})
const summaryTagType = computed(() => {
  if (running.value) {
    return 'warning'
  }
  if (!finished.value) {
    return 'info'
  }
  return blockingResults.value.length ? 'danger' : 'success'
})

const runChecks = async () => {
  if (!props.exam || running.value) {
    return
  }
  const currentSeq = ++runSeq
  running.value = true
  finished.value = false
  results.value = []

  try {
    const nextResults = await runPreExamCheck({ retainScreenShare: true })
    if (currentSeq !== runSeq) {
      return
    }
    results.value = nextResults
    finished.value = true
  } catch (error) {
    if (currentSeq !== runSeq) {
      return
    }
    results.value = [
      {
        key: 'network',
        label: '检测流程',
        status: 'failed',
        detail: error?.message || '考前检测失败，请刷新页面后重试。',
        blocking: true
      }
    ]
    finished.value = true
  } finally {
    if (currentSeq === runSeq) {
      running.value = false
    }
    releaseMediaStreams({ includeRetainedScreenShare: false })
  }
}

const confirmEnter = () => {
  if (!canEnter.value) {
    ElMessage.warning('请先完成并通过考前检测')
    return
  }
  emit('passed', {
    accept: () => {
      passedToExam = true
      visible.value = false
    },
    reject: () => {
      passedToExam = false
      releaseMediaStreams()
      visible.value = false
    }
  })
}

const onClosed = () => {
  runSeq += 1
  running.value = false
  const keepRetainedScreenShare = passedToExam
  releaseMediaStreams({ includeRetainedScreenShare: !keepRetainedScreenShare })
  if (!keepRetainedScreenShare) {
    passedToExam = false
  }
}

watch(() => props.modelValue, (value) => {
  if (value) {
    runSeq += 1
    passedToExam = false
    running.value = false
    finished.value = false
    results.value = []
  }
})

onBeforeUnmount(() => {
  releaseMediaStreams({ includeRetainedScreenShare: !passedToExam })
})
</script>

<style scoped>
.pre-exam-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.pre-exam-dialog__header p {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-size: 13px;
}

.pre-exam-dialog__header h2 {
  margin: 0;
  color: var(--text-main);
  font-size: 20px;
  line-height: 1.25;
}

.pre-exam-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 768px) {
  .pre-exam-dialog__header,
  .pre-exam-dialog__footer {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
