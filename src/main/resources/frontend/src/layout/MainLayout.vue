<template>
  <div class="layout-root">
    <aside class="sidebar">
      <div class="logo">Exam MVP</div>
      <el-menu :default-active="active" @select="onSelect" class="menu" unique-opened>
        <el-sub-menu v-if="isTeacher" index="teacher">
          <template #title>教师端</template>
          <el-menu-item index="/teacher/questions">题库管理</el-menu-item>
          <el-menu-item index="/teacher/papers">组卷管理</el-menu-item>
          <el-menu-item index="/teacher/exams">考试发布</el-menu-item>
          <el-menu-item index="/teacher/grading">主观阅卷</el-menu-item>
          <el-menu-item index="/teacher/analytics">数据看板</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="isStudent" index="student">
          <template #title>学生端</template>
          <el-menu-item index="/student/exams">我的考试</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="isAdmin" index="admin">
          <template #title>管理端</template>
          <el-menu-item index="/admin/users">用户管理</el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div class="bottom">
        <div class="user">{{ auth.username || '未登录' }}</div>
        <el-button size="small" type="danger" plain @click="logout">退出</el-button>
      </div>
    </aside>

    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const active = computed(() => route.path)
const isAdmin = computed(() => auth.roles.includes('ADMIN'))
const isTeacher = computed(() => auth.roles.includes('TEACHER') || auth.roles.includes('ADMIN'))
const isStudent = computed(() => auth.roles.includes('STUDENT'))

const onSelect = (index) => {
  router.push(index)
}

const logout = () => {
  auth.clear()
  router.push('/login')
}
</script>

<style scoped>
.layout-root {
  height: 100vh;
  display: grid;
  grid-template-columns: 250px 1fr;
}

.sidebar {
  background: linear-gradient(180deg, #0f766e 0%, #115e59 100%);
  color: white;
  display: flex;
  flex-direction: column;
}

.logo {
  padding: 18px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
}

.menu {
  flex: 1;
  background: transparent;
  border-right: none;
}

.bottom {
  padding: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user {
  font-size: 13px;
}

.content {
  padding: 18px;
  overflow: auto;
}

@media (max-width: 900px) {
  .layout-root {
    grid-template-columns: 1fr;
  }

  .sidebar {
    height: auto;
  }
}
</style>
