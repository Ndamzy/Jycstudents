# JY-COMPUTERS Single-Administrator Authentication Server

Server-side authentication and session authority ensuring **only one exclusive owner administrator** (`jy_single_owner_admin`) can sign in and obtain Firebase custom claims (`jySingleAdmin: true`, `role: "admin"`).

## Security Controls Implemented
- **Zero Client Secrets**: Passwords, salted `scrypt` hashes (`ADMIN_PASSWORD_HASH`), recovery key hashes (`ADMIN_RECOVERY_KEY_HASH`), and `ADMIN_SESSION_SECRET` live exclusively on this server.
- **Google Sign-In Disabled for Admin**: Admin authentication uses `POST /admin/login` and mints short-lived Firebase custom tokens for `jy_single_owner_admin`. `firestore.rules` explicitly blocks `google.com` sign-in providers from admin privileges.
- **Brute-Force Protection**: 5 failed login/recovery attempts within 15 minutes triggers a 15-minute lockout (`HTTP 429` + `Retry-After`) with constant-time dummy `scrypt` work and generic errors (no username enumeration).
- **Session Fixation & CSRF Protection**: Fresh 256-bit `sessionId`, `HttpOnly; Secure; SameSite=Strict` cookies (`jy_admin_sid`), CSRF double-submit token validation, 30-minute inactivity expiry, 8-hour max session lifetime, and immediate server-side revocation on `/admin/logout`.
- **Single-Admin Enforcement**: No `/admin/register` or `/admin/create` endpoint exists; explicit requests return `HTTP 403`.
- **Owner-Only Password Recovery**: `POST /admin/recover` requires the offline server-provisioned recovery key, enforces password complexity (>=12 chars, upper, lower, digit, symbol), and increments `sessionVersion` to invalidate all active sessions immediately.
- **Write-Only Firestore Audit Log (`/admin_auth_audit`)**: Every successful, failed, and locked-out admin login attempt (plus logout and recovery events) is sanitized of all credentials/tokens and written via `firebase-admin` to Cloud Firestore `/admin_auth_audit` with server timestamps (`FieldValue.serverTimestamp()`) and non-sensitive `deviceInfo`. `firestore.rules` locks `/admin_auth_audit` against all client reads, updates, deletes, and spoofing writes.

## Option A: Direct Firebase Authentication + Custom Claims (No Extra Server Required)
The app maps the exclusive administrator username `admin` to `admin@jy-computers.com` in Firebase Authentication and verifies server-issued custom claims (`{ jySingleAdmin: true, role: "admin" }`, rejecting any `google.com` or student account).

1. Enable **Email/Password** in Firebase Console -> Authentication -> Sign-in method (project `gen-lang-client-0200990761`).
2. Provision the administrator user and custom claims via Google Cloud Shell or a workstation with Admin SDK credentials (never store plaintext passwords in source files):
   ```bash
   cd admin-auth-server
   npm install
   GOOGLE_CLOUD_PROJECT=gen-lang-client-0200990761 node provision-firebase-admin.js
   ```

## Option B: Hardened Cloud Run `admin-auth-server` (Optional Extra Layer)
1. **Provision private credentials locally (never commit output to Git):**
   ```bash
   cd admin-auth-server
   node provision-admin.js
   ```
2. **Deploy to Cloud Run (or your Node host) using Google Secret Manager / environment secrets** (ensure the Cloud Run service account has Cloud Datastore User / Firebase Auth permissions):
   ```bash
   gcloud run deploy jy-admin-auth \
     --source ./admin-auth-server \
     --region us-central1 \
     --allow-unauthenticated \
     --set-env-vars "ADMIN_USERNAME=...,ADMIN_PASSWORD_HASH=...,ADMIN_RECOVERY_KEY_HASH=...,ADMIN_SESSION_SECRET=...,FIRESTORE_DATABASE_ID=ai-studio-android-jycomput-682cf32f-41f3-47bf-a6b2-6ad57154d3a5"
   ```
3. **Configure the Android app:**
   Set `ADMIN_AUTH_ENDPOINT_URL=https://<your-cloud-run-url>` in **AI Studio Secrets** (or `.env`) and build the APK.

## Local Security Test Suite
```bash
cd admin-auth-server
npm test
```
