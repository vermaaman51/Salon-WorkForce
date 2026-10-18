# Studio — Salon Workforce

Spring Boot serves the frontend and REST API; PostgreSQL stores tenant, store, user and role data. JPA entities are in `com.salon.entity`; schema changes use Flyway and Hibernate validates the mappings.

## Run locally

Build using JDK 17+ and Maven: `mvn package`. Then run `./start-local.ps1`. This computer's launcher uses PostgreSQL 18 and JDK 18, PostgreSQL at `127.0.0.1:5433` (database/user `salon`), and the app at http://localhost:8080. Local credentials, logs and persistent database files are in Git-ignored `.local/`.

Sign in at `/login` with mobile number `9616543935` and the ADMIN password. Email and tenant username login are not supported. The existing Aman Verma ADMIN account receives the local `ADMIN_PASSWORD` only if it has no password hash; find this password in `.local/credentials.json`. Passwords are BCrypt hashes. Existing password hashes are never overwritten by startup. Public signup is disabled under this schema.

## ADMIN workspace

ADMIN login opens Dashboard by default. The sidebar includes Dashboard, Manage Store, and Manage Store Manager. Lists, counts and mutations use the ADMIN user's tenant assignment from PostgreSQL. The current workspace supports one ADMIN tenant per login; multiple ADMIN tenant assignments are rejected until tenant switching is implemented.

- Stores: add name, city and address; edit or delete through row actions.
- Store managers: add name, email, mobile number, address, store and password; edit details or reassign stores. A login password is required when creating or editing an account. Manager accounts use the STORE_MANAGER role and mobile-number login.
- Removing a manager removes that store assignment; the user identity remains for future use and other assignments. Removing a store with assignments is blocked; first reassign or remove managers.
- Non-ADMIN users do not receive the ADMIN sidebar and cannot access ADMIN APIs. Store managers open their own dashboard at `/manager/dashboard`, with Manage Receptionist and Manage Expert navigation.

All writes require CSRF tokens. Server-side authorization and tenant scoping apply independently of the UI. Cross-tenant IDs return not found. An account shared with another tenant cannot be edited from this workspace. Database uniqueness and foreign keys reject conflicting writes.

## Schema

| Entity | Table | Fields |
|---|---|---|
| Tenant | tenant | id, tenant_name, username, country |
| Store | store | id, city, address, tenant_id, name |
| User | users | id, name, mobileno, email, address, password_hash |
| Role | role | id, roletype |
| UserRole | user_role | id, user_id, role_id, store_id, tenant_id |

All IDs and foreign keys use UUID. UserRole requires a tenant and permits a null store for ADMIN assignments. The requested `usernamem` was interpreted as `username`; `users` avoids PostgreSQL's reserved USER keyword.

V3 replaced the old workforce tables; V4 added UserRole tenant ownership; V5 adds login credentials and unique emails; V6 enforces unique mobile numbers for login. Historical migrations are retained for Flyway checksum compatibility. Legacy controllers and tests remain inactive as references; do not enable the legacy profile against this schema.

## API and verification

ADMIN-only endpoints: `GET /api/admin/session`, `GET/POST /api/admin/stores`, `PUT/DELETE /api/admin/stores/{id}`, `GET/POST /api/admin/managers`, `PUT/DELETE /api/admin/managers/{assignmentId}`. The manager identifier is the UserRole assignment UUID. `GET /auth/csrf` supplies the CSRF token; form POST `/login` with `mobileNo` and `password` authenticates; POST `/logout` ends the session. `GET /health` checks the database.

`mvn test` verifies UUID entity persistence, ADMIN login/default routing, store and manager CRUD, CSRF, tenant isolation and non-ADMIN rejection. Old workforce behavior tests are disabled. Live PostgreSQL verification also covers UI creation/modification, store deletion protection and manager access rejection. Temporary verification records are removed afterward.

## Deployment

