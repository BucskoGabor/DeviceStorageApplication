import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from '@/lib/theme/theme-provider'
import { I18nextProvider } from 'react-i18next'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { AppRoutes } from '@/routes'
import { i18n } from '@/lib/i18n/i18n'
import { Toaster } from '@/components/SonnerWrapper'

/**
 * QueryClient konfiguráció:
 * - staleTime: 5s — 5 másodperces deduplication ablak
 * - retry: 1 — egyszer retry hiba esetén
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 1000,
      retry: 1,
    },
  },
})
/**
 * Az alkalmazás gyökér komponense.
 *
 * Provider-ek sorrendje (kívülről befelé):
 *   1. ErrorBoundary — runtime render hibák elkapása
 *   2. I18nextProvider — i18next példány (locale, translations)
 *   3. ThemeProvider — dark/light mód (next-themes)
 *   4. QueryClientProvider — TanStack Query (szerver cache)
 *   5. BrowserRouter — React Router (route kezelés)
 */
export default function App() {
  return (
    <ErrorBoundary>
      <I18nextProvider i18n={i18n}>
        <ThemeProvider defaultTheme="system" storageKey="device-storage-theme">
          <QueryClientProvider client={queryClient}>
            <BrowserRouter>
              <AppRoutes />
              <Toaster />
            </BrowserRouter>
          </QueryClientProvider>
        </ThemeProvider>
      </I18nextProvider>
    </ErrorBoundary>
  )
}
