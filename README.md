# Vetora

Plataforma SaaS multi-tenant para clínicas veterinarias y propietarios de mascotas.

- **Backend:** Java 21, Spring Boot 3.5, Spring Security, JWT + refresh tokens, JPA/Hibernate, Flyway, PostgreSQL, OpenAPI.
- **Web:** Angular 19, Tailwind CSS, ngx-translate (español por defecto / inglés).
- **Móvil:** Flutter para propietarios, misma API REST.
- **Multi-tenant:** base de datos compartida, esquema compartido, `tenant_id`. El tenant se resuelve desde el JWT, nunca desde un identificador enviado por el cliente.

```
Angular + Flutter  →  Spring Boot /api/v1  →  PostgreSQL
                              ↓
                    TenantContext (JWT → usuario → tenant)
```

## Requisitos

- JDK 21 y Maven Wrapper (`backend/mvnw`)
- Node.js 22 (frontend)
- Flutter 3.24+ (app móvil)
- PostgreSQL 16 (local o con Docker)
- Docker (opcional, para PostgreSQL o el stack completo)

## Variables de entorno

| Variable | Descripción | Valor de desarrollo |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev`, `postgres`, `test` | `dev` |
| `DATABASE_URL` | JDBC PostgreSQL | `jdbc:postgresql://localhost:5432/animalin` |
| `DATABASE_USER` | Usuario PostgreSQL | `postgres` |
| `DATABASE_PASSWORD` | Contraseña PostgreSQL | `postgres` |
| `ANIMALIN_JWT_SECRET` | Secreto JWT (≥ 256 bits) | solo desarrollo |
| `API_URL` | Base URL Flutter (`--dart-define`) | `http://localhost:8080/api/v1` |

El API usa **PostgreSQL** en todos los perfiles (incluido `dev` y `test`). Arranque local típico:

```bash
docker compose up -d postgres
```

La base por defecto es `animalin` con usuario y contraseña `postgres`.

## Multi-tenancy y seguridad

- Usuarios globales en `users`. La pertenencia a una clínica está en `tenant_memberships`.
- El personal de clínica recibe `tenantId` en el JWT. Los propietarios pueden relacionarse con varias clínicas (`tenantId` nulo en el token, acceso por mascota/membresía).
- `SUPER_ADMIN` no opera datos clínicos de un tenant.
- Los repositorios y `AccessGuard` filtran siempre por tenant. Un ID de otra clínica responde **404**, no 403, para no filtrar existencia.
- Recurso de ejemplo: `GET /api/v1/pets` (el backend aplica el tenant). No usar `/tenants/{id}/pets` para personal autenticado.
- Branding dinámico: `GET /api/v1/settings/branding` (sesión) y `GET /api/v1/public/tenants/{slug}/branding` (login de clínica). Si no hay logo, Angular y Flutter muestran la marca Vetora.

## Roles

`SUPER_ADMIN` · `TENANT_ADMIN` · `VETERINARIAN` · `RECEPTIONIST` · `PET_OWNER`

La recepción no tiene `MEDICAL_RECORD_READ` / `WRITE` por defecto.

## Datos de demostración

Contraseña común: **`Admin123!`**

| Email | Rol | Clínica |
| --- | --- | --- |
| `leo.a@example.org` | SUPER_ADMIN | plataforma |
| `tina.r@example.net` | TENANT_ADMIN | san-martin |
| `emma.t@example.net` | VETERINARIAN | san-martin |
| `nathan.k@example.net` | RECEPTIONIST | san-martin |
| `juan.owner@animalin.app` | PET_OWNER | san-martin (Luna) |
| `rachel.c@example.org` | TENANT_ADMIN | huellitas |
| `walt.e@example.net` | PET_OWNER | huellitas |
| `xavier.y@example.org` | PET_OWNER | ambas clínicas |

Login de clínica con branding: `http://localhost:4200/login/san-martin`

OpenAPI: `http://localhost:8080/swagger-ui.html`

## Backend

```bash
docker compose up -d postgres
cd backend
./mvnw spring-boot:run
# pruebas (incluye aislamiento multi-tenant; requieren PostgreSQL)
./mvnw test
```

Migraciones Flyway en `backend/src/main/resources/db/migration/`.

## Frontend Angular

```bash
cd web
npm install
npm start          # proxy /api → http://localhost:8080
npm run build
```

Tema claro / oscuro / sistema. Idioma: preferencia de usuario → clínica → `es`.

## Flutter

```bash
cd mobile
flutter create . --project-name animalin
flutter pub get
flutter run --dart-define=API_URL=http://10.0.2.2:8080/api/v1
```

En iOS simulador use `http://localhost:8080/api/v1`. FCM queda preparado en `lib/core/push.dart` (registrar token en `POST /api/v1/notifications/push-token`).

La app de propietarios consume `GET /api/v1/dashboard` (próxima cita, vacuna y tratamientos), el catálogo por veterinaria (`/branches|services|veterinarians/tenant/{id}`) y el branding embebido en mascotas y citas (`tenantName`, `tenantLogoUrl`).

## Docker

```bash
# Solo PostgreSQL (desarrollo local del API / tests)
docker compose up -d postgres

# API + PostgreSQL + panel web
docker compose up --build
```

El panel queda en `http://localhost:4200` y el API en `http://localhost:8080`.

## Internacionalización

Archivos:

- Angular: `web/public/assets/i18n/{es,en}.json`
- Flutter: `mobile/assets/i18n/{es,en}.json`

## Arquitectura de módulos (API)

`auth`, `users`, `tenants`, `plans`, `branches`, `owners`, `pets`, `appointments`, `medical`, `documents`, `messaging`, `notifications`, `reports`, `admin`, `audit`, `storage`.

Los archivos clínicos se guardan fuera de PostgreSQL (disco local en desarrollo; listo para S3/Cloudinary). Ruta lógica: `/tenants/{tenantId}/pets/{petId}/documents/`.

## Estado de las fases (MVP)

Cubierto en esta base:

1. Arquitectura, multi-tenant, autenticación JWT + refresh, roles/permisos.
2. Veterinarias, sucursales, usuarios, propietarios, mascotas (CRUD + soft delete).
3. Agenda (día/semana/mes), citas, disponibilidad y reprogramación del propietario.
4. Expediente, consultas, vacunas, tratamientos, recetas PDF, laboratorios, procedimientos y cirugías.
5. Documentos, notificaciones, recordatorios de vacunas, mensajería.
6. App Flutter del propietario (branding por clínica, tema, reset de contraseña, PDF).
7. Reportes CSV/Excel (sujetos al plan).
8. Planes y suscripciones, límites de plan, branding dinámico, auditoría clínica y de plataforma.

Aún preparado, no obligatorio para el MVP:

- Pasarela de pago, S3/Cloudinary en producción, FCM real, verificación de email, drag & drop del calendario, reportes PDF, inventario/POS/facturación.
