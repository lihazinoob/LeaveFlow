import axios from 'axios'

/**
 * The single Axios instance used by every API call.
 *
 * Token handling:
 *  - The access token lives only in this module's memory (never in localStorage), so an XSS bug
 *    cannot read a stored token. It is lost on page reload, and the app then restores it via /refresh.
 *  - The refresh token is an httpOnly cookie. The browser sends it to /api/auth/* automatically;
 *    this code never sees it.
 *  - When a request fails with 401, we refresh once and retry the request. Parallel 401s share the
 *    same refresh call (see refreshSession): the server rotates the refresh token on every use,
 *    so two simultaneous refreshes would look like token theft and end the session.
 */

let accessToken = null
let refreshPromise = null
let onSessionExpired = () => {}

export function setAccessToken(token) {
  accessToken = token
}

/** AuthContext registers what to do when the session can no longer be refreshed (go to login). */
export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler
}

const api = axios.create({ baseURL: '/api', withCredentials: true })

// A bare instance without interceptors, so refresh/logout can never trigger themselves in a loop.
const authApi = axios.create({ baseURL: '/api/auth', withCredentials: true })

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    const status = error.response?.status

    // Login/refresh/logout go through authApi, which has no interceptor, so they can never land here.
    if (status !== 401 || !original || original._retried) {
      return Promise.reject(error)
    }

    original._retried = true
    try {
      await refreshSession()
      return api(original)
    } catch (refreshError) {
      setAccessToken(null)
      onSessionExpired()
      return Promise.reject(refreshError)
    }
  },
)

/**
 * Exchanges the refresh cookie for a new access token (and a rotated cookie).
 * Concurrent callers get the same promise, so only one request reaches the server.
 * Resolves with { accessToken, expiresAt, user }.
 */
export function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = authApi
      .post('/refresh')
      .then(({ data }) => {
        setAccessToken(data.accessToken)
        return data
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

export async function loginRequest(email, password) {
  const { data } = await authApi.post('/login', { email, password })
  setAccessToken(data.accessToken)
  return data
}

export async function logoutRequest() {
  try {
    await authApi.post('/logout')
  } finally {
    setAccessToken(null)
  }
}

export default api
