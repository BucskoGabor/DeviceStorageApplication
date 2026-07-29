import { Toaster as SonnerToaster } from 'sonner'
import { useTheme } from 'next-themes'

/**
 * Sonner toast wrapper — i18n-aware, theme-aware.
 *
 * A toast messageKey-ket az i18next fordítja le a user locale-ján.
 * Ha a messageKey hiányzik a frontend resource fájlból, a backend response
 * message mezője jelenik meg fallback-ként.
 */
export function Toaster() {
  const { theme } = useTheme()
  return (
    <SonnerToaster
      theme={theme === 'dark' ? 'dark' : 'light'}
      position="top-right"
      richColors
      closeButton
      toastOptions={{
        duration: 5000,
      }}
    />
  )
}