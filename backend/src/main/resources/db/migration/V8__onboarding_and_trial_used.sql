-- Persist clinic onboarding completion and one-time BASIC monthly trial usage.
-- Do not rewrite previous Flyway versions.

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS trial_used BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS trial_used BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tenants t
SET trial_used = TRUE
WHERE COALESCE(t.trial_used, FALSE) = FALSE
  AND (
        t.status IN ('TRIAL', 'TRIALING')
        OR t.trial_ends_at IS NOT NULL
        OR EXISTS (
            SELECT 1
            FROM subscriptions s
            WHERE s.tenant_id = t.id
              AND (s.trial = TRUE OR s.status IN ('TRIAL', 'TRIALING'))
        )
      );

UPDATE users u
SET trial_used = TRUE
WHERE COALESCE(u.trial_used, FALSE) = FALSE
  AND EXISTS (
        SELECT 1
        FROM tenant_memberships m
        JOIN tenants t ON t.id = m.tenant_id
        WHERE m.user_id = u.id
          AND m.status = 'ACTIVE'
          AND t.trial_used = TRUE
  );

-- Existing clinics with a tenant must not be sent back to onboarding.
UPDATE clinic_signups cs
SET status = 'COMPLETED',
    completed_at = COALESCE(cs.completed_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
WHERE cs.status <> 'COMPLETED'
  AND cs.tenant_id IS NOT NULL
  AND EXISTS (
        SELECT 1
        FROM tenants t
        WHERE t.id = cs.tenant_id
          AND t.status IN ('ACTIVE', 'TRIAL', 'TRIALING', 'PAST_DUE', 'GRACE_PERIOD', 'SUSPENDED', 'PAUSED', 'CANCELED', 'CANCELLED')
  );

INSERT INTO clinic_signups (
    user_id, tenant_id, email, status, plan_id, billing_cycle, completed_at, created_at, updated_at
)
SELECT m.user_id,
       t.id,
       u.email,
       'COMPLETED',
       t.plan_id,
       s.billing_cycle,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM tenant_memberships m
JOIN tenants t ON t.id = m.tenant_id AND t.deleted = FALSE
JOIN users u ON u.id = m.user_id AND u.deleted = FALSE
JOIN roles r ON r.id = m.role_id
LEFT JOIN LATERAL (
    SELECT sub.billing_cycle
    FROM subscriptions sub
    WHERE sub.tenant_id = t.id
    ORDER BY sub.started_at DESC NULLS LAST, sub.id DESC
    LIMIT 1
) s ON TRUE
WHERE m.status = 'ACTIVE'
  AND r.code IN ('TENANT_OWNER', 'TENANT_ADMIN')
  AND t.status IN ('ACTIVE', 'TRIAL', 'TRIALING', 'PAST_DUE', 'GRACE_PERIOD', 'SUSPENDED', 'PAUSED')
  AND NOT EXISTS (
        SELECT 1 FROM clinic_signups cs WHERE cs.user_id = m.user_id
  );
