# Backend Modul (Spring Boot 3.3 + Java 21)

A Tanszéki Eszköznyilvántartó Rendszer központi REST API kiszolgálója és üzleti logikai rétege.

## Főbb technológiák
- **Java 21** & **Spring Boot 3.3.2**
- **Spring Security 6** (Stateless JWT token autentikáció, Argon2id jelszóhashelés)
- **Spring Data JPA** & **Hibernate** (PostgreSQL adatbázis kezelés)
- **Flyway** (Automatikus adatbázis séma és adat migrációk)
- **AES-GCM titkosítás** (Szenzitív mezők és licencek adatbázis szintű titkosítása)
- **Bucket4j** (IP és felhasználói szintű Rate Limiting)
- **SpringDoc OpenAPI** (Swagger UI dokumentáció `/swagger-ui.html`)
- **Micrometer & OpenTelemetry** (Metrikák és elosztott nyomkövetés)

## Fejlesztői környezet futtatása
```bash
# Függőségek letöltése és build tesztekkel
mvn clean install

# Alkalmazás futtatása lokálisan (alapértelmezetten 8080-as port)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Csomagstruktúra
- `hu.tanszek.device.auth`: Autentikáció, JWT token kezelés, AD/LDAP integráció
- `hu.tanszek.device.user`: Felhasználókezelés, szerepkörök és jogosultságok
- `hu.tanszek.device.device`: Eszköz CRUD, státuszgépek és keresések
- `hu.tanszek.device.assignment`: Átadás-átvételi és jóváhagyási munkafolyamatok
- `hu.tanszek.device.location`: Helyszínek és tanszéki irodák hierarchiája
- `hu.tanszek.device.software`: Szoftver licencek és titkosított kulcsok
- `hu.tanszek.device.attachment`: Eszközmellékletek és fájlkezelés
- `hu.tanszek.device.audit`: Teljes körű audit naplózás és visszaállítás (rollback)
- `hu.tanszek.device.crypto`: Kriptográfiai szolgáltatások (Argon2id, AES-256-GCM)
- `hu.tanszek.device.config`: Biztonsági, CORS, OpenAPI és ütemezett feladatok konfigurációja