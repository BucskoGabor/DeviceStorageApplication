import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ShieldAlert, ArrowLeft, Home } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { useAuthStore } from '@/lib/store/authStore'

/**
 * ForbiddenPage — 403-as hibaoldal.
 *
 * Akkor jelenik meg, ha a user be van jelentkezve, de nincs meg a szükséges
 * role/permission egy adott oldal eléréséhez.
 *
 * <p>Megjeleníti:
 * <ul>
 *   <li>A hibaüzenetet (i18n: permissionDenied)</li>
 *   <li>A user jelenlegi role-ját Badge-ben</li>
 *   <li>A user összes permission-jét Badge-listában</li>
 *   <li>"Vissza a dashboardra" + "Kijelentkezés" gombok</li>
 * </ul>
 */
export function ForbiddenPage() {
  const { t } = useTranslation()
  const role = useAuthStore((state) => state.role)
  const permissions = useAuthStore((state) => state.permissions)
  const clearAuth = useAuthStore((state) => state.clearAuth)

  const roleLabel = role ? t(`roles.${role}`, role) : '—'

  const handleLogout = () => {
    clearAuth()
    window.location.href = '/login'
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card className="w-full max-w-2xl">
        <CardHeader>
          <div className="mb-2 flex items-center gap-3">
            <ShieldAlert className="h-8 w-8 text-destructive" />
            <CardTitle className="text-2xl">{t('forbidden.title')}</CardTitle>
          </div>
          <CardDescription>
            {t('permissionDenied')}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div>
            <p className="mb-2 text-xs uppercase text-muted-foreground">
              {t('users.role')}
            </p>
            <Badge variant="secondary">{roleLabel}</Badge>
          </div>

          <Separator />

          <div>
            <p className="mb-2 text-xs uppercase text-muted-foreground">
              {t('myProfile.permissions')} ({permissions.length})
            </p>
            {permissions.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t('myProfile.noPermissions')}</p>
            ) : (
              <div className="flex flex-wrap gap-1">
                {permissions.map((p) => (
                  <Badge key={p} variant="outline" className="font-mono text-xs">
                    {p}
                  </Badge>
                ))}
              </div>
            )}
          </div>

          <Separator />

          <p className="text-sm text-muted-foreground">
            {t('forbidden.helpText')}
          </p>

          <div className="flex flex-wrap gap-2">
            <Button asChild>
              <Link to="/my-dashboard">
                <Home className="mr-2 h-4 w-4" />
                {t('nav.dashboard')}
              </Link>
            </Button>
            <Button variant="outline" onClick={handleLogout}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              {t('common.logout')}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
