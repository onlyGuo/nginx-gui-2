/**
 * Centralized fetch wrapper with auto token injection and 401 handling.
 */
export async function api(url, options = {}) {
  const token = sessionStorage.getItem('nginx-gui-token')

  const headers = { ...(options.headers || {}) }
  if (token) {
    headers['Authorization'] = 'Bearer ' + token
  }
  // Don't set Content-Type for FormData (browser sets boundary automatically)
  if (!(options.body instanceof FormData) && !headers['Content-Type'] && options.body) {
    headers['Content-Type'] = 'application/json'
  }

  const res = await fetch(url, { ...options, headers })

  if (res.status === 401) {
    // Token expired or invalid — clear auth and redirect to login
    sessionStorage.removeItem('nginx-gui-token')
    sessionStorage.removeItem('nginx-gui-user')
    window.location.hash = '#/login'
    throw new Error('未登录或登录已过期')
  }

  return res
}

/**
 * Build URL with token query param (for EventSource/SSE which can't set headers).
 */
export function sseUrl(url) {
  const token = sessionStorage.getItem('nginx-gui-token')
  const separator = url.includes('?') ? '&' : '?'
  return token ? url + separator + 'token=' + token : url
}
