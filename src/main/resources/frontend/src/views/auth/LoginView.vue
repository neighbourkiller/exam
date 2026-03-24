<template>
  <div class="login-page">
    <div class="login-card">
      <h1>在线考试系统</h1>
      <p>Spring Boot + Vue3 MVP</p>
      <el-form :model="form" label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="admin / teacher1 / student1" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="123456" />
        </el-form-item>
        <el-button type="primary" class="full" :loading="loading" @click="onSubmit">登录</el-button>
      </el-form>
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
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at 20% 20%, #a7f3d0, #f0fdfa 40%, #ffffff 100%);
}

.login-card {
  width: 420px;
  padding: 28px;
  border-radius: 16px;
  background: #ffffffd9;
  backdrop-filter: blur(5px);
  box-shadow: 0 18px 40px rgba(15, 118, 110, 0.22);
}

h1 {
  margin: 0;
  color: #134e4a;
}

p {
  margin-top: 8px;
  color: #64748b;
}

.full {
  width: 100%;
}
</style>
