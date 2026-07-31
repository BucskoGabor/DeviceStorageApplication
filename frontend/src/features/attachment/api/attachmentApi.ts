import { apiClient } from '@/lib/api/axios'
import type { AxiosProgressEvent } from 'axios'

/**
 * Attachment API — device-ökhöz csatolt fájlok feltöltése / listázása / törlése / letöltése.
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
 * Fájl feltöltési folyamat callback.
 *
 * <p>Az axios {@code onUploadProgress} event-ből jön — a {@code progressEvent.loaded}
 * és {@code progressEvent.total} mezők használhatók a százalékos haladáshoz.
 */
export type UploadProgressCallback = (progressEvent: AxiosProgressEvent) => void

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
 *
 * @param deviceId a cél device azonosítója
 * @param file a feltöltendő fájl
 * @param onProgress opcionális callback a feltöltési haladásról
 */
export async function upload(
  deviceId: number,
  file: File,
  onProgress?: UploadProgressCallback
): Promise<DeviceAttachment> {
  if (file.size > 5 * 1024 * 1024) {
    throw new Error('File too large (max 5MB)')
  }

  const formData = new FormData()
  formData.append('file', file)

  const response = await apiClient.post<DeviceAttachment>(
    `/api/devices/${deviceId}/attachments`,
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: onProgress,
    }
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
 * Attachment letöltési URL-je (Content-Disposition: attachment).
 *
 * <p>Az axios interceptor automatikusan hozzáadja a Bearer tokent és
 * a CSRF headert, így a böngésző fetch-e proxy-n át megy. A letöltés
 * triggereléséhez {@code window.open(downloadUrl, '_blank')} vagy
 * egy anchor tag használható.
 */
export function downloadUrl(attachmentId: number): string {
  return `/api/attachments/${attachmentId}/file?inline=false`
}

/**
 * Attachment inline preview URL-je (Content-Disposition: inline).
 *
 * <p>Csak image/* és application/pdf mime típusokhoz érdemes használni —
 * ezeket a böngésző natívan meg tudja jeleníteni. Más típusoknál (pl. .docx)
 * a böngésző letöltést ajánl fel.
 */
export function previewUrl(attachmentId: number): string {
  return `/api/attachments/${attachmentId}/file?inline=true`
}

/**
 * Megállapítja, hogy egy attachment böngészőben inline megjeleníthető-e.
 */
export function canPreview(mimeType: string): boolean {
  return (
    mimeType.startsWith('image/') ||
    mimeType === 'application/pdf' ||
    mimeType.startsWith('text/')
  )
}

/**
 * Formázott fájlméret megjelenítés (pl. "1.5 MB").
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * Százalékos érték kiszámítása (0-100).
 */
export function calculateProgress(loaded: number, total: number): number {
  if (total <= 0) return 0
  return Math.min(100, Math.round((loaded / total) * 100))
}

export const attachmentApi = {
  findByDevice,
  upload,
  delete: deleteAttachment,
  formatFileSize,
  downloadUrl,
  previewUrl,
  canPreview,
  calculateProgress,
}
