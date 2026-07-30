import { useTranslation } from 'react-i18next'
import { ArrowRight } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * DiffViewer — JSON diff megjelenítő az audit log changes_json-höz.
 *
 * <p>Formátum: {before: {...}, after: {...}}. Mindkét oldalt szépen formázva
 * JSON-ként jeleníti meg, a mezők közötti különbségeket kiemeli.
 */
interface DiffViewerProps {
  changesJson: string | null
}

export function DiffViewer({ changesJson }: DiffViewerProps) {
  const { t } = useTranslation()

  if (!changesJson) {
    return <p className="text-sm text-muted-foreground">—</p>
  }

  let before: Record<string, unknown> = {}
  let after: Record<string, unknown> = {}

  try {
    const parsed = JSON.parse(changesJson)
    before = (parsed.before as Record<string, unknown>) ?? {}
    after = (parsed.after as Record<string, unknown>) ?? {}
  } catch {
    return <pre className="overflow-auto rounded-md bg-destructive/10 p-3 text-xs">{changesJson}</pre>
  }

  const allKeys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)])).sort()

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          {t('audit.changesBefore')} <ArrowRight className="h-3 w-3" /> {t('audit.changesAfter')}
        </CardTitle>
        <CardDescription>{allKeys.length} mező összehasonlítása</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-border">
                <th className="px-2 py-1 text-left font-medium">Field</th>
                <th className="px-2 py-1 text-left font-medium">{t('audit.changesBefore')}</th>
                <th className="px-2 py-1 text-left font-medium">{t('audit.changesAfter')}</th>
              </tr>
            </thead>
            <tbody>
              {allKeys.map((key) => {
                const beforeVal = before[key]
                const afterVal = after[key]
                const changed = JSON.stringify(beforeVal) !== JSON.stringify(afterVal)
                return (
                  <tr key={key} className={changed ? 'border-b border-border bg-yellow-500/10' : 'border-b border-border'}>
                    <td className="px-2 py-1 font-mono">{key}</td>
                    <td className="px-2 py-1 font-mono">
                      {beforeVal === undefined ? '—' : JSON.stringify(beforeVal)}
                    </td>
                    <td className="px-2 py-1 font-mono">
                      {afterVal === undefined ? '—' : JSON.stringify(afterVal)}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  )
}
