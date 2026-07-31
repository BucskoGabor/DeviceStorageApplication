import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { DashboardPage } from '@/features/auth/pages/DashboardPage'
import { deviceApi } from '@/features/device/api/deviceApi'

/**
 * MyDashboardPage — a bejelentkezett user saját dashboardja.
 *
 * Tartalom:
 * - Saját eszközök listája (row-level filter által engedélyezett eszközök)
 * - Statisztika és áttekintés
 */
interface MyDashboardPageProps {
  children?: React.ReactNode
}

export function MyDashboardPage({ children }: MyDashboardPageProps) {
  const { t } = useTranslation()

  const { data: devicesData, isLoading: isDevicesLoading } = useQuery({
    queryKey: ['my-devices-summary'],
    queryFn: () => deviceApi.findAll({ page: 0, size: 5 }),
  })

  const devices = devicesData?.content ?? []

  return (
    <DashboardPage>
      {children}
      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.myDevices')}</CardTitle>
            <CardDescription>
              {devicesData?.totalElements
                ? `${devicesData.totalElements} eszköz elérhető`
                : t('dashboard.noDevices')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {isDevicesLoading ? (
              <p className="text-sm text-muted-foreground">Betöltés...</p>
            ) : devices.length > 0 ? (
              <ul className="space-y-2">
                {devices.map((device) => (
                  <li key={device.id} className="flex items-center justify-between text-sm border-b border-border py-1">
                    <span className="font-medium">{device.inventoryNumber}</span>
                    <span className="rounded bg-primary/10 px-2 py-0.5 text-xs text-primary font-semibold">
                      {device.type}
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-muted-foreground">{t('dashboard.noDevices')}</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.myLocations')}</CardTitle>
            <CardDescription>Irodai / Hozzárendelt lokációk</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-muted-foreground">{t('dashboard.noLocations')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.recentAssignments')}</CardTitle>
            <CardDescription>Aktivitási előzmények</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-muted-foreground">—</p>
          </CardContent>
        </Card>
      </div>
    </DashboardPage>
  )
}
