import { apiClient } from '@/lib/api/axios'

import type { Device } from '@/features/device/api/deviceApi'
import type { DeviceAssignment } from '@/features/assignment/api/assignmentApi'

export interface PermissionDto {
  id: number
  name: string
}

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
    name: string
    permissions?: PermissionDto[]
  }
  directPermissions?: PermissionDto[]
  effectivePermissions?: string[]
  officeLocationSummary?: {
    id: number
    name: string
    type: string
  } | null
  officeLocation?: {
    id: number
    name: string
    type: string
  } | null
  createdAt?: string
  updatedAt?: string
}

export interface CreateUserPayload {
  email: string
  role: string
  initialPassword?: string
  active?: boolean
  directPermissionIds?: number[]
}

export interface UpdateUserPayload {
  role?: string
  officeLocationId?: number | null
  clearOfficeLocation?: boolean
  active?: boolean
  directPermissionIds?: number[]
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
  findCurrentDevices: async (id: number): Promise<Device[]> => {
    const response = await apiClient.get<Device[]>(`/api/users/${id}/devices`)
    return response.data
  },
  findAssignmentHistory: async (id: number): Promise<DeviceAssignment[]> => {
    const response = await apiClient.get<DeviceAssignment[]>(`/api/users/${id}/assignments`)
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
  resetPassword: async (id: number): Promise<string> => {
    const response = await apiClient.post<{ newPassword: string }>(
      `/api/users/${id}/reset-password`
    )
    return response.data.newPassword
  },
}
