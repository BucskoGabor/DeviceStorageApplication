import { type ReactNode, useState } from 'react'
import { NavLink, Outlet, Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import {
  LayoutDashboard,
  Laptop,
  MapPin,
  Shield,
  ClipboardCheck,
  Users,
  ShieldCheck,
  FileText,
  Upload,
  User,
  LogOut,
  Boxes,
  Menu,
  X,
} from 'lucide-react'
import { useAuthStore } from '@/lib/store/authStore'
import { authApi } from '@/features/auth/api/authApi'
import { assignmentApi } from '@/features/assignment/api/assignmentApi'
import { deviceApi } from '@/features/device/api/deviceApi'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ThemeToggle } from '@/components/theme/ThemeToggle'
import { LanguageSelector } from '@/components/LanguageSelector'
import { cn } from '@/lib/utils'

interface AppLayoutProps {
  children?: ReactNode
}

interface NavItem {
  to: string
  icon: React.ElementType
  labelKey: string
  badge?: number
  end?: boolean
}

interface NavGroup {
  groupKey: string
  items: NavItem[]
}

export function AppLayout({ children }: AppLayoutProps) {
  const { t } = useTranslation()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)

  const clearAuth = useAuthStore((state) => state.clearAuth)
  const userEmail = useAuthStore((state) => state.userEmail)
  const permissions = useAuthStore((state) => state.permissions)
  const role = useAuthStore((state) => state.role)

  const hasPermission = (perm: string) => permissions.includes(perm)
  const hasAnyPermission = (perms: string[]) => perms.some((p) => permissions.includes(p))

  const canApproveAssignments = hasPermission('ASSIGNMENT_APPROVE')
  const canApproveMaintenance = hasPermission('DEVICE_MAINTENANCE_APPROVE')
  const canApproveDisposal = hasPermission('DEVICE_DISPOSE_APPROVE')
  const canApproveAny = canApproveAssignments || canApproveMaintenance || canApproveDisposal

  // Pending counts for badge
  const { data: pendingAssignments = [] } = useQuery({
    queryKey: ['pending-assignments-count'],
    queryFn: () => assignmentApi.findPendingAssignments(),
    enabled: canApproveAssignments,
    refetchInterval: 30000,
  })

  const { data: pendingMaintenance = [] } = useQuery({
    queryKey: ['pending-maintenance-count'],
    queryFn: () => deviceApi.findPendingMaintenance(),
    enabled: canApproveMaintenance,
    refetchInterval: 30000,
  })

  const { data: pendingDisposal = [] } = useQuery({
    queryKey: ['pending-disposal-count'],
    queryFn: () => deviceApi.findPendingDisposal(),
    enabled: canApproveDisposal,
    refetchInterval: 30000,
  })

  const totalPending =
    (canApproveAssignments ? pendingAssignments.length : 0) +
    (canApproveMaintenance ? pendingMaintenance.length : 0) +
    (canApproveDisposal ? pendingDisposal.length : 0)

  const handleLogout = async () => {
    try {
      await authApi.logout()
    } catch {
      // Ignore backend error on logout
    } finally {
      clearAuth()
      window.location.href = '/login'
    }
  }

  // Define navigation groups dynamically based on permissions
  const navGroups: NavGroup[] = []

  // 1. Áttekintés (Mindenkinek)
  navGroups.push({
    groupKey: 'nav.overview',
    items: [
      {
        to: '/my-dashboard',
        icon: LayoutDashboard,
        labelKey: 'nav.dashboard',
      },
    ],
  })

  // 2. Erőforrások
  const resourceItems: NavItem[] = []
  if (hasAnyPermission(['DEVICE_READ', 'DEVICE_CREATE', 'DEVICE_UPDATE', 'DEVICE_DELETE', 'DEVICE_MANAGE'])) {
    resourceItems.push({
      to: '/devices',
      icon: Laptop,
      labelKey: 'nav.devices',
    })
  }
  if (hasAnyPermission(['LOCATION_READ', 'LOCATION_CREATE', 'LOCATION_UPDATE', 'LOCATION_DELETE'])) {
    resourceItems.push({
      to: '/locations',
      icon: MapPin,
      labelKey: 'nav.locations',
    })
  }
  if (hasAnyPermission(['SOFTWARE_LICENSE_VIEW', 'SOFTWARE_CREATE', 'SOFTWARE_UPDATE', 'SOFTWARE_DELETE'])) {
    resourceItems.push({
      to: '/software',
      icon: Shield,
      labelKey: 'nav.software',
    })
  }
  if (resourceItems.length > 0) {
    navGroups.push({
      groupKey: 'nav.resources',
      items: resourceItems,
    })
  }

  // 3. Műveletek / Jóváhagyások
  if (canApproveAny) {
    navGroups.push({
      groupKey: 'nav.operations',
      items: [
        {
          to: '/approvals',
          icon: ClipboardCheck,
          labelKey: 'assignments.approvalQueue',
          badge: totalPending > 0 ? totalPending : undefined,
        },
      ],
    })
  }

  // 4. Rendszerkezelés (Adminisztráció)
  const adminItems: NavItem[] = []
  if (hasAnyPermission(['USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_DELETE'])) {
    adminItems.push({
      to: '/users',
      icon: Users,
      labelKey: 'nav.users',
    })
  }
  if (hasAnyPermission(['ROLE_READ', 'ROLE_MANAGE'])) {
    adminItems.push({
      to: '/roles',
      icon: ShieldCheck,
      labelKey: 'nav.roles',
    })
  }
  if (hasPermission('AUDIT_READ')) {
    adminItems.push({
      to: '/audit',
      icon: FileText,
      labelKey: 'nav.audit',
    })
  }
  if (hasPermission('IMPORT_EXECUTE')) {
    adminItems.push({
      to: '/import',
      icon: Upload,
      labelKey: 'nav.import',
    })
  }
  if (adminItems.length > 0) {
    navGroups.push({
      groupKey: 'nav.administration',
      items: adminItems,
    })
  }

  const roleLabel = role ? role.replace('ROLE_', '') : ''

  return (
    <div className="flex min-h-screen bg-background">
      {/* Mobile Backdrop */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-background/80 backdrop-blur-sm md:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-border bg-card transition-transform duration-200 md:static md:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        {/* Brand Header */}
        <div className="flex h-16 items-center justify-between border-b border-border px-5">
          <Link
            to="/my-dashboard"
            className="flex items-center gap-2.5 font-semibold text-foreground tracking-tight hover:opacity-90 transition-opacity"
            onClick={() => setMobileOpen(false)}
          >
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground shadow-sm">
              <Boxes className="h-5 w-5" />
            </div>
            <span className="text-base leading-tight font-bold">{t('appName', 'Tanszéki Raktár')}</span>
          </Link>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 md:hidden"
            onClick={() => setMobileOpen(false)}
          >
            <X className="h-4 w-4" />
          </Button>
        </div>

        {/* Navigation Items */}
        <div className="flex-1 overflow-y-auto px-3 py-4 space-y-6">
          {navGroups.map((group) => (
            <div key={group.groupKey} className="space-y-1.5">
              <div className="px-3 text-[11px] font-semibold tracking-wider text-muted-foreground/70 uppercase">
                {t(group.groupKey, group.groupKey)}
              </div>
              <div className="space-y-0.5">
                {group.items.map((item) => {
                  const Icon = item.icon
                  const isActive =
                    item.to === '/my-dashboard'
                      ? location.pathname === '/my-dashboard'
                      : location.pathname.startsWith(item.to)

                  return (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      end={'end' in item ? item.end : false}
                      onClick={() => setMobileOpen(false)}
                      className={cn(
                        'flex items-center justify-between rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                        isActive
                          ? 'bg-primary text-primary-foreground shadow-sm'
                          : 'text-muted-foreground hover:bg-accent hover:text-foreground'
                      )}
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <Icon className="h-4 w-4 shrink-0" />
                        <span className="truncate">{t(item.labelKey, item.labelKey)}</span>
                      </div>
                      {item.badge !== undefined && (
                        <Badge
                          variant={isActive ? 'secondary' : 'default'}
                          className={cn(
                            'ml-2 px-1.5 py-0 text-xs font-semibold shrink-0',
                            isActive ? 'bg-primary-foreground/20 text-primary-foreground' : 'bg-primary text-primary-foreground'
                          )}
                        >
                          {item.badge}
                        </Badge>
                      )}
                    </NavLink>
                  )
                })}
              </div>
            </div>
          ))}
        </div>

        {/* Sidebar Footer: User Card, Language, Theme, Profile & Logout */}
        <div className="border-t border-border p-3 space-y-3 bg-muted/20">
          <div className="flex items-center justify-between px-1">
            <LanguageSelector />
            <ThemeToggle />
          </div>

          <div className="rounded-lg border border-border/80 bg-card p-2.5 shadow-sm space-y-2">
            <div className="flex items-center justify-between gap-2">
              <div className="min-w-0">
                <p className="truncate text-xs font-semibold text-foreground">
                  {userEmail || 'User'}
                </p>
                {role && (
                  <p className="text-[10px] font-mono text-muted-foreground">
                    {t(`roles.${role}`, roleLabel)}
                  </p>
                )}
              </div>
              <Link
                to="/my-profile"
                className="rounded-md p-1 text-muted-foreground hover:bg-accent hover:text-foreground"
                title={t('nav.profile', 'Profil')}
                onClick={() => setMobileOpen(false)}
              >
                <User className="h-4 w-4" />
              </Link>
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={handleLogout}
              className="w-full justify-center h-7 text-xs text-muted-foreground hover:text-destructive hover:border-destructive/40"
            >
              <LogOut className="mr-1.5 h-3.5 w-3.5" />
              {t('nav.logout', 'Kijelentkezés')}
            </Button>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex flex-1 flex-col min-w-0">
        {/* Mobile Top Bar */}
        <header className="flex h-14 items-center justify-between border-b border-border bg-card px-4 md:hidden">
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8"
              onClick={() => setMobileOpen(true)}
            >
              <Menu className="h-5 w-5" />
            </Button>
            <span className="font-semibold text-sm">{t('appName', 'Tanszéki Raktár')}</span>
          </div>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <Link
              to="/my-profile"
              className="rounded-md p-1.5 text-muted-foreground hover:bg-accent hover:text-foreground"
            >
              <User className="h-4 w-4" />
            </Link>
          </div>
        </header>

        <main className="flex-1 p-6 md:p-8 max-w-7xl w-full mx-auto">
          {children ?? <Outlet />}
        </main>
      </div>
    </div>
  )
}
