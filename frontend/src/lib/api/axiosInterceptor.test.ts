import { describe, it, expect, beforeEach } from 'vitest'
import axios from 'axios'
import { apiClient } from './axios'
import { useAuthStore } from '@/lib/store/authStore'

describe('apiClient request interceptor', () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
  })

  it('attaches Authorization header when accessToken exists in store', async () => {
    useAuthStore
      .getState()
      .setAuth('valid-bearer-token', 'user@tanszek.local', 'ROLE_TEACHER', ['READ'], false)

    const headers = new axios.AxiosHeaders()
    const handler = (apiClient.interceptors.request as any).handlers[0]
    const config = await handler.fulfilled({
      headers,
      method: 'get',
    })

    expect(config.headers.get('Authorization')).toBe('Bearer valid-bearer-token')
  })

  it('does not attach Authorization header when accessToken is null', async () => {
    const headers = new axios.AxiosHeaders()
    const handler = (apiClient.interceptors.request as any).handlers[0]
    const config = await handler.fulfilled({
      headers,
      method: 'get',
    })

    expect(config.headers.get('Authorization')).toBeUndefined()
  })
})
