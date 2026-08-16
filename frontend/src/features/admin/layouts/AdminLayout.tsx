import { type ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  Users,
  Laptop,
  MapPin,
  FileText,
  Shield,
  ShieldCheck,
  Upload,
  LayoutDashboard,
  ClipboardCheck,
} from 'lucide-react'
import { DashboardPage } from '@/features/auth/pages/DashboardPage'
import { cn } from '@/lib/utils'

const SIDEBAR_WIDTH = 'w-56' // 224px

const sidebarItems = [
  { to: '/admin', icon: LayoutDashboard, labelKey: 'admin.title', end: true },
  { to: '/admin/users', icon: Users, labelKey: 'admin.users' },
  { to: '/admin/roles', icon: ShieldCheck, labelKey: 'admin.roles' },
  { to: '/admin/devices', icon: Laptop, labelKey: 'admin.devices' },
  { to: '/admin/locations', icon: MapPin, labelKey: 'admin.locations' },
  { to: '/admin/software', icon: Shield, labelKey: 'admin.softwares' },
  { to: '/admin/approvals', icon: ClipboardCheck, labelKey: 'assignments.approvalQueue' },
  { to: '/admin/audit', icon: FileText, labelKey: 'admin.audit' },
  { to: '/admin/import', icon: Upload, labelKey: 'admin.import' },
] as const

import { useAuthStore } from '@/lib/store/authStore'

interface AdminLayoutProps {
  children?: ReactNode
}

export function AdminLayout({ children }: AdminLayoutProps) {
  const { t } = useTranslation()
  const permissions = useAuthStore((state) => state.permissions)

  const hasPermission = (perm: string) => permissions.includes(perm)
  const hasAnyPermission = (perms: string[]) => perms.some((p) => permissions.includes(p))

  const filteredItems = sidebarItems.filter((item) => {
    if (item.to === '/admin/users') return hasAnyPermission(['USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_DELETE'])
    if (item.to === '/admin/roles') return hasAnyPermission(['ROLE_READ', 'ROLE_MANAGE'])
    if (item.to === '/admin/devices') return hasAnyPermission(['DEVICE_READ', 'DEVICE_CREATE', 'DEVICE_UPDATE', 'DEVICE_DELETE', 'DEVICE_MANAGE'])
    if (item.to === '/admin/locations') return hasAnyPermission(['LOCATION_READ', 'LOCATION_CREATE', 'LOCATION_UPDATE', 'LOCATION_DELETE'])
    if (item.to === '/admin/software') return hasAnyPermission(['SOFTWARE_LICENSE_VIEW', 'SOFTWARE_CREATE', 'SOFTWARE_UPDATE', 'SOFTWARE_DELETE'])
    if (item.to === '/admin/audit') return hasPermission('AUDIT_READ')
    if (item.to === '/admin/approvals') return hasAnyPermission(['ASSIGNMENT_APPROVE', 'DEVICE_MAINTENANCE_APPROVE', 'DEVICE_DISPOSE_APPROVE'])
    if (item.to === '/admin/import') return hasPermission('IMPORT_EXECUTE')
    return true
  })

  return (
    <DashboardPage>
      <div className="flex gap-6">
        {/* Sidebar */}
        <aside className={cn('flex-shrink-0', SIDEBAR_WIDTH)}>
          <nav className="sticky top-4 flex flex-col gap-1 rounded-lg border border-border bg-card p-2">
            {filteredItems.map((item) => {
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
