import { useEffect, type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuthStore } from '@/lib/store/authStore'
import { authApi } from '@/features/auth/api/authApi'

interface RequireAuthProps {
  children: ReactNode
}

/**
 * RequireAuth — wrapper komponens, ami csak authentikált user-ek számára
 * rendereli a children-t.
 *
 * <p>Működése:
 * <ol>
 *   <li>Silent refresh a refresh_token cookie-ból (ha van, és még nem próbálkoztunk)</li>
 *   <li>Ha van access token ÉS van role: rendereljük a children-t</li>
 *   <li>Ha van access token DE nincs role: /api/auth/me hívás a role/permissions
 *       visszatöltéséhez (F5 page reload esetén, amikor a store üres, de a
 *       SecurityContext a JWT-ből vissza tudja állítani a role-t)</li>
 *   <li>Ha silent refresh fail ÉS nincs access token: redirect /login</li>
 * </ol>
 */
export function RequireAuth({ children }: RequireAuthProps) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const role = useAuthStore((state) => state.role)
  const setAuth = useAuthStore((state) => state.setAuth)

  // Ha van access token, de nincs role → me hívás a role/permissions betöltéséhez
  useEffect(() => {
    if (accessToken && !role) {
      authApi
        .me()
        .then((me) => {
          setAuth(
            accessToken,
            me.email || me.emailHash,
            me.role,
            me.permissions,
            me.mustChangePassword
          )
        })
        .catch(() => {
          // 401 → access token lejárt
        })
    }
  }, [accessToken, role, setAuth])

  if (!accessToken) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
