import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { type ColumnDef } from '@tanstack/react-table'
import { Plus, Trash2, Pencil, Copy, Check } from 'lucide-react'
import { DataTable } from '@/components/DataTable/DataTable'
import { softwareApi, type Software } from '@/features/software/api/softwareApi'
import { useAuthStore } from '@/lib/store/authStore'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { toast } from 'sonner'
export function SoftwarePage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.permissions)
  const canCreate = permissions.includes('SOFTWARE_CREATE')
  const canUpdate = permissions.includes('SOFTWARE_UPDATE')
  const canDelete = permissions.includes('SOFTWARE_DELETE')
  const canViewKey = permissions.includes('SOFTWARE_LICENSE_VIEW')
  const [page, setPage] = useState(0)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [editingSoftware, setEditingSoftware] = useState<Software | null>(null)
  const [deleteSoftwareId, setDeleteSoftwareId] = useState<number | null>(null)
  const [name, setName] = useState('')
  const [licenseKey, setLicenseKey] = useState('')
  const pageSize = 20
  const { data, isLoading } = useQuery({
    queryKey: ['software', page, pageSize],
    queryFn: () => softwareApi.findAll({ page, size: pageSize }),
  })
  const createMutation = useMutation({
    mutationFn: softwareApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['software'] })
      setIsCreateOpen(false)
      setName('')
      setLicenseKey('')
      toast.success(t('common.created', 'Sikeresen létrehozva'))
    },
    onError: () => {
      toast.error(t('common.error', 'Hiba történt'))
    },
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: { name?: string; licenseKey?: string } }) =>
      softwareApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['software'] })
      setEditingSoftware(null)
      setName('')
      setLicenseKey('')
      toast.success(t('common.updated', 'Sikeresen frissítve'))
    },
    onError: () => {
      toast.error(t('common.error', 'Hiba történt'))
    },
  })
  const deleteMutation = useMutation({
    mutationFn: softwareApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['software'] })
      toast.success(t('common.deleted', 'Sikeresen törölve'))
    },
  })
  const openEdit = (sw: Software) => {
    setEditingSoftware(sw)
    setName(sw.name)
    setLicenseKey(sw.licenseKey ?? '')
  }
  const columns = useMemo<ColumnDef<Software, unknown>[]>(
    () => [
      {
        id: 'id',
        accessorKey: 'id',
        header: 'ID',
        cell: (info) => <span className="font-mono text-xs">{String(info.getValue())}</span>,
      },
      {
        id: 'name',
        accessorKey: 'name',
        header: t('devices.name', 'Szoftver neve'),
      },
      {
        id: 'licenseKey',
        header: t('devices.licenseKey', 'Licence kulcs'),
        cell: (info) => {
          const sw = info.row.original
          return <LicenseKeyCell software={sw} canView={canViewKey} />
        },
      },
      {
        id: 'devices',
        header: t('softwares.installedDevices', 'Eszközök'),
        cell: (info) => {
          const sw = info.row.original
          return <DevicesCell softwareId={sw.id} />
        },
      },
      {
        id: 'actions',
        header: t('common.actions', 'Műveletek'),
        cell: (info) => {
          const sw = info.row.original
          return (
            <div className="flex gap-1">
              {canUpdate && (
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('common.edit', 'Szerkesztés')}
                  onClick={() => openEdit(sw)}
                >
                  <Pencil className="h-4 w-4" />
                </Button>
              )}
              {canDelete && (
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('common.delete', 'Törlés')}
                  onClick={() => setDeleteSoftwareId(sw.id)}
                >
                  <Trash2 className="h-4 w-4 text-destructive" />
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [t, deleteMutation, canViewKey, canUpdate, canDelete]
  )
  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!name || !licenseKey) return
    createMutation.mutate({ name, licenseKey })
  }
  const handleEditSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!editingSoftware) return
    const payload: { name?: string; licenseKey?: string } = {}
    if (name !== editingSoftware.name) payload.name = name
    if (licenseKey && licenseKey !== editingSoftware.licenseKey) payload.licenseKey = licenseKey
    if (Object.keys(payload).length === 0) {
      setEditingSoftware(null)
      return
    }
    updateMutation.mutate({ id: editingSoftware.id, payload })
  }
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('admin.softwares', 'Szoftverek és Licencek')}</h1>
        {canCreate && (
          <Button onClick={() => setIsCreateOpen(!isCreateOpen)}>
            <Plus className="mr-2 h-4 w-4" />
            {t('softwares.create', 'Új szoftver')}
          </Button>
        )}
      </div>
      {isCreateOpen && (
        <div className="rounded-lg border border-border bg-card p-6 shadow-lg space-y-4 max-w-lg">
          <h2 className="text-lg font-semibold">{t('softwares.create', 'Új szoftver és licenc')}</h2>
          <form onSubmit={handleCreateSubmit} className="space-y-3">
            <div>
              <Label className="text-xs text-muted-foreground">{t('devices.name', 'Szoftver neve')}</Label>
              <Input
                placeholder="pl. AutoCAD 2025"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </div>
            <div>
              <Label className="text-xs text-muted-foreground">{t('devices.licenseKey', 'Licenc kulcs')}</Label>
              <Input
                placeholder="XXXX-XXXX-XXXX-XXXX"
                value={licenseKey}
                onChange={(e) => setLicenseKey(e.target.value)}
                required
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                {t('common.cancel', 'Mégse')}
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {t('common.save', 'Mentés')}
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
      />
      <ConfirmDialog
        open={deleteSoftwareId !== null}
        onOpenChange={(open) => !open && setDeleteSoftwareId(null)}
        description={t('softwares.confirmDelete', 'Biztosan törlöd ezt a szoftvert?')}
        onConfirm={() => {
          if (deleteSoftwareId) {
            deleteMutation.mutate(deleteSoftwareId)
            setDeleteSoftwareId(null)
          }
        }}
      />
      {/* Edit Dialog */}
      <Dialog
        open={editingSoftware !== null}
        onOpenChange={(open) => {
          if (!open) {
            setEditingSoftware(null)
            setName('')
            setLicenseKey('')
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('common.edit', 'Szoftver szerkesztése')}</DialogTitle>
            <DialogDescription>
              {editingSoftware?.name}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleEditSubmit} className="space-y-3 py-4">
            <div>
              <Label htmlFor="edit-name" className="text-xs text-muted-foreground">
                {t('devices.name', 'Szoftver neve')}
              </Label>
              <Input
                id="edit-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="edit-license" className="text-xs text-muted-foreground">
                {t('devices.licenseKey', 'Licenc kulcs')}
              </Label>
              <Input
                id="edit-license"
                placeholder={
                  editingSoftware?.licenseKeyMasked ?? 'XXXX-XXXX-XXXX-XXXX'
                }
                value={licenseKey}
                onChange={(e) => setLicenseKey(e.target.value)}
              />
              <p className="mt-1 text-xs text-muted-foreground">
                {canViewKey
                  ? t('softwares.editLicenseHelp', 'Hagyd üresen, ha nem akarod módosítani.')
                  : t('softwares.noViewPermission', 'Nincs jogosultságod a licence kulcs megtekintéséhez — csak a nevet módosíthatod.')}
              </p>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditingSoftware(null)}
                disabled={updateMutation.isPending}
              >
                {t('common.cancel', 'Mégse')}
              </Button>
              <Button type="submit" disabled={updateMutation.isPending}>
                {t('common.save', 'Mentés')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
function LicenseKeyCell({ software, canView }: { software: Software; canView: boolean }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  if (canView && software.licenseKey) {
    const handleCopy = () => {
      navigator.clipboard.writeText(software.licenseKey!)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    }
    return (
      <div className="flex items-center gap-1">
        <span className="font-mono text-xs">{software.licenseKey}</span>
        <Button variant="ghost" size="icon" className="h-6 w-6" onClick={handleCopy} title={t('devices.copy', 'Másolás')}>
          {copied ? <Check className="h-3 w-3 text-green-600" /> : <Copy className="h-3 w-3" />}
        </Button>
      </div>
    )
  }
  return (
    <Badge variant="outline" className="font-mono text-xs">
      {software.licenseKeyMasked ?? '****-****-****-'}
    </Badge>
  )
}
function DevicesCell({ softwareId }: { softwareId: number }) {
  const { t } = useTranslation()
  const { data: devices, isLoading } = useQuery({
    queryKey: ['software-devices', softwareId],
    queryFn: () => softwareApi.findDevicesBySoftware(softwareId),
    staleTime: 60000,
  })
  if (isLoading) {
    return <span className="text-xs text-muted-foreground">…</span>
  }
  if (!devices || devices.length === 0) {
    return <span className="text-xs text-muted-foreground">—</span>
  }
  return (
    <div className="flex max-w-[200px] flex-wrap gap-1">
      {devices.slice(0, 3).map((d) => (
        <Badge key={d.id} variant="secondary" className="font-mono text-xs">
          {d.inventoryNumber}
        </Badge>
      ))}
      {devices.length > 3 && (
        <Badge variant="outline" className="text-xs">
          +{devices.length - 3} {t('softwares.more', 'további')}
        </Badge>
      )}
    </div>
  )
}
