import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { DashboardPage } from '@/features/auth/pages/DashboardPage'
import { useAuthStore } from '@/lib/store/authStore'
import { PasswordChangeForm } from '@/features/auth/components/PasswordChangeForm'
import { useState } from 'react'

/**
 * Központi route konfig.
 *
 * Route objektumok:
 * - protected: true = bejelentkezés szükséges
 * - roles: tömb a szükséges ROLE_ prefix-szel ellátott role-okból
 * - permissions: tömb a szükséges permission-ökből
 *
 * TODO Task 4.4: route loader guard-ok implementálása a SecurityContext alapján.
 * Most placeholder, minden route közvetlenül a komponenst rendereli.
 */
export function AppRoutes() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const mustChangePassword = useAuthStore((state) => state.mustChangePassword)
  const [passwordChanged, setPasswordChanged] = useState(false)

  // Ha nincs access token, redirect login
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
      <Routes>
        <Route
          path="*"
          element={
            <DashboardPage>
              <PasswordChangeForm
                open={true}
                onSuccess={() => setPasswordChanged(true)}
              />
            </DashboardPage>
          }
        />
      </Routes>
    )
  }

  // Normál authenticated routing
  return (
    <Routes>
      <Route path="/login" element={<Navigate to="/my-dashboard" replace />} />
      <Route path="/my-dashboard" element={<DashboardPage />} />
      <Route path="/admin" element={<DashboardPage />} />
      <Route path="/" element={<Navigate to="/my-dashboard" replace />} />
      <Route path="*" element={<Navigate to="/my-dashboard" replace />} />
    </Routes>
  )
}
