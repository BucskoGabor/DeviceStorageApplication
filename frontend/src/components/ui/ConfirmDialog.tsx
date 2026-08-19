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

interface ConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title?: string
  description?: string
  confirmText?: string
  cancelText?: string
  variant?: 'default' | 'destructive'
  onConfirm: () => void | Promise<void>
  loading?: boolean
}

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmText,
  cancelText,
  variant = 'destructive',
  onConfirm,
  loading = false,
}: ConfirmDialogProps) {
  const { t } = useTranslation()
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleConfirm = async () => {
    // Megvárjuk az async onConfirm befejezését, hogy a dialog csak a
    // kérés (pl. delete mutation) tényleges completion-je után záródjon be.
    // Így a user kap visszajelzést a hibáról, és nincs dupla kattintás.
    setIsSubmitting(true)
    try {
      await onConfirm()
      onOpenChange(false)
    } catch (error) {
      // Hiba esetén a dialog marad nyitva, hogy a hibaüzenet látható legyen;
      // a hívó komponens felelős a toast / inline error megjelenítéséért.
      console.error('ConfirmDialog onConfirm failed', error)
    } finally {
      setIsSubmitting(false)
    }
  }

  const isBusy = loading || isSubmitting

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title || t('common.confirm', 'Megerősítés')}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isBusy}>
            {cancelText || t('common.cancel', 'Mégse')}
          </Button>
          <Button variant={variant} onClick={handleConfirm} disabled={isBusy}>
            {confirmText || t('common.confirm', 'Megerősítés')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
