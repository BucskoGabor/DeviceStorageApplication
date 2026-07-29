import { apiClient } from '@/lib/api/axios'
import { SizeLimits } from '@/lib/api/sizeLimits'

/**
 * Attachment API — device-ökhöz csatolt fájlok feltöltése / listázása / törlése.
 */
export interface DeviceAttachment {
  id: number
  deviceId: number
  fileName: string
  mimeType: string
  sizeBytes: number
  uploadedAt: string
  uploadedById: number
  storagePath: string
}

/**
 * Device összes attachment listája.
 */
export async function findByDevice(deviceId: number): Promise<DeviceAttachment[]> {
  const response = await apiClient.get<DeviceAttachment[]>(`/api/devices/${deviceId}/attachments`)
  return response.data
}

/**
 * Fájl feltöltése egy device-hoz (multipart/form-data).
 * Backend endpoint: POST /api/devices/{deviceId}/attachments
 */
export async function upload(
  deviceId: number,
  file: File
): Promise<DeviceAttachment> {
  if (file.size > 5 * 1024 * 1024) {
    throw new Error('File too large (max 5MB)')
  }

  const formData = new FormData()
  formData.append('file', file)

  const response = await apiClient.post<DeviceAttachment>(
    `/api/devices/${deviceId}/attachments`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  )
  return response.data
}

/**
 * Attachment törlése ID alapján.
 */
export async function deleteAttachment(attachmentId: number): Promise<void> {
  await apiClient.delete(`/api/attachments/${attachmentId}`)
}

/**
 * Formázott fájlméret megjelenítés (pl. "1.5 MB").
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export const attachmentApi = {
  findByDevice,
  upload,
  delete: deleteAttachment,
  formatFileSize,
}
