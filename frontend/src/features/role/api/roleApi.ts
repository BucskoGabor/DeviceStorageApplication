import { apiClient } from '@/lib/api/axios'

export interface PermissionDto {
  id: number
  name: string
}

export interface RoleDto {
  id: number
  name: string
  permissions: PermissionDto[]
}

export interface CreateRolePayload {
  name: string
  permissionIds: number[]
}

export interface UpdateRolePayload {
  name?: string
  permissionIds?: number[]
}

export const roleApi = {
  findAll: async (): Promise<RoleDto[]> => {
    const response = await apiClient.get<RoleDto[]>('/api/roles')
    return response.data
  },
  findById: async (id: number): Promise<RoleDto> => {
    const response = await apiClient.get<RoleDto>(`/api/roles/${id}`)
    return response.data
  },
  create: async (payload: CreateRolePayload): Promise<RoleDto> => {
    const response = await apiClient.post<RoleDto>('/api/roles', payload)
    return response.data
  },
  update: async (id: number, payload: UpdateRolePayload): Promise<RoleDto> => {
    const response = await apiClient.put<RoleDto>(`/api/roles/${id}`, payload)
    return response.data
  },
  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/roles/${id}`)
  },
  getPermissions: async (): Promise<PermissionDto[]> => {
    const response = await apiClient.get<PermissionDto[]>('/api/permissions')
    return response.data
  },
}
