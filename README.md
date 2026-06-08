# ZenoLabs

Laboratory Information System for the ZenoHosp suite. Mirrors the HMS Radiology
module 1:1 (entities, services, UI, billing integration) against the **shared
HMS database** so a lab order behaves exactly like a radiology order — including
auto-billing on report generation that routes IPD → existing admission invoice
and OPD → standalone walk-in invoice.

```
labs/
├── labs-backend/   Spring Boot 3.4.3, Java 21 (Render, Docker)
└── labs-frontend/  Vite + React 18 (Vercel)
```

## Domains

| App           | URL                              |
|---------------|----------------------------------|
| Lab API       | https://api-labs.zenohosp.com    |
| Lab UI        | https://labs.zenohosp.com        |

## Local development

```bash
# Terminal 1 — backend on :8086 (shares HMS Supabase)
cd labs-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2 — frontend on :5175 (proxies /api → :8086)
cd labs-frontend
npm install
npm run dev
```

HMS keeps `:9001` — no port conflicts. Login goes through Directory SSO; the
shared `sso_token` cookie means signing in on hms.zenohosp.com or
directory.zenohosp.com lands you straight into the labs UI too.

## Key surface

- `POST /api/lab` — create order (capture `price` at order time)
- `PATCH /api/lab/{id}/collect` — mark sample collected
- `PATCH /api/lab/{id}/report` — finalise report; **auto-bills** if price > 0
- `GET /api/lab?status=COMPLETED` — union of `REPORT_GENERATED + BILLED`

The `lab_orders` table is the only schema labs owns; everything else
(`patients`, `admissions`, `hospital_services`, `invoices`, `invoice_items`,
`users`, `hospitals`) is read-only / append-only against the shared HMS schema.
