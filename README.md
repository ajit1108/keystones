# Keystone FSM — Field Service Management

A role-based field service management application: customers raise service requests, dispatchers triage and assign them, technicians work them in the field, and managers close them out with full SLA, inventory, photo and reporting visibility.

- **Backend:** Spring Boot 3.3.5, Java 21, Spring Security (stateless JWT), Spring Data MongoDB
- **Database:** MongoDB (embedded Flapdoodle MongoDB 4.20 for tests)
- **Frontend:** static HTML/CSS/vanilla JS single-page app, dark design system, no build step
- **Docs:** OpenAPI 3 via springdoc (`/swagger-ui.html`)

---

## 1. Feature summary

### Work orders
- Auto-generated codes (`WO-YYYYMMDD-####`) with an atomic per-day sequence.
- Lifecycle: `NEW → ASSIGNED → IN_PROGRESS → COMPLETED → CLOSED`, plus `ON_HOLD` and `CANCELLED`.
  Illegal transitions return `409 Conflict`; every transition appends an immutable status-history entry with actor, timestamp and optional note.
- Role-gated actions: only the assigned technician can start/complete their job; cancellation is manager/dispatcher; closing is manager-only.
- Assignment/reassignment, time logging, part usage (atomic stock decrement, overdraw rejected with `409`), photo attachments.
- Table and Kanban views, search, status filter, pagination; technician "My Jobs" mobile card view; read-only customer detail view (costs and internal notes stripped).

### SLA
- Policy by priority: **CRITICAL 4h, HIGH 24h, MEDIUM 72h, LOW 168h**.
- Status is derived: `ON_TRACK` → `AT_RISK` at 80% of the window → `BREACHED` after the due date (breach is sticky).
- Scheduled sweep creates at-risk/breach notifications; breach and at-risk counts are surfaced on the dashboard.

### Customers, sites and users
- Customer CRUD (manager/dispatcher); sites CRUD nested under each customer; customer users may only read their own organisation and sites.
- User administration (manager only): create, edit name/role/enabled, with guards against self-disable/self-demotion; `CUSTOMER` accounts must be linked to a customer.
- Customer portal: organisation summary, own request history, and a "New Service Request" form scoped server-side to the signed-in customer.

### Parts and reports
- Parts inventory with low-stock badges and paginated search; manager-only create/edit.
- Dashboard summary (manager/dispatcher): totals, by status, SLA buckets, overdue, by site and by technician, with optional customer/status filters.
- Reports page: same summary with customer, technician, status and date-range filters, plus by-status, by-priority, by-site and by-technician breakdowns.
- In-app notifications (bell + polling): assignment, SLA at-risk/breach, customer request; mark-one and mark-all read.

### Photos
- JPEG/PNG/WEBP/GIF, max 5 MB, stored on disk, metadata embedded in the work order.
- Downloads are authenticated and role-scoped (customer users only get photos on their own orders); upload/delete limited to people who can work on the order.

---

## 2. Repository layout

```
KeyStone/
├── backend/                  Spring Boot service
│   ├── src/main/java/com/keystone/fsm/
│   │   ├── config/           security, Mongo, OpenAPI, demo data seeder
│   │   ├── controller/       REST controllers (thin, DTOs only)
│   │   ├── domain/           entities, enums, SLA policy
│   │   ├── dto/              request/response records
│   │   ├── exception/        structured error handling
│   │   ├── repository/       Spring Data Mongo repositories
│   │   ├── security/         JWT filter, token provider, principal
│   │   └── service/          business rules, scoping, reporting
│   ├── src/test/             integration + unit tests
│   └── Dockerfile
├── frontend/                 static SPA (index.html, css/, js/)
│   ├── Dockerfile            nginx image
│   └── nginx.conf            serves static files, proxies /api to backend
├── docker-compose.yml        mongo + backend + frontend
└── .env.example
```

---

## 3. Run locally

