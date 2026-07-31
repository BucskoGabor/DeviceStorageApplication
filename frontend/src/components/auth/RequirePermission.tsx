import { useEffect, type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/lib/store/authStore'

interface RequirePermissionProps {
  permission: string
  children: ReactNode
}

/**
 * RequirePermission — csak akkor rendereli a children-t, ha a user rendelkezik
 * a megadott permission-nel.
 *
 * <p>Ha nincs jog: Sonner warning toast-ot küld (a {@code permissionDenied}
 * i18n kulccsal), majd a {@code /403} route-ra navigál.
 *
 * <p>Példa: {@code <RequirePermission permission="USER_MANAGE">...</RequirePermission>}
 */
export function RequirePermission({ permission, children }: RequirePermissionProps) {
  const { t } = useTranslation()
  const accessToken = useAuthStore((state) => state.accessToken)
  const permissions = useAuthStore((state) => state.permissions)

  const isAuthenticated = !!accessToken
  const hasPermission = permissions.includes(permission)

  useEffect(() => {
    if (isAuthenticated && !hasPermission) {
      toast.warning(
        t('permissionDenied') + ` (${permission})`,
        { position: 'top-right' }
      )
    }
  }, [isAuthenticated, hasPermission, permission, t])

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (!hasPermission) {
    return <Navigate to="/403" replace />
  }

  return <>{children}</>
}
