import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { FileQuestion, ArrowLeft, Home } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * NotFoundPage — 404-es hibaoldal.
 *
 * Akkor jelenik meg, ha a user olyan URL-re navigál, ami nem létezik a route
 * konfigurációban (catch-all `*` route a routes/index.tsx-ben). Korábban a
 * catch-all néma átirányítást csinált a /my-dashboard-re, ami elrejtette a
 * deep-link-ek (pl. törölt eszközre mutató bookmark) hibáit.
 *
 * <p>Megjeleníti:
 * <ul>
 *   <li>A 404-es hibaüzenetet (i18n: notFound)</li>
 *   <li>A user által kért URL-t, hogy a hibát könnyen reprodukálhassák</li>
 *   <li>"Vissza a dashboardra" + "Vissza az előző oldalra" gombokat</li>
 * </ul>
 */
export function NotFoundPage() {
  const { t } = useTranslation()
  const location = useLocation()

  const canGoBack = typeof window !== 'undefined' && window.history.length > 1

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card className="w-full max-w-2xl">
        <CardHeader>
          <div className="flex items-center gap-3">
            <FileQuestion className="h-8 w-8 text-muted-foreground" aria-hidden="true" />
            <div className="flex-1">
              <CardTitle className="text-2xl">
                {t('errors.notFound.title', 'Az oldal nem található')}
              </CardTitle>
              <CardDescription>
                {t(
                  'errors.notFound.description',
                  'A keresett oldal nem létezik vagy át lett helyezve.'
                )}
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="rounded-md border bg-muted/40 p-3 text-sm">
            <p className="font-medium text-muted-foreground">
              {t('errors.notFound.requestedPath', 'Kért útvonal')}:
            </p>
            <code className="mt-1 block break-all font-mono text-xs">{location.pathname}</code>
          </div>

          <div className="flex flex-wrap gap-2">
            <Button asChild>
              <Link to="/my-dashboard">
                <Home className="mr-2 h-4 w-4" aria-hidden="true" />
                {t('errors.notFound.backToDashboard', 'Vissza a dashboardra')}
              </Link>
            </Button>
            {canGoBack && (
              <Button variant="outline" onClick={() => window.history.back()} type="button">
                <ArrowLeft className="mr-2 h-4 w-4" aria-hidden="true" />
                {t('errors.notFound.goBack', 'Vissza az előző oldalra')}
              </Button>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
