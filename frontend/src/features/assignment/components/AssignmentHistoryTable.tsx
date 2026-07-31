import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { assignmentApi, type DeviceAssignment } from '../api/assignmentApi'
import { StatusBadge } from './StatusBadge'

interface AssignmentHistoryTableProps {
  deviceId: number
}

/**
 * AssignmentHistoryTable — Device teljes assignment history táblázat.
 *
 * Lapozással, createdDate desc sorrendben.
 */
export function AssignmentHistoryTable({ deviceId }: AssignmentHistoryTableProps) {
  const { t } = useTranslation()

  const { data, isLoading } = useQuery({
    queryKey: ['assignments', deviceId],
    queryFn: () => assignmentApi.findAssignmentsByDevice(deviceId, { page: 0, size: 50 }),
  })

  if (isLoading) {
    return <p className="text-muted-foreground">{t('common.loading')}...</p>
  }

  if (!data || data.content.length === 0) {
    return <p className="text-muted-foreground">{t('common.noData')}</p>
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('assignments.status')}</TableHead>
          <TableHead>{t('assignments.fromLocation')}</TableHead>
          <TableHead>{t('assignments.toLocation')}</TableHead>
          <TableHead>{t('assignments.toUser')}</TableHead>
          <TableHead>{t('assignments.requestedBy')}</TableHead>
          <TableHead>{t('assignments.approvedBy')}</TableHead>
          <TableHead>{t('audit.timestamp')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {data.content.map((a: DeviceAssignment) => (
          <TableRow key={a.id}>
            <TableCell>
              <StatusBadge status={a.status} />
            </TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {a.fromLocation?.name ?? '—'}
            </TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {a.toLocation?.name ?? '—'}
            </TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {a.toUser?.email ?? '—'}
            </TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {a.createdByUser?.email ?? '—'}
            </TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {a.approvedBy?.email ?? '—'}
            </TableCell>
            <TableCell className="text-xs text-muted-foreground">
              {new Date(a.createdDate).toLocaleString()}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
