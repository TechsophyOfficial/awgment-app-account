# Awgment Account Service (`awgment-app-account`)

Headless, multi-tenant Identity & Access Management (IAM) backend API (`/accounts/v2`) for the Awgment Platform.

* **Authoritative Functional & Security Spec:** [USE_CASES.md](./USE_CASES.md) (`v2.3` — 44 Use Cases)
* **Phased Implementation Roadmap:** [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)
* **Legacy App & Data Migration Guide:** [MIGRATION_GUIDE.md](../MIGRATION_GUIDE.md)

---

## 1. Tech Stack & Architectural Boundaries

| Component | Version / Standard |
| :--- | :--- |
| **Runtime** | Java 21 LTS |
| **Framework** | Spring Boot `3.3.5` (Web, Security OAuth2 Resource Server, Data JPA, Validation, Actuator) |
| **Database** | PostgreSQL `16` (Schema-per-tenant `tenant_<id>` + Flyway migrations) |
| **Identity Provider** | Keycloak `26.0.5` (Quarkus) + `keycloak-admin-client:26.0.5` |
| **Build Tool** | Gradle `8.10.2` (wrapper included) |
| **API Docs** | SpringDoc OpenAPI `2.6.0` (RFC 7807 `ProblemDetail` error responses) |
| **Test Harness** | JUnit 5, Spring MockMvc, H2, Testcontainers `1.21.4` (`postgres:16-alpine` + `keycloak:26.0.5`), Mailpit, JaCoCo (`>= 85%` gate) |

**Explicit Architectural Exclusions (v2):**
* **No MongoDB** (100% PostgreSQL 16 + Flyway)
* **No Workflow Engine** (no Camunda, Flowable, Zeebe, or Temporal)
* **No FormIO / UI Modules** (strictly headless JSON API)
* **No Spring Cloud Config** (100% standalone `application.yml` + environment variables)
* **No Server-Side `Accept-Language` Translation Bundles:** REST endpoints return locale-neutral machine-readable RFC 7807 `errorCode`s with English diagnostic fallbacks so client applications localize UI copy directly; end-user login & `execute-actions-email` i18n is delegated to Keycloak 26 via the synced `locale` user attribute (`tp_user.locale`).

---

## 2. Prerequisites

* **JDK 21 LTS** (e.g., Microsoft OpenJDK 21 / Eclipse Temurin 21). If using SDKMAN, run from the repository root:
  ```bash
  sdk env install
  ```
* **Docker Desktop / Docker Engine** (`24+`; Docker Engine `29+` supported out of the box via Testcontainers `1.21.4`) with **Docker Compose v2** (`docker compose`).
* **IDE:** IntelliJ IDEA (recommended) or VS Code with Java Extension Pack.
* **Optional CLI tools:** `curl` and `jq` for local API smoke testing.

---

## 3. Environment Variables Reference

All runtime settings are configured in [src/main/resources/application.yml](./src/main/resources/application.yml) with environment-variable overrides (see [docker.env](./docker.env) for a local Docker template).

