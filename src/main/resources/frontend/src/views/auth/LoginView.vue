<template>
  <div class="login-page">
    <!-- 装饰性背景 -->
    <div class="bg-pattern"></div>
    <div class="glow glow-1"></div>
    <div class="glow glow-2"></div>

    <div class="login-shell">
      <!-- 左侧品牌展示区 -->
      <section class="brand-panel">
        <div class="brand-header">
          <div class="brand-logo">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"></path>
              <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"></path>
            </svg>
          </div>
          <span class="brand-badge">Academic Exam Portal</span>
        </div>
        
        <div class="brand-content">
          <h1>在线考试系统</h1>
          <p class="brand-subtitle">
            基于 Spring Boot 3 + Vue 3 的一站式数字化测评平台，提供稳定、公平、高效的考试体验。
          </p>
          <ul class="feature-list">
            <li>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
              <span>智能防作弊监控系统</span>
            </li>
            <li>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
              <span>多维成绩深度数据分析</span>
            </li>
            <li>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
              <span>毫秒级高并发自动评阅</span>
            </li>
          </ul>
        </div>
        
        <div class="brand-footer">
          <p>© 2026 Ekusys Exam. All Rights Reserved.</p>
        </div>
      </section>

      <!-- 右侧登录操作区 -->
      <section class="login-panel">
        <div class="login-card">
          <div class="card-header">
            <h2>欢迎登录</h2>
            <p>请输入您的凭证以访问工作台</p>
          </div>

          <el-form :model="form" label-position="top" @submit.prevent="onSubmit" class="login-form">
            <el-form-item label="用户名">
              <el-input 
                v-model="form.username" 
                placeholder="用户名/邮箱" 
                size="large"
              >
                <template #prefix>
                  <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                    <circle cx="12" cy="7" r="4"></circle>
                  </svg>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="密码">
              <el-input 
                v-model="form.password" 
                type="password" 
                show-password 
                placeholder="请输入密码" 
                size="large"
              >
                <template #prefix>
                  <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                  </svg>
                </template>
              </el-input>
            </el-form-item>

            <div class="form-actions">
              <el-button type="primary" class="login-btn" native-type="submit" :loading="loading" size="large">
                验证并登录
              </el-button>
            </div>
          </el-form>

          <div class="quick-login">
            <span class="divider-text">测试账号快捷登录</span>
            <div class="account-hints">
              <button
                v-for="account in accountHints"
                :key="account.label"
                type="button"
                class="hint-tag"
                @click="fillAccount(account)"
              >
                {{ account.label }}
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { loginApi, meApi } from '../../api'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: '123456'
})

const accountHints = [
  { label: '管理员', username: 'admin', password: '123456' },
  { label: '教师', username: 'teacher1', password: '123456' },
  { label: '学生', username: 'student1', password: '123456' }
]

const fillAccount = (account) => {
  form.username = account.username
  form.password = account.password
}

