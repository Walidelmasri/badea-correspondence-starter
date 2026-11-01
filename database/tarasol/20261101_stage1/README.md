# Tarasol — Stage 1: isolated organization foundation (1 November 2026)

**Baseline:** `Archive(20261029-113156).zip`, supplied by the user as the current codebase. **Status:** Prepared changes; not applied to Oracle or any running container.

## Scope

- Creates four new, prefixed tables in `STRATEGYKPI` using **unqualified SQL names**. No DDL or DML targets `BADEA_ADDONS`, `BADEA_A`, SAP, or existing STRATEGYKPI objects.
- Inserts 19 organizational units, codes `101` through `119`. Arabic names use `UNISTR` literals to prevent incorrect client-terminal encoding. Initial French names are the English strings supplied in the existing department dataset; **not newly translated French content**. `SAP_CODE` intentionally remains null pending explicit source mapping confirmation. Unit classifications marked `OTHER` should be approved before routing uses them.
- Leaves assignment, secretary and delegation tables **empty**. No manager or acting-manager identity is inferred from numerical data, and no new authorization is granted.
- Adds `TarasolOrganizationReader`, an **unwired**, read-only JDBC adapter with a bounded SQL timeout, and JUnit/Mockito tests. No production service, Spring XML bean, API, UI, existing correspondence model, mailbox, PDF, mail, or workflow logic is changed.
- Preserves the existing Oracle identity lookup `BADEA_ADDONS.EMPLOYEES` and existing `COR_DEPARTMENTS` reads **until an independently tested later cutover**.

## Before running SQL

1. Arrange a recoverable Oracle schema backup/export. This operation creates objects, and Oracle DDL **commits implicitly**; `ROLLBACK` cannot undo table creation. Keep backup evidence.
2. Connect in SQL Developer to `STRATEGYKPI` and choose **Run Script (F5)**, not Execute Statement (Ctrl+Enter), for the numbered SQL files.
3. Execute in order:
   - `00_preflight.sql` — checks current login/schema and absence of colliding objects.
   - `01_create_tables.sql` — creates new tables and indexes only.
   - `02_seed_org_units.sql` — refuses to run unless the new unit catalog is empty; seeds 19 units, no user assignments.
   - `03_validate.sql` — read-only post-migration checks; verifies all 19 unit codes and zero grants.
4. **Stop on any error**; do not rerun `01` after a partial failure. First inspect `USER_OBJECTS` / `USER_TABLES` and reconcile. Do not drop populated objects as a quick workaround.
5. Confirm the application Oracle account can `SELECT` the new `STRATEGYKPI` tables before a future cutover. An unqualified table name resolves in the **connection's current schema**, which may differ from the SQL Developer session. No `ALTER SESSION` or account grants are included here.

## Apply the code patch (Mac)

From your latest repository:

```bash
cd ~/Desktop/BADEA/badea-correspondence-starter
git status --short
git apply --check ~/Downloads/Tarasol_Stage1_Organization_Foundation_20261101.patch
git apply ~/Downloads/Tarasol_Stage1_Organization_Foundation_20261101.patch
git diff --check
mvn -f backend/alfresco-extension/pom.xml clean package
```

Review `git diff --stat`. The patch should **add** only files under `database/tarasol/20261101_stage1/` and `TarasolOrganizationReader*.java`. It should not modify any existing source file. **Do not deploy the new JAR just to enable Stage 1**; the Java class is not wired and does not impact live access.

## Data integrity / business rules to enforce in Stage 2/3

- `EMP_ID` is `VARCHAR2(20)` to match `BADEA_ADDONS.EMPLOYEES.EMP_ID`. The new tables deliberately contain **no cross-schema foreign key**; future application writes must verify employee identity and active status.
- `UNIT_CODE` preserves immutable historical codes (101–119). No existing Alfresco folders, record ownership, references, PDF labels, signatures, or history are migrated.
- Multiple `ACTING_MANAGER` assignments **may** coexist only when a unit has no active `MANAGER` appointment. **Time-overlap and vacancy checks are not enforced by this Stage 1 DDL**; the Stage 2/3 management service must perform transactional, serialized checks (lock the unit row and check overlapping appointment intervals). Do not manually populate assignments until then.
- The `MANAGER_ASSIGNMENT_ID` FK in secretary/delegation records ensures the source assignment belongs to the same unit, **not that it has a managerial capacity or current validity**; that semantic check also belongs to the management service. No secretary automatically gets approval authority.
- Delegate historical access is prohibited by default; an explicit `CASE_HANDOFF` with technical ID will later permit specified legacy cases. No existing APIs read these tables yet.
- `STARTS_AT` is inclusive; `ENDS_AT` is exclusive. All times are stored with timezone. Revocation overrides date validity. Writes and changes will require auditable approver/creator details.
- Reassignment, role-grant authorization, case-level access, secretary submission, acting-manager direct-to-President route, user management, and safe multi-workspace UI are **not Stage 1 features**. They require dedicated integration and security tests.

## Verification scope

- New main-source reader compiled successfully with JDK 21 (`javac`) in the preparation environment.
- SQL was statically inspected (19 inserts, four prefixed tables, constraint and index names, no legacy-table mutations).
- **Oracle 19c execution was not available here; not yet verified against a real database.**
- **Full Maven/Angular suite not executed here** because Maven and dependency cache are unavailable. Run the Maven command above; Angular is unchanged.

## Rollback

Because nothing is wired to live authentication or routing, code rollback is simply reversing the patch/commit; all existing behavior continues unchanged even if the new tables remain present. In a database rollback, **leave the unused tables in place** until an authorized DBA has verified they contain no later assignments or business data; removing tables requires a separately reviewed teardown, not an automatic `DROP` script.
