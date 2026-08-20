import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { type ColumnDef } from '@tanstack/react-table'
import { Plus, Pencil, Trash2, Eye, X, Wrench, RotateCcw, PackageX } from 'lucide-react'
import { Link } from 'react-router-dom'
import { DataTable } from '@/components/DataTable/DataTable'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { DebouncedInput } from '@/components/ui/debounced-input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { deviceApi, type Device } from '@/features/device/api/deviceApi'
import { StatusBadge } from '@/features/assignment/components/StatusBadge'
import { LocationTreeSelector } from '@/features/location/components/LocationTreeSelector'
import { locationApi, type LocationTreeNode } from '@/features/location/api/locationApi'
import { useAuthStore } from '@/lib/store/authStore'
import { toast } from 'sonner'
import { resolveToastMessage } from '@/lib/utils/toastUtils'
import { deviceKeys, locationKeys } from '@/lib/api/queryKeys'
import {
  invalidateAssignmentWorkflow,
  invalidateMaintenanceWorkflow,
  invalidateDisposalWorkflow,
} from '@/lib/api/invalidation'

const ALL_STATUSES: Device['status'][] = [
  'PENDING',
  'IN_STORAGE',
  'ASSIGNED',
  'PENDING_MAINTENANCE',
  'MAINTENANCE',
  'PENDING_DISPOSAL',
  'DISPOSED',
]

const STATUS_I18N_KEY: Record<Device['status'], string> = {
  PENDING: 'devices.statusPending',
  ASSIGNED: 'devices.statusAssigned',
  IN_STORAGE: 'devices.statusInStorage',
  PENDING_MAINTENANCE: 'devices.statusPendingMaintenance',
  MAINTENANCE: 'devices.statusMaintenance',
  PENDING_DISPOSAL: 'devices.statusPendingDisposal',
  DISPOSED: 'devices.statusDisposed',
}

