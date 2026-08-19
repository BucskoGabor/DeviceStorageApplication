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
 * fallback UI-t, és logolja az error-t a konzolra.
 *
 * <p>A boundary resetelődik, ha a felhasználó a React Router-en belül
 * navigál. Ezt a `popstate` böngésző esemény figyelésével érjük el:
 * mikor a user visszalép a history-ban (vagy SPA-n belül a React Router
 * history-t változtat), a `popstate` tüzel, és a `componentDidMount`-ban
 * regisztrált listener reseteli a hibás state-et. A React Router
 * `push`/`replace` navigációi natív history API-t hívnak, így a
 * `popstate` megbízhatóan jelzi a navigációt.
 *
 * <p>Miért NEM használunk useNavigate/useLocation hook-ot: a boundary-t
 * gyakran a &lt;BrowserRouter&gt; KÍVÜL wrap-eljük (az App.tsx gyökerénél),
 * és a tesztek sem nyújtanak Router kontextust. Hook-ok itt crashelnének.
 * A `popstate` globális listener ezt a problémát elkerüli.
 *
 * <p>A "Vissza a főoldalra" gomb `window.location.assign('/my-dashboard')`-t
 * használ, ami full page reload — elfogadható trade-off, mert
 * helyreállítási akcióról van szó, nem normál navigációról.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  private popstateHandler: (() => void) | null = null

  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error('[ErrorBoundary] Captured UI Exception:', {
      error: error.message,
      stack: error.stack,
      componentStack: errorInfo.componentStack,
      timestamp: new Date().toISOString(),
    })
  }

  componentDidMount(): void {
    // popstate listener regisztrálása, ami a history navigációt figyeli.
    // A React Router saját history implementációja a natív history.pushState
    // és history.replaceState köré épül, és a back/forward (vagy history.go)
    // a 'popstate' eseményt tüzelni fogja — ekkor reseteljük a hibát.
    this.popstateHandler = () => {
      if (this.state.hasError) {
        this.setState({ hasError: false, error: undefined })
      }
    }
    window.addEventListener('popstate', this.popstateHandler)
  }

  componentWillUnmount(): void {
    if (this.popstateHandler) {
      window.removeEventListener('popstate', this.popstateHandler)
      this.popstateHandler = null
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        this.props.fallback ?? (
          <div className="flex min-h-screen items-center justify-center bg-background p-4">
            <div className="max-w-md rounded-lg border border-border bg-card p-6 text-card-foreground shadow-sm">
              <h2 className="mb-2 text-xl font-semibold">Hiba történt</h2>
              <p className="mb-4 text-sm text-muted-foreground">
                Az alkalmazás váratlan hibát észlelt. Kérjük, frissítse az oldalt vagy lépjen vissza
                a főoldalra.
              </p>
              {this.state.error && (
                <pre className="mb-4 overflow-auto rounded bg-muted p-3 text-xs text-muted-foreground">
                  {this.state.error.message}
                </pre>
              )}
              <div className="flex flex-col gap-2">
                <button
                  onClick={() => (window.location.href = '/my-dashboard')}
                  className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
                >
                  Vissza a főoldalra
                </button>
                {typeof window !== 'undefined' && window.history.length > 1 && (
                  <button
                    onClick={() => window.history.back()}
                    className="w-full rounded-md border border-border bg-background px-4 py-2 text-sm font-medium text-foreground hover:bg-muted"
                  >
                    Vissza az előző oldalra
                  </button>
                )}
              </div>
            </div>
          </div>
        )
      )
    }

    return this.props.children
  }
}
