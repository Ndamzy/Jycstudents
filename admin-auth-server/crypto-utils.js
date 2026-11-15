"use strict";

const crypto = require("node:crypto");

const SINGLE_ADMIN_UID = "jy_single_owner_admin";
const SCRYPT_N = 16384;
const SCRYPT_R = 8;
const SCRYPT_P = 1;
const SCRYPT_KEYLEN = 64;

// Precomputed dummy scrypt hash so nonexistent usernames execute identical scrypt work
const DUMMY_SALT = Buffer.from("00112233445566778899aabbccddeeff", "hex");
const DUMMY_KEY = crypto.scryptSync("dummy-constant-time-work", DUMMY_SALT, SCRYPT_KEYLEN, {
  N: SCRYPT_N,
  r: SCRYPT_R,
  p: SCRYPT_P,
});

function hashSecret(plainText) {
  if (typeof plainText !== "string" || plainText.length === 0) {
    throw new Error("Secret value must be a non-empty string.");
  }
  const salt = crypto.randomBytes(16);
  const derived = crypto.scryptSync(plainText, salt, SCRYPT_KEYLEN, {
    N: SCRYPT_N,
    r: SCRYPT_R,
    p: SCRYPT_P,
  });
  return `scrypt$${SCRYPT_N}$${SCRYPT_R}$${SCRYPT_P}$${salt.toString("base64url")}$${derived.toString("base64url")}`;
}

function verifySecret(plainText, encodedHash) {
  if (typeof plainText !== "string" || plainText.length === 0 || typeof encodedHash !== "string") {
    crypto.scryptSync(plainText || "invalid", DUMMY_SALT, SCRYPT_KEYLEN, {
      N: SCRYPT_N,
      r: SCRYPT_R,
      p: SCRYPT_P,
    });
    return false;
  }
  const parts = encodedHash.split("$");
  if (parts.length !== 6 || parts[0] !== "scrypt") {
    crypto.scryptSync(plainText, DUMMY_SALT, SCRYPT_KEYLEN, {
      N: SCRYPT_N,
      r: SCRYPT_R,
      p: SCRYPT_P,
    });
    return false;
  }
  const N = Number(parts[1]);
  const r = Number(parts[2]);
  const p = Number(parts[3]);
  const salt = Buffer.from(parts[4], "base64url");
  const expected = Buffer.from(parts[5], "base64url");
  if (!Number.isFinite(N) || !Number.isFinite(r) || !Number.isFinite(p) || salt.length < 8 || expected.length < 16) {
    crypto.scryptSync(plainText, DUMMY_SALT, SCRYPT_KEYLEN, {
      N: SCRYPT_N,
      r: SCRYPT_R,
      p: SCRYPT_P,
    });
    return false;
  }
  const derived = crypto.scryptSync(plainText, salt, expected.length, { N, r, p });
  return crypto.timingSafeEqual(derived, expected);
}

function constantTimeEquals(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const aHash = crypto.createHash("sha256").update(a, "utf8").digest();
  const bHash = crypto.createHash("sha256").update(b, "utf8").digest();
  return crypto.timingSafeEqual(aHash, bHash);
}

function validateStrongPassword(password) {
  if (typeof password !== "string" || password.length < 12) {
    return "Password must be at least 12 characters long.";
  }
  if (!/[A-Z]/.test(password)) {
    return "Password must include at least one uppercase letter.";
  }
  if (!/[a-z]/.test(password)) {
    return "Password must include at least one lowercase letter.";
  }
  if (!/[0-9]/.test(password)) {
    return "Password must include at least one digit.";
  }
  if (!/[^A-Za-z0-9]/.test(password)) {
    return "Password must include at least one special character.";
  }
  return null;
}

function signSessionToken(payloadObj, secret) {
  const payloadB64 = Buffer.from(JSON.stringify(payloadObj), "utf8").toString("base64url");
  const sig = crypto.createHmac("sha256", secret).update(payloadB64).digest("base64url");
  return `${payloadB64}.${sig}`;
}

function verifySessionToken(token, secret) {
  if (typeof token !== "string" || !token.includes(".")) return null;
  const dotIdx = token.indexOf(".");
  const payloadB64 = token.slice(0, dotIdx);
  const sigB64 = token.slice(dotIdx + 1);
  if (!payloadB64 || !sigB64) return null;
  const expectedSig = crypto.createHmac("sha256", secret).update(payloadB64).digest("base64url");
  if (!constantTimeEquals(sigB64, expectedSig)) return null;
  try {
    return JSON.parse(Buffer.from(payloadB64, "base64url").toString("utf8"));
  } catch {
    return null;
  }
}

function parseCookies(cookieHeader) {
  const result = Object.create(null);
  if (typeof cookieHeader !== "string" || !cookieHeader.trim()) return result;
  for (const part of cookieHeader.split(";")) {
    const eq = part.indexOf("=");
    if (eq <= 0) continue;
    const key = part.slice(0, eq).trim();
    const val = part.slice(eq + 1).trim();
    if (key) result[key] = decodeURIComponent(val);
  }
  return result;
}

/**
 * Builds an unsigned custom token accepted ONLY by the Firebase Auth Emulator.
 * Used when FIREBASE_AUTH_EMULATOR_HOST is explicitly configured in test/local environments.
 */
function createEmulatorCustomToken(uid, claims = {}) {
  const nowSec = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: "none", typ: "JWT" })).toString("base64url");
  const payload = Buffer.from(
    JSON.stringify({
      iss: "firebase-auth-emulator@example.com",
      sub: "firebase-auth-emulator@example.com",
      aud: "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit",
      iat: nowSec,
      exp: nowSec + 3600,
      uid,
      claims,
    })
  ).toString("base64url");
  return `${header}.${payload}.`;
}

module.exports = {
  SINGLE_ADMIN_UID,
  DUMMY_KEY,
  hashSecret,
  verifySecret,
  constantTimeEquals,
  validateStrongPassword,
  signSessionToken,
  verifySessionToken,
  parseCookies,
  createEmulatorCustomToken,
};
