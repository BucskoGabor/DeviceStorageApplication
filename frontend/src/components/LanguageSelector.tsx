import { useTranslation } from 'react-i18next'
import { Languages } from 'lucide-react'
import { Button } from '@/components/ui/button'

/**
 * LanguageSelector — nyelv választó a header-ben.
 *
 * <p>A választott nyelv a localStorage-ba mentődik ('device-storage-locale'),
 * így a következő session-ökben is megmarad. Az i18next a localStorage-ból
 * olvassa a nyelvet az App komponens mountolásakor.
 */
export function LanguageSelector() {
  const { i18n, t } = useTranslation()

  const languages = [
    { code: 'hu', labelKey: 'login.languageHungarian', flag: '🇭🇺' },
    { code: 'en', labelKey: 'login.languageEnglish', flag: '🇬🇧' },
  ] as const

  const currentLanguage = languages.find((l) => l.code === i18n.language) ?? languages[0]

  const handleLanguageChange = (langCode: string) => {
    i18n.changeLanguage(langCode)
    localStorage.setItem('device-storage-locale', langCode)
  }

  return (
    <div className="flex items-center gap-1">
      <Languages className="h-4 w-4 text-muted-foreground" />
      <div className="flex gap-1">
        {languages.map((lang) => (
          <Button
            key={lang.code}
            variant={lang.code === currentLanguage.code ? 'default' : 'ghost'}
            size="sm"
            onClick={() => handleLanguageChange(lang.code)}
            aria-label={t(lang.labelKey)}
            title={t(lang.labelKey)}
          >
            <span aria-hidden="true">{lang.flag}</span>
          </Button>
        ))}
      </div>
    </div>
  )
}