| Variable | Default | Required in Prod | Description |
| :--- | :--- | :---: | :--- |
| `SPRING_PROFILES_ACTIVE` | *(none)* | No | Set `local` in local development to activate [application-local.yml](./src/main/resources/application-local.yml) dev password defaults. Outside `local`/`test`, `StartupSecurityValidator` fails fast on blank or dev-default secrets. |
| `SERVER_PORT` | `8081` | No | HTTP listen port for the service. |
| `AWGMENT_NODE_ID` | `-1` | Recommended | Deterministic Snowflake node ID (`0..1023`) for multi-replica pods (e.g., StatefulSet ordinal); `-1` auto-derives from NIC MAC (risks collision on identical container virtual NICs). |
| `AWGMENT_DB_URL` | `jdbc:postgresql://localhost:5432/awgment_account` | **Yes** | PostgreSQL 16 JDBC URL (use `?sslmode=require` in production). |
| `AWGMENT_DB_USERNAME` | `awgment` | **Yes** | PostgreSQL username (needs `CREATE` schema privilege on `awgment_account` for tenant provisioning). |
| `AWGMENT_DB_PASSWORD` | *(none; `local` profile only)* | **Yes** | PostgreSQL password (inject via secret manager / Kubernetes Secret; dev default exists only in `local` profile). |
| `AWGMENT_DB_POOL_MAX` | `20` | No | HikariCP maximum pool size. |
| `AWGMENT_DB_POOL_MIN_IDLE` | `5` | No | HikariCP minimum idle connections. |
| `AWGMENT_DB_CONN_TIMEOUT_MS` | `9500` | No | HikariCP connection acquisition timeout (ms). |
| `AWGMENT_DB_LEAK_THRESHOLD_MS` | `0` | No | HikariCP leak detection threshold (ms; `0` disabled by default to avoid false alarms during migrations/bulk imports). |
| `AWGMENT_FLYWAY_ENABLED` | `true` | No | Enables Spring Boot Flyway migrations on `public` schema at startup. |
| `AWGMENT_FLYWAY_MIGRATE_ON_STARTUP` | `true` | No | Enables `TenantFlywayMigrator` startup migration of all `ACTIVE` tenant schemas (`SUSPENDED` and `PROVISIONING_FAILED` tenants are skipped; per-tenant startup failures quarantine only the broken tenant). |
| `AWGMENT_MULTITENANCY_ENABLED` | `true` | No | Enables Hibernate 6 schema-per-tenant connection routing (`SET search_path TO tenant_<id>`). |
| `AWGMENT_TENANT_STATUS_CACHE_TTL_SECONDS` | `60` | No | Bounded TTL (seconds, capped at `60`) for `TenantLookupService` local status cache. |
| `AWGMENT_MULTITENANCY_PROVISIONING_STALE_TTL_SECONDS` | `300` | No | Age (seconds, min `30`) after which a crashed `PROVISIONING` row can be reclaimed by `POST /retry-provisioning`. |
| `AWGMENT_KEYCLOAK_URL` | `http://localhost:8080` | **Yes** | External public base URL of Keycloak 26 used for strict JWT `iss` validation (no trailing slash). |
| `AWGMENT_KEYCLOAK_INTERNAL_URL` | *(blank)* | Recommended | Optional internal ClusterIP/Docker base URL for split-horizon JWKS fetches (validates JWT `iss` against `AWGMENT_KEYCLOAK_URL` while fetching JWKS internally). |
| `AWGMENT_PLATFORM_REALM` | `platform` | **Yes** | Dedicated Platform Control Realm authorized for `/accounts/v2/tenants/**`. |
| `AWGMENT_EXPECTED_AUDIENCE` | `awgment-account` | **Yes** | Mandatory JWT `aud` claim verified on every bearer token. |
| `AWGMENT_TRUSTED_CLIENT_ID` | `awgment-account` | **Yes** | Single trusted Keycloak client from which Awgment RBAC roles are extracted. |
| `AWGMENT_SERVICE_ACCOUNT_CLIENTS` | `awgment-service-client` | No | Comma-separated allowlist of confidential client IDs accepted by `ServiceAccountPrincipalDetector`. |
| `AWGMENT_SECURITY_DECODER_CACHE_MAX_SIZE` | `500` | No | Bounded LRU capacity for cached per-issuer `JwtDecoder` managers. |
| `AWGMENT_SECURITY_DECODER_CACHE_TTL_SECONDS` | `300` | No | TTL (seconds) before cached per-issuer `JwtDecoder` managers refresh JWKS metadata. |
| `AWGMENT_SECURITY_OIDC_CONNECT_TIMEOUT_MS` | `5000` | No | Bounded non-redirecting HTTP connect timeout (ms, min `100`) for OIDC discovery/JWKS calls. |
| `AWGMENT_SECURITY_OIDC_READ_TIMEOUT_MS` | `8000` | No | Bounded non-redirecting HTTP read timeout (ms, min `100`) for OIDC discovery/JWKS calls. |
| `AWGMENT_KEYCLOAK_ADMIN_URL` | `AWGMENT_KEYCLOAK_INTERNAL_URL` or `AWGMENT_KEYCLOAK_URL` | **Yes** | Internal or external Keycloak Admin REST base URL used by `KeycloakAdminRealmClient`. |
| `AWGMENT_KEYCLOAK_ADMIN_REALM` | `master` | **Yes** | Realm used by `KeycloakAdminRealmClient` when inspecting/updating tenant realms. |
| `AWGMENT_KEYCLOAK_ADMIN_CLIENT_ID` | `admin-cli` | **Yes** | Client ID for Keycloak Admin REST access. |
| `AWGMENT_KEYCLOAK_ADMIN_CLIENT_SECRET` | *(blank)* | Conditional | Confidential client secret; when non-blank uses `client_credentials` grant, otherwise uses `password` grant. |
| `AWGMENT_KEYCLOAK_ADMIN_USERNAME` | `admin` | Conditional | Admin username when using `password` grant (`AWGMENT_KEYCLOAK_ADMIN_CLIENT_SECRET` blank). |
| `AWGMENT_KEYCLOAK_ADMIN_PASSWORD` | *(none; `local` profile only)* | Conditional | Admin password when using `password` grant (dev default exists only in `local` profile; inject via secret manager in production). |
| `AWGMENT_KEYCLOAK_CONNECT_TIMEOUT_MS` | `5000` | No | Bounded HTTP connect timeout (ms, min `100`) for Keycloak Admin REST calls. |
| `AWGMENT_KEYCLOAK_READ_TIMEOUT_MS` | `8000` | No | Bounded HTTP read timeout (ms, min `100`) for Keycloak Admin REST calls. |
| `AWGMENT_OUTBOX_WORKER_ENABLED` | `true` | No | Enables the scheduled coalesced outbox poller (`CoalescedOutboxWorker`). |
| `AWGMENT_OUTBOX_POLL_INTERVAL_MS` | `2000` | No | Fixed delay (ms, min `100`) between outbox polling passes across `ACTIVE` tenants. |
| `AWGMENT_OUTBOX_LEASE_TTL_SECONDS` | `60` | No | Distributed lease duration (seconds, min `5`, strictly `>>` Keycloak HTTP timeout) before an `IN_PROGRESS` job is reclaimable. |
| `AWGMENT_OUTBOX_VERIFICATION_DELAY_SECONDS` | `15` | No | Forced verification reconcile delay (seconds, min `1`) scheduled after reclaiming an expired lease. |
| `AWGMENT_OUTBOX_SYNC_REVOKE_TIMEOUT_MS` | `5000` | No | Bounded synchronous Keycloak revocation deadline (ms, `500..10000`) on `DELETE /accounts/v2/users/{id}`. |
| `AWGMENT_OUTBOX_MAX_RETRIES` | `5` | No | Maximum retry attempts (min `1`) before an outbox row transitions to `FAILED`. |
| `AWGMENT_OUTBOX_BATCH_SIZE` | `20` | No | Maximum outbox rows claimed per tenant poll (`1..200`). |
| `AWGMENT_OUTBOX_BASE_BACKOFF_SECONDS` | `5` | No | Initial exponential retry backoff delay (seconds, min `1`). |
| `AWGMENT_OUTBOX_MAX_BACKOFF_SECONDS` | `300` | No | Maximum exponential retry backoff delay (seconds, min `5`). |
| `AWGMENT_OUTBOX_WORKER_ID` | *(hostname/UUID)* | No | Optional worker identifier stamped onto `tp_keycloak_outbox.locked_by` (truncated to 100 chars). |
| `AWGMENT_OPENAPI_ENABLED` | `false` | No | Exposes `/v3/api-docs` & `/swagger-ui.html`. **Must remain `false` in production.** |

