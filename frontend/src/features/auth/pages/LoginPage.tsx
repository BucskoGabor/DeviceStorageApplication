import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

/**
 * Bejelentkezési oldal (placeholder).
 *
 * TODO Task 4.3: teljes implementáció (form, validáció, axios POST /api/auth/login,
 * refresh token cookie tárolás, must_change_password redirect).
 */
export function LoginPage() {
  const { t } = useTranslation()

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <div className="w-full max-w-md rounded-lg border border-border bg-card p-8 shadow-sm">
        <h1 className="mb-6 text-center text-2xl font-bold">{t('login.title')}</h1>
        <p className="text-center text-sm text-muted-foreground">
          {t('common.loading')} — TODO Task 4.3
        </p>
        <Button className="mt-6 w-full">{t('login.submit')}</Button>
      </div>
    </div>
  )
}