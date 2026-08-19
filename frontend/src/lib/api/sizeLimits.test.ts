import { describe, it, expect } from 'vitest'
import { SizeLimits } from './sizeLimits'

describe('SizeLimits', () => {
  it('defines positive integer bounds for all fields', () => {
    Object.entries(SizeLimits).forEach(([_key, value]) => {
      expect(typeof value).toBe('number')
      expect(value).toBeGreaterThan(0)
      expect(Number.isInteger(value)).toBe(true)
    })
  })

  it('maintains expected hierarchy of text length limits', () => {
    expect(SizeLimits.SHORT_TEXT_MAX).toBeLessThan(SizeLimits.MEDIUM_TEXT_MAX)
    expect(SizeLimits.MEDIUM_TEXT_MAX).toBeLessThan(SizeLimits.LONG_TEXT_MAX)
    expect(SizeLimits.LONG_TEXT_MAX).toBeLessThan(SizeLimits.VERY_LONG_TEXT_MAX)
  })

  it('matches backend entity size contracts', () => {
    expect(SizeLimits.INVENTORY_NUMBER_MAX).toBe(50)
    expect(SizeLimits.EMAIL_MAX).toBe(255)
    expect(SizeLimits.URL_MAX).toBe(2048)
  })
})
