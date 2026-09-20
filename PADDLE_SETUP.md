# Paddle Billing setup — Animexa / Vetora

Spring Boot is the source of truth for Paddle Billing. Angular only uses the public client-side token. There is no Next.js, Node, or Route Handler in this integration.

## Architecture overview

```
Angular (Paddle.js overlay)  →  POST /api/v1/billing/checkout
                               ←  priceId + custom_data { tenant_id, tenant_slug }
Paddle Checkout  →  Paddle Billing
Paddle webhooks  →  POST /api/v1/billing/webhooks/paddle  (public, signature-verified)
                 →  subscriptions / tenants (PostgreSQL)
Daily UTC job    →  GRACE_PERIOD past gracePeriodEndsAt → SUSPENDED
Security filter  →  blocks clinic APIs when status is SUSPENDED or CANCELED
                 →  allows /auth/me, logout, switch-tenant, /billing/**
                 →  SUPER_ADMIN is never blocked
```

Sandbox API: `https://sandbox-api.paddle.com`  
Live API: `https://api.paddle.com`  
Currency: USD. Grace period after the first confirmed failed/past-due payment: 10 calendar days (UTC).

## Files created and modified

### Created

- `backend/src/main/java/com/animalin/billing/**` — configuration, DTOs, API, webhooks, access filter, grace job, catalog
- `backend/src/main/java/com/animalin/billing/paddle/**` — RestClient Paddle API client and HMAC verifier
- `backend/src/main/resources/db/migration/V4__paddle_billing.sql`
- `backend/src/test/java/com/animalin/billing/**`
- `backend/application-secrets.example.properties`
- `application-secrets.example.properties`
- `web/src/app/pages/clinic/billing/billing.page.ts`
- `web/src/app/core/services/billing.service.ts`
- `web/src/app/core/services/paddle.service.ts`
- `PADDLE_SETUP.md` (this file)

### Modified

- `Plan` / `Subscription` entities and repositories
- `SecurityConfig`, `ApiException`, `ApiError`, `AdminController`, `AdminService`
- `application.properties`, `application.yml`, `.gitignore`
- Angular routes, shell, interceptor, i18n, environments, admin plans page

Existing `plans` and `subscriptions` tables were extended. Flyway V1–V3 were not rewritten.

## Local Sandbox setup