### Implemented Phase 1 Endpoints (`/accounts/v2/tenants/**`)

All tenant management endpoints strictly require a valid bearer JWT issued by `AWGMENT_PLATFORM_REALM` (`iss` prefix-validated before any JWKS fetch), containing `aud = AWGMENT_EXPECTED_AUDIENCE`, and holding the `awgment-platform-admin` role on `AWGMENT_TRUSTED_CLIENT_ID`. Tenant-realm tokens are rejected with `403 TENANT_NOT_ACTIVE`.

| Use Case | Method & Path | Description |
| :--- | :--- | :--- |
| `UC-TENANT-01` | `POST /accounts/v2/tenants` | Validates `tenantId` (`^[a-z][a-z0-9_]{1,49}$` + reserved denylist), creates `tenant_<tenantId>`, runs Flyway migrations, seeds `system` + optional bootstrap approver, verifies Keycloak 26 Section 3 realm security contract, and activates tenant (`201 Created`). Commits `PROVISIONING_FAILED` on downstream error. |
| `UC-TENANT-02` | `POST /accounts/v2/tenants/{tenantId}/retry-provisioning` | Idempotently resumes a `PROVISIONING_FAILED` tenant (`200 OK`; `409 INVALID_STATE_TRANSITION` for other states). |
| `UC-TENANT-03` | `GET /accounts/v2/tenants` | Paginated tenant listing with optional `status` filter, bounded `page >= 0` & `size` (`1..100`), and allowlisted `sort` fields (`tenantId`, `displayName`, `status`, `createdAt`, `updatedAt`). |
| `UC-TENANT-04` | `PATCH /accounts/v2/tenants/{tenantId}/status` | Optimistic-locked (`version`) platform-wide suspension (`ACTIVE -> SUSPENDED`, disabling the Keycloak realm & evicting caches) or reactivation (`SUSPENDED -> ACTIVE`, executing pending Flyway migrations & Section 3 realm verification before admitting traffic). |

