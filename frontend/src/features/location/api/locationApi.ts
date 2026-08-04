import { apiClient } from '@/lib/api/axios'

export interface Location {
  id: number
  name: string
  parentId?: number | null
  type: 'CLASSROOM' | 'OFFICE' | 'STORAGE' | 'GROUP'
  version?: number
}

export interface LocationTreeNode {
  id: number
  name: string
  type: Location['type']
  parentId: number | null
  depth: number
  children: LocationTreeNode[]
}

export interface CreateLocationPayload {
  name: string
  type: Location['type']
  parentId?: number | null
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

/**
 * Location API client.
 */
export const locationApi = {
  findAll: async (params?: { page?: number; size?: number }): Promise<PageResponse<Location>> => {
    const response = await apiClient.get<PageResponse<Location>>('/api/locations', { params })
    return response.data
  },

  /**
   * Teljes hierarchikus fa.
   * Max depth 10 — efelett a node-ok üres children listát kapnak.
   */
  findTree: async (): Promise<LocationTreeNode[]> => {
    const response = await apiClient.get<LocationTreeNode[]>('/api/locations/tree')
    return response.data
  },

  /**
   * Root helyszínek (parent == null).
   */
  findRoots: async (): Promise<Location[]> => {
    const response = await apiClient.get<Location[]>('/api/locations/roots')
    return response.data
  },

  /**
   * Helyszínek típus szerint.
   */
  findByType: async (type: Location['type']): Promise<Location[]> => {
    const response = await apiClient.get<Location[]>(`/api/locations/by-type/${type}`)
    return response.data
  },

  create: async (payload: CreateLocationPayload): Promise<Location> => {
    const response = await apiClient.post<Location>('/api/locations', payload)
    return response.data
  },
  update: async (id: number, payload: Partial<CreateLocationPayload>): Promise<Location> => {
    const response = await apiClient.put<Location>(`/api/locations/${id}`, payload)
    return response.data
  },
  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/locations/${id}`)
  },
}
