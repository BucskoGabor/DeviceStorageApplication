import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { resolveToastMessage } from '@/lib/utils/toastUtils'
import {
  assignmentApi,
  type DeviceAssignment,
  type CreateAssignmentPayload,
} from '../api/assignmentApi'
import { assignmentKeys } from '@/lib/api/queryKeys'
import { invalidateAssignmentWorkflow } from '@/lib/api/invalidation'

/**
 * useAssignmentsByDevice — egy device teljes assignment history lekérdezése.
 */
export function useAssignmentsByDevice(deviceId: number) {
  return useQuery({
    queryKey: assignmentKeys.byDevice(deviceId),
    queryFn: () => assignmentApi.findAssignmentsByDevice(deviceId, { page: 0, size: 50 }),
    enabled: Number.isFinite(deviceId),
  })
}

/**
 * usePendingAssignments — jóváhagyásra váró assignment lista.
 */
export function usePendingAssignments() {
  return useQuery({
    queryKey: assignmentKeys.pending(),
    queryFn: () => assignmentApi.findPendingAssignments(),
    refetchInterval: 30000,
  })
}

/**
 * useRequestAssignment — POST /api/devices/{deviceId}/assignments
 */
export function useRequestAssignment(deviceId: number, onSuccess?: () => void) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CreateAssignmentPayload) =>
      assignmentApi.requestAssignment(deviceId, payload),
    onSuccess: () => {
      invalidateAssignmentWorkflow(queryClient)
      toast.success(t('assignments.requestAssignmentSuccess'))
      onSuccess?.()
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })
}

/**
 * useApproveAssignment — POST /api/devices/assignments/{id}/approve
 */
export function useApproveAssignment(_deviceId?: number) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (assignmentId: number) => assignmentApi.approveAssignment(assignmentId),
    onSuccess: () => {
      invalidateAssignmentWorkflow(queryClient)
      toast.success(t('assignments.approveAssignmentSuccess'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })
}

/**
 * useRequestUnassignment — POST /api/devices/assignments/{id}/unassign
 */
export function useRequestUnassignment(_deviceId?: number) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      assignmentId,
      targetLocationId,
    }: {
      assignmentId: number
      targetLocationId?: number
    }) => assignmentApi.requestUnassignment(assignmentId, targetLocationId),
    onSuccess: () => {
      invalidateAssignmentWorkflow(queryClient)
      toast.success(t('assignments.requestUnassignmentSuccess'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })
}

/**
 * useApproveUnassignment — POST /api/devices/assignments/{id}/approve-unassign
 */
export function useApproveUnassignment(_deviceId?: number) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (unassignmentId: number) => assignmentApi.approveUnassignment(unassignmentId),
    onSuccess: () => {
      invalidateAssignmentWorkflow(queryClient)
      toast.success(t('assignments.approveUnassignmentSuccess'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })
}

/**
 * useRejectAssignment — POST /api/devices/assignments/{id}/reject
 */
export function useRejectAssignment(_deviceId?: number) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (assignmentId: number) => assignmentApi.rejectAssignment(assignmentId),
    onSuccess: () => {
      invalidateAssignmentWorkflow(queryClient)
      toast.success(t('assignments.rejectAssignmentSuccess'))
    },
    onError: (error: any) => {
      toast.error(resolveToastMessage(error.response))
    },
  })
}

/**
 * useDeviceCurrentAssignment — a device aktuális (active=true) assignmentje a listából.
 */
export function useDeviceCurrentAssignment(deviceId: number): DeviceAssignment | undefined {
  const { data } = useAssignmentsByDevice(deviceId)
  return data?.content.find((a) => a.active)
}
