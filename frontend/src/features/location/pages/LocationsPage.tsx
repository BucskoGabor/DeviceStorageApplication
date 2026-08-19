import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { type ColumnDef } from '@tanstack/react-table'
import { Plus, Trash2, Pencil, List, TreePine, Eye } from 'lucide-react'
import { DataTable } from '@/components/DataTable/DataTable'
import { locationApi, type Location } from '@/features/location/api/locationApi'
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
import { useAuthStore } from '@/lib/store/authStore'
import { LocationTreeView } from '@/features/location/components/LocationTreeView'
import {
  LocationTreeSelector,
  findLocationNode,
} from '@/features/location/components/LocationTreeSelector'
import { locationKeys } from '@/lib/api/queryKeys'
type ViewMode = 'list' | 'tree'
export function LocationsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.permissions)
  const canCreate = permissions.includes('LOCATION_CREATE')
  const canUpdate = permissions.includes('LOCATION_UPDATE')
  const canDelete = permissions.includes('LOCATION_DELETE')
  const [page, setPage] = useState(0)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [editingLocation, setEditingLocation] = useState<Location | null>(null)
  const [deleteLocationId, setDeleteLocationId] = useState<number | null>(null)
  // Form states for create
  const [name, setName] = useState('')
  const [type, setType] = useState<'CLASSROOM' | 'OFFICE' | 'STORAGE' | 'GROUP'>('OFFICE')
  const [parentId, setParentId] = useState<number | null>(null)
  const [parentName, setParentName] = useState<string>('')
  const [parentSelectorOpen, setParentSelectorOpen] = useState(false)
  // Form states for edit
  const [editName, setEditName] = useState('')
  const [editType, setEditType] = useState<'CLASSROOM' | 'OFFICE' | 'STORAGE' | 'GROUP'>('OFFICE')
  const [editParentId, setEditParentId] = useState<number | null>(null)
  const [editParentName, setEditParentName] = useState<string>('')
  const [editParentSelectorOpen, setEditParentSelectorOpen] = useState(false)
  const [viewMode, setViewMode] = useState<ViewMode>('list')
  const pageSize = 20
  const { data, isLoading } = useQuery({
    queryKey: locationKeys.list({ page, pageSize }),
    queryFn: () => locationApi.findAll({ page, size: pageSize }),
  })
  const { data: tree, isLoading: treeLoading } = useQuery({
    queryKey: locationKeys.tree(),
    queryFn: () => locationApi.findTree(),
    enabled: true,
  })
  const createMutation = useMutation({
    mutationFn: locationApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: locationKeys.all })
      setIsCreateOpen(false)
      setName('')
      setParentId(null)
      setParentName('')
      toast.success(t('common.created', 'Sikeresen létrehozva'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })
  const updateMutation = useMutation({
    mutationFn: ({
      id,
      payload,
    }: {
      id: number
      payload: Parameters<typeof locationApi.update>[1]
    }) => locationApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: locationKeys.all })
      setEditingLocation(null)
      toast.success(t('common.updated', 'Sikeresen frissítve'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })
  const deleteMutation = useMutation({
    mutationFn: locationApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: locationKeys.all })
      toast.success(t('common.deleted', 'Sikeresen törölve'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })
  const openEdit = (loc: Location) => {
    setEditingLocation(loc)
    setEditName(loc.name)
    setEditType(loc.type)
    setEditParentId(loc.parentId ?? null)
    const pNode = findLocationNode(tree, loc.parentId)
    setEditParentName(pNode ? pNode.name : loc.parentId != null ? `Helyszín #${loc.parentId}` : '')
  }
  const handleEditSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!editingLocation || !editName) return
    const payload: Parameters<typeof locationApi.update>[1] = {}
    if (editName !== editingLocation.name) payload.name = editName
    if (editType !== editingLocation.type) payload.type = editType
    if (editParentId !== (editingLocation.parentId ?? null)) payload.parentId = editParentId
    if (Object.keys(payload).length === 0) {
      setEditingLocation(null)
      return
    }
    updateMutation.mutate({ id: editingLocation.id, payload })
  }
  const columns = useMemo<ColumnDef<Location, unknown>[]>(
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
        header: t('locations.name'),
      },
      {
        id: 'type',
        accessorKey: 'type',
        header: t('locations.type'),
        cell: (info) => {
          const type = info.getValue() as string
          return (
            <Badge variant="secondary">
              {t(`locations.type${type.charAt(0) + type.slice(1).toLowerCase()}`, type)}
            </Badge>
          )
        },
      },
      {
        id: 'parentId',
        accessorKey: 'parentId',
        header: t('locations.parent'),
        cell: (info) => {
          const pId = info.getValue() as number | null
          if (!pId) return <span className="text-muted-foreground">—</span>
          const pNode = findLocationNode(tree, pId)
          return <span className="text-xs">{pNode ? pNode.name : `#${pId}`}</span>
        },
      },
      {
        id: 'actions',
        header: t('common.actions', 'Műveletek'),
        cell: (info) => {
          const location = info.row.original
          return (
            <div className="flex gap-1">
              <Button
                variant="ghost"
                size="icon"
                title={t('common.details', 'Részletek')}
                onClick={() => navigate(`/locations/${location.id}`)}
              >
                <Eye className="h-4 w-4 text-muted-foreground hover:text-foreground" />
              </Button>
              {canUpdate && (
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('common.edit', 'Szerkesztés')}
                  onClick={() => openEdit(location)}
                >
                  <Pencil className="h-4 w-4" />
                </Button>
              )}
              {canDelete && (
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('common.delete', 'Törlés')}
                  onClick={() => setDeleteLocationId(location.id)}
                >
                  <Trash2 className="h-4 w-4 text-destructive" />
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [t, canUpdate, canDelete, deleteMutation, navigate, tree]
  )
  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!name) return
    const payload: { name: string; type: Location['type']; parentId?: number | null } = {
      name,
      type,
    }
    if (parentId != null) payload.parentId = parentId
    createMutation.mutate(payload)
  }
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('admin.locations')}</h1>
        <div className="flex gap-2">
          <div className="flex rounded-md border border-border">
            <Button
              variant={viewMode === 'list' ? 'default' : 'ghost'}
              size="sm"
              onClick={() => setViewMode('list')}
              className="rounded-r-none"
            >
              <List className="mr-1 h-4 w-4" />
              {t('locations.viewList')}
            </Button>
            <Button
              variant={viewMode === 'tree' ? 'default' : 'ghost'}
              size="sm"
              onClick={() => setViewMode('tree')}
              className="rounded-l-none"
            >
              <TreePine className="mr-1 h-4 w-4" />
              {t('locations.viewTree')}
            </Button>
          </div>
          {canCreate && (
            <Button onClick={() => setIsCreateOpen(!isCreateOpen)}>
              <Plus className="mr-2 h-4 w-4" />
              {t('locations.create')}
            </Button>
          )}
        </div>
      </div>
      {isCreateOpen && (
        <div className="max-w-lg space-y-4 rounded-lg border border-border bg-card p-6 shadow-lg">
          <h2 className="text-lg font-semibold">{t('locations.create')}</h2>
          <form onSubmit={handleCreateSubmit} className="space-y-3">
            <div>
              <Label htmlFor="loc-name" className="text-xs text-muted-foreground">
                {t('locations.name')}
              </Label>
              <Input
                id="loc-name"
                placeholder="pl. Labor 204"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="loc-type" className="text-xs text-muted-foreground">
                {t('locations.type')}
              </Label>
              <select
                id="loc-type"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={type}
                onChange={(e: any) => setType(e.target.value)}
              >
                <option value="OFFICE">{t('locations.typeOffice')}</option>
                <option value="CLASSROOM">{t('locations.typeClassroom')}</option>
                <option value="STORAGE">{t('locations.typeStorage')}</option>
                <option value="GROUP">{t('locations.typeGroup')}</option>
              </select>
            </div>
            <div>
              <Label className="text-xs text-muted-foreground">{t('locations.parent')}</Label>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  className="flex-1 justify-start"
                  onClick={() => setParentSelectorOpen(true)}
                >
                  {parentName ? (
                    <span className="text-xs font-medium text-foreground">{parentName}</span>
                  ) : (
                    <span className="text-muted-foreground">
                      {t('locations.noParent', 'Nincs szülő (root)')}
                    </span>
                  )}
                </Button>
                {parentId != null && (
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => {
                      setParentId(null)
                      setParentName('')
                    }}
                  >
                    ×
                  </Button>
                )}
              </div>
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
      {viewMode === 'list' ? (
        <DataTable
          data={data?.content ?? []}
          columns={columns}
          isLoading={isLoading}
          page={page}
          pageSize={pageSize}
          totalElements={data?.totalElements ?? 0}
          onPageChange={setPage}
        />
      ) : (
        <LocationTreeView tree={tree ?? []} isLoading={treeLoading} />
      )}
      <LocationTreeSelector
        open={parentSelectorOpen}
        onOpenChange={setParentSelectorOpen}
        onSelect={(id, node) => {
          setParentId(id)
          setParentName(node?.name ?? '')
        }}
        selectedId={parentId}
      />
      {/* Edit Location Dialog */}
      <Dialog
        open={editingLocation !== null}
        onOpenChange={(open) => !open && setEditingLocation(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('locations.edit', 'Helyszín szerkesztése')}</DialogTitle>
            <DialogDescription>
              #{editingLocation?.id} ({editingLocation?.name})
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleEditSubmit} className="space-y-3 py-3">
            <div>
              <Label htmlFor="edit-loc-name" className="text-xs text-muted-foreground">
                {t('locations.name')}
              </Label>
              <Input
                id="edit-loc-name"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="edit-loc-type" className="text-xs text-muted-foreground">
                {t('locations.type')}
              </Label>
              <select
                id="edit-loc-type"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={editType}
                onChange={(e: any) => setEditType(e.target.value)}
              >
                <option value="OFFICE">{t('locations.typeOffice')}</option>
                <option value="CLASSROOM">{t('locations.typeClassroom')}</option>
                <option value="STORAGE">{t('locations.typeStorage')}</option>
                <option value="GROUP">{t('locations.typeGroup')}</option>
              </select>
            </div>
            <div>
              <Label className="text-xs text-muted-foreground">{t('locations.parent')}</Label>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  className="flex-1 justify-start"
                  onClick={() => setEditParentSelectorOpen(true)}
                >
                  {editParentName ? (
                    <span className="text-xs font-medium text-foreground">{editParentName}</span>
                  ) : editParentId != null ? (
                    <span className="text-xs font-medium text-foreground">
                      Helyszín #{editParentId}
                    </span>
                  ) : (
                    <span className="text-muted-foreground">
                      {t('locations.noParent', 'Nincs szülő (root)')}
                    </span>
                  )}
                </Button>
                {editParentId != null && (
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => {
                      setEditParentId(null)
                      setEditParentName('')
                    }}
                  >
                    ×
                  </Button>
                )}
              </div>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditingLocation(null)}
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
      <ConfirmDialog
        open={deleteLocationId !== null}
        onOpenChange={(open) => !open && setDeleteLocationId(null)}
        description={t('locations.confirmDelete')}
        onConfirm={() => {
          if (deleteLocationId) {
            deleteMutation.mutate(deleteLocationId)
            setDeleteLocationId(null)
          }
        }}
      />
      <LocationTreeSelector
        open={editParentSelectorOpen}
        onOpenChange={setEditParentSelectorOpen}
        onSelect={(id, node) => {
          setEditParentId(id)
          setEditParentName(node?.name ?? '')
        }}
        selectedId={editParentId}
      />
    </div>
  )
}
