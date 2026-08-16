import { apiClient } from '@/lib/api/axios'

/**
 * Device API — eszköz CRUD + assignment + software kapcsolat műveletek.
 */

export interface Device {
  id: number
  type: string
  inventoryNumber: string
  status:
    | 'PENDING'
    | 'ASSIGNED'
    | 'IN_STORAGE'
    | 'PENDING_MAINTENANCE'
    | 'MAINTENANCE'
    | 'PENDING_DISPOSAL'
    | 'DISPOSED'
  statusReason?: string | null
  previousStatus?: string | null
  currentLocation?: {
    id: number
    name: string
    type: 'STORAGE' | 'OFFICE' | 'GROUP'
  } | null
  createdAt: string
  updatedAt: string
}

export interface DeviceSoftware {
  id: number
  name: string
  licenseKey?: string | null
  licenseKeyMasked?: string | null
}

export interface PageRequest {
  page: number
  size: number
  sort?: string
  status?: string
  type?: string
  inventoryNumber?: string
  filter?: Record<string, string>
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

/**
 * Device lista (lapozva és szűrve).
 */
export async function findAllDevices(params: PageRequest): Promise<PageResponse<Device>> {
  const { filter, ...queryParams } = params
  const mergedParams = {
    ...queryParams,
    ...(filter?.inventoryNumber ? { inventoryNumber: filter.inventoryNumber } : {}),
  }
  const response = await apiClient.get<PageResponse<Device>>('/api/devices', {
    params: mergedParams,
  })
  return response.data
}

/**
 * Device keresése ID alapján.
 */
export async function findDeviceById(id: number): Promise<Device> {
  const response = await apiClient.get<Device>(`/api/devices/${id}`)
  return response.data
}

/**
 * Új device létrehozása.
 */
export async function createDevice(
  device: Omit<Device, 'id' | 'createdAt' | 'updatedAt'>
): Promise<Device> {
  const response = await apiClient.post<Device>('/api/devices', device)
  return response.data
}

/**
 * Device módosítása.
 */
export async function updateDevice(id: number, device: Partial<Device>): Promise<Device> {
  const response = await apiClient.put<Device>(`/api/devices/${id}`, device)
  return response.data
}

/**
 * Device törlése.
 */
export async function deleteDevice(id: number): Promise<void> {
  await apiClient.delete(`/api/devices/${id}`)
}

/**
 * Device összes telepített szoftvere.
 */
export async function findSoftwareByDevice(deviceId: number): Promise<DeviceSoftware[]> {
  const response = await apiClient.get<DeviceSoftware[]>(`/api/devices/${deviceId}/software`)
  return response.data
}

/**
 * Szoftver hozzárendelése egy device-hoz.
 */
export async function attachSoftware(
  deviceId: number,
  softwareId: number
): Promise<DeviceSoftware> {
  const response = await apiClient.post<DeviceSoftware>(`/api/devices/${deviceId}/software`, {
    softwareId,
  })
  return response.data
}

/**
 * Szoftver leválasztása egy device-ról.
 */
export async function detachSoftware(deviceId: number, softwareId: number): Promise<void> {
  await apiClient.delete(`/api/devices/${deviceId}/software/${softwareId}`)
}

/**
 * Karbantartás kérése.
 */
export async function requestMaintenance(deviceId: number, reason?: string): Promise<Device> {
  const response = await apiClient.post<Device>(`/api/devices/${deviceId}/maintenance/request`, {
    reason,
  })
  return response.data
}

/**
 * Karbantartás jóváhagyása.
 */
export async function approveMaintenance(deviceId: number): Promise<Device> {
  const response = await apiClient.post<Device>(`/api/devices/${deviceId}/maintenance/approve`)
  return response.data
}

/**
 * Karbantartás elutasítása.
 */
export async function rejectMaintenance(deviceId: number): Promise<Device> {
  const response = await apiClient.post<Device>(`/api/devices/${deviceId}/maintenance/reject`)
  return response.data
}

/**
 * Eszköz visszavétele karbantartásból.
 */
export async function returnFromMaintenance(deviceId: number): Promise<Device> {
  const response = await apiClient.post<Device>(`/api/devices/${deviceId}/return-from-maintenance`)
  return response.data
}

/**
 * Selejtezés kérése.
 */
export async function requestDisposal(deviceId: number, reason?: string): Promise<Device> {
  const response = await apiClient.post<Device>(`/api/devices/${deviceId}/dispose/request`, {
    reason,
  })
  return response.data
}

/**
 * Selejtezés jóváhagyása.
 */
export async function approveDisposal(deviceId: number): Promise<Device> {
  const response = await apiClient.post<Device>(`/api/devices/${deviceId}/dispose/approve`)
  return response.data
}

/**
 * Selejtezés elutasítása.
 */
export async function rejectDisposal(deviceId: number): Promise<Device> {
  const response = await apiClient.post<Device>(`/api/devices/${deviceId}/dispose/reject`)
  return response.data
}

/**
 * Függő karbantartási kérelmek listázása.
 */
export async function findPendingMaintenance(): Promise<Device[]> {
  const response = await apiClient.get<Device[]>('/api/devices/pending-maintenance')
  return response.data
}

/**
 * Függő selejtezési kérelmek listázása.
 */
export async function findPendingDisposal(): Promise<Device[]> {
  const response = await apiClient.get<Device[]>('/api/devices/pending-disposal')
  return response.data
}

/**
 * Státusz váltás (operatív/admin beavatkozás).
 */
export async function changeStatus(deviceId: number, status: Device['status']): Promise<Device> {
  const response = await apiClient.patch<Device>(`/api/devices/${deviceId}/status`, {
    status,
  })
  return response.data
}

/**
 * Device API namespace.
 */
export const deviceApi = {
  findAll: findAllDevices,
  findById: findDeviceById,
  create: createDevice,
  update: updateDevice,
  delete: deleteDevice,
  findSoftwareByDevice,
  attachSoftware,
  detachSoftware,
  changeStatus,
  requestMaintenance,
  approveMaintenance,
  rejectMaintenance,
  returnFromMaintenance,
  requestDisposal,
  approveDisposal,
  rejectDisposal,
  findPendingMaintenance,
  findPendingDisposal,
  // Aliasok a meglévő hivatkozásokhoz
  sendToMaintenance: requestMaintenance,
  disposeDevice: requestDisposal,
}

export default deviceApi
