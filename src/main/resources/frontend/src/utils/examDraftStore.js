const DB_NAME = 'exam-local-drafts'
const DB_VERSION = 1
const STORE_NAME = 'drafts'

let openRequestPromise = null

const canUseIndexedDb = () =>
  typeof window !== 'undefined'
  && typeof window.indexedDB !== 'undefined'

const cloneAnswers = (answers = {}) => {
  const result = {}
  Object.entries(answers || {}).forEach(([questionId, value]) => {
    if (Array.isArray(value)) {
      result[questionId] = value.map((item) => String(item))
      return
    }
    result[questionId] = value == null ? '' : String(value)
  })
  return result
}

const cloneMarkedQuestionIds = (markedQuestionIds = []) => {
  if (!Array.isArray(markedQuestionIds)) {
    return []
  }
  return [...new Set(markedQuestionIds.map((item) => String(item)).filter((item) => item))]
}

const buildStorageKey = (userId, examId) => `${String(userId)}:${String(examId)}`

const openDatabase = () => {
  if (!canUseIndexedDb()) {
    return Promise.resolve(null)
  }
  if (!openRequestPromise) {
    openRequestPromise = new Promise((resolve, reject) => {
      const request = window.indexedDB.open(DB_NAME, DB_VERSION)
      request.onerror = () => reject(request.error || new Error('打开本地草稿库失败'))
      request.onupgradeneeded = () => {
        const db = request.result
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          db.createObjectStore(STORE_NAME, { keyPath: 'storageKey' })
        }
      }
      request.onsuccess = () => resolve(request.result)
    })
  }
  return openRequestPromise
}

const withStore = async (mode, handler) => {
  const db = await openDatabase()
  if (!db) {
    return null
  }
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(STORE_NAME, mode)
    const store = transaction.objectStore(STORE_NAME)
    let settled = false

    const finish = (value) => {
      if (settled) {
        return
      }
      settled = true
      resolve(value)
    }

    transaction.onerror = () => {
      if (settled) {
        return
      }
      settled = true
      reject(transaction.error || new Error('本地草稿操作失败'))
    }

    handler(store, finish)
  })
}

export const loadDraft = async (userId, examId) => {
  if (!userId || !examId) {
    return null
  }
  const result = await withStore('readonly', (store, done) => {
    const request = store.get(buildStorageKey(userId, examId))
    request.onerror = () => done(null)
    request.onsuccess = () => done(request.result || null)
  })
  if (!result) {
    return null
  }
  return {
    ...result,
    answers: cloneAnswers(result.answers || {}),
    markedQuestionIds: cloneMarkedQuestionIds(result.markedQuestionIds || [])
  }
}

export const saveDraft = async ({ userId, examId, answers, markedQuestionIds, updatedAt, lastSyncedAt, dirty }) => {
  if (!userId || !examId) {
    return null
  }
  const record = {
    storageKey: buildStorageKey(userId, examId),
    userId: String(userId),
    examId: String(examId),
    answers: cloneAnswers(answers),
    markedQuestionIds: cloneMarkedQuestionIds(markedQuestionIds),
    updatedAt: Number.isFinite(Number(updatedAt)) ? Number(updatedAt) : Date.now(),
    lastSyncedAt: Number.isFinite(Number(lastSyncedAt)) ? Number(lastSyncedAt) : null,
    dirty: Boolean(dirty)
  }
  await withStore('readwrite', (store, done) => {
    const request = store.put(record)
    request.onerror = () => done(null)
    request.onsuccess = () => done(record)
  })
  return record
}

export const markSynced = async (userId, examId, lastSyncedAt = Date.now()) => {
  const existing = await loadDraft(userId, examId)
  if (!existing) {
    return null
  }
  return saveDraft({
    ...existing,
    lastSyncedAt,
    dirty: false
  })
}

export const clearDraft = async (userId, examId) => {
  if (!userId || !examId) {
    return
  }
  await withStore('readwrite', (store, done) => {
    const request = store.delete(buildStorageKey(userId, examId))
    request.onerror = () => done(null)
    request.onsuccess = () => done(null)
  })
}
