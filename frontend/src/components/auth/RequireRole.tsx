import { type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuthStore } from '@/lib/store/authStore'

interface RequireRoleProps {
  roles: string[]
  children: ReactNode
}

/**
 * RequireRole — csak akkor rendereli a children-t, ha a user role-ja
 * a megadott role-ok listájában van.
 *
 * Példa: <RequireRole roles={['ROLE_ADMIN']}>...</RequireRole>
 *
 * Ha nincs token: redirect /login.
 * Ha van token, de nincs meg a role: redirect /403.
 */
export function RequireRole({ roles, children }: RequireRoleProps) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const userRole = useAuthStore((state) => state.role)

  if (!accessToken) {
    return <Navigate to="/login" replace />
  }

  if (!userRole || !roles.includes(userRole)) {
    return <Navigate to="/403" replace />
  }

  return <>{children}</>
}
