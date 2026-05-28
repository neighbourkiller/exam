<template>
  <el-card class="page-card user-page-card">
    <template #header>
      <div class="page-header">
        <div>
          <div class="header">用户管理</div>
          <div class="header-subtitle">维护账号信息、启用状态和角色授权</div>
        </div>
        <div class="header-actions">
          <el-button @click="load">刷新</el-button>
          <el-button type="primary" @click="openCreate">新增用户</el-button>
        </div>
      </div>
    </template>

    <section class="filter-panel">
      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="用户名 / 姓名 / 学号"
            class="keyword-input"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-panel">
      <div class="table-toolbar">
        <div class="table-title">
          <span>用户列表</span>
          <small>共 {{ total }} 人，已选 {{ selectedRows.length }} 人</small>
        </div>
        <div class="batch-toolbar">
          <el-button size="small" :disabled="!selectedRows.length" @click="batchUser('ENABLE')">批量启用</el-button>
          <el-button size="small" :disabled="!selectedRows.length" @click="batchUser('DISABLE')">批量禁用</el-button>
          <el-select
            v-model="batchRoleIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
            placeholder="分配角色"
            class="batch-select"
          >
            <el-option v-for="role in roleOptions" :key="role.id" :label="`${role.name}(${role.code})`" :value="role.id" />
          </el-select>
          <el-button size="small" :disabled="!selectedRows.length || !batchRoleIds.length" @click="batchAssignRoles">应用角色</el-button>
        </div>
      </div>

      <el-table :data="users" v-loading="loading" class="user-table" empty-text="暂无用户数据" @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="ID" width="110" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="studentNo" label="学号" min-width="130">
          <template #default="{ row }">{{ row.studentNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="scope">
            <div class="role-tags">
              <el-tag
                v-for="role in scope.row.roles || []"
                :key="role.id || role.code"
                :type="roleTagType(role.code)"
                size="small"
                effect="plain"
              >
                {{ role.name || role.code }}
              </el-tag>
              <span v-if="!(scope.row.roles || []).length" class="muted-text">-</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="教学班" min-width="220">
          <template #default="scope">
            <div class="class-list">
              <span v-if="!(scope.row.teachingClasses || []).length" class="muted-text">-</span>
              <span
                v-for="item in scope.row.teachingClasses || []"
                :key="item.id"
                class="class-item"
                :title="`${item.id} - ${item.name || ''}`"
              >
                {{ item.id }} - {{ item.name || '-' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'info'" size="small">
              {{ scope.row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openEdit(scope.row)">修改</el-button>
            <el-button link type="warning" @click="reset(scope.row)">重置密码</el-button>
            <el-button link type="danger" @click="del(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        class="pager"
        @current-change="load"
        @size-change="handleSizeChange"
      />
    </section>

    <el-dialog v-model="createDialogVisible" title="新增用户" width="640px" destroy-on-close>
      <el-form :model="form" label-position="top" class="user-form">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="用户名"><el-input v-model="form.username" placeholder="学生可用学号自动填充" /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="密码"><el-input v-model="form.password" show-password /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="角色">
              <el-select v-model="form.roleIds" multiple collapse-tags collapse-tags-tooltip>
                <el-option v-for="role in roleOptions" :key="role.id" :label="`${role.name}(${role.code})`" :value="role.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="createFormIsStudent" :xs="24" :sm="12">
            <el-form-item label="学号">
              <el-input v-model="form.studentNo" clearable placeholder="学生角色可填写学号" />
            </el-form-item>
          </el-col>
          <el-col v-if="createFormIsStudent" :xs="24" :sm="12">
            <el-form-item label="教学班">
              <el-select v-model="form.teachingClassIds" multiple filterable clearable collapse-tags placeholder="可选多个教学班">
                <el-option
                  v-for="item in teachingClassOptions"
                  :key="item.id"
                  :label="teachingClassLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="create">创建用户</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="修改用户" width="560px">
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="editForm.realName" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
        <template v-if="editForm.isStudent">
          <el-form-item label="学号">
            <el-input v-model="editForm.studentNo" clearable />
          </el-form-item>
          <el-form-item label="教学班">
            <el-select v-model="editForm.teachingClassIds" multiple filterable clearable placeholder="可选多个教学班">
              <el-option
                v-for="item in teachingClassOptions"
                :key="item.id"
                :label="teachingClassLabel(item)"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, reactive, ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchUsersApi,
  listTeachingClassesApi,
  createUserApi,
  deleteUserApi,
  queryUsersApi,
  resetPasswordApi,
  rolesApi,
  updateUserApi
} from '../../api'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '' })
const users = ref([])
const total = ref(0)
const loading = ref(false)
const roleOptions = ref([])
const teachingClassOptions = ref([])
const selectedRows = ref([])
const batchRoleIds = ref([])
const createDialogVisible = ref(false)
const editDialogVisible = ref(false)
const editForm = reactive({
  id: null,
  username: '',
  realName: '',
  enabled: true,
  studentNo: '',
  teachingClassIds: [],
  isStudent: false
})

const form = reactive({
  username: '',
  realName: '',
  password: '',
  roleIds: [],
  studentNo: '',
  teachingClassIds: []
})

const teachingClassLabel = (item) => `${item.id} - ${item.name || ''} (${item.subjectName || '未知课程'})`
const roleTagType = (code) => {
  const typeMap = {
    ADMIN: 'danger',
    TEACHER: 'warning',
    STUDENT: 'success'
  }
  return typeMap[code] || 'info'
}

const createFormIsStudent = computed(() => {
  const studentRole = roleOptions.value.find((role) => role.code === 'STUDENT')
  if (!studentRole) {
    return false
  }
  return (form.roleIds || []).some((roleId) => String(roleId) === String(studentRole.id))
})

watch(createFormIsStudent, (isStudent) => {
  if (!isStudent) {
    form.studentNo = ''
    form.teachingClassIds = []
  }
})

watch(
  () => form.studentNo,
  (value, oldValue) => {
    if (!createFormIsStudent.value) return
    const oldText = oldValue || ''
    if (!form.username || form.username === oldText) {
      form.username = value || ''
    }
  }
)

const loadRoles = async () => {
  roleOptions.value = await rolesApi()
}

const loadTeachingClasses = async () => {
  teachingClassOptions.value = await listTeachingClassesApi()
}

const load = async () => {
  loading.value = true
  try {
    const page = await queryUsersApi({
      ...query,
      keyword: (query.keyword || '').trim()
    })
    users.value = page.records || []
    total.value = page.total || 0
    selectedRows.value = []
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.pageNum = 1
  load()
}

const resetSearch = () => {
  query.keyword = ''
  search()
}

const handleSizeChange = () => {
  query.pageNum = 1
  load()
}

const resetCreateForm = () => {
  form.username = ''
  form.realName = ''
  form.password = ''
  form.roleIds = []
  form.studentNo = ''
  form.teachingClassIds = []
}

const openCreate = () => {
  resetCreateForm()
  createDialogVisible.value = true
}

const create = async () => {
  const realName = (form.realName || '').trim()
  if (!realName) {
    ElMessage.warning('请输入姓名')
    return
  }
  const isStudent = createFormIsStudent.value
  const studentNo = isStudent ? ((form.studentNo || '').trim() || null) : null
  const username = ((form.username || '').trim()) || (isStudent ? (studentNo || '') : '')
  if (!username) {
    ElMessage.warning('请输入用户名')
    return
  }
  const payload = {
    username,
    realName,
    password: form.password,
    roleIds: form.roleIds,
    studentNo,
    teachingClassIds: isStudent ? form.teachingClassIds : []
  }
  await createUserApi(payload)
  ElMessage.success('创建成功')
  resetCreateForm()
  createDialogVisible.value = false
  await load()
}

const openEdit = (row) => {
  const roles = (row.roles || []).map((r) => r.code)
  const isStudent = roles.includes('STUDENT')
  editForm.id = row.id
  editForm.username = row.username || ''
  editForm.realName = row.realName || ''
  editForm.enabled = row.enabled !== false
  editForm.studentNo = row.studentNo || ''
  editForm.teachingClassIds = isStudent ? (row.teachingClasses || []).map((c) => c.id) : []
  editForm.isStudent = isStudent
  editDialogVisible.value = true
}

const saveEdit = async () => {
  const realName = (editForm.realName || '').trim()
  if (!realName) {
    ElMessage.warning('请输入姓名')
    return
  }
  const payload = {
    realName,
    enabled: !!editForm.enabled,
    studentNo: editForm.isStudent ? ((editForm.studentNo || '').trim() || null) : null,
    teachingClassIds: editForm.isStudent ? editForm.teachingClassIds : null
  }
  await updateUserApi(editForm.id, payload)
  ElMessage.success('用户更新成功')
  editDialogVisible.value = false
  await load()
}

const del = async (id) => {
  await ElMessageBox.confirm('确认删除该用户？', '提示')
  await deleteUserApi(id)
  ElMessage.success('删除成功')
  await load()
}

const reset = async (row) => {
  const { value } = await ElMessageBox.prompt(`请输入 ${row.username} 的新密码`, '重置密码', {
    inputType: 'password',
    inputPlaceholder: '请输入新密码',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
  await resetPasswordApi(row.id, { password: value })
  ElMessage.success(`已更新 ${row.username} 的密码`)
}

const batchUser = async (action) => {
  await batchUsersApi({
    userIds: selectedRows.value.map((item) => item.id),
    action
  })
  ElMessage.success('批量操作成功')
  await load()
}

const batchAssignRoles = async () => {
  await batchUsersApi({
    userIds: selectedRows.value.map((item) => item.id),
    action: 'ASSIGN_ROLES',
    roleIds: batchRoleIds.value
  })
  ElMessage.success('角色分配成功')
  batchRoleIds.value = []
  await load()
}

onMounted(async () => {
  await loadRoles()
  await loadTeachingClasses()
  await load()
})
</script>

<style scoped>
.user-page-card {
  border: none;
  border-radius: 16px;
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.02);
  background-color: #ffffff;
}

:deep(.el-card__header) {
  border-bottom: 1px solid #eef2f7;
  padding: 20px 24px;
}

:deep(.el-card__body) {
  padding: 20px 24px 24px;
}

.page-header,
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header { 
  font-size: 20px; 
  font-weight: 700; 
  color: #1e293b;
  line-height: 1.3;
}

.header-subtitle {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.filter-panel {
  padding: 14px 16px;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  background: #f8fafc;
}

.filter-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-form :deep(.el-form-item) {
  margin: 0;
}

.keyword-input {
  width: 300px;
}

.table-panel {
  margin-top: 18px;
}

.table-toolbar {
  margin-bottom: 12px;
}

.table-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 160px;
}

.table-title span {
  color: #1e293b;
  font-size: 16px;
  font-weight: 700;
}

.table-title small {
  color: #64748b;
  font-size: 13px;
}

.batch-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.batch-select {
  width: 240px;
}

:deep(.el-table) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-table th.el-table__cell) {
  background-color: #f8fafc;
  color: #475569;
  font-weight: 600;
  border-bottom: 1px solid #f1f5f9;
}

:deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid #f1f5f9;
  padding: 12px 0;
}

:deep(.el-table--enable-row-hover .el-table__body tr:hover > td.el-table__cell) {
  background-color: #f8fafc;
}

.user-table :deep(.el-button + .el-button) {
  margin-left: 6px;
}

.role-tags,
.class-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.class-item {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #334155;
  font-size: 13px;
}

.muted-text {
  color: #94a3b8;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.user-form :deep(.el-select) {
  width: 100%;
}

:deep(.el-input__wrapper),
:deep(.el-select__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background-color: #f8fafc;
  transition: all 0.2s ease;
}

:deep(.el-input__wrapper.is-focus),
:deep(.el-select__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #bfdbfe inset, 0 0 0 1px #3b82f6 inset;
  background-color: #ffffff;
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
}

:deep(.el-dialog) {
  border-radius: 16px;
  max-width: calc(100vw - 32px);
}

@media (max-width: 960px) {
  .page-header,
  .table-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions,
  .batch-toolbar {
    justify-content: flex-start;
  }

  .keyword-input,
  .batch-select {
    width: 100%;
  }
}

</style>
