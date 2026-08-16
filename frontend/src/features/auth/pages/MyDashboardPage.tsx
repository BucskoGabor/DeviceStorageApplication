import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { MapPin, Laptop, Activity } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { deviceApi } from '@/features/device/api/deviceApi'
import { authApi } from '@/features/auth/api/authApi'
import { userApi } from '@/features/user/api/userApi'
import { assignmentApi } from '@/features/assignment/api/assignmentApi'
import { useAuthStore } from '@/lib/store/authStore'

/**
 * MyDashboardPage — a bejelentkezett user saját dashboardja.
 *
 * Tartalom:
 * - Saját / elérhető eszközök listája
 * - Saját irodai lokáció (userApi.findById(me.id))
 * - Jóváhagyási és aktivitási áttekintés (ha van hozzá jogosultság)
 */
export function MyDashboardPage({ children }: { children?: React.ReactNode }) {
  const { t } = useTranslation()
  const permissions = useAuthStore((state) => state.permissions)
  const canViewApprovals =
    permissions.includes('DEVICE_ASSIGN') ||
    permissions.includes('DEVICE_UNASSIGN') ||
    permissions.includes('DEVICE_MAINTENANCE_APPROVE') ||
    permissions.includes('DEVICE_DISPOSE_APPROVE')

  const { data: me, isLoading: isMeLoading } = useQuery({
    queryKey: ['me'],
    queryFn: () => authApi.me(),
  })

  const { data: myUserDetail, isLoading: isUserDetailLoading } = useQuery({
    queryKey: ['user-detail', me?.id],
    queryFn: () => userApi.findById(me!.id),
    enabled: Boolean(me?.id),
  })

  const { data: devicesData, isLoading: isDevicesLoading } = useQuery({
    queryKey: ['my-devices-summary'],
    queryFn: () => deviceApi.findAll({ page: 0, size: 5 }),
  })

  const { data: pendingAssignments, isLoading: isPendingLoading } = useQuery({
    queryKey: ['pending-assignments-summary'],
    queryFn: () => assignmentApi.findPendingAssignments(),
    enabled: canViewApprovals,
  })

  const devices = devicesData?.content ?? []
  const office = myUserDetail?.officeLocation ?? myUserDetail?.officeLocationSummary

  return (
    <div className="space-y-6">
      {children}
      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        {/* Saját / elérhető eszközök */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Laptop className="h-5 w-5 text-primary" />
              {t('dashboard.myDevices')}
            </CardTitle>
            <CardDescription>
              {devicesData?.totalElements
                ? `${devicesData.totalElements} ${t('common.itemsPerPage', 'eszköz elérhető')}`
                : t('dashboard.noDevices')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {isDevicesLoading ? (
              <p className="text-sm text-muted-foreground">{t('common.loading')}...</p>
            ) : devices.length > 0 ? (
              <ul className="space-y-2">
                {devices.map((device) => (
                  <li
                    key={device.id}
                    className="flex items-center justify-between border-b border-border py-1.5 text-sm"
                  >
                    <span className="font-mono font-medium">{device.inventoryNumber}</span>
                    <Badge variant="outline" className="text-xs">
                      {device.type}
                    </Badge>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted-foreground">{t('dashboard.noDevices')}</p>
            )}
          </CardContent>
        </Card>

        {/* Saját lokációk */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <MapPin className="h-5 w-5 text-primary" />
              {t('dashboard.myLocations')}
            </CardTitle>
            <CardDescription>{t('users.office', 'Irodai beosztás')}</CardDescription>
          </CardHeader>
          <CardContent>
            {isMeLoading || isUserDetailLoading ? (
              <p className="text-sm text-muted-foreground">{t('common.loading')}...</p>
            ) : office ? (
              <div className="space-y-1 rounded-md border border-border bg-card p-3">
                <p className="text-sm font-semibold">{office.name}</p>
                <p className="font-mono text-xs text-muted-foreground">
                  Type: {office.type} (#{office.id})
                </p>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">{t('dashboard.noLocations')}</p>
            )}
          </CardContent>
        </Card>

        {/* Aktivitási előzmények / Jóváhagyási sor (csak jogosultsággal) */}
        {canViewApprovals && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Activity className="h-5 w-5 text-primary" />
                {t('dashboard.recentAssignments')}
              </CardTitle>
              <CardDescription>
                {t('assignments.approvalQueue', 'Függőben lévő kérelmek')}
              </CardDescription>
            </CardHeader>
            <CardContent>
              {isPendingLoading ? (
                <p className="text-sm text-muted-foreground">{t('common.loading')}...</p>
              ) : pendingAssignments && pendingAssignments.length > 0 ? (
                <ul className="space-y-2">
                  {pendingAssignments.slice(0, 4).map((pa) => (
                    <li
                      key={pa.id}
                      className="flex items-center justify-between border-b border-border py-1 text-xs"
                    >
                      <span className="font-mono">#{pa.device?.id ?? pa.id}</span>
                      <Badge variant="secondary" className="text-xs">
                        {pa.status}
                      </Badge>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-muted-foreground">Nincs függőben lévő kérelem</p>
              )}
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  )
}
