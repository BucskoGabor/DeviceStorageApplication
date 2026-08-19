import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { User, Mail, Shield, Key, MapPin, ArrowLeft, Laptop, History, Eye } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { userApi } from '@/features/user/api/userApi'
import { StatusBadge } from '@/features/assignment/components/StatusBadge'
import { userKeys } from '@/lib/api/queryKeys'

/**
 * UserDetailPage — /admin/users/:id route.
 *
 * Egy adott user részletes adatai:
 * - Személyes adatok (név, email hash, role)
 * - Jogosultságok (örökölt és közvetlen)
 * - Fiók státusz (aktív, zárolt, stb.)
 * - Két külön fülön (Tabs):
 *   1. Jelenleg a felhasználóhoz rendelt eszközök listája
 *   2. Korábbi hozzárendelések és eszközmozgások története
 */
export function UserDetailPage() {
  const { t } = useTranslation()
  const params = useParams<{ id: string }>()
  const navigate = useNavigate()
  const userId = Number(params.id)
  const [activeTab, setActiveTab] = useState<'current' | 'history'>('current')

  const { data: user, isLoading: isUserLoading } = useQuery({
    queryKey: userKeys.detail(userId),
    queryFn: () => userApi.findById(userId),
    enabled: Number.isFinite(userId),
  })

  const { data: currentDevices, isLoading: isCurrentLoading } = useQuery({
    queryKey: userKeys.devices(userId),
    queryFn: () => userApi.findCurrentDevices(userId),
    enabled: Number.isFinite(userId),
  })

  const { data: assignmentHistory, isLoading: isHistoryLoading } = useQuery({
    queryKey: userKeys.history(userId),
    queryFn: () => userApi.findAssignmentHistory(userId),
    enabled: Number.isFinite(userId),
  })
  if (isUserLoading) {
    return <p className="text-muted-foreground">{t('common.loading')}...</p>
  }

  if (!user) {
    return (
      <div className="space-y-4">
        <Button variant="ghost" onClick={() => navigate('/users')}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          {t('common.back', 'Vissza')}
        </Button>
        <p className="text-destructive">{t('common.error')}</p>
      </div>
    )
  }

  const roleName = user.role?.name ?? 'ROLE_USER'
  const officeName = user.officeLocation?.name ?? user.officeLocationSummary?.name ?? '—'
  const rolePermissions = user.role?.permissions ?? []
  const directPermissions = user.directPermissions ?? []

  return (
    <div className="space-y-6">
      {/* Vissza gomb és Fejléc */}
      <div>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => navigate('/users')}
          className="-ml-2 mb-2 text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="mr-2 h-4 w-4" />
          {t('users.title', 'Felhasználók')}
        </Button>
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <User className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-2xl font-semibold text-foreground">
                {user.email || user.emailMasked || `#${user.id}`}
              </h1>
              <div className="mt-0.5 flex items-center gap-2 text-xs text-muted-foreground">
                <span>#{user.id}</span>
                <span>•</span>
                <Badge variant="outline" className="text-[11px]">
                  {t(`roles.${roleName}`, roleName)}
                </Badge>
                <span>•</span>
                <Badge variant={user.active ? 'default' : 'destructive'} className="text-[11px]">
                  {user.active ? t('users.active') : t('users.inactive')}
                </Badge>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Alapadatok kártya */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <User className="h-5 w-5 text-primary" />
              {t('myProfile.basicInfo')}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <ProfileField
              icon={<Mail className="h-4 w-4 text-muted-foreground" />}
              label={t('users.email')}
              value={
                <span className="font-mono text-sm">
                  {user.email || user.emailMasked || user.emailHash}
                </span>
              }
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

        {/* Fiók státusz kártya */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t('users.status')}</CardTitle>
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
            {user.createdAt && (
              <div className="text-xs text-muted-foreground">
                <Key className="mr-1 inline h-3 w-3" />
                {t('audit.timestamp', 'Létrehozva')}: {new Date(user.createdAt).toLocaleString()}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Jogosultságok kártya */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Shield className="h-5 w-5 text-primary" />
            {t('myProfile.permissions', 'Jogosultságok')}
          </CardTitle>
          <CardDescription>
            {user.effectivePermissions?.length ?? rolePermissions.length + directPermissions.length}{' '}
            {t('myProfile.permissionsCount', 'érvényes jogosultság')}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div>
            <h4 className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {t('roles.inheritedPermissions', 'Szerepkörből örökölt jogok')} (
              {rolePermissions.length})
            </h4>
            {rolePermissions.length > 0 ? (
              <div className="flex flex-wrap gap-1.5">
                {rolePermissions.map((p) => (
                  <Badge key={p.id} variant="secondary" className="font-mono text-[11px]">
                    {p.name}
                  </Badge>
                ))}
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">
                {t('common.noData', 'Nincs jog a szerepkörben')}
              </p>
            )}
          </div>

          <div>
            <h4 className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {t('users.directPermissions', 'Közvetlenül hozzárendelt jogok')} (
              {directPermissions.length})
            </h4>
            {directPermissions.length > 0 ? (
              <div className="flex flex-wrap gap-1.5">
                {directPermissions.map((p) => (
                  <Badge
                    key={p.id}
                    variant="default"
                    className="bg-primary/80 font-mono text-[11px]"
                  >
                    {p.name}
                  </Badge>
                ))}
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">
                {t('common.noData', 'Nincsenek egyedi közvetlen jogok')}
              </p>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Két fül választó (Tabs) az eszközökhöz */}
      <div className="flex border-b border-border">
        <button
          type="button"
          onClick={() => setActiveTab('current')}
          className={`flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
            activeTab === 'current'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <Laptop className="h-4 w-4" />
          <span>{t('users.currentDevices', 'Jelenleg hozzárendelt eszközök')}</span>
          <Badge variant="secondary" className="ml-1 text-xs">
            {currentDevices?.length ?? 0}
          </Badge>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('history')}
          className={`flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
            activeTab === 'history'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <History className="h-4 w-4" />
          <span>{t('users.assignmentHistory', 'Korábbi hozzárendelések előzményei')}</span>
          <Badge variant="secondary" className="ml-1 text-xs">
            {assignmentHistory?.length ?? 0}
          </Badge>
        </button>
      </div>

      {/* 1. FELÜLET: Jelenleg hozzárendelt eszközök */}
      {activeTab === 'current' && (
        <Card>
          <CardHeader className="py-4">
            <CardTitle className="text-base">
              {t('users.currentDevicesTitle', 'A felhasználóhoz jelenleg hozzárendelt eszközök')}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {isCurrentLoading ? (
              <p className="p-6 text-sm text-muted-foreground">{t('common.loading')}...</p>
            ) : !currentDevices || currentDevices.length === 0 ? (
              <div className="p-8 text-center text-sm text-muted-foreground">
                <Laptop className="mx-auto mb-2 h-8 w-8 text-muted-foreground/50" />
                <p>
                  {t(
                    'users.noCurrentDevices',
                    'A felhasználóhoz jelenleg nincs eszköz hozzárendelve.'
                  )}
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground">
                    <tr>
                      <th className="p-3 font-medium">
                        {t('devices.inventoryNumber', 'Leltári szám')}
                      </th>
                      <th className="p-3 font-medium">{t('devices.type', 'Típus')}</th>
                      <th className="p-3 font-medium">{t('locations.title', 'Helyszín')}</th>
                      <th className="p-3 font-medium">{t('devices.status', 'Státusz')}</th>
                      <th className="p-3 text-right font-medium">
                        {t('common.actions', 'Műveletek')}
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {currentDevices.map((dev) => (
                      <tr key={dev.id} className="hover:bg-muted/30">
                        <td className="p-3 font-mono font-medium text-foreground">
                          {dev.inventoryNumber}
                        </td>
                        <td className="p-3">{dev.type}</td>
                        <td className="p-3 text-muted-foreground">
                          {dev.currentLocation?.name ?? '—'}
                        </td>
                        <td className="p-3">
                          <StatusBadge status={dev.status as any} />
                        </td>
                        <td className="p-3 text-right">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => navigate(`/devices/${dev.id}`)}
                            title={t('common.details', 'Részletek')}
                          >
                            <Eye className="mr-1.5 h-4 w-4" />
                            {t('common.details', 'Részletek')}
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* 2. FELÜLET: Korábbi hozzárendelések előzményei */}
      {activeTab === 'history' && (
        <Card>
          <CardHeader className="py-4">
            <CardTitle className="text-base">
              {t(
                'users.assignmentHistoryTitle',
                'A felhasználó korábbi eszközhozzárendelési előzményei'
              )}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {isHistoryLoading ? (
              <p className="p-6 text-sm text-muted-foreground">{t('common.loading')}...</p>
            ) : !assignmentHistory || assignmentHistory.length === 0 ? (
              <div className="p-8 text-center text-sm text-muted-foreground">
                <History className="mx-auto mb-2 h-8 w-8 text-muted-foreground/50" />
                <p>
                  {t(
                    'users.noAssignmentHistory',
                    'Nem található korábbi hozzárendelési előzmény a felhasználóhoz.'
                  )}
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground">
                    <tr>
                      <th className="p-3 font-medium">{t('assignments.device', 'Eszköz')}</th>
                      <th className="p-3 font-medium">{t('assignments.fromLocation', 'Honnan')}</th>
                      <th className="p-3 font-medium">{t('assignments.toLocation', 'Hová')}</th>
                      <th className="p-3 font-medium">{t('assignments.date', 'Időpont')}</th>
                      <th className="p-3 font-medium">{t('assignments.status', 'Státusz')}</th>
                      <th className="p-3 text-right font-medium">
                        {t('common.actions', 'Műveletek')}
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {assignmentHistory.map((item) => (
                      <tr key={item.id} className="hover:bg-muted/30">
                        <td className="p-3">
                          <span className="font-mono font-medium text-foreground">
                            {item.device?.inventoryNumber ?? `#${item.device?.id}`}
                          </span>
                          {item.device?.type && (
                            <span className="ml-2 text-xs text-muted-foreground">
                              ({item.device.type})
                            </span>
                          )}
                        </td>
                        <td className="p-3 text-muted-foreground">
                          {item.fromLocation?.name ?? '—'}
                        </td>
                        <td className="p-3 font-medium text-foreground">
                          {item.toLocation?.name ?? '—'}
                        </td>
                        <td className="p-3 text-xs text-muted-foreground">
                          {item.createdDate ? new Date(item.createdDate).toLocaleDateString() : '—'}
                          {item.unassignDate && (
                            <span className="block text-[11px] text-muted-foreground/75">
                              → {new Date(item.unassignDate).toLocaleDateString()}
                            </span>
                          )}
                        </td>
                        <td className="p-3">
                          <StatusBadge status={item.status} />
                        </td>
                        <td className="p-3 text-right">
                          {item.device?.id && (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => navigate(`/devices/${item.device?.id}`)}
                              title={t('common.details', 'Eszköz adatlap')}
                            >
                              <Eye className="mr-1.5 h-4 w-4" />
                              {t('common.details', 'Részletek')}
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      )}
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
