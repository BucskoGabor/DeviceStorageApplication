import { apiClient } from '@/lib/api/axios'

/**
 * Device API — eszköz CRUD + assignment műveletek.
 */

export interface Device {
  id: number
  type: string
  inventoryNumber: string
  status: 'PENDING' | 'ASSIGNED' | 'IN_STORAGE' | 'MAINTENANCE' | 'DISPOSED'
  createdAt: string
  updatedAt: string
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
 * Device API namespace.
 */
export const deviceApi = {
  findAll: findAllDevices,
  findById: findDeviceById,
  create: createDevice,
  update: updateDevice,
  delete: deleteDevice,
}

export default deviceApi
