import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { StatusBadge } from './StatusBadge'

describe('StatusBadge', () => {
  it('renders IN_STORAGE badge correctly', () => {
    render(<StatusBadge status="IN_STORAGE" />)
    expect(screen.getByText(/Raktárban|In Storage/i)).toBeInTheDocument()
  })

  it('renders ASSIGNED badge correctly', () => {
    render(<StatusBadge status="ASSIGNED" />)
    expect(screen.getByText(/Hozzárendelve|Assigned/i)).toBeInTheDocument()
  })

  it('renders PENDING_ASSIGNMENT badge correctly', () => {
    render(<StatusBadge status="PENDING_ASSIGNMENT" />)
    expect(screen.getByText(/Hozzárendelés függőben|Pending Assignment/i)).toBeInTheDocument()
  })

  it('renders MAINTENANCE badge correctly', () => {
    render(<StatusBadge status="MAINTENANCE" />)
    expect(screen.getByText(/Karbantartás alatt|Maintenance/i)).toBeInTheDocument()
  })

  it('renders DISPOSED badge correctly', () => {
    render(<StatusBadge status="DISPOSED" />)
    expect(screen.getByText(/Selejtezve|Disposed/i)).toBeInTheDocument()
  })

  it('renders fallback label for unknown status', () => {
    render(<StatusBadge status="CUSTOM_UNKNOWN_STATUS" />)
    expect(screen.getByText('CUSTOM_UNKNOWN_STATUS')).toBeInTheDocument()
  })
})
