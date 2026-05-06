import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const isLoggedIn = ref(!!sessionStorage.getItem('nginx-gui-token'))
  const username = ref(sessionStorage.getItem('nginx-gui-user') || '')

  function login(user, password) {
    // static mock
    if (user === 'admin' && password === 'admin') {
      isLoggedIn.value = true
      username.value = user
      sessionStorage.setItem('nginx-gui-token', 'mock-token')
      sessionStorage.setItem('nginx-gui-user', user)
      return true
    }
    return false
  }

  function logout() {
    isLoggedIn.value = false
    username.value = ''
    sessionStorage.removeItem('nginx-gui-token')
    sessionStorage.removeItem('nginx-gui-user')
  }

  return { isLoggedIn, username, login, logout }
})
