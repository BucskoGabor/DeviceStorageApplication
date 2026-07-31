import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { type ColumnDef } from '@tanstack/react-table'
import { Plus, Trash2, List, TreePine } from 'lucide-react'
import { DataTable } from '@/components/DataTable/DataTable'
import { locationApi, type Location } from '@/features/location/api/locationApi'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'
import { LocationTreeView } from '@/features/location/components/LocationTreeView'
import { LocationTreeSelector } from '@/features/location/components/LocationTreeSelector'

type ViewMode = 'list' | 'tree'

export function LocationsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [name, setName] = useState('')
  const [type, setType] = useState<'CLASSROOM' | 'OFFICE' | 'STORAGE' | 'GROUP'>('OFFICE')
  const [parentId, setParentId] = useState<number | null>(null)
  const [parentSelectorOpen, setParentSelectorOpen] = useState(false)
  const [viewMode, setViewMode] = useState<ViewMode>('list')
  const pageSize = 20

  const { data, isLoading } = useQuery({
    queryKey: ['locations', page, pageSize],
    queryFn: () => locationApi.findAll({ page, size: pageSize }),
  })

  const { data: tree, isLoading: treeLoading } = useQuery({
    queryKey: ['locations', 'tree'],
    queryFn: () => locationApi.findTree(),
    enabled: viewMode === 'tree',
  })

  const createMutation = useMutation({
    mutationFn: locationApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['locations'] })
      setIsCreateOpen(false)
      setName('')
      setParentId(null)
      toast.success(t('common.created', 'Sikeresen létrehozva'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: locationApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['locations'] })
      toast.success(t('common.deleted', 'Sikeresen törölve'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

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
          return <Badge variant="secondary">{t(`locations.type${type.charAt(0) + type.slice(1).toLowerCase()}`, type)}</Badge>
        },
      },
      {
        id: 'parentId',
        accessorKey: 'parentId',
        header: t('locations.parent'),
        cell: (info) => {
          const parentId = info.getValue()
          return parentId ? <span className="font-mono text-xs">#{String(parentId)}</span> : <span className="text-muted-foreground">—</span>
        },
      },
      {
        id: 'actions',
        header: t('common.actions', 'Műveletek'),
        cell: (info) => {
          const location = info.row.original
          return (
            <Button
              variant="ghost"
              size="icon"
              title={t('common.delete', 'Törlés')}
              onClick={() => {
                if (confirm(t('locations.confirmDelete'))) {
                  deleteMutation.mutate(location.id)
                }
              }}
            >
              <Trash2 className="h-4 w-4 text-destructive" />
            </Button>
          )
        },
      },
    ],
    [t, deleteMutation]
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
          <Button onClick={() => setIsCreateOpen(!isCreateOpen)}>
            <Plus className="mr-2 h-4 w-4" />
            {t('locations.create')}
          </Button>
        </div>
      </div>

      {isCreateOpen && (
        <div className="rounded-lg border border-border bg-card p-6 shadow-lg space-y-4 max-w-lg">
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
                  {parentId != null ? `#${parentId}` : t('locations.noParent')}
                </Button>
                {parentId != null && (
                  <Button type="button" variant="ghost" onClick={() => setParentId(null)}>
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
        onSelect={(id) => setParentId(id)}
        selectedId={parentId}
      />
    </div>
  )
}