### Implemented Phase 2 Endpoints (`/accounts/v2/users/**` & `/accounts/v2/sync/outbox/**`)

Tenant user and outbox endpoints require an `ACTIVE` tenant JWT (`iss` prefix-validated, `aud = AWGMENT_EXPECTED_AUDIENCE`), run inside schema-isolated `tenant_<id>` connections, and enforce 4-tier `RoleGovernancePolicy` with recursive composite-role closure.

| Use Case | Method & Path | Description |
| :--- | :--- | :--- |
| `UC-USER-01` | `POST /accounts/v2/users` | Operator (`awgment-account-create-or-update` / `awgment-account-all`) user provisioning (`201 Created`): validates `service-account*` ban, active/disabled email & username uniqueness, `tp_trusted_idp` admission for `EXTERNAL_IDP`, 4-tier `RoleGovernancePolicy`, and enqueues coalesced `RECONCILE_USER` (+ `SEND_ACTION_EMAIL` for `KEYCLOAK_LOCAL`). |
| `UC-USER-01` | `PUT /accounts/v2/users/{id}` | Operator update (`200 OK`) enforcing `@Version` (`409 CONCURRENT_MODIFICATION`), higher-tier target protection across desired + live Keycloak roles, and strict immutability of `userName`/`emailId`/`identitySource`/`idpAlias`/`status`/`roles`/`groups` (`400 IMMUTABLE_FIELD_MODIFICATION`). |
| `UC-USER-02` | `GET /accounts/v2/users/{id}` | Fetches user profile (`awgment-account-read` / `all` / `admin`), supporting `onlyMandatoryFields=true`. |
| `UC-USER-03` | `GET /accounts/v2/users` | Indexed JPA Specification search/filter (`q`, `status`, `identitySource`, `idpAlias`, `department`, `loginId`, `onlyMandatoryFields`, `page >= 0`, `size` `1..200`, allowlisted `sort`); hides `system` user unless verified service account or `loginId=system`. |
| `UC-USER-04` | `DELETE /accounts/v2/users/{id}` | Deactivates user (`awgment-account-delete` / `all`): guards self-disable (`403 SELF_ACTION_FORBIDDEN`) & higher-tier targets (`403 ROLE_ESCALATION_FORBIDDEN`), commits PostgreSQL `DISABLED` + coalesced `RECONCILE_USER`, and runs synchronous-first Keycloak `disable+logout` (`204 No Content` on success, `202 Accepted` `{ status: "DISABLED", syncStatus: "PENDING_SYNC" }` + `awgment_revocation_sync_failed_total` on failure). |
| `UC-USER-05` | `POST /accounts/v2/users/{id}/reactivate` | Approver-only (`awgment-account-admin`) transition `DISABLED -> ACTIVE` (`200 OK`; `409 INVALID_STATE_TRANSITION` otherwise) with coalesced `RECONCILE_USER` sync. |
| `UC-USER-06` | `GET /accounts/v2/users/me` | Self-service profile read (`isAuthenticated()`) resolved strictly by `jwt.sub`; blocks `DISABLED`/`PENDING_APPROVAL`/`REJECTED` (`403 ACCESS_*`). |
| `UC-USER-07` | `PUT /accounts/v2/users/me` | Self-service non-ACL profile update (`isAuthenticated()`): rejects admin/identity fields (`department`, `attributes`, `status`, `roles`, `groups`) and `EXTERNAL_IDP` name edits (`400 IMMUTABLE_FIELD_MODIFICATION`); syncs `locale`/local name changes to Keycloak. |
| `UC-SYNC-01` | `GET /accounts/v2/sync/outbox` | Paginated inspection of `tp_keycloak_outbox` (`status`, `aggregateType`, `page >= 0`, `size` `1..100`) for `awgment-account-read` / `all` / `admin`. |
| `UC-SYNC-02` | `POST /accounts/v2/sync/outbox/{id}/retry` | Resets a `FAILED` `RECONCILE_*` outbox job to `PENDING` (`200 OK`) and triggers immediate dispatch; blocks ambiguous `SEND_ACTION_EMAIL` retries (`409 INVALID_STATE_TRANSITION`). |

