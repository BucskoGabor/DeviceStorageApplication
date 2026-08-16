import { apiClient } from '@/lib/api/axios'

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  expiresIn: number  // másodperc
  role: string  // pl. "ROLE_ADMIN"
  permissions: string[]  // pl. ["DEVICE_READ", "USER_MANAGE"]
  mustChangePassword: boolean  // first-login flag
}

export interface MeResponse {
  id: number
  email: string
  emailHash: string
  emailEncrypted: string
  emailMasked: string
  active: boolean
  role: string
  permissions: string[]
  mustChangePassword: boolean
  officeLocation?: {
    id: number
    name: string
    type: string
  } | null
}

export interface PasswordChangeRequest {
  currentPassword: string
  newPassword: string
}

/**
 * Bejelentkezés.
 *
 * Sikeres login esetén:
 * - A backend beállítja a refresh_token HttpOnly cookie-t
 * - A response.accessToken a Bearer token
 *
 * A useAuthStore.setAuth() hívása a caller felelőssége.
 */
export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/api/auth/login', request)
  return response.data
}

/**
 * Token frissítése.
 *
 * A refresh_token cookie automatikusan csatolódik a kéréshez.
 * A useAuthStore.setAccessToken() hívása a caller felelőssége.
 */
export async function refresh(): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/api/auth/refresh', {})
  return response.data
}

/**
 * Kijelentkezés — refresh_token cookie revoke.
 */
export async function logout(): Promise<void> {
  await apiClient.post('/api/auth/logout')
}

/**
 * Jelszócsere — current + new password.
 */
export async function changePassword(request: PasswordChangeRequest): Promise<void> {
  await apiClient.post('/api/auth/password-change', request)
}

/**
 * Auth API namespace export.
 */
export async function fetchMe(): Promise<MeResponse> {
  const response = await apiClient.get<MeResponse>('/api/auth/me')
  return response.data
}

export const authApi = {
  login,
  refresh,
  logout,
  changePassword,
  me: fetchMe,
}

export default authApi
