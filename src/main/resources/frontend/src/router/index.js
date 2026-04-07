import { createRouter, createWebHistory } from 'vue-router'
import { restoreSession } from '../api/http'
import { useAuthStore } from '../stores/auth'

const LoginView = () => import('../views/auth/LoginView.vue')
const MainLayout = () => import('../layout/MainLayout.vue')
const TeacherQuestion = () => import('../views/teacher/QuestionManage.vue')
const TeacherPaper = () => import('../views/teacher/PaperGenerate.vue')
const TeacherExam = () => import('../views/teacher/ExamPublish.vue')
const TeacherProctoring = () => import('../views/teacher/ExamProctoring.vue')
const TeacherClass = () => import('../views/teacher/ClassManage.vue')
const TeacherGrading = () => import('../views/teacher/GradingView.vue')
const TeacherAnalytics = () => import('../views/teacher/AnalyticsView.vue')
const StudentExamList = () => import('../views/student/ExamList.vue')
const StudentExamDoing = () => import('../views/student/ExamDoing.vue')
const StudentResults = () => import('../views/student/StudentResults.vue')
const AdminUser = () => import('../views/admin/UserManage.vue')
const AdminCourse = () => import('../views/admin/CourseManage.vue')

const routes = [
  { path: '/login', component: LoginView },
  {
    path: '/',
    component: MainLayout,
    redirect: '/teacher/questions',
    children: [
      { path: 'teacher/questions', component: TeacherQuestion, meta: { roles: ['TEACHER', 'ADMIN'] } },
      { path: 'teacher/papers', component: TeacherPaper, meta: { roles: ['TEACHER', 'ADMIN'] } },
      { path: 'teacher/exams', component: TeacherExam, meta: { roles: ['TEACHER', 'ADMIN'] } },
      { path: 'teacher/proctoring', component: TeacherProctoring, meta: { roles: ['TEACHER', 'ADMIN'] } },
      { path: 'teacher/classes', component: TeacherClass, meta: { roles: ['TEACHER', 'ADMIN'] } },
      { path: 'teacher/grading', component: TeacherGrading, meta: { roles: ['TEACHER', 'ADMIN'] } },
      { path: 'teacher/analytics', component: TeacherAnalytics, meta: { roles: ['TEACHER', 'ADMIN'] } },
      { path: 'student/exams', component: StudentExamList, meta: { roles: ['STUDENT'] } },
      { path: 'student/results', component: StudentResults, meta: { roles: ['STUDENT'] } },
      { path: 'student/exam/:id', component: StudentExamDoing, meta: { roles: ['STUDENT'], examFullscreen: true } },
      { path: 'student/result', redirect: '/student/results', meta: { roles: ['STUDENT'] } },
      { path: 'admin/users', component: AdminUser, meta: { roles: ['ADMIN'] } },
      { path: 'admin/courses', component: AdminCourse, meta: { roles: ['ADMIN'] } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.path === '/login') {
    return true
  }

  if (!auth.isLogin) {
    const restored = await restoreSession()
    if (!restored) {
      return '/login'
    }
  }

  const allowRoles = to.meta?.roles || []
  if (!allowRoles.length) {
    return true
  }
  const hasRole = auth.roles.some((r) => allowRoles.includes(r))
  if (!hasRole) {
    if (auth.isStudent) return '/student/exams'
    if (auth.isAdmin) return '/admin/users'
    return '/teacher/questions'
  }
  return true
})

export default router
