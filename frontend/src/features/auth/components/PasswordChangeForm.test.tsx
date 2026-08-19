import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { PasswordChangeForm } from './PasswordChangeForm'
import { authApi } from '@/features/auth/api/authApi'
import { useAuthStore } from '@/lib/store/authStore'

vi.mock('@/features/auth/api/authApi', () => ({
  authApi: {
    changePassword: vi.fn(),
  },
}))

describe('PasswordChangeForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().clearAuth()
    useAuthStore.getState().setMustChangePassword(true)
  })

  it('renders dialog title and inputs when open', () => {
    render(<PasswordChangeForm open={true} />)

    expect(screen.getByRole('heading', { level: 2 })).toBeInTheDocument()
    expect(document.querySelector('input#currentPassword')).toBeInTheDocument()
    expect(document.querySelector('input#newPassword')).toBeInTheDocument()
    expect(document.querySelector('input#confirmNewPassword')).toBeInTheDocument()
  })

  it('validates password mismatch client-side', async () => {
    const user = userEvent.setup()
    render(<PasswordChangeForm open={true} />)

    const currentInput = document.querySelector('input#currentPassword') as HTMLInputElement
    const newInput = document.querySelector('input#newPassword') as HTMLInputElement
    const confirmInput = document.querySelector('input#confirmNewPassword') as HTMLInputElement
    const submitBtn = screen.getByRole('button', {
      name: /Change Password|Jelszó megváltoztatása|Módosítás/i,
    })

    await user.type(currentInput, 'OldPassword123!')
    await user.type(newInput, 'NewSecurePassword123!')
    await user.type(confirmInput, 'DifferentPassword123!')
    await user.click(submitBtn)

    await waitFor(() => {
      expect(authApi.changePassword).not.toHaveBeenCalled()
    })
  })

  it('submits successfully when passwords are valid and match', async () => {
    const user = userEvent.setup()
    const onSuccess = vi.fn()
    vi.mocked(authApi.changePassword).mockResolvedValueOnce(undefined)

    render(<PasswordChangeForm open={true} onSuccess={onSuccess} />)

    const currentInput = document.querySelector('input#currentPassword') as HTMLInputElement
    const newInput = document.querySelector('input#newPassword') as HTMLInputElement
    const confirmInput = document.querySelector('input#confirmNewPassword') as HTMLInputElement
    const submitBtn = screen.getByRole('button', {
      name: /Change Password|Jelszó megváltoztatása|Módosítás/i,
    })

    await user.type(currentInput, 'OldPassword123!')
    await user.type(newInput, 'NewSecurePassword123!')
    await user.type(confirmInput, 'NewSecurePassword123!')
    await user.click(submitBtn)

    await waitFor(() => {
      expect(authApi.changePassword).toHaveBeenCalledWith({
        currentPassword: 'OldPassword123!',
        newPassword: 'NewSecurePassword123!',
      })
      expect(onSuccess).toHaveBeenCalled()
    })
  })
})
