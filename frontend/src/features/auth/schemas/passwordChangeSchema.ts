import { z } from 'zod'
import { SizeLimits } from '@/lib/api/sizeLimits'

/**
 * Password change form Zod validációs séma.
 */
export const passwordChangeSchema = z
  .object({
    currentPassword: z.string().min(1, { message: 'validation.notBlank' }),
    newPassword: z
      .string()
      .min(12, { message: 'passwordChangeTooShort' })
      .max(SizeLimits.PASSWORD_HASH_MAX, { message: 'validationMaxLength' })
      .regex(/[a-z]/, { message: 'validationNewPasswordLowercase' })
      .regex(/[A-Z]/, { message: 'validationNewPasswordUppercase' })
      .regex(/[0-9]/, { message: 'validationNewPasswordDigit' })
      .regex(/[^a-zA-Z0-9]/, { message: 'validationNewPasswordSpecial' }),
    confirmNewPassword: z.string().min(1, { message: 'validation.notBlank' }),
  })
  .refine((data) => data.newPassword === data.confirmNewPassword, {
    message: 'passwordChangeMismatch',
    path: ['confirmNewPassword'],
  })
  .refine((data) => data.newPassword !== data.currentPassword, {
    message: 'passwordChangeSameAsOld',
    path: ['newPassword'],
  })

export type PasswordChangeFormData = z.infer<typeof passwordChangeSchema>
