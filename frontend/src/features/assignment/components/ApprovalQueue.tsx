import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Check, X, Eye, Wrench, Trash2, ArrowRightLeft } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
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
import { Badge } from '@/components/ui/badge'
import { resolveToastMessage } from '@/lib/utils/toastUtils'
import { assignmentApi, type DeviceAssignment } from '../api/assignmentApi'
import { deviceApi, type Device } from '@/features/device/api/deviceApi'
import { StatusBadge } from './StatusBadge'
import { useAuthStore } from '@/lib/store/authStore'
import { assignmentKeys, maintenanceKeys, disposalKeys } from '@/lib/api/queryKeys'
import {
  invalidateAssignmentWorkflow,
  invalidateMaintenanceWorkflow,
  invalidateDisposalWorkflow,
} from '@/lib/api/invalidation'

/**
 * ApprovalQueue — Jóváhagyásra váró kérelmek listája:
 * 1. Hozzárendelések (PENDING_ASSIGNMENT + PENDING_UNASSIGNMENT)
 * 2. Karbantartási kérelmek (PENDING_MAINTENANCE)
 * 3. Selejtezési kérelmek (PENDING_DISPOSAL)
 *
 * 100% permission-alapú jóváhagyási és elutasítási műveletek.
 */
export function ApprovalQueue() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((state) => state.permissions)

  const canApproveAssignment = permissions.includes('ASSIGNMENT_APPROVE')
  const canApproveMaintenance = permissions.includes('DEVICE_MAINTENANCE_APPROVE')
  const canApproveDisposal = permissions.includes('DEVICE_DISPOSE_APPROVE')

  const [activeTab, setActiveTab] = useState<'assignments' | 'maintenance' | 'disposal'>(
    'assignments'
  )

  // 1. Assignments query
  const { data: pendingAssignments = [], isLoading: isLoadingAssignments } = useQuery({
    queryKey: assignmentKeys.pending(),
    queryFn: () => assignmentApi.findPendingAssignments(),
    refetchInterval: 30000,
  })

  // 2. Maintenance requests query
  const { data: pendingMaintenance = [], isLoading: isLoadingMaintenance } = useQuery({
    queryKey: maintenanceKeys.pending(),
    queryFn: () => deviceApi.findPendingMaintenance(),
    refetchInterval: 30000,
    enabled: canApproveMaintenance,
  })

  // 3. Disposal requests query
  const { data: pendingDisposal = [], isLoading: isLoadingDisposal } = useQuery({
    queryKey: disposalKeys.pending(),
    queryFn: () => deviceApi.findPendingDisposal(),
    refetchInterval: 30000,
    enabled: canApproveDisposal,
  })

  // Assignment Mutations
  const approveAssignmentMutation = useMutation({
    mutationFn: (id: number) => assignmentApi.approveAssignment(id),
    onSuccess: () => {
      invalidateAssignmentWorkflow(queryClient)
      toast.success(t('assignments.approveAssignmentSuccess'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const approveUnassignmentMutation = useMutation({
    mutationFn: (id: number) => assignmentApi.approveUnassignment(id),
    onSuccess: () => {
      invalidateAssignmentWorkflow(queryClient)
      toast.success(t('assignments.approveUnassignmentSuccess'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const rejectAssignmentMutation = useMutation({
    mutationFn: (id: number) => assignmentApi.rejectAssignment(id),
    onSuccess: () => {
      invalidateAssignmentWorkflow(queryClient)
      toast.success(t('assignments.rejectAssignmentSuccess'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  // Maintenance Mutations
  const approveMaintenanceMutation = useMutation({
    mutationFn: (id: number) => deviceApi.approveMaintenance(id),
    onSuccess: () => {
      invalidateMaintenanceWorkflow(queryClient)
      toast.success(t('devices.approveMaintenanceSuccess', 'Karbantartási kérelem jóváhagyva'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const rejectMaintenanceMutation = useMutation({
    mutationFn: (id: number) => deviceApi.rejectMaintenance(id),
    onSuccess: () => {
      invalidateMaintenanceWorkflow(queryClient)
      toast.success(t('devices.rejectMaintenanceSuccess', 'Karbantartási kérelem elutasítva'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  // Disposal Mutations
  const approveDisposalMutation = useMutation({
    mutationFn: (id: number) => deviceApi.approveDisposal(id),
    onSuccess: () => {
      invalidateDisposalWorkflow(queryClient)
      toast.success(t('devices.approveDisposalSuccess', 'Selejtezési kérelem jóváhagyva'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  const rejectDisposalMutation = useMutation({
    mutationFn: (id: number) => deviceApi.rejectDisposal(id),
    onSuccess: () => {
      invalidateDisposalWorkflow(queryClient)
      toast.success(t('devices.rejectDisposalSuccess', 'Selejtezési kérelem elutasítva'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })

  return (
    <div className="w-full space-y-4">
      {/* 3 Tab Selector */}
      <div className="flex border-b border-border">
        <button
          type="button"
          onClick={() => setActiveTab('assignments')}
          className={`flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
            activeTab === 'assignments'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <ArrowRightLeft className="h-4 w-4" />
          <span>{t('assignments.approvalTabAssignments', 'Hozzárendelések')}</span>
          {pendingAssignments.length > 0 && (
            <Badge variant="secondary" className="ml-1 px-1.5 py-0 text-xs">
              {pendingAssignments.length}
            </Badge>
          )}
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('maintenance')}
          className={`flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
            activeTab === 'maintenance'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <Wrench className="h-4 w-4 text-amber-600 dark:text-amber-400" />
          <span>{t('assignments.approvalTabMaintenance', 'Karbantartás')}</span>
          {pendingMaintenance.length > 0 && (
            <Badge variant="secondary" className="ml-1 px-1.5 py-0 text-xs">
              {pendingMaintenance.length}
            </Badge>
          )}
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('disposal')}
          className={`flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
            activeTab === 'disposal'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          <Trash2 className="h-4 w-4 text-rose-600 dark:text-rose-400" />
          <span>{t('assignments.approvalTabDisposal', 'Selejtezés')}</span>
          {pendingDisposal.length > 0 && (
            <Badge variant="secondary" className="ml-1 px-1.5 py-0 text-xs">
              {pendingDisposal.length}
            </Badge>
          )}
        </button>
      </div>

      {/* 1. Assignments Tab */}
      {activeTab === 'assignments' && (
        <div className="space-y-4">
          {isLoadingAssignments ? (
            <p className="text-muted-foreground">{t('common.loading')}...</p>
          ) : pendingAssignments.length === 0 ? (
            <p className="py-4 text-center text-muted-foreground">
              {t('assignments.noPendingAssignments', 'Nincs jóváhagyásra váró hozzárendelés')}
            </p>
          ) : (
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
                {pendingAssignments.map((a: DeviceAssignment) => {
                  const isPendingAssignment = a.status === 'PENDING_ASSIGNMENT'
                  const canActOnRow = canApproveAssignment
                  const isApproving =
                    approveAssignmentMutation.isPending &&
                    approveAssignmentMutation.variables === a.id
                  const isUnassignApproving =
                    approveUnassignmentMutation.isPending &&
                    approveUnassignmentMutation.variables === a.id
                  const isRejecting =
                    rejectAssignmentMutation.isPending &&
                    rejectAssignmentMutation.variables === a.id
                  const isRowMutating = isApproving || isUnassignApproving || isRejecting

                  return (
                    <TableRow key={a.id}>
                      <TableCell className="font-mono text-xs font-medium">
                        <div className="flex items-center gap-1.5">
                          <span>{a.device?.inventoryNumber ?? `#${a.device?.id ?? '?'}`}</span>
                          {a.device?.id && (
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-6 w-6"
                              onClick={() => a.device && navigate(`/devices/${a.device.id}`)}
                              title={t('common.details', 'Részletek')}
                            >
                              <Eye className="h-3.5 w-3.5" />
                            </Button>
                          )}
                        </div>
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
                          {isPendingAssignment && canApproveAssignment && (
                            <Button
                              size="sm"
                              variant="default"
                              disabled={isRowMutating}
                              onClick={() => approveAssignmentMutation.mutate(a.id)}
                            >
                              <Check className="mr-1 h-4 w-4" />
                              {t('assignments.approveAssignment')}
                            </Button>
                          )}

                          {!isPendingAssignment && canApproveAssignment && (
                            <Button
                              size="sm"
                              variant="default"
                              disabled={isRowMutating}
                              onClick={() => approveUnassignmentMutation.mutate(a.id)}
                            >
                              <Check className="mr-1 h-4 w-4" />
                              {t('assignments.approveUnassignment')}
                            </Button>
                          )}

                          {canActOnRow && (
                            <Button
                              size="sm"
                              variant="destructive"
                              disabled={isRowMutating}
                              onClick={() => rejectAssignmentMutation.mutate(a.id)}
                            >
                              <X className="mr-1 h-4 w-4" />
                              {t('assignments.rejectAssignment')}
                            </Button>
                          )}

                          {!canActOnRow && (
                            <span className="text-xs italic text-muted-foreground">
                              {t('common.readOnly', 'Csak megtekintés')}
                            </span>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          )}
        </div>
      )}

      {/* 2. Maintenance Tab */}
      {activeTab === 'maintenance' && (
        <div className="space-y-4">
          {isLoadingMaintenance ? (
            <p className="text-muted-foreground">{t('common.loading')}...</p>
          ) : pendingMaintenance.length === 0 ? (
            <p className="py-4 text-center text-muted-foreground">
              {t('devices.noPendingMaintenance', 'Nincs jóváhagyásra váró karbantartási kérelem')}
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('devices.inventoryNumber', 'Leltári szám')}</TableHead>
                  <TableHead>{t('devices.type', 'Típus')}</TableHead>
                  <TableHead>{t('devices.currentLocation', 'Helyszín')}</TableHead>
                  <TableHead>{t('devices.statusReason', 'Karbantartás indoka')}</TableHead>
                  <TableHead>{t('audit.timestamp', 'Időpont')}</TableHead>
                  <TableHead className="text-right">{t('common.actions', 'Műveletek')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {pendingMaintenance.map((device: Device) => {
                  const isApproving =
                    approveMaintenanceMutation.isPending &&
                    approveMaintenanceMutation.variables === device.id
                  const isRejecting =
                    rejectMaintenanceMutation.isPending &&
                    rejectMaintenanceMutation.variables === device.id
                  const isRowMutating = isApproving || isRejecting

                  return (
                    <TableRow key={device.id}>
                      <TableCell className="font-mono text-xs font-medium">
                        <div className="flex items-center gap-1.5">
                          <span>{device.inventoryNumber}</span>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6"
                            onClick={() => navigate(`/devices/${device.id}`)}
                            title={t('common.details', 'Részletek')}
                          >
                            <Eye className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      </TableCell>
                      <TableCell className="text-xs">{device.type}</TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {device.currentLocation?.name ?? '—'}
                      </TableCell>
                      <TableCell className="max-w-xs truncate text-xs font-medium text-amber-700 dark:text-amber-300">
                        {device.statusReason || '—'}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {new Date(device.updatedAt || device.createdAt).toLocaleString()}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end space-x-2">
                          {canApproveMaintenance ? (
                            <>
                              <Button
                                size="sm"
                                variant="default"
                                disabled={isRowMutating}
                                onClick={() => approveMaintenanceMutation.mutate(device.id)}
                              >
                                <Check className="mr-1 h-4 w-4" />
                                {t('devices.approveMaintenance', 'Jóváhagyás')}
                              </Button>
                              <Button
                                size="sm"
                                variant="destructive"
                                disabled={isRowMutating}
                                onClick={() => rejectMaintenanceMutation.mutate(device.id)}
                              >
                                <X className="mr-1 h-4 w-4" />
                                {t('devices.rejectMaintenance', 'Elutasítás')}
                              </Button>
                            </>
                          ) : (
                            <span className="text-xs italic text-muted-foreground">
                              {t('common.readOnly', 'Csak megtekintés')}
                            </span>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          )}
        </div>
      )}

      {/* 3. Disposal Tab */}
      {activeTab === 'disposal' && (
        <div className="space-y-4">
          {isLoadingDisposal ? (
            <p className="text-muted-foreground">{t('common.loading')}...</p>
          ) : pendingDisposal.length === 0 ? (
            <p className="py-4 text-center text-muted-foreground">
              {t('devices.noPendingDisposal', 'Nincs jóváhagyásra váró selejtezési kérelem')}
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('devices.inventoryNumber', 'Leltári szám')}</TableHead>
                  <TableHead>{t('devices.type', 'Típus')}</TableHead>
                  <TableHead>{t('devices.currentLocation', 'Helyszín')}</TableHead>
                  <TableHead>{t('devices.statusReason', 'Selejtezés indoka')}</TableHead>
                  <TableHead>{t('audit.timestamp', 'Időpont')}</TableHead>
                  <TableHead className="text-right">{t('common.actions', 'Műveletek')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {pendingDisposal.map((device: Device) => {
                  const isApproving =
                    approveDisposalMutation.isPending &&
                    approveDisposalMutation.variables === device.id
                  const isRejecting =
                    rejectDisposalMutation.isPending &&
                    rejectDisposalMutation.variables === device.id
                  const isRowMutating = isApproving || isRejecting

                  return (
                    <TableRow key={device.id}>
                      <TableCell className="font-mono text-xs font-medium">
                        <div className="flex items-center gap-1.5">
                          <span>{device.inventoryNumber}</span>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-6 w-6"
                            onClick={() => navigate(`/devices/${device.id}`)}
                            title={t('common.details', 'Részletek')}
                          >
                            <Eye className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      </TableCell>
                      <TableCell className="text-xs">{device.type}</TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {device.currentLocation?.name ?? '—'}
                      </TableCell>
                      <TableCell className="max-w-xs truncate text-xs font-medium text-rose-700 dark:text-rose-300">
                        {device.statusReason || '—'}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {new Date(device.updatedAt || device.createdAt).toLocaleString()}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end space-x-2">
                          {canApproveDisposal ? (
                            <>
                              <Button
                                size="sm"
                                variant="default"
                                disabled={isRowMutating}
                                onClick={() => approveDisposalMutation.mutate(device.id)}
                              >
                                <Check className="mr-1 h-4 w-4" />
                                {t('devices.approveDisposal', 'Jóváhagyás')}
                              </Button>
                              <Button
                                size="sm"
                                variant="destructive"
                                disabled={isRowMutating}
                                onClick={() => rejectDisposalMutation.mutate(device.id)}
                              >
                                <X className="mr-1 h-4 w-4" />
                                {t('devices.rejectDisposal', 'Elutasítás')}
                              </Button>
                            </>
                          ) : (
                            <span className="text-xs italic text-muted-foreground">
                              {t('common.readOnly', 'Csak megtekintés')}
                            </span>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          )}
        </div>
      )}
    </div>
  )
}
