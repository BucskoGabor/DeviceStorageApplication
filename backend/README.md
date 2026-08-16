# Backend (Spring Boot 3.3 + Java 21)

Ez a mappa fogja tartalmazni a Spring Boot alkalmazást. A projekt inicializálása a Task 1.2-ben történik.

Tervezett struktúra:
```
backend/
├── pom.xml
├── Dockerfile
├── src/
│   ├── main/
│   │   ├── java/com/tanszek/device/
│   │   │   ├── DeviceStorageApplication.java
│   │   │   ├── auth/
│   │   │   ├── user/
│   │   │   ├── device/
│   │   │   ├── location/
│   │   │   ├── software/
│   │   │   ├── assignment/
│   │   │   ├── attachment/
│   │   │   ├── audit/
│   │   │   ├── import/
│   │   │   ├── crypto/
│   │   │   ├── config/
│   │   │   └── common/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── messages_hu.properties
│   │       ├── messages_en.properties
│   │       └── db/migration/
│   │           ├── V1__init_schema.sql
│   │           └── V2__seed.sql
│   └── test/
│       └── java/com/tanszek/device/
└── target/   (gitignore-d, build artifact)
```

Lásd: [`implementation_plan.md`](../implementation_plan.md) §0 (Backend Package Struktúra) és §3 (Backend Architektúra).