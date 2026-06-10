# AGENTS.md — McTicket

Multi-service helpdesk ticket system. Docker Compose at root.

| Service | Stack | Port | Entrypoint |
|---------|-------|------|------------|
| `backend` | Spring Boot 4.0.6 / Java 25 / Maven 3.9.16 | `8080` | `McTicketApplication.java` |
| `frontend` | Angular 21.2 / standalone / Material / Vitest | `80` (nginx) | `src/main.ts` |
| `nlp` | FastAPI / Python 3.13 / scikit-learn + YAKE | `8000` | `main.py` |
| `db` | PostgreSQL 18 Alpine | `5432` | — |
| `seaweedfs-master` | SeaweedFS (Go) | `9333` | `weed master` |
| `seaweedfs-volume` | SeaweedFS (Go) | `8080` | `weed volume` |
| `seaweedfs-filer` | SeaweedFS (Go) | `8888` | `weed filer` |
| `seaweedfs-s3` | SeaweedFS (Go) | `8333` | `weed s3` (S3 API gateway) |

## Agent runs in Docker, not on host

All commands (`mvn`, `ng`, `python`, `pip`, `node`) execute **inside the agent container** — host tooling is inaccessible. Build/run via `docker compose up --build`. Rebuild one: `docker compose build <svc>`.

## Backend

- **Package**: `it.domheadroom.mc_ticket`
- **Build**: `./mvnw package -DskipTests` (multi-stage Docker, tests skipped during build)
- **Test**: `./mvnw test` (runs in agent container only if Maven/mvnw available; otherwise `docker compose build backend` to verify compilation)
- **Env vars** (from `.env`): `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `SPRING_DATASOURCE_URL`, `NLP_BASE_URL`, `SEAWEEDFS_ACCESS_KEY`, `SEAWEEDFS_SECRET_KEY`, `APP_S3_ENDPOINT`
- **Object storage**: SeaweedFS (master + volume + filer + s3 gateway) replaces local Docker volume for uploads
- **File download**: `GET /api/attachments/{id}` streams from S3
- **JPA**: `ddl-auto=validate` — schema DDL must be pre-applied (see `helpdesk_schema.sql`)
- **DB schema**: `helpdesk`; extensions required: `pgcrypto`, `pg_trgm`, `btree_gin`
- **Seeded categories** (7): `rete`, `database`, `bug-applicativo`, `configurazione`, `hardware`, `servizi-web`, `altro`
- **API docs**: `http://localhost:8080/swagger-ui.html` (springdoc-openapi 2.8.6)
- **DB console**: H2 available at runtime (dev dependency)
- **Lombok**: annotation processor configured in `pom.xml`; excluded from fat JAR

## Frontend

- **Package manager**: Bun (in `angular.json`). Dockerfile uses `npm install` instead.
- **Test**: `ng test` (Vitest via `@angular/build:unit-test`)
- **No lint/typecheck** scripts defined. Formatter: **Prettier** (`.prettierrc` — single quotes, printWidth 100).
- **OpenAPI client** auto-generated from `src/openapi.json` → `src/app/generated/`. Regenerate after backend API changes: `npx @openapitools/openapi-generator-cli generate -i src/openapi.json -g typescript-angular -o src/app/generated`
- **Routes**: `login`, `home`, `ticket-list` (lazy-loaded standalone)
- **Auth guards**: `auth-guard`, `guest-guard`, `admin-guard` in `auth/guards/`
- **Angular conventions**: See `frontend/AGENTS.md` (standalone, signals, OnPush, `input()`/`output()`, native control flow, inject())
- **No tests run in Docker build** — only `npm run build`

## NLP

Real NLP (not a stub). Auto-trains at startup on 55 seed examples.

| Endpoint | Purpose |
|---|---|
| `POST /analyze` | Extract keywords + classify category + estimate priority |
| `POST /analyze-batch` | Same, for bulk tickets |
| `GET /categories` | List supported categories |
| `GET /priorities` | List priority levels |
| `GET /health` | Health check |

- **Keyword extraction**: YAKE (Italian), top 10 keywords sorted by relevance
- **Category classifier**: TF-IDF + LogisticRegression (scikit-learn) with keyword-based fallback when ML confidence < 0.4
  - Categories: `rete`, `database`, `bug-applicativo`, `configurazione`, `hardware`, `servizi-web`, `altro` (7 — matches DB seed)
- **Priority**: rule-based keyword scoring → `p1` (critical) … `p4` (low); default `p3`
- **Confidence**: average of category + priority confidence
- **Model persistence**: joblib at `/app/models/classifier.joblib` (empty volume = retrains from seed)
- **Install deps**: `pip install -r requirements.txt`; run: `uvicorn main:app --reload`

## General

- `.env` at root (gitignored) supplies secrets to Docker Compose
- `JWT_SECRET` — base64-encoded, 24h expiration
- DB is ephemeral (named volume `pgdata`). Adminer at `http://localhost:8081`
- Italian language throughout: DB FTS uses `'italian'` config, NLP models/stoplists are Italian
