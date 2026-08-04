import { useState } from 'react'
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
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { userApi } from '@/features/user/api/userApi'
import { useRequestAssignment } from '../hooks/useAssignments'
import { LocationTreeSelector } from '@/features/location/components/LocationTreeSelector'

interface AssignmentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  deviceId: number
}

/**
 * AssignmentDialog — Eszköz hozzárendelési kérés indítása.
 *
 * Szabály: Az eszköz VAGY Helyszínhez (Location), VAGY Felhasználóhoz (User) rendelhető hozzá,
 * egyszerre mindkettő vagy egyik sem tilos.
 */
export function AssignmentDialog({ open, onOpenChange, deviceId }: AssignmentDialogProps) {
  const { t } = useTranslation()
  const [targetType, setTargetType] = useState<'location' | 'user'>('location')
  const [targetLocationId, setTargetLocationId] = useState<number | null>(null)
  const [targetUserId, setTargetUserId] = useState<string>('__none__')
  const [locationSelectorOpen, setLocationSelectorOpen] = useState(false)

  const { data: usersPage } = useQuery({
    queryKey: ['users', 'all'],
    queryFn: () => userApi.findAll({ page: 0, size: 50 }),
    enabled: open,
  })

  const requestMutation = useRequestAssignment(deviceId, () => {
    setTargetLocationId(null)
    setTargetUserId('__none__')
    onOpenChange(false)
  })

  const activeUsers = usersPage?.content.filter((u) => u.active) ?? []

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
                    {targetLocationId != null
                      ? `#${targetLocationId}`
                      : t('devices.selectLocation')}
                  </Button>
                  {targetLocationId != null && (
                    <Button
                      type="button"
                      variant="ghost"
                      onClick={() => setTargetLocationId(null)}
                    >
                      ×
                    </Button>
                  )}
                </div>
              </div>
            ) : (
              <div className="space-y-2">
                <Label htmlFor="target-user">{t('devices.selectUser')}</Label>
                <Select value={targetUserId} onValueChange={setTargetUserId}>
                  <SelectTrigger id="target-user">
                    <SelectValue placeholder={t('devices.selectUser')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none__">—</SelectItem>
                    {activeUsers.map((u) => (
                      <SelectItem key={u.id} value={String(u.id)}>
                        {u.emailHash?.substring(0, 8) ?? `#${u.id}`} ({u.role?.name ?? '—'})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
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
            <Button
              onClick={handleSubmit}
              disabled={!isValid || requestMutation.isPending}
            >
              {t('assignments.requestAssignment')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <LocationTreeSelector
        open={locationSelectorOpen}
        onOpenChange={setLocationSelectorOpen}
        onSelect={(id) => setTargetLocationId(id)}
        selectedId={targetLocationId}
        excludeGroupType
      />
    </>
  )
}
