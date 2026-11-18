# JY-COMPUTERS Owner Administrator Authentication & Custom Claims

JY-COMPUTERS uses **Option A: Exclusive Owner Google Sign-In with Firebase Custom Claims** for single-administrator authorization on project `gen-lang-client-0200990761`.

## Active Architecture (Option A)
- **Single Authorized Identity**: Only the verified Google Sign-In account `joshua.dangana@gmail.com` (`firebase.sign_in_provider == 'google.com'`, `email_verified == true`) with server-assigned custom claims `{ jySingleAdmin: true, role: "admin" }` is recognized as administrator by both `firestore.rules` and the Android client (`AdminAuthClient`, `ProfileAndSettingsRepository.isUidAdmin`).
- **No Second Admin**: Clients, students, instructors, and other Google accounts cannot assign `role: "admin"` in Firestore or mint Firebase custom claims. Even another user possessing spoofed claims fails the exact `email == 'joshua.dangana@gmail.com'` check in `firestore.rules` and Kotlin.
- **No Unverified Client Secrets**: No service account keys, passwords, or hardcoded recovery secrets exist in the APK.

## Provisioning Owner Custom Claims (Server-Side Admin SDK)
Because this AI Studio container does not hold Google Cloud IAM / Admin SDK credentials for `gen-lang-client-0200990761`, the owner must run `provision-firebase-admin.js` once from Google Cloud Shell or a workstation with Application Default Credentials:

1. Sign in once in the Android app using Google Sign-In (`joshua.dangana@gmail.com`) so your Firebase Auth UID exists.
2. Run in Google Cloud Shell:
   ```bash
   cd admin-auth-server
   npm install
   GOOGLE_CLOUD_PROJECT=gen-lang-client-0200990761 node provision-firebase-admin.js --revoke-others
   ```
3. Sign out and sign back in under **Administrator Login -> Administrator Sign In with Google (Owner Only)** so your refreshed Firebase ID token contains `{ jySingleAdmin: true, role: "admin" }`.

## Legacy `admin-auth-server` (Optional / Undeployed)
`server.js` and `provision-admin.js` in this folder implement an optional Node/Cloud Run backend for custom-token issuance and write-only `/admin_auth_audit` logging. It is **not deployed** in the active Option A mobile architecture and is **not** called by the Android app (`ADMIN_AUTH_ENDPOINT_URL` is unused).

## Local Security Test Suite
```bash
cd admin-auth-server
npm test
```
