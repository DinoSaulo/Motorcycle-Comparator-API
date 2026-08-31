# Motorcycle Comparison API

RESTful backend for a motorcycle specification catalogue with side-by-side comparison,
in the spirit of "Tudo Celular" but for motorcycles.

Spring Boot 3.4 · Java 23 · PostgreSQL · JWT · OpenAPI

---

## 📊 Project Status & Quality Metrics

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

### SonarQube Cloud Analysis

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=DinoSaulo_Motorcycle-Comparator-API&metric=alert_status)](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=DinoSaulo_Motorcycle-Comparator-API&metric=coverage)](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=DinoSaulo_Motorcycle-Comparator-API&metric=bugs)](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=DinoSaulo_Motorcycle-Comparator-API&metric=code_smells)](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=DinoSaulo_Motorcycle-Comparator-API&metric=vulnerabilities)](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=DinoSaulo_Motorcycle-Comparator-API&metric=security_rating)](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=DinoSaulo_Motorcycle-Comparator-API&metric=reliability_rating)](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=DinoSaulo_Motorcycle-Comparator-API&metric=sqale_rating)](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=DinoSaulo_Motorcycle-Comparator-API&metric=sqale_index)](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=DinoSaulo_Motorcycle-Comparator-API&metric=duplicated_lines_density)](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=DinoSaulo_Motorcycle-Comparator-API&metric=ncloc)](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API)

---

## Status

**Iteration 1 — Production Ready** — the API skeleton is complete, builds green, and is covered by 120+ tests
(113 unit/slice + 7 integration; 98%+ instruction / 90%+ branch). Flyway migrations have
landed: every environment, local development included, now runs the same migrated
schema. Core service and controller layers are 100% instruction-covered.

**Latest updates:**
- ✅ SonarQube Cloud integration & continuous quality monitoring
- ✅ GitHub Actions hardened with pinned commit SHAs (GitHub actions security)
- ✅ Comprehensive security test suite with CSRF and authentication coverage
- ✅ Motorcycle data expanded: Kawasaki, Harley Davidson, Royal Enfield, BMW, Honda 2026 specifications
- ✅ Brand-casing normalization: all manufacturer names now in consistent Title Case
- ✅ DomainValidationException for safe, consistent error messaging
- ⏳ React frontend and Selenium/Cucumber E2E layer (coming next)

## Quick start

```bash
# 1. A PostgreSQL instance
docker run --name motorcycle-db -e POSTGRES_DB=motorcycle_comparison \
  -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16

# 2. Run (dev profile is the default: Flyway migrates the schema and applies the
#    repeatable seed in db/seed/R__dev_seed.sql; the schema is no longer recreated
#    on every boot, so data you add by hand survives a restart)
mvn spring-boot:run

# 3. Explore
open http://localhost:8080/swagger-ui.html
```

| What | Where |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |

Seed accounts (dev only, in memory): `admin` / `admin123` — `editor` / `editor123`.

The database must be **empty** the first time: Flyway 10 refuses to migrate a non-empty
schema that has no `flyway_schema_history`. If you still have a database built by the old
`create-drop` dev profile, drop and recreate it rather than setting `baseline-on-migrate`,
which would falsely record `V1` as already applied.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/motorcycles` | public | Paged, filtered catalogue |
| `GET` | `/api/v1/motorcycles/{id}` | public | One motorcycle, all specs |
| `GET` | `/api/v1/motorcycles/slug/{slug}` | public | Same, by public slug |
| `GET` | `/api/v1/motorcycles/brands` | public | Distinct brands, for the filter sidebar |
| `GET` | `/api/v1/motorcycles/compare?ids=1,2,3` | public | **Side-by-side comparison** |
| `GET` | `/api/v1/images/motorcycles/{fileName}` | public | Serve an uploaded image |
| `POST` | `/api/v1/motorcycles` | `ROLE_ADMIN` | Create |
| `PUT` | `/api/v1/motorcycles/{id}` | `ROLE_ADMIN` | Replace (never the image — see below) |
| `DELETE` | `/api/v1/motorcycles/{id}` | `ROLE_ADMIN` | Delete |
| `POST` | `/api/v1/motorcycles/{id}/image` | `ROLE_ADMIN` | Upload or replace the image (`multipart/form-data`, part `file`) |
| `DELETE` | `/api/v1/motorcycles/{id}/image` | `ROLE_ADMIN` | Clear the image |
| `POST` | `/api/v1/auth/login` | public | Exchange credentials for a JWT |

**The image is owned by the two `/image` endpoints, not by `PUT`.** A full-replacement `PUT`
that omitted `imageUrl` would otherwise blank the column and strand the file on disk with
nothing left holding its name. `imageUrl` on the request body is honoured on `POST` only, for
a curated externally hosted URL; those are never deleted, because they were never ours.
Uploads accept JPEG, PNG and WebP, and the declared content type must match the file's
own leading bytes.

Catalogue filters, all optional and combinable:
`brand`, `category`, `modelYear`, `minDisplacementCc`, `maxDisplacementCc`,
`minPowerHp`, `minPriceEur`, `maxPriceEur`, `q` (free text), plus `page`, `size`, `sort`.

```bash
curl "http://localhost:8080/api/v1/motorcycles?category=NAKED&minDisplacementCc=600&sort=priceEur,asc"

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)

