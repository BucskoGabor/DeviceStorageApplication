import { create } from 'zustand'

/**
 * Auth store — Zustand store a felhasználói authentikációs állapothoz.
 *
 * <p>A refresh token az HttpOnly Secure SameSite=Strict cookie-ban van, és
 * a böngésző automatikusan csatolja minden kéréshez (withCredentials: true).
 * Az access token memory-ban van (Zustand store), és az axios interceptor
 * Bearer header-ként csatolja.
 *
 * <p>State-ek:
 * <ul>
 *   <li>accessToken: az aktuális JWT access token
 *   <li>userEmail: a bejelentkezett user email hash-e (a token subject mező)
 *   <li>role: a user role-ja (ROLE_ADMIN / ROLE_TEACHER / ROLE_STUDENT)
 *   <li>permissions: a user permission-jeinek listája
 *   <li>mustChangePassword: true ha a user-nek first-login jelszócserét kell végrehajtania
 * </ul>
 */
interface AuthState {
  accessToken: string | null
  userEmail: string | null
  role: string | null
  permissions: string[]
  mustChangePassword: boolean
  initialRefreshDone: boolean

  // Actions
  setAuth: (token: string, email: string, role: string, permissions: string[], mustChangePassword: boolean) => void
  setAccessToken: (token: string | null) => void
  clearAuth: () => void
  setMustChangePassword: (value: boolean) => void
  setInitialRefreshDone: (value: boolean) => void
}

export const useAuthStore = create<AuthState>((set) => ({
  // Initial state (not authenticated)
  accessToken: null,
  userEmail: null,
  role: null,
  permissions: [],
  mustChangePassword: false,
  initialRefreshDone: false,

  // Set all auth state (called after login)
  setAuth: (token, email, role, permissions, mustChangePassword) =>
    set({
      accessToken: token,
      userEmail: email,
      role,
      permissions,
      mustChangePassword,
      initialRefreshDone: true,
    }),

  // Set just the access token (called after silent refresh)
  setAccessToken: (token) => set({ accessToken: token, initialRefreshDone: true }),

  // Clear all auth state (called on logout or refresh failure)
  clearAuth: () =>
    set({
      accessToken: null,
      userEmail: null,
      role: null,
      permissions: [],
      mustChangePassword: false,
      initialRefreshDone: true,
    }),

  // Set mustChangePassword flag (cleared after successful password change)
  setMustChangePassword: (value) => set({ mustChangePassword: value }),

  // Set initialRefreshDone flag
  setInitialRefreshDone: (value) => set({ initialRefreshDone: value }),
}))

/**
 * Helper: access token lekérése a Zustand store-ból (axios interceptor-hoz).
 */
export const getAccessToken = (): string | null =>
  useAuthStore.getState().accessToken

/**
 * Helper: access token beállítása a Zustand store-ban.
 */
export const setAccessToken = (token: string | null): void => {
  useAuthStore.getState().setAccessToken(token)
}
