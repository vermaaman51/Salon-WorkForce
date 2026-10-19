# OSSC Workforce

Spring Boot serves the frontend and REST API; PostgreSQL stores tenant, store, account,
appointment and append-only order history data. Java 17 or newer and Maven are required.

## Run locally

Build with `mvn package`, then run `./start-local.ps1`. The launcher starts the existing
local PostgreSQL instance if needed and activates the `local` Spring profile:
`jdbc:postgresql://127.0.0.1:5433/salon`, username `salon`. The password is read from
Git-ignored `.local/credentials.json` into `LOCAL_DB_PASSWORD`. No Railway connection
is used by this profile. For IDE launches, select profile `local` and set
`LOCAL_DB_PASSWORD` to your local database password.

The launcher uses JDK 18, sets the JVM timezone to UTC and serves the app on port 8080.
Credentials, logs and backups remain in Git-ignored `.local/`. Mobile devices on the
same permitted network can use the PC's LAN IP and port 8080. Railway continues to use
the `prod` profile and its configured environment variables.

Login uses a 10-digit mobile number and password. Public signup is disabled. Existing
accounts and tenant/store/role assignments must be provisioned explicitly for a new
database. `ADMIN_PASSWORD` initializes the configured existing Admin account only when
its password hash is absent; startup never replaces an existing password.

## Final SQL schema

See [database/README.md](database/README.md) for the seven final table scripts, the
one-time adoption procedure for an existing database, and future schema changes.

- `db/migration/common/`: final definitions for tenant, users, role, store, user_role and orders.
- `db/migration/postgresql/V7__order_history.sql`: final history table and PostgreSQL triggers.
- `db/migration/h2/V7__order_history.sql`: test database equivalent.

Flyway selects the database-specific location and executes each version once. Subsequent
starts validate the recorded versions. There are no Java Flyway migrations or obsolete
create/alter/drop chains. Hibernate validates entity mappings; related entities use scalar
UUID foreign-key fields rather than lazy entity associations.

The local existing database was adopted at final V7 without modifying application rows.
Its previous metadata is retained in `flyway_schema_history_before_final_schema`, and its
pre-adoption backup is `.local/before-final-sql-schema.dump`. Any other database using the
former migration chain must be upgraded to former V13 and adopted before starting this
version. Empty databases run all seven final definitions automatically.

## Roles and workflows

- **Admin:** dashboard reports, store management, and store manager management.
- **Store Manager:** selected-store sales, completed/assigned appointments, Expert filters,
  receptionist/Expert management, and the full appointment workflow.
- **Receptionist:** customer queries, appointments, assignment, checkout and payments.
- **Expert:** staff identity and appointment assignee; no dedicated workspace yet.

Deleting a store manager removes its `user_role` assignment and deletes the `users`
record when no other assignments remain. Accounts shared with another role, store or
tenant are preserved. Both steps run in one transaction.

Admin reports default to today and All stores. From/To searches allow dates within the
past calendar month through today, with a maximum range of 30 inclusive calendar days.
The business timezone uses `TZ`, default `Asia/Kolkata`. Reports use appointment dates
and current order/payment state; they are not payment-receipt-date reports. The service
column is read directly, with no fallback to appointment details.

Appointments support multiple fixed services (Hair, Nail, Facial, Massage, Makeup,
Waxing, Skincare), stored as a comma-separated `service` string. Service charge is a
nonnegative decimal with up to two decimal places. Appointment details is optional;
query details is required. The date/time dialog commits with Okay and preserves the
previous selection on Cancel.

Customer checkout requires an assigned Expert. Payment Mode is optional for Pending
payment and required for Completed payment. Cash and UPI are the supported modes.
Mark paid completes an outstanding payment and requires a mode. This records payment
status only; it does not charge customers or integrate with a payment processor.

Expert forms do not request passwords. New Experts created without credentials cannot
sign in; edits preserve any existing password hash. Manager and receptionist creation
and editing require valid passwords (minimum 12 characters, maximum 72 UTF-8 bytes).
Other displayed data-entry fields are mandatory; filter controls remain optional.

## Order history

Every order INSERT, UPDATE or DELETE automatically appends a full database snapshot.
Each snapshot has a new UUID, increasing history_sequence, the original order_id,
event_type, snapshot_at and all order fields including service and payment_mode.
PostgreSQL blocks history UPDATE/DELETE/TRUNCATE and order TRUNCATE. Snapshot writes
share the order transaction and roll back together. History deliberately has no foreign
keys to live records so it survives their deletion.

Inspect snapshots with:

```sql
SELECT * FROM order_history
WHERE order_id = 'original-order-uuid'
ORDER BY history_sequence DESC;
```

