<template>
  <div :class="['layout-root', { 'layout-root--exam': isExamFullscreen }]">
    <header v-if="!isExamFullscreen" class="top-nav glass-card">
      <div class="nav-container">
        <div class="logo">
          <span class="logo-icon">✨</span>
          <span class="logo-text">Exam MVP</span>
        </div>

        <el-menu 
          :default-active="active" 
          mode="horizontal" 
          @select="onSelect" 
          class="top-menu" 
          :ellipsis="false"
        >
          <!-- 学生端菜单 -->
          <template v-if="isStudent">
            <el-menu-item index="/student/exams">我的考试</el-menu-item>
            <el-menu-item index="/student/environment-check">环境检测</el-menu-item>
            <el-menu-item index="/student/results">考试结果</el-menu-item>
          </template>

          <!-- 教师端菜单 -->
          <el-sub-menu v-if="isTeacher" index="teacher">
            <template #title>教师工作台</template>
            <el-menu-item index="/teacher/exams">考试发布</el-menu-item>
            <el-menu-item index="/teacher/papers">组卷管理</el-menu-item>
            <el-menu-item index="/teacher/questions">题库管理</el-menu-item>
            <el-menu-item index="/teacher/proctoring">监考中心</el-menu-item>
            <el-menu-item index="/teacher/grading">主观阅卷</el-menu-item>
            <el-menu-item index="/teacher/classes">班级管理</el-menu-item>
            <el-menu-item index="/teacher/analytics">数据看板</el-menu-item>
          </el-sub-menu>

          <!-- 管理端菜单 -->
          <el-sub-menu v-if="isAdmin" index="admin">
            <template #title>系统管理</template>
            <el-menu-item index="/admin/users">用户管理</el-menu-item>
            <el-menu-item index="/admin/courses">课程管理</el-menu-item>
            <el-menu-item index="/admin/teaching-classes">教学班管理</el-menu-item>
            <el-menu-item index="/admin/bulk-import">批量导入</el-menu-item>
            <el-menu-item index="/admin/exam-monitor">考试监控</el-menu-item>
            <el-menu-item index="/admin/audit-logs">操作日志</el-menu-item>
          </el-sub-menu>
        </el-menu>

        <div class="user-actions">
          <div class="user-info">
            <div class="avatar">{{ auth.username?.charAt(0)?.toUpperCase() || 'U' }}</div>
            <span class="username">{{ auth.username || '未登录' }}</span>
          </div>
          <el-button size="small" type="danger" plain round @click="logout" class="logout-btn">退出</el-button>
        </div>
      </div>
    </header>

    <main :class="['content', { 'content--exam': isExamFullscreen }]">
      <div v-if="!isExamFullscreen" class="content-wrapper">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
      <router-view v-else v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logoutApi } from '../api'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const active = computed(() => route.path)
const isExamFullscreen = computed(() => Boolean(route.meta?.examFullscreen))
const isAdmin = computed(() => auth.roles.includes('ADMIN'))
const isTeacher = computed(() => auth.roles.includes('TEACHER') || auth.roles.includes('ADMIN'))
const isStudent = computed(() => auth.roles.includes('STUDENT'))

const onSelect = (index) => {
  router.push(index)
}

const logout = async () => {
  try {
    await logoutApi()
  } catch (error) {
    console.error(error)
  } finally {
    auth.clear()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-root {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--bg-main);
}

.layout-root--exam {
  display: block;
}

.top-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  border-radius: 0 !important;
  border-left: none !important;
  border-right: none !important;
  border-top: none !important;
  border-bottom: 1px solid rgba(59, 130, 246, 0.1) !important;
  background: rgba(255, 255, 255, 0.85) !important;
  box-shadow: 0 4px 24px rgba(59, 130, 246, 0.05) !important;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.nav-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
  width: 100%;
  gap: 24px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 800;
  color: var(--brand);
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.logo-icon {
  font-size: 22px;
}

.top-menu {
  flex: 1;
  border-bottom: none !important;
  background: transparent !important;
  justify-content: center;
}

/* Override Element Plus Menu Styles */
:deep(.el-menu--horizontal > .el-menu-item),
:deep(.el-menu--horizontal > .el-sub-menu .el-sub-menu__title) {
  height: 64px;
  line-height: 64px;
  font-weight: 600;
  color: var(--text-muted);
  border-bottom: 2px solid transparent;
  transition: all 0.3s;
}

:deep(.el-menu--horizontal > .el-menu-item:hover),
:deep(.el-menu--horizontal > .el-sub-menu:hover .el-sub-menu__title) {
  color: var(--brand) !important;
  background: rgba(59, 130, 246, 0.04) !important;
}

:deep(.el-menu--horizontal > .el-menu-item.is-active),
:deep(.el-menu--horizontal > .el-sub-menu.is-active .el-sub-menu__title) {
  color: var(--brand) !important;
  border-bottom: 2px solid var(--brand) !important;
  background: rgba(59, 130, 246, 0.06) !important;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand) 0%, var(--brand-hover) 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.2);
}

.username {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  white-space: nowrap;
}

.logout-btn {
  font-weight: 600;
}

.content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.content-wrapper {
  flex: 1;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.content--exam {
  display: block;
}

@media (max-width: 768px) {
  .nav-container {
    padding: 0 16px;
    gap: 12px;
  }
  .username {
    display: none;
  }
  .content-wrapper {
    padding: 16px;
  }
}
</style>
