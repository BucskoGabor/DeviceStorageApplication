import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { type ColumnDef } from '@tanstack/react-table'
import { Plus, Trash2, Unlock, Pencil, FileText } from 'lucide-react'
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
import { userApi, type AppUserDto, type RoleName } from '@/features/user/api/userApi'
import { useAuthStore } from '@/lib/store/authStore'
import { Badge } from '@/components/ui/badge'
import { toast } from 'sonner'

const ROLE_OPTIONS: RoleName[] = ['ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT']

export function UsersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.permissions)
  const canManage = permissions.includes('USER_MANAGE')

  const [page, setPage] = useState(0)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [editingUser, setEditingUser] = useState<AppUserDto | null>(null)
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<RoleName>('ROLE_TEACHER')

  const pageSize = 20

  const { data, isLoading } = useQuery({
    queryKey: ['users', page, pageSize],
    queryFn: () => userApi.findAll({ page, size: pageSize }),
  })

  const createMutation = useMutation({
    mutationFn: userApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setIsCreateOpen(false)
      setEmail('')
      toast.success(t('common.created', 'Sikeresen létrehozva'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: Parameters<typeof userApi.update>[1] }) =>
      userApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setEditingUser(null)
      toast.success(t('common.updated', 'Sikeresen frissítve'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: userApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      toast.success(t('common.deleted', 'Sikeresen törölve'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  const unlockMutation = useMutation({
    mutationFn: userApi.unlock,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      toast.success(t('users.unlockAccount'))
    },
  })

  const columns = useMemo<ColumnDef<AppUserDto, unknown>[]>(
    () => [
      {
        id: 'id',
        accessorKey: 'id',
        header: 'ID',
        cell: (info) => <span className="font-mono text-xs">{String(info.getValue())}</span>,
      },
      {
        id: 'emailMasked',
        accessorKey: 'emailMasked',
        header: t('users.email'),
        cell: (info) => (
          <span className="font-mono text-xs">{String(info.getValue())}</span>
        ),
      },
      {
        id: 'role',
        header: t('users.role'),
        cell: (info) => {
          const roleName = info.row.original.role?.name ?? 'ROLE_USER'
          return (
            <Badge variant="outline" className="font-medium">
              {t(`roles.${roleName}`, roleName)}
            </Badge>
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
                <Link to={`/admin/users/${user.id}`}>
                  <FileText className="h-4 w-4" />
                </Link>
              </Button>
              {canManage && (
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('common.edit')}
                  onClick={() => setEditingUser(user)}
                >
                  <Pencil className="h-4 w-4" />
                </Button>
              )}
              {canManage && (
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('users.unlockAccount')}
                  onClick={() => unlockMutation.mutate(user.id)}
                >
                  <Unlock className="h-4 w-4 text-blue-400" />
                </Button>
              )}
              {canManage && (
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('common.delete')}
                  onClick={() => {
                    if (confirm(t('users.confirmDelete'))) {
                      deleteMutation.mutate(user.id)
                    }
                  }}
                >
                  <Trash2 className="h-4 w-4 text-destructive" />
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [t, deleteMutation, unlockMutation, canManage]
  )

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!email) return
    createMutation.mutate({ email, role })
  }

  const handleEditSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!editingUser) return
    const payload: Parameters<typeof userApi.update>[1] = {}
    const newActive = (document.getElementById('edit-active') as HTMLInputElement)?.checked

    const roleSelect = (document.getElementById('edit-role') as HTMLSelectElement)
    if (roleSelect && roleSelect.value !== editingUser.role?.name) {
      payload.role = roleSelect.value as RoleName
    }
    if (newActive !== editingUser.active) {
      payload.active = newActive
    }

    if (Object.keys(payload).length === 0) {
      setEditingUser(null)
      return
    }
    updateMutation.mutate({ id: editingUser.id, payload })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('admin.users')}</h1>
        {canManage && (
          <Button onClick={() => setIsCreateOpen(!isCreateOpen)}>
            <Plus className="mr-2 h-4 w-4" />
            {t('users.create')}
          </Button>
        )}
      </div>

      {isCreateOpen && (
        <div className="rounded-lg border border-border bg-card p-6 shadow-lg space-y-4 max-w-lg">
          <h2 className="text-lg font-semibold">{t('users.create')}</h2>
          <form onSubmit={handleCreateSubmit} className="space-y-3">
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
              <Label htmlFor="user-role" className="text-xs text-muted-foreground">
                {t('users.role')}
              </Label>
              <select
                id="user-role"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                value={role}
                onChange={(e: any) => setRole(e.target.value)}
              >
                {ROLE_OPTIONS.map((r) => (
                  <option key={r} value={r}>
                    {t(`roles.${r}`, r)}
                  </option>
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
      />

      <Dialog
        open={editingUser !== null}
        onOpenChange={(open) => {
          if (!open) setEditingUser(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('users.edit')}</DialogTitle>
            <DialogDescription>
              #{editingUser?.id} ({editingUser?.emailHash.slice(0, 12)}…)
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleEditSubmit} className="space-y-3 py-4">
            <div>
              <Label htmlFor="edit-role" className="text-xs text-muted-foreground">
                {t('users.role')}
              </Label>
              <select
                id="edit-role"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
                defaultValue={editingUser?.role?.name ?? 'ROLE_TEACHER'}
              >
                {ROLE_OPTIONS.map((r) => (
                  <option key={r} value={r}>
                    {t(`roles.${r}`, r)}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="edit-active" className="flex items-center gap-2 text-xs">
                <input
                  id="edit-active"
                  type="checkbox"
                  className="h-4 w-4"
                  defaultChecked={editingUser?.active ?? true}
                />
                {t('users.active')}
              </Label>
              <p className="text-xs text-muted-foreground">
                {t('users.activeChangeWarning')}
              </p>
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
    </div>
  )
}
