import type { AssignmentStatus } from '../api/assignmentApi'

interface StatusBadgeProps {
  status: AssignmentStatus
}

/**
 * StatusBadge — Assignment státusz színes badge.
 *
 * Színek:
 * - PENDING_ASSIGNMENT: kék (info, figyelemfelkeltő)
 * - ASSIGNED: zöld (siker, végleges)
 * - PENDING_UNASSIGNMENT: sárga (figyelmeztető)
 * - IN_STORAGE: szürke (semleges, nyugalmi állapot)
 */
export function StatusBadge({ status }: StatusBadgeProps) {
  const variant = getVariant(status)
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${variant}`}
    >
      {status}
    </span>
  )
}

function getVariant(status: AssignmentStatus): string {
  switch (status) {
    case 'PENDING_ASSIGNMENT':
      return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300'
    case 'ASSIGNED':
      return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
    case 'PENDING_UNASSIGNMENT':
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300'
    case 'IN_STORAGE':
      return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300'
    case 'REJECTED':
      return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'
    default:
      return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300'
  }
}
