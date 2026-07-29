import { apiClient } from '@/lib/api/axios'
import { SizeLimits } from '@/lib/api/sizeLimits'

/**
 * Import API — Excel feltöltés, preview, execute.
 *
 * A backend ImportController endpoint-jait hívja:
 * - POST /api/import/preview (multipart/form-data)
 * - POST /api/import/execute (JSON body: ImportPreviewResponse)
 */

export interface ImportUserRow {
  email: string
  firstName: string
  lastName: string
  role: string
  active?: boolean | null
  officeLocationName?: string | null
}

export interface ImportDeviceRow {
  inventoryNumber: string
  type: string
  status: string
  locationName?: string | null
}

export interface InvalidRow {
  rowNumber: number
  entityType: string
  rawData: string
  errors: string[]
}

export interface ImportPreviewResponse {
  totalRows: number
  validUsers: ImportUserRow[]
  validDevices: ImportDeviceRow[]
  invalidRows: InvalidRow[]
}

export interface ImportResult {
  usersInserted: number
  usersUpdated: number
  devicesInserted: number
  devicesUpdated: number
  errors: number
}

/**
 * Excel fájl preview — feltöltés + validáció szárazon.
 *
 * A backend válasza ImportPreviewResponse.
 * Hibás sorok a response.invalidRows-ban jelennek meg.
 */
export async function previewImport(file: File): Promise<ImportPreviewResponse> {
  if (file.size > SizeLimits.VERY_LONG_TEXT_MAX) {
    throw new Error('File too large for preview (max 10MB)')
  }

  const formData = new FormData()
  formData.append('file', file)

  const response = await apiClient.post<ImportPreviewResponse>('/api/import/preview', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return response.data
}

/**
 * Import tényleges végrehajtása a preview response alapján.
 *
 * A backend idempotens UPDATE-or-SKIP logikát futtat (email_hash / inventory_number).
 */
export async function executeImport(preview: ImportPreviewResponse): Promise<ImportResult> {
  const response = await apiClient.post<ImportResult>('/api/import/execute', preview)
  return response.data
}

/**
 * Import API namespace.
 */
export const importApi = {
  preview: previewImport,
  execute: executeImport,
}

export default importApi
