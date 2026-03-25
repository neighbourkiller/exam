import { defineStore } from 'pinia'

function parseJwtPayload(token) {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const json = atob(padded)
    return JSON.parse(json)
  } catch {
    return null
  }
}

function isExpired(token) {
  const payload = parseJwtPayload(token)
  if (!payload || !payload.exp) return true
  return payload.exp * 1000 <= Date.now()
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    username: localStorage.getItem('username') || '',
    roles: JSON.parse(localStorage.getItem('roles') || '[]')
  }),
  getters: {
    isLogin: (state) => !!state.token && !isExpired(state.token),
    isAdmin: (state) => state.roles.includes('ADMIN'),
    isTeacher: (state) => state.roles.includes('TEACHER'),
    isStudent: (state) => state.roles.includes('STUDENT')
  },
  actions: {
    ensureSession() {
      if (!this.token) {
        return false
      }
      if (isExpired(this.token)) {
        this.clear()
        return false
      }
      return true
    },
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