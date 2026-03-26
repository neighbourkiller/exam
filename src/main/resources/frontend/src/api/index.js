import http from './http'

export const loginApi = (data) => http.post('/auth/login', data)
export const meApi = () => http.get('/auth/me')

export const queryQuestionsApi = (data) => http.post('/questions/query', data)
export const listQuestionSubjectsApi = () => http.get('/questions/subjects')
export const getQuestionDetailApi = (id) => http.get(`/questions/${id}`)
export const createQuestionApi = (data) => http.post('/questions', data)
export const uploadQuestionImageApi = (formData) => http.post('/questions/images/upload', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})
export const updateQuestionApi = (id, data = {}) => http.put(`/questions/${id}`, data)
export const deleteQuestionApi = (id) => http.delete(`/questions/${id}`)

export const createManualPaperApi = (data) => http.post('/papers/manual', data)
export const createAutoPaperApi = (data) => http.post('/papers/auto-generate', data)
export const paperDetailApi = (id) => http.get(`/papers/${id}`)

export const createExamApi = (data) => http.post('/exams', data)
export const publishExamApi = (id) => http.post(`/exams/${id}/publish`)
export const teacherExamsApi = () => http.get('/exams/teacher')
export const studentExamsApi = () => http.get('/exams/student')
export const startExamApi = (id) => http.post(`/exams/${id}/start`)
export const snapshotApi = (id, data) => http.post(`/exams/${id}/snapshot`, data)
export const submitExamApi = (id, data) => http.post(`/exams/${id}/submit`, data)
export const antiCheatApi = (id, data) => http.post(`/exams/${id}/anti-cheat-events`, data)

export const gradingPendingApi = () => http.get('/grading/pending')
export const gradingScoreApi = (submissionId, data) => http.post(`/grading/${submissionId}/subjective-score`, data)

export const scoreDistributionApi = (examId) => http.get(`/analytics/exams/${examId}/score-distribution`)
export const classTrendApi = (examId) => http.get(`/analytics/exams/${examId}/class-trend`)
export const wrongTopicsApi = (examId) => http.get(`/analytics/exams/${examId}/wrong-topics`)

export const queryUsersApi = (data) => http.post('/admin/users/query', data)
export const createUserApi = (data) => http.post('/admin/users', data)
export const updateUserApi = (id, data) => http.put(`/admin/users/${id}`, data)
export const deleteUserApi = (id) => http.delete(`/admin/users/${id}`)
export const resetPasswordApi = (id, data) => http.post(`/admin/users/${id}/reset-password`, data)
export const assignRolesApi = (id, data) => http.put(`/admin/users/${id}/roles`, data)
export const rolesApi = () => http.get('/admin/roles')
export const createRoleApi = (data) => http.post('/admin/roles', data)

export const listCoursesApi = () => http.get('/admin/courses')
export const createCourseApi = (data) => http.post('/admin/courses', data)
export const updateCourseApi = (id, data) => http.put(`/admin/courses/${id}`, data)
