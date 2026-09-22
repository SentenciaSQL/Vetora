# Lunaveta

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
| `FRONTEND_URL` | Origen del panel Angular (enlaces de correo) | `http://localhost:4200` |
| `PUBLIC_APP_URL` | Alias de `FRONTEND_URL` | `http://localhost:4200` |
| `RESEND_API_KEY` | API key de Resend (solo backend / Railway) | vacío en local |
| `RESEND_FROM` | Remitente verificado | `LunaVeta <no-reply@lunaveta.com>` |
| `RESEND_API_URL` | API REST de Resend | `https://api.resend.com` |
| `ACCOUNT_DELETION_TOKEN_HOURS` | Caducidad operativa del enlace de eliminación | `24` |
| `ACCOUNT_DELETION_UNVERIFIED_RETENTION_DAYS` | Borrado operativo de solicitudes no verificadas | `30` |

Los correos transaccionales (verificación, reenvío, recuperación de contraseña, confirmación de cambio, invitaciones de equipo y eliminación de cuenta) se envían con `POST https://api.resend.com/emails` mediante `RestClient`. No se usa SMTP ni el SDK de Resend. En Railway agregue las variables `RESEND_*`, `FRONTEND_URL` y, si quiere cambiar los plazos operativos, `ACCOUNT_DELETION_TOKEN_HOURS` y `ACCOUNT_DELETION_UNVERIFIED_RETENTION_DAYS` en el servicio del API; no las exponga al frontend Angular.

La página pública de eliminación de cuenta es `https://lunaveta.com/eliminar-cuenta`. No requiere sesión. El enlace de confirmación se construye con `FRONTEND_URL` o `PUBLIC_APP_URL`.

Vea `.env.example` para una plantilla. No coloque la API key en el repositorio.

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
- Branding dinámico: `GET /api/v1/settings/branding` (sesión) y `GET /api/v1/public/tenants/{slug}/branding` (login de clínica). Si no hay logo, Angular y Flutter muestran la marca Lunaveta.

## Roles

`SUPER_ADMIN` · `TENANT_ADMIN` · `VETERINARIAN` · `RECEPTIONIST` · `PET_OWNER`

La recepción no tiene `MEDICAL_RECORD_READ` / `WRITE` por defecto.

## Datos de demostración

Contraseña común: **`Admin123!`**

| Email | Rol | Clínica |
| --- | --- | --- |
| `leo.a@example.org` | SUPER_ADMIN | plataforma |
| `propietario.sanmartin@animalin.app` | TENANT_OWNER | san-martin |
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

En iOS simulador use `http://localhost:8080/api/v1`. Los mensajes nuevos envían una notificación push al resto de participantes. La app registra el dispositivo con `POST /api/v1/devices` y lo desactiva con `DELETE /api/v1/devices/current`. El endpoint anterior `POST /api/v1/notifications/push-token` sigue activo y escribe en la misma tabla.

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

- Pasarela de pago, S3/Cloudinary en producción, drag & drop del calendario, reportes PDF, inventario/POS/facturación.

## Notificaciones push (FCM)

El envío real usa Firebase Admin en el API. Si `FCM_ENABLED=false`, el backend arranca y guarda los mensajes sin contactar a Firebase. Si está en `true` y faltan credenciales, arranca igual y deja el envío desactivado, con un error en el log que no incluye el secreto.

Variables del servicio API en Railway:

| Variable | Valor |
| --- | --- |
| `FCM_ENABLED` | `true` en producción |
| `FIREBASE_PROJECT_ID` | `lunaveta-68cc1` |
| `FIREBASE_SERVICE_ACCOUNT_BASE64` | contenido Base64 del JSON de la cuenta de servicio |

Generar el Base64 sin publicar el archivo:

```bash
# Linux
base64 -w 0 firebase-service-account.json

# macOS
base64 -i firebase-service-account.json | tr -d '\n'
```

En Firebase Console: Project settings → Service accounts → Generate new private key. Pegue el resultado solo en Railway. No lo suba al repositorio ni lo imprima en logs.

Android ya usa el paquete `com.sentenciasql.lunaveta` y el plugin de Google Services. Coloque `google-services.json` del proyecto `lunaveta-68cc1` en `mobile/android/app/google-services.json`. Ese archivo de cliente está en `.gitignore`.

iOS queda preparado, pero falta configuración manual porque no hay cuenta de Apple ni clave APNs en este repositorio:

1. Registrar la app iOS en Firebase con el bundle actual `com.example.animalin`.
2. Descargar `GoogleService-Info.plist` y colocarlo en `mobile/ios/Runner/GoogleService-Info.plist` (también está en `.gitignore`).
3. En Apple Developer crear una clave APNs (`.p8`). No la suba al repositorio.
4. En Firebase → Cloud Messaging → Apple app, cargar la clave, el Key ID y el Team ID.
5. En Xcode confirmar Push Notifications y Background Modes → Remote notifications. El proyecto ya incluye `Runner.entitlements` con `aps-environment=development` y `UIBackgroundModes=remote-notification`.
6. Para App Store cambie `aps-environment` a `production`.

Sin ese plist y sin la clave APNs, Android puede recibir pushes y iOS queda compilable, pero el token de iOS no se emitirá.
