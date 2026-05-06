import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  const theme = ref(localStorage.getItem('nginx-gui-theme') || 'dark')

  function setTheme(val) {
    theme.value = val
    document.documentElement.setAttribute('data-theme', val)
    localStorage.setItem('nginx-gui-theme', val)
  }

  function toggle() {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  // init
  document.documentElement.setAttribute('data-theme', theme.value)

  return { theme, setTheme, toggle }
})
