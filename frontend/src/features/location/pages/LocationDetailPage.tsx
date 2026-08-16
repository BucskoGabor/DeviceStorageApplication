import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { MapPin, ArrowLeft, Laptop, History, Eye } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { locationApi } from '@/features/location/api/locationApi'
import { StatusBadge } from '@/features/assignment/components/StatusBadge'

/**
 * LocationDetailPage — /admin/locations/:id route.
 *
 * Megjeleníti a helyszín adatait, valamint két külön fülön (Tabs):
 * 1. Jelenleg hozzárendelt / a helyszínen lévő aktív eszközök listája
 * 2. Korábbi hozzárendelések és eszközmozgások története
 */
export function LocationDetailPage() {
  const { t } = useTranslation()
  const params = useParams<{ id: string }>()
  const navigate = useNavigate()
  const locationId = Number(params.id)
  const [activeTab, setActiveTab] = useState<'current' | 'history'>('current')

  const { data: location, isLoading: locationLoading } = useQuery({
    queryKey: ['location', locationId],
    queryFn: () => locationApi.findById(locationId),
    enabled: Number.isFinite(locationId),
  })

  const { data: parentLocation } = useQuery({
    queryKey: ['location', location?.parentId],
    queryFn: () => locationApi.findById(location!.parentId!),
    enabled: Boolean(location?.parentId),
  })

  const { data: currentDevices, isLoading: currentLoading } = useQuery({
    queryKey: ['location-current-devices', locationId],
    queryFn: () => locationApi.findCurrentDevices(locationId),
    enabled: Number.isFinite(locationId),
  })

  const { data: assignmentHistory, isLoading: historyLoading } = useQuery({
    queryKey: ['location-assignment-history', locationId],
    queryFn: () => locationApi.findAssignmentHistory(locationId),
    enabled: Number.isFinite(locationId),
  })

  if (locationLoading) {
    return <p className="text-muted-foreground">{t('common.loading', 'Betöltés...')}...</p>
  }

  if (!location) {
    return (
      <div className="space-y-4">
        <Button variant="ghost" onClick={() => navigate('/locations')}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          {t('common.back', 'Vissza')}
        </Button>
        <p className="text-destructive">{t('common.error', 'Helyszín nem található')}</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Vissza gomb és Fejléc */}
      <div>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => navigate('/locations')}
          className="-ml-2 mb-2 text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="mr-2 h-4 w-4" />
          {t('locations.title', 'Helyszínek')}
        </Button>
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <MapPin className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-2xl font-semibold text-foreground">{location.name}</h1>
              <div className="mt-0.5 flex items-center gap-2 text-xs text-muted-foreground">
                <span>#{location.id}</span>
                <span>•</span>
                <Badge variant="secondary" className="text-[11px]">
                  {t(
                    `locations.type${location.type.charAt(0) + location.type.slice(1).toLowerCase()}`,
                    location.type
                  )}
                </Badge>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Alapadatok kártya */}
      <Card>
        <CardHeader className="py-4">
          <CardTitle className="text-sm font-semibold">
            {t('locations.title', 'Helyszín adatai')}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 md:grid-cols-3">
            <div>
              <span className="block text-xs text-muted-foreground">
                {t('locations.name', 'Név')}
              </span>
              <span className="font-medium text-foreground">{location.name}</span>
            </div>
            <div>
              <span className="block text-xs text-muted-foreground">
                {t('locations.type', 'Típus')}
              </span>
              <Badge variant="outline" className="mt-0.5">
                {t(
                  `locations.type${location.type.charAt(0) + location.type.slice(1).toLowerCase()}`,
                  location.type
                )}
              </Badge>
            </div>
            <div>
              <span className="block text-xs text-muted-foreground">
                {t('locations.parent', 'Szülő helyszín')}
              </span>
              {parentLocation ? (
                <Link
                  to={`/locations/${parentLocation.id}`}
                  className="mt-0.5 inline-flex items-center gap-1 font-medium text-primary hover:underline"
                >
                  <MapPin className="h-3 w-3" />
                  {parentLocation.name}
                </Link>
              ) : (
                <span className="text-xs italic text-muted-foreground">
                  {t('locations.noParent', 'Nincs szülő (gyökér)')}
                </span>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Két fül választó (Tabs) */}
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
          <span>{t('locations.currentDevices', 'Jelenleg hozzárendelt eszközök')}</span>
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
          <span>{t('locations.assignmentHistory', 'Korábbi hozzárendelések előzményei')}</span>
          <Badge variant="secondary" className="ml-1 text-xs">
            {assignmentHistory?.length ?? 0}
          </Badge>
        </button>
      </div>

      {/* 1. FELÜLET: Jelenleg itt lévő eszközök */}
      {activeTab === 'current' && (
        <Card>
          <CardHeader className="py-4">
            <CardTitle className="text-base">
              {t(
                'locations.currentDevicesTitle',
                'A helyszínen jelenleg elérhető / hozzárendelt eszközök'
              )}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {currentLoading ? (
              <p className="p-6 text-sm text-muted-foreground">
                {t('common.loading', 'Betöltés...')}...
              </p>
            ) : !currentDevices || currentDevices.length === 0 ? (
              <div className="p-8 text-center text-sm text-muted-foreground">
                <Laptop className="mx-auto mb-2 h-8 w-8 text-muted-foreground/50" />
                <p>
                  {t(
                    'locations.noCurrentDevices',
                    'Jelenleg nincs eszköz ehhez a helyszínhez rendelve.'
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
                'locations.assignmentHistoryTitle',
                'Hozzárendelési és eszközmozgási előzmények ezen a helyszínen'
              )}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {historyLoading ? (
              <p className="p-6 text-sm text-muted-foreground">
                {t('common.loading', 'Betöltés...')}...
              </p>
            ) : !assignmentHistory || assignmentHistory.length === 0 ? (
              <div className="p-8 text-center text-sm text-muted-foreground">
                <History className="mx-auto mb-2 h-8 w-8 text-muted-foreground/50" />
                <p>
                  {t(
                    'locations.noAssignmentHistory',
                    'Nem található korábbi hozzárendelési előzmény ehhez a helyszínhez.'
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
                      <th className="p-3 font-medium">{t('assignments.toUser', 'Felhasználó')}</th>
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
                        <td className="p-3 font-mono text-xs">
                          {item.toUser?.email ?? item.fromUser?.email ?? '—'}
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
                              onClick={() => navigate(`/devices/${item.device!.id}`)}
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
