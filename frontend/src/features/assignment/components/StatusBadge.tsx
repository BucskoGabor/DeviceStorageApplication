import { useTranslation } from 'react-i18next'
import type { AssignmentStatus } from '../api/assignmentApi'
import type { Device } from '@/features/device/api/deviceApi'

type AnyStatus = AssignmentStatus | Device['status'] | string

interface StatusBadgeProps {
  status: AnyStatus
}

/**
 * StatusBadge — Assignment és Device státusz színes badge.
 */
export function StatusBadge({ status }: StatusBadgeProps) {
  const { t } = useTranslation()
  const variant = getVariant(status)
  const label = getLabel(status, t)

  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${variant}`}
    >
      {label}
    </span>
  )
}

function getLabel(status: AnyStatus, t: any): string {
  switch (status) {
    case 'PENDING_ASSIGNMENT':
      return t('assignments.statusPendingAssignment', 'Hozzárendelés függőben')
    case 'PENDING_UNASSIGNMENT':
      return t('assignments.statusPendingUnassignment', 'Visszavétel függőben')
    case 'ASSIGNED':
      return t('devices.statusAssigned', 'Hozzárendelve')
    case 'IN_STORAGE':
      return t('devices.statusInStorage', 'Raktárban')
    case 'PENDING_MAINTENANCE':
      return t('devices.statusPendingMaintenance', 'Karbantartás függőben')
    case 'MAINTENANCE':
      return t('devices.statusMaintenance', 'Karbantartás alatt')
    case 'PENDING_DISPOSAL':
      return t('devices.statusPendingDisposal', 'Selejtezés függőben')
    case 'DISPOSED':
      return t('devices.statusDisposed', 'Selejtezve')
    case 'REJECTED':
      return t('assignments.statusRejected', 'Elutasítva')
    case 'PENDING':
      return t('devices.statusPending', 'Függőben')
    default:
      return status
  }
}

function getVariant(status: AnyStatus): string {
  switch (status) {
    case 'PENDING_ASSIGNMENT':
      return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300'
    case 'ASSIGNED':
      return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
    case 'PENDING_UNASSIGNMENT':
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300'
    case 'IN_STORAGE':
      return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300'
    case 'PENDING_MAINTENANCE':
      return 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300 border border-amber-300 dark:border-amber-700'
    case 'MAINTENANCE':
      return 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300'
    case 'PENDING_DISPOSAL':
      return 'bg-rose-100 text-rose-800 dark:bg-rose-900/30 dark:text-rose-300 border border-rose-300 dark:border-rose-700'
    case 'DISPOSED':
      return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'
    case 'REJECTED':
      return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'
    default:
      return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300'
  }
}