Recovery is a deliberate database operation after checking tenant/store/assignee
references; no automatic restore endpoint is exposed.

## API and authorization

- Admin: `/api/admin/session`, `/stores`, `/managers`, `/sales`.
- Manager staff/dashboard: `/api/manager/session`, `/receptionists`, `/experts`, `/orders`.
- Manager appointments: `/api/manager/appointments/session`, `/experts`, `/orders` and
  `/orders/{id}/assign`, `/checkout`, `/payment`.
- Reception: `/api/reception/session`, `/experts`, `/orders` and the same order actions.

Store-specific routes require `storeId`; tenant/store ownership is rechecked in the
service and repository. Manager appointment APIs operate on appointments only, not
queries. Service entry points independently enforce roles. Cross-store/tenant IDs are
rejected. Sessions are server-side and expire after 30 minutes of inactivity. Protected
API requests require login; all writes require CSRF. Logout invalidates the session.

For API clients, retain cookies while fetching `/auth/csrf`, submitting form POST `/login`
with `mobileNo`, `password` and `_csrf`, then fetching the post-login CSRF token. Send its
returned header/token on writes. Basic/Bearer headers are not configured login methods.

## Verification and deployment

`mvn test` covers final schema persistence, roles, login, CRUD, input validation,
appointment/payment transitions, store/tenant isolation, CSRF, immutable snapshots and
transaction rollback. Legacy workflow tests remain disabled; their controllers are under
the inactive `legacy` profile and should not be enabled against this schema.

Connect the GitHub repository to a Railway application service. `railway.json` selects
the Dockerfile, which runs the test suite and starts the production profile. In the app
service's Variables, reference the PostgreSQL service variables (replace `Postgres` with
your database service name):

```text
PGHOST=${{Postgres.PGHOST}}
PGPORT=${{Postgres.PGPORT}}
PGDATABASE=${{Postgres.PGDATABASE}}
PGUSER=${{Postgres.PGUSER}}
PGPASSWORD=${{Postgres.PGPASSWORD}}
TZ=Asia/Kolkata
```

Railway supplies `PORT`. These references use private networking inside Railway.
Alternatively set `DB_URL` to a full `jdbc:postgresql://...` URL plus `DB_USER` and
`DB_PASSWORD`; raw `postgresql://...` URLs are not JDBC URLs. Never commit credentials.
The JVM uses UTC for database compatibility; `TZ` controls business report dates.
Startup applies Flyway migrations and `/health` confirms readiness. A new database has
no accounts; provision tenant, Admin user and role assignment before login, then set
`ADMIN_PASSWORD` to initialize its password (12+ characters).

Generate a Railway public domain for HTTPS access. Production expects HTTPS behind a
trusted proxy. Sessions are in memory, so use one replica unless shared sessions are
implemented. PostgreSQL backups should be retained separately from the application host.

## Platform owner and subscriptions

ROOT is a singleton platform account, separate from tenant Admins. On first startup,
set `ROOT_MOBILE` (10 digits) and `ROOT_PASSWORD` (12+ characters, at most 72 UTF-8
bytes). Startup creates it once and never resets its password. Local launches read
these values from `.local/root-credentials.json`. Keep this file private.
Sign in normally; ROOT opens `/root` to create/update tenants, set expiry, enable or
disable access, and create/edit/remove tenant Admin access.

V9 adds tenant `_is_active`, `segment_type` (BASE, PRO, PRO_PLUS), and nullable
`subscription_expires_at`. Existing tenants remain active on BASE with no expiry.
Expiry is an absolute timestamp; the ROOT form displays local time. Both manual
disabling and elapsed expiry block all tenant users, including existing sessions,
with “Your subscription has expired”. Renew by enabling the tenant and setting a
future expiry (or clearing expiry). ROOT remains accessible.

Only Admin workspaces show a live countdown in the final 24 hours. Subscription expiry
is captured after successful login and returned with the Admin session; the countdown
updates locally without subscription polling. This is an in-app reminder; no email/SMS delivery is configured.
Plan entitlements are described below. Accounts with assignments to multiple tenants
are blocked if any assigned
tenant is disabled or expired, preventing access through stale role assignments.

ROOT dashboard cards filter the tenant list by total, active, expiry today, or expiry
in the next seven days (tomorrow through day seven). Expiry date windows use
Asia/Kolkata calendar days and include disabled tenants with matching expiry dates.
Cards are white until selected; selecting the same card again clears the filter.
Tenant rows are sorted by expiry ascending, with no-expiry subscriptions last.
Run dashboard date/filter unit tests without a browser:
`node --test src/test/js/tenant-expiry.test.cjs`.


## Workspace plans

The workspace badge shows Base, Pro or Pro Plus for Admins, Store Managers and
Receptionists. Clicking it compares all three plans. ROOT changes the subscription
segment; tenant users contact the platform owner to upgrade.

