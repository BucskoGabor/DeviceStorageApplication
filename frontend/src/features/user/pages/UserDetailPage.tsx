import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { User, Mail, Shield, Key, MapPin } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { userApi } from '@/features/user/api/userApi'

/**
 * UserDetailPage — /admin/users/:id route.
 *
 * Egy adott user részletes adatai:
 * - Személyes adatok (név, email hash, role)
 * - Státusz (active/inactive, mustChangePassword, lockedUntil, failedLoginCount)
 * - Office location
 *
 * A page csak USER_READ permission-t kér — nem szerkeszthető.
 * A szerkesztéshez a UsersPage táblázat Pencil ikonját kell használni.
 */
export function UserDetailPage() {
  const { t } = useTranslation()
  const params = useParams<{ id: string }>()
  const userId = Number(params.id)

  const { data: user, isLoading } = useQuery({
    queryKey: ['user', userId],
    queryFn: () => userApi.findById(userId),
    enabled: Number.isFinite(userId),
  })

  if (isLoading) {
    return <p className="text-muted-foreground">{t('common.loading')}...</p>
  }

  if (!user) {
    return <p className="text-destructive">{t('common.error')}</p>
  }

  const roleName = user.role?.name ?? 'ROLE_USER'
  const officeName = user.officeLocation?.name ?? '—'

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">
        {t('users.user')} #{user.id}
      </h1>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <User className="h-5 w-5" />
            {t('myProfile.basicInfo')}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <ProfileField
            icon={<Mail className="h-4 w-4 text-muted-foreground" />}
            label={t('users.email')}
            value={<span className="font-mono text-xs">{user.email || user.emailMasked}</span>}
          />
          <ProfileField
            icon={<Shield className="h-4 w-4 text-muted-foreground" />}
            label={t('users.role')}
            value={<Badge variant="outline">{t(`roles.${roleName}`, roleName)}</Badge>}
          />
          <ProfileField
            icon={<MapPin className="h-4 w-4 text-muted-foreground" />}
            label={t('users.office')}
            value={officeName}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('users.status')}</CardTitle>
          <CardDescription>{t('users.accountStatus')}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex flex-wrap gap-2">
            <Badge variant={user.active ? 'default' : 'destructive'}>
              {user.active ? t('users.active') : t('users.inactive')}
            </Badge>
            {user.mustChangePassword && (
              <Badge variant="outline">{t('users.mustChangePassword')}</Badge>
            )}
            {user.lockedUntil && (
              <Badge variant="destructive">
                {t('users.lockedUntil', { date: new Date(user.lockedUntil).toLocaleString() })}
              </Badge>
            )}
            {(user.failedLoginCount ?? 0) > 0 && (
              <Badge variant="outline">
                {t('users.failedLoginCount', { count: user.failedLoginCount })}
              </Badge>
            )}
          </div>
          <div className="text-xs text-muted-foreground">
            <Key className="mr-1 inline h-3 w-3" />
            {t('users.createdAt')}: #{user.id} (a created_at mező a BaseEntity-ben van, de itt nem lekérdezett)
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

interface ProfileFieldProps {
  icon: React.ReactNode
  label: string
  value: React.ReactNode
}

function ProfileField({ icon, label, value }: ProfileFieldProps) {
  return (
    <div className="flex items-start gap-3">
      <div className="mt-0.5">{icon}</div>
      <div className="flex-1">
        <p className="text-xs text-muted-foreground">{label}</p>
        <div className="mt-1 text-sm">{value}</div>
      </div>
    </div>
  )
}
