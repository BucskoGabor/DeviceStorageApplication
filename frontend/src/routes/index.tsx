import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { MyDashboardPage } from '@/features/auth/pages/MyDashboardPage'
import { ForbiddenPage } from '@/features/auth/pages/ForbiddenPage'
import { AdminLayout } from '@/features/admin/layouts/AdminLayout'
import { PasswordChangeForm } from '@/features/auth/components/PasswordChangeForm'
import { useAuthStore } from '@/lib/store/authStore'
import { RequireAuth } from '@/components/auth/RequireAuth'
import { RequirePermission } from '@/components/auth/RequirePermission'
import { DevicesPage } from '@/features/device/pages/DevicesPage'
import { DeviceDetailPage } from '@/features/attachment/pages/DeviceDetailPage'
import { AuditPage } from '@/features/audit/pages/AuditPage'
import { ImportPage } from '@/features/import/pages/ImportPage'
import { UsersPage } from '@/features/user/pages/UsersPage'
import { RolesPage } from '@/features/role/pages/RolesPage'
import { LocationsPage } from '@/features/location/pages/LocationsPage'
import { SoftwarePage } from '@/features/software/pages/SoftwarePage'
import { PendingApprovalsPage } from '@/features/assignment/pages/PendingApprovalsPage'
import { MyProfilePage } from '@/features/auth/pages/MyProfilePage'
import { UserDetailPage } from '@/features/user/pages/UserDetailPage'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { userApi } from '@/features/user/api/userApi'
import { deviceApi } from '@/features/device/api/deviceApi'
import { locationApi } from '@/features/location/api/locationApi'

import { useSilentRefresh } from '@/hooks/useSilentRefresh'

/**
 * Központi route konfig.
 *
 * Route hierarchy:
 * - /login (nyilvános, ha nincs token)
 * - /403 (nyilvános, role check fail)
 * - /my-dashboard (RequireAuth, bármely role)
 * - /admin + sub-routes (RequireRole: ROLE_ADMIN)
 *
 * Ha mustChangePassword=true: a DashboardPage kártya fölött PasswordChangeForm modal.
 * A modal sikeres close után passwordChanged=true → dashboard tartalom megjelenik.
 */
export function AppRoutes() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const initialRefreshDone = useAuthStore((state) => state.initialRefreshDone)
  const mustChangePassword = useAuthStore((state) => state.mustChangePassword)
  const [passwordChanged, setPasswordChanged] = useState(false)

  // Automatrikus silent refresh indítása oldalbetöltéskor / F5 frissítéskor
  useSilentRefresh()

  // Még fut a kezdeti silent refresh → Betöltés képernyő
  if (!initialRefreshDone) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <p className="text-muted-foreground">Betöltés...</p>
      </div>
    )
  }

  // Ha nincs access token, csak /login elérhető
  if (!accessToken) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  // Ha be van jelentkezve, de a jelszót meg kell változtatni (és még nem tette meg)
  // A teljes route rendszert csak a modal köré wrap-elve rendereljük,
  // hogy a user ne érhessen el semmit a csere előtt.
  if (mustChangePassword && !passwordChanged) {
    return (
      <Routes>
        <Route
          path="*"
          element={
            <MyDashboardPage>
              <PasswordChangeForm
                open={true}
                onSuccess={() => setPasswordChanged(true)}
              />
            </MyDashboardPage>
          }
        />
      </Routes>
    )
  }

  // Normál authenticated routing
  return (
    <Routes>
      {/* Login → my-dashboard (ha már be van jelentkezve) */}
      <Route path="/login" element={<Navigate to="/my-dashboard" replace />} />

      {/* 403-as oldal */}
      <Route path="/403" element={<ForbiddenPage />} />

      {/* My Dashboard (minden bejelentkezett user számára) */}
      <Route
        path="/my-dashboard"
        element={
          <RequireAuth>
            <MyDashboardPage />
          </RequireAuth>
        }
      />

      {/* Saját profil (minden bejelentkezett user számára) */}
      <Route
        path="/my-profile"
        element={
          <RequireAuth>
            <MyProfilePage />
          </RequireAuth>
        }
      />

      {/* Admin / Management route-ok (ROLE_ADMIN és ROLE_TEACHER) */}
      <Route
        path="/admin"
        element={
          <RequireRole roles={['ROLE_ADMIN', 'ROLE_TEACHER']}>
            <AdminLayout />
          </RequireRole>
        }
      >
        <Route index element={<AdminIndexPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="users/:id" element={<UserDetailPage />} />
        <Route path="roles" element={<RolesPage />} />
        <Route path="devices" element={<DevicesPage />} />
        <Route path="devices/:id" element={<DeviceDetailPage />} />
        <Route path="locations" element={<LocationsPage />} />
        <Route path="software" element={<SoftwarePage />} />
        <Route path="audit" element={<AuditPage />} />
        <Route path="import" element={<ImportPage />} />
        <Route path="approvals" element={<PendingApprovalsPage />} />
      </Route>

      {/* Fallback */}
      <Route path="/" element={<Navigate to="/my-dashboard" replace />} />
      <Route path="*" element={<Navigate to="/my-dashboard" replace />} />
    </Routes>
  )
}

function AdminIndexPage() {
  const { data: usersData } = useQuery({
    queryKey: ['users-summary'],
    queryFn: () => userApi.findAll({ page: 0, size: 1 }),
  })

  const { data: devicesData } = useQuery({
    queryKey: ['devices-summary'],
    queryFn: () => deviceApi.findAll({ page: 0, size: 1 }),
  })

  const { data: locationsData } = useQuery({
    queryKey: ['locations-summary'],
    queryFn: () => locationApi.findAll({ page: 0, size: 1 }),
  })

  const stats = [
    {
      label: 'Felhasználók',
      value: usersData?.totalElements ?? 0,
      href: '/admin/users',
    },
    {
      label: 'Eszközök',
      value: devicesData?.totalElements ?? 0,
      href: '/admin/devices',
    },
    {
      label: 'Helyszínek',
      value: locationsData?.totalElements ?? 0,
      href: '/admin/locations',
    },
  ]

  return (
    <div className="space-y-4">
      <h2 className="text-2xl font-semibold">Admin Dashboard</h2>
      <p className="text-sm text-muted-foreground">
        Válassz egy aloldalt a bal oldali menüből (Felhasználók, Eszközök, Helyszínek, Szoftverek, Audit napló, Importálás).
      </p>
      <div className="grid gap-4 md:grid-cols-3">
        {stats.map((stat) => (
          <Card key={stat.href}>
            <CardHeader>
              <CardDescription>{stat.label}</CardDescription>
              <CardTitle className="text-3xl">{stat.value}</CardTitle>
            </CardHeader>
            <CardContent>
              <a href={stat.href} className="text-sm text-primary hover:underline">
                Megtekintés →
              </a>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
