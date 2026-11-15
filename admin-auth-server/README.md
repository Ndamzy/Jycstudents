# JY-COMPUTERS Single-Administrator Authentication Server

Server-side authentication and session authority ensuring **only one exclusive owner administrator** (`jy_single_owner_admin`) can sign in and obtain Firebase custom claims (`jySingleAdmin: true`, `role: "admin"`).

## Security Controls Implemented
- **Zero Client Secrets**: Passwords, salted `scrypt` hashes (`ADMIN_PASSWORD_HASH`), recovery key hashes (`ADMIN_RECOVERY_KEY_HASH`), and `ADMIN_SESSION_SECRET` live exclusively on this server.
- **Google Sign-In Disabled for Admin**: Admin authentication uses `POST /admin/login` and mints short-lived Firebase custom tokens for `jy_single_owner_admin`. `firestore.rules` explicitly blocks `google.com` sign-in providers from admin privileges.
- **Brute-Force Protection**: 5 failed login/recovery attempts within 15 minutes triggers a 15-minute lockout (`HTTP 429` + `Retry-After`) with constant-time dummy `scrypt` work and generic errors (no username enumeration).
- **Session Fixation & CSRF Protection**: Fresh 256-bit `sessionId`, `HttpOnly; Secure; SameSite=Strict` cookies (`jy_admin_sid`), CSRF double-submit token validation, 30-minute inactivity expiry, 8-hour max session lifetime, and immediate server-side revocation on `/admin/logout`.
- **Single-Admin Enforcement**: No `/admin/register` or `/admin/create` endpoint exists; explicit requests return `HTTP 403`.
- **Owner-Only Password Recovery**: `POST /admin/recover` requires the offline server-provisioned recovery key, enforces password complexity (>=12 chars, upper, lower, digit, symbol), and increments `sessionVersion` to invalidate all active sessions immediately.

## Setup & Cloud Run Deployment
1. **Provision private credentials locally (never commit output to Git):**
   ```bash
   cd admin-auth-server
   node provision-admin.js
   ```
2. **Deploy to Cloud Run (or your Node host) using Google Secret Manager / environment secrets:**
   ```bash
   gcloud run deploy jy-admin-auth \
     --source ./admin-auth-server \
     --region us-central1 \
     --allow-unauthenticated \
     --set-env-vars "ADMIN_USERNAME=...,ADMIN_PASSWORD_HASH=...,ADMIN_RECOVERY_KEY_HASH=...,ADMIN_SESSION_SECRET=..."
   ```
3. **Configure the Android app:**
   Set `ADMIN_AUTH_ENDPOINT_URL=https://<your-cloud-run-url>` in **AI Studio Secrets** (or `.env`) and build the APK.

## Local Security Test Suite
```bash
cd admin-auth-server
npm test
```
