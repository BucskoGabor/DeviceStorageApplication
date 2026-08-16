import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Undo2, Filter, X, Eye } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DataTable } from '@/components/DataTable/DataTable'
import { type ColumnDef } from '@tanstack/react-table'
import { auditApi, type AuditLog } from '@/features/audit/api/auditApi'
import { DiffViewer } from '@/components/DiffViewer/DiffViewer'
import { useAuthStore } from '@/lib/store/authStore'

/**
 * AuditPage — admin/audit táblázat (szűrő, lapozás, diff side panel, rollback gomb).
 */
export function AuditPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.permissions)
  const canRollback = permissions.includes('AUDIT_ROLLBACK')

  const [page, setPage] = useState(0)
  const [filterEmail, setFilterEmail] = useState('')
  const [filterEntityType, setFilterEntityType] = useState('')
  const [filterEntityId, setFilterEntityId] = useState('')
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null)
  const [rollbackConfirmOpen, setRollbackConfirmOpen] = useState(false)
  const pageSize = 20

  const { data, isLoading } = useQuery({
    queryKey: ['audit', page, pageSize, filterEmail, filterEntityType, filterEntityId],
    queryFn: () =>
      auditApi.findAll({
        page,
        size: pageSize,
        userEmail: filterEmail || undefined,
        entityType: filterEntityType || undefined,
        entityId: filterEntityId ? Number(filterEntityId) : undefined,
      }),
  })

  const rollbackMutation = useMutation({
    mutationFn: (id: number) => auditApi.rollback(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['audit'] })
      toast.success(t('audit.rollbackSuccess'), { position: 'top-right' })
      setSelectedLog(null)
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey), { position: 'top-right' })
    },
  })

  const columns: ColumnDef<AuditLog, unknown>[] = [
    {
      id: 'timestamp',
      accessorKey: 'timestamp',
      header: t('audit.timestamp'),
      cell: (info) => new Date(info.getValue() as string).toLocaleString(),
    },
    {
      id: 'userEmail',
      accessorKey: 'userEmail',
      header: t('audit.user'),
    },
    {
      id: 'method',
      accessorKey: 'method',
      header: t('audit.method'),
      cell: (info) => (
        <Badge variant="outline" className="font-mono text-xs">
          {String(info.getValue())}
        </Badge>
      ),
    },
    {
      id: 'endpoint',
      accessorKey: 'endpoint',
      header: t('audit.endpoint'),
      cell: (info) => <span className="font-mono text-xs">{String(info.getValue())}</span>,
    },
    {
      id: 'entityType',
      accessorKey: 'entityType',
      header: t('audit.entityType'),
      cell: (info) => {
        const log = info.row.original
        return log.entityType ? (
          <span className="text-xs">
            {log.entityType} #{log.entityId}
          </span>
        ) : (
          <span className="text-muted-foreground">—</span>
        )
      },
    },
    {
      id: 'httpStatus',
      accessorKey: 'httpStatus',
      header: t('audit.status'),
      cell: (info) => {
        const status = info.getValue() as number
        const variant = status >= 200 && status < 300 ? 'default' : 'destructive'
        return <Badge variant={variant as any}>{status}</Badge>
      },
    },
    {
      id: 'actions',
      header: t('common.actions'),
      cell: (info) => (
        <Button variant="ghost" size="sm" onClick={() => setSelectedLog(info.row.original)}>
          <Eye className="mr-1.5 h-4 w-4" />
          {t('audit.viewDetails')}
        </Button>
      ),
    },
  ]

  const hasActiveFilters = Boolean(filterEmail || filterEntityType || filterEntityId)

  const clearFilters = () => {
    setFilterEmail('')
    setFilterEntityType('')
    setFilterEntityId('')
    setPage(0)
  }

  return (
    <>
      <div className="space-y-6">
      <h1 className="mb-4 text-2xl font-semibold">{t('audit.title')}</h1>

      {/* Szűrők */}
      <Card className="mb-4">
        <CardContent className="flex flex-wrap items-center gap-3 p-4">
          <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
            <Filter className="h-4 w-4" />
            <span>{t('common.search', 'Szűrés')}</span>
          </div>

          <Input
            placeholder={t('audit.user')}
            value={filterEmail}
            onChange={(e) => {
              setFilterEmail(e.target.value)
              setPage(0)
            }}
            className="w-56"
          />

          <Select
            value={filterEntityType}
            onValueChange={(val) => {
              setFilterEntityType(val === '__all__' ? '' : val)
              setPage(0)
            }}
          >
            <SelectTrigger className="w-48">
              <SelectValue placeholder={t('audit.entityType')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">{t('common.all', 'Összes típus')}</SelectItem>
              <SelectItem value="Device">{t('nav.devices')}</SelectItem>
              <SelectItem value="User">{t('nav.users')}</SelectItem>
              <SelectItem value="Location">{t('nav.locations')}</SelectItem>
              <SelectItem value="Assignment">{t('nav.assignments')}</SelectItem>
              <SelectItem value="DeviceAssignment">DeviceAssignment</SelectItem>
              <SelectItem value="DeviceAttachment">{t('nav.attachments')}</SelectItem>
            </SelectContent>
          </Select>

          <Input
            placeholder="Entitás ID (pl. 42)"
            type="number"
            value={filterEntityId}
            onChange={(e) => {
              setFilterEntityId(e.target.value)
              setPage(0)
            }}
            className="w-36"
          />

          {hasActiveFilters && (
            <Button variant="ghost" size="sm" onClick={clearFilters}>
              <X className="mr-1 h-4 w-4" />
              {t('common.clear', 'Törlés')}
            </Button>
          )}
        </CardContent>
      </Card>

      {/* Táblázat */}
      <DataTable
        data={data?.content ?? []}
        columns={columns}
        isLoading={isLoading}
        page={page}
        pageSize={pageSize}
        totalElements={data?.totalElements ?? 0}
        onPageChange={setPage}
      />

      {/* Diff side panel */}
      {selectedLog && (
        <div
          className="fixed inset-0 z-50 bg-black/50"
          onClick={() => setSelectedLog(null)}
        >
          <div
            className="absolute right-0 top-0 h-full w-1/2 overflow-auto bg-background p-6 shadow-lg"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-xl font-semibold">
                {t('audit.viewDetails')} #{selectedLog.id}
              </h2>
              <Button variant="ghost" size="icon" onClick={() => setSelectedLog(null)}>
                <X className="h-4 w-4" />
              </Button>
            </div>

            <Card className="mb-4">
              <CardHeader>
                <CardTitle>{t('audit.timestamp')}</CardTitle>
                <CardDescription>{new Date(selectedLog.timestamp).toLocaleString()}</CardDescription>
              </CardHeader>
              <CardContent>
                <dl className="grid grid-cols-2 gap-2 text-sm">
                  <dt className="font-medium">{t('audit.user')}:</dt>
                  <dd className="font-mono">{selectedLog.userEmail}</dd>
                  <dt className="font-medium">{t('audit.endpoint')}:</dt>
                  <dd className="font-mono">{selectedLog.endpoint}</dd>
                  <dt className="font-medium">{t('audit.method')}:</dt>
                  <dd>
                    <Badge variant="outline">{selectedLog.method}</Badge>
                  </dd>
                  <dt className="font-medium">{t('audit.entityType')}:</dt>
                  <dd>
                    {selectedLog.entityType} #{selectedLog.entityId}
                  </dd>
                </dl>
              </CardContent>
            </Card>

            {/* Indoklás kiemelés, ha van a payloadban */}
            {(() => {
              try {
                if (selectedLog.requestPayload) {
                  const parsed = JSON.parse(selectedLog.requestPayload)
                  if (parsed && parsed.reason) {
                    return (
                      <div className="mb-4 rounded-lg border border-border bg-muted/40 p-4">
                        <span className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                          {t('audit.reason', 'Indoklás / Megjegyzés')}
                        </span>
                        <p className="text-sm font-medium italic text-foreground">
                          "{parsed.reason}"
                        </p>
                      </div>
                    )
                  }
                }
              } catch {
                // Ignore parse errors
              }
              return null
            })()}

            {/* Kérelem részletei (Payload) ha elérhető */}
            {selectedLog.requestPayload && (
              <Card className="mb-4">
                <CardHeader className="py-3">
                  <CardTitle className="text-sm">{t('audit.requestPayload', 'Kérelem adatai (Request Payload)')}</CardTitle>
                </CardHeader>
                <CardContent className="py-2">
                  <pre className="overflow-x-auto rounded bg-muted/60 p-3 font-mono text-xs whitespace-pre-wrap">
                    {selectedLog.requestPayload}
                  </pre>
                </CardContent>
              </Card>
            )}

            {selectedLog.changesJson && (
              <div className="mb-4">
                <DiffViewer changesJson={selectedLog.changesJson} />
              </div>
            )}

            {canRollback && (
              <Button
                variant="destructive"
                onClick={() => setRollbackConfirmOpen(true)}
                disabled={rollbackMutation.isPending}
              >
                <Undo2 className="mr-2 h-4 w-4" />
                {t('audit.rollback')}
              </Button>
            )}
          </div>
        </div>
      )}
    </div>

      <ConfirmDialog
        open={rollbackConfirmOpen}
        onOpenChange={setRollbackConfirmOpen}
        description={t('audit.rollbackConfirm')}
        loading={rollbackMutation.isPending}
        onConfirm={() => {
          if (selectedLog) {
            rollbackMutation.mutate(selectedLog.id)
            setRollbackConfirmOpen(false)
          }
        }}
      />
    </>
  )
}
