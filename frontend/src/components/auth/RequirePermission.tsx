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

  // AND szemantika: ha mindkét prop meg van adva, mindkettőnek teljesülnie kell.
  // - `permissions` (AND): a usernek MINDENT meg kell kapnia.
  // - `anyPermission` (OR): a usernek LEGALÁBB EGYET meg kell kapnia.
  // - Ha mindkettő definiálva van, a kettő együttesen is AND-ként viselkedik
  //   (a `permissions` blokkolja az `anyPermission` hatását is).
  let hasAllPermissions = true
  if (permissions && permissions.length > 0) {
    hasAllPermissions = permissions.every((p) => userPermissions.includes(p))
  }

  let hasAnyPermission = true
  if (anyPermission && anyPermission.length > 0) {
    hasAnyPermission = anyPermission.some((p) => userPermissions.includes(p))
  }

  if (!hasAllPermissions || !hasAnyPermission) {
    return <Navigate to="/403" replace />
  }

  return <>{children}</>
}
