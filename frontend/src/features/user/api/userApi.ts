import { apiClient } from '@/lib/api/axios'

export type RoleName = 'ROLE_ADMIN' | 'ROLE_TEACHER' | 'ROLE_STUDENT'

export interface AppUserDto {
  id: number
  /** Visszafejtett email cím. */
  email: string
  /** Maszkolt email: "a***@tanszek.local" */
  emailMasked?: string
  /** SHA-256 hex az emailből (egyediség + gyors keresés). */
  emailHash?: string
  active: boolean
  mustChangePassword: boolean
  failedLoginCount?: number
  lockedUntil?: string | null
  role?: {
    id: number
    name: RoleName
  }
  officeLocation?: {
    id: number
    name: string
    type: string
  } | null
}

export interface CreateUserPayload {
  email: string
  role: RoleName
  active?: boolean
}

export interface UpdateUserPayload {
  firstName?: string
  lastName?: string
  role?: RoleName
  officeLocationId?: number | null
  clearOfficeLocation?: boolean
  active?: boolean
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export const userApi = {
  findAll: async (params?: { page?: number; size?: number }): Promise<PageResponse<AppUserDto>> => {
    const response = await apiClient.get<PageResponse<AppUserDto>>('/api/users', { params })
    return response.data
  },
  findById: async (id: number): Promise<AppUserDto> => {
    const response = await apiClient.get<AppUserDto>(`/api/users/${id}`)
    return response.data
  },
  create: async (payload: CreateUserPayload): Promise<AppUserDto> => {
    const response = await apiClient.post<AppUserDto>('/api/users', payload)
    return response.data
  },
  update: async (id: number, payload: UpdateUserPayload): Promise<AppUserDto> => {
    const response = await apiClient.put<AppUserDto>(`/api/users/${id}`, payload)
    return response.data
  },
  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/users/${id}`)
  },
  unlock: async (id: number): Promise<void> => {
    await apiClient.post(`/api/users/${id}/unlock`)
  },
}