---

## 4. Developer Setup & Running in IDE

### Step 1: Start Local Infrastructure (PostgreSQL 16 + Keycloak 26 + Mailpit)

From the **workspace root** (`Awgment-OSS/`):

```bash
docker compose up -d
```

Wait for all three containers to report healthy (`docker compose ps`):

| Service | Container | Host Port(s) | Purpose |
| :--- | :--- | :--- | :--- |
| PostgreSQL 16 | `awgment-postgres` | `5432` | Hosts `keycloak` and `awgment_account` databases (initialized by `docker/init-db.sh`). |
| Keycloak 26.0.5 | `awgment-keycloak` | `8080` | Auto-imports 3 pre-wired realms from `src/test/resources/keycloak/`: `platform`, `acme`, and `mock-entra`. Admin console: `http://localhost:8080` (`admin` / `admin_local_dev_password`). |
| Mailpit | `awgment-mailpit` | `1025` (SMTP), `8025` (UI) | Captures Keycloak `execute-actions-email` invites & password resets locally at `http://localhost:8025`. |

Fetch a local dev JWT for any persona using the helper script from the workspace root:

```bash
export TOKEN=$(./docker/scripts/get-token.sh operator)
# Supported personas: platform-admin | approver | operator | acl-admin | user | service
```

---

### Step 2A: IntelliJ IDEA Setup

1. **Open Project:**
   * Launch IntelliJ IDEA → **File → Open** → select `awgment-app-account/build.gradle` → choose **Open as Project**.
2. **Configure JDK 21:**
   * **File → Project Structure → Project:** set **SDK** to **Java 21** and **Language level** to **21**.
   * **Settings → Build, Execution, Deployment → Build Tools → Gradle:** set **Gradle JVM** to **Project SDK (Java 21)** and **Build and run using** to **Gradle**.
3. **Enable Lombok Annotation Processing:**
   * Install the **Lombok** plugin (bundled in modern IntelliJ IDEA).
   * Go to **Settings → Build, Execution, Deployment → Compiler → Annotation Processors** → check **Enable annotation processing**.
4. **Create Run Configuration:**
   * **Run → Edit Configurations → `+` → Application** (or Spring Boot):
     * **Name:** `AwgmentAccountLocal`
     * **Main class:** `com.techsophy.tsf.account.TechsophyPlatformAccountApplication`
     * **Module:** `awgment-app-account.main`
     * **Environment variables:**
       ```text
       SPRING_PROFILES_ACTIVE=local;SERVER_PORT=8081;AWGMENT_DB_URL=jdbc:postgresql://localhost:5432/awgment_account;AWGMENT_DB_USERNAME=awgment;AWGMENT_DB_PASSWORD=awgment_local_dev_password;AWGMENT_KEYCLOAK_URL=http://localhost:8080;AWGMENT_OPENAPI_ENABLED=true
       ```
5. **Run / Debug:**
   * Click **Run** or **Debug** on `AwgmentAccountLocal`.
   * Verify health probes & local Swagger UI:
     * Liveness: `http://localhost:8081/actuator/health/liveness`
     * Readiness: `http://localhost:8081/actuator/health/readiness`
     * Swagger UI (dev only): `http://localhost:8081/swagger-ui.html`

---

### Step 2B: VS Code Setup

1. Install extensions: **Extension Pack for Java** (`vscjava.vscode-java-pack`), **Gradle for Java** (`vscjava.vscode-gradle`), and **Lombok Annotations Support** (`GabrielBB.vscode-lombok`).
2. Open the `awgment-app-account` folder in VS Code and ensure JDK 21 is selected (`Java: Configure Java Runtime`).
3. Add `.vscode/launch.json` (do not commit personal secrets):
   ```json
   {
     "version": "0.2.0",
     "configurations": [
       {
         "type": "java",
         "name": "AwgmentAccountLocal",
         "request": "launch",
         "mainClass": "com.techsophy.tsf.account.TechsophyPlatformAccountApplication",
         "projectName": "awgment-app-account",
         "env": {
           "SPRING_PROFILES_ACTIVE": "local",
           "SERVER_PORT": "8081",
           "AWGMENT_DB_URL": "jdbc:postgresql://localhost:5432/awgment_account",
           "AWGMENT_DB_USERNAME": "awgment",
           "AWGMENT_DB_PASSWORD": "awgment_local_dev_password",
           "AWGMENT_KEYCLOAK_URL": "http://localhost:8080",
           "AWGMENT_OPENAPI_ENABLED": "true"
         }
       }
     ]
   }
   ```
