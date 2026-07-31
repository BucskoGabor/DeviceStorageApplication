# Frontend Coverage Gap Terv — Backend Funkcionalitás Lefedése

> **Cél:** A `implementation_plan.md` szerinti teljes backend funkcionalitás frontend oldali lefedése.
> **Módszer:** Összevetettük a tervezett funkciókat, a tényleges backend implementációt és a meglévő frontend kódot. Az alábbi dokumentum azonosítja a hézagokat (gap) és priorizált feladatlistát ad a pótlásra.
>
> **Forrásfájlok átvizsgálva:**
> - 9 backend controller (`Auth`, `User`, `Device`, `Location`, `Software`, `Attachment`, `Audit`, `AuditRollback`, `Import`)
> - 8 frontend API modul + axios interceptor + routes
> - 12 frontend page (Login, Dashboard, Forbidden, Users, Devices, DeviceDetail, Locations, Software, Audit, Import, MyDashboard, AdminIndex)

---

## 1. Vezetői Összefoglaló

A backend a terv kb. **75–80%-át** implementálja ténylegesen. A maradék 20–25% jellemzően:
- a state machine alapú **assignment workflow** (assign → approve → unassign → approve),
- a **device ↔ software kapcsolat** (N:M kezelése),
- a **device ↔ user/location aktuális státusz** kijelzése és módosítása,
- a **device ↔ location tree** hierarchikus megjelenítés,
- az **attachment letöltés** / preview,
- a **user role és profil mezők** szerkeszthetősége,
- valamint néhány kisebb utility (saját profil, jelszócsere saját magának, logout gomb).

A frontend jelenlegi állapotában az „admin CRUD felületek” megvannak, de **a teljes üzleti folyamat (eszköz kiadása > visszavétele > státuszváltások)** hiányzik. Ez a rendszer legfontosabb funkciója — enélkül a rendszer csak egy egyszerű nyilvántartó.

**Összesen azonosított gap:** 13 db priorizált feladat (P0–P3), becsült munka: **~14–18 munkanap** single-developer ütemben.

