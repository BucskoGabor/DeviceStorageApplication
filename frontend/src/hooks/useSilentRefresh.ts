import { useEffect } from 'react'
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
 * <p>Ha a /api/auth/me hívás a frissítés UTÁN hibát dob, a user továbbra
 * is bejelentkezve marad (token érvényes, szerepkör ismert), de az email
 * mező {@code null} marad (nem üres string) — így az AppLayout felelős
 * dönteni a "—" placeholder megjelenítéséről.
 *
 * <p>Használat: a route guard komponensekben (RequireAuth, RequireRole).
 */
export function useSilentRefresh() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const initialRefreshDone = useAuthStore((state) => state.initialRefreshDone)
  const setAuth = useAuthStore((state) => state.setAuth)
  const clearAuth = useAuthStore((state) => state.clearAuth)

  useEffect(() => {
    // Csak akkor fut, ha NINCS access token ÉS még nem próbálkoztunk
    if (!accessToken && !initialRefreshDone) {
      authApi
        .refresh()
        .then(async (resp) => {
          // 1) Refresh response-ból beállítjuk a tokent + role + permissions-t.
          //    Az email-t egyelőre null hagyjuk — a /me hívás felülírja, ha sikerül.
          setAuth(resp.accessToken, null, resp.role, resp.permissions, resp.mustChangePassword)

          // 2) Az email-t és a frissített role/permissions-t külön /me hívással
          //    töltjük, hogy a display (AppLayout) biztosan valós email-t mutasson.
          try {
            const me = await authApi.me()
            setAuth(
              resp.accessToken,
              me.email ?? null,
              me.role,
              me.permissions,
              me.mustChangePassword
            )
          } catch (err) {
            // A /me fail (pl. backend bug, hálózati hiba) — a user továbbra is
            // be van jelentkezve (token + role ismert), csak az email marad null.
            // NE állítsuk vissza üres stringre, mert az AppLayout "—"-ként kezeli.
            // eslint-disable-next-line no-console
            console.warn('[useSilentRefresh] /api/auth/me hívás sikertelen — email marad null', err)
          }
        })
        .catch(() => {
          // Refresh token is lejárt vagy nincs → clearAuth + initialRefreshDone: true
          clearAuth()
        })
    }
    // A setInitialRefreshDone setter, soha nem hívódik közvetlenül — a setAuth
    // és a clearAuth mellékhatásként állítja be az initialRefreshDone-t.
    // A dependency array-ben csak a ténylegesen használt action-ök szerepelnek.
  }, [accessToken, initialRefreshDone, setAuth, clearAuth])
}
