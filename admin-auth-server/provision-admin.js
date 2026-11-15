#!/usr/bin/env node
"use strict";

/**
 * Private Administrator Provisioning Script for JY-COMPUTERS.
 *
 * Generates strong salted scrypt hashes for:
 *   - ADMIN_USERNAME
 *   - ADMIN_PASSWORD_HASH
 *   - ADMIN_RECOVERY_KEY_HASH
 *   - ADMIN_SESSION_SECRET
 *
 * Never stores plaintext credentials in source control or client code.
 */

const crypto = require("node:crypto");
const readline = require("node:readline");
const { hashSecret, validateStrongPassword, SINGLE_ADMIN_UID } = require("./crypto-utils");

function promptHidden(rl, question) {
  return new Promise((resolve) => {
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
    if (stdin.isTTY) {
      stdin.on("data", onData);
    }
    rl.question(question, (answer) => {
      if (stdin.isTTY) process.stdout.write("\n");
      resolve(answer);
    });
  });
}

async function main() {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: Boolean(process.stdin.isTTY),
  });

  try {
    const username = await new Promise((resolve) => {
      rl.question("Enter private Owner Admin Username (e.g. jy_owner_admin): ", (ans) =>
        resolve(ans.trim())
      );
    });
    if (username.length < 5) {
      console.error("Error: Admin username must be at least 5 characters.");
      process.exitCode = 1;
      return;
    }

    const password = await promptHidden(
      rl,
      "Enter strong Admin Password (>=12 chars, upper, lower, digit, symbol): "
    );
    const pwError = validateStrongPassword(password);
    if (pwError) {
      console.error(`Error: ${pwError}`);
      process.exitCode = 1;
      return;
    }

    const confirm = await promptHidden(rl, "Confirm Admin Password: ");
    if (password !== confirm) {
      console.error("Error: Passwords do not match.");
      process.exitCode = 1;
      return;
    }

    const rawRecoveryKey = crypto.randomBytes(24).toString("base64url");
    const passwordHash = hashSecret(password);
    const recoveryKeyHash = hashSecret(rawRecoveryKey);
    const sessionSecret = crypto.randomBytes(48).toString("base64url");

    console.log("\n=== JY-COMPUTERS SINGLE-ADMINISTRATOR SERVER SECRETS ===");
    console.log(`SINGLE_ADMIN_UID=${SINGLE_ADMIN_UID}`);
    console.log(`ADMIN_USERNAME=${username}`);
    console.log(`ADMIN_PASSWORD_HASH=${passwordHash}`);
    console.log(`ADMIN_RECOVERY_KEY_HASH=${recoveryKeyHash}`);
    console.log(`ADMIN_SESSION_SECRET=${sessionSecret}`);
    console.log("\n=== SAVE THIS OWNER-ONLY RECOVERY KEY OFFLINE (SHOWN ONCE) ===");
    console.log(`OWNER_RECOVERY_KEY=${rawRecoveryKey}`);
    console.log("Store OWNER_RECOVERY_KEY in an offline password vault. Do NOT commit any of these values to Git.\n");
  } finally {
    rl.close();
  }
}

if (require.main === module) {
  main().catch((err) => {
    console.error(err);
    process.exitCode = 1;
  });
}