curl -X DELETE http://localhost:8080/api/v1/motorcycles/1 -H "Authorization: Bearer $TOKEN"
```

## Design decisions

**The comparison endpoint returns rows, not objects.** A naive design returns a list of
motorcycles and lets React build the table. That forces the frontend to know every spec
name, its unit, its display order and which direction counts as "better" — knowledge that
would then live in two codebases and drift apart. Instead the response is already
`groups → rows → values[]`, where `values[i]` always belongs to `motorcycles[i]`:

```json
{
  "motorcycles": [ { "id": 1, "displayName": "Yamaha MT-09 (2024)" }, { "id": 2, "…": "…" } ],
  "groups": [
    { "name": "Performance",
      "rows": [
        { "label": "Max power", "unit": "hp", "values": ["117", "94"],
          "winnerIndexes": [0], "differing": true },
        { "label": "Fuel consumption", "unit": "l/100km", "values": ["5", null],
          "winnerIndexes": [], "differing": false }
      ] }
  ]
}
```

The table component becomes a dumb renderer. Adding a spec to the comparison is a
one-line change in `ComparisonService.GROUPS` and needs no frontend deploy.

- `winnerIndexes` holds *every* index tied for best, so the UI can highlight a tie
  instead of arbitrarily crowning the first column. Empty for free-text specs.
- `values[i]` is `null`, never `0`, when a figure is not published — a missing
  spec must render as a dash.

**The specification model is hybrid.** Typed columns (`EngineSpecification`,
`Dimension`, the chassis fields) for everything the engine filters, sorts or ranks on;
a `motorcycle_additional_specs` key/value side table for the long tail that differs per
manufacturer (rider modes, TFT size, connectivity). Adding a long-tail spec never
requires a migration; anything that becomes a real comparison criterion gets promoted
to a typed column.

**A missing figure sorts last, never first.** PostgreSQL orders `NULL` last for `ASC`
but first for `DESC`, so `?sort=priceEur,desc` would open page 1 with every bike whose
price nobody published. `hibernate.order_by.default_null_ordering: last` fixes it for
every sort at once. It is *not* done with `Sort.Order#nullsLast()`: the catalogue query
goes through JPA Criteria, and Spring Data throws `UnsupportedOperationException` for
null precedence there.

**Concurrent edits are detected, not merged.** `Motorcycle` carries a JPA `@Version`, so
the second of two simultaneous admin `PUT`s gets a 409 instead of silently overwriting
the first. Creates take the other route: the slug is probed with `existsBySlug` as a fast
path, and if a concurrent insert wins the unique index anyway, the write is retried once
with the next suffix — in a fresh transaction, because a constraint violation aborts the
one it happened in.

**Performance.** `open-in-view` is off, so lazy loading cannot silently become N+1
inside the serializer. Comparison uses one entity-graph query for the whole set;
catalogue pages rely on `default_batch_fetch_size` to pull the to-one blocks in
batches. Catalogue filters go through JPA Criteria, and the join to the engine table
is added only when an engine facet is actually in play.

**Security is stateless.** Reads are public — a comparison site behind a login is
useless — and every write requires `ROLE_ADMIN` carried in a JWT. Errors from the
security layer are rendered in the same `ApiError` shape as everything else, and a 401
carries the `WWW-Authenticate: Bearer` challenge RFC 7235 requires. The OpenAPI document
marks only the write operations as needing the bearer scheme: a document-wide requirement
would make a generated client send a token to the public reads and to `/auth/login` too.