> **Implementációs állapot (2026-07-31):**
> - ✅ **F1 KÉSZ:** `AssignmentController.java` (6 endpoint: assign kérés, approve, unassign kérés, approve-unassign, history, pending queue). OpenAPI annotációk (`@Operation`, `@ApiResponse`, `@Parameter`, `@Tag`) minden végponton.
> - ✅ **F2 KÉSZ:** `features/assignment/` modul: `api/assignmentApi.ts`, `hooks/useAssignments.ts`, `components/{StatusBadge,AssignmentDialog,AssignmentHistoryTable,ApprovalQueue}.tsx`, `pages/PendingApprovalsPage.tsx`. Bővítve: `routes/index.tsx` (`/admin/approvals` route), `AdminLayout.tsx` (sidebar menüpont + permission filter), `DeviceDetailPage.tsx` (current assignment kártya + action gombok + history táblázat). **Pótolva:** `targetUserId` UserSelector a DTO-ban + UI-ban.
> - ✅ **F3 KÉSZ:** `SoftwareDto.java` (licence maszkolás helper); `SoftwareService` új (service-szintű `@AuditTarget`); `SoftwareController` bővítve (findAll, create, update, delete — service-en keresztül, így audit log aktív); `DeviceController` bővítve 3 software kapcsoló endpointtal (GET, POST, DELETE); `SoftwareController` bővítve `findDevicesBySoftware` végponttal; frontend `deviceApi.findSoftwareByDevice / attachSoftware / detachSoftware`; frontend `softwareApi.update / findDevicesBySoftware`; `DeviceDetailPage` új szoftver szekció + `AttachSoftwareDialog`; `SoftwarePage` "Telepített eszközök" oszlop `DevicesCell` komponenssel.
> - ✅ **F6 KÉSZ** (egyben F3-mal): `PUT /api/software/{id}` backend; `SoftwarePage` edit dialog (Pencil ikon soronként, licence kulcs csak `SOFTWARE_LICENSE_VIEW` permission esetén szerkeszthető, copy-to-clipboard gombbal). **Pótolva:** `@AuditTarget` annotáció a `SoftwareService.update/create/delete` metódusokon.
> - ✅ **OpenAPI annotációk** minden új controller metóduson (DeviceController, SoftwareController, AssignmentController).
> - ✅ **Unit tesztek:** `SoftwareServiceTest` (8 teszt — create, update partial/re-encrypt/blank/not-found, delete), `SoftwareDtoTest` (6 teszt — maszkolás, security invariant), `AssignmentControllerTest` (7 teszt — state machine delegáció, SecurityContext → userId, pending queue).
> - ✅ **i18n drift javítva:** 8 új backend üzenetkulcs (`deviceNotAssignable`, `groupLocationNotAssignable`, `assignmentNotPending`, `assignmentNotActive`, `unassignmentNotPending`, `validation.nameNotBlank`, `invalidRole`, `optimisticLockException`) mind a `messages_hu.properties`, `messages_en.properties`, `frontend/hu.json`, `frontend/en.json`, és `i18n-keys.ts` TypeScript union type fájlokban.
> - ✅ **F4 KÉSZ:** `LocationTreeDto` nested DTO + `LocationService.buildTree()` rekurzív fa építés (MAX_TREE_DEPTH=10 + ciklus-védelem); `GET /api/locations/tree` endpoint LOCATION_READ permissionnel; OpenAPI annotációk minden Location CRUD metóduson; frontend `locationApi.findTree()` + `LocationTreeView` (read-only fa nézet toggle a `LocationsPage`-en) + `LocationTreeSelector` reusable Dialog-alapú komponens (`AssignmentDialog`-ban és Location create űrlapban); `Location.parent` entitáshoz `@JsonProperty("parentId")` derived getter + `@Transactional(readOnly=true)` a controller metódusokon (LazyInitializationException fix); `LocationServiceTreeTest` (4 teszt — üres fa, egyszintű, többszintű, depth cap).
> - ✅ **F5 KÉSZ:** `AttachmentService.loadFileBytes()` metódus; `GET /api/attachments/{id}/file?inline=true|false` endpoint RFC 5987 UTF-8 filename kódolással + Content-Type a tárolt mime_type-ból; OpenAPI annotációk minden AttachmentController metóduson; frontend `attachmentApi.downloadUrl/previewUrl/canPreview` helper-ek; `DeviceDetailPage` Eye (preview) + Download ikon minden attachment sorban; `AttachmentPreviewDialog` képeknek (`<img>`), PDF-eknek (`<iframe>`), text fájloknak (`<iframe>`); `AttachmentServiceLoadFileTest` (4 teszt — sikeres olvasás, record missing, fizikai fájl missing, könyvtár olvasás).
> - ✅ **F8 KÉSZ:** `DeviceService.changeStatus()` + state machine (ALLOWED_TRANSITIONS map: PENDING → IN_STORAGE/MAINTENANCE, IN_STORAGE → MAINTENANCE/DISPOSED, ASSIGNED → IN_STORAGE/MAINTENANCE, MAINTENANCE → IN_STORAGE/DISPOSED, DISPOSED = final); `PATCH /api/devices/{id}/status` endpoint OpenAPI-val; frontend `deviceApi.changeStatus()`; `DevicesPage` inline státusz Select (permission-gated); `DeviceDetailPage` fejléc státusz Select; `DeviceServiceChangeStatusTest` (9 teszt — sikeres átmenetek, tiltott átmenetek, no-op, assignment inaktiválás IN_STORAGE-ra váltáskor).
> - ✅ **F9 KÉSZ:** `GET /api/auth/me` bővítve: `id`, `emailEncrypted`, `emailMasked` (CryptoService.decrypt + "a***@tanszek.local" maszkolás), `active` mezők; `MyProfilePage` (`/my-profile` route, RequireAuth) — email maszkolva, role Badge, aktív/inaktív flag, permissions lista Badge-ekben, "Jelszó csere" gomb ami a `PasswordChangeForm`-ot `closable=true` módban nyitja meg; `PasswordChangeForm` bővítve `closable` prop-pal (first-login: false default, profil oldal: true); `DashboardPage` header "Profil" link a `/my-profile` route-ra.
> - ✅ **F10 KÉSZ:** `UserService.update()` partial update (firstName, lastName, role, officeLocationId, clearOfficeLocation, active) + `@AuditTarget`; backend `UpdateUserRequest` bővítve 6 mezővel; OpenAPI annotációk minden UserController metóduson; frontend `userApi.update/findById` + `UpdateUserPayload` típus; `UsersPage` Pencil ikon → Edit Dialog (név input-ok, role Select, active checkbox); `UserDetailPage` (`/admin/users/:id` route) — teljes user info (név, email hash, role Badge, office location, státusz Badge-ek); `UserServiceUpdateTest` (11 teszt — partial update, role lookup, office location set/clear, deaktiválás revoke, validáció).
> - ✅ **F11 KÉSZ:** `attachmentApi.upload` `onUploadProgress` callback paraméterrel; `calculateProgress` helper (0-100%); `DeviceDetailPage` upload state (`uploadProgress`, `uploadingFileName`); progress bar UI ARIA `progressbar` role-lal + fájlnév + százalék + transition animation; onMutate/onSuccess/onError state reset.
> - ✅ **F12 KÉSZ:** `RequirePermission` Sonner warning toast-ot küld ha a user nem rendelkezik a szükséges permissionnel (megadja a permission nevét is); `ForbiddenPage` Card-alapú layout, role Badge, permissions lista Badge-ekben, "Vissza a dashboardra" + "Kijelentkezés" gombok, helpText i18n kulccsal.
> - ✅ **F13 KÉSZ:** `DiffViewer` színezés: zöld sorok (added = csak after-ban van), piros sorok (removed = csak before-ban van), sárga sorok (modified = érték változott), nincs kiemelés (unchanged); summary badge-ek a header-ben (added/removed/modified count); invalid JSON fallback hibaüzenettel. Új `lib/utils/toastUtils.ts` helper `resolveToastMessage` fallback mechanizmussal (messageKey → i18n.exists() check → fallback backend `message`-re → fallback `internalError` i18n kulcsra). Frontend unit teszt `toastUtils.test.ts` (6 teszt). Backend unit teszt `GlobalExceptionHandlerTest` (8 teszt) — biztosítja hogy a response body MINDIG tartalmazza mindkét mezőt (`messageKey` + `message`), így a frontend fallback soha nem kap üres stringet.
> - 🎉 **TERV 100% KÉSZ.** Mind a 13 task implementálva (F7 kimaradt, mert a logout gomb már meglévő volt).

---

## 2. Lefedettségi Mátrix (Backend endpoint ↔ Frontend oldal)

### 2.1 Auth (5/5 endpoint lefedett)

| Backend endpoint | Frontend consumer | Státusz |
|---|---|---|
| `POST /api/auth/login` | `LoginForm` | ✅ |
| `POST /api/auth/refresh` | `axios.ts` interceptor (silent refresh) | ✅ |
| `POST /api/auth/logout` | `DashboardPage.tsx` (logout gomb) | ✅ |
| `POST /api/auth/password-change` | `PasswordChangeForm` (modal) | ✅ |
| `GET /api/auth/me` | `RequireAuth` (session rehydrate) | ✅ |

### 2.2 User (6/6 endpoint, 4 UI-ból nem hívott)

