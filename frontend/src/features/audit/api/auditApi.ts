import { apiClient } from '@/lib/api/axios'

/**
 * Audit API — audit log lista + rollback.
 */
export interface AuditLog {
  id: number
  timestamp: string
  userEmail: string
  endpoint: string
  method: string
  requestPayload: string | null
  changesJson: string | null
  httpStatus: number
  entityType: string | null
  entityId: number | null
  createdAt: string
  updatedAt: string
}

/**
 * Audit log lista (lapozva, opcionális szűrő userEmail/entityType/entityId).
 */
export async function findAll(params: {
  page: number
  size: number
  userEmail?: string
  entityType?: string
  entityId?: number
}): Promise<{
  content: AuditLog[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}> {
  const response = await apiClient.get('/api/audit', { params })
  return response.data
}

/**
 * Audit log rollback (POST /api/audit/rollback/{id}).
 */
export async function rollback(auditLogId: number): Promise<AuditLog> {
  const response = await apiClient.post<AuditLog>(`/api/audit/rollback/${auditLogId}`)
  return response.data
}

export const auditApi = { findAll, rollback }