export function DevicesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.permissions)
  const canCreate = permissions.includes('DEVICE_CREATE')
  const canUpdate = permissions.includes('DEVICE_UPDATE')
  const canDelete = permissions.includes('DEVICE_DELETE')
  const canRequestMaintenance = permissions.includes('DEVICE_MAINTENANCE_REQUEST')
  const canRequestDisposal = permissions.includes('DEVICE_DISPOSE_REQUEST')

  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [typeFilter, setTypeFilter] = useState<string>('')
  const [editingDevice, setEditingDevice] = useState<Device | null>(null)
  const [editType, setEditType] = useState('')

  // Maintenance & Dispose dialog states
  const [maintenanceDevice, setMaintenanceDevice] = useState<Device | null>(null)
  const [maintenanceReason, setMaintenanceReason] = useState('')
  const [disposeDeviceItem, setDisposeDeviceItem] = useState<Device | null>(null)
  const [disposeReason, setDisposeReason] = useState('')
  const [deleteDeviceId, setDeleteDeviceId] = useState<number | null>(null)

  const pageSize = 20

  const { data, isLoading } = useQuery({
    queryKey: deviceKeys.list({ page, pageSize, search, statusFilter, typeFilter }),
    queryFn: () =>
      deviceApi.findAll({
        page,
        size: pageSize,
        inventoryNumber: search || undefined,
        status: statusFilter || undefined,
        type: typeFilter || undefined,
      }),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: Partial<Device> }) =>
      deviceApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: deviceKeys.all })
      setEditingDevice(null)
      toast.success(t('common.updated', 'Sikeresen frissítve'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const requestMaintenanceMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      deviceApi.requestMaintenance(id, reason),
    onSuccess: () => {
      invalidateMaintenanceWorkflow(queryClient)
      setMaintenanceDevice(null)
      setMaintenanceReason('')
      toast.success(
        t('devices.requestMaintenanceSuccess', 'Karbantartási kérelem sikeresen elküldve')
      )
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const returnFromMaintenanceMutation = useMutation({
    mutationFn: (id: number) => deviceApi.returnFromMaintenance(id),
    onSuccess: () => {
      invalidateMaintenanceWorkflow(queryClient)
      toast.success(
        t('devices.returnedFromMaintenanceSuccess', 'Eszköz visszavéve karbantartásból')
      )
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const requestDisposalMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      deviceApi.requestDisposal(id, reason),
    onSuccess: () => {
      invalidateDisposalWorkflow(queryClient)
      setDisposeDeviceItem(null)
      setDisposeReason('')
      toast.success(t('devices.requestDisposalSuccess', 'Selejtezési kérelem sikeresen elküldve'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deviceApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: deviceKeys.all })
      setDeleteDeviceId(null)
      toast.success(t('common.deleted', 'Sikeresen törölve'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const openEdit = (device: Device) => {
    setEditingDevice(device)
    setEditType(device.type)
  }

  const handleEditSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!editingDevice) return
    const payload: Partial<Device> = {}
    if (editType !== editingDevice.type) payload.type = editType

    if (Object.keys(payload).length === 0) {
      setEditingDevice(null)
      return
    }
    updateMutation.mutate({ id: editingDevice.id, payload })
  }

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
        cell: (info) => <StatusBadge status={info.row.original.status as any} />,
      },
      {
        id: 'actions',
        header: t('common.actions'),
        cell: (info) => {
          const device = info.row.original
          return (
            <div className="flex items-center gap-1">
              <Button variant="ghost" size="icon" asChild title={t('devices.viewDetails')}>
                <Link to={`/devices/${device.id}`}>
                  <Eye className="h-4 w-4" />
                </Link>
              </Button>
              {canUpdate && (
                <>
                  <Button
                    variant="ghost"
                    size="icon"
                    title={t('common.edit')}
                    onClick={() => openEdit(device)}
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>

                  {(device.status === 'IN_STORAGE' || device.status === 'ASSIGNED') &&
                    canRequestMaintenance && (
                      <Button
                        variant="ghost"
                        size="icon"
                        title={t('devices.requestMaintenance', 'Karbantartás kérése')}
                        onClick={() => setMaintenanceDevice(device)}
                      >
                        <Wrench className="h-4 w-4 text-amber-500 hover:text-amber-600" />
                      </Button>
                    )}

                  {device.status === 'MAINTENANCE' && (
                    <Button
                      variant="ghost"
                      size="icon"
                      title={t('devices.returnFromMaintenance', 'Visszavétel raktárba')}
                      onClick={() => returnFromMaintenanceMutation.mutate(device.id)}
                    >
                      <RotateCcw className="h-4 w-4 text-emerald-500 hover:text-emerald-600" />
                    </Button>
                  )}

                  {(device.status === 'IN_STORAGE' || device.status === 'MAINTENANCE') &&
                    canRequestDisposal && (
                      <Button
                        variant="ghost"
                        size="icon"
                        title={t('devices.requestDisposal', 'Selejtezés kérése')}
                        onClick={() => setDisposeDeviceItem(device)}
                      >
                        <PackageX className="h-4 w-4 text-rose-500 hover:text-rose-600" />
                      </Button>
                    )}
                </>
              )}
              {canDelete && device.status === 'DISPOSED' && (
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('common.delete', 'Végleges törlés')}
                  onClick={() => setDeleteDeviceId(device.id)}
                >
                  <Trash2 className="h-4 w-4 text-destructive hover:text-destructive/80" />
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [
      t,
      canUpdate,
      canDelete,
      canRequestMaintenance,
      canRequestDisposal,
      returnFromMaintenanceMutation,
    ]
  )

  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [deviceType, setDeviceType] = useState('laptop')
  const [inventoryNumber, setInventoryNumber] = useState('')
  const [storageLocationId, setStorageLocationId] = useState<number | null>(null)
  const [storageSelectorOpen, setStorageSelectorOpen] = useState(false)

  const { data: locationTree } = useQuery({
    queryKey: locationKeys.tree(),
    queryFn: () => locationApi.findTree(),
  })

  const selectedStorageLocation: LocationTreeNode | null = useMemo(() => {
    if (!locationTree || storageLocationId == null) return null
    const findInTree = (nodes: LocationTreeNode[]): LocationTreeNode | null => {
      for (const n of nodes) {
        if (n.id === storageLocationId) return n
        const found = findInTree(n.children || [])
        if (found) return found
      }
      return null
    }
    return findInTree(locationTree)
  }, [locationTree, storageLocationId])

  const createMutation = useMutation({
    mutationFn: deviceApi.create,
    onSuccess: () => {
      invalidateAssignmentWorkflow(queryClient)
      setIsCreateOpen(false)
      setInventoryNumber('')
      setStorageLocationId(null)
      toast.success(t('common.created', 'Sikeresen létrehozva'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!inventoryNumber || storageLocationId == null) return
    createMutation.mutate({
      type: deviceType,
      inventoryNumber,
      storageLocationId,
    } as any)
  }

  const hasActiveFilters = Boolean(statusFilter || typeFilter || search)

  const clearFilters = () => {
    setStatusFilter('')
    setTypeFilter('')
    setSearch('')
    setPage(0)
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('devices.title')}</h1>
        {canCreate && (
          <Button onClick={() => setIsCreateOpen(!isCreateOpen)}>
            <Plus className="mr-2 h-4 w-4" />
            {t('devices.create')}
          </Button>
        )}
      </div>

      {/* Status Quick Filter Tabs */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-card p-3">
        <div className="flex flex-wrap items-center gap-1.5">
          <button
            type="button"
            className={`rounded-md px-3 py-1.5 text-xs font-medium transition-all ${
              statusFilter === ''
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'bg-muted/50 text-muted-foreground hover:bg-accent hover:text-foreground'
            }`}
            onClick={() => {
              setStatusFilter('')
              setPage(0)
            }}
          >
            {t('common.all', 'Összes')}
          </button>
          {ALL_STATUSES.map((st) => (
            <button
              key={st}
              type="button"
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition-all ${
                statusFilter === st
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'bg-muted/50 text-muted-foreground hover:bg-accent hover:text-foreground'
              }`}
              onClick={() => {
                setStatusFilter(st)
                setPage(0)
              }}
            >
              {t(STATUS_I18N_KEY[st])}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          <DebouncedInput
            placeholder={t('devices.type') + '...'}
            value={typeFilter}
            onDebouncedChange={(val) => {
              setTypeFilter(val)
              setPage(0)
            }}
            className="h-8 w-44 text-xs"
            delay={300}
          />
          {hasActiveFilters && (
            <Button variant="ghost" size="sm" onClick={clearFilters} className="h-8 text-xs">
              <X className="mr-1 h-3.5 w-3.5" />
              {t('common.clear', 'Törlés')}
            </Button>
          )}
        </div>
      </div>

      {isCreateOpen && (
        <div className="max-w-lg space-y-4 rounded-lg border border-border bg-card p-6 shadow-lg">
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
              <Label className="text-xs text-muted-foreground">
                {t('assignments.toLocation')} <span className="text-destructive">*</span>
              </Label>
              <Button
                type="button"
                variant="outline"
                className="w-full justify-start"
                onClick={() => setStorageSelectorOpen(true)}
              >
                {selectedStorageLocation ? (
                  <span className="font-mono text-xs">{selectedStorageLocation.name}</span>
                ) : (
                  <span className="text-muted-foreground">{t('devices.selectLocation')}</span>
                )}
              </Button>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                {t('common.cancel')}
              </Button>
              <Button
                type="submit"
                disabled={createMutation.isPending || storageLocationId == null}
              >
                {t('common.save')}
              </Button>
            </div>
          </form>
        </div>
      )}

      <LocationTreeSelector
        open={storageSelectorOpen}
        onOpenChange={setStorageSelectorOpen}
        onSelect={(id) => setStorageLocationId(id)}
        selectedId={storageLocationId}
        onlyStorageType
      />

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
        onSearchChange={(val) => {
          setSearch(val)
          setPage(0)
        }}
        searchPlaceholder={t('devices.inventoryNumber')}
      />

      {/* Edit Device Dialog */}
      <Dialog
        open={editingDevice !== null}
        onOpenChange={(open) => !open && setEditingDevice(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('devices.edit', 'Eszköz szerkesztése')}</DialogTitle>
            <DialogDescription>
              {editingDevice?.inventoryNumber} (#{editingDevice?.id})
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleEditSubmit} className="space-y-4 py-3">
            <div>
              <Label htmlFor="edit-dev-type" className="text-xs text-muted-foreground">
                {t('devices.type')}
              </Label>
              <Input
                id="edit-dev-type"
                value={editType}
                onChange={(e) => setEditType(e.target.value)}
                required
              />
            </div>
            <div>
              <Label className="mb-1 block text-xs text-muted-foreground">
                {t('devices.status')}
              </Label>
              <div className="pt-1">
                {editingDevice && <StatusBadge status={editingDevice.status as any} />}
              </div>
              <p className="mt-1.5 text-[11px] text-muted-foreground">
                A státusz az eszköz hozzárendeléseit tükrözi, nem módosítható közvetlenül.
              </p>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditingDevice(null)}
                disabled={updateMutation.isPending}
              >
                {t('common.cancel')}
              </Button>
              <Button type="submit" disabled={updateMutation.isPending}>
                {t('common.save')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
      {/* Karbantartás kérése Dialog */}
      <Dialog
        open={maintenanceDevice !== null}
        onOpenChange={(open) => !open && setMaintenanceDevice(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('devices.requestMaintenance', 'Karbantartás kérése')}</DialogTitle>
            <DialogDescription>
              {maintenanceDevice?.inventoryNumber} (#{maintenanceDevice?.id}) —{' '}
              {t(
                'devices.requestMaintenanceDesc',
                'Kérelem benyújtása karbantartásra indoklással.'
              )}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div>
              <Label htmlFor="tbl-maint-reason" className="text-xs text-muted-foreground">
                {t('devices.maintenanceReason', 'Karbantartás indoka')}
              </Label>
              <Input
                id="tbl-maint-reason"
                value={maintenanceReason}
                onChange={(e) => setMaintenanceReason(e.target.value)}
                placeholder={t(
                  'devices.maintenanceReasonPlaceholder',
                  'pl. kijelző hiba, akkumulátor csere'
                )}
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setMaintenanceDevice(null)}
              disabled={requestMaintenanceMutation.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button
              onClick={() => {
                if (maintenanceDevice) {
                  requestMaintenanceMutation.mutate({
                    id: maintenanceDevice.id,
                    reason: maintenanceReason,
                  })
                }
              }}
              disabled={requestMaintenanceMutation.isPending}
            >
              {t('devices.submitRequest', 'Kérelem elküldése')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Selejtezés kérése Dialog */}
      <Dialog
        open={disposeDeviceItem !== null}
        onOpenChange={(open) => !open && setDisposeDeviceItem(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('devices.requestDisposal', 'Selejtezés kérése')}</DialogTitle>
            <DialogDescription>
              {t(
                'devices.requestDisposalDesc',
                'Figyelem: A jóváhagyott selejtezés végleges állapotot eredményez.'
              )}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div>
              <Label htmlFor="tbl-dispose-reason" className="text-xs text-muted-foreground">
                {t('devices.disposeReason', 'Selejtezés indoka')}
              </Label>
              <Input
                id="tbl-dispose-reason"
                value={disposeReason}
                onChange={(e) => setDisposeReason(e.target.value)}
                placeholder={t('devices.disposeReasonPlaceholder', 'pl. gazdaságtalanul javítható')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setDisposeDeviceItem(null)}
              disabled={requestDisposalMutation.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                if (disposeDeviceItem) {
                  requestDisposalMutation.mutate({
                    id: disposeDeviceItem.id,
                    reason: disposeReason,
                  })
                }
              }}
              disabled={requestDisposalMutation.isPending}
            >
              {t('devices.submitRequest', 'Kérelem elküldése')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteDeviceId !== null}
        onOpenChange={(open) => !open && setDeleteDeviceId(null)}
        description={t(
          'devices.confirmDelete',
          'Biztosan véglegesen törölni szeretnéd ezt az eszközt?'
        )}
        loading={deleteMutation.isPending}
        onConfirm={() => {
          if (deleteDeviceId) {
            deleteMutation.mutate(deleteDeviceId)
          }
        }}
      />
    </div>
  )
}
