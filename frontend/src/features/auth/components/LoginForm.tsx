import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/lib/store/authStore'
import { authApi, LoginResponse } from '@/features/auth/api/authApi'
import { loginSchema, type LoginFormData } from '@/features/auth/schemas/loginSchema'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * LoginForm — email + password form React Hook Form + Zod validációval.
 *
 * Sikeres login esetén:
 * - A useAuthStore eltárolja a tokent, email-t, role-t, permission-öket, mustChangePassword flag-et
 * - Ha mustChangePassword=true: Dashboard-on inline modal jelenik meg a PasswordChangeForm-mal
 * - Ha mustChangePassword=false: navigate('/my-dashboard')
 */
export function LoginForm() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const setAuth = useAuthStore((state) => state.setAuth)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  })

  const onSubmit = async (data: LoginFormData) => {
    setIsSubmitting(true)
    try {
      const response: LoginResponse = await authApi.login(data)
      setAuth(
        response.accessToken,
        data.email,
        response.role,
        response.permissions,
        response.mustChangePassword
      )
      toast.success(t('loginSuccess'), { position: 'top-right' })
      navigate('/my-dashboard')
    } catch (error: any) {
      const messageKey = error.response?.data?.messageKey ?? 'invalidCredentials'
      toast.error(t(messageKey), { position: 'top-right' })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <Label htmlFor="email">{t('login.email')}</Label>
        <Input
          id="email"
          type="email"
          placeholder={t('login.emailPlaceholder')}
          autoComplete="email"
          {...register('email')}
          aria-invalid={!!errors.email}
        />
        {errors.email && (
          <p className="text-sm text-destructive">{t(errors.email.message ?? 'validationError')}</p>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="password">{t('login.password')}</Label>
        <Input
          id="password"
          type="password"
          autoComplete="current-password"
          {...register('password')}
          aria-invalid={!!errors.password}
        />
        {errors.password && (
          <p className="text-sm text-destructive">{t(errors.password.message ?? 'validationError')}</p>
        )}
      </div>

      <Button type="submit" disabled={isSubmitting} className="mt-2">
        {isSubmitting ? t('login.submitting') : t('login.submit')}
      </Button>
    </form>
  )
}