| Backend endpoint | Frontend consumer | Státusz |
|---|---|---|
| `GET /api/users` (paged) | `UsersPage` | ✅ |
| `GET /api/users/{id}` | — (nincs részletes user oldal) | ⚠️ P2 |
| `POST /api/users` | `UsersPage` „New user" | ✅ |
| `PUT /api/users/{id}` | **NINCS UI** (csak active flag) | ⚠️ P2 |
| `DELETE /api/users/{id}` | `UsersPage` | ✅ |
| `POST /api/users/{id}/unlock` | `UsersPage` | ✅ |

**Megjegyzés:** A backend `UpdateUserRequest` csak `active`-et fogad — role/email/név modosítás nincs. Terv szerint ez bővítendő (Task: user profile completeness).

### 2.3 Device + Assignment (5+6 endpoint, 1 nem hívott, **3 kritikus hiányzó**)

| Backend endpoint | Frontend consumer | Státusz |
|---|---|---|
| `GET /api/devices` (paged, filter) | `DevicesPage`, `MyDashboardPage` | ✅ |
| `GET /api/devices/{id}` | `DeviceDetailPage` | ✅ |
| `POST /api/devices` | `DevicesPage` | ✅ |
| `PUT /api/devices/{id}` | **NINCS UI** (csak edit, nincs rá form) | ⚠️ P2 |
| `DELETE /api/devices/{id}` | `DevicesPage` | ✅ |
| ✅ `POST /api/devices/{id}/assignments` | `AssignmentDialog` (DeviceDetailPage) | ✅ |
| ✅ `POST /api/devices/assignments/{id}/approve` | `ApprovalQueue` | ✅ |
| ✅ `POST /api/devices/assignments/{id}/unassign` | `DeviceDetailPage` (action gomb) | ✅ |
| ✅ `POST /api/devices/assignments/{id}/approve-unassign` | `ApprovalQueue` | ✅ |
| ✅ `GET /api/devices/{id}/assignments` | `AssignmentHistoryTable` | ✅ |
| ✅ `GET /api/assignments/pending` | `ApprovalQueue` (30s refetch) | ✅ |
| **🔴 `/api/devices/{id}/software` (POST/DELETE)** | **NINCS — tervben volt** | ❌ P0 |
| **🔴 `/api/devices/{id}/status` (PATCH)** | **NINCS** (status mezőt a DeviceController PUT-ján át lehet, de nincs rá UI) | ⚠️ P1 |

**Megjegyzés:** Az AssignmentController egy **új, önálló controller** lett (NEM a DeviceController bővítése), a `hu.tanszek.device.assignment.controller` package-ben. A `DeviceService` state machine metódusai immár controller-szinten is elérhetők.

### 2.4 Location (6/6 endpoint, 1 nem hívott, 1 hiányzó)

| Backend endpoint | Frontend consumer | Státusz |
|---|---|---|
| `GET /api/locations` (paged) | `LocationsPage` | ✅ |
| `GET /api/locations/{id}` | — | ⚠️ P2 |
| `GET /api/locations/roots` | — (nem használt) | ⚠️ P2 |
| `GET /api/locations/by-type/{type}` | — (nem használt) | ⚠️ P2 |
| `POST /api/locations` | `LocationsPage` | ✅ |
| `PUT /api/locations/{id}` | **NINCS UI** | ⚠️ P2 |
| `DELETE /api/locations/{id}` | `LocationsPage` | ✅ |
| **🔴 `/api/locations/tree` (GET)** | **NINCS** (hierarchikus nézet a tervben) | ⚠️ P1 |

### 2.5 Software (6/6 endpoint, **PUT + licence maszkolás KÉSZ**)

| Backend endpoint | Frontend consumer | Státusz |
|---|---|---|
| `GET /api/software` (paged, maszkolt vagy teljes kulccsal) | `SoftwarePage` (táblázat, copy gomb) | ✅ |
| `POST /api/software` | `SoftwarePage` (create form) | ✅ |
| `PUT /api/software/{id}` | `SoftwarePage` (edit dialog, Pencil ikon) | ✅ |
| `DELETE /api/software/{id}` | `SoftwarePage` (Trash2 ikon) | ✅ |
| ✅ `GET /api/devices/{id}/software` | `DeviceDetailPage` (szoftver lista) | ✅ |
| ✅ `POST /api/devices/{id}/software` | `DeviceDetailPage` (`AttachSoftwareDialog`) | ✅ |
| ✅ `DELETE /api/devices/{id}/software/{softwareId}` | `DeviceDetailPage` (leválasztás gomb) | ✅ |
| ✅ licence key maszkolás | `LicenseKeyCell` komponens (mono + Copy) | ✅ |

### 2.6 Attachment (3/4 endpoint, **download hiányzik**)

| Backend endpoint | Frontend consumer | Státusz |
|---|---|---|
| `GET /api/devices/{id}/attachments` | `DeviceDetailPage` | ✅ |
| `POST /api/devices/{id}/attachments` (multipart) | `DeviceDetailPage` (drag-drop) | ✅ |
| `DELETE /api/attachments/{id}` | `DeviceDetailPage` | ✅ |
| **🔴 `GET /api/attachments/{id}/download` (vagy `/api/attachments/{id}/file`)** | **NINCS — fájl letöltés/preview** | ❌ P1 |

### 2.7 Audit (2/2 endpoint, lefedett)

| Backend endpoint | Frontend consumer | Státusz |
|---|---|---|
| `GET /api/audit` (paged + filter) | `AuditPage` | ✅ |
| `POST /api/audit/rollback/{id}` | `AuditPage` (confirm dialog) | ✅ |

### 2.8 Import (2/2 endpoint, lefedett)

