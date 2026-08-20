import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { type ColumnDef } from '@tanstack/react-table'
import { Plus, Trash2, Unlock, Pencil, Eye, MapPin } from 'lucide-react'
import { Link } from 'react-router-dom'
import { DataTable } from '@/components/DataTable/DataTable'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { userApi, type AppUserDto } from '@/features/user/api/userApi'
import { roleApi, type PermissionDto } from '@/features/role/api/roleApi'
import { useAuthStore } from '@/lib/store/authStore'
import { Badge } from '@/components/ui/badge'
import { toast } from 'sonner'
import { LocationTreeSelector } from '@/features/location/components/LocationTreeSelector'
import { userKeys, roleKeys } from '@/lib/api/queryKeys'

export function UsersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.permissions)
  const canCreate = permissions.includes('USER_CREATE')
  const canUpdate = permissions.includes('USER_UPDATE')
  const canDelete = permissions.includes('USER_DELETE')

  const [page, setPage] = useState(0)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [editingUser, setEditingUser] = useState<AppUserDto | null>(null)
  const [deleteUserId, setDeleteUserId] = useState<number | null>(null)
  const [unlockUserId, setUnlockUserId] = useState<number | null>(null)
  const [editOfficeLocationId, setEditOfficeLocationId] = useState<number | null>(null)
  const [editOfficeLocationName, setEditOfficeLocationName] = useState<string>('')
  const [officeSelectorOpen, setOfficeSelectorOpen] = useState(false)

  // Create form state
  const [email, setEmail] = useState('')
  const [selectedRole, setSelectedRole] = useState<string>('ROLE_TEACHER')
  const [initialPassword, setInitialPassword] = useState('')
  const [selectedDirectPerms, setSelectedDirectPerms] = useState<number[]>([])

  // Edit form direct perms
  const [editDirectPerms, setEditDirectPerms] = useState<number[]>([])

  const pageSize = 20

  const { data, isLoading } = useQuery({
    queryKey: userKeys.list({ page, pageSize }),
    queryFn: () => userApi.findAll({ page, size: pageSize }),
  })

  const { data: roles = [] } = useQuery({
    queryKey: roleKeys.all,
    queryFn: roleApi.findAll,
    enabled: canCreate || canUpdate,
  })

  const { data: allPermissions = [] } = useQuery<PermissionDto[]>({
    queryKey: roleKeys.permissions(),
    queryFn: roleApi.getPermissions,
    enabled: canCreate || canUpdate,
  })

  const createMutation = useMutation({
    mutationFn: userApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.all })
      setIsCreateOpen(false)
      setEmail('')
      setSelectedRole('ROLE_TEACHER')
      setInitialPassword('')
      setSelectedDirectPerms([])
      toast.success(t('common.created', 'Sikeresen létrehozva'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(
        t(`messages.${messageKey}`, {
          defaultValue: t(messageKey, {
            defaultValue: error.response?.data?.message || t('common.error'),
          }),
        })
      )
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: Parameters<typeof userApi.update>[1] }) =>
      userApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.all })
      setEditingUser(null)
      toast.success(t('common.updated', 'Sikeresen frissítve'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(
        t(`messages.${messageKey}`, {
          defaultValue: t(messageKey, {
            defaultValue: error.response?.data?.message || t('common.error'),
          }),
        })
      )
    },
  })

  const deleteMutation = useMutation({
    mutationFn: userApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.all })
      toast.success(t('common.deleted', 'Sikeresen törölve'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(
        t(`messages.${messageKey}`, {
          defaultValue: t(messageKey, {
            defaultValue: error.response?.data?.message || t('common.error'),
          }),
        })
      )
    },
  })

  const unlockMutation = useMutation({
    mutationFn: userApi.unlock,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.all })
      toast.success(t('users.unlockAccount', 'Fiók feloldva'))
    },
  })

  const openEdit = (user: AppUserDto) => {
    setEditingUser(user)
    setEditOfficeLocationId(user.officeLocation?.id ?? null)
    setEditOfficeLocationName(user.officeLocation?.name ?? '')
    setEditDirectPerms(user.directPermissions?.map((p) => p.id) ?? [])
  }

  const toggleCreatePerm = (permId: number) => {
    setSelectedDirectPerms((prev) =>
      prev.includes(permId) ? prev.filter((id) => id !== permId) : [...prev, permId]
    )
  }

  const toggleEditPerm = (permId: number) => {
    setEditDirectPerms((prev) =>
      prev.includes(permId) ? prev.filter((id) => id !== permId) : [...prev, permId]
    )
  }

  const columns = useMemo<ColumnDef<AppUserDto, unknown>[]>(
    () => [
      {
        id: 'id',
        accessorKey: 'id',
        header: 'ID',
        cell: (info) => <span className="font-mono text-xs">{String(info.getValue())}</span>,
      },
      {
        id: 'email',
        header: t('users.email'),
        cell: (info) => {
          const user = info.row.original
          return (
            <span className="font-mono text-xs">
              {user.email || user.emailMasked || user.emailHash}
            </span>
          )
        },
      },
      {
        id: 'role',
        header: t('users.role'),
        cell: (info) => {
          const user = info.row.original
          const roleName = user.role?.name ?? 'ROLE_USER'
          const directCount = user.directPermissions?.length ?? 0
          return (
            <div className="flex flex-wrap items-center gap-1.5">
              <Badge variant="outline" className="text-xs font-medium">
                {t(`roles.${roleName}`, roleName)}
              </Badge>
              {directCount > 0 && (
                <Badge
                  variant="secondary"
                  className="px-1.5 py-0 text-[10px]"
                  title={t('users.directPermissions', 'Közvetlen jogok')}
                >
                  +{directCount} jog
                </Badge>
              )}
            </div>
          )
        },
      },
      {
        id: 'officeLocation',
        header: t('users.office', 'Iroda'),
        cell: (info) => {
          const office = info.row.original.officeLocation ?? info.row.original.officeLocationSummary
          return office ? (
            <span className="text-xs">{office.name}</span>
          ) : (
            <span className="text-xs text-muted-foreground">—</span>
          )
        },
      },
      {
        id: 'active',
        accessorKey: 'active',
        header: t('users.active'),
        cell: (info) => {
          const active = info.getValue() as boolean
          return (
            <Badge variant={active ? 'default' : 'destructive'} className="text-xs">
              {active ? t('users.active') : t('users.inactive')}
            </Badge>
          )
        },
      },
      {
        id: 'actions',
        header: t('common.actions'),
        cell: (info) => {
          const user = info.row.original
          return (
            <div className="flex gap-1">
              <Button variant="ghost" size="icon" asChild title={t('users.viewDetails')}>
                <Link to={`/users/${user.id}`}>
                  <Eye className="h-4 w-4" />
                </Link>
              </Button>
              {canUpdate && (
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('common.edit')}
                  onClick={() => openEdit(user)}
                >
                  <Pencil className="h-4 w-4" />
                </Button>
              )}
              {canUpdate && (
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('users.unlockAccount')}
                  onClick={() => setUnlockUserId(user.id)}
                >
                  <Unlock className="h-4 w-4 text-blue-400" />
                </Button>
              )}
              {canDelete && (
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('common.delete')}
                  onClick={() => setDeleteUserId(user.id)}
                >
                  <Trash2 className="h-4 w-4 text-destructive" />
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [t, deleteMutation, unlockMutation, canUpdate, canDelete]
  )

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!email) return
    createMutation.mutate({
      email,
      role: selectedRole,
      initialPassword: initialPassword.trim() || undefined,
      directPermissionIds: selectedDirectPerms.length > 0 ? selectedDirectPerms : undefined,
    })
  }

  const handleEditSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!editingUser) return
    const payload: Parameters<typeof userApi.update>[1] = {}
    const newActive = (document.getElementById('edit-active') as HTMLInputElement)?.checked
    const roleSelect = document.getElementById('edit-role') as HTMLSelectElement
    if (roleSelect && roleSelect.value !== editingUser.role?.name) {
      payload.role = roleSelect.value
    }
    if (newActive !== editingUser.active) {
      payload.active = newActive
    }
    const currentOfficeId = editingUser.officeLocation?.id ?? null
    if (editOfficeLocationId !== currentOfficeId) {
      if (editOfficeLocationId === null) {
        payload.clearOfficeLocation = true
      } else {
        payload.officeLocationId = editOfficeLocationId
      }
    }
    payload.directPermissionIds = editDirectPerms

    updateMutation.mutate({ id: editingUser.id, payload })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('admin.users')}</h1>
        {canCreate && (
          <Button onClick={() => setIsCreateOpen(!isCreateOpen)}>
            <Plus className="mr-2 h-4 w-4" />
            {t('users.create')}
          </Button>
        )}
      </div>

      {isCreateOpen && (
        <div className="max-w-2xl space-y-4 rounded-lg border border-border bg-card p-6 shadow-lg">
          <h2 className="text-lg font-semibold">{t('users.create')}</h2>
          <form onSubmit={handleCreateSubmit} className="space-y-4">
            <div>
              <Label htmlFor="user-email" className="text-xs text-muted-foreground">
                {t('users.email')}
              </Label>
              <Input
                id="user-email"
                type="email"
                placeholder="oktato@tanszek.local"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="user-initial-password" className="text-xs text-muted-foreground">
                {t(
                  'users.initialPassword',
                  'Kezdeti jelszó (opcionális, alapértelmezett: ChangeMe123!)'
                )}
              </Label>
              <Input
                id="user-initial-password"
                type="text"
                placeholder="ChangeMe123!"
                value={initialPassword}
                onChange={(e) => setInitialPassword(e.target.value)}
              />
              <p className="mt-1 text-[11px] text-muted-foreground">
                {t(
                  'users.mustChangePasswordHelp',
                  'Az első bejelentkezéskor a felhasználónak kötelezően meg kell változtatnia ezt a jelszót.'
                )}
              </p>
            </div>
            <div>
              <Label htmlFor="user-role" className="text-xs text-muted-foreground">
                {t('users.role')}
              </Label>
              <select
                id="user-role"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={selectedRole}
                onChange={(e) => setSelectedRole(e.target.value)}
              >
                {roles.map((r) => (
                  <option key={r.id} value={r.name}>
                    {t(`roles.${r.name}`, r.name)}
                  </option>
                ))}
              </select>
            </div>

            {allPermissions.length > 0 && (
              <div className="space-y-2">
                <Label className="text-xs text-muted-foreground">
                  {t('users.directPermissions', 'Közvetlen jogosultságok (opcionális extra jogok)')}
                </Label>
                <div className="grid max-h-44 grid-cols-2 gap-2 overflow-y-auto rounded-md border border-border bg-muted/20 p-3">
                  {allPermissions.map((perm) => (
                    <label
                      key={perm.id}
                      className="flex cursor-pointer items-center gap-2 text-xs hover:text-foreground"
                    >
                      <input
                        type="checkbox"
                        checked={selectedDirectPerms.includes(perm.id)}
                        onChange={() => toggleCreatePerm(perm.id)}
                        className="rounded border-border"
                      />
                      <span className="text-[11px]">
                        {t(`permissions.${perm.name}`, perm.name)}
                      </span>
                    </label>
                  ))}
                </div>
              </div>
            )}

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
      />

      <ConfirmDialog
        open={deleteUserId !== null}
        onOpenChange={(open) => !open && setDeleteUserId(null)}
        description={t('users.confirmDelete')}
        onConfirm={() => {
          if (deleteUserId) {
            deleteMutation.mutate(deleteUserId)
            setDeleteUserId(null)
          }
        }}
      />

      <ConfirmDialog
        open={unlockUserId !== null}
        onOpenChange={(open) => !open && setUnlockUserId(null)}
        description={t('users.confirmUnlock', 'Biztosan feloldod a fiók zárolását?')}
        onConfirm={() => {
          if (unlockUserId) {
            unlockMutation.mutate(unlockUserId)
            setUnlockUserId(null)
          }
        }}
      />

      <Dialog
        open={editingUser !== null}
        onOpenChange={(open) => {
          if (!open) setEditingUser(null)
        }}
      >
        <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('users.edit')}</DialogTitle>
            <DialogDescription>
              {editingUser?.email || editingUser?.emailMasked || `#${editingUser?.id}`}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleEditSubmit} className="space-y-4 py-2">
            <div>
              <Label htmlFor="edit-role" className="text-xs text-muted-foreground">
                {t('users.role')}
              </Label>
              <select
                id="edit-role"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                defaultValue={editingUser?.role?.name ?? 'ROLE_TEACHER'}
              >
                {roles.map((r) => (
                  <option key={r.id} value={r.name}>
                    {t(`roles.${r.name}`, r.name)}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label className="text-xs text-muted-foreground">
                {t('users.office', 'Irodai helyszín')}
              </Label>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  className="flex-1 justify-start"
                  onClick={() => setOfficeSelectorOpen(true)}
                >
                  <MapPin className="mr-2 h-4 w-4 text-muted-foreground" />
                  {editOfficeLocationName ? (
                    <span className="text-xs font-medium text-foreground">
                      {editOfficeLocationName}
                    </span>
                  ) : editOfficeLocationId != null ? (
                    <span className="text-xs font-medium text-foreground">
                      Helyszín #{editOfficeLocationId}
                    </span>
                  ) : (
                    <span className="text-muted-foreground">
                      {t('locations.noParent', 'Nincs iroda beállítva')}
                    </span>
                  )}
                </Button>
                {editOfficeLocationId != null && (
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => {
                      setEditOfficeLocationId(null)
                      setEditOfficeLocationName('')
                    }}
                  >
                    ×
                  </Button>
                )}
              </div>
            </div>

            {allPermissions.length > 0 && (
              <div className="space-y-2">
                <Label className="text-xs text-muted-foreground">
                  {t('users.directPermissions', 'Közvetlen jogosultságok')}
                </Label>
                <div className="grid max-h-44 grid-cols-2 gap-2 overflow-y-auto rounded-md border border-border bg-muted/20 p-3">
                  {allPermissions.map((perm) => (
                    <label
                      key={perm.id}
                      className="flex cursor-pointer items-center gap-2 text-xs hover:text-foreground"
                    >
                      <input
                        type="checkbox"
                        checked={editDirectPerms.includes(perm.id)}
                        onChange={() => toggleEditPerm(perm.id)}
                        className="rounded border-border"
                      />
                      <span className="text-[11px]">
                        {t(`permissions.${perm.name}`, perm.name)}
                      </span>
                    </label>
                  ))}
                </div>
              </div>
            )}

            <div>
              <Label
                htmlFor="edit-active"
                className="flex cursor-pointer items-center gap-2 text-xs"
              >
                <input
                  id="edit-active"
                  type="checkbox"
                  className="h-4 w-4 rounded border-border"
                  defaultChecked={editingUser?.active ?? true}
                />
                {t('users.active')}
              </Label>
              <p className="mt-1 text-xs text-muted-foreground">{t('users.activeChangeWarning')}</p>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditingUser(null)}
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
      <LocationTreeSelector
        open={officeSelectorOpen}
        onOpenChange={setOfficeSelectorOpen}
        onSelect={(id, node) => {
          setEditOfficeLocationId(id)
          setEditOfficeLocationName(node?.name ?? '')
        }}
        selectedId={editOfficeLocationId}
        excludeGroupType
      />
    </div>
  )
}
