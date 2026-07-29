import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '@/features/auth/pages/LoginPage'

/**
 * Központi route konfig.
 *
 * Route objektumok:
 * - protected: true = bejelentkezés szükséges (redirect /login-re ha nincs token)
 * - roles: tömb a szükséges ROLE_ prefix-szel ellátott role-okból
 * - permissions: tömb a szükséges permission-ökből
 *
 * TODO Task 4.4: route loader guard-ok implementálása a SecurityContext alapján.
 * Most placeholder, minden route közvetlenül a komponenst rendereli.
 */
export function AppRoutes() {
  return (
    <Routes>
      {/* Nyilvános route */}
      <Route path="/login" element={<LoginPage />} />

      {/* Védett route-ok — Task 4.4 */}
      <Route
        path="/my-dashboard"
        element={
          <div className="p-8">
            <h1 className="text-2xl font-bold">Saját Dashboard</h1>
            <p className="text-muted-foreground">TODO Task 4.4: dashboard implementálás</p>
          </div>
        }
      />

      {/* Admin route-ok */}
      <Route
        path="/admin"
        element={
          <div className="p-8">
            <h1 className="text-2xl font-bold">Admin</h1>
            <p className="text-muted-foreground">TODO Task 4.4: admin layout + role check</p>
          </div>
        }
      />

      {/* Fallback */}
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}