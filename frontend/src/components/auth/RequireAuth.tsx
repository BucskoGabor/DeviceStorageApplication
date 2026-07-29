import { type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuthStore } from '@/lib/store/authStore'

interface RequireAuthProps {
  children: ReactNode
}

/**
 * RequireAuth — wrapper komponens, ami csak authentikált user-ek számára
 * rendereli a children-t. Ha nincs access token, redirect /login.
 */
export function RequireAuth({ children }: RequireAuthProps) {
  const accessToken = useAuthStore((state) => state.accessToken)

  if (!accessToken) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