**Every error a client can trigger returns `ApiError`, even before the controller runs.**
A malformed body, an unsupported `Content-Type` or an unrecognised method used to fall
through Spring's own resolvers to the catch-all handler and come back as a bare 500 —
reachable anonymously, on `/api/v1/auth/login` alone. They are now explicit 400/415/405
responses; the parser's own message quotes payload fragments and internal class names,
so it stays at `debug` in the log instead of reaching the client.

**A CHECK or foreign-key violation is 400, a UNIQUE violation is 409.** Every constraint
in the Flyway migrations is named for exactly this: `GlobalExceptionHandler` reads the
constraint name off the exception cause to pick the status, and never forwards the name
itself — a `ck_*`/`fk_*` failure means the payload was wrong, a `uk_*` failure is a
genuine conflict.

**The committed credentials live in the `dev` profile, not at the root.** `JWT_SECRET`,
`ADMIN_PASSWORD` and `EDITOR_PASSWORD` have no fallback outside dev, so an environment
that forgets to set them fails to start instead of booting on a signing key that is
public in this repository. The failure is a placeholder-resolution error naming the
variable, at startup, before the first request.

## Data integrity

**Brand field is normalized to Title Case.** Historical imports mixed inconsistent
casing for the same manufacturer (e.g., `HONDA` and `Honda` as separate rows), breaking
catalogue filters and grouping. Migration `V4` normalizes all uppercase names to Title
Case (`initcap(lower(brand))`), while preserving recognized acronyms (`BMW`, `KTM`,
`TVS`, `BRP`, `SWM`, `FYM`, `MRX`, `MVK`, `SBM`, `NIU`, `UM`) and correcting names with
embedded acronyms (`MV Agusta`, `CFMoto`). All 7009 affected rows were consolidated on
first run; the migration is idempotent.

## Testing

```bash
mvn test      # unit + slice tests
mvn verify    # + failsafe (*IT) + JaCoCo report at target/site/jacoco/index.html
```

Core layers reach 100% instruction coverage:
- `MotorcycleService` — 36 tests covering slug derivation, concurrent races, image handling, 
  null branches, and specification merging
- `MotorcycleController` — 25+ tests covering all 10 endpoints, binding, validation, status 
  codes, and error cases

| Layer | Tool | What it proves | Coverage |
|---|---|---|---|
| `service/` | JUnit 5 + Mockito | Business rules in isolation: slug derivation, winner selection, null handling, specification merging | 99%+ instruction |
| `repository/` | `@DataJpaTest` + H2 | That entity graphs, criteria joins and JPQL actually compile and return the right rows | 98%+ instruction |
| migrations | `SchemaMigrationIT` + Testcontainers | That the migrations and the entities describe the same database — boots on real PostgreSQL 16 with `ddl-auto: validate` | 100% |
| `controller/` | `@WebMvcTest` + MockMvc | HTTP contract: routing, binding, validation, status mapping | 100% instruction |
| `controller/` | `@SpringBootTest` | Full pass through the real filter chain: 401 vs 403, admin round trip | — |

Tests use H2 in PostgreSQL compatibility mode via `src/test/resources/application.yml`, which 
shadows the main config on the test classpath. Flyway is **off** there: H2 cannot parse the 
functional expressions (`lower(brand)`) and GIN trigram indexes, so the slice tests build the 
schema from entities. `@DataJpaTest` covers entity graphs, Criteria queries, and JPQL logic 
against a real relational engine. `SchemaMigrationIT` closes the database gap — it runs in the 
failsafe (`*IT`) phase, starts `postgres:16-alpine` via Testcontainers, runs all real migrations, 
and boots the application with `ddl-auto: validate`, so entity/migration divergence fails the 
build. Requires a running Docker daemon; `mvn test` alone does not run integration tests.

## Configuration

Everything is overridable by environment variable:

| Variable | Default | Notes |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local Postgres | |
| `JWT_SECRET` | **dev profile only** | Required outside dev; no fallback, the app refuses to start. Base64, ≥ 32 bytes decoded |
| `JWT_TTL` | `PT2H` | ISO-8601 duration |
| `ADMIN_PASSWORD` / `EDITOR_PASSWORD` | **dev profile only** (`admin123` / `editor123`) | Required outside dev, same rule as the secret |
| `CORS_ALLOWED_ORIGINS` | `localhost:3000,localhost:5173` | |
| `DB_POOL_SIZE` / `DB_POOL_MIN_IDLE` | `10` / `2` | Hikari sizing |
| `SPRING_FLYWAY_LOCATIONS` | see below | Migration locations |
| `IMAGE_STORAGE_LOCATION` | `./uploads/motorcycles` | Where uploaded images are written |
| `IMAGE_MAX_FILE_SIZE` | `5MB` | The business limit; an image above it is a 400 |
| `IMAGE_MAX_UPLOAD_SIZE` / `IMAGE_MAX_REQUEST_SIZE` | `6MB` / `7MB` | Transport backstop, deliberately above the business limit so Tomcat's 413 does not pre-empt it |

