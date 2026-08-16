import { apiClient } from '@/lib/api/axios'

/**
 * Assignment API — device assignment workflow endpointok.
 *
 * State machine:
 *   IN_STORAGE → PENDING_ASSIGNMENT → ASSIGNED → PENDING_UNASSIGNMENT → IN_STORAGE
 */

export type AssignmentStatus =
  | 'IN_STORAGE'
  | 'ASSIGNED'
  | 'PENDING_ASSIGNMENT'
  | 'PENDING_UNASSIGNMENT'
  | 'REJECTED'

export interface DeviceAssignment {
  id: number
  device?: { id: number; inventoryNumber?: string; type?: string }
  fromLocation?: { id: number; name?: string } | null
  toLocation?: { id: number; name?: string } | null
  fromUser?: { id: number; email?: string } | null
  toUser?: { id: number; email?: string } | null
  createdByUser?: { id: number; email?: string }
  approvedBy?: { id: number; email?: string } | null
  unassignedBy?: { id: number; email?: string } | null
  unassignApprovedBy?: { id: number; email?: string } | null
  dateOfAssignment?: string | null
  createdDate: string
  unassignDate?: string | null
  unassignCreatedDate?: string | null
  status: AssignmentStatus
  active: boolean
}

export interface CreateAssignmentPayload {
  targetLocationId?: number | null
  targetUserId?: number | null
}

/**
 * POST /api/devices/{deviceId}/assignments
 * Assign kérés létrehozása (PENDING_ASSIGNMENT).
 */
export async function requestAssignment(
  deviceId: number,
  payload: CreateAssignmentPayload
): Promise<DeviceAssignment> {
  const { data } = await apiClient.post<DeviceAssignment>(
    `/api/devices/${deviceId}/assignments`,
    payload
  )
  return data
}

/**
 * POST /api/devices/assignments/{assignmentId}/approve
 * PENDING_ASSIGNMENT → ASSIGNED jóváhagyása.
 */
export async function approveAssignment(assignmentId: number): Promise<DeviceAssignment> {
  const { data } = await apiClient.post<DeviceAssignment>(
    `/api/devices/assignments/${assignmentId}/approve`
  )
  return data
}

/**
 * POST /api/devices/assignments/{assignmentId}/reject
 * Függőben lévő kérelem (assign/unassign) elutasítása.
 */
export async function rejectAssignment(assignmentId: number): Promise<DeviceAssignment> {
  const { data } = await apiClient.post<DeviceAssignment>(
    `/api/devices/assignments/${assignmentId}/reject`
  )
  return data
}

/**
 * POST /api/devices/assignments/{assignmentId}/unassign
 * Aktív assignment visszavételi kérése (PENDING_UNASSIGNMENT).
 */
export async function requestUnassignment(assignmentId: number, targetLocationId?: number): Promise<DeviceAssignment> {
  const { data } = await apiClient.post<DeviceAssignment>(
    `/api/devices/assignments/${assignmentId}/unassign`, null, { params: targetLocationId ? { targetLocationId } : undefined }
  )
  return data
}

/**
 * POST /api/devices/assignments/{unassignmentId}/approve-unassign
 * PENDING_UNASSIGNMENT → IN_STORAGE jóváhagyása.
 */
export async function approveUnassignment(unassignmentId: number): Promise<DeviceAssignment> {
  const { data } = await apiClient.post<DeviceAssignment>(
    `/api/devices/assignments/${unassignmentId}/approve-unassign`
  )
  return data
}

/**
 * GET /api/devices/{deviceId}/assignments
 * Device összes assignment history-ja (lapozva, createdDate desc).
 */
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export async function findAssignmentsByDevice(
  deviceId: number,
  params: { page?: number; size?: number } = {}
): Promise<PageResponse<DeviceAssignment>> {
  const { data } = await apiClient.get<PageResponse<DeviceAssignment>>(
    `/api/devices/${deviceId}/assignments`,
    { params }
  )
  return data
}

/**
 * GET /api/assignments/pending
 * Jóváhagyásra váró assignment-ek listája.
 */
export async function findPendingAssignments(): Promise<DeviceAssignment[]> {
  const { data } = await apiClient.get<DeviceAssignment[]>(`/api/assignments/pending`)
  return data
}

export const assignmentApi = {
  requestAssignment,
  approveAssignment,
  rejectAssignment,
  requestUnassignment,
  approveUnassignment,
  findAssignmentsByDevice,
  findPendingAssignments,
}