1. Create a [Paddle Sandbox](https://sandbox-vendors.paddle.com) account (separate from live).
2. Copy `backend/application-secrets.example.properties` to `application-secrets.properties` in the directory you start the API from (`backend/` if you run `./mvnw spring-boot:run` there).
3. Fill in:

```properties
PADDLE_API_KEY=pdl_sdbx_apikey_...
PADDLE_ENVIRONMENT=sandbox
PADDLE_WEBHOOK_SECRET=pdl_ntfset_...
PADDLE_CLIENT_TOKEN=test_...
PADDLE_GRACE_PERIOD_DAYS=10
```

4. Start PostgreSQL and the API:

```bash
docker compose up -d postgres
cd backend && ./mvnw spring-boot:run
cd web && npm start
```

`spring.config.import=optional:file:./application-secrets.properties` is already configured. Never commit `application-secrets.properties` or `.env`.

## Required Paddle permissions

Create a **server-side API key** (Developer tools → Authentication) with at least:

- `product.read` / `product.write`
- `price.read` / `price.write`
- `customer.read`
- `subscription.read` / `subscription.write`
- `transaction.read`
- customer portal session create (included with subscription/customer write on current Billing keys)

Create a separate **client-side token** for Paddle.js. That token is the only Paddle credential allowed in Angular.

## Environment variables

| Variable | Where | Secret? |
| --- | --- | --- |
| `PADDLE_API_KEY` | Spring / Railway | Yes |
| `PADDLE_ENVIRONMENT` | Spring / Railway (`sandbox` or `production`) | No |
| `PADDLE_WEBHOOK_SECRET` | Spring / Railway (per notification destination) | Yes |
| `PADDLE_CLIENT_TOKEN` | Spring / Railway; Angular may also use `environment.paddleClientToken` | No (public) |
| `PADDLE_GRACE_PERIOD_DAYS` | Default `10` | No |
| `PADDLE_WEBHOOK_TOLERANCE_SECONDS` | Default `5` | No |
| `PADDLE_GRACE_PERIOD_JOB_ENABLED` | Default `true` | No |
| `PADDLE_GRACE_PERIOD_CRON` | Default `0 5 0 * * *` UTC | No |
| `PADDLE_CANCEL_ON_SUSPEND` | Default `false` — do not auto-cancel in Paddle | No |

Placeholders in `application.properties`:

```
paddle.api-key=${PADDLE_API_KEY:}
paddle.environment=${PADDLE_ENVIRONMENT:sandbox}
paddle.webhook-secret=${PADDLE_WEBHOOK_SECRET:}
paddle.client-token=${PADDLE_CLIENT_TOKEN:}
paddle.grace-period-days=${PADDLE_GRACE_PERIOD_DAYS:10}
```

## How to create products and prices

Preferred: platform admin `POST /api/v1/admin/plans` with `syncToPaddle: true` (requires `SUPER_ADMIN` and a configured API key). That creates a Paddle product (`tax_category: saas`) plus a monthly USD price and an optional annual USD price, then stores `pro_…` / `pri_…` on the local plan.

Dashboard fallback:

1. Sandbox → Catalog → Products → New product (tax category **SaaS**).
2. New price → Recurring → Monthly → USD amount.
3. Optional second price → Yearly.
4. Copy the IDs into **Plataforma → Planes** (`paddleProductId`, `paddleMonthlyPriceId`, `paddleAnnualPriceId`).

Amounts in the Paddle API are the lowest currency unit as a string (`"2900"` = $29.00). Changing a local monthly amount creates a **new** Paddle price and archives the old one for new sales; existing subscribers keep the archived price unless you migrate them.

## How to create the client-side token

Sandbox → Developer tools → Authentication → **Client-side token**. Put it in `PADDLE_CLIENT_TOKEN`. Angular receives it from `GET /api/v1/billing/config` and `POST /api/v1/billing/checkout`. You may also paste it into `web/src/environments/environment.ts` (`paddleClientToken`) for local builds. Never put an API key or webhook secret in Angular.

## How to create the webhook destination

1. Sandbox → Developer tools → Notifications → New destination.
2. Type: Webhook.
3. URL: `https://<api-host>/api/v1/billing/webhooks/paddle`
4. For local development, tunnel the API (`hookdeck listen 8080` or ngrok) and use that HTTPS URL.
5. Copy the destination secret into `PADDLE_WEBHOOK_SECRET`. It is shown once and is **not** the API key.

The endpoint is public (no JWT). All other `/api/v1/billing/**` routes require authentication. The raw body is hashed with HMAC-SHA256 against `ts:body` from `Paddle-Signature` (`ts` + `h1`). Invalid, missing, or expired signatures return HTTP 400 so Paddle retries. Duplicate `event_id` values return HTTP 200 without applying the event again.

## Required webhook events

Subscribe at least:

- `customer.created`, `customer.updated`
- `subscription.created`, `subscription.updated`, `subscription.activated`, `subscription.canceled`, `subscription.past_due`
- `transaction.completed`, `transaction.payment_failed`, `transaction.past_due`, `transaction.updated`

`subscription.updated` / `transaction.updated` are inspected by payload `status` (for example `past_due`, `active`, `canceled`, `completed`).

## How to obtain the webhook secret

On the notification destination, copy **Secret key** (`pdl_ntfset_…`). Each destination (sandbox vs live, each URL) has its own secret. Rotate by creating a new destination and updating `PADDLE_WEBHOOK_SECRET`.

## Railway configuration

Set the variables above on the **API** service. Do not put `PADDLE_API_KEY` or `PADDLE_WEBHOOK_SECRET` on the Angular/Netlify site.

Webhook URL:

`https://<railway-api-domain>/api/v1/billing/webhooks/paddle`

The grace job uses PostgreSQL `SELECT … FOR UPDATE SKIP LOCKED` and a UTC cron so multiple Railway replicas do not double-suspend the same row. `PADDLE_CANCEL_ON_SUSPEND` stays `false` unless you explicitly want Paddle cancellation when the local grace period expires.

## Netlify / Angular configuration

Production API base is `environment.prod.ts` → `apiUrl`. Optional public token:

```ts
paddleEnvironment: 'sandbox', // change to 'production' at go-live
paddleClientToken: 'test_…'   // or leave empty and use GET /billing/config
```

Spanish is the default UI language. Checkout locale is `es` unless the authenticated user locale is `en`.

## Sandbox test cards and testing procedure

Only test cards work in sandbox.

| Card | Result |
| --- | --- |
| `4242 4242 4242 4242` | Success, no 3DS |
| `4000 0038 0000 0446` | Success with 3DS |
| `4000 0000 0000 0002` | Declined |
| `4000 0027 6000 3184` | First payment succeeds, later renewals fail (dunning) |

Any future expiry, any CVC.

Procedure:

1. Log in as a `TENANT_ADMIN` (demo: `tina.r@example.net` / `Admin123!`).
2. Open **Facturación**, choose a monthly plan whose `pri_…` exists.
3. Complete checkout with `4242…`.
4. Confirm `transaction.completed` and `subscription.created` in Paddle logs and that `GET /api/v1/billing/subscription` is `ACTIVE`.
5. Customer portal button should return a short-lived URL from the API (the API key never reaches the browser).

## How to test failed payments and the 10-day grace period

1. Use `4000 0000 0000 0002` or simulate `transaction.past_due` / `subscription.past_due`.
2. Local status becomes `GRACE_PERIOD`. `firstPaymentFailedAt` is set once. `gracePeriodEndsAt` = that instant + 10 days.
3. Replay the same failure: the deadline must **not** move.
4. Clinic APIs stay available; the amber banner shows the exact suspension date.
5. After `gracePeriodEndsAt`, the UTC job (or a simulated clock in tests) sets `SUSPENDED` and `suspendedAt`. Data and users are kept. Only billing, session, and logout remain available. `SUPER_ADMIN` is not blocked.
6. A later successful `transaction.completed` restores `ACTIVE` and clears grace/suspension fields.

To exercise grace quickly in sandbox, temporarily set `PADDLE_GRACE_PERIOD_DAYS=0` is **not** supported (minimum 1). Use the webhook simulator with a past `occurred_at` plus a unit/integration test, or set `grace_period_ends_at` in the database for a staging tenant.

## Production migration checklist

- [ ] Live Paddle account, approved domain, live API key (`pdl_live_apikey_…`) and live client token
- [ ] Recreate products/prices in **live** (IDs are not shared with sandbox)
- [ ] Map live `pro_` / `pri_` IDs on plans
- [ ] Live notification destination + new webhook secret
- [ ] `PADDLE_ENVIRONMENT=production`
- [ ] `environment.prod.ts` `paddleEnvironment: 'production'`
- [ ] Confirm Railway webhook URL is HTTPS and returns 2xx within 5 seconds
- [ ] Backfill existing Paddle customers if any
- [ ] Disable demo data in production (`SPRING_PROFILES_ACTIVE` without the seeder profile if applicable)

## Security considerations

- Tenant id is taken from the JWT, never from the browser, except as Paddle `custom_data` that the server generated.
- Checkout `priceId` must match an enabled plan row.
- Webhook signature is verified before parse/persist. Comparison is constant-time.
- API keys, webhook secrets, and payment payloads are not logged.
- `PaddleProperties.toString()` redacts secrets.
- Suspended tenants cannot call clinic endpoints; they can restore payment through billing.
- Platform administrators (`SUPER_ADMIN`) bypass tenant subscription blocking.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| Every webhook returns 400 | Wrong `PADDLE_WEBHOOK_SECRET` for that destination, or body was parsed before verification |
| 200 but no subscription row | Event had no `custom_data.tenant_id` and no matching `paddle_customer_id` |
| Checkout opens then fails | Price ID not in the local `plans` table, or sandbox token used against live (or the reverse) |
| Portal 400 | Tenant has not completed a checkout, so `paddle_customer_id` is null |
| Clinic 403 `TENANT_SUBSCRIPTION_SUSPENDED` | Grace expired or Paddle cancellation took effect; open `/billing` |
| Duplicate events | Expected; unique `paddle_event_id` returns 200 |
| Multiple Railway instances double-run the job | Should not suspend twice; `SKIP LOCKED` + status check. Confirm cron timezone UTC |

## API contract (Angular)

- `GET /api/v1/billing/plans` — enabled plans and public price IDs
- `GET /api/v1/billing/config` — environment, client token, plans
- `GET /api/v1/billing/subscription` — current tenant subscription, grace dates, access flags
- `POST /api/v1/billing/checkout` — `{ priceId, billingCycle }` → Paddle.js payload
- `POST /api/v1/billing/customer-portal` → `{ url }`
- `POST /api/v1/billing/subscription/cancel` — `{ effectiveFrom: "next_billing_period" \| "immediately" }`
- `POST /api/v1/billing/subscription/change-plan` — `{ priceId }`
- Suspended error: HTTP 403, `code: TENANT_SUBSCRIPTION_SUSPENDED`, `details.gracePeriodEndsAt` when present
