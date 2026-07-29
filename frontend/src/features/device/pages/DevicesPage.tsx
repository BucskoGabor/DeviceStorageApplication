import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { type ColumnDef } from '@tanstack/react-table'
import { Plus, Pencil, Trash2, FileText } from 'lucide-react'
import { Link } from 'react-router-dom'
import { DataTable } from '@/components/DataTable/DataTable'
import { Button } from '@/components/ui/button'
import { deviceApi, type Device } from '@/features/device/api/deviceApi'

/**
 * DevicesPage — admin/devices táblázat (DataTable + lapozás + szűrés).
 *
 * A deviceApi.findAll() hívja a backend /api/devices endpoint-ot, ami
 * a JpaSpecificationExecutor + row-level filter alapján szűri az adatokat
 * (STUDENT csak saját, TEACHER saját + irodai, ADMIN mindent).
 */
export function DevicesPage() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const pageSize = 20

  // TanStack Query: devices lista
  const { data, isLoading } = useQuery({
    queryKey: ['devices', page, pageSize, search],
    queryFn: () =>
      deviceApi.findAllDevices({
        page,
        size: pageSize,
        filter: search ? { inventoryNumber: search } : undefined,
      }),
  })

  // Device oszlopok definiálása
  const columns = useMemo<ColumnDef<Device, unknown>[]>(
    () => [
      {
        id: 'id',
        accessorKey: 'id',
        header: t('devices.id'),
        cell: (info) => <span className="font-mono text-xs">{String(info.getValue())}</span>,
      },
      {
        id: 'inventoryNumber',
        accessorKey: 'inventoryNumber',
        header: t('devices.inventoryNumber'),
      },
      {
        id: 'type',
        accessorKey: 'type',
        header: t('devices.type'),
      },
      {
        id: 'status',
        accessorKey: 'status',
        header: t('devices.status'),
        cell: (info) => {
          const status = info.getValue() as Device['status']
          const statusKey = `devices.status${status.charAt(0)}${status.slice(1).toLowerCase()}`
          return <span className="rounded-md bg-accent px-2 py-1 text-xs">{t(statusKey)}</span>
        },
      },
      {
        id: 'actions',
        header: t('common.actions'),
        cell: (info) => {
          const device = info.row.original
          return (
            <div className="flex gap-2">
              <Button variant="ghost" size="icon" asChild>
                <Link to={`/admin/devices/${device.id}`}>
                  <FileText className="h-4 w-4" />
                </Link>
              </Button>
              <Button variant="ghost" size="icon" asChild>
                <Link to={`/admin/devices/${device.id}/edit`}>
                  <Pencil className="h-4 w-4" />
                </Link>
              </Button>
              <Button variant="ghost" size="icon" onClick={() => {
                if (confirm(t('devices.confirmDelete'))) {
                  deviceApi.deleteDevice(device.id).then(() => {
                    window.location.reload()
                  })
                }
              }}>
                <Trash2 className="h-4 w-4 text-destructive" />
              </Button>
            </div>
          )
        },
      },
    ],
    [t]
  )

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('devices.title')}</h1>
        <Button asChild>
          <Link to="/admin/devices/create">
            <Plus className="mr-2 h-4 w-4" />
            {t('devices.create')}
          </Link>
        </Button>
      </div>

      <DataTable
        data={data?.content ?? []}
        columns={columns}
        isLoading={isLoading}
        page={page}
        pageSize={pageSize}
        totalElements={data?.totalElements ?? 0}
        onPageChange={setPage}
        searchColumnId="inventoryNumber"
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder={t('devices.inventoryNumber')}
      />
    </div>
  )
}
