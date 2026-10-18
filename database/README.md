# Final SQL schema

Flyway runs each migration once and records it in `flyway_schema_history`.
There are seven final table definitions:

| Version | Table | File |
|---|---|---|
| V1 | tenant | `src/main/resources/db/migration/common/V1__tenant.sql` |
| V2 | users | `src/main/resources/db/migration/common/V2__users.sql` |
| V3 | role | `src/main/resources/db/migration/common/V3__role.sql` |
| V4 | store | `src/main/resources/db/migration/common/V4__store.sql` |
| V5 | user_role | `src/main/resources/db/migration/common/V5__user_role.sql` |
| V6 | orders | `src/main/resources/db/migration/common/V6__orders.sql` |
| V7 | order_history | `src/main/resources/db/migration/postgresql/V7__order_history.sql` |

The PostgreSQL history file also installs snapshot and append-only protection triggers.
The H2 version is for automated tests and calls the test-only Java trigger adapter.
There are no Java Flyway migrations.

## New database

Start the application against an empty PostgreSQL database. Flyway creates the seven tables
and their indexes, foreign keys and triggers in order. Subsequent starts validate the
applied migrations and do not rerun them. Provision tenant/account/role data separately.

## Existing database at the former V13

Do not drop application tables or enable automatic Flyway clean/repair. Back up the
database and stop the application. Run `adopt-existing-schema.sql` once with PostgreSQL
credentials and `psql -v ON_ERROR_STOP=1 -f database/adopt-existing-schema.sql`.

The script checks the old V13 schema, archives its metadata as
`flyway_schema_history_before_final_schema`, and records a V7 baseline. Application data,
IDs, credentials and order history are preserved. Restart with the final SQL resources.
Other databases using the old migration chain must complete this same adoption before
starting the new application. Earlier schema versions must first be upgraded to old V13.

Once deployed, do not edit already-applied V1–V7 scripts. Add V8 or higher for future changes.
The superseded migration sources remain recoverable from the initial Git commit.
