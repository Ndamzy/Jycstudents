#!/usr/bin/env node
"use strict";

/**
 * Provisions the single JY-COMPUTERS Administrator user in Firebase Authentication
 * (`admin@jy-computers.com` mapped from username `admin`) and sets server-side
 * custom claims (`{ jySingleAdmin: true, role: "admin" }`).
 *
 * Security notes:
 *   - NEVER hardcodes or commits the administrator password to source control.
 *   - Run from a trusted workstation or Google Cloud Shell with Application Default Credentials
 *     or GOOGLE_APPLICATION_CREDENTIALS pointing to a service account key for project
 *     `gen-lang-client-0200990761`.
 *
 * Usage:
 *   cd admin-auth-server && npm install
 *   GOOGLE_CLOUD_PROJECT=gen-lang-client-0200990761 node provision-firebase-admin.js
 */

const readline = require("node:readline");
const { SINGLE_ADMIN_UID } = require("./crypto-utils");

const ADMIN_EMAIL = "admin@jy-computers.com";
const DEFAULT_PROJECT_ID = "gen-lang-client-0200990761";

function promptHidden(question) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
      terminal: Boolean(process.stdin.isTTY),
    });
    const stdin = process.stdin;
    const onData = (char) => {
      const ch = String(char);
      if (ch === "\n" || ch === "\r" || ch === "\u0004") {
        stdin.removeListener("data", onData);
      } else {
        process.stdout.clearLine?.(0);
        process.stdout.cursorTo?.(0);
        process.stdout.write(question + "*".repeat(rl.line.length));
      }
    };
    if (stdin.isTTY) stdin.on("data", onData);
    rl.question(question, (answer) => {
      if (stdin.isTTY) process.stdout.write("\n");
      rl.close();
      resolve(answer);
    });
  });
}

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
    userRecord = await auth.getUserByEmail(ADMIN_EMAIL);
    console.log(`Found existing Firebase Auth admin user: uid=${userRecord.uid}, email=${userRecord.email}`);
  } catch (err) {
    if (err?.code !== "auth/user-not-found") {
      throw err;
    }
  }

  if (!userRecord) {
    try {
      userRecord = await auth.getUser(SINGLE_ADMIN_UID);
      console.log(`Found existing Firebase Auth user by UID: uid=${userRecord.uid}`);
    } catch (err) {
      if (err?.code !== "auth/user-not-found") {
        throw err;
      }
    }
  }

  let password = process.env.ADMIN_INITIAL_PASSWORD || "";
  if (!userRecord || process.argv.includes("--reset-password")) {
    if (!password) {
      password = await promptHidden(`Enter Administrator Password for '${ADMIN_EMAIL}': `);
    }
    if (!password || password.length < 8) {
      throw new Error("Administrator password must be at least 8 characters long.");
    }
  }

  if (!userRecord) {
    userRecord = await auth.createUser({
      uid: SINGLE_ADMIN_UID,
      email: ADMIN_EMAIL,
      emailVerified: true,
      displayName: "JY-Computers Administrator",
      password,
      disabled: false,
    });
    console.log(`Created administrator user: uid=${userRecord.uid}, email=${userRecord.email}`);
  } else {
    const updates = {
      email: ADMIN_EMAIL,
      emailVerified: true,
      displayName: "JY-Computers Administrator",
      disabled: false,
    };
    if (password) {
      updates.password = password;
    }
    userRecord = await auth.updateUser(userRecord.uid, updates);
    console.log(`Updated administrator user: uid=${userRecord.uid}, email=${userRecord.email}`);
  }

  await auth.setCustomUserClaims(userRecord.uid, {
    jySingleAdmin: true,
    role: "admin",
  });

  const verified = await auth.getUser(userRecord.uid);
  console.log("Verified Firebase Custom Claims:", JSON.stringify(verified.customClaims || {}));
  console.log("Provisioning complete. Username 'admin' now authenticates cleanly via Firebase Authentication.");
}

if (require.main === module) {
  main().catch((err) => {
    console.error("Failed to provision Firebase administrator:", err?.message || err);
    process.exitCode = 1;
  });
}