Use the included Dockerfile/Railway configuration. The production profile reads PGHOST, PGPORT, PGDATABASE, PGUSER and PGPASSWORD; configure PORT through the platform, TZ to the salon timezone, and ADMIN_PASSWORD for initial local ADMIN credential provisioning. Provision tenants/users/roles explicitly; no public ADMIN signup exists. The production profile expects HTTPS behind a trusted proxy. Sessions are in memory, so use one replica and expect sign-in after restarts.


## Store manager workspace

Mobile/password login routes STORE_MANAGER users to `/manager/dashboard` by default. ADMIN users retain their ADMIN dashboard. Managers can select only stores assigned to them and see receptionist/expert counts for the selected store.

Manage Receptionist and Manage Expert each support add, modify and delete row actions. Creation requires name, email, unique mobile number, address and a password of at least 12 characters. Passwords use BCrypt; a password is required when creating or editing staff. Staff accounts use RECEPTIONIST or EXPERT roles and mobile-number login. Receptionists have the customer/order workspace described below; a expert-specific workspace is not yet implemented.

Data uses the existing users, role and user_role tables. Tenant and store are derived from the manager's current database assignment, never accepted from the staff form. API requests for unassigned stores, another tenant or the wrong staff category are rejected. Editing users with other role assignments is blocked to prevent changing shared accounts. Deletion removes the selected staff assignment while preserving the user identity and other roles.

Manager-only API endpoints: GET `/api/manager/session`; GET/POST `/api/manager/receptionists` and `/api/manager/experts`; PUT/DELETE `/api/manager/{category}/{assignmentId}`. All staff endpoints require a `storeId` query parameter and all writes require CSRF. The backend rechecks current manager assignments on each request. ADMIN, receptionist and expert sessions cannot call manager-only endpoints unless they also hold STORE_MANAGER.

`StoreManagerTests` covers default routing, both staff CRUD flows, mobile login, same-tenant store isolation, cross-tenant isolation, wrong-category IDs, role rejection, revoked assignments, CSRF and duplicate mobile validation. Browser and live PostgreSQL checks also verify staff creation, modification and removal. Temporary test records are removed after verification.

## Receptionist workspace and orders

RECEPTIONIST mobile/password login opens `/reception/dashboard`. The sidebar includes Dashboard, Customer queries, Appointments, and Checkout & payments. Receptionists can select only their assigned stores; all queries and mutations recheck that assignment in PostgreSQL. ADMIN, STORE_MANAGER and EXPERT roles do not grant receptionist API access on their own.

- Add a customer query with name, mobile number and query details; resolve it when handled.
- Add an appointment with customer details, service description, date/time and amount. Amount is stored as NUMERIC(12,2), never floating point in PostgreSQL.
- Assign or reassign an open appointment to a EXPERT UserRole belonging to that same tenant and store. Expert availability/time-overlap planning is outside this workflow.
- Complete an assigned appointment at customer exit, recording payment as PENDING or COMPLETED. Completed orders cannot be reassigned or checked out again. A completed appointment with pending payment can later be marked paid.
- Queries do not require experts or payments; they keep amount zero and payment PENDING, which is not included in appointment outstanding-payment totals.

Flyway V7 creates `orders` (ORDER is a reserved SQL keyword) and `com.salon.entity.Order` maps it. Fields: UUID id, tenant_id, store_id, assignee (nullable expert UserRole UUID), order_type (QUERY/APPOINTMENT), order_status (PENDING/ASSIGNED/COMPLETED), payment_status (PENDING/COMPLETED), customer_name, customer_mobile, details, amount, appointment_at and created_at. Composite foreign keys enforce consistent tenant/store ownership and same-store assignee references. Database checks enforce valid states and nonnegative amounts. Order transitions lock the order row to serialize concurrent updates. Referenced expert assignments and stores cannot be deleted while order history depends on them.

