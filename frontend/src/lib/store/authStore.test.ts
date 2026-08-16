import { describe, it, expect, beforeEach } from 'vitest'
import { useAuthStore, getAccessToken, setAccessToken } from './authStore'

describe('useAuthStore', () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
  })

  it('initializes with unauthenticated default state', () => {
    const state = useAuthStore.getState()
    expect(state.accessToken).toBeNull()
    expect(state.userEmail).toBeNull()
    expect(state.role).toBeNull()
    expect(state.permissions).toEqual([])
    expect(state.mustChangePassword).toBe(false)
  })

  it('sets authentication details upon setAuth', () => {
    useAuthStore
      .getState()
      .setAuth(
        'mock-jwt-token',
        'teacher@tanszek.local',
        'ROLE_TEACHER',
        ['DEVICE_READ', 'DEVICE_ASSIGN_REQUEST'],
        true
      )

    const state = useAuthStore.getState()
    expect(state.accessToken).toBe('mock-jwt-token')
    expect(state.userEmail).toBe('teacher@tanszek.local')
    expect(state.role).toBe('ROLE_TEACHER')
    expect(state.permissions).toContain('DEVICE_READ')
    expect(state.mustChangePassword).toBe(true)
    expect(state.initialRefreshDone).toBe(true)
    expect(getAccessToken()).toBe('mock-jwt-token')
  })

  it('updates only accessToken upon setAccessToken', () => {
    useAuthStore.getState().setAuth('token1', 'admin@tanszek.local', 'ROLE_ADMIN', ['ALL'], false)

    setAccessToken('token2')

    const state = useAuthStore.getState()
    expect(state.accessToken).toBe('token2')
    expect(state.userEmail).toBe('admin@tanszek.local')
    expect(state.role).toBe('ROLE_ADMIN')
    expect(state.initialRefreshDone).toBe(true)
  })

  it('clears authentication state upon clearAuth', () => {
    useAuthStore.getState().setAuth('token1', 'admin@tanszek.local', 'ROLE_ADMIN', ['ALL'], true)

    useAuthStore.getState().clearAuth()

    const state = useAuthStore.getState()
    expect(state.accessToken).toBeNull()
    expect(state.userEmail).toBeNull()
    expect(state.role).toBeNull()
    expect(state.permissions).toEqual([])
    expect(state.mustChangePassword).toBe(false)
    expect(state.initialRefreshDone).toBe(true)
  })

  it('updates mustChangePassword correctly', () => {
    useAuthStore.getState().setMustChangePassword(true)
    expect(useAuthStore.getState().mustChangePassword).toBe(true)

    useAuthStore.getState().setMustChangePassword(false)
    expect(useAuthStore.getState().mustChangePassword).toBe(false)
  })
})
