import { describe, it, expect } from 'vitest'
import { loginSchema } from './loginSchema'
import { passwordChangeSchema } from './passwordChangeSchema'

describe('loginSchema', () => {
  it('validates correct email and password', () => {
    const result = loginSchema.safeParse({
      email: 'admin@tanszek.local',
      password: 'StrongPassword123!',
    })
    expect(result.success).toBe(true)
  })

  it('rejects invalid email formats', () => {
    const result = loginSchema.safeParse({
      email: 'invalid-email',
      password: 'SecretPassword',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error?.issues[0]?.message).toBe('validation.email')
    }
  })

  it('rejects empty password', () => {
    const result = loginSchema.safeParse({
      email: 'admin@tanszek.local',
      password: '',
    })
    expect(result.success).toBe(false)
  })
})

describe('passwordChangeSchema', () => {
  it('accepts compliant password change request', () => {
    const result = passwordChangeSchema.safeParse({
      currentPassword: 'OldPassword123!',
      newPassword: 'NewCompliantPassword2026!',
      confirmNewPassword: 'NewCompliantPassword2026!',
    })
    expect(result.success).toBe(true)
  })

  it('rejects passwords shorter than 12 characters', () => {
    const result = passwordChangeSchema.safeParse({
      currentPassword: 'OldPassword123!',
      newPassword: 'Short1!',
      confirmNewPassword: 'Short1!',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      const issue = result.error.issues.find((i) => i.message === 'passwordChangeTooShort')
      expect(issue).toBeDefined()
    }
  })

  it('rejects when confirm password does not match', () => {
    const result = passwordChangeSchema.safeParse({
      currentPassword: 'OldPassword123!',
      newPassword: 'NewCompliantPassword2026!',
      confirmNewPassword: 'DifferentPassword2026!',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      const issue = result.error.issues.find((i) => i.message === 'passwordChangeMismatch')
      expect(issue).toBeDefined()
    }
  })

  it('rejects when new password matches old password', () => {
    const result = passwordChangeSchema.safeParse({
      currentPassword: 'SamePassword123!',
      newPassword: 'SamePassword123!',
      confirmNewPassword: 'SamePassword123!',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      const issue = result.error.issues.find((i) => i.message === 'passwordChangeSameAsOld')
      expect(issue).toBeDefined()
    }
  })
})