Reception APIs: GET `/api/reception/session`, `/experts`, `/orders`; POST `/orders`; PATCH `/orders/{id}/assign`, `/orders/{id}/checkout`, `/orders/{id}/payment`. All except session require `storeId`; writes require CSRF. Assignment accepts `{assignee: userRoleUuid}`, checkout accepts `{paymentStatus: PENDING|COMPLETED}`, and payment marks an already-completed appointment paid. Store and tenant are server-derived. This records payment status only; it does not charge customers or integrate a payment processor. Amounts use the salon's configured currency; currency conversion and partial payments are not implemented.

`ReceptionTests` covers appointment creation and amount validation, same-store expert filtering, wrong-role and cross-store assignment rejection, query resolution, checkout with paid/pending states, later payment collection, invalid transitions, role checks and CSRF. The full browser workflow and PostgreSQL schema were verified with temporary records that were then removed. The pre-migration backup is `.local/before-reception-orders.dump`.

## Backend package layout

The main Spring Boot application stays in `com.salon` so component scanning covers the packages below:

| Package | Responsibility |
|---|---|
| `com.salon.controller` | HTTP routes, request validation, response statuses and global API error handling |
| `com.salon.service` | ADMIN/manager/reception business rules, authorization scope, account login and transaction boundaries |
| `com.salon.repository` | Typed JDBC database operations and parameterized SQL |
| `com.salon.dto` | Request records grouped by workflow |
| `com.salon.entity` | JPA entity mappings |
| `com.salon.config` | Spring Security wiring and ADMIN bootstrap configuration |
| `com.salon.constant` | Role constants and shared OrderType, OrderStatus and PaymentStatus enums |
| `com.salon.utility` | Current-user UUID conversion, password policy and local timestamp JSON serialization |
| `com.salon.legacy` | Inactive former workforce controllers, services and security configuration, retained under the legacy profile |

Active controllers delegate to services, and SQL is confined to repositories. Transactional writes are declared on public service methods so row locks and multi-table updates share the same transaction. HTTP URLs, JSON field names and database migrations are unchanged by this package refactor. `WorkspaceController` owns the role-specific page routes; `AccountUserDetailsService` loads mobile-number credentials and authorities from `AccountRepository`.

## Authentication and API access

The application uses server-side Spring Security sessions. Every protected API request requires an authenticated session established by mobile number/password login. A CSRF token alone does not authenticate a caller. Anonymous API reads and writes return JSON HTTP 401; wrong-role access or authenticated writes without a valid CSRF token return JSON HTTP 403. Public endpoints are limited to login assets, login/CSRF setup, root redirect and the minimal health check.

Role checks are applied both by the HTTP security filter chain and `@PreAuthorize` on AdminService, StoreManagerService and ReceptionService. Database-derived tenant/store checks still run inside services. Changing the client to Postman does not change permissions: authorized API clients can call only what their logged-in account permits.

Login rotates the session ID. Sessions expire after 30 minutes of inactivity and use cookie-only tracking with HttpOnly/SameSite cookies (Secure in the HTTPS production profile). CSRF-protected POST logout clears authentication, invalidates the server session and deletes JSESSIONID. A copied session ID stops working after logout. `com.salon.security.ApiSecurityHandler` supplies consistent JSON authentication/authorization errors.

For an authorized Postman/API client: GET `/auth/csrf` retaining the session cookie; POST `/login` using form fields `mobileNo`, `password` and `_csrf`; then GET `/auth/csrf` again for the post-login token. Retain the authenticated session cookie and send the returned CSRF header/token on writes. No public API key or hardcoded frontend secret grants access. Arbitrary Bearer or Basic headers are not authentication methods in this application.

`ApiAuthenticationTests` covers anonymous API reads and all mutation routes, anonymous requests carrying a valid CSRF token, wrong-role rejection, authenticated writes without CSRF, service invocation without a controller, session-ID rotation and logout invalidation. Live HTTP checks verify 401/403 behavior and rejection of a previously valid session after logout.

