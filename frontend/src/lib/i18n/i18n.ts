import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import hu from './hu.json'
import en from './en.json'
import { i18nKeys, defaultMessages } from './i18n-keys'

/**
 * i18next konfiguráció.
 *
 * Locale prioritás:
 *   1. User choice (localStorage 'device-storage-locale' kulcs)
 *   2. navigator.language (Accept-Language header helyett böngészőben)
 *   3. hu (fallback)
 *
 * A i18nKeys és defaultMessages a backend üzenetállományaiból generálódik.
 */
void i18nKeys
void defaultMessages

i18n.use(initReactI18next).init({
  resources: {
    hu: { translation: hu },
    en: { translation: en },
  },
  lng: (typeof localStorage !== 'undefined' ? localStorage.getItem('device-storage-locale') : null) ?? (typeof navigator !== 'undefined' ? navigator.language.split('-')[0] : null) ?? 'hu',
  fallbackLng: 'hu',
  debug: import.meta.env.DEV,
  interpolation: {
    escapeValue: false, // React már escape-el
  },
  react: {
    useSuspense: false,
  },
})

export { i18n }
