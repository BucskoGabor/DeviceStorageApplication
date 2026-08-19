import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ErrorBoundary } from './ErrorBoundary'

function BuggyComponent(): JSX.Element {
  throw new Error('Test rendering crash')
}

function HealthyComponent(): JSX.Element {
  return <div>Healthy Component Content</div>
}

describe('ErrorBoundary', () => {
  const originalError = console.error

  beforeEach(() => {
    console.error = vi.fn()
  })

  afterEach(() => {
    console.error = originalError
  })

  it('renders children when no error occurs', () => {
    render(
      <ErrorBoundary>
        <HealthyComponent />
      </ErrorBoundary>
    )

    expect(screen.getByText('Healthy Component Content')).toBeInTheDocument()
  })

  it('renders fallback UI and error message when a child component crashes', () => {
    render(
      <ErrorBoundary>
        <BuggyComponent />
      </ErrorBoundary>
    )

    expect(screen.getByText('Hiba történt')).toBeInTheDocument()
    expect(screen.getByText('Test rendering crash')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Vissza a főoldalra' })).toBeInTheDocument()
  })

  it('renders custom fallback when provided', () => {
    render(
      <ErrorBoundary fallback={<div>Custom Error Fallback</div>}>
        <BuggyComponent />
      </ErrorBoundary>
    )

    expect(screen.getByText('Custom Error Fallback')).toBeInTheDocument()
  })
})
