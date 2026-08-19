import { apiClient } from '@/lib/api/axios'

/**
 * Software API — szoftver CRUD + licence key műveletek.
 *
 * Licence key maszkolás (F3):
 * - Ha a user rendelkezik SOFTWARE_LICENSE_VIEW permissionnel: `licenseKey` tartalmazza a visszafejtett értéket.
 * - Egyébként: `licenseKeyMasked` maszkolt formátumban (****-****-****-XXXX).
 * - A két mező közül pontosan az egyik kitöltött.
 */

export interface Software {
  id: number
  name: string
  licenseKey?: string | null
  licenseKeyMasked?: string | null
  installedDeviceCount?: number
  deviceInventoryNumbers?: string[]
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface CreateSoftwarePayload {
  name: string
  licenseKey: string
}

export interface UpdateSoftwarePayload {
  name?: string
  licenseKey?: string
}

export async function findAllSoftware(
  params: { page?: number; size?: number } = {}
): Promise<PageResponse<Software>> {
  const response = await apiClient.get<PageResponse<Software>>('/api/software', { params })
  return response.data
}

export async function createSoftware(payload: CreateSoftwarePayload): Promise<Software> {
  const response = await apiClient.post<Software>('/api/software', payload)
  return response.data
}

export async function updateSoftware(
  id: number,
  payload: UpdateSoftwarePayload
): Promise<Software> {
  const response = await apiClient.put<Software>(`/api/software/${id}`, payload)
  return response.data
}

export async function deleteSoftware(id: number): Promise<void> {
  await apiClient.delete(`/api/software/${id}`)
}

export interface DeviceSummary {
  id: number
  inventoryNumber: string
  type: string
  status: string
}

export async function findDevicesBySoftware(softwareId: number): Promise<DeviceSummary[]> {
  const response = await apiClient.get<DeviceSummary[]>(`/api/software/${softwareId}/devices`)
  return response.data
}

export const softwareApi = {
  findAll: findAllSoftware,
  create: createSoftware,
  update: updateSoftware,
  delete: deleteSoftware,
  findDevicesBySoftware,
}

export default softwareApi
