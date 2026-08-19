import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { userApi } from '@/features/user/api/userApi'
import { userKeys } from '@/lib/api/queryKeys'
import { useRequestAssignment } from '../hooks/useAssignments'
import { LocationTreeSelector } from '@/features/location/components/LocationTreeSelector'

interface AssignmentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  deviceId: number
}

const PAGE_SIZE = 50

/**
 * AssignmentDialog — Eszköz hozzárendelési kérés indítása.
 *
 * Szabály: Az eszköz VAGY Helyszínhez (Location), VAGY Felhasználóhoz (User) rendelhető hozzá,
 * egyszerre mindkettő vagy egyik sem tilos.
 *
 * <p>A user lista a backend PAGINATION_MAX_SIZE=50 korlátja miatt csak az első 50
 * aktív user-t tölti be. Ezt a korlátot a user kereső inputtal hidaljuk át: a
 * user begépelheti a keresett user email-jét / szerepkörét, és a kliens oldalon
 * szűrjük a listát.
 */
export function AssignmentDialog({ open, onOpenChange, deviceId }: AssignmentDialogProps) {
  const { t } = useTranslation()
  const [targetType, setTargetType] = useState<'location' | 'user'>('location')
  const [targetLocationId, setTargetLocationId] = useState<number | null>(null)
  const [targetLocationName, setTargetLocationName] = useState<string>('')
  const [targetUserId, setTargetUserId] = useState<string>('__none__')
  const [locationSelectorOpen, setLocationSelectorOpen] = useState(false)
  const [userSearch, setUserSearch] = useState<string>('')

  const { data: usersPage } = useQuery({
    queryKey: userKeys.list({ page: 0, size: PAGE_SIZE }),
    queryFn: () => userApi.findAll({ page: 0, size: PAGE_SIZE }),
    enabled: open,
  })

  const activeUsers = useMemo(() => (usersPage?.content ?? []).filter((u) => u.active), [usersPage])

  const filteredUsers = useMemo(() => {
    const q = userSearch.trim().toLowerCase()
    if (!q) return activeUsers
    return activeUsers.filter((u) => {
      const email = (u.email || u.emailMasked || '').toLowerCase()
      const role = (u.role?.name || '').toLowerCase()
      return email.includes(q) || role.includes(q)
    })
  }, [activeUsers, userSearch])

  const requestMutation = useRequestAssignment(deviceId, () => {
    setTargetLocationId(null)
    setTargetLocationName('')
    setTargetUserId('__none__')
    setUserSearch('')
    onOpenChange(false)
  })

  const handleSubmit = () => {
    if (targetType === 'location') {
      if (targetLocationId == null) return
      requestMutation.mutate({ targetLocationId })
    } else {
      if (!targetUserId || targetUserId === '__none__') return
      requestMutation.mutate({ targetUserId: Number(targetUserId) })
    }
  }

  const isValid =
    (targetType === 'location' && targetLocationId != null) ||
    (targetType === 'user' && targetUserId !== '__none__')

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('assignments.requestAssignment')}</DialogTitle>
            <DialogDescription>
              {t('devices.assignTo')} #{deviceId}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            {/* Target Type Selector (Location vs User toggle) */}
            <div className="flex rounded-md bg-muted p-1 text-muted-foreground">
              <button
                type="button"
                className={`flex-1 rounded-sm px-3 py-1.5 text-xs font-medium transition-all ${
                  targetType === 'location'
                    ? 'bg-background text-foreground shadow-sm'
                    : 'hover:text-foreground'
                }`}
                onClick={() => {
                  setTargetType('location')
                  setTargetUserId('__none__')
                }}
              >
                {t('devices.location', 'Helyszín')}
              </button>
              <button
                type="button"
                className={`flex-1 rounded-sm px-3 py-1.5 text-xs font-medium transition-all ${
                  targetType === 'user'
                    ? 'bg-background text-foreground shadow-sm'
                    : 'hover:text-foreground'
                }`}
                onClick={() => {
                  setTargetType('user')
                  setTargetLocationId(null)
                  setTargetLocationName('')
                }}
              >
                {t('devices.user', 'Felhasználó')}
              </button>
            </div>

            {targetType === 'location' ? (
              <div className="space-y-2">
                <Label>{t('devices.selectLocation')}</Label>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    className="flex-1 justify-start"
                    onClick={() => setLocationSelectorOpen(true)}
                  >
                    {targetLocationName ? (
                      <span className="text-xs font-medium">{targetLocationName}</span>
                    ) : (
                      <span className="text-muted-foreground">{t('devices.selectLocation')}</span>
                    )}
                  </Button>
                  {targetLocationId != null && (
                    <Button
                      type="button"
                      variant="ghost"
                      onClick={() => {
                        setTargetLocationId(null)
                        setTargetLocationName('')
                      }}
                    >
                      ×
                    </Button>
                  )}
                </div>
              </div>
            ) : (
              <div className="space-y-2">
                <Label htmlFor="target-user-search">{t('devices.selectUser')}</Label>
                <Input
                  id="target-user-search"
                  type="search"
                  placeholder={t('users.searchByEmail', 'Keresés email vagy szerepkör szerint…')}
                  value={userSearch}
                  onChange={(e) => setUserSearch(e.target.value)}
                  className="mb-2"
                />
                <Select value={targetUserId} onValueChange={setTargetUserId}>
                  <SelectTrigger id="target-user">
                    <SelectValue
                      placeholder={
                        filteredUsers.length === 0
                          ? t('users.noMatch', 'Nincs találat')
                          : t('devices.selectUser')
                      }
                    />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none__">—</SelectItem>
                    {filteredUsers.map((u) => (
                      <SelectItem key={u.id} value={String(u.id)}>
                        {u.email || u.emailMasked || `#${u.id}`} ({u.role?.name ?? '—'})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {activeUsers.length >= PAGE_SIZE && (
                  <p className="text-xs text-muted-foreground">
                    {t('users.showingFirstN', {
                      count: PAGE_SIZE,
                      defaultValue: `Az első ${PAGE_SIZE} aktív user jelenik meg. Használd a keresőt a többi megtalálásához.`,
                    })}
                  </p>
                )}
              </div>
            )}
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={requestMutation.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSubmit} disabled={!isValid || requestMutation.isPending}>
              {t('assignments.requestAssignment')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <LocationTreeSelector
        open={locationSelectorOpen}
        onOpenChange={setLocationSelectorOpen}
        selectedId={targetLocationId}
        excludeGroupType
        onSelect={(id, node) => {
          setTargetLocationId(id)
          setTargetLocationName(node?.name ?? '')
        }}
      />
    </>
  )
}
