import { lazy, Suspense, useState } from 'react'
import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { MyDashboardPage } from '@/features/auth/pages/MyDashboardPage'
import { ForbiddenPage } from '@/features/auth/pages/ForbiddenPage'
import { NotFoundPage } from '@/features/auth/pages/NotFoundPage'
import { PasswordChangeForm } from '@/features/auth/components/PasswordChangeForm'
import { useAuthStore } from '@/lib/store/authStore'
import { RequireAuth } from '@/components/auth/RequireAuth'
import { RequirePermission } from '@/components/auth/RequirePermission'
import { AppLayout } from '@/components/layout/AppLayout'
import { useSilentRefresh } from '@/hooks/useSilentRefresh'

// Lazy-loaded feature pages
const DevicesPage = lazy(() =>
  import('@/features/device/pages/DevicesPage').then((m) => ({ default: m.DevicesPage }))
)
const DeviceDetailPage = lazy(() =>
  import('@/features/attachment/pages/DeviceDetailPage').then((m) => ({
    default: m.DeviceDetailPage,
  }))
)
const AuditPage = lazy(() =>
  import('@/features/audit/pages/AuditPage').then((m) => ({ default: m.AuditPage }))
)
const ImportPage = lazy(() =>
  import('@/features/import/pages/ImportPage').then((m) => ({ default: m.ImportPage }))
)
const UsersPage = lazy(() =>
  import('@/features/user/pages/UsersPage').then((m) => ({ default: m.UsersPage }))
)
const UserDetailPage = lazy(() =>
  import('@/features/user/pages/UserDetailPage').then((m) => ({ default: m.UserDetailPage }))
)
const RolesPage = lazy(() =>
  import('@/features/role/pages/RolesPage').then((m) => ({ default: m.RolesPage }))
)
const LocationsPage = lazy(() =>
  import('@/features/location/pages/LocationsPage').then((m) => ({ default: m.LocationsPage }))
)
const LocationDetailPage = lazy(() =>
  import('@/features/location/pages/LocationDetailPage').then((m) => ({
    default: m.LocationDetailPage,
  }))
)
const SoftwarePage = lazy(() =>
  import('@/features/software/pages/SoftwarePage').then((m) => ({ default: m.SoftwarePage }))
)
const PendingApprovalsPage = lazy(() =>
  import('@/features/assignment/pages/PendingApprovalsPage').then((m) => ({
    default: m.PendingApprovalsPage,
  }))
)
const MyProfilePage = lazy(() =>
  import('@/features/auth/pages/MyProfilePage').then((m) => ({ default: m.MyProfilePage }))
)

function LoadingFallback() {
  return (
    <div className="flex h-64 items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
    </div>
  )
}
/**
 * Redirect helpers for backward compatibility with legacy /admin/* routes.
 */
function RedirectToDevice() {
  const { id } = useParams<{ id: string }>()
  return <Navigate to={`/devices/${id}`} replace />
}

function RedirectToLocation() {
  const { id } = useParams<{ id: string }>()
  return <Navigate to={`/locations/${id}`} replace />
}

function RedirectToUser() {
  const { id } = useParams<{ id: string }>()
  return <Navigate to={`/users/${id}`} replace />
}

/**
 * Központi route konfig — 100% Permission-alapú jogosultságkezelés és Egységes AppLayout.
 */
