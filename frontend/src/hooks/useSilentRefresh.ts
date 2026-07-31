import { useEffect, useState } from 'react'
import { useAuthStore } from '@/lib/store/authStore'
import { authApi } from '@/features/auth/api/authApi'

/**
 * useSilentRefresh — automatikus silent refresh a refresh_token cookie-ból.
 *
 * <p>Ha a user F5-öt nyom, az access token elveszik a memóriából, de a
 * refresh_token cookie megmarad. Ez a hook page loadkor meghívja a
 * {@code /api/auth/refresh} endpointot, és visszaállítja az access tokent +
 * role + permissions listát a Zustand store-ba.
 *
 * <p>Az endpoint a refresh_token cookie-ból olvas (HttpOnly, SameSite=Strict,
 * Secure), tehát a JavaScript nem fér hozzá — csak a backend olvassa. Az
 * axios kérés {@code withCredentials: true}-val küldi a cookie-t.
 *
 * <p>Sikertelen refresh (lejárt vagy hiányzó token) esetén a store-ból
 * törlődik a state, és a navigáció /login-ra történik.
 *
 * <p>Használat: a route guard komponensekben (RequireAuth, RequireRole).
 */
export function useSilentRefresh() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const setAuth = useAuthStore((state) => state.setAuth)
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const [refreshAttempted, setRefreshAttempted] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)

  useEffect(() => {
    // Csak akkor fut, ha NINCS access token ÉS még nem próbálkoztunk
    if (!accessToken && !refreshAttempted) {
      setRefreshAttempted(true)
      setIsRefreshing(true)
      authApi.refresh()
        .then((resp) => {
          // A /api/auth/refresh response shape: { accessToken, role, permissions, mustChangePassword }
          setAuth(
            resp.accessToken,
            '',
            resp.role,
            resp.permissions,
            resp.mustChangePassword
          )
          // Az emailHash-t külön /me hívással töltjük (opcionális, csak display-hez kell)
          authApi.me()
            .then((me) => {
              setAuth(
                resp.accessToken,
                me.emailHash,
                me.role,
                me.permissions,
                me.mustChangePassword
              )
            })
            .catch(() => {
              // A /me fail, de a refresh sikeres volt — a user be van jelentkezve
            })
        })
        .catch(() => {
          // Refresh token is lejárt vagy nincs → /login
          clearAuth()
        })
        .finally(() => {
          setIsRefreshing(false)
        })
    }
  }, [accessToken, refreshAttempted, setAuth, clearAuth])

  return { isRefreshing, refreshAttempted }
}
