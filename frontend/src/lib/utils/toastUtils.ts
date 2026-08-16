import { i18n } from '@/lib/i18n/i18n'

/**
 * Toast üzenet feloldása a backend response-ból.
 *
 * <p>A backend hibaválaszok tartalmaznak egy {@code messageKey} mezőt (i18n kulcs),
 * és opcionálisan egy natív {@code message} mezőt (angol nyelvű fallback).
 *
 * <p>Feloldási sorrend:
 * <ol>
 *   <li>Ha van {@code messageKey} ÉS az i18n resource tartalmazza — lefordítjuk a user locale-jára</li>
 *   <li>Ha van {@code messageKey}, DE az i18n resource NEM tartalmazza —
 *       fallback a backend natív {@code message} mezőjére</li>
 *   <li>Ha nincs {@code messageKey} — natív {@code message} mezőt használjuk</li>
 *   <li>Ha egyik sincs — az "internalError" i18n kulcsot használjuk</li>
 * </ol>
 *
 * @param response a backend axios hiba response objektum (vagy undefined)
 * @returns a felhasználónak megjelenítendő üzenet string
 */
export function resolveToastMessage(
  response: { data?: { messageKey?: string; message?: string } } | undefined | null
): string {
  if (!response?.data) {
    return i18n.exists('messages.internalError')
      ? i18n.t('messages.internalError')
      : i18n.t('internalError')
  }

  const { messageKey, message } = response.data

  if (messageKey) {
    if (i18n.exists(messageKey)) {
      return i18n.t(messageKey)
    }
    if (i18n.exists(`messages.${messageKey}`)) {
      return i18n.t(`messages.${messageKey}`)
    }
  }

  if (message) {
    return message
  }

  if (messageKey) {
    return i18n.exists('messages.internalError')
      ? i18n.t('messages.internalError')
      : i18n.t('internalError')
  }

  return i18n.exists('messages.internalError')
    ? i18n.t('messages.internalError')
    : i18n.t('internalError')
}

/**
 * Sonner toast típus — error / warning / success.
 */
export type ToastKind = 'error' | 'warning' | 'success' | 'info'

/**
 * Backend hiba toast megjelenítése a SonnerWrapper-en keresztül.
 *
 * <p>Lazy import — a Sonner csomag a hívó oldalon legyen bundle-ölve,
 * ne az utils fájlból (kisebb initial bundle).
 */
export async function showErrorToast(
  response: { data?: { messageKey?: string; message?: string } } | undefined | null,
  fallbackMessage?: string
): Promise<void> {
  const { toast } = await import('sonner')
  const resolved = resolveToastMessage(response)
  toast.error(fallbackMessage ?? resolved)
}
