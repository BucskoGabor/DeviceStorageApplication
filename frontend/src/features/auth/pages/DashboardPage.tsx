import { type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/lib/store/authStore'

/**
 * DashboardPage — wrapper komponens a védett route-okhoz.
 *
 * TODO Task 4.4: a tényleges dashboard layout (sidebar, nav, main content)
 * itt lesz implementálva. Most egy placeholder, ami a felső nav-ot
 * és a children tartalmat rendereli.
 */
interface DashboardPageProps {
  children?: ReactNode
}

export function DashboardPage({ children }: DashboardPageProps) {
  const { t } = useTranslation()
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const userEmail = useAuthStore((state) => state.userEmail)

  const handleLogout = () => {
    clearAuth()
    window.location.href = '/login'
  }

  return (
    <div className="min-h-screen bg-background">
      <header className="flex items-center justify-between border-b border-border bg-card p-4">
        <h1 className="text-xl font-semibold">{t('appName')}</h1>
        <div className="flex items-center gap-4">
          {userEmail && <span className="text-sm text-muted-foreground">{userEmail}</span>}
          <button
            onClick={handleLogout}
            className="rounded-md bg-primary px-3 py-1 text-sm text-primary-foreground hover:bg-primary/90"
          >
            {t('nav.logout')}
          </button>
        </div>
      </header>

      <main className="p-8">
        {children ?? <p className="text-muted-foreground">TODO Task 4.4: dashboard layout</p>}
      </main>
    </div>
  )
}