The catalogue caps `?size=` at 100 rows (`spring.data.web.pageable.max-page-size`): the
endpoint is public and every row carries both specification blocks, so Spring's own
ceiling of 2000 is a free amplification lever.

Profiles: `dev` (default) and `prod` differ only in seeding and logging — both run
`ddl-auto: validate` against a Flyway-migrated schema. `dev` adds `classpath:db/seed`
to the migration locations and turns SQL logging on; `prod` never sees the seed and
hides health details.

Migrations live in three locations, on purpose:

| Location | Contents | Applied in |
|---|---|---|
| `classpath:db/migration` | `V1` schema, `V3` optimistic-locking column, `V4` brand-casing normalization | every profile |
| `classpath:db/search` | `V2` — `CREATE EXTENSION pg_trgm` and the trigram indexes | every profile |
| `classpath:db/seed` | `R__dev_seed.sql` — 53 demo motorcycles | `dev` only |

An environment that cannot install `pg_trgm` can drop the search location with
`SPRING_FLYWAY_LOCATIONS=classpath:db/migration`. Free-text search still works, it just
falls back to a sequential scan. Adding `db/search` back later needs
`spring.flyway.out-of-order: true`, because `V2` would then be applied after `V3`.

## Known limitations

These are deliberate iteration-1 boundaries, not oversights:

1. **Users are in memory**, configured in `application.yml`. Replacing them with a
   `User` entity touches only the `UserDetailsService` bean.
2. **Tokens cannot be revoked** before they expire — the trade-off for statelessness.
   The TTL is short for that reason.
3. **Seed figures in `db/seed/R__dev_seed.sql` are indicative demo values**, not an
   authoritative specification source. A real catalogue needs a vetted import pipeline.
4. **No rate limiting** on the public endpoints yet — image serving included.
5. **Images live on the local file system**, under `IMAGE_STORAGE_LOCATION`. A deployed
   environment must point that at a mounted volume: a container's own file system is
   discarded on every redeploy, and a second replica would not see the first one's uploads.
   `FileStorageService` deals in file names precisely so an S3 adapter can replace it without
   touching anything above.
6. **Optimistic locking covers `motorcycles` only, and only writes that genuinely overlap.**
   `version` is not yet in `MotorcycleResponse` nor accepted by `PUT` (no `If-Match`), so two
   admin edits racing each other still get a 409, but two sequential ones — edit, walk away,
   edit again later against what is by then stale data — silently overwrite each other. The
   specification blocks have no counter of their own; they are never written except through
   their motorcycle.

## Roadmap

- [x] REST API, data model, security, OpenAPI, CI
- [x] Flyway migrations, `pg_trgm` search indexes, optimistic locking
- [x] Comprehensive test coverage (100% instruction on core service/controller layers, 120+ tests)
- [x] Data integrity: brand-casing normalization (V4 migration)
- [ ] `User` entity, refresh tokens
- [ ] Redis cache on comparison responses, rate limiting

## CI/CD

**Maven CI Pipeline** — `.github/workflows/maven-ci.yml` builds and tests on JDK 23 (the target) and 21 (the
previous LTS, kept as the supported floor) for every push and PR to `main`, publishes
the JUnit report and the JaCoCo artifact from the JDK 23 run, and packages the boot jar
on `main`.

Both legs pass `-Dmaven.compiler.release=${{ matrix.java }}`. The pom pins
`java.version` to 23, and javac refuses a `--release` newer than itself, so without the
override the JDK 21 leg cannot compile at all — the floor it advertises would never
actually be built.

**Security Hardening** — All GitHub Actions are pinned to explicit commit SHAs (no floating tags),
preventing supply-chain attacks. Each action reference is cryptographically locked to its known-good version.

**SonarQube Cloud Analysis** — `.github/workflows/sonarqube.yml` runs continuous code quality analysis
on every push to `main` and every PR, scanning for bugs, code smells, security hotspots, and coverage metrics.
Results are published to [SonarCloud](https://sonarcloud.io/summary/overall?id=DinoSaulo_Motorcycle-Comparator-API).
