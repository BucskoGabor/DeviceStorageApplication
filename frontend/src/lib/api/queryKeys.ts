export const deviceKeys = {
  all: ['devices'] as const,
  lists: () => [...deviceKeys.all, 'list'] as const,
  list: (f: Record<string, unknown>) => [...deviceKeys.lists(), f] as const,
  details: () => [...deviceKeys.all, 'detail'] as const,
  detail: (id: number) => [...deviceKeys.details(), id] as const,
}

export const assignmentKeys = {
  all: ['assignments'] as const,
  byDevice: (id: number) => [...assignmentKeys.all, 'device', id] as const,
  pending: () => [...assignmentKeys.all, 'pending'] as const,
}

export const maintenanceKeys = {
  all: ['maintenance'] as const,
  pending: () => [...maintenanceKeys.all, 'pending'] as const,
}

export const disposalKeys = {
  all: ['disposal'] as const,
  pending: () => [...disposalKeys.all, 'pending'] as const,
}

export const locationKeys = {
  all: ['locations'] as const,
  lists: () => [...locationKeys.all, 'list'] as const,
  list: (f: Record<string, unknown>) => [...locationKeys.lists(), f] as const,
  tree: () => [...locationKeys.all, 'tree'] as const,
  details: () => [...locationKeys.all, 'detail'] as const,
  detail: (id: number) => [...locationKeys.details(), id] as const,
  devices: (id: number) => [...locationKeys.all, 'devices', id] as const,
  history: (id: number) => [...locationKeys.all, 'history', id] as const,
}

export const userKeys = {
  all: ['users'] as const,
  lists: () => [...userKeys.all, 'list'] as const,
  list: (f: Record<string, unknown>) => [...userKeys.lists(), f] as const,
  details: () => [...userKeys.all, 'detail'] as const,
  detail: (id: number) => [...userKeys.details(), id] as const,
  devices: (id: number) => [...userKeys.all, 'devices', id] as const,
  history: (id: number) => [...userKeys.all, 'history', id] as const,
}

export const authKeys = {
  me: () => ['auth', 'me'] as const,
}

export const softwareKeys = {
  all: ['software'] as const,
  lists: () => [...softwareKeys.all, 'list'] as const,
  list: (f: Record<string, unknown>) => [...softwareKeys.lists(), f] as const,
  devices: (id: number) => [...softwareKeys.all, 'devices', id] as const,
}

export const auditKeys = {
  all: ['audit'] as const,
  list: (f: Record<string, unknown>) => [...auditKeys.all, 'list', f] as const,
}

export const roleKeys = {
  all: ['roles'] as const,
  permissions: () => ['permissions'] as const,
}
