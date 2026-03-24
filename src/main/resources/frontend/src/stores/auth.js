import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    username: localStorage.getItem('username') || '',
    roles: JSON.parse(localStorage.getItem('roles') || '[]')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => state.roles.includes('ADMIN'),
    isTeacher: (state) => state.roles.includes('TEACHER'),
    isStudent: (state) => state.roles.includes('STUDENT')
  },
  actions: {
    setAuth(payload) {
      this.token = payload.accessToken
      this.refreshToken = payload.refreshToken
      this.roles = payload.roles || []
      this.username = payload.username || ''
      localStorage.setItem('token', this.token)
      localStorage.setItem('refreshToken', this.refreshToken)
      localStorage.setItem('roles', JSON.stringify(this.roles))
      localStorage.setItem('username', this.username)
    },
    clear() {
      this.token = ''
      this.refreshToken = ''
      this.roles = []
      this.username = ''
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('roles')
      localStorage.removeItem('username')
    }
  }
})