| Plan | Stores | Store Managers | Receptionists | Printable invoices | CSV sales reports |
|---|---:|---:|---:|---|---|
| Base | 1 | 1 | 1 | No | No |
| Pro | 3 | 3 | 3 | Yes | No |
| Pro Plus | 7 | 14 | 14 | Yes | Yes |

Limits apply across the tenant. Store and staff creation lock the tenant row in the
same transaction as the insert, so concurrent creates cannot exceed the limit.
Admins can manage receptionists through their workspace. Receptionists created by
Admins and Store Managers share the same quota. Existing rows are retained on a
downgrade; further creation is blocked until counts fall below the current plan limit.

Pro and Pro Plus Store Managers and Receptionists can open a printable invoice from
completed appointments/checkout. Invoice access rechecks the current plan, role,
tenant, store and completed appointment on the server. Invoice numbers are short,
permanent sequence numbers (such as INV-000123), registered once per order.
Payment status is displayed, including outstanding payments.

Pro Plus Admin and Store Manager sales lists offer backend CSV downloads. Each export
rechecks the live subscription, plan, role and store ownership. CSV fields are quoted,
Excel-compatible and protected against spreadsheet formulas.
Plan metadata is included in existing session responses; no plan polling is added.

Run browser-free frontend unit tests with `node --test src/test/js/*.test.cjs`.
`mvn test` includes plan quotas, invoices, authorization and existing workflows.

Store Manager and Receptionist dashboards use the same appointment dashboard component.
Cards are Total appointments, Unassigned appointments, Running appointments (Pending or Assigned), Completed
appointments and Total sales (Completed appointments with Completed payment). Expert,
order status and payment status filters apply to both cards and the displayed list.
Selecting a card filters the list locally; only the selected card is green. Manager
CSV exports cover today's appointments for the selected store on eligible plans. Receptionist queries are
excluded from all appointment totals. Workspace plan badges sit below the profile
on Admin, Store Manager, Receptionist and manager appointment pages.

V10 adds optional `gst_number` columns to tenant/store and an `order_invoice` registry.
A new store defaults to the tenant GSTIN; the Admin can change or clear it for that
store. Later tenant changes do not rewrite existing store GST numbers. Invoices show
the store GSTIN when present, without adding tax charges to the recorded amount.
The plan comparison is compact, highlights the current plan in green, and shows all
three plans together. Invoice layout puts the centered title above left-aligned
business information, followed by customer details, service/amount and payment details.


## Paged order lists

V11 adds database-maintained `orders.modified_at`, a created-date index and tenant/store
indexes for latest-modified ordering. Existing rows take their last audit snapshot time.
New snapshots also include modified_at; previous immutable snapshots remain intact.

Manager and Receptionist order APIs return `{items, page, pageSize, total, totalPages, summary}`.
Pages are zero-based and fixed at 20 records, ordered by modified_at DESC then UUID DESC.
Supported filters are view (total, unassigned, running, completed, sales, checkout),
type (APPOINTMENT or QUERY), status, payment, expert (assignment UUID or UNASSIGNED),
and date. Manager APIs always scope to appointments and assigned stores. Filtering
happens before paging; dashboard summary totals cover all filtered appointments.
Admin sales retain the `orders` array with the same page metadata and full-range totals.

All order views have Previous/Next controls. CSV reports export all matching rows
through a dedicated authenticated backend request. Subscription reminders use the login-time snapshot
and never send timer-driven HTTP requests; backend subscription enforcement remains live.


## Backend CSV reports

- Admin: `GET /api/admin/sales/export?from=YYYY-MM-DD&to=YYYY-MM-DD&storeId=UUID`.
  The Download CSV report button reads the current From/To/Store input fields, including
  All stores. The existing past-month/30-day inclusive range validation applies.
- Store Manager: `GET /api/manager/sales/export?storeId=UUID`. The server chooses
  today in the configured business timezone (TZ, default Asia/Kolkata) at request time.
  Client date parameters cannot expand this range. Only the selected assigned store
  is included; dashboard tabs/expert/status filters do not change this day report.
- Both require a currently active, unexpired Pro Plus subscription. Base, Pro,
  Receptionists, wrong-role users and users outside the tenant/store cannot export.
- Exports include all matching appointments, not only paid appointments or the current
  page. Admin CSV columns match the sales data plus appointment date/time; Manager
  CSV also includes Expert. Queries are excluded.
- The backend streams records with a 200-row JDBC fetch size in a read-only transaction.
  Async processing is bounded to 2 core / 4 maximum workers and 20 queued requests.
  The browser downloads the server CSV; there is no client-side CSV generation.