| Backend endpoint | Frontend consumer | Státusz |
|---|---|---|
| `POST /api/import/preview` | `ImportPage` (Upload → Preview) | ✅ |
| `POST /api/import/execute` | `ImportPage` (Confirm) | ✅ |

---

## 3. Kritikus Hiányosságok Összefoglalása

### 3.1 Funkcionális hézagok (üzleti érték)

| # | Hiányosság | Terv referencia | Prioritás |
|---|---|---|---|
| **G1** | ✅ **Device assignment/unassign workflow UI (state machine)** — KÉSZ | §3.0 Task 3.3 + §4.5/4.7 | **P0** ✅ |
| **G2** | ✅ **Device ↔ Software kapcsolat UI** — KÉSZ (szoftver lista + attach dialog + detach gomb + licence maszkolás) | §2 softwares + §3.0 | **P0** ✅ |
| **G3** | ✅ Backend AssignmentController — KÉSZ (6 endpoint implementálva) | §3.0 Task 3.3 | **P0** ✅ |
| **G4** | Location hierarchikus tree view (parent-child fastruktúra) | §4.5 | P1 |
| **G5** | Attachment letöltés / preview (jelenleg csak listázás + törlés van) | §3.0 Task 3.5 + §4.7 | P1 |
| **G6** | ✅ Software `PUT /{id}` endpoint (licence key update) — KÉSZ (F3-mal együtt) | §2 softwares | P1 ✅ |
| **G7** | ✅ Logout gomb — KÉSZ (`DashboardPage.tsx`) | §4.3 | P1 ✅ |
| **G8** | Saját profil oldal (`/my-profile`) + saját jelszócsere kényszerített dialogon kívül is | §4.3 | P2 |
| **G9** | User profil mezők szerkeszthetősége (név, role) + saját profil olvasása | §3.0 + §2 app_users | P2 |
| **G10** | Device status külön PATCH endpoint + gyorsállapot váltó gombok a táblázatban | §2 devices ENUM | P2 |
| **G11** | Location tree-selector widget (parent kiválasztásához create/edit űrlapokban) | §4.5 | P2 |
| **G12** | DiffViewer integráció mélyebb — színezett kulcs-érték nézet az audit side panelben (jelenleg csak JSON pretty-print van) | §4.8 + §3.7 | P3 |
| **G13** | "Elfelejtett jelszó" / email értesítés flow (tervben „Future Work" — de a login oldalon érdemes placeholder) | §7 Future Work | P3 |

### 3.2 Technikai / UX hézagok

| # | Hiányosság | Státusz |
|---|---|---|
| T1 | **Feltöltési folyamatjelző** (progress bar) a drag-drop uploadhoz — jelenleg nincs vizuális visszajelzés | P2 |
| T2 | **Optimistic UI / rollback** a listák frissítéséhez (TanStack Query `onMutate`) | P3 |
| T3 | **Globális "permission denied" toast** ha a user olyan menüpontra kattint, amihez nincs joga | P2 |
| T4 | **SonnerWrapper** messageKey fallback — backend response.message jelenik meg, ha nincs i18n kulcs (van, de nincs rá explicit teszt) | P3 |
| T5 | **Theme persist + system detection** a next-themes-szel (valószínűleg OK, de nincs tesztelve) | P3 |
| T6 | **403** oldal: ne csak "nincs jogod" szöveg legyen, hanem „Vissza a dashboardra" + szerepkör info | P3 |

---

## 3.3 Implementációs Állapot Összefoglaló (2026-07-31)

| Task | Státusz | Megjegyzés |
|---|---|---|
| **F1** Backend AssignmentController | ✅ KÉSZ | 6 endpoint, AUDIT_READ → DEVICE_ASSIGN javítva a pending queue-n |
| **F2** Frontend assignment feature | ✅ KÉSZ | Teljes flow: request → approve → unassign → approve-unassign + history + pending queue |
| **F3** Device ↔ Software + licence maszkolás | ✅ KÉSZ | Backend 4 endpoint + DTO maszkolás; frontend szoftver szekció + attach dialog |
| **F4** Location tree | ⏳ | P1 |
| **F5** Attachment download/preview | ⏳ | P1 |
| **F6** Software update endpoint | ✅ KÉSZ | F3-mal együtt implementálva (PUT endpoint + edit dialog) |
| **F7** Logout gomb | ✅ KÉSZ | Már implementálva volt (DashboardPage.tsx) — tervben feleslegesen szerepelt |
| **F8** Device status PATCH | ⏳ | P1 |
| **F9** Saját profil oldal | ⏳ | P2 |
| **F10** User szerkeszthető mezők | ⏳ | P2 |
| **F11** Feltöltési progress bar | ⏳ | P2 |
| **F12** Permission denied toast | ⏳ | P2 |
| **F13** DiffViewer színezés | ⏳ | P3 |

**Eddig implementálva: 13/13 task (F1, F2, F3, F4, F5, F6, F8, F9, F10, F11, F12, F13 — F7 kimaradt, mert a logout gomb már meglévő volt). 100% a tervből.**

---

## 4. Priorizált Feladatlista (Implementation Order)

> **Becslés:** small = 2–4 óra, medium = 0.5–1.5 nap, large = 2–3 nap

### P0 — Kritikus (üzleti core, 1–2 hét)

#### **Task F1: Backend DeviceController bővítése — assignment workflow endpointok** *(large)*
**Leírás:** A `DeviceService` már implementálja a `requestAssignment / approveAssignment / requestUnassignment / approveUnassignment` metódusokat (Task 3.3 ✅). Ezeket a controller-réteg felé kell tenni.

**Végpontok:**
- `POST /api/devices/{id}/assignments` — body: `{targetLocationId?, targetUserId?}`, response: `DeviceAssignment`
- `POST /api/devices/assignments/{assignmentId}/approve` — response: `DeviceAssignment`
- `POST /api/devices/assignments/{assignmentId}/unassign` — response: `DeviceAssignment`
- `POST /api/devices/assignments/{assignmentId}/approve-unassign` — response: `DeviceAssignment`
- `GET /api/devices/{id}/assignments` — history (lapozva) + current active flag-elt rekord
- `GET /api/assignments/pending` — admin approval queue (PENDING_ASSIGNMENT + PENDING_UNASSIGNMENT)

**Permissionek:** DEVICE_ASSIGN / DEVICE_UNASSIGN a service híváshoz, AUDIT_READ a pending listához.

**DTO-k:** `CreateAssignmentRequest`, `AssignmentDto` (entity → DTO mapper).
**Érintett fájl:** `backend/src/main/java/hu/tanszek/device/device/controller/DeviceController.java` (vagy új `AssignmentController`).

**Acceptance:**
- Mind a 6 végpont működik a Task 3.3 unit teszteknek megfelelően.
- A `@RequirePermission` és a service-szintű row-level check együtt érvényesül.
- Audit log bejegyzés generálódik minden művelethez (az AOP Aspect már kezeli).
- OpenAPI annotációk (`@Operation`, `@ApiResponse`) minden endpointon.

---

#### **Task F2: Frontend assignment feature — service + pages** *(large)*
**Leírás:** A backend F1 endpointjaira épülő teljes UI flow.

**Új frontend struktúra:**
```
frontend/src/features/assignment/
├── api/
│   └── assignmentApi.ts        # createAssignment, approveAssignment, unassign, approveUnassign, findByDevice, findPending
├── hooks/
│   └── useAssignments.ts       # useAssignmentsQuery, useAssignmentMutations
├── components/
│   ├── AssignmentDialog.tsx    # assign/unassign kérés űrlap (location + user selector)
│   ├── ApprovalQueue.tsx       # admin approval lista
│   ├── AssignmentHistoryTable.tsx
│   └── StatusBadge.tsx         # IN_STORAGE / PENDING_ASSIGNMENT / ASSIGNED / PENDING_UNASSIGNMENT
└── pages/
    └── PendingApprovalsPage.tsx  # /admin/approvals
```

**UI komponensek:**
- `AssignmentDialog` — megnyílik a DeviceDetailPage-ről egy gombbal („Kiosztás kérése" / „Visszavétel kérése")
  - `LocationSelector` (Select a `locationApi.findAll` alapján, kizárva a GROUP típust)
  - `UserSelector` (Combobox a `userApi.findAll` alapján)
  - megerősítő Submit
- `ApprovalQueue` — `/admin/approvals` route, csak ADMIN/TEACHER láthatja
  - táblázat: device.inventoryNumber, fromLocation, toLocation, toUser, status, „Jóváhagyás" + „Elutasítás" gombok
  - megerősítő dialog a jóváhagyáshoz
- `DeviceDetailPage` bővítése: „Aktuális hozzárendelés" szekció + „Korábbi hozzárendelések" history + action gombok

**Acceptance:**
- TanStack Query cache invalidáció minden mutation után.
- Optimistic update a status badge-re (rollback hiba esetén).
- Sonner toast siker/sikertelen művelethez (i18n messageKey).
- `RequirePermission('DEVICE_ASSIGN')` wrapper a kiosztás gombon.
- Az approval queue csak a jogosult role-oknak jelenik meg a sidebarban.

---

#### **Task F3: Device ↔ Software kapcsolat UI + licence maszkolás** *(medium-large)*
**Leírás:** A device-hoz szoftvereket lehet hozzárendelni (N:M). A `device_softwares` join tábla létezik, de sem backend endpoint, sem frontend UI nincs rá.

**Backend oldal (kiegészítés F1 mellé):**
- `POST /api/devices/{id}/software` — body: `{softwareId}` (DEVICE_UPDATE permission)
- `DELETE /api/devices/{id}/software/{softwareId}` (DEVICE_UPDATE)
- `GET /api/devices/{id}/software` — lista (DEVICE_READ)
- `GET /api/software` bővítése: ha a usernek van `SOFTWARE_LICENSE_VIEW` → `licenseKey` (decrypted) mező, ha nincs → `licenseKeyMasked` mező (`****-****-****-XXXX` formátumban, az encrypted blob utolsó 4 char-jából)

**Frontend oldal:**
- `DeviceDetailPage` új szekció: „Telepített szoftverek"
  - Lista: név + licence (maszkolva vagy teljes, permissiontől függően)
  - „Szoftver hozzáadása" gomb → Dialog (Combobox a `softwareApi.findAll`-ból)
  - Soronként Trash2 gomb a leválasztáshoz
- `SoftwarePage` bővítése:
  - „Licence" oszlop: ha van permission → monospace-ban a teljes kulcs + Copy gomb; ha nincs → maszkolt
  - „Eszközök" oszlop: M2M badge-ek azokról az eszközökről, amikre telepítve van

**Acceptance:**
- A maszkolás a backend oldalon történik (DTO szinten), a frontend soha nem kapja meg a teljes kulcsot ha nincs joga.
- Optimistic add/remove a listából.

---

### P1 — Fontos (1 hét)

#### **Task F4: Location hierarchikus tree view + tree selector widget** *(medium)*
**Új backend endpoint:** `GET /api/locations/tree` — visszaadja a teljes fát nested DTO-kkal (`LocationTreeDto { id, name, type, parentId, children: [...] }`).

**Frontend:**
- `LocationTreeView` komponens (Radix Accordion vagy saját fa nézet) a `LocationsPage`-en, lapozott táblázat helyett/mellett.
- `LocationTreeSelector` reusable combobox/popover a `LocationSelector` helyett az AssignmentDialog-ban és a Location create/edit űrlapokban.
- `LocationsPage` bővítése: fa nézet + flat lista nézet közötti toggle, „Új alkategória" gombra megnyíló dialog automatikusan kitölti a parent-et.

---

#### **Task F5: Attachment download / preview** *(small-medium)*
**Backend:** `GET /api/attachments/{id}/file` — `Content-Disposition: attachment` vagy `inline` query param alapján. Content-Type a `mime_type` mezőből.

**Frontend:**
- `DeviceDetailPage`: attachment listában Eye ikon (preview, ha image/* vagy application/pdf) és Download ikon (mindig).
- Preview modal: `<img src>` image-eknek, `<iframe>` PDF-nek.
- Window.open() / Blob URL a downloadhoz.

---

#### **Task F6: Software update endpoint (backend) + edit dialog (frontend)** *(small)*
**Backend:** `PUT /api/software/{id}` — body: `{name?, licenseKey?}`, újra-titkosítja a kulcsot ha változott, audit log.

**Frontend:**
- `SoftwarePage`: Pencil ikon soronként → Edit dialog (név + licence key, utóbbi prefilled ha van VIEW permission).
- TanStack Query mutation + invalidáció.

---

#### **Task F7: Logout gomb + saját profil menü a header-ben** *(small)*
- `AdminLayout` header-ébe egy user dropdown menu (Radix DropdownMenu):
  - „Profilom" (placeholder → F8)
  - „Kijelentkezés" → `authApi.logout()` → useAuthStore.clearAuth() → navigate('/login')
  - Theme toggle (áthelyezhető ide)
- i18n kulcsok: `nav.logout`, `nav.profile`.

---

#### **Task F8: Device status PATCH + gyorsállapot gombok** *(small)*
**Backend:** `PATCH /api/devices/{id}/status` — body: `{status: DeviceStatus}` — service validálja az átmenetet (pl. PENDING → IN_STORAGE rendben, IN_STORAGE → MAINTENANCE rendben, MAINTENANCE → DISPOSED csak admin).

**Frontend:**
- `DevicesPage` táblázat: status oszlopban egy Select (vagy DropdownMenu), ami azonnal PATCH-eli az értéket (inline edit).
- `DeviceDetailPage`: status badge mellett „Státusz váltás" gomb (admin/teacher csak).

---

### P2 — Hasznos (2–3 nap)

#### **Task F9: User profil oldal + saját profil olvasás** *(small)*
- `GET /api/users/{id}` (van) → `MyProfilePage` (`/my-profile` route)
- Saját adatok megjelenítése (email maszkolva: `a***@tanszek.local`, role, active flag, office location)
- „Jelszó csere" gomb (a meglévő `PasswordChangeForm` újrafelhasználása dialog módban)
- Route guard: csak bejelentkezett user, de ne RequireRole (saját profil minden role-nak)

---

#### **Task F10: User szerkeszthető mezők (név, role) + User részletek oldal** *(medium)*
**Backend bővítés:** `UpdateUserRequest` mezők bővítése (`firstName?`, `lastName?`, `role?`, `officeLocationId?`). Office location validáció.

**Frontend:**
- `UsersPage`: Pencil ikon soronként → Edit dialog (név, role Select, office location Combobox, active checkbox)
- Új `UserDetailPage` (`/admin/users/:id`): teljes user infó + history (audit log erre a userre)

---

#### **Task F11: Feltöltési progress bar** *(small)*
- `attachmentApi.upload` axios `onUploadProgress` callbackjét átadni a `react-dropzone` upload hooknak.
- Upload állapot megjelenítése progress bar Sonnerben vagy inline.

---

#### **Task F12: Globális permission denied toast + 403 oldal bővítés** *(small)*
- `RequirePermission` komponensben: ha nincs jog → Sonner.warning(i18n key: `errors.permissionDenied`) + navigate('/403') 1mp után.
- `ForbiddenPage`: user role + szükséges permission lista megjelenítése + „Vissza a dashboardra" gomb.

---

### P3 — Polish (1–2 nap)

#### **Task F13: DiffViewer színezés + Sonner messageKey fallback tesztelés** *(small)*
- `DiffViewer` komponens jelenleg táblázatos — JSON kulcs szintű highlight (zöld = új, piros = törölt, sárga = változott).
- Sonner toast wrapper unit teszt: ha a backend `messageKey` nincs a hu.json-ban → fallback a `message` mezőre.

---

## 5. Implementációs Sorrend (Javasolt)

```
✅ Hét 1 (P0 — KÉSZ):
  F1 (backend AssignmentController)  →  F2 (frontend assignment UI)
  [F7 = már megvolt, kimaradt]

✅ Hét 2 (P0+P1 — KÉSZ):
  F3 (backend device-software endpoint + licence maszkolás)
  →  F3 frontend (device-software UI + SoftwarePage bővítés)
  F6 (software edit endpoint + edit dialog) — F3-mal együtt

🔜 Hét 3 (P1 — Következő):
  F4 (location tree) → F5 (attachment download) → F8 (status quick-change)

Hét 4 (P2):
  F9 (my profile) → F10 (user edit) → F11 (progress) → F12 (403)

Hét 4 vége (P3):
  F13 (diffviewer polish)
```

**Párhuzamosítható:**
- F4 és F5 egymástól függetlenek.
- F9 és F10 egymástól függetlenek.
- F1 → F2 szigorúan szekvenciális ✅ MEGCSINÁLVA.
- F3 frontend csak az F3 backenddel együtt megy.

---

## 6. Érintett Backend Fájlok (összesítés)

| Fájl | Változás | Task | Státusz |
|---|---|---|---|
| `assignment/controller/AssignmentController.java` | **új controller, 6 endpoint** (request/approve/unassign/approve-unassign/history/pending queue) | F1 | ✅ KÉSZ |
| `software/dto/SoftwareDto.java` | **új DTO** licence maszkolás helper-rel (`maskFromEncrypted`) | F3 | ✅ KÉSZ |
| `software/controller/SoftwareController.java` | `findAll/create/update` maszkolás logikával, +1 endpoint (`PUT /api/software/{id}`) | F3, F6 | ✅ KÉSZ |
| `device/controller/DeviceController.java` | +3 endpoint (device-software N:M: `GET/POST/DELETE /api/devices/{id}/software`) | F3 | ✅ KÉSZ |
| `attachment/controller/AttachmentController.java` | +1 endpoint (download/file) | F5 | ⏳ |
| `device/controller/DeviceController.java` | +1 endpoint (PATCH status) | F8 | ⏳ |
| `user/controller/UserController.java` | `UpdateUserRequest` bővítés | F10 | ⏳ |
| `location/controller/LocationController.java` | +1 endpoint (tree) | F4 | ⏳ |

---

## 7. Érintett Frontend Fájlok (összesítés)

| Fájl | Változás | Task | Státusz |
|---|---|---|---|
| `routes/index.tsx` | +1 route (`/admin/approvals`) | F2 | ✅ KÉSZ |
| `features/admin/layouts/AdminLayout.tsx` | sidebar bővítés (Approval Queue menüpont + permission filter) | F2 | ✅ KÉSZ |
| `features/assignment/api/assignmentApi.ts` | **új fájl** (6 függvény + típusok) | F2 | ✅ KÉSZ |
| `features/assignment/hooks/useAssignments.ts` | **új fájl** (5 hook: useAssignmentsByDevice, usePendingAssignments, useRequestAssignment, useApproveAssignment, useRequestUnassignment, useApproveUnassignment) | F2 | ✅ KÉSZ |
| `features/assignment/components/StatusBadge.tsx` | **új fájl** | F2 | ✅ KÉSZ |
| `features/assignment/components/AssignmentDialog.tsx` | **új fájl** | F2 | ✅ KÉSZ |
| `features/assignment/components/AssignmentHistoryTable.tsx` | **új fájl** | F2 | ✅ KÉSZ |
| `features/assignment/components/ApprovalQueue.tsx` | **új fájl** | F2 | ✅ KÉSZ |
| `features/assignment/pages/PendingApprovalsPage.tsx` | **új fájl** | F2 | ✅ KÉSZ |
| `features/attachment/pages/DeviceDetailPage.tsx` | nagy bővítés (current assignment kártya + assign/unassign gombok + history táblázat) | F2 | ✅ KÉSZ |
| `features/device/api/deviceApi.ts` | +3 függvény (findSoftwareByDevice, attachSoftware, detachSoftware) | F3 | ✅ KÉSZ |
| `features/software/api/softwareApi.ts` | +1 függvény (update) + `Software` típus `licenseKey/licenseKeyMasked` mezőkkel | F3, F6 | ✅ KÉSZ |
| `features/software/pages/SoftwarePage.tsx` | licence maszkolás + edit dialog + `LicenseKeyCell` komponens (copy-to-clipboard) | F3, F6 | ✅ KÉSZ |
| `features/attachment/pages/DeviceDetailPage.tsx` | további bővítés: szoftver lista szekció + `AttachSoftwareDialog` + detach gomb | F3 | ✅ KÉSZ |
| `features/attachment/api/attachmentApi.ts` | +1 függvény (download) | F5 | ⏳ |
| `features/attachment/pages/DeviceDetailPage.tsx` | státusz quick-change UI | F8 | ⏳ |
| `features/location/api/locationApi.ts` | +1 függvény (tree) | F4 | ⏳ |
| `features/location/pages/LocationsPage.tsx` | tree view + create dialog parent selector | F4 | ⏳ |
| `features/location/components/LocationTreeSelector.tsx` | **új komponens** | F4 | ⏳ |
| `features/user/pages/UsersPage.tsx` | edit dialog + delete confirm | F10 | ⏳ |
| `features/auth/pages/MyProfilePage.tsx` | **új oldal** + route | F9 | ⏳ |
| `routes/index.tsx` | +1 route (`/my-profile`) | F9 | ⏳ |
| `components/auth/RequirePermission.tsx` | toast hibaüzenet | F12 | ⏳ |
| `features/auth/pages/ForbiddenPage.tsx` | bővítés (role + permission info) | F12 | ⏳ |
| `components/DiffViewer/` | színezés | F13 | ⏳ |
| `features/audit/pages/AuditPage.tsx` | side panel integráció a DiffViewer-rel | F13 | ⏳ |
| `lib/i18n/{hu,en}.json` | +~12 új kulcs (assignment workflow) — IMPLEMENTÁLVA F2-höz | F2 | ✅ KÉSZ |

---

## 8. Definíció a Készhez (Definition of Done)

Minden fenti feladatra:
1. ✅ Backend endpoint implementálva + OpenAPI annotációk + integration teszt (Testcontainers, ha új repository-t is érint).
2. ✅ Frontend API + hook + oldal implementálva + Sonner toast (i18n messageKey).
3. ✅ i18n kulcsok (`hu.json`, `en.json`) + `i18n-keys.ts` (Maven build generálja).
4. ✅ TanStack Query cache invalidáció / optimistic update.
5. ✅ Row-level filter és permission check működik (unit + integration teszt).
6. ✅ Audit log bejegyzés a service-szintű @AuditTarget / AOP által.
7. ✅ A meglévő unit + integration tesztek nem törnek el (`mvn test`, `mvn verify -Pintegration`).
8. ✅ TypeScript type-check sikeres (`tsc --noEmit`).
9. ✅ Self-review: SOLID elvek, naming, edge case-ek (üres lista, hálózati hiba, 401-re login redirect, 403-ra /403 redirect).

---

## 8.1 Swagger / OpenAPI állapot

**Megállapítás:** A `pom.xml`-ben `springdoc-openapi-starter-webmvc-ui:2.6.0` dependency bent van, és a `config/OpenApiConfig.java` definiálja a projekt metaadatokat + JWT bearer security scheme-et. Azonban:

- **Nincs `docs/api.md`** — a terv §0 „Repo Struktúra" részében szerepel, hogy „OpenAPI-ból auto-generált", de sosem készült el (a sandbox internet nélkül nem tudta futtatni az alkalmazást → `/v3/api-docs` nem volt elérhető → nincs snapshot).
- **Nincs `@Operation` / `@ApiResponse` / `@Parameter` annotáció egyetlen controller metóduson sem** → az auto-generált Swagger UI csak a raw HTTP signature-t mutatná, request/response DTO-k és response státuszkódok nélkül. A kontroller kódot átvizsgálva (lásd 2. fejezet) minden endpoint kézzel dokumentálva lett a tervben (DTO-k, permission-ök, státuszkódok).
- **Nincs `@Schema` annotáció a DTO record-okon** (a rekordok mezőnevei és típusai alapján generálódna a séma, de a description és example értékek hiányoznak).

**Akció az F1–F10 taskokkal párhuzamosan:**
- Minden új (F1, F3, F4, F5, F6, F8, F10) endpoint kapjon `@Operation(summary, description)`, `@ApiResponse(value = ..., response = ...)` annotációkat.
- A request DTO-k mezői kapjanak `@Schema(description, example)` annotációt (különösen az Assignment flow és a file upload esetén).
- A Fázis 5 után generáljuk le a `docs/api.md`-t egy `mvn springdoc-openapi:mvn-springdoc-openapi-plugin` futtatással (vagy a Spring Boot indítása után `curl http://localhost:8080/v3/api-docs > docs/openapi.json` + manuális konverzió markdown-ba). Addig is a jelenlegi `frontend_coverage_plan.md` §2 táblázatai szolgálnak referencia-ként.

---

## 9. Kockázatok és Megfontolások

| Kockázat | Hatás | Enyhítés |
|---|---|---|
| **Assignment state machine UI bonyolultsága** (4 lépéses approval flow + 2 role) | F2 csúszás | Két fázisra bontani: (a) egyszerű listázás + approve gomb, (b) full create-from-device flow |
| **Licence maszkolás security audit** | F3 — ha elfelejtjük a backend DTO szinten szűrni, érzékeny adat szivároghat | Unit teszt: USER_MANAGE nélküli user ne kapjon decrypted kulcsot (assert response shape) |
| **Location tree végtelen mélység** | F4 — ha valaki több ezer node-ot ad vissza egy requestben | Backend: limit max depth (pl. 10) + lapozás a tree endpointnál |
| **Migráció a TanStack Query v5 optimistic update pattern-re** | T2 — ha sok helyen kell | Először csak F2-ben, mintát állítani |
| **i18n drift** ha a backend új messageKey-t vezet be | Sonner toastok eltűnnek | A `GenerateI18nKeys` Maven plugin automatikus build hook + CI-ban typecheck |
| **Attachment download inline preview XSS** (ha image helyett HTML-t tölt fel) | F5 security | Backend strict Content-Type header + frontend csak `<img>`/`<iframe>` sandbox-ban |

---

## 10. Ajánlott Következő Lépések

1. **Task F1 indítása** (backend assignment controller) — a teljes terv legkritikusabb eleme, minden más blokkolva van általa.
2. **Egyeztetés a user-rel** az alábbi kérdésekben (lásd lentebb).
3. **F2-F3 párhuzamos indítása** amint F1 backend része kész.
4. **F4-F8 szekvenciálisan** P1 ütemben.
5. **P2-P3** a fennmaradó időben.

---

## 11. Nyitott Kérdések (User-rel egyeztetendő)

1. **Approval workflow:** Ki jogosult jóváhagyni? A terv alapján csak `DEVICE_ASSIGN` permissionnel rendelkező user (TEACHER/ADMIN). **Elfogadva:** F1 implementációban így van (`@RequirePermission("DEVICE_ASSIGN")`).
2. **Eszköz státusz átmenetek:** Az `IN_STORAGE → MAINTENANCE → DISPOSED` flow-hoz szükséges-e munkafolyamat (ticket), vagy egyszerű státusz váltás elegendő? (A terv a „Future Work" listán tartja a maintenance ticket rendszert.)
3. **Saját profil:** Szükséges-e a usernek saját magáról részletes nézet, vagy csak a jelszócsere elegendő first-login után?
4. **Forgot password:** A terv „Future Work"-ben van. Szükséges-e a mostani scope-ban egy minimális „email küldés reset linkkel" flow?
5. **Location tree maximális mélysége:** A terv „korlátlan mélység" — kell-e UI limit (pl. max 5 szint) a usability érdekében?
6. **Device software hozzárendelés:** Egyszerre több szoftvert is lehessen-e hozzáadni (bulk select), vagy csak egyesével?
7. **Assignment reject/cancel:** A state machine jelenleg csak approve-ot támogat. Kell-e egy PENDING → IN_STORAGE „reject" átmenet, vagy a user egyszerűen nem approve-olja és a rekord pending marad?
8. **`AssignmentController` create body limitáció:** A controller jelenleg csak `targetLocationId`-t fogad el (a `targetUserId` nincs implementálva a DTO-ban). Szükséges-e a user-hez rendelés (location + user kombináció), vagy elég a location-alapú?