### Prerequisites
- Java 21, Maven 3.9+
- MongoDB 6/7 reachable at `mongodb://localhost:27017/keystone` (or set `MONGODB_URI`)
- A static file server for the frontend (or open `frontend/index.html` directly)

### Backend
```bash
cd backend
mvn spring-boot:run
```
Listens on `http://localhost:8080`. Demo data is seeded on first run when the database is empty.

Run the test suite (uses embedded MongoDB, no local database needed):
```bash
cd backend
mvn test
```

### Frontend
The frontend has no build step. Serve the folder:
```bash
cd frontend
npx serve -l 3000
# or: python -m http.server 3000
```
Open `http://localhost:3000`.

API base resolution (`frontend/js/api.js`):
- opened from `file://` → `http://localhost:8080`
- served from a local dev port (e.g. 3000) → `http://localhost:8080`
- served by the bundled nginx container → same origin, `/api` proxied to `backend:8080`
- override with `window.KEYSTONE_API_BASE = 'https://api.example.com'` before the scripts load.

### Docker Compose
```bash
cp .env.example .env      # then edit secrets
docker compose up --build
```
- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

MongoDB data and uploaded photos live in the `mongo_data` and `uploads_data` volumes.

---

## 4. Demo accounts

| Role | Email | Password |
|------|-------|----------|
| Manager | `manager@keystone.com` | `Manager1!` |
| Dispatcher | `dispatcher@keystone.com` | `Dispatch1!` |
| Technician | `tech1@keystone.com` | `Technic1!` |
| Technician | `tech2@keystone.com` | `Technic2!` |
| Customer (Acme Corp) | `customer@acmecorp.com` | `Customer1!` |
| Customer (TechVenture) | `customer@techventure.com` | `Customer2!` |

The login screen has one-click buttons for the first four. Seeded data includes two customers, five sites, five parts and eight work orders across every status and SLA bucket.

> **Change these credentials before any real deployment.** They are demo seeds only.

---

## 5. Configuration

| Variable | Default | Purpose |
|----------|---------|---------|
| `MONGODB_URI` | `mongodb://localhost:27017/keystone` | Mongo connection string |
| `PORT` | `8080` | HTTP port; hosting platforms inject this |
| `JWT_SECRET` | dev-only default | HS256 signing key, min 32 chars — always override in production |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Token lifetime |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | `*` | Allowed browser origins — set the exact frontend URL in production |
| `UPLOAD_STORAGE` | `filesystem` | `filesystem` locally; `gridfs` on hosts with ephemeral disks |
| `UPLOAD_DIR` | `./uploads` | Photo storage directory (filesystem mode only) |
| `UPLOAD_MAX_BYTES` | `5242880` (5 MB) | Per-file upload limit |
| `SEED_DEMO_DATA` | `true` | Seed demo accounts/data when the database is empty |
| `SLA_CHECK_INTERVAL_MS` | `300000` | SLA sweep interval |
| `SLA_CHECK_INITIAL_DELAY_MS` | `60000` | Delay before the first sweep |

Never commit a real `.env`; it is ignored by git.

### Public deployment (Render + MongoDB Atlas)

The reference deployment uses two Render services fed from this repository:

| Service | Render type | Root directory | Command / publish dir |
|---------|-------------|----------------|-----------------------|
| `keystone-api` | Web service, Docker | `backend` | Dockerfile `Dockerfile`, health check `/v3/api-docs` |
| `keystone-web` | Static site | `frontend` | no build command, publish directory `.` |

Atlas: create a free M0 cluster, a database user limited to `readWrite` on `keystone`, and a network rule. On Render's free tier the egress IPs are dynamic, so the network rule is `0.0.0.0/0`.

Backend environment (Render → Environment):

```text
MONGODB_URI=mongodb+srv://<user>:<password>@<cluster>/keystone?retryWrites=true&w=majority
JWT_SECRET=<48-byte random value, generated once>
CORS_ALLOWED_ORIGIN_PATTERNS=https://<frontend-service>.onrender.com
UPLOAD_STORAGE=gridfs
SEED_DEMO_DATA=true
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75
```

