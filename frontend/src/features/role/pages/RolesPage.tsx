import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Shield, Plus, Pencil, Trash2, Lock, CheckCircle2 } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { roleApi, type RoleDto } from '@/features/role/api/roleApi'
import { RoleDialog } from '@/features/role/components/RoleDialog'
import { useAuthStore } from '@/lib/store/authStore'
import { toast } from 'sonner'

const SYSTEM_ROLES = ['ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT']

export function RolesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.permissions)
  const canManage = permissions.includes('USER_MANAGE')

  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [editingRole, setEditingRole] = useState<RoleDto | null>(null)
  const [deletingRoleId, setDeletingRoleId] = useState<number | null>(null)

  const { data: roles = [], isLoading: isLoadingRoles } = useQuery({
    queryKey: ['roles'],
    queryFn: roleApi.findAll,
  })

  const { data: allPermissions = [], isLoading: isLoadingPerms } = useQuery({
    queryKey: ['permissions'],
    queryFn: roleApi.getPermissions,
  })

  const createMutation = useMutation({
    mutationFn: (payload: { name: string; permissionIds: number[] }) =>
      roleApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      setIsCreateOpen(false)
      toast.success(t('roles.createdSuccess', 'Szerepkör sikeresen létrehozva'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: { name?: string; permissionIds?: number[] } }) =>
      roleApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      setEditingRole(null)
      toast.success(t('roles.updatedSuccess', 'Szerepkör sikeresen frissítve'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: roleApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      setDeletingRoleId(null)
      toast.success(t('roles.deletedSuccess', 'Szerepkör sikeresen törölve'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })

  const handleSaveRole = (name: string, permissionIds: number[]) => {
    if (editingRole) {
      updateMutation.mutate({ id: editingRole.id, payload: { name, permissionIds } })
    } else {
      createMutation.mutate({ name, permissionIds })
    }
  }

  if (isLoadingRoles || isLoadingPerms) {
    return <div className="p-4 text-muted-foreground">{t('common.loading', 'Betöltés...')}</div>
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight flex items-center gap-2">
            <Shield className="h-6 w-6 text-primary" />
            {t('roles.title', 'Szerepkörök és Jogosultságok')}
          </h1>
          <p className="text-sm text-muted-foreground">
            {t('roles.subtitle', 'Rendszerbeli munkakörök és granularis hozzáférési jogosultságok kezelése.')}
          </p>
        </div>

        {canManage && (
          <Button onClick={() => setIsCreateOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            {t('roles.newRole', 'Új szerepkör')}
          </Button>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {roles.map((role) => {
          const isSystem = SYSTEM_ROLES.includes(role.name)

          return (
            <Card key={role.id} className="flex flex-col justify-between">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-lg flex items-center gap-2">
                    {role.name}
                    {isSystem && (
                      <Badge variant="secondary" className="text-[10px] font-normal flex items-center gap-1">
                        <Lock className="h-3 w-3" />
                        {t('roles.systemRole', 'Rendszer')}
                      </Badge>
                    )}
                  </CardTitle>

                  {canManage && (
                    <div className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        title={t('common.edit', 'Szerkesztés')}
                        onClick={() => setEditingRole(role)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      {!isSystem && (
                        <Button
                          variant="ghost"
                          size="icon"
                          className="text-destructive hover:text-destructive"
                          title={t('common.delete', 'Törlés')}
                          onClick={() => setDeletingRoleId(role.id)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                    </div>
                  )}
                </div>
                <CardDescription>
                  {t('roles.assignedPermissions', 'Hozzárendelt jogosultságok')}: {role.permissions.length} / {allPermissions.length}
                </CardDescription>
              </CardHeader>

              <CardContent className="space-y-3 pt-0 flex-1">
                <div className="flex flex-wrap gap-1.5 max-h-36 overflow-y-auto p-1">
                  {role.permissions.length > 0 ? (
                    role.permissions.map((perm) => (
                      <Badge key={perm.id} variant="outline" className="text-[11px] font-mono">
                        <CheckCircle2 className="mr-1 h-3 w-3 text-emerald-500" />
                        {perm.name}
                      </Badge>
                    ))
                  ) : (
                    <span className="text-xs text-muted-foreground italic">
                      {t('roles.noPermissions', 'Nincsenek hozzárendelt jogosultságok')}
                    </span>
                  )}
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>

      <RoleDialog
        open={isCreateOpen || editingRole !== null}
        onOpenChange={(open) => {
          if (!open) {
            setIsCreateOpen(false)
            setEditingRole(null)
          }
        }}
        role={editingRole}
        allPermissions={allPermissions}
        onSubmit={handleSaveRole}
        isLoading={createMutation.isPending || updateMutation.isPending}
      />

      <Dialog open={deletingRoleId !== null} onOpenChange={() => setDeletingRoleId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('roles.deleteConfirmTitle', 'Szerepkör törlése')}</DialogTitle>
            <DialogDescription>
              {t(
                'roles.deleteConfirmDesc',
                'Biztosan törölni szeretné ezt a szerepkört? A művelet nem vonható vissza.'
              )}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeletingRoleId(null)}>
              {t('common.cancel', 'Mégse')}
            </Button>
            <Button
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={() => deletingRoleId && deleteMutation.mutate(deletingRoleId)}
            >
              {deleteMutation.isPending ? t('common.deleting', 'Törlés...') : t('common.delete', 'Törlés')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
