# Frontend Modul (React 18 + TypeScript + Vite)

A Tanszéki Eszköznyilvántartó Rendszer modern, reszponzív webes felhasználói felülete.

## Főbb technológiák
- **React 18** & **TypeScript**
- **Vite** (Gyors fejlesztői környezet és optimalizált production build)
- **TailwindCSS** & **Radix UI** / **shadcn/ui** komponensarchitektúra
- **TanStack React Query** (Szerverállapot-kezelés, automatikus cache-elés és érvénytelenítés)
- **React Router 6** (Kliensoldali útvonalválasztás és védett route-ok)
- **Lucide Icons** (Egységes vektorgrafikus ikonkészlet)
- **i18n Többnyelvűsítés** (Magyar és angol nyelvi támogatás)

## Fejlesztői környezet futtatása
```bash
# Függőségek telepítése
npm install

# Fejlesztői szerver indítása (alapértelmezetten 5173-as port)
npm run dev

# Production build készítése
npm run build

# Kódminőség és linter ellenőrzése
npm run lint
```

## Mappastruktúra
- `src/features/`: Moduláris funkcionalitás (auth, device, assignment, location, software, audit, attachment, user)
- `src/components/ui/`: Újrafelhasználható UI primitívek és vezérlőelemek
- `src/lib/`: API kliens, fordítási fájlok (i18n), segédfüggvények
- `src/routes/`: Alkalmazás navigáció és jogosultság-alapú védett útvonalak
- `src/types/`: Globális TypeScript típusdefiníciók