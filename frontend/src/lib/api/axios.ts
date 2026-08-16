import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/lib/store/authStore'

/**
 * Axios HTTP kliens a backend API-hoz.
 *
 * Funkciók:
 * - withCredentials: true (refresh token cookie automatikus csatolás)
 * - Bearer token az Authorization headerben (Zustand store-ból)
 * - X-XSRF-TOKEN header state-changing kéréseknél (CSRF)
 * - Silent refresh 401-re (refresh-in-progress lock + queue)
 * - Globális hibakezelő interceptor (Sonner toast messageKey-vel)
 */

const baseURL = import.meta.env.VITE_API_BASE_URL ?? ''

export const apiClient = axios.create({
  baseURL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

// ===== Request Interceptor: Bearer token + CSRF =====

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  // Access token hozzáadása a Zustand store-ból
  const accessToken = useAuthStore.getState().accessToken
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }

  // CSRF token hozzáadása state-changing kéréseknél
  const isStateChanging = !['get', 'head', 'options'].includes(
    (config.method ?? 'get').toLowerCase()
  )
  if (isStateChanging) {
    // A XSRF-TOKEN cookie-ból olvassuk ki (Spring Security által beállítva)
    const cookies = document.cookie.split(';')
    const xsrfToken = cookies.find((c) => c.trim().startsWith('XSRF-TOKEN='))?.split('=')[1]
    if (xsrfToken) {
      config.headers.set('X-XSRF-TOKEN', decodeURIComponent(xsrfToken))
    }
  }

  return config
})

// ===== Refresh-in-progress lock =====

let refreshPromise: Promise<string | null> | null = null

async function performRefresh(): Promise<string | null> {
  try {
    const response = await axios.post(`${baseURL}/api/auth/refresh`, {}, { withCredentials: true })

    const data = response.data as {
      accessToken: string
      role?: string
      permissions?: string[]
      mustChangePassword?: boolean
    }
    const current = useAuthStore.getState()
    if (data.role && data.permissions) {
      current.setAuth(
        data.accessToken,
        current.userEmail || '',
        data.role,
        data.permissions,
        data.mustChangePassword ?? false
      )
    } else {
      current.setAccessToken(data.accessToken)
    }
    return data.accessToken
  } catch {
    // Refresh failure: clear auth state
    useAuthStore.getState().clearAuth()
    return null
  }
}

// ===== Response Interceptor: 401 silent refresh =====

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }

    // Ne próbálkozzunk refresh-sel a /login, /refresh, /logout endpointokon
    const isAuthEndpoint = error.config?.url?.includes('/api/auth/')
    if (error.response?.status === 401 && !originalRequest._retry && !isAuthEndpoint) {
      originalRequest._retry = true

      // Refresh-in-progress lock: ha már fut egy refresh, várunk arra
      if (!refreshPromise) {
        refreshPromise = performRefresh()
      }

      const newToken = await refreshPromise
      refreshPromise = null

      if (newToken) {
        originalRequest.headers = {
          ...originalRequest.headers,
          Authorization: `Bearer ${newToken}`,
        }
        return apiClient.request(originalRequest)
      }

      // Refresh failure: redirect login
      useAuthStore.getState().clearAuth()
      window.location.href = '/login'
    }

    return Promise.reject(error)
  }
)

/**
 * Access token lekérdezése a Zustand store-ból.
 * Re-export a régi API kompatibilitáshoz.
 */
export const getAccessToken = (): string | null => useAuthStore.getState().accessToken

/**
 * Access token beállítása a Zustand store-ban.
 * Re-export a régi API kompatibilitáshoz.
 */
export const setAccessToken = (token: string | null): void => {
  useAuthStore.getState().setAccessToken(token)
}
