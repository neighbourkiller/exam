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
    return Promise.reject(new Error(resp?.message || '请求失败'))
  },
  (error) => {
    ElMessage.error(error?.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default http
