import { useState, useEffect, type ComponentProps } from 'react'
import { Input } from '@/components/ui/input'

interface DebouncedInputProps extends Omit<ComponentProps<typeof Input>, 'value' | 'onChange'> {
  value: string
  onDebouncedChange: (value: string) => void
  /** Késleltetés milliszekundumban — default 300ms. */
  delay?: number
}

/**
 * DebouncedInput — egyszerű debounce wrapper az Input komponens köré.
 *
 * <p>A value prop-ból indul, de a user gépelését egy lokális state-ben tartja,
 * és csak {@code delay} ms inaktivitás után hívja meg az {@code onDebouncedChange}-et.
 * Így a gyors gépelés NEM generál felesleges re-render / API hívás láncot.
 *
 * <p>A {@code value} prop változásakor (pl. clear filter esetén) a lokális state
 * szinkronizálódik — így a szülő komponens bármikor alaphelyzetbe állíthatja.
 */
export function DebouncedInput({
  value: externalValue,
  onDebouncedChange,
  delay = 300,
  ...inputProps
}: DebouncedInputProps) {
  const [localValue, setLocalValue] = useState(externalValue)

  // External value → local sync (clear filter, stb.)
  useEffect(() => {
    setLocalValue(externalValue)
  }, [externalValue])

  // Debounce: a localValue változásakor delay ms után hívjuk a callback-et.
  useEffect(() => {
    if (localValue === externalValue) return
    const handle = window.setTimeout(() => {
      onDebouncedChange(localValue)
    }, delay)
    return () => window.clearTimeout(handle)
  }, [localValue, externalValue, delay, onDebouncedChange])

  return (
    <Input {...inputProps} value={localValue} onChange={(e) => setLocalValue(e.target.value)} />
  )
}