Receptionist filters: Customer queries supports status, created date and All/Unassigned assignee filters (queries are unassigned in the current model). Appointments supports status, scheduled appointment date, expert/Unassigned and payment status. Filters combine with AND, are independent between tabs, persist through data refresh, and reset on store changes. Clear filters restores all results; result counts and no-match guidance are shown. Filtering runs on the already store-scoped API results; dashboard totals continue to summarize the whole selected store. Browser verification covered combined status/date/expert/payment filtering, pending versus paid, unassigned appointments, query creation-date filtering, no matches and clearing filters. Temporary verification data was removed afterward.

## Foreign-key entity mappings

Entities store related UUIDs directly: Store.tenantId; UserRole.userId, roleId, storeId and tenantId; Order.tenantId, storeId and assignee. PostgreSQL foreign-key constraints remain enforced by Flyway migrations. No entity associations or lazy proxies are used. The orders endpoint reads only orders and returns assignee as a UserRole UUID; expert_name is no longer returned. The receptionist UI resolves names from its separately loaded, store-scoped expert list. Authorization and assignment validation still query role/store relationships.


Store manager dashboard: Sales, Completed orders and Assigned to experts tabs show all-time appointment activity for the selected assigned store. Sales lists customer, service (the appointment details), amount, order/payment status and expert. Collected sales includes only completed appointments marked paid. Customer queries are excluded. GET /api/manager/orders?storeId=UUID enforces current manager assignment and tenant scope and reads order fields without joins. Staff management remains available in the sidebar.


ADMIN daily sales: GET /api/admin/sales returns today's appointment sales across the authenticated ADMIN tenant. The business day uses TZ (default Asia/Kolkata), with inclusive midnight and exclusive next midnight against appointment_at. It returns store/customer/service/amount/order/payment status without expert fields. Collected sales totals completed appointments marked paid; unpaid amount covers today's appointments with pending payment. This is appointment-day reporting, not payment-receipt-day reporting because no payment timestamp is stored.


Appointments: V8 adds orders.service (VARCHAR(255)), separate from details. New appointments require one or more fixed services: Hair, Nail, Facial, Massage, Makeup, Waxing, Skincare. The API accepts a comma-separated service string and validates, trims and deduplicates selections. Queries do not require services. The date/time dialog commits the selection with Okay; Cancel preserves the previous value. Sales dashboards read only service; missing service remains null, and details is never used as the service name.


## Required fields and order history

All active data-entry forms require their displayed fields (filter controls remain optional). Mobile numbers must be exactly 10 ASCII digits, with no country prefix, spaces or letters. The browser blocks invalid characters and the backend validates requests, including login. Manager/staff edit forms require re-entering a valid password; minimum 12 characters and maximum 72 UTF-8 bytes still apply. Appointment services, details, date/time and amount remain required; queries require only their query fields.

V9 adds order_history and snapshots every existing order as BASELINE. Each snapshot has a new UUID id, an increasing history_sequence, the original orders.id as order_id, event_type, snapshot_at and all current order columns. V10 installs database triggers: INSERT/UPDATE snapshots use the new order state, DELETE snapshots use the last state. History writes share the order transaction and roll back together. PostgreSQL blocks UPDATE/DELETE/TRUNCATE on history, and blocks TRUNCATE on orders to prevent bypassing delete snapshots. There is no foreign key from history to live records, so history survives deletion. Direct SQL writes to orders are captured too. Existing invalid legacy mobile values are preserved; new form/API input follows the stricter rule.

Inspect snapshots in TablePlus with:

```sql
SELECT * FROM order_history
WHERE order_id = 'original-order-uuid'
ORDER BY history_sequence DESC;
```

Recovery is a deliberate database operation from a chosen snapshot after checking tenant/store/assignee references; no automatic restore endpoint is exposed. The pre-migration database backup is .local/before-order-history.dump.

