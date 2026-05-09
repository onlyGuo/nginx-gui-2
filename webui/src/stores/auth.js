import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '../utils/api'

export const useAuthStore = defineStore('auth', () => {
  const isLoggedIn = ref(!!sessionStorage.getItem('nginx-gui-token'))
  const username = ref(sessionStorage.getItem('nginx-gui-user') || '')
  const role = ref(sessionStorage.getItem('nginx-gui-role') || '')
  const isAdmin = computed(() => role.value === 'ADMIN')

  async function login(user, password) {
    const res = await api('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: user, password })
    })
    const data = await res.json()
    if (data.code === 200) {
      isLoggedIn.value = true
      username.value = data.data.username
      role.value = data.data.role
      sessionStorage.setItem('nginx-gui-token', data.data.token)
      sessionStorage.setItem('nginx-gui-user', data.data.username)
      sessionStorage.setItem('nginx-gui-role', data.data.role)
      return true
    }
    return false
  }

  async function logout() {
    try {
      await api('/api/v1/auth/logout', { method: 'POST' })
    } catch (e) {
      // Ignore network errors during logout
    }
    isLoggedIn.value = false
    username.value = ''
    role.value = ''
    sessionStorage.removeItem('nginx-gui-token')
    sessionStorage.removeItem('nginx-gui-user')
    sessionStorage.removeItem('nginx-gui-role')
  }

  return { isLoggedIn, username, role, isAdmin, login, logout }
})
