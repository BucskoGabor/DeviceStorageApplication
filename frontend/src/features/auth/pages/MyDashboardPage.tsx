import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { DashboardPage } from '@/features/auth/pages/DashboardPage'

/**
 * MyDashboardPage — a bejelentkezett user saját dashboardja.
 *
 * Tartalom:
 * - Saját eszközök listája (Task 4.5-ben DataTable komponens)
 * - Saját lokációk
 * - Legutóbbi hozzárendelések
 * - TODO: Task 4.4 — DeviceQueryService hívása a row-level filterrel
 */
export function MyDashboardPage() {
  const { t } = useTranslation()

  return (
    <DashboardPage>
      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.myDevices')}</CardTitle>
            <CardDescription>TODO Task 4.4: deviceApi.findMyDevices()</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-muted-foreground">{t('dashboard.noDevices')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.myLocations')}</CardTitle>
            <CardDescription>TODO Task 4.4: locationApi.findMyLocations()</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-muted-foreground">{t('dashboard.noLocations')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.recentAssignments')}</CardTitle>
            <CardDescription>TODO Task 4.4: assignmentApi.findRecent()</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-muted-foreground">—</p>
          </CardContent>
        </Card>
      </div>
    </DashboardPage>
  )
}
