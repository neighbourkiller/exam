<template>
  <div class="environment-check-panel">
    <el-progress
      :percentage="progressPercent"
      :status="progressStatus"
      :stroke-width="8"
      :show-text="false"
    />

    <div class="check-list">
      <div
        v-for="item in displayResults"
        :key="item.key"
        :class="['check-item', `check-item--${item.status}`]"
      >
        <div class="check-item__head">
          <strong>{{ item.label }}</strong>
          <el-tag :type="statusType(item.status)" size="small" effect="light">
            {{ statusText(item.status) }}
          </el-tag>
        </div>
        <p>{{ item.detail }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { CHECK_DEFINITIONS } from '../../utils/preExamCheck'

const props = defineProps({
  results: {
    type: Array,
    default: () => []
  },
  running: {
    type: Boolean,
    default: false
  },
  finished: {
    type: Boolean,
    default: false
  }
})

const placeholders = computed(() =>
  CHECK_DEFINITIONS.map((item) => ({
    key: item.key,
    label: item.label,
    status: 'pending',
    detail: item.pendingDetail
  }))
)

const displayResults = computed(() => {
  if (!props.results.length) {
    return placeholders.value
  }
  const knownResults = placeholders.value.map((placeholder) =>
    props.results.find((item) => item.key === placeholder.key) || placeholder
  )
  const extraResults = props.results.filter((item) =>
    !placeholders.value.some((placeholder) => placeholder.key === item.key)
  )
  return [...knownResults, ...extraResults]
})

const passedCount = computed(() =>
  displayResults.value.filter((item) => item.status === 'passed' || item.status === 'warning').length
)
const blockingResults = computed(() =>
  displayResults.value.filter((item) => item.blocking && item.status === 'failed')
)
const progressPercent = computed(() => {
  if (props.running) {
    return Math.max(15, Math.round((passedCount.value / placeholders.value.length) * 100))
  }
  if (!props.finished) {
    return 0
  }
  return Math.round((passedCount.value / placeholders.value.length) * 100)
})
const progressStatus = computed(() => {
  if (!props.finished || props.running) {
    return undefined
  }
  return blockingResults.value.length ? 'exception' : 'success'
})

const statusType = (status) => {
  if (status === 'passed') return 'success'
  if (status === 'warning') return 'warning'
  if (status === 'failed') return 'danger'
  return 'info'
}

const statusText = (status) => {
  if (status === 'passed') return '通过'
  if (status === 'warning') return '提醒'
  if (status === 'failed') return '失败'
  return '待检测'
}
</script>

<style scoped>
.environment-check-panel {
  display: grid;
  gap: 18px;
}

.check-list {
  display: grid;
  gap: 12px;
}

.check-item {
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: var(--radius-sm);
  background: #ffffff;
}

.check-item--passed {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.check-item--warning {
  border-color: #fed7aa;
  background: #fff7ed;
}

.check-item--failed {
  border-color: #fecaca;
  background: #fef2f2;
}

.check-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.check-item__head strong {
  color: var(--text-main);
  font-size: 15px;
}

.check-item p {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}
</style>
