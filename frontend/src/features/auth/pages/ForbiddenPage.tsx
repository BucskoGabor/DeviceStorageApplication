import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'

/**
 * ForbiddenPage — 403-as hibaoldal.
 *
 * Akkor jelenik meg, ha a user be van jelentkezve, de nincs meg a szükséges
 * role/permission egy adott oldal eléréséhez.
 */
export function ForbiddenPage() {
  const { t } = useTranslation()

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <div className="max-w-md text-center">
        <h1 className="mb-4 text-3xl font-bold">{t('permissionDenied')}</h1>
        <p className="mb-6 text-muted-foreground">{t('permissionDenied')}</p>
        <Button asChild>
          <Link to="/my-dashboard">{t('nav.dashboard')}</Link>
        </Button>
      </div>
    </div>
  )
}
