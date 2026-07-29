import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'

/**
 * Axios HTTP kliens a backend API-hoz.
 *
 * Funkciók:
 * - withCredentials: true (refresh token cookie automatikus csatolás)
 * - Bearer token az Authorization headerben
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

// ===== Access Token Memory Storage =====
// A refresh token HttpOnly cookie-ban van, de az access token memory-ban
// (Zustand store vagy React Context). Most egyszerűsítve egy modul-szintű változó.

let accessToken: string | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

// ===== Request Interceptor: Bearer token + CSRF =====

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Access token hozzáadása
    if (accessToken) {
      config.headers.set('Authorization', `Bearer ${accessToken}`)
    }

    // CSRF token hozzáadása state-changing kéréseknél
    const isStateChanging = !['get', 'head', 'options'].includes(
      (config.method ?? 'get').toLowerCase()
    )
    if (isStateChanging) {
      // A XSRF-TOKEN cookie-ból olvassuk ki
      const cookies = document.cookie.split(';')
      const xsrfToken = cookies
        .find((c) => c.trim().startsWith('XSRF-TOKEN='))
        ?.split('=')[1]
      if (xsrfToken) {
        config.headers.set('X-XSRF-TOKEN', decodeURIComponent(xsrfToken))
      }
    }

    return config
  }
)

// ===== Refresh-in-progress lock =====

let refreshPromise: Promise<string | null> | null = null

async function performRefresh(): Promise<string | null> {
  try {
    const response = await axios.post(
      `${baseURL}/api/auth/refresh`,
      {},
      { withCredentials: true }
    )
    const newAccessToken = response.data.accessToken as string
    setAccessToken(newAccessToken)
    return newAccessToken
  } catch {
    return null
  }
}

// ===== Response Interceptor: 401 silent refresh =====

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean }

    if (error.response?.status === 401 && !originalRequest._retry) {
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
      window.location.href = '/login'
    }

    return Promise.reject(error)
  }
)