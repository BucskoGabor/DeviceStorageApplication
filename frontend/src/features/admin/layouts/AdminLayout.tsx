import { type ReactNode } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  Users,
  Laptop,
  MapPin,
  FileText,
  Shield,
  Upload,
  LayoutDashboard,
} from 'lucide-react'
import { DashboardPage } from '@/features/auth/pages/DashboardPage'
import { cn } from '@/lib/utils'

/**
 * AdminLayout — ROLE_ADMIN route-ok layout-ja: Sidebar + Header + Main content.
 *
 * Az oldalsó menüben az admin aloldalak linkjei. A NavLink aktív státuszt
 * kap a jelenlegi route alapján (React Router).
 */
const SIDEBAR_WIDTH = 'w-56' // 224px

const sidebarItems = [
  { to: '/admin', icon: LayoutDashboard, labelKey: 'admin.title', end: true },
  { to: '/admin/users', icon: Users, labelKey: 'admin.users' },
  { to: '/admin/devices', icon: Laptop, labelKey: 'admin.devices' },
  { to: '/admin/locations', icon: MapPin, labelKey: 'admin.locations' },
  { to: '/admin/software', icon: Shield, labelKey: 'admin.softwares' },
  { to: '/admin/audit', icon: FileText, labelKey: 'admin.audit' },
  { to: '/admin/import', icon: Upload, labelKey: 'admin.import' },
] as const

interface AdminLayoutProps {
  children?: ReactNode
}

export function AdminLayout({ children }: AdminLayoutProps) {
  const { t } = useTranslation()
  const location = useLocation()

  return (
    <DashboardPage>
      <div className="flex gap-6">
        {/* Sidebar */}
        <aside className={cn('flex-shrink-0', SIDEBAR_WIDTH)}>
          <nav className="sticky top-4 flex flex-col gap-1 rounded-lg border border-border bg-card p-2">
            {sidebarItems.map((item) => {
              const Icon = item.icon
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={'end' in item ? item.end : false}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                      isActive
                        ? 'bg-primary text-primary-foreground'
                        : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                    )
                  }
                >
                  <Icon className="h-4 w-4" />
                  <span>{t(item.labelKey)}</span>
                </NavLink>
              )
            })}
          </nav>
        </aside>

        {/* Main content */}
        <main className="flex-1">{children ?? <Outlet />}</main>
      </div>
    </DashboardPage>
  )
}
