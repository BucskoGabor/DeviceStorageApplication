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
 * A user a {@link LocationTreeSelector}-ral választja ki a cél location-t
 * (kizárva GROUP típusú helyeket — azokra nem lehet eszközt rendelni).
 * Opcionálisan a cél felhasználót is kiválaszthatja.
 *
 * A kérés PENDING_ASSIGNMENT státusszal jön létre a backend-en.
 * Egy admin/teachernek kell jóváhagynia az Approval Queue-n.
 */
export function AssignmentDialog({ open, onOpenChange, deviceId }: AssignmentDialogProps) {
  const { t } = useTranslation()
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
    if (targetLocationId == null) return
    const payload: { targetLocationId: number; targetUserId?: number | null } = {
      targetLocationId: targetLocationId,
    }
    if (targetUserId && targetUserId !== '__none__') {
      payload.targetUserId = Number(targetUserId)
    }
    requestMutation.mutate(payload)
  }

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
              disabled={targetLocationId == null || requestMutation.isPending}
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
