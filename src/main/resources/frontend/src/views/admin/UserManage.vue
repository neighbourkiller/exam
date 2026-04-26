<template>
  <el-card class="page-card">
    <template #header><div class="header">用户与角色管理</div></template>

    <el-form :inline="true" :model="query">
      <el-form-item label="关键字"><el-input v-model="query.keyword" clearable /></el-form-item>
      <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
    </el-form>

    <div class="batch-toolbar">
      <el-button size="small" :disabled="!selectedRows.length" @click="batchUser('ENABLE')">批量启用</el-button>
      <el-button size="small" :disabled="!selectedRows.length" @click="batchUser('DISABLE')">批量禁用</el-button>
      <el-select v-model="batchRoleIds" multiple clearable placeholder="批量分配角色" class="batch-select">
        <el-option v-for="role in roleOptions" :key="role.id" :label="`${role.name}(${role.code})`" :value="role.id" />
      </el-select>
      <el-button size="small" :disabled="!selectedRows.length || !batchRoleIds.length" @click="batchAssignRoles">应用角色</el-button>
    </div>

    <el-table :data="users" @selection-change="selectedRows = $event">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="id" label="ID" width="120" />
      <el-table-column prop="username" label="用户名" width="130" />
      <el-table-column prop="realName" label="姓名" width="130" />
      <el-table-column prop="studentNo" label="学号" width="150" />
      <el-table-column label="角色">
        <template #default="scope">
          {{ (scope.row.roles || []).map((r) => r.code).join(', ') }}
        </template>
      </el-table-column>
      <el-table-column label="教学班">
        <template #default="scope">
          {{ (scope.row.teachingClasses || []).map((c) => `${c.id}-${c.name || ''}`).join(', ') }}
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="90">
        <template #default="scope">{{ scope.row.enabled ? '启用' : '禁用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300">
        <template #default="scope">
          <el-button type="primary" size="small" @click="openEdit(scope.row)">修改</el-button>
          <el-button type="warning" size="small" @click="reset(scope.row)">重置密码</el-button>
          <el-button type="danger" size="small" @click="del(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider />

    <el-row :gutter="18">
      <el-col :span="12">
        <el-card>
          <template #header>新增用户</template>
          <el-form :model="form" label-width="100px">
            <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
            <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="form.password" /></el-form-item>
          <el-form-item label="角色">
              <el-select v-model="form.roleIds" multiple>
                <el-option v-for="role in roleOptions" :key="role.id" :label="`${role.name}(${role.code})`" :value="role.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="createFormIsStudent" label="学号">
              <el-input v-model="form.studentNo" clearable placeholder="学生角色可填写学号" />
            </el-form-item>
            <el-form-item v-if="createFormIsStudent" label="教学班">
              <el-select v-model="form.teachingClassIds" multiple filterable clearable placeholder="可选多个教学班">
                <el-option
                  v-for="item in teachingClassOptions"
                  :key="item.id"
                  :label="teachingClassLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-button type="primary" @click="create">创建</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>新增角色</template>
          <el-form :model="roleForm" label-width="80px">
            <el-form-item label="编码"><el-input v-model="roleForm.code" /></el-form-item>
            <el-form-item label="名称"><el-input v-model="roleForm.name" /></el-form-item>
            <el-button type="success" @click="createRole">新增角色</el-button>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

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
  createRoleApi,
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
const roleOptions = ref([])
const teachingClassOptions = ref([])
const selectedRows = ref([])
const batchRoleIds = ref([])
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

const roleForm = reactive({ code: '', name: '' })

const teachingClassLabel = (item) => `${item.id} - ${item.name || ''} (${item.subjectName || '未知课程'})`
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
  const page = await queryUsersApi(query)
  users.value = page.records || []
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
  form.username = ''
  form.realName = ''
  form.password = ''
  form.roleIds = []
  form.studentNo = ''
  form.teachingClassIds = []
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

const createRole = async () => {
  await createRoleApi(roleForm)
  ElMessage.success('角色创建成功')
  roleForm.code = ''
  roleForm.name = ''
  await loadRoles()
}

onMounted(async () => {
  await loadRoles()
  await loadTeachingClasses()
  await load()
})
</script>

<style scoped>


.page-card {
  border: none;
  border-radius: 16px;
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.02);
  background-color: #ffffff;
}

:deep(.el-card__header) {
  border-bottom: none;
  padding-bottom: 0;
}

.header { 
  font-size: 20px; 
  font-weight: 700; 
  color: #1e293b;
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

:deep(.el-input__wrapper), :deep(.el-select__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  background-color: #f8fafc;
  transition: all 0.2s ease;
}

:deep(.el-input__wrapper.is-focus), :deep(.el-select__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #bfdbfe inset, 0 0 0 1px #3b82f6 inset;
  background-color: #ffffff;
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
}

:deep(.el-dialog) {
  border-radius: 16px;
}

.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0;
}

.batch-select {
  width: 240px;
}

</style>
