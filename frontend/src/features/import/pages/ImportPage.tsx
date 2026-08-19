import { useState, useCallback } from 'react'
import { useDropzone } from 'react-dropzone'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deviceKeys, userKeys, locationKeys } from '@/lib/api/queryKeys'
import { toast } from 'sonner'
import { Upload, FileSpreadsheet, AlertCircle, CheckCircle2, Download } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import {
  importApi,
  type ImportPreviewResponse,
  type InvalidRow,
} from '@/features/import/api/importApi'

/**
 * ImportPage — Excel import Upload → Preview → Confirm flow.
 *
 * State machine:
 * 1. EMPTY: dropzone megjelenítve, nincs fájl
 * 2. PREVIEW: preview response megjelenítve (valid/invalid sorok)
 * 3. RESULT: import eredmény megjelenítve (inserted/updated/errors)
 */
type ImportState = 'EMPTY' | 'PREVIEW' | 'RESULT'

export function ImportPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportPreviewResponse | null>(null)
  const [importResult, setImportResult] = useState<Awaited<
    ReturnType<typeof importApi.execute>
  > | null>(null)
  const [state, setState] = useState<ImportState>('EMPTY')

  // Preview mutation
  const previewMutation = useMutation({
    mutationFn: (file: File) => importApi.preview(file),
    onSuccess: (data) => {
      setPreview(data)
      setState('PREVIEW')
      toast.success(`${t('import.preview')}: ${data.totalRows} ${t('import.rows')}`, {
        position: 'top-right',
      })
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey
      const fallback = t(
        'import.excelError',
        'A feltöltött fájl nem feldolgozható vagy érvénytelen szerkezetű Excel fájl.'
      )
      toast.error(messageKey ? t(messageKey) : fallback, { position: 'top-right' })
    },
  })

  // Execute mutation
  const executeMutation = useMutation({
    mutationFn: (preview: ImportPreviewResponse) => importApi.execute(preview),
    onSuccess: (data) => {
      setImportResult(data)
      setState('RESULT')
      queryClient.invalidateQueries({ queryKey: deviceKeys.all })
      queryClient.invalidateQueries({ queryKey: userKeys.all })
      queryClient.invalidateQueries({ queryKey: locationKeys.all })
      toast.success(t('import.importSuccess'), { position: 'top-right' })
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey), { position: 'top-right' })
    },
  })

  // Dropzone konfiguráció
  const onDrop = useCallback(
    (acceptedFiles: File[]) => {
      const droppedFile = acceptedFiles[0]
      if (!droppedFile) return
      setFile(droppedFile)
      setState('EMPTY')
      setPreview(null)
      setImportResult(null)
      previewMutation.mutate(droppedFile)
    },
    [previewMutation]
  )

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx'],
    },
    maxSize: 10 * 1024 * 1024, // 10MB
    maxFiles: 1,
  })

  // Hibás sorok CSV export
  const downloadErrorCsv = () => {
    if (!preview?.invalidRows.length) return

    const csv = [
      ['Row', 'EntityType', 'RawData', 'Errors'].join(','),
      ...preview.invalidRows.map((row) =>
        [
          String(row.rowNumber),
          row.entityType,
          `"${row.rawData.replace(/"/g, '""')}"`,
          `"${row.errors.join('; ').replace(/"/g, '""')}"`,
        ].join(',')
      ),
    ].join('\n')

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'import-errors.csv'
    a.click()
    URL.revokeObjectURL(url)
  }

  const handleExecute = () => {
    if (!preview) return
    executeMutation.mutate(preview)
  }

  const handleReset = () => {
    setFile(null)
    setPreview(null)
    setImportResult(null)
    setState('EMPTY')
  }

  return (
    <div className="space-y-6">
      <h1 className="mb-4 text-2xl font-semibold">{t('import.title')}</h1>

      {/* EMPTY state: dropzone */}
      {state === 'EMPTY' && !previewMutation.isPending && (
        <Card>
          <CardHeader>
            <CardTitle>{t('import.uploadTitle')}</CardTitle>
            <CardDescription>{t('import.uploadHelp')}</CardDescription>
          </CardHeader>
          <CardContent>
            <div
              {...getRootProps()}
              className={`flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-12 transition-colors ${
                isDragActive ? 'border-primary bg-primary/5' : 'border-muted-foreground/25'
              }`}
            >
              <input {...getInputProps()} />
              <Upload className="mb-4 h-12 w-12 text-muted-foreground" />
              <p className="text-sm text-muted-foreground">{t('import.dragDrop')}</p>
            </div>
            {previewMutation.isError && (
              <p className="mt-4 text-sm text-destructive">
                {t((previewMutation.error as any)?.response?.data?.messageKey ?? 'internalError')}
              </p>
            )}
          </CardContent>
        </Card>
      )}

      {/* Loading state */}
      {previewMutation.isPending && (
        <Card>
          <CardContent className="p-12 text-center text-muted-foreground">
            {t('common.loading')}...
          </CardContent>
        </Card>
      )}

      {/* PREVIEW state */}
      {state === 'PREVIEW' && preview && (
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="flex items-center gap-2">
                    <FileSpreadsheet className="h-5 w-5" />
                    {file?.name}
                  </CardTitle>
                  <CardDescription>
                    {t('import.totalRows')}: {preview.totalRows}
                  </CardDescription>
                </div>
                <Button variant="outline" onClick={handleReset}>
                  {t('common.cancel')}
                </Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex flex-wrap gap-4">
                <Badge variant="default" className="bg-green-500">
                  <CheckCircle2 className="mr-1 h-3 w-3" />
                  {preview.validUsers.length} {t('import.users')}
                </Badge>
                <Badge variant="default" className="bg-green-500">
                  <CheckCircle2 className="mr-1 h-3 w-3" />
                  {preview.validDevices.length} {t('import.devices')}
                </Badge>
                {preview.invalidRows.length > 0 && (
                  <Badge variant="destructive">
                    <AlertCircle className="mr-1 h-3 w-3" />
                    {preview.invalidRows.length} {t('import.invalidRows')}
                  </Badge>
                )}
              </div>

              {preview.invalidRows.length > 0 && (
                <>
                  <Separator />
                  <div>
                    <div className="mb-2 flex items-center justify-between">
                      <h3 className="text-sm font-semibold">{t('import.invalidRows')}</h3>
                      <Button variant="outline" size="sm" onClick={downloadErrorCsv}>
                        <Download className="mr-2 h-3 w-3" />
                        {t('import.rowNumber')} CSV
                      </Button>
                    </div>
                    <div className="space-y-2">
                      {preview.invalidRows.map((row) => (
                        <InvalidRowItem key={`${row.entityType}-${row.rowNumber}`} row={row} />
                      ))}
                    </div>
                  </div>
                </>
              )}

              <Separator />

              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={handleReset}>
                  {t('common.cancel')}
                </Button>
                <Button
                  onClick={handleExecute}
                  disabled={
                    executeMutation.isPending ||
                    preview.validUsers.length + preview.validDevices.length === 0
                  }
                >
                  {executeMutation.isPending ? t('import.importing') : t('import.import')}
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* RESULT state */}
      {state === 'RESULT' && importResult && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CheckCircle2 className="h-5 w-5 text-green-500" />
              {t('import.importSuccess')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-4 md:grid-cols-5">
              <ResultMetric
                label={t('import.usersInserted')}
                value={importResult.usersInserted}
                color="text-green-500"
              />
              <ResultMetric
                label={t('import.usersUpdated')}
                value={importResult.usersUpdated}
                color="text-blue-500"
              />
              <ResultMetric
                label={t('import.devicesInserted')}
                value={importResult.devicesInserted}
                color="text-green-500"
              />
              <ResultMetric
                label={t('import.devicesUpdated')}
                value={importResult.devicesUpdated}
                color="text-blue-500"
              />
              <ResultMetric
                label={t('import.errors')}
                value={importResult.errors}
                color={importResult.errors > 0 ? 'text-destructive' : 'text-muted-foreground'}
              />
            </div>
            <div className="mt-6 flex justify-end">
              <Button onClick={handleReset}>{t('import.import')}</Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

function InvalidRowItem({ row }: { row: InvalidRow }) {
  const { t } = useTranslation()
  return (
    <div className="rounded-md border border-destructive/30 bg-destructive/5 p-3">
      <div className="flex items-center gap-2">
        <Badge variant="destructive" className="text-xs">
          {t('import.rowNumber')} {row.rowNumber}
        </Badge>
        <span className="text-xs text-muted-foreground">{row.entityType}</span>
      </div>
      <p className="mt-1 font-mono text-xs text-muted-foreground">{row.rawData}</p>
      <ul className="mt-1 list-inside list-disc text-xs text-destructive">
        {row.errors.map((error, i) => (
          <li key={i}>{t(error)}</li>
        ))}
      </ul>
    </div>
  )
}

function ResultMetric({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="rounded-md border border-border p-4 text-center">
      <p className={`text-2xl font-semibold ${color}`}>{value}</p>
      <p className="mt-1 text-xs text-muted-foreground">{label}</p>
    </div>
  )
}
