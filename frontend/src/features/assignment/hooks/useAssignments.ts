import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { assignmentApi, type DeviceAssignment, type CreateAssignmentPayload } from '../api/assignmentApi'

/**
 * useAssignmentsByDevice — egy device teljes assignment history lekérdezése.
 */
export function useAssignmentsByDevice(deviceId: number) {
  return useQuery({
    queryKey: ['assignments', deviceId],
    queryFn: () => assignmentApi.findAssignmentsByDevice(deviceId, { page: 0, size: 50 }),
    enabled: Number.isFinite(deviceId),
  })
}

/**
 * usePendingAssignments — jóváhagyásra váró assignment lista.
 */
export function usePendingAssignments() {
  return useQuery({
    queryKey: ['pending-assignments'],
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
      queryClient.invalidateQueries({ queryKey: ['assignments', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['pending-assignments'] })
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      toast.success(t('assignments.requestAssignmentSuccess'))
      onSuccess?.()
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })
}

/**
 * useApproveAssignment — POST /api/devices/assignments/{id}/approve
 */
export function useApproveAssignment(deviceId: number) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (assignmentId: number) => assignmentApi.approveAssignment(assignmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assignments', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['pending-assignments'] })
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      toast.success(t('assignments.approveAssignmentSuccess'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })
}

/**
 * useRequestUnassignment — POST /api/devices/assignments/{id}/unassign
 */
export function useRequestUnassignment(deviceId: number) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (assignmentId: number) => assignmentApi.requestUnassignment(assignmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assignments', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['pending-assignments'] })
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      toast.success(t('assignments.requestUnassignmentSuccess'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
    },
  })
}

/**
 * useApproveUnassignment — POST /api/devices/assignments/{id}/approve-unassign
 */
export function useApproveUnassignment(deviceId: number) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (unassignmentId: number) => assignmentApi.approveUnassignment(unassignmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assignments', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['pending-assignments'] })
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      toast.success(t('assignments.approveUnassignmentSuccess'))
    },
    onError: (error: any) => {
      const messageKey = error.response?.data?.messageKey ?? 'internalError'
      toast.error(t(messageKey, { defaultValue: t('common.error') }))
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
