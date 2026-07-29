import { ThemeProvider as NextThemesProvider } from 'next-themes'
import type { ReactNode } from 'react'

interface ThemeProviderProps {
  children: ReactNode
  defaultTheme?: 'light' | 'dark' | 'system'
  storageKey?: string
}

/**
 * next-themes wrapper a dark mode kezeléséhez.
 *
 * - defaultTheme="system": az OS preference-t követi
 * - storageKey: localStorage kulcs a user választás tárolásához
 * - A useTheme hook-ot használhatjuk bármely komponensben a currentMode lekéréséhez
 */
export function ThemeProvider({
  children,
  defaultTheme = 'system',
  storageKey = 'device-storage-theme',
}: ThemeProviderProps) {
  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme={defaultTheme}
      enableSystem
      disableTransitionOnChange
      storageKey={storageKey}
    >
      {children}
    </NextThemesProvider>
  )
}