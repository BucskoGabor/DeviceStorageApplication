import { Component, type ErrorInfo, type ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
  fallback?: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
  error?: Error
}

/**
 * React Error Boundary — runtime render hibák elkapására.
 *
 * A route-ok köré wrap-elve. Ha egy komponens runtime error-t dob (pl.
 * undefined state, render hiba), a boundary elkapja, megjelenít egy
 * fallback UI-t, és logolja az error-t a backend /api/audit/error endpointján
 * (vagy lokálisan konzolra).
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // TODO Task 4.1: POST /api/audit/error endpointra küldés
    // Most konzolra logolunk
    console.error('ErrorBoundary caught error:', error, errorInfo)
  }

  render() {
    if (this.state.hasError) {
      return (
        this.props.fallback ?? (
          <div className="flex min-h-screen items-center justify-center bg-background p-4">
            <div className="max-w-md rounded-lg border border-border bg-card p-6 text-card-foreground shadow-sm">
              <h2 className="mb-2 text-xl font-semibold">Hiba történt</h2>
              <p className="mb-4 text-sm text-muted-foreground">
                  Az alkalmazás váratlan hibát észlelt. Kérjük, frissítse az oldalt vagy lépjen vissza a főoldalra.
              </p>
              {this.state.error && (
                <pre className="mb-4 overflow-auto rounded bg-muted p-3 text-xs text-muted-foreground">
                  {this.state.error.message}
                </pre>
              )}
              <button
                onClick={() => (window.location.href = '/')}
                className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
              >
                Vissza a főoldalra
              </button>
            </div>
          </div>
        )
      )
    }

    return this.props.children
  }
}