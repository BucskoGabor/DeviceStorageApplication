import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { type ColumnDef } from '@tanstack/react-table'
import { Plus, Pencil, Trash2, FileText } from 'lucide-react'
import { Link } from 'react-router-dom'
import { DataTable } from '@/components/DataTable/DataTable'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { deviceApi, type Device } from '@/features/device/api/deviceApi'
import { useAuthStore } from '@/lib/store/authStore'
import { toast } from 'sonner'

const ALL_STATUSES: Device['status'][] = [
  'PENDING',
  'IN_STORAGE',
  'ASSIGNED',
  'MAINTENANCE',
  'DISPOSED',
]

const STATUS_I18N_KEY: Record<Device['status'], string> = {
  PENDING: 'devices.statusPending',
  ASSIGNED: 'devices.statusAssigned',
  IN_STORAGE: 'devices.statusInStorage',
  MAINTENANCE: 'devices.statusMaintenance',
  DISPOSED: 'devices.statusDisposed',
}

export function DevicesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.permissions)
  const canUpdate = permissions.includes('DEVICE_UPDATE')

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const pageSize = 20

  const { data, isLoading } = useQuery({
    queryKey: ['devices', page, pageSize, search],
    queryFn: () =>
      deviceApi.findAll({
        page,
        size: pageSize,
        filter: search ? { inventoryNumber: search } : undefined,
      }),
  })

  const changeStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: Device['status'] }) =>
      deviceApi.changeStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      toast.success(t('devices.statusChanged'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  const columns = useMemo<ColumnDef<Device, unknown>[]>(
    () => [
      {
        id: 'id',
        accessorKey: 'id',
        header: t('devices.id'),
        cell: (info) => <span className="font-mono text-xs">{String(info.getValue())}</span>,
      },
      {
        id: 'inventoryNumber',
        accessorKey: 'inventoryNumber',
        header: t('devices.inventoryNumber'),
      },
      {
        id: 'type',
        accessorKey: 'type',
        header: t('devices.type'),
      },
      {
        id: 'status',
        accessorKey: 'status',
        header: t('devices.status'),
        cell: (info) => {
          const device = info.row.original
          const currentStatus = device.status
          if (!canUpdate) {
            return (
              <span className="rounded-md bg-accent px-2 py-1 text-xs">
                {t(STATUS_I18N_KEY[currentStatus])}
              </span>
            )
          }
          return (
            <Select
              value={currentStatus}
              disabled={changeStatusMutation.isPending}
              onValueChange={(value: Device['status']) => {
                if (value !== currentStatus) {
                  changeStatusMutation.mutate({ id: device.id, status: value })
                }
              }}
            >
              <SelectTrigger className="h-8 w-[160px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ALL_STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {t(STATUS_I18N_KEY[s])}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )
        },
      },
      {
        id: 'actions',
        header: t('common.actions'),
        cell: (info) => {
          const device = info.row.original
          return (
            <div className="flex gap-2">
              <Button variant="ghost" size="icon" asChild title={t('devices.viewDetails')}>
                <Link to={`/admin/devices/${device.id}`}>
                  <FileText className="h-4 w-4" />
                </Link>
              </Button>
              <Button variant="ghost" size="icon" asChild title={t('common.edit')}>
                <Link to={`/admin/devices/${device.id}/edit`}>
                  <Pencil className="h-4 w-4" />
                </Link>
              </Button>
              <Button
                variant="ghost"
                size="icon"
                title={t('common.delete')}
                onClick={() => {
                  if (confirm(t('devices.confirmDelete'))) {
                    deviceApi.delete(device.id).then(() => {
                      queryClient.invalidateQueries({ queryKey: ['devices'] })
                      toast.success(t('common.deleted', 'Sikeresen törölve'))
                    })
                  }
                }}
              >
                <Trash2 className="h-4 w-4 text-destructive" />
              </Button>
            </div>
          )
        },
      },
    ],
    [t, canUpdate, changeStatusMutation, queryClient]
  )

  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [deviceType, setDeviceType] = useState('laptop')
  const [inventoryNumber, setInventoryNumber] = useState('')
  const [deviceStatus, setDeviceStatus] = useState<Device['status']>('IN_STORAGE')

  const createMutation = useMutation({
    mutationFn: deviceApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      setIsCreateOpen(false)
      setInventoryNumber('')
      toast.success(t('common.created', 'Sikeresen létrehozva'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!inventoryNumber) return
    createMutation.mutate({
      type: deviceType,
      inventoryNumber,
      status: deviceStatus,
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('devices.title')}</h1>
        <Button onClick={() => setIsCreateOpen(!isCreateOpen)}>
          <Plus className="mr-2 h-4 w-4" />
          {t('devices.create')}
        </Button>
      </div>

      {isCreateOpen && (
        <div className="rounded-lg border border-border bg-card p-6 shadow-lg space-y-4 max-w-lg">
          <h2 className="text-lg font-semibold">{t('devices.create')}</h2>
          <form onSubmit={handleCreateSubmit} className="space-y-3">
            <div>
              <Label htmlFor="dev-inv" className="text-xs text-muted-foreground">
                {t('devices.inventoryNumber')}
              </Label>
              <Input
                id="dev-inv"
                placeholder="INV-2026-0008"
                value={inventoryNumber}
                onChange={(e) => setInventoryNumber(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="dev-type" className="text-xs text-muted-foreground">
                {t('devices.type')}
              </Label>
              <Input
                id="dev-type"
                placeholder="laptop / desktop / monitor / projector"
                value={deviceType}
                onChange={(e) => setDeviceType(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="dev-status" className="text-xs text-muted-foreground">
                {t('devices.status')}
              </Label>
              <select
                id="dev-status"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={deviceStatus}
                onChange={(e: any) => setDeviceStatus(e.target.value)}
              >
                {ALL_STATUSES.map((s) => (
                  <option key={s} value={s}>{t(STATUS_I18N_KEY[s])}</option>
                ))}
              </select>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                {t('common.cancel')}
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {t('common.save')}
              </Button>
            </div>
          </form>
        </div>
      )}

      <DataTable
        data={data?.content ?? []}
        columns={columns}
        isLoading={isLoading}
        page={page}
        pageSize={pageSize}
        totalElements={data?.totalElements ?? 0}
        onPageChange={setPage}
        searchColumnId="inventoryNumber"
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder={t('devices.inventoryNumber')}
      />
    </div>
  )
}