4. Press `F5` to start debugging.

---

### Step 2C: Running from Terminal (`./gradlew bootRun`)

```bash
cd awgment-app-account
SPRING_PROFILES_ACTIVE=local AWGMENT_OPENAPI_ENABLED=true ./gradlew bootRun
```

Verify the service is responding:

```bash
curl -s http://localhost:8081/actuator/health/readiness
# {"status":"UP"}
```

---

## 5. Testing & Quality Gates

The project enforces **Test-Driven Development (TDD)** and a **3-layer verification harness**:

| Layer | Command | Requires Docker? | What It Runs |
| :--- | :--- | :---: | :--- |
| **Layer 1 (Unit, MockMvc & Coverage Gate)** | `./gradlew check` | No | Fast offline JUnit 5 tests, `TestJwtFactory` RSA JWT tests, Keycloak blueprint validation, `compileIntegrationTestJava`, and **JaCoCo `>= 85%` coverage verification**. |
| **Layer 2 (Live Container Integration)** | `./gradlew integrationTest` | **Yes** | Testcontainers spinning up real `postgres:16-alpine` + `quay.io/keycloak/keycloak:26.0.5` importing `platform`, `acme`, and `mock-entra` realms. |
| **Layer 3 (Full Local Stack)** | `docker compose up -d` | **Yes** | Persistent local PostgreSQL 16 + Keycloak 26 + Mailpit for manual `curl`/Swagger testing. |

Run everything together before opening a PR:

```bash
./gradlew check integrationTest
```

HTML reports are generated at:
* Unit tests: `build/reports/tests/test/index.html`
* Integration tests: `build/reports/tests/integrationTest/index.html`
* JaCoCo coverage: `build/reports/jacoco/test/html/index.html`

### Postman Collection (v2.1)

Import both files into Postman (or run via Newman):
* **Collection:** [docs/postman/awgment-app-account-v2.postman_collection.json](./docs/postman/awgment-app-account-v2.postman_collection.json)
* **Local Environment:** [docs/postman/awgment-local.postman_environment.json](./docs/postman/awgment-local.postman_environment.json)

Fill in the local dev secret fields in your Postman environment (left blank in git), run **00 - Auth → Get Platform Admin Token**, and execute the domain folders in sequence. Every request includes `pm.test(...)` assertions and RFC 7807 response examples, verified automatically by `PostmanCollectionContractTest`.

---

## 6. Standalone Server Deployment Guide

This guide covers deploying `awgment-app-account` onto a standalone Linux server (Ubuntu/RHEL VM or bare-metal host) behind a TLS-terminating reverse proxy.

### 6.1 Pre-Deployment Prerequisites

1. **PostgreSQL 16 Database:**
   * Create the dedicated `awgment_account` database and a least-privilege application role that owns `awgment_account` (so Flyway can create `tenant_<id>` schemas on demand) without `SUPERUSER` privileges:
     ```sql
     CREATE ROLE awgment_app WITH LOGIN PASSWORD '<STRONG_GENERATED_PASSWORD>';
     CREATE DATABASE awgment_account OWNER awgment_app;
     REVOKE ALL ON DATABASE awgment_account FROM PUBLIC;
     ```
   * Enforce TLS (`sslmode=require`) and restrict network access (`pg_hba.conf` / firewall) to the application host only.
2. **Keycloak 26.0.5 (Hardened per `USE_CASES.md` Section 3):**
   * Provision a dedicated **Platform Control Realm** (`platform`) containing `awgment-platform-admin` on client `awgment-account` (with `aud=awgment-account` mapper).
   * Ensure tenant realms enforce:
     * Zero default Awgment roles on new users.
     * Short access-token lifespan (`accessTokenLifespan <= 300`).
     * Declarative User Profile locking `awgment_status`, `userId`, and `awgment_sync_version` to `edit = ["admin"]` with unmanaged attributes disabled (omit `unmanagedAttributePolicy`).
     * First Broker Login automatic email linking disabled.
     * Note: On Keycloak `26.0.5`, set `KC_FEATURES_DISABLED=organization` if importing realms containing service-account clients at startup.
