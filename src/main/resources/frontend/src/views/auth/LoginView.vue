<template>
  <div class="login-page">
    <div class="aurora aurora-one"></div>
    <div class="aurora aurora-two"></div>
    <div class="login-shell">
      <section class="brand-panel">
        <span class="brand-badge">Exam Portal</span>
        <h1>在线考试系统</h1>
        <p class="brand-subtitle">
          稳定、安全、可追踪的考试流程，帮助教师与管理员更高效地完成教学评测。
        </p>
        <ul class="feature-list">
          <li>统一账号体系，角色权限清晰</li>
          <li>试卷发布、作答、阅卷流程闭环</li>
          <li>成绩分析结果实时可视化</li>
        </ul>
      </section>
      <section class="login-panel">
        <div class="login-card">
          <h2>欢迎回来</h2>
          <p class="card-subtitle">输入账号信息，进入你的工作台</p>
          <el-form :model="form" label-position="top" @submit.prevent="onSubmit">
            <el-form-item label="用户名">
              <el-input v-model="form.username" placeholder="admin / teacher1 / student1" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password" show-password placeholder="123456" />
            </el-form-item>
            <el-button type="primary" class="full" native-type="submit" :loading="loading">
              安全登录
            </el-button>
          </el-form>
          <div class="account-hints">
            <span>快捷账号:</span>
            <button
              v-for="account in accountHints"
              :key="account.label"
              type="button"
              @click="fillAccount(account)"
            >
              {{ account.label }}
            </button>
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
      username: form.username,
      roles: tokens.roles || []
    })
    const me = await meApi()
    auth.setAuth({
      ...tokens,
      username: me.username,
      roles: me.roles
    })
    ElMessage.success('登录成功')
    if (me.roles.includes('STUDENT')) {
      router.push('/student/exams')
    } else if (me.roles.includes('ADMIN') && !me.roles.includes('TEACHER')) {
      router.push('/admin/users')
    } else {
      router.push('/teacher/questions')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 32px;
  display: grid;
  place-items: center;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 10% 20%, rgba(16, 185, 129, 0.2), transparent 42%),
    radial-gradient(circle at 90% 10%, rgba(245, 158, 11, 0.2), transparent 36%),
    linear-gradient(130deg, #f0fdfa 0%, #f8fafc 45%, #ffffff 100%);
}

.aurora {
  position: absolute;
  border-radius: 999px;
  filter: blur(70px);
  opacity: 0.38;
  pointer-events: none;
  animation: drift 10s ease-in-out infinite alternate;
}

.aurora-one {
  width: 380px;
  height: 380px;
  background: #34d399;
  top: -120px;
  left: -80px;
}

.aurora-two {
  width: 320px;
  height: 320px;
  background: #f59e0b;
  bottom: -120px;
  right: -90px;
  animation-delay: 1.4s;
}

.login-shell {
  width: min(1040px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  border-radius: 28px;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 35px 80px rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(10px);
  position: relative;
  z-index: 2;
}

.brand-panel {
  padding: 48px 44px;
  color: #e2fef6;
  background:
    radial-gradient(circle at 0% 0%, rgba(45, 212, 191, 0.35), transparent 35%),
    linear-gradient(165deg, #0f766e 0%, #115e59 45%, #0f172a 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand-badge {
  align-self: flex-start;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: rgba(255, 255, 255, 0.13);
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.brand-panel h1 {
  margin: 18px 0 12px;
  font-size: clamp(30px, 3vw, 40px);
  line-height: 1.15;
  letter-spacing: 0.01em;
}

.brand-subtitle {
  margin: 0;
  color: rgba(241, 245, 249, 0.9);
  line-height: 1.7;
  font-size: 15px;
  max-width: 440px;
}

.feature-list {
  margin: 22px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #e6fffa;
}

.feature-list li::before {
  content: '';
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #fcd34d;
  box-shadow: 0 0 0 6px rgba(252, 211, 77, 0.16);
}

.login-panel {
  padding: 34px;
  background: linear-gradient(165deg, rgba(255, 255, 255, 0.92) 0%, rgba(248, 250, 252, 0.92) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 30px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(148, 163, 184, 0.25);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.12);
}

.login-card h2 {
  margin: 0;
  font-size: 26px;
  color: #0f172a;
}

.card-subtitle {
  margin: 10px 0 20px;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}

:deep(.el-form-item__label) {
  color: #334155;
  font-weight: 600;
  padding-bottom: 8px;
}

:deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.38) inset;
  padding: 2px 12px;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 2px rgba(16, 185, 129, 0.14),
    0 0 0 1px rgba(16, 185, 129, 0.75) inset;
}

:deep(.el-button--primary) {
  border: none;
  background: linear-gradient(120deg, #0f766e 0%, #0ea5a4 58%, #14b8a6 100%);
}

:deep(.el-button--primary:hover) {
  filter: brightness(1.04);
  transform: translateY(-1px);
}

.account-hints {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
}

.account-hints button {
  border: 1px solid rgba(148, 163, 184, 0.45);
  border-radius: 999px;
  background: #fff;
  color: #0f766e;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  padding: 8px 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.account-hints button:hover {
  border-color: rgba(20, 184, 166, 0.7);
  color: #0f172a;
  background: #ecfeff;
}

.full {
  width: 100%;
  height: 42px;
  font-weight: 700;
  transition: all 0.2s ease;
}

@keyframes drift {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(16px, -12px, 0) scale(1.08);
  }
}

@media (max-width: 980px) {
  .login-page {
    padding: 18px;
  }

  .login-shell {
    grid-template-columns: 1fr;
    max-width: 560px;
  }

  .brand-panel {
    padding: 34px 28px 24px;
  }

  .login-panel {
    padding: 24px;
  }
}

@media (max-width: 560px) {
  .brand-panel h1 {
    font-size: 30px;
  }

  .login-card {
    padding: 22px 18px;
    border-radius: 16px;
  }

  .account-hints {
    gap: 6px;
  }
}
</style>
