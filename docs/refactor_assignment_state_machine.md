# Refaktor terv

## 1. Problema

A `DeviceAssignment` ket state-et tarol: `active: boolean` + `status: AssignmentStatus`.

Kovetkezmenyek:
- Inconsistent possible (PENDING_ASSIGNMENT + active=true).
- rejectAssignment elfelejti visszaallitani a korabbi aktivot.
- Frontend `find((a) => a.active)` torekeny.

## 2. Megoldas: status mint egyetlen state

`active` flag megszunik. A `status` enum kieg.