Frontend: set the backend origin in `frontend/js/config.js` before deploying:

```js
window.KEYSTONE_API_BASE = 'https://<backend-service>.onrender.com';
```

Notes and limitations:

- Render free web services sleep after 15 minutes of inactivity; the first request afterwards can take 30–60 seconds. The SLA sweep only runs while the service is awake.
- Always-on backend requires a paid Render instance. Using GridFS avoids the extra cost of a persistent disk.
- The seeded demo accounts are public. Anyone can sign in as manager and edit demo data; disable `SEED_DEMO_DATA` or change the passwords for a private deployment.
- Swagger UI remains public at `/swagger-ui.html`; API documentation at `/v3/api-docs`.

---

## 6. API overview

All endpoints are under `/api` and require `Authorization: Bearer <token>` except login and the OpenAPI routes.

| Area | Endpoints |
|------|-----------|
| Auth | `POST /auth/login`, `GET /auth/me` |
| Users | `GET /users`, `GET /users/technicians`, `GET /users/{id}`, `POST /users`, `PUT /users/{id}` |
| Customers | `GET/POST /customers`, `GET /customers/me`, `GET/PUT /customers/{id}` |
| Sites | `GET/POST /customers/{id}/sites`, `GET /customers/{id}/sites/all`, `GET/PUT /customers/{id}/sites/{siteId}` |
| Work orders | `GET/POST /work-orders`, `GET/PUT /work-orders/{id}`, `POST /work-orders/{id}/{assign,status,parts,time}`, `POST|GET|DELETE /work-orders/{id}/attachments[/{attachmentId}]` |
| Parts | `GET/POST /parts`, `GET/PUT /parts/{id}` |
| Reports | `GET /reports/summary` (customer, technician, status, from, until) |
| Notifications | `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all` |

Errors use a consistent JSON body (`timestamp`, `status`, `message`, optional `fieldErrors`) with `400` validation, `401` auth, `403` authorization, `404` missing, `409` conflict/illegal transition.

---

## 7. Design decisions and deviations

These were deliberate, approved substitutions against the original brief:

| Brief | Implementation | Reason |
|-------|----------------|--------|
| PostgreSQL | MongoDB | Approved by the project owner to keep the existing database; documents fit nested status history, part usage and time logs naturally. |
| React + TypeScript | HTML/CSS/vanilla JS SPA | Approved to avoid a rewrite; keeps deployment to static files. |
| Email notifications | In-app notifications (extra) | No SMTP dependency; persisted notifications with polling and read state. |
| — | Photo attachments (extra) | Field evidence with role-scoped downloads. |

Other decisions:
- Multi-document transactions are enabled only when MongoDB runs as a replica set or mongos. On a standalone `mongod`, the app probes `hello` at startup, logs a warning and falls back to a no-op transaction manager: writes succeed but lose cross-document atomicity. Use Atlas or a replica set for ACID guarantees.
- SLA breach is **sticky** once recorded by the scheduler or computed on read, so a late completion cannot erase a breach.
- Stock is decremented with a single atomic `findAndModify`, so concurrent usage cannot oversell.
- Customer responses are built through `WorkOrderResponse.forCustomer`, which removes unit costs, labour minutes and internal notes.
- The frontend escapes all interpolated server strings (`esc()`); no raw HTML sinks carry user data.

---

## 8. Verified behaviour

The automated suite covers:

- login success/failure, protected endpoints, JSON 401
- role matrix on work-order creation, assignment, transitions and closing
- illegal transitions (`409`) and technician-only start/complete
- customer tenant scoping (own orders/sites only, foreign access forbidden)
- part usage stock decrement and overdraw rejection
- notification creation on assignment and mark-all-read
- photo upload, authenticated download, customer scoping and delete
- SLA policy windows, `dueAt`, 80% at-risk boundary and null-due handling

Commands and results are recorded in the final delivery report.
