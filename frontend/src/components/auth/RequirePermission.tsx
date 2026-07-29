import { type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuthStore } from '@/lib/store/authStore'

interface RequirePermissionProps {
  permission: string
  children: ReactNode
}

/**
 * RequirePermission — csak akkor rendereli a children-t, ha a user rendelkezik
 * a megadott permission-nel.
 *
 * Példa: <RequirePermission permission="USER_MANAGE">...</RequirePermission>
 */
export function RequirePermission({ permission, children }: RequirePermissionProps) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const permissions = useAuthStore((state) => state.permissions)

  if (!accessToken) {
    return <Navigate to="/login" replace />
  }

  if (!permissions.includes(permission)) {
    return <Navigate to="/403" replace />
  }

  return <>{children}</>
}
