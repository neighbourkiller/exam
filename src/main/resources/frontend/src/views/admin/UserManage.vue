<template>
  <el-card class="page-card">
    <template #header><div class="header">用户与角色管理</div></template>

    <el-form :inline="true" :model="query">
      <el-form-item label="关键字"><el-input v-model="query.keyword" clearable /></el-form-item>
      <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
    </el-form>

    <el-table :data="users" border>
      <el-table-column prop="id" label="ID" width="120" />
      <el-table-column prop="username" label="用户名" width="130" />
      <el-table-column prop="realName" label="姓名" width="130" />
      <el-table-column label="角色">
        <template #default="scope">
          {{ (scope.row.roles || []).map((r) => r.code).join(', ') }}
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="90">
        <template #default="scope">{{ scope.row.enabled ? '启用' : '禁用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="scope">
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
            <el-form-item label="班级ID"><el-input v-model="form.classId" /></el-form-item>
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
  </el-card>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createRoleApi,
  createUserApi,
  deleteUserApi,
  queryUsersApi,
  resetPasswordApi,
  rolesApi
} from '../../api'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '' })
const users = ref([])
const roleOptions = ref([])

const form = reactive({
  username: '',
  realName: '',
  password: '123456',
  roleIds: [],
  classId: ''
})

const roleForm = reactive({ code: '', name: '' })

const loadRoles = async () => {
  roleOptions.value = await rolesApi()
}

const load = async () => {
  const page = await queryUsersApi(query)
  users.value = page.records || []
}

const create = async () => {
  const payload = {
    ...form,
    classId: (form.classId || '').trim() || null
  }
  await createUserApi(payload)
  ElMessage.success('创建成功')
  form.username = ''
  form.realName = ''
  form.password = '123456'
  form.roleIds = []
  form.classId = ''
  await load()
}

const del = async (id) => {
  await ElMessageBox.confirm('确认删除该用户？', '提示')
  await deleteUserApi(id)
  ElMessage.success('删除成功')
  await load()
}

const reset = async (row) => {
  await resetPasswordApi(row.id, { password: '123456' })
  ElMessage.success(`已将 ${row.username} 密码重置为 123456`)
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
  await load()
})
</script>

<style scoped>
.header { font-size: 18px; font-weight: 700; }
</style>
