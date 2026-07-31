import { apiClient } from '@/lib/api/axios'

/**
 * Device API — eszköz CRUD + assignment + software kapcsolat műveletek.
 */

export interface Device {
  id: number
  type: string
  inventoryNumber: string
  status: 'PENDING' | 'ASSIGNED' | 'IN_STORAGE' | 'MAINTENANCE' | 'DISPOSED'
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
  const response = await apiClient.get<PageResponse<Device>>('/api/devices', { params })
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
export async function createDevice(device: Omit<Device, 'id' | 'createdAt' | 'updatedAt'>): Promise<Device> {
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
export async function attachSoftware(deviceId: number, softwareId: number): Promise<DeviceSoftware> {
  const response = await apiClient.post<DeviceSoftware>(
    `/api/devices/${deviceId}/software`,
    { softwareId }
  )
  return response.data
}

/**
 * Szoftver leválasztása egy device-ról.
 */
export async function detachSoftware(deviceId: number, softwareId: number): Promise<void> {
  await apiClient.delete(`/api/devices/${deviceId}/software/${softwareId}`)
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
}

/**
 * Státusz váltás (operatív/admin beavatkozás).
 *
 * <p>A backend state machine validációt futtat — nem minden átmenet megengedett.
 * DISPOSED végállapot. Részletek: {@code DeviceService.changeStatus()}.
 */
export async function changeStatus(
  deviceId: number,
  status: Device['status']
): Promise<Device> {
  const response = await apiClient.patch<Device>(`/api/devices/${deviceId}/status`, {
    status,
  })
  return response.data
}

export default deviceApi
