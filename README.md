# OSSC Workforce

Spring Boot serves the frontend and REST API; PostgreSQL stores tenant, store, account,
appointment and append-only order history data. Java 17 or newer and Maven are required.

## Run locally

Build with `mvn package`, then run `./start-local.ps1`. The launcher connects to Railway
using Git-ignored `.local/railway.json` with `DB_URL`, `DB_USER` and `DB_PASSWORD`.
Use a JDBC URL such as `jdbc:postgresql://PUBLIC_HOST:PUBLIC_PORT/railway?sslmode=require`.
Railway's private hostname is accessible only inside Railway. The launcher uses JDK 18,
sets the JVM timezone to UTC and serves the app on port 8080. Credentials, logs and
backups remain in Git-ignored `.local/`. Mobile devices on the same permitted network
can use the PC's LAN IP and port 8080.

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
