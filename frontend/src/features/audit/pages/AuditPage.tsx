import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Undo2, Filter, X } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DataTable } from '@/components/DataTable/DataTable'
import { type ColumnDef } from '@tanstack/react-table'
import { auditApi, type AuditLog } from '@/features/audit/api/auditApi'
import { AdminLayout } from '@/features/admin/layouts/AdminLayout'

/**
 * AuditPage — admin/audit táblázat (szűrő, lapozás, diff side panel, rollback gomb).
 */
export function AuditPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [filterEmail, setFilterEmail] = useState('')
  const [filterEntityType, setFilterEntityType] = useState('')
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null)
  const pageSize = 20

  const { data, isLoading } = useQuery({
    queryKey: ['audit', page, pageSize, filterEmail, filterEntityType],
    queryFn: () =>
      auditApi.findAll({
        page,
        size: pageSize,
        userEmail: filterEmail || undefined,
        entityType: filterEntityType || undefined,
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
          {t('audit.viewDetails')}
        </Button>
      ),
    },
  ]

  return (
    <AdminLayout>
      <h1 className="mb-4 text-2xl font-semibold">{t('audit.title')}</h1>

      {/* Szűrők */}
      <Card className="mb-4">
        <CardContent className="flex flex-wrap gap-2 p-4">
          <div className="flex flex-1 items-center gap-2">
            <Filter className="h-4 w-4 text-muted-foreground" />
            <Input
              placeholder={t('audit.user')}
              value={filterEmail}
              onChange={(e) => setFilterEmail(e.target.value)}
              className="max-w-xs"
            />
            {filterEmail && (
              <Button variant="ghost" size="icon" onClick={() => setFilterEmail('')}>
                <X className="h-4 w-4" />
              </Button>
            )}
          </div>
          <Select value={filterEntityType} onValueChange={setFilterEntityType}>
            <SelectTrigger className="w-48">
              <SelectValue placeholder={t('audit.entityType')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="Device">{t('nav.devices')}</SelectItem>
              <SelectItem value="User">{t('nav.users')}</SelectItem>
              <SelectItem value="Location">{t('nav.locations')}</SelectItem>
              <SelectItem value="Assignment">{t('nav.assignments')}</SelectItem>
              <SelectItem value="DeviceAssignment">DeviceAssignment</SelectItem>
              <SelectItem value="DeviceAttachment">{t('nav.attachments')}</SelectItem>
            </SelectContent>
          </Select>
          {filterEntityType && (
            <Button variant="ghost" size="icon" onClick={() => setFilterEntityType('')}>
              <X className="h-4 w-4" />
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
        searchColumnId="userEmail"
        searchValue={filterEmail}
        onSearchChange={setFilterEmail}
        searchPlaceholder={t('audit.user')}
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

            {selectedLog.changesJson && (
              <Card className="mb-4">
                <CardHeader>
                  <CardTitle>{t('audit.changesBefore')} / {t('audit.changesAfter')}</CardTitle>
                </CardHeader>
                <CardContent>
                  <pre className="overflow-auto rounded-md bg-muted p-3 text-xs">
                    {JSON.stringify(JSON.parse(selectedLog.changesJson), null, 2)}
                  </pre>
                </CardContent>
              </Card>
            )}

            <Button
              variant="destructive"
              onClick={() => {
                if (confirm(t('audit.rollbackConfirm'))) {
                  rollbackMutation.mutate(selectedLog.id)
                }
              }}
              disabled={rollbackMutation.isPending}
            >
              <Undo2 className="mr-2 h-4 w-4" />
              {t('audit.rollback')}
            </Button>
          </div>
        </div>
      )}
    </AdminLayout>
  )
}
