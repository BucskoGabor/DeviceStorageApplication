import { type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuthStore } from '@/lib/store/authStore'

interface RequirePermissionProps {
  permissions?: string[]
  anyPermission?: string[]
  children: ReactNode
}

/**
 * RequirePermission — 100% permission-based route guard.
 *
 * Role is NOT checked directly; permissions array in authStore includes both
 * role-inherited permissions AND direct user permissions.
 */
export function RequirePermission({
  permissions,
  anyPermission,
  children,
}: RequirePermissionProps) {
  const accessToken = useAuthStore((state) => state.accessToken)
  const userPermissions = useAuthStore((state) => state.permissions)

  if (!accessToken) {
    return <Navigate to="/login" replace />
  }

  let hasAccess = true

  if (permissions && permissions.length > 0) {
    const hasAll = permissions.every((p) => userPermissions.includes(p))
    if (!hasAll) hasAccess = false
  }

  if (anyPermission && anyPermission.length > 0) {
    const hasAny = anyPermission.some((p) => userPermissions.includes(p))
    if (!hasAny) hasAccess = false
  }

  if (!hasAccess) {
    return <Navigate to="/403" replace />
  }

  return <>{children}</>
}
