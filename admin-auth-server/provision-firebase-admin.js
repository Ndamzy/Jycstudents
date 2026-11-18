#!/usr/bin/env node
"use strict";

/**
 * Option A: Assigns exclusive single-administrator Firebase Custom Claims
 * (`{ jySingleAdmin: true, role: "admin" }`) to the owner's existing verified
 * Google Sign-In Firebase user (`joshua.dangana@gmail.com`).
 *
 * Security guarantees:
 *   - NEVER creates a duplicate Firebase user or sets/stores a password.
 *   - NEVER deletes or modifies existing student, instructor, or payment data.
 *   - Verifies the exact owner email and Google Sign-In provider (`google.com`)
 *     before writing custom claims, and optionally audits/strips any stray admin
 *     claims from other users if `--revoke-others` is passed.
 *
 * Prerequisites:
 *   1. The owner must sign in at least once in JY-COMPUTERS using Google Sign-In
 *      with `joshua.dangana@gmail.com` so the Firebase Auth UID exists.
 *   2. Run from Google Cloud Shell or a trusted workstation with Admin SDK credentials
 *      for Firebase project `gen-lang-client-0200990761`.
 *
 * Usage:
 *   cd admin-auth-server && npm install
 *   GOOGLE_CLOUD_PROJECT=gen-lang-client-0200990761 node provision-firebase-admin.js
 */

const OWNER_ADMIN_EMAIL = "joshua.dangana@gmail.com";
const EXPECTED_OWNER_UID = "msUb7EcbUye0HyBqCNMe7FCIKvA3";
const DEFAULT_PROJECT_ID = "gen-lang-client-0200990761";

async function main() {
  const admin = require("firebase-admin");
  const projectId =
    process.env.GOOGLE_CLOUD_PROJECT ||
    process.env.GCLOUD_PROJECT ||
    process.env.GCP_PROJECT ||
    DEFAULT_PROJECT_ID;

  if (!admin.apps.length) {
    admin.initializeApp({ projectId });
  }

  const auth = admin.auth();
  let userRecord = null;
  try {
    userRecord = await auth.getUser(EXPECTED_OWNER_UID);
  } catch (err) {
    if (err?.code === "auth/user-not-found") {
      throw new Error(
        `No Firebase Auth account found for pinned owner UID '${EXPECTED_OWNER_UID}'. Verify the account exists in Firebase Authentication first.`
      );
    }
    throw err;
  }

  if (userRecord.uid !== EXPECTED_OWNER_UID) {
    throw new Error(`UID mismatch: expected '${EXPECTED_OWNER_UID}', got '${userRecord.uid}'.`);
  }

  const normalizedEmail = (userRecord.email || "").trim().toLowerCase();
  if (normalizedEmail !== OWNER_ADMIN_EMAIL) {
    throw new Error(
      `Email mismatch for UID '${EXPECTED_OWNER_UID}': expected '${OWNER_ADMIN_EMAIL}', got '${userRecord.email}'. Aborting without setting claims.`
    );
  }

  const byEmail = await auth.getUserByEmail(OWNER_ADMIN_EMAIL);
  if (byEmail.uid !== EXPECTED_OWNER_UID) {
    throw new Error(
      `Email lookup mismatch: '${OWNER_ADMIN_EMAIL}' resolved to '${byEmail.uid}', expected '${EXPECTED_OWNER_UID}'.`
    );
  }

  const providers = (userRecord.providerData || []).map((p) => p.providerId);
  console.log(
    `Verified existing owner user: uid=${userRecord.uid}, email=${userRecord.email}, emailVerified=${userRecord.emailVerified}, providers=[${providers.join(", ")}]`
  );

  if (userRecord.emailVerified !== true) {
    throw new Error(
      `Owner account '${OWNER_ADMIN_EMAIL}' (uid=${userRecord.uid}) does not have emailVerified=true. Verify the email in Google/Firebase before provisioning claims.`
    );
  }

  if (!providers.includes("google.com")) {
    throw new Error(
      `Account '${OWNER_ADMIN_EMAIL}' does not have Google Sign-In ('google.com') linked. Sign in with Google first.`
    );
  }

  await auth.setCustomUserClaims(EXPECTED_OWNER_UID, {
    jySingleAdmin: true,
    role: "admin",
  });

  if (process.argv.includes("--revoke-others")) {
    let pageToken;
    let strippedCount = 0;
    do {
      const page = await auth.listUsers(1000, pageToken);
      for (const u of page.users) {
        if (u.uid !== EXPECTED_OWNER_UID && u.customClaims && (u.customClaims.jySingleAdmin || u.customClaims.role === "admin")) {
          const cleaned = { ...u.customClaims };
          delete cleaned.jySingleAdmin;
          if (cleaned.role === "admin") delete cleaned.role;
          await auth.setCustomUserClaims(u.uid, Object.keys(cleaned).length ? cleaned : null);
          strippedCount += 1;
          console.log(`Revoked stale admin claims from non-owner user uid=${u.uid} (${u.email || "no-email"})`);
        }
      }
      pageToken = page.pageToken;
    } while (pageToken);
    console.log(`Audit complete. Revoked stale admin claims from ${strippedCount} other user(s).`);
  }

  const verified = await auth.getUser(EXPECTED_OWNER_UID);
  console.log("Verified Firebase Custom Claims:", JSON.stringify(verified.customClaims || {}));
  console.log(
    `Provisioning complete. ${OWNER_ADMIN_EMAIL} (uid=${verified.uid}) is now the exclusive JY-COMPUTERS owner administrator.`
  );
}

if (require.main === module) {
  main().catch((err) => {
    console.error("Failed to provision Firebase owner custom claims:", err?.message || err);
    process.exitCode = 1;
  });
}