const onSubmit = async () => {
  loading.value = true
  try {
    const tokens = await loginApi(form)
    auth.setAuth({
      ...tokens,
      roles: tokens.roles || []
    })
    const me = await meApi()
    auth.setProfile(me)
    ElMessage.success('登录成功，欢迎进入系统')
    if (me.roles.includes('STUDENT')) {
      router.push('/student/exams')
    } else if (me.roles.includes('ADMIN') && !me.roles.includes('TEACHER')) {
      router.push('/admin/users')
    } else {
      router.push('/teacher/questions')
    }
  } catch (err) {
    console.error(err)
    ElMessage.error(err.message || '登录失败，请检查账号密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background-color: #0f172a;
  padding: 20px;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

/* 装饰性背景 */
.bg-pattern {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(#1e293b 1px, transparent 1px);
  background-size: 32px 32px;
  opacity: 0.3;
}

.glow {
  position: absolute;
  width: 50vw;
  height: 50vh;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.15;
  pointer-events: none;
}

.glow-1 {
  background: #3b82f6;
  top: -10%;
  left: -10%;
}

.glow-2 {
  background: #0ea5e9;
  bottom: -10%;
  right: -10%;
}

.login-shell {
  width: 100%;
  max-width: 1100px;
  min-height: 640px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 32px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  overflow: hidden;
  z-index: 10;
}

/* 品牌展示面板 */
.brand-panel {
  padding: 60px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: linear-gradient(135deg, rgba(30, 41, 59, 0.8) 0%, rgba(15, 23, 42, 0.9) 100%);
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  color: #f8fafc;
}

.brand-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.brand-logo {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: white;
  box-shadow: 0 8px 16px rgba(37, 99, 235, 0.3);
}

.brand-logo svg {
  width: 24px;
  height: 24px;
}

.brand-badge {
  font-size: 13px;
  font-weight: 600;
  color: #3b82f6;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  background: rgba(59, 130, 246, 0.1);
  padding: 4px 12px;
  border-radius: 20px;
}

.brand-content h1 {
  font-size: 42px;
  font-weight: 800;
  margin: 24px 0 16px;
  background: linear-gradient(to right, #f8fafc, #94a3b8);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.brand-subtitle {
  font-size: 16px;
  line-height: 1.6;
  color: #94a3b8;
  max-width: 400px;
}

.feature-list {
  margin-top: 40px;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  color: #e2e8f0;
}

.feature-list li svg {
  width: 18px;
  height: 18px;
  color: #10b981;
}

.brand-footer {
  font-size: 13px;
  color: #64748b;
}

/* 登录操作面板 */
.login-panel {
  padding: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.02);
}

.login-card {
  width: 100%;
  max-width: 400px;
}

.card-header {
  margin-bottom: 32px;
}

.card-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #f8fafc;
  margin: 0 0 8px;
}

.card-header p {
  font-size: 14px;
  color: #94a3b8;
  margin: 0;
}

.login-form {
  margin-bottom: 32px;
}

:deep(.el-form-item__label) {
  color: #cbd5e1 !important;
  font-weight: 500 !important;
  padding-bottom: 8px !important;
}

:deep(.el-input__wrapper) {
  background-color: rgba(30, 41, 59, 0.5) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset !important;
  border-radius: 12px !important;
  transition: all 0.2s ease !important;
}

:deep(.el-input__wrapper.is-focus) {
  background-color: rgba(30, 41, 59, 0.8) !important;
  box-shadow: 0 0 0 1px #3b82f6 inset, 0 0 0 4px rgba(59, 130, 246, 0.1) !important;
}

:deep(.el-input__inner) {
  color: #f8fafc !important;
  height: 48px !important;
}

.input-icon {
  width: 18px;
  height: 18px;
  color: #64748b;
}

.login-btn {
  width: 100%;
  height: 52px !important;
  border-radius: 12px !important;
  font-size: 16px !important;
  font-weight: 600 !important;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%) !important;
  border: none !important;
  box-shadow: 0 10px 15px -3px rgba(37, 99, 235, 0.4) !important;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

.login-btn:hover {
  transform: translateY(-2px) !important;
  box-shadow: 0 20px 25px -5px rgba(37, 99, 235, 0.5) !important;
}

.login-btn:active {
  transform: translateY(0) !important;
}

/* 快捷登录 */
.quick-login {
  position: relative;
  text-align: center;
}

.divider-text {
  font-size: 12px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.divider-text::before,
.divider-text::after {
  content: "";
  flex: 1;
  height: 1px;
  background: rgba(255, 255, 255, 0.05);
}

.account-hints {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
}

.hint-tag {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  color: #94a3b8;
  font-size: 13px;
  padding: 8px 16px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.hint-tag:hover {
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.4);
  color: #3b82f6;
}

@media (max-width: 1024px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 540px;
    min-height: auto;
  }

  .brand-panel {
    display: none;
  }

  .login-panel {
    padding: 48px 32px;
  }
}

@media (max-width: 480px) {
  .login-shell {
    border-radius: 0;
    min-height: 100vh;
  }
  
  .login-page {
    padding: 0;
  }
}
</style>

