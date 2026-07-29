import { useTranslation } from 'react-i18next'
import { Moon, Sun } from 'lucide-react'
import { useTheme } from 'next-themes'
import { LoginForm } from '@/features/auth/components/LoginForm'
import { LanguageSelector } from '@/components/LanguageSelector'
import { Button } from '@/components/ui/button'

/**
 * LoginPage — bejelentkezési oldal.
 *
 * Tartalom:
 * - App cím és alcím
 * - LoginForm (email + password)
 * - LanguageSelector (HU/EN)
 * - ThemeToggle (light/dark)
 */
export function LoginPage() {
  const { t } = useTranslation()
  const { theme, setTheme } = useTheme()

  const toggleTheme = () => {
    setTheme(theme === 'dark' ? 'light' : 'dark')
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <div className="w-full max-w-md rounded-lg border border-border bg-card p-8 shadow-sm">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-2xl font-bold">{t('appName')}</h1>
          <div className="flex items-center gap-2">
            <LanguageSelector />
            <Button
              variant="ghost"
              size="icon"
              onClick={toggleTheme}
              aria-label={t(theme === 'dark' ? 'login.themeLight' : 'login.themeDark')}
            >
              {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </Button>
          </div>
        </div>

        <p className="mb-6 text-sm text-muted-foreground">{t('login.subtitle')}</p>

        <LoginForm />
      </div>
    </div>
  )
}
