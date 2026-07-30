import axios from 'axios'
import { apiClient } from '@/lib/api/axios'

/**
 * Auth API — bejelentkezés, refresh, logout, password change.
 *
 * A backend AuthController endpoint-jait hívja:
 * - POST /api/auth/login
 * - POST /api/auth/refresh
 * - POST /api/auth/logout
 * - POST /api/auth/password-change
 */

const baseURL = import.meta.env.VITE_API_BASE_URL ?? ''

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
export const authApi = {
  login,
  refresh,
  logout,
  changePassword,
}

export default authApi
