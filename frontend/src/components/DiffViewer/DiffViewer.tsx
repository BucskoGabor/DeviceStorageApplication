import { useTranslation } from 'react-i18next'
import { ArrowRight, Plus, Minus, Pencil } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * DiffViewer — JSON diff megjelenítő az audit log changes_json mezőjéhez.
 *
 * <p>Bemeneti formátum: {@code {before: {...}, after: {...}}}. Mindkét oldalt
 * táblázatos formában jeleníti meg, a mezők közötti különbségeket színkódokkal
 * jelöli:
 * <ul>
 *   <li><b>Zöld</b> — új mező (csak az after-ban van, before-ban undefined)</li>
 *   <li><b>Piros</b> — törölt mező (csak a before-ban van, after-ban undefined)</li>
 *   <li><b>Sárga</b> — módosított mező (mindkettőben megvan, de értékük különbözik)</li>
 *   <li><b>Nincs kiemelés</b> — változatlan mező</li>
 * </ul>
 *
 * <p>Ha a JSON parse fail, a nyers string egy hibaüzenetes blokkban jelenik meg.
 */
interface DiffViewerProps {
  changesJson: string | null
}

type DiffState = 'unchanged' | 'added' | 'removed' | 'modified'

function classifyChange(beforeVal: unknown, afterVal: unknown): DiffState {
  // A JSON deszerializáció során a hiányzó mező `undefined`, míg a kifejezetten
  // null-ra állított mező `null` lesz. Ha ezeket megkülönböztetnénk, akkor pl.
  // egy `field: null → field: undefined` változás "modified"-ként jelenne meg,
  // pedig a felhasználó számára ez nem valódi változás (mindkettő "nincs érték").
  // A normalizálás: mindkettőt `null`-ként kezeljük az összehasonlításnál.
  const normalize = (v: unknown) => (v === undefined ? null : v)
  const beforeNorm = normalize(beforeVal)
  const afterNorm = normalize(afterVal)

  const beforePresent = beforeVal !== undefined && beforeVal !== null
  const afterPresent = afterVal !== undefined && afterVal !== null

  if (!beforePresent && afterPresent) return 'added'
  if (beforePresent && !afterPresent) return 'removed'
  if (JSON.stringify(beforeNorm) !== JSON.stringify(afterNorm)) return 'modified'
  return 'unchanged'
}

const DIFF_ROW_STYLES: Record<DiffState, string> = {
  unchanged: 'border-b border-border',
  added: 'border-b border-border bg-green-500/10 dark:bg-green-900/20',
  removed: 'border-b border-border bg-red-500/10 dark:bg-red-900/20',
  modified: 'border-b border-border bg-yellow-500/10 dark:bg-yellow-900/20',
}

const DIFF_BADGE_STYLES: Record<DiffState, string> = {
  unchanged: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
  added: 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300',
  removed: 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300',
  modified: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/40 dark:text-yellow-300',
}

function DiffBadge({ state }: { state: DiffState }) {
  const { t } = useTranslation()
  const icons = {
    added: <Plus className="mr-1 h-3 w-3" />,
    removed: <Minus className="mr-1 h-3 w-3" />,
    modified: <Pencil className="mr-1 h-3 w-3" />,
    unchanged: null,
  }
  const labels = {
    added: t('audit.diffAdded', 'Új'),
    removed: t('audit.diffRemoved', 'Törölt'),
    modified: t('audit.diffModified', 'Módosított'),
    unchanged: t('audit.diffUnchanged', 'Változatlan'),
  }

  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${DIFF_BADGE_STYLES[state]}`}
    >
      {icons[state]}
      {labels[state]}
    </span>
  )
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
    return (
      <pre className="overflow-auto rounded-md bg-destructive/10 p-3 text-xs">
        {t('audit.invalidJson', 'Érvénytelen JSON:')}
        {'\n'}
        {changesJson}
      </pre>
    )
  }

  const allKeys = Array.from(new Set([...Object.keys(before), ...Object.keys(after)])).sort()
  const summary = {
    added: allKeys.filter((k) => classifyChange(before[k], after[k]) === 'added').length,
    removed: allKeys.filter((k) => classifyChange(before[k], after[k]) === 'removed').length,
    modified: allKeys.filter((k) => classifyChange(before[k], after[k]) === 'modified').length,
    unchanged: allKeys.filter((k) => classifyChange(before[k], after[k]) === 'unchanged').length,
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          {t('audit.changesBefore')} <ArrowRight className="h-3 w-3" /> {t('audit.changesAfter')}
        </CardTitle>
        <CardDescription className="flex flex-wrap gap-2 text-xs">
          <span>
            {allKeys.length} {t('audit.fieldsCompared', 'mező')}
          </span>
          {summary.added > 0 && <DiffBadge state="added" />}
          {summary.removed > 0 && <DiffBadge state="removed" />}
          {summary.modified > 0 && <DiffBadge state="modified" />}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="overflow-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-border">
                <th className="px-2 py-1 text-left font-medium">{t('audit.field', 'Mező')}</th>
                <th className="px-2 py-1 text-left font-medium">{t('audit.state', 'Állapot')}</th>
                <th className="px-2 py-1 text-left font-medium">{t('audit.changesBefore')}</th>
                <th className="px-2 py-1 text-left font-medium">{t('audit.changesAfter')}</th>
              </tr>
            </thead>
            <tbody>
              {allKeys.map((key) => {
                const beforeVal = before[key]
                const afterVal = after[key]
                const state = classifyChange(beforeVal, afterVal)
                return (
                  <tr key={key} className={DIFF_ROW_STYLES[state]}>
                    <td className="px-2 py-1 font-mono font-semibold">{key}</td>
                    <td className="px-2 py-1">
                      <DiffBadge state={state} />
                    </td>
                    <td className="px-2 py-1 font-mono">
                      {beforeVal === undefined ? (
                        <span className="text-muted-foreground">—</span>
                      ) : (
                        JSON.stringify(beforeVal)
                      )}
                    </td>
                    <td className="px-2 py-1 font-mono">
                      {afterVal === undefined ? (
                        <span className="text-muted-foreground">—</span>
                      ) : (
                        JSON.stringify(afterVal)
                      )}
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