export function AppRoutes() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const initialRefreshDone = useAuthStore((state) => state.initialRefreshDone)
  const mustChangePassword = useAuthStore((state) => state.mustChangePassword)
  const [passwordChanged, setPasswordChanged] = useState(false)

  // Automatikus silent refresh indítása oldalbetöltéskor / F5 frissítéskor
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
  if (mustChangePassword && !passwordChanged) {
    return (
      <Suspense fallback={<LoadingFallback />}>
        <Routes>
          <Route
            path="*"
            element={
              <AppLayout>
                <MyDashboardPage>
                  <PasswordChangeForm open={true} onSuccess={() => setPasswordChanged(true)} />
                </MyDashboardPage>
              </AppLayout>
            }
          />
        </Routes>
      </Suspense>
    )
  }

  // Normál authenticated routing — Egységes AppLayout és 100% permission alapú route guard-ok
  return (
    <Suspense fallback={<LoadingFallback />}>
      <Routes>
        {/* Login → my-dashboard (ha már be van jelentkezve) */}
        <Route path="/login" element={<Navigate to="/my-dashboard" replace />} />

        {/* 403-as oldal */}
        <Route path="/403" element={<ForbiddenPage />} />

        {/* Védett alkalmazás útvonalak az egységes AppLayout alatt */}
        <Route
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        >
          {/* Főoldal / Dashboard */}
          <Route path="/my-dashboard" element={<MyDashboardPage />} />
          <Route path="/dashboard" element={<Navigate to="/my-dashboard" replace />} />

          {/* Saját profil */}
          <Route path="/my-profile" element={<MyProfilePage />} />

          {/* Erőforrások (Resources) */}
          <Route
            path="/devices"
            element={
              <RequirePermission permissions={['DEVICE_READ']}>
                <DevicesPage />
              </RequirePermission>
            }
          />
          <Route
            path="/devices/:id"
            element={
              <RequirePermission permissions={['DEVICE_READ']}>
                <DeviceDetailPage />
              </RequirePermission>
            }
          />
          <Route
            path="/locations"
            element={
              <RequirePermission permissions={['LOCATION_READ']}>
                <LocationsPage />
              </RequirePermission>
            }
          />
          <Route
            path="/locations/:id"
            element={
              <RequirePermission permissions={['LOCATION_READ']}>
                <LocationDetailPage />
              </RequirePermission>
            }
          />
          <Route
            path="/software"
            element={
              <RequirePermission anyPermission={['SOFTWARE_LICENSE_VIEW', 'SOFTWARE_MANAGE']}>
                <SoftwarePage />
              </RequirePermission>
            }
          />

          {/* Műveletek / Jóváhagyások (Operations) */}
          <Route
            path="/approvals"
            element={
              <RequirePermission
                anyPermission={[
                  // ASSIGNMENT_APPROVE azért kell, mert a backend AssignmentController
                  // ezt a permission-t követeli meg a jóváhagyás endpointokon
                  // (approve / approve-unassign / reject / findPending). Ha csak
                  // DEVICE_ASSIGN lenne meg, a user beléphet, de minden hívás 403-at adna.
                  'ASSIGNMENT_APPROVE',
                  'DEVICE_ASSIGN',
                  'DEVICE_UNASSIGN',
                  'DEVICE_MAINTENANCE_APPROVE',
                  'DEVICE_DISPOSE_APPROVE',
                ]}
              >
                <PendingApprovalsPage />
              </RequirePermission>
            }
          />

          {/* Rendszerkezelés / Adminisztráció (Administration) */}
          <Route
            path="/users"
            element={
              <RequirePermission anyPermission={['USER_READ', 'USER_MANAGE']}>
                <UsersPage />
              </RequirePermission>
            }
          />
          <Route
            path="/users/:id"
            element={
              <RequirePermission anyPermission={['USER_READ', 'USER_MANAGE']}>
                <UserDetailPage />
              </RequirePermission>
            }
          />
          <Route
            path="/roles"
            element={
              <RequirePermission anyPermission={['USER_READ', 'USER_MANAGE']}>
                <RolesPage />
              </RequirePermission>
            }
          />
          <Route
            path="/audit"
            element={
              <RequirePermission permissions={['AUDIT_READ']}>
                <AuditPage />
              </RequirePermission>
            }
          />
          <Route
            path="/import"
            element={
              <RequirePermission anyPermission={['USER_MANAGE', 'DEVICE_CREATE']}>
                <ImportPage />
              </RequirePermission>
            }
          />

          {/* Visszafelé kompatibilis /admin/* átirányítások */}
          <Route path="/admin" element={<Navigate to="/my-dashboard" replace />} />
          <Route path="/admin/devices" element={<Navigate to="/devices" replace />} />
          <Route path="/admin/devices/:id" element={<RedirectToDevice />} />
          <Route path="/admin/locations" element={<Navigate to="/locations" replace />} />
          <Route path="/admin/locations/:id" element={<RedirectToLocation />} />
          <Route path="/admin/software" element={<Navigate to="/software" replace />} />
          <Route path="/admin/approvals" element={<Navigate to="/approvals" replace />} />
          <Route path="/admin/users" element={<Navigate to="/users" replace />} />
          <Route path="/admin/users/:id" element={<RedirectToUser />} />
          <Route path="/admin/roles" element={<Navigate to="/roles" replace />} />
          <Route path="/admin/audit" element={<Navigate to="/audit" replace />} />
          <Route path="/admin/import" element={<Navigate to="/import" replace />} />

          {/* 404-es oldal — catch-all a védett útvonalakon belül */}
          <Route path="*" element={<NotFoundPage />} />
        </Route>

        {/* Root fallback — ismeretlen URL-ek */}
        <Route path="/" element={<Navigate to="/my-dashboard" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}
