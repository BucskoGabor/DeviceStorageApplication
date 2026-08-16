import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { User, Mail, Shield, Key } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { authApi } from '@/features/auth/api/authApi'
import { PasswordChangeForm } from '@/features/auth/components/PasswordChangeForm'

/**
 * MyProfilePage — /my-profile route.
 *
 * A bejelentkezett user saját profil adatainak megjelenítése:
 * - Email (maszkolva)
 * - Role (i18n kulccsal)
 * - Permissions lista
 * - Aktív/inaktív státusz
 * - Jelszó csere gomb (újrafelhasználja a PasswordChangeForm-ot closable módban)
 *
 * Route guard: RequireAuth — minden bejelentkezett role elérheti (nincs RequireRole).
 */
export function MyProfilePage() {
  const { t } = useTranslation()
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false)

  const { data: me, isLoading } = useQuery({
    queryKey: ['me'],
    queryFn: () => authApi.me(),
    staleTime: 60000,
  })

  if (isLoading) {
    return <p className="text-muted-foreground">{t('common.loading')}...</p>
  }

  if (!me) {
    return <p className="text-destructive">{t('common.error')}</p>
  }

  const email = me.email || me.emailMasked || me.emailHash
  const roleLabel = me.role.replace('ROLE_', '')
  const roleI18nKey = `roles.${me.role}`

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{t('myProfile.title')}</h1>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <User className="h-5 w-5" />
            {t('myProfile.basicInfo')}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <ProfileField
            icon={<Mail className="h-4 w-4 text-muted-foreground" />}
            label={t('users.email')}
            value={<span className="font-mono text-sm">{email}</span>}
          />
          <ProfileField
            icon={<Shield className="h-4 w-4 text-muted-foreground" />}
            label={t('users.role')}
            value={
              <div className="flex items-center gap-2">
                <Badge variant="secondary">{t(roleI18nKey, roleLabel)}</Badge>
                {!me.active && <Badge variant="destructive">{t('users.inactive')}</Badge>}
              </div>
            }
          />
          <ProfileField
            icon={<Key className="h-4 w-4 text-muted-foreground" />}
            label={t('myProfile.password')}
            value={
              <Button variant="outline" size="sm" onClick={() => setPasswordDialogOpen(true)}>
                <Key className="mr-2 h-4 w-4" />
                {t('passwordChange.title')}
              </Button>
            }
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Shield className="h-5 w-5" />
            {t('myProfile.permissions')}
          </CardTitle>
          <CardDescription>
            {me.permissions.length} {t('myProfile.permissionsCount')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {me.permissions.length === 0 ? (
            <p className="text-muted-foreground">{t('myProfile.noPermissions')}</p>
          ) : (
            <div className="flex flex-wrap gap-1">
              {me.permissions.map((p) => (
                <Badge key={p} variant="outline" className="font-mono text-xs">
                  {t(`permissions.${p}`, p)}
                </Badge>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <PasswordChangeForm
        open={passwordDialogOpen}
        onSuccess={() => setPasswordDialogOpen(false)}
        closable
      />
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
