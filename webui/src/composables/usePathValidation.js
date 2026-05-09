import { ref } from 'vue'
import { api } from '../utils/api'

const valid = ref(true)
const checking = ref(true)

async function check() {
  checking.value = true
  try {
    const res = await api('/api/v1/paths/validate')
    const json = await res.json()
    valid.value = json.data?.valid ?? false
  } catch {
    valid.value = false
  } finally {
    checking.value = false
  }
}

export function usePathValidation() {
  return { valid, checking, check }
}
