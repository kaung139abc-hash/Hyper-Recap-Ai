# Hyper Recap AI License Server

Cloudflare Workers + D1 license backend. Supports 3-day trial, 30-day monthly, lifetime, hashed key storage, device binding, and owner admin endpoints.

## One-time Cloudflare setup

1. In Cloudflare, create a D1 database named `hyper-recap-licenses`.
2. Create a Cloudflare API token with Workers Scripts edit and D1 edit permissions for the intended account.
3. In the GitHub repository, open **Settings → Secrets and variables → Actions** and add these repository secrets:
   - `CLOUDFLARE_API_TOKEN`
   - `CLOUDFLARE_ACCOUNT_ID`
   - `D1_DATABASE_ID` (the database UUID from Cloudflare)
   - `ADMIN_SECRET` (a long, unique random secret; never commit or share it)
4. Open **Actions → Deploy Hyper Recap License Server → Run workflow**.
5. After a successful deployment, note the Worker URL shown in the deployment logs (normally `https://hyper-recap-license.<account-subdomain>.workers.dev`).

The workflow applies `schema.sql`, sets the Worker admin secret, and deploys the Worker. It does not need any Cloudflare credentials in source code.

## Admin dashboard

Open `admin.html` locally or host it on a private HTTPS site. Enter the deployed Worker URL and the admin secret. Never publish the admin secret or host the dashboard publicly without access control.

## API

- POST `/v1/activate` with `{ "licenseKey": "...", "deviceId": "..." }`
- POST `/v1/check` with the same fields
- POST `/admin/create` with `{ "plan": "trial|monthly|lifetime", "deviceLimit": 1, "customerNote": "..." }`
- POST `/admin/list`
- POST `/admin/revoke` with `{ "id": 123 }`
- POST `/admin/extend` with `{ "id": 123, "days": 30 }`
- POST `/admin/stats`

Admin endpoints require `Authorization: Bearer <ADMIN_SECRET>`. Only SHA-256 hashes of license keys are stored.

## Important production status

The Worker source and deployment workflow are prepared, but the service is **not deployed** until the repository secrets are configured and the workflow completes successfully. The Android client still contains a placeholder Worker URL; after deployment, update `LicenseManager.SERVER_URL` to the real Worker URL and build again. The current Android activation screen is not yet a complete paywall: paid features are not comprehensively blocked when a license is invalid.

Payments are manual; there is no payment provider/webhook yet. Add rate limiting, audit logs, abuse monitoring, owner-only dashboard hosting, privacy/refund terms, and server-side authorization for any paid cloud features before public launch. Client-side checks can be patched. A deliberate offline grace-period policy is also needed.
