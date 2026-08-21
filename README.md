# Motorcycle Comparison API

RESTful backend for a motorcycle specification catalogue with side-by-side comparison,
in the spirit of "Tudo Celular" but for motorcycles.

Spring Boot 3.4 · Java 21 · PostgreSQL · JWT · OpenAPI

---

## Status

Iteration 1 — the API skeleton is complete, builds green, and is covered by 56 tests
(92% instruction / 80% branch coverage). The React frontend and the Selenium/Cucumber
E2E layer are not started yet.

## Quick start

```bash
# 1. A PostgreSQL instance
docker run --name motorcycle-db -e POSTGRES_DB=motorcycle_comparison \
  -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16

# 2. Run (dev profile is the default: schema is created from the entities and
#    seeded from data.sql on every boot)
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

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/motorcycles` | public | Paged, filtered catalogue |
| `GET` | `/api/v1/motorcycles/{id}` | public | One motorcycle, all specs |
| `GET` | `/api/v1/motorcycles/slug/{slug}` | public | Same, by public slug |
| `GET` | `/api/v1/motorcycles/brands` | public | Distinct brands, for the filter sidebar |
| `GET` | `/api/v1/motorcycles/compare?ids=1,2,3` | public | **Side-by-side comparison** |
| `POST` | `/api/v1/motorcycles` | `ROLE_ADMIN` | Create |
| `PUT` | `/api/v1/motorcycles/{id}` | `ROLE_ADMIN` | Replace |
| `DELETE` | `/api/v1/motorcycles/{id}` | `ROLE_ADMIN` | Delete |
| `POST` | `/api/v1/auth/login` | public | Exchange credentials for a JWT |

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

**Performance.** `open-in-view` is off, so lazy loading cannot silently become N+1
inside the serializer. Comparison uses one entity-graph query for the whole set;
catalogue pages rely on `default_batch_fetch_size` to pull the to-one blocks in
batches. Catalogue filters go through JPA Criteria, and the join to the engine table
is added only when an engine facet is actually in play.

**Security is stateless.** Reads are public — a comparison site behind a login is
useless — and every write requires `ROLE_ADMIN` carried in a JWT. Errors from the
security layer are rendered in the same `ApiError` shape as everything else.

## Testing

```bash
mvn test      # unit + slice tests
mvn verify    # + failsafe (*IT) + JaCoCo report at target/site/jacoco/index.html
```

| Layer | Tool | What it proves |
|---|---|---|
| `service/` | JUnit 5 + Mockito | Business rules in isolation: slug derivation, winner selection, null handling |
| `repository/` | `@DataJpaTest` + H2 | That entity graphs, criteria joins and JPQL actually compile and return the right rows |
| `controller/` | `@WebMvcTest` + MockMvc | HTTP contract: routing, binding, validation, status mapping |
| `controller/` | `@SpringBootTest` | Full pass through the real filter chain: 401 vs 403, admin round trip |

Tests use H2 in PostgreSQL mode via `src/test/resources/application.yml`, which shadows
the main config on the test classpath.

## Configuration

Everything is overridable by environment variable:

| Variable | Default | Notes |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local Postgres | |
| `JWT_SECRET` | dev-only value in `application.yml` | **Must be overridden outside dev.** Base64, ≥ 32 bytes decoded |
| `JWT_TTL` | `PT2H` | ISO-8601 duration |
| `ADMIN_PASSWORD` / `EDITOR_PASSWORD` | `admin123` / `editor123` | Dev only |
| `CORS_ALLOWED_ORIGINS` | `localhost:3000,localhost:5173` | |

Profiles: `dev` (default — `create-drop` + `data.sql` seed) and `prod`
(`ddl-auto: validate`, no seeding, health details hidden).

## Known limitations

These are deliberate iteration-1 boundaries, not oversights:

1. **No schema migrations.** `dev` rebuilds from the entities each boot. Flyway must
   land before anything is deployed — `prod` is already set to `validate`, so it will
   refuse to start against a schema nobody migrated.
2. **Users are in memory**, configured in `application.yml`. Replacing them with a
   `User` entity touches only the `UserDetailsService` bean.
3. **Tokens cannot be revoked** before they expire — the trade-off for statelessness.
   The TTL is short for that reason.
4. **Seed figures in `data.sql` are indicative demo values**, not an authoritative
   specification source. A real catalogue needs a vetted import pipeline.
5. **No rate limiting** on the public endpoints yet.

## Roadmap

- [x] REST API, data model, security, OpenAPI, CI
- [ ] Flyway migrations, `User` entity, refresh tokens
- [ ] React frontend with the side-by-side comparison table
- [ ] E2E tests with Selenium + Cucumber (wired to the `*IT` / failsafe phase)
- [ ] Redis cache on comparison responses, rate limiting

## CI

`.github/workflows/maven-ci.yml` builds and tests on JDK 21 and 23 for every push and
PR to `main`, publishes the JUnit report and the JaCoCo artifact, and packages the boot
jar on `main`.
