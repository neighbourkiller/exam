import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  timeout: 15000
})

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
  (error) => {
    const status = error?.response?.status
    if (status === 401 || status === 403) {
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('roles')
      localStorage.removeItem('username')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
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
