import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { MyDashboardPage } from '@/features/auth/pages/MyDashboardPage'
import { ForbiddenPage } from '@/features/auth/pages/ForbiddenPage'
import { AdminLayout } from '@/features/admin/layouts/AdminLayout'
import { PasswordChangeForm } from '@/features/auth/components/PasswordChangeForm'
import { useAuthStore } from '@/lib/store/authStore'
import { RequireAuth } from '@/components/auth/RequireAuth'
import { RequireRole } from '@/components/auth/RequireRole'
import { DevicesPage } from '@/features/device/pages/DevicesPage'
import { useState } from 'react'

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
  const mustChangePassword = useAuthStore((state) => state.mustChangePassword)
  const [passwordChanged, setPasswordChanged] = useState(false)

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

      {/* Admin route-ok (csak ROLE_ADMIN) */}
      <Route
        path="/admin"
        element={
          <RequireRole roles={['ROLE_ADMIN']}>
            <AdminLayout />
          </RequireRole>
        }
      >
        <Route index element={<AdminIndexPage />} />
        <Route path="devices" element={<DevicesPage />} />
      </Route>

      {/* Fallback */}
      <Route path="/" element={<Navigate to="/my-dashboard" replace />} />
      <Route path="*" element={<Navigate to="/my-dashboard" replace />} />
    </Routes>
  )
}

/**
 * Admin index page placeholder - az admin layouton belül.
 */
function AdminIndexPage() {
  return (
    <div className="rounded-lg border border-border bg-card p-6">
      <h2 className="text-xl font-semibold">Admin Dashboard</h2>
      <p className="mt-2 text-sm text-muted-foreground">
        Válassz egy aloldalt a bal oldali menüből (Users, Devices, Locations, stb.).
      </p>
    </div>
  )
}
