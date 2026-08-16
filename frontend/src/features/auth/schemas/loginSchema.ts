import { z } from 'zod'

/**
 * Login form Zod validációs séma.
 */
export const loginSchema = z.object({
  email: z
    .string()
    .email({ message: 'validation.email' })
    .min(1, { message: 'validation.notBlank' }),
  password: z.string().min(1, { message: 'validation.notBlank' }),
})

export type LoginFormData = z.infer<typeof loginSchema>
