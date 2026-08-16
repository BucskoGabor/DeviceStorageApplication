import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { LoginForm } from './LoginForm'
import { authApi } from '@/features/auth/api/authApi'
import { useAuthStore } from '@/lib/store/authStore'

vi.mock('@/features/auth/api/authApi', () => ({
  authApi: {
    login: vi.fn(),
  },
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

describe('LoginForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().clearAuth()
  })

  it('renders form inputs and submit button', () => {
    render(
      <MemoryRouter>
        <LoginForm />
      </MemoryRouter>
    )

    expect(screen.getByLabelText(/login\.email/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/login\.password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /login\.submit/i })).toBeInTheDocument()
  })

  it('submits credentials and navigates upon success', async () => {
    const user = userEvent.setup()
    vi.mocked(authApi.login).mockResolvedValueOnce({
      accessToken: 'sample-jwt',
      role: 'ROLE_ADMIN',
      permissions: ['ALL'],
      mustChangePassword: false,
      expiresIn: 900,
    })

    render(
      <MemoryRouter>
        <LoginForm />
      </MemoryRouter>
    )

    await user.type(screen.getByLabelText(/login\.email/i), 'admin@tanszek.local')
    await user.type(screen.getByLabelText(/login\.password/i), 'ValidPass123!')
    await user.click(screen.getByRole('button', { name: /login\.submit/i }))

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith({
        email: 'admin@tanszek.local',
        password: 'ValidPass123!',
      })
      expect(useAuthStore.getState().accessToken).toBe('sample-jwt')
      expect(mockNavigate).toHaveBeenCalledWith('/my-dashboard')
    })
  })
})
