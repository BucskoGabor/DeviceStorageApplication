import { useState, useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Shield, CheckSquare, Square } from 'lucide-react'
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
import { type RoleDto, type PermissionDto } from '@/features/role/api/roleApi'

interface RoleDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  role: RoleDto | null
  allPermissions: PermissionDto[]
  onSubmit: (name: string, permissionIds: number[]) => void
  isLoading?: boolean
}

interface PermissionGroup {
  nameKey: string
  icon: string
  permissions: PermissionDto[]
}

const SYSTEM_ROLES = ['ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT']

export function RoleDialog({
  open,
  onOpenChange,
  role,
  allPermissions,
  onSubmit,
  isLoading = false,
}: RoleDialogProps) {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [selectedPermissionIds, setSelectedPermissionIds] = useState<number[]>([])

  useEffect(() => {
    if (role) {
      setName(role.name)
      setSelectedPermissionIds(role.permissions.map((p) => p.id))
    } else {
      setName('')
      setSelectedPermissionIds([])
    }
  }, [role, open])

  const isSystemRole = role ? SYSTEM_ROLES.includes(role.name) : false

  const permissionGroups = useMemo<PermissionGroup[]>(() => {
    const getPrefix = (permName: string) => {
      if (permName.startsWith('DEVICE_')) return { group: 'roles.groupDevice', icon: '📱' }
      if (permName.startsWith('USER_')) return { group: 'roles.groupUser', icon: '👥' }
      if (permName.startsWith('LOCATION_')) return { group: 'roles.groupLocation', icon: '🏢' }
      if (permName.startsWith('AUDIT_')) return { group: 'roles.groupAudit', icon: '🛡️' }
      if (permName.startsWith('SOFTWARE_')) return { group: 'roles.groupSoftware', icon: '💻' }
      return { group: 'roles.groupOther', icon: '⚙️' }
    }

    const groupsMap = new Map<string, { nameKey: string; icon: string; permissions: PermissionDto[] }>()

    allPermissions.forEach((perm) => {
      const { group, icon } = getPrefix(perm.name)
      if (!groupsMap.has(group)) {
        groupsMap.set(group, { nameKey: group, icon, permissions: [] })
      }
      groupsMap.get(group)!.permissions.push(perm)
    })

    return Array.from(groupsMap.values())
  }, [allPermissions])

  const togglePermission = (id: number) => {
    setSelectedPermissionIds((prev) =>
      prev.includes(id) ? prev.filter((pId) => pId !== id) : [...prev, id]
    )
  }

  const toggleGroup = (groupPermissions: PermissionDto[]) => {
    const groupIds = groupPermissions.map((p) => p.id)
    const allSelected = groupIds.every((id) => selectedPermissionIds.includes(id))

    if (allSelected) {
      setSelectedPermissionIds((prev) => prev.filter((id) => !groupIds.includes(id)))
    } else {
      setSelectedPermissionIds((prev) => Array.from(new Set([...prev, ...groupIds])))
    }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSubmit(name, selectedPermissionIds)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Shield className="h-5 w-5 text-primary" />
            {role ? t('roles.editRole', 'Szerepkör módosítása') : t('roles.createRole', 'Új szerepkör')}
          </DialogTitle>
          <DialogDescription>
            {t('roles.dialogDescription', 'Adja meg a szerepkör nevét és állítsa be a jogosultságokat.')}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-6 py-2">
          <div className="space-y-2">
            <Label htmlFor="role-name">{t('roles.roleName', 'Szerepkör neve')}</Label>
            <Input
              id="role-name"
              placeholder="ROLE_MANAGER"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isSystemRole}
              required
            />
            {isSystemRole && (
              <p className="text-xs text-muted-foreground">
                {t('roles.systemRoleNameNotice', 'Rendszer szerepkör neve nem módosítható.')}
              </p>
            )}
          </div>

          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <Label className="text-sm font-semibold">
                {t('roles.permissions', 'Jogosultságok')} ({selectedPermissionIds.length} / {allPermissions.length})
              </Label>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setSelectedPermissionIds(allPermissions.map((p) => p.id))}
                >
                  <CheckSquare className="mr-1 h-3.5 w-3.5" />
                  {t('roles.selectAll', 'Összes kijelölése')}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setSelectedPermissionIds([])}
                >
                  <Square className="mr-1 h-3.5 w-3.5" />
                  {t('roles.deselectAll', 'Kijelölések törlése')}
                </Button>
              </div>
            </div>

            <div className="grid gap-4">
              {permissionGroups.map((group) => {
                const groupIds = group.permissions.map((p) => p.id)
                const allSelected = groupIds.every((id) => selectedPermissionIds.includes(id))

                return (
                  <div key={group.nameKey} className="rounded-lg border p-3 space-y-3 bg-card">
                    <div className="flex items-center justify-between border-b pb-2">
                      <span className="font-medium text-xs flex items-center gap-1.5">
                        <span>{group.icon}</span>
                        {t(group.nameKey, group.nameKey)}
                      </span>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="h-7 text-xs"
                        onClick={() => toggleGroup(group.permissions)}
                      >
                        {allSelected
                          ? t('roles.deselectGroup', 'Kijelölés megszüntetése')
                          : t('roles.selectGroup', 'Csoport kijelölése')}
                      </Button>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      {group.permissions.map((perm) => {
                        const isChecked = selectedPermissionIds.includes(perm.id)
                        return (
                          <label
                            key={perm.id}
                            className={`flex items-center space-x-2.5 rounded-md p-2 text-xs border transition-colors cursor-pointer ${
                              isChecked
                                ? 'bg-primary/10 border-primary/40 font-medium'
                                : 'hover:bg-accent border-transparent'
                            }`}
                          >
                            <input
                              type="checkbox"
                              className="h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary accent-primary"
                              checked={isChecked}
                              onChange={() => togglePermission(perm.id)}
                            />
                            <span className="font-mono">{perm.name}</span>
                          </label>
                        )
                      })}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('common.cancel', 'Mégse')}
            </Button>
            <Button type="submit" disabled={isLoading || (!name.trim() && !isSystemRole)}>
              {isLoading ? t('common.saving', 'Mentés...') : t('common.save', 'Mentés')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
