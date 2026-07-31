import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
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
import { useAuthStore } from '@/lib/store/authStore'
import { authApi } from '@/features/auth/api/authApi'
import {
  passwordChangeSchema,
  type PasswordChangeFormData,
} from '@/features/auth/schemas/passwordChangeSchema'

/**
 * PasswordChangeForm — inline modal first-login jelszócseréhez.
 *
 * A Dashboard-en jelenik meg, ha a useAuthStore.mustChangePassword=true.
 * A user nem tudja bezárni a modalt a Cancel gomb nélkül (csak a kényszerített
 * csere után). Sikeres csere után a mustChangePassword=false-ra vált,
 * a modal automatikusan bezáródik.
 */
interface PasswordChangeFormProps {
  open: boolean
  onSuccess?: () => void
  /**
   * Ha true, a user bezárhatja a dialog-ot a sikeres csere előtt
   * (pl. saját profil oldal). Ha false (default), a dialog csak
   * sikeres csere után záródik be (first-login flow).
   */
  closable?: boolean
}

export function PasswordChangeForm({ open, onSuccess, closable = false }: PasswordChangeFormProps) {
  const { t } = useTranslation()
  const setMustChangePassword = useAuthStore((state) => state.setMustChangePassword)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<PasswordChangeFormData>({
    resolver: zodResolver(passwordChangeSchema),
    defaultValues: {
      currentPassword: '',
      newPassword: '',
      confirmNewPassword: '',
    },
  })

  const onSubmit = async (data: PasswordChangeFormData) => {
    setIsSubmitting(true)
    try {
      await authApi.changePassword({
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      })
      setMustChangePassword(false)
      toast.success(t('passwordChange.success'), { position: 'top-right' })
      reset()
      onSuccess?.()
    } catch (error: any) {
      const messageKey = error.response?.data?.messageKey ?? 'error'
      toast.error(t(messageKey), { position: 'top-right' })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={() => {
        if (closable) onSuccess?.()
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('login.mustChangePasswordTitle')}</DialogTitle>
          <DialogDescription>{t('login.mustChangePasswordMessage')}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="currentPassword">{t('passwordChange.currentPassword')}</Label>
            <Input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              {...register('currentPassword')}
              aria-invalid={!!errors.currentPassword}
            />
            {errors.currentPassword && (
              <p className="text-sm text-destructive">
                {t(errors.currentPassword.message ?? 'validationError')}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="newPassword">{t('passwordChange.newPassword')}</Label>
            <Input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              {...register('newPassword')}
              aria-invalid={!!errors.newPassword}
            />
            <p className="text-xs text-muted-foreground">{t('passwordChange.newPasswordHelp')}</p>
            {errors.newPassword && (
              <p className="text-sm text-destructive">
                {t(errors.newPassword.message ?? 'validationError')}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="confirmNewPassword">{t('passwordChange.confirm')}</Label>
            <Input
              id="confirmNewPassword"
              type="password"
              autoComplete="new-password"
              {...register('confirmNewPassword')}
              aria-invalid={!!errors.confirmNewPassword}
            />
            {errors.confirmNewPassword && (
              <p className="text-sm text-destructive">
                {t(errors.confirmNewPassword.message ?? 'validationError')}
              </p>
            )}
          </div>

          <DialogFooter>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? t('passwordChange.submitting') : t('passwordChange.confirm')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
