# Frontend (Vite + React 18 + TypeScript)

Ez a mappa fogja tartalmazni a Vite + React alkalmazást. A projekt inicializálása a Task 1.7-ben történik.

Tervezett struktúra:
```
frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── Dockerfile
├── nginx.conf
├── .env.example
├── public/
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── components/
    │   ├── ui/         # shadcn komponensek
    │   ├── DataTable/
    │   ├── DiffViewer/
    │   └── SonnerWrapper/
    ├── features/
    │   ├── auth/
    │   ├── user/
    │   ├── device/
    │   ├── location/
    │   ├── software/
    │   ├── assignment/
    │   ├── attachment/
    │   └── audit/
    ├── hooks/
    ├── lib/
    │   ├── api/
    │   ├── i18n/       # hu.json, en.json, i18n-keys.ts (generált)
    │   ├── theme/
    │   └── validation/
    ├── routes/
    │   └── index.ts
    └── types/
```

Lásd: [`implementation_plan.md`](../implementation_plan.md) §4 (Frontend Architektúra).