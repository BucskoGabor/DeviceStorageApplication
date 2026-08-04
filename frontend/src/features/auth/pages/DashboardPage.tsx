import { type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/lib/store/authStore'
import { authApi } from '@/features/auth/api/authApi'

import { ThemeToggle } from '@/components/theme/ThemeToggle'

/**
 * DashboardPage — wrapper komponens a védett route-okhoz.
 *
 * A felső nav-ot és a children tartalmat rendereli.
 * Admin felhasználóknak navigációs linkek jelennek meg
 * az admin felület aloldalaihoz.
 */
interface DashboardPageProps {
  children?: ReactNode
}

export function DashboardPage({ children }: DashboardPageProps) {
  const { t } = useTranslation()
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const userEmail = useAuthStore((state) => state.userEmail)
  const role = useAuthStore((state) => state.role)
  const permissions = useAuthStore((state) => state.permissions)
  const location = useLocation()

  const handleLogout = async () => {
    try {
      await authApi.logout()
    } catch {
      // Backend logout hiba esetén is töröljük a helyi auth state-et
    } finally {
      clearAuth()
      window.location.href = '/login'
    }
  }

  const canAccessAdmin = role === 'ROLE_ADMIN' || role === 'ROLE_TEACHER' || permissions.includes('DEVICE_READ')

  const navLinks = [
    { to: '/my-dashboard', label: t('nav.dashboard', 'Saját Dashboard') },
    ...(canAccessAdmin ? [{ to: '/admin', label: t('nav.admin', 'Admin Panel') }] : []),
  ]

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-card">
        <div className="flex items-center justify-between p-4">
          <h1 className="text-xl font-semibold">{t('appName')}</h1>
          <div className="flex items-center gap-3">
            <ThemeToggle />
            {userEmail && <span className="text-sm text-muted-foreground">{userEmail}</span>}
            <Link
              to="/my-profile"
              className="rounded-md px-3 py-1 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground"
            >
              {t('nav.profile', 'Profil')}
            </Link>
            <button
              onClick={handleLogout}
              className="rounded-md bg-primary px-3 py-1 text-sm text-primary-foreground hover:bg-primary/90"
            >
              {t('nav.logout')}
            </button>
          </div>
        </div>
        {navLinks.length > 1 && (
          <nav className="flex gap-1 px-4 pb-2">
            {navLinks.map((link) => {
              const isActive =
                link.to === '/my-dashboard'
                  ? location.pathname === '/my-dashboard'
                  : location.pathname.startsWith(link.to)
              return (
                <Link
                  key={link.to}
                  to={link.to}
                  className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-primary/10 text-primary'
                      : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                  }`}
                >
                  {link.label}
                </Link>
              )
            })}
          </nav>
        )}
      </header>

      <main className="p-8">
        {children ?? <p className="text-muted-foreground">{t('dashboard.welcome', 'Üdvözöljük a Tanszéki Nyilvántartó Rendszerben!')}</p>}
      </main>
    </div>
  )
}

