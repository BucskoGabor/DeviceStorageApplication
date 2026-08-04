import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Check, X } from 'lucide-react'
import { toast } from 'sonner'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { resolveToastMessage } from '@/lib/utils/toastUtils'
import { assignmentApi, type DeviceAssignment } from '../api/assignmentApi'
import { StatusBadge } from './StatusBadge'

/**
 * ApprovalQueue — Jóváhagyásra váró assignment lista (PENDING_ASSIGNMENT + PENDING_UNASSIGNMENT).
 *
 * Az admin/teacher itt tudja jóváhagyni vagy elutasítani a függőben lévő kérelmeket.
 */
export function ApprovalQueue() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['pending-assignments'],
    queryFn: () => assignmentApi.findPendingAssignments(),
    refetchInterval: 30000,
  })

  const approveAssignmentMutation = useMutation({
    mutationFn: (id: number) => assignmentApi.approveAssignment(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pending-assignments'] })
      queryClient.invalidateQueries({ queryKey: ['assignments'] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      toast.success(t('assignments.approveAssignmentSuccess'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const approveUnassignmentMutation = useMutation({
    mutationFn: (id: number) => assignmentApi.approveUnassignment(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pending-assignments'] })
      queryClient.invalidateQueries({ queryKey: ['assignments'] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      toast.success(t('assignments.approveUnassignmentSuccess'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const rejectMutation = useMutation({
    mutationFn: (id: number) => assignmentApi.rejectAssignment(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pending-assignments'] })
      queryClient.invalidateQueries({ queryKey: ['assignments'] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      toast.success(t('assignments.rejectAssignmentSuccess'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  if (isLoading) {
    return <p className="text-muted-foreground">{t('common.loading')}...</p>
  }

  if (!data || data.length === 0) {
    return <p className="text-muted-foreground">{t('common.noData')}</p>
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('assignments.device')}</TableHead>
          <TableHead>{t('assignments.status')}</TableHead>
          <TableHead>{t('assignments.fromLocation')}</TableHead>
          <TableHead>{t('assignments.toLocation')}</TableHead>
          <TableHead>{t('assignments.toUser')}</TableHead>
          <TableHead>{t('assignments.requestedBy')}</TableHead>
          <TableHead>{t('audit.timestamp')}</TableHead>
          <TableHead className="text-right">{t('common.actions')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {data.map((a: DeviceAssignment) => (
          <TableRow key={a.id}>
            <TableCell className="font-mono text-xs">
              {a.device?.inventoryNumber ?? `#${a.device?.id ?? '?'}`}
            </TableCell>
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
              {new Date(a.createdDate).toLocaleString()}
            </TableCell>
            <TableCell className="text-right">
              <div className="flex items-center justify-end space-x-2">
                {a.status === 'PENDING_ASSIGNMENT' ? (
                  <Button
                    size="sm"
                    variant="default"
                    disabled={approveAssignmentMutation.isPending || rejectMutation.isPending}
                    onClick={() => approveAssignmentMutation.mutate(a.id)}
                  >
                    <Check className="mr-1 h-4 w-4" />
                    {t('assignments.approveAssignment')}
                  </Button>
                ) : (
                  <Button
                    size="sm"
                    variant="default"
                    disabled={approveUnassignmentMutation.isPending || rejectMutation.isPending}
                    onClick={() => approveUnassignmentMutation.mutate(a.id)}
                  >
                    <Check className="mr-1 h-4 w-4" />
                    {t('assignments.approveUnassignment')}
                  </Button>
                )}
                <Button
                  size="sm"
                  variant="destructive"
                  disabled={
                    approveAssignmentMutation.isPending ||
                    approveUnassignmentMutation.isPending ||
                    rejectMutation.isPending
                  }
                  onClick={() => rejectMutation.mutate(a.id)}
                >
                  <X className="mr-1 h-4 w-4" />
                  {t('assignments.rejectAssignment')}
                </Button>
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