ADMIN report filters: /api/admin/sales accepts optional date=YYYY-MM-DD and storeId=UUID. Date defaults to today in the business timezone and is limited to today and the preceding 29 days. Store IDs are checked against the ADMIN tenant before querying. Report rows and totals use the selected appointment date and store; omitted storeId means all tenant stores. The dashboard has date/store controls with Search and resets to today/all stores whenever Dashboard is opened. Reports reflect the current state of orders for the chosen appointment day.


ADMIN range reporting supersedes the rolling 30-day lookback: use /api/admin/sales?from=YYYY-MM-DD&to=YYYY-MM-DD with optional storeId. Any past/current range is allowed when it spans at most 30 calendar days inclusive; future, reversed, incomplete and oversized ranges are rejected. No dates defaults to today/today. The legacy date parameter still selects a single day and cannot be combined with from/to. Both report controls default to today whenever Dashboard opens. Compact summary spacing preserves at least five visible rows on tested desktop sizes, with additional rows scrolling within the table.


Admin report availability is now limited to the past calendar month through today in the business timezone. For example, 18 October permits dates from 18 September onward. Both date pickers receive this minimum from the API, and the backend enforces it for both range and legacy single-day requests. The separate maximum of 30 inclusive calendar days per report remains enforced. The collected-sales caption has been removed from the Admin dashboard.


Store manager dashboard layout is compact and keeps the activity table in its own scroll region, with seven visible rows verified at desktop sizes 1365x768 and 1024x600. Expert filtering uses the already-authorized selected-store order data and applies to Sales/Completed/Assigned tabs and summary totals. All experts and Unassigned are available; filters reset when switching stores and persist across refresh within a store.


Appointment form update: amount is labelled Service charge. Appointment details is optional and appears last in the form. Omitted/blank appointment details is stored as an empty string; query details remains mandatory. Existing numeric amount validation and snapshot capture continue to apply.


Customer checkout now requires Payment Mode (CASH or UPI) for appointments, including pending checkouts. Mark paid also requires paymentMode and can update the mode when payment is collected later; repeating an already completed payment is rejected. Queries do not require/store a mode. V12 adds nullable payment_mode columns with allowed-value constraints to orders and order_history and updates the snapshot trigger. Existing rows retain null because their payment mode was not recorded. The API checkout body includes paymentStatus and paymentMode; payment body includes paymentMode. Pre-migration backup: .local/before-payment-mode.dump.


Payment Mode validation: checkout mode is optional when paymentStatus=PENDING and mandatory when paymentStatus=COMPLETED. Switching status in the form updates required validation immediately. Mark paid always requires Cash/UPI because it completes payment. A blank optional selection is omitted from the request and stored as null.


Store Manager appointment workspace: /manager/appointments is linked from the manager sidebar and reuses the receptionist appointment forms and client logic. Manager APIs use /api/manager/appointments with session, experts, orders, assign, checkout and payment endpoints. Each public service entry enforces its own role; shared private workflow logic scopes stores using that role's current database assignment. Managers can create/list/update appointments only, not customer queries. Creation, assignment, checkout and later payment retain the existing validation, row locking and order_history snapshots. /api/reception remains receptionist-only.


Expert role rename: V13 renames WORKER to EXPERT in the role table while preserving role UUIDs, account assignments, order assignees and history. Active UI, backend role checks and API paths now use Expert/EXPERT/experts (including /api/manager/experts, /api/reception/experts and /api/manager/appointments/experts). Existing expert mobile/password credentials remain valid. Pre-migration backup: .local/before-expert-role-rename.dump.


Expert form password update: Add/Modify Expert no longer displays Login password. Expert creation accepts no password and stores a null password_hash, so these new records cannot sign in. Expert edits without password preserve any existing credential hash. Receptionist creation/edit still requires a valid login password. Other identity validation remains required.