3. **Production Security Rules:**
   * Keep `AWGMENT_OPENAPI_ENABLED=false` (Swagger & `/v3/api-docs` stay disabled).
   * Expose `/actuator/health/liveness` and `/actuator/health/readiness` **only** to internal health probes/load balancers — block `/actuator/**` on the public reverse proxy.
   * Never store secrets in git or world-readable files.

---

### 6.2 Option A — Standalone Docker Deployment (Recommended)

#### 1. Build the Application JAR & Image

On your build machine or CI pipeline:

```bash
cd awgment-app-account
./gradlew clean check bootJar
docker build -t awgment-app-account:2.0.0 .
```

*(Alternatively, push to your container registry and `docker pull` on the target server.)*

#### 2. Create Protected Production Environment File

On the standalone server:

```bash
sudo mkdir -p /etc/awgment
sudo tee /etc/awgment/account.env > /dev/null <<'EOF'
SERVER_PORT=8081
AWGMENT_NODE_ID=0
AWGMENT_DB_URL=jdbc:postgresql://db.internal.example.com:5432/awgment_account?sslmode=require
AWGMENT_DB_USERNAME=awgment_app
AWGMENT_DB_PASSWORD=<STRONG_DB_PASSWORD>
AWGMENT_DB_POOL_MAX=20
AWGMENT_DB_POOL_MIN_IDLE=5
AWGMENT_DB_CONN_TIMEOUT_MS=9500
AWGMENT_DB_LEAK_THRESHOLD_MS=0
AWGMENT_FLYWAY_ENABLED=true
AWGMENT_FLYWAY_MIGRATE_ON_STARTUP=true
AWGMENT_MULTITENANCY_ENABLED=true
AWGMENT_TENANT_STATUS_CACHE_TTL_SECONDS=60
AWGMENT_KEYCLOAK_URL=https://auth.example.com
AWGMENT_KEYCLOAK_INTERNAL_URL=https://auth.internal.example.com
AWGMENT_PLATFORM_REALM=platform
AWGMENT_EXPECTED_AUDIENCE=awgment-account
AWGMENT_TRUSTED_CLIENT_ID=awgment-account
AWGMENT_SERVICE_ACCOUNT_CLIENTS=awgment-service-client
AWGMENT_KEYCLOAK_ADMIN_URL=https://auth.internal.example.com
AWGMENT_KEYCLOAK_ADMIN_REALM=master
AWGMENT_KEYCLOAK_ADMIN_CLIENT_ID=awgment-admin-sync
AWGMENT_KEYCLOAK_ADMIN_CLIENT_SECRET=<STRONG_KEYCLOAK_ADMIN_CLIENT_SECRET>
AWGMENT_OPENAPI_ENABLED=false
EOF
sudo chmod 600 /etc/awgment/account.env
```

#### 3. Run the Hardened Container

Publish port `8081` (restrict external ingress to port `8081` via host firewall/security group or bind to the loopback IP so all external traffic passes through the TLS reverse proxy):

```bash
docker run -d \
  --name awgment-app-account \
  --restart unless-stopped \
  --env-file /etc/awgment/account.env \
  -p 8081:8081 \
  --read-only \
  --tmpfs /tmp:noexec,nosuid,size=128m \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --memory 1g \
  awgment-app-account:2.0.0
```

Verify startup & logs:

```bash
docker logs -f --tail 50 awgment-app-account
curl -fsS http://localhost:8081/actuator/health/readiness
```

---

### 6.3 Option B — Native Bare-Metal `systemd` Deployment (No Docker)

#### 1. Install Java 21 & Provision Service User

```bash
# Example on Ubuntu 22.04/24.04 (Temurin 21 or distro openjdk-21-jre-headless)
sudo apt-get update && sudo apt-get install -y openjdk-21-jre-headless

# Dedicated non-login system user
sudo useradd --system --home /opt/awgment/account --shell /usr/sbin/nologin awgment
sudo mkdir -p /opt/awgment/account /etc/awgment
```

#### 2. Deploy the Bootable JAR & Environment File

Build locally (`./gradlew clean check bootJar`) and copy `build/libs/awgment-app-account-*.jar` to the server:

