-- V4__add_rejected_status_to_assignments.sql
-- Hozzáadja a REJECTED státuszt a device_assignments_status_check CHECK constrainthez.

ALTER TABLE device_assignments DROP CONSTRAINT IF EXISTS device_assignments_status_check;

ALTER TABLE device_assignments ADD CONSTRAINT device_assignments_status_check
  CHECK (status IN ('IN_STORAGE', 'ASSIGNED', 'PENDING_ASSIGNMENT', 'PENDING_UNASSIGNMENT', 'REJECTED'));
