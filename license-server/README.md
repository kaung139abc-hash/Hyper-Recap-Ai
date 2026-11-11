# Hyper Recap AI License Server

Cloudflare Workers + D1 license backend. Supports trial (3 days), monthly (30 days), lifetime, hashed key storage, device binding, admin create/list/revoke/extend/stats.

## Deploy
1. Install Wrangler and sign in to Cloudflare.
2. Create D1: `npx wrangler d1 create hyper-recap-licenses`; copy the returned database_id into wrangler.toml.
3. Apply schema: `npx wrangler d1 execute hyper-recap-licenses --file=./schema.sql`.
4. Set a strong admin secret: `npx wrangler secret put ADMIN_SECRET`.
5. Deploy: `npx wrangler deploy`.

## API
- POST /v1/activate {licenseKey, deviceId}
- POST /v1/check {licenseKey, deviceId}
- POST /admin/create {plan, deviceLimit, customerNote}
- POST /admin/list {}
- POST /admin/revoke {id}
- POST /admin/extend {id, days}
- POST /admin/stats {}

Admin endpoints require Authorization: Bearer <ADMIN_SECRET>. Store only SHA-256 hashes of license keys. Keep ADMIN_SECRET off the Android client.

## Important
This is a backend foundation, not a live deployed service. Replace the D1 placeholder and deploy it to obtain the API URL. The Android app is not yet wired to this API. Payments are manual until a payment provider and webhook are configured. Add rate limiting, audit logs, abuse monitoring, HTTPS custom domain, and a proper owner-only admin UI before public launch. A client-side license check can be patched; use server-side checks for any paid cloud functionality and treat offline grace periods as a deliberate business rule.