```bash
sudo cp awgment-app-account-2.0.0-SNAPSHOT.jar /opt/awgment/account/awgment-app-account.jar
sudo chown -R awgment:awgment /opt/awgment/account
sudo chmod 550 /opt/awgment/account/awgment-app-account.jar

# Create /etc/awgment/account.env using the template from Section 6.2
sudo chown root:awgment /etc/awgment/account.env
sudo chmod 640 /etc/awgment/account.env
```

#### 3. Create the Hardened `systemd` Unit

```bash
sudo tee /etc/systemd/system/awgment-account.service > /dev/null <<'EOF'
[Unit]
Description=Awgment Headless IAM Account Service (v2)
After=network-online.target postgresql.service
Wants=network-online.target

[Service]
Type=simple
User=awgment
Group=awgment
WorkingDirectory=/opt/awgment/account
EnvironmentFile=/etc/awgment/account.env
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -jar /opt/awgment/account/awgment-app-account.jar
Restart=on-failure
RestartSec=5
TimeoutStopSec=30

# OS-level hardening
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
ProtectKernelTunables=true
ProtectControlGroups=true
RestrictSUIDSGID=true
LockPersonality=true

[Install]
WantedBy=multi-user.target
EOF
```

#### 4. Enable, Start & Verify

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now awgment-account
sudo systemctl status awgment-account
sudo journalctl -u awgment-account -f
```

Confirm local probes:

```bash
curl -fsS http://localhost:8081/actuator/health/liveness
curl -fsS http://localhost:8081/actuator/health/readiness
```

---

### 6.4 Reverse Proxy & TLS Termination (Nginx Example)

Terminate TLS at Nginx (or Envoy/Caddy/Kong) and forward only `/accounts/v2/` to `localhost:8081`, keeping `/actuator/**` internal-only:

```nginx
server {
    listen 443 ssl http2;
    server_name api.example.com;

    ssl_certificate     /etc/letsencrypt/live/api.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.example.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    client_max_body_size 6m; # Supports 5 MB bulk CSV limit + multipart overhead

    # Block Actuator from public internet
    location /actuator/ {
        return 404;
    }

    location /accounts/v2/ {
        proxy_pass http://localhost:8081;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 30s;
    }
}
```

---

### 6.5 Upgrade & Rollback Runbook

1. **Backup PostgreSQL** (`pg_dump -Fc awgment_account > awgment_account_$(date +%F).dump`) before every release containing Flyway schema migrations.
2. **Deploy new artifact:**
   * **Docker:** `docker stop awgment-app-account && docker rm awgment-app-account`, then run the new versioned image tag.
   * **`systemd`:** Replace `/opt/awgment/account/awgment-app-account.jar` atomically (`mv` from staged file) and run `sudo systemctl restart awgment-account`.
3. **Verify readiness:** Confirm `GET http://localhost:8081/actuator/health/readiness` returns `{"status":"UP"}` and inspect logs for zero Flyway or startup errors.
4. **Rollback:** Restore the previous JAR/image tag (and restore the `pg_dump` snapshot if a schema migration must be reverted).

---

## 7. Troubleshooting

* **Testcontainers fails with `BadRequestException (Status 400)` on Docker Desktop 4.55+ / Docker Engine 29+:**
  * Docker Engine 29 requires minimum API `1.44`. This repo pins `testcontainers.version = 1.21.4` in [build.gradle](./build.gradle) to support Docker 29 natively. Run `./gradlew --refresh-dependencies integrationTest` if your Gradle cache holds older artifacts.
* **Keycloak `26.0.5` fails during `--import-realm` with `Session not bound to a realm`:**
  * Known upstream `26.0.5` bug in `DirImportProvider` with the `organization` feature enabled. Ensure `KC_FEATURES_DISABLED=organization` is set (already configured in `../docker-compose.yml` and `PostgresKeycloakContainersIT`).
* **Lombok getters/loggers show red in IntelliJ IDEA:**
  * Enable **Annotation Processing** in IntelliJ settings and re-import the Gradle project.
* **Swagger UI returns `401` / `404`:**
  * Swagger UI is intentionally disabled by default (`AWGMENT_OPENAPI_ENABLED=false`). Set `AWGMENT_OPENAPI_ENABLED=true` in local development only.
* **Edited Keycloak realm blueprint JSONs are not picked up by `docker compose up -d`:**
  * Keycloak `--import-realm` skips realms that already exist in the persistent PostgreSQL `postgres_data` volume. Run `docker compose down -v && docker compose up -d` to re-import updated blueprints cleanly.
