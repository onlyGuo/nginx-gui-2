import { ref } from 'vue'

const valid = ref(true)
const checking = ref(true)

async function check() {
  checking.value = true
  try {
    const res = await fetch('/api/v1/paths/validate')
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
