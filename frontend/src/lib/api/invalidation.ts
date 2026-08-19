import { QueryClient } from '@tanstack/react-query'
import {
  deviceKeys,
  assignmentKeys,
  maintenanceKeys,
  disposalKeys,
  locationKeys,
  userKeys,
  softwareKeys,
  auditKeys,
} from './queryKeys'

/** Hozzárendelés / visszavétel jóváhagyása, elutasítása, kérelem indítás */
export function invalidateAssignmentWorkflow(qc: QueryClient) {
  qc.invalidateQueries({ queryKey: deviceKeys.all })
  qc.invalidateQueries({ queryKey: assignmentKeys.all })
  qc.invalidateQueries({ queryKey: locationKeys.all })
  qc.invalidateQueries({ queryKey: userKeys.all })
}

/** Karbantartás kérelem / jóváhagyás / elutasítás / visszavétel */
export function invalidateMaintenanceWorkflow(qc: QueryClient) {
  qc.invalidateQueries({ queryKey: deviceKeys.all })
  qc.invalidateQueries({ queryKey: maintenanceKeys.all })
}

/** Selejtezés kérelem / jóváhagyás / elutasítás */
export function invalidateDisposalWorkflow(qc: QueryClient) {
  qc.invalidateQueries({ queryKey: deviceKeys.all })
  qc.invalidateQueries({ queryKey: disposalKeys.all })
}

/** Audit rollback — bármely entitást érinthet */
export function invalidateAuditRollback(qc: QueryClient) {
  qc.invalidateQueries({ queryKey: deviceKeys.all })
  qc.invalidateQueries({ queryKey: assignmentKeys.all })
  qc.invalidateQueries({ queryKey: locationKeys.all })
  qc.invalidateQueries({ queryKey: userKeys.all })
  qc.invalidateQueries({ queryKey: softwareKeys.all })
  qc.invalidateQueries({ queryKey: auditKeys.all })
}
