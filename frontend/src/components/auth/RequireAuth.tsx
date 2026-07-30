import { useEffect, type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuthStore } from '@/lib/store/authStore'
import { authApi } from '@/features/auth/api/authApi'

interface RequireAuthProps {
  children: ReactNode
}

/**
 * RequireAuth — wrapper komponens, ami csak authentikált user-ek számára
 * rendereli a children-t. Ha nincs access token, redirect /login.
 *
 * <p>Ha van access token, de a role/permissions null (F5 / page reload),
 * a me endpoint-ot hívja, hogy újra betöltse a state-et a SecurityContext-ből.
 */
export function RequireAuth({ children }: RequireAuthProps) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const role = useAuthStore((state) => state.role)
  const setAuth = useAuthStore((state) => state.setAuth)

  useEffect(() => {
    if (accessToken && !role) {
      authApi.me()
        .then((me) => {
          setAuth(accessToken, me.emailHash, me.role, me.permissions, me.mustChangePassword)
        })
        .catch(() => {
          // 401 esetén a silent refresh + failed login redirect
        })
    }
  }, [accessToken, role, setAuth])

  if (!accessToken) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
