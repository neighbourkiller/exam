import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:16730/api/v1'

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000
})

let refreshPromise = null

const isAuthRoute = (url = '') => url.includes('/auth/login') || url.includes('/auth/refresh')

const clearAuthState = () => {
  try {
    const auth = useAuthStore()
    auth.clear()
  } catch {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('roles')
    localStorage.removeItem('username')
  }
}

const applyAuthPayload = (payload) => {
  try {
    const auth = useAuthStore()
    auth.setAuth({
      ...payload,
      username: auth.username || localStorage.getItem('username') || ''
    })
  } catch {
    localStorage.setItem('token', payload.accessToken || '')
    localStorage.setItem('refreshToken', payload.refreshToken || '')
    localStorage.setItem('roles', JSON.stringify(payload.roles || []))
  }
}

const redirectToLogin = () => {
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

const refreshAccessToken = async () => {
  if (refreshPromise) {
    return refreshPromise
  }
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) {
    throw new Error('缺少刷新令牌')
  }
  refreshPromise = axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken }, {
    timeout: 15000
  }).then((response) => {
    const payload = response?.data?.data
    if (!response?.data?.success || !payload?.accessToken) {
      throw new Error(response?.data?.message || '刷新登录态失败')
    }
    applyAuthPayload(payload)
    return payload.accessToken
  }).finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const resp = response.data
    if (resp && resp.success) {
      return resp.data
    }
    ElMessage.error(resp?.message || '请求失败')
    const businessError = new Error(resp?.message || '请求失败')
    businessError.code = resp?.code || null
    businessError.responseData = resp
    return Promise.reject(businessError)
  },
  async (error) => {
    const status = error?.response?.status
    const originalRequest = error?.config || {}
    if ((status === 401 || status === 403) && !originalRequest._retry && !isAuthRoute(originalRequest.url)) {
      originalRequest._retry = true
      try {
        const accessToken = await refreshAccessToken()
        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers.Authorization = `Bearer ${accessToken}`
        return http(originalRequest)
      } catch (refreshError) {
        clearAuthState()
        redirectToLogin()
        ElMessage.error('登录已失效，请重新登录')
        return Promise.reject(refreshError)
      }
    }

    if (status === 401 || status === 403) {
      clearAuthState()
      redirectToLogin()
      ElMessage.error('登录已失效，请重新登录')
      return Promise.reject(error)
    }

    ElMessage.error(error?.response?.data?.message || error.message || '网络异常')
    if (error?.response?.data?.code && !error.code) {
      error.code = error.response.data.code
    }
    return Promise.reject(error)
  }
)

export default http
