import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { RequirePermission } from './RequirePermission'
import { useAuthStore } from '@/lib/store/authStore'

describe('RequirePermission', () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
  })

  it('redirects to /login when unauthenticated', () => {
    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route
            path="/protected"
            element={
              <RequirePermission permissions={['DEVICE_READ']}>
                <div>Protected Content</div>
              </RequirePermission>
            }
          />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText('Login Page')).toBeInTheDocument()
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
  })

  it('redirects to /403 when user lacks required permission', () => {
    useAuthStore
      .getState()
      .setAuth('dummy-token', 'user@tanszek.local', 'ROLE_STUDENT', ['DEVICE_READ'], false)

    render(
      <MemoryRouter initialEntries={['/admin-only']}>
        <Routes>
          <Route path="/403" element={<div>Forbidden 403</div>} />
          <Route
            path="/admin-only"
            element={
              <RequirePermission permissions={['USER_MANAGE']}>
                <div>Admin Content</div>
              </RequirePermission>
            }
          />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText('Forbidden 403')).toBeInTheDocument()
    expect(screen.queryByText('Admin Content')).not.toBeInTheDocument()
  })

  it('renders children when user has all required permissions', () => {
    useAuthStore
      .getState()
      .setAuth(
        'dummy-token',
        'admin@tanszek.local',
        'ROLE_ADMIN',
        ['DEVICE_READ', 'DEVICE_CREATE'],
        false
      )

    render(
      <MemoryRouter initialEntries={['/devices']}>
        <Routes>
          <Route
            path="/devices"
            element={
              <RequirePermission permissions={['DEVICE_READ', 'DEVICE_CREATE']}>
                <div>Devices Management</div>
              </RequirePermission>
            }
          />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText('Devices Management')).toBeInTheDocument()
  })

  it('renders children when user matches anyPermission', () => {
    useAuthStore
      .getState()
      .setAuth('dummy-token', 'teacher@tanszek.local', 'ROLE_TEACHER', ['DEVICE_ASSIGN'], false)

    render(
      <MemoryRouter initialEntries={['/approvals']}>
        <Routes>
          <Route path="/403" element={<div>Forbidden 403</div>} />
          <Route
            path="/approvals"
            element={
              <RequirePermission
                anyPermission={['DEVICE_ASSIGN', 'DEVICE_MAINTENANCE_APPROVE']}
              >
                <div>Approval Queue</div>
              </RequirePermission>
            }
          />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText('Approval Queue')).toBeInTheDocument()
  })
})
