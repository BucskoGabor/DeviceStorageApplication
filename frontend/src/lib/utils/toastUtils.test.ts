// @vitest-environment node
// Mock import: a @/lib/i18n/config az i18next instance, amit mockolni kell
// A teszt célja: a resolveToastMessage fallback mechanizmus unit tesztelése

import { describe, it, expect, vi, beforeEach } from 'vitest'

// i18n mock
const mockI18n = {
  t: vi.fn((_key: string) => `[${_key}]`),
  exists: vi.fn((_key: string) => true),
}

vi.mock('@/lib/i18n/i18n', () => ({
  default: mockI18n,
}))

describe('resolveToastMessage fallback mechanism', () => {
  beforeEach(() => {
    mockI18n.t.mockClear()
    mockI18n.exists.mockClear()
  })

  it('returns translated messageKey when i18n resource has it', async () => {
    mockI18n.exists.mockReturnValue(true)
    mockI18n.t.mockReturnValue('Fordított hibaüzenet')

    const { resolveToastMessage } = await import('@/lib/utils/toastUtils')
    const result = resolveToastMessage({
      data: { messageKey: 'permissionDenied', message: 'Native message' },
    })

    expect(mockI18n.exists).toHaveBeenCalledWith('permissionDenied')
    expect(mockI18n.t).toHaveBeenCalledWith('permissionDenied')
    expect(result).toBe('Fordított hibaüzenet')
  })

  it('falls back to native message when messageKey is missing from resource', async () => {
    mockI18n.exists.mockReturnValue(false)
    mockI18n.t.mockReturnValue('Should not be called for missing key')

    const { resolveToastMessage } = await import('@/lib/utils/toastUtils')
    const result = resolveToastMessage({
      data: { messageKey: 'unknownKey', message: 'Backend native message' },
    })

    expect(mockI18n.exists).toHaveBeenCalledWith('unknownKey')
    expect(result).toBe('Backend native message')
    // i18n.t ne legyen hívva a hiányzó kulccsal — fallback a message-re
    expect(mockI18n.t).not.toHaveBeenCalledWith('unknownKey')
  })

  it('returns native message when no messageKey is provided', async () => {
    mockI18n.exists.mockReturnValue(false)

    const { resolveToastMessage } = await import('@/lib/utils/toastUtils')
    const result = resolveToastMessage({
      data: { message: 'Just a native message' },
    })

    expect(result).toBe('Just a native message')
  })

  it('returns internalError fallback when neither key nor message present', async () => {
    mockI18n.t.mockReturnValue('Váratlan hiba történt')

    const { resolveToastMessage } = await import('@/lib/utils/toastUtils')
    const result = resolveToastMessage({ data: {} })

    expect(mockI18n.t).toHaveBeenCalledWith('internalError')
    expect(result).toBe('Váratlan hiba történt')
  })

  it('handles null/undefined response gracefully', async () => {
    mockI18n.t.mockReturnValue('Fallback error')

    const { resolveToastMessage } = await import('@/lib/utils/toastUtils')

    expect(resolveToastMessage(null)).toBe('Fallback error')
    expect(resolveToastMessage(undefined)).toBe('Fallback error')
    expect(resolveToastMessage({})).toBe('Fallback error')
  })

  it('handles response with data but neither messageKey nor message', async () => {
    mockI18n.t.mockReturnValue('Fallback')

    const { resolveToastMessage } = await import('@/lib/utils/toastUtils')
    const result = resolveToastMessage({ data: {} })

    expect(result).toBe('Fallback')
    expect(mockI18n.t).toHaveBeenCalledWith('internalError')
  })
})
