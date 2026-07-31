package hu.tanszek.device.assignment.entity;

/**
 * Device assignment státuszok (state machine).
 *
 * <p>A {@code AssignmentService} state machine-t kezel:
 *
 * <pre>
 *   IN_STORAGE → PENDING_ASSIGNMENT → ASSIGNED → PENDING_UNASSIGNMENT → IN_STORAGE
 * </pre>
 */
public enum AssignmentStatus {
  /** Eszköz raktárban, nincs aktív assignment */
  IN_STORAGE,

  /** Eszköz hozzárendelve (aktív, érvényes) */
  ASSIGNED,

  /** Assign kérelem beadva, jóváhagyásra vár */
  PENDING_ASSIGNMENT,

  /** Unassign kérelem beadva, jóváhagyásra vár */
  PENDING_UNASSIGNMENT
}
