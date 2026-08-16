import { useState } from 'react'
import { useTranslation } from 'react-i18next'
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
import { useRequestUnassignment } from '../hooks/useAssignments'
import { LocationTreeSelector } from '@/features/location/components/LocationTreeSelector'
import { MapPin } from 'lucide-react'

interface UnassignDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  deviceId: number
  assignmentId: number
}

/**
  UnassignDialog — Eszköz visszavétel/levétel kérés indítása Dialog-ban.
  A felhasználó számára KÖTELEZŐ egy RAKTÁR típusú célhelyszínt kiválasztani.
 */
export function UnassignDialog({
  open,
  onOpenChange,
  deviceId,
  assignmentId,
}: UnassignDialogProps) {
  const { t } = useTranslation()
  const [targetLocationId, setTargetLocationId] = useState<number | null>(null)
  const [targetLocationName, setTargetLocationName] = useState<string>('')
  const [locationSelectorOpen, setLocationSelectorOpen] = useState(false)

  const requestUnassignMutation = useRequestUnassignment(deviceId)

  const handleSubmit = () => {
    if (targetLocationId === null) return
    requestUnassignMutation.mutate(
      { assignmentId, targetLocationId },
      {
        onSuccess: () => {
          setTargetLocationId(null)
          setTargetLocationName('')
          onOpenChange(false)
        },
      }
    )
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t('assignments.unassignDialogTitle', 'Eszköz visszavétele (levétel)')}
            </DialogTitle>
            <DialogDescription>
              {t(
                'assignments.unassignDialogDescription',
                'Válaszd ki a raktárt, ahová az eszköz visszakerül.'
              )}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>
                {t('assignments.targetStorageLocation', 'Cél raktárhelyszín (kötelező)')}
              </Label>
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  className="w-full justify-start text-left font-normal"
                  onClick={() => setLocationSelectorOpen(true)}
                >
                  <MapPin className="mr-2 h-4 w-4 text-muted-foreground" />
                  {targetLocationName ? (
                    <span className="text-xs font-medium text-foreground">
                      {targetLocationName}
                    </span>
                  ) : (
                    <span className="text-muted-foreground">
                      {t('assignments.selectStorageLocation', 'Válassz ki egy raktárat...')}
                    </span>
                  )}
                </Button>
              </div>
              {targetLocationId === null && (
                <p className="text-xs text-destructive">
                  {t(
                    'assignments.storageLocationRequired',
                    'A raktárhelyszín kiválasztása kötelező.'
                  )}
                </p>
              )}
            </div>
          </div>

          <DialogFooter>
            <Button
              variant="ghost"
              onClick={() => onOpenChange(false)}
              disabled={requestUnassignMutation.isPending}
            >
              {t('common.cancel', 'Mégse')}
            </Button>
            <Button
              variant="destructive"
              onClick={handleSubmit}
              disabled={targetLocationId === null || requestUnassignMutation.isPending}
            >
              {t('devices.unassign', 'Hozzárendelés visszavonása')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <LocationTreeSelector
        open={locationSelectorOpen}
        onOpenChange={setLocationSelectorOpen}
        onSelect={(id, node) => {
          setTargetLocationId(id)
          setTargetLocationName(node?.name ?? '')
        }}
        selectedId={targetLocationId}
        onlyStorageType={true}
      />
    </>
  )
}
