import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { RequireRole } from './RequireRole'
import { useAuthStore } from '@/lib/store/authStore'

describe('RequireRole', () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
  })

  it('redirects to /login when unauthenticated', () => {
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route
            path="/admin"
            element={
              <RequireRole roles={['ROLE_ADMIN']}>
                <div>Protected Admin Area</div>
              </RequireRole>
            }
          />
          <Route path="/login" element={<div>Login Page Target</div>} />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.queryByText('Protected Admin Area')).not.toBeInTheDocument()
    expect(screen.getByText('Login Page Target')).toBeInTheDocument()
  })

  it('redirects to /403 when authenticated with incorrect role', () => {
    useAuthStore
      .getState()
      .setAuth('token', 'student@tanszek.local', 'ROLE_STUDENT', ['DEVICE_READ'], false)

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route
            path="/admin"
            element={
              <RequireRole roles={['ROLE_ADMIN']}>
                <div>Protected Admin Area</div>
              </RequireRole>
            }
          />
          <Route path="/403" element={<div>Forbidden 403 Target</div>} />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.queryByText('Protected Admin Area')).not.toBeInTheDocument()
    expect(screen.getByText('Forbidden 403 Target')).toBeInTheDocument()
  })

  it('renders children when user has matching role', () => {
    useAuthStore.getState().setAuth('token', 'admin@tanszek.local', 'ROLE_ADMIN', ['ALL'], false)

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route
            path="/admin"
            element={
              <RequireRole roles={['ROLE_ADMIN']}>
                <div>Protected Admin Area</div>
              </RequireRole>
            }
          />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText('Protected Admin Area')).toBeInTheDocument()
  })
})
