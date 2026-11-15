"use strict";

const { test, beforeEach, afterEach } = require("node:test");
const assert = require("node:assert/strict");
const { createAdminAuthService } = require("./server");
const { hashSecret, createEmulatorCustomToken } = require("./crypto-utils");

const TEST_ADMIN_USER = "jy_owner_admin_test";
const TEST_ADMIN_PASS = "StrongOwner#2026!";
const TEST_RECOVERY_KEY = "OwnerRecoveryKey-99887766";
const TEST_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";

let currentNow = 1_700_000_000_000;
let server;
let baseUrl;
let service;
let persistedAuditDocs = [];

beforeEach(async () => {
  currentNow = 1_700_000_000_000;
  persistedAuditDocs = [];
  service = createAdminAuthService({
    adminUsername: TEST_ADMIN_USER,
    adminPasswordHash: hashSecret(TEST_ADMIN_PASS),
    adminRecoveryKeyHash: hashSecret(TEST_RECOVERY_KEY),
    sessionSecret: TEST_SECRET,
    nowFn: () => currentNow,
    maxFailedAttempts: 5,
    lockoutMs: 15 * 60 * 1000,
    sessionTtlMs: 8 * 60 * 60 * 1000,
    inactivityTimeoutMs: 30 * 60 * 1000,
    firebaseBridge: {
      mode: "test-stub",
      async createCustomToken(uid, claims) {
        return createEmulatorCustomToken(uid, claims);
      },
      async revokeRefreshTokens() {},
      async writeAuditLog(entry) {
        persistedAuditDocs.push(entry);
      },
    },
  });
  server = service.createHttpServer();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const addr = server.address();
  baseUrl = `http://127.0.0.1:${addr.port}`;
});

afterEach(async () => {
  if (server) {
    await new Promise((resolve) => server.close(resolve));
  }
});

test("TEST 1: Valid owner administrator login issues session token, HttpOnly/Secure/SameSite=Strict cookies, and customToken", async () => {
  const res = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Cookie: "jy_admin_sid=attacker_fixed_session" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: TEST_ADMIN_PASS }),
  });
  assert.equal(res.status, 200);
  const json = await res.json();
  assert.equal(json.uid, "jy_single_owner_admin");
  assert.equal(json.role, "admin");
  assert.ok(json.sessionToken);
  assert.ok(json.csrfToken);
  assert.ok(json.customToken);
  assert.notEqual(json.sessionToken, "attacker_fixed_session");

  const setCookies = res.headers.getSetCookie();
  assert.ok(setCookies.some((c) => c.startsWith("jy_admin_sid=") && c.includes("HttpOnly") && c.includes("Secure") && c.includes("SameSite=Strict")));
  assert.ok(setCookies.some((c) => c.startsWith("jy_admin_csrf=") && c.includes("SameSite=Strict") && !c.includes("HttpOnly")));
});

test("TEST 2: Wrong password or wrong username returns generic 401 without user enumeration", async () => {
  const wrongPass = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: "WrongPassword#123" }),
  });
  assert.equal(wrongPass.status, 401);
  const wrongPassJson = await wrongPass.json();

  const wrongUser = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "someone_else", password: "WrongPassword#123" }),
  });
  assert.equal(wrongUser.status, 401);
  const wrongUserJson = await wrongUser.json();

  assert.equal(wrongPassJson.error, "Invalid administrator credentials.");
  assert.equal(wrongUserJson.error, wrongPassJson.error);
});

test("TEST 6: Direct unauthenticated request & missing CSRF cookie header on admin APIs are rejected", async () => {
  const noAuth = await fetch(`${baseUrl}/admin/session`);
  assert.equal(noAuth.status, 401);

  const noAuthLogs = await fetch(`${baseUrl}/admin/audit-logs`);
  assert.equal(noAuthLogs.status, 401);

  const loginRes = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: TEST_ADMIN_PASS }),
  });
  const { sessionToken, csrfToken } = await loginRes.json();

  // Cookie request without CSRF header -> 403
  const missingCsrf = await fetch(`${baseUrl}/admin/refresh`, {
    method: "POST",
    headers: { Cookie: `jy_admin_sid=${sessionToken}; jy_admin_csrf=${csrfToken}` },
  });
  assert.equal(missingCsrf.status, 403);

  // Cookie request WITH matching CSRF header -> 200
  const withCsrf = await fetch(`${baseUrl}/admin/refresh`, {
    method: "POST",
    headers: {
      Cookie: `jy_admin_sid=${sessionToken}; jy_admin_csrf=${csrfToken}`,
      "X-CSRF-Token": csrfToken,
    },
  });
  assert.equal(withCsrf.status, 200);
});

test("TEST 8: Creating a second administrator account via HTTP endpoints is permanently blocked (403)", async () => {
  const loginRes = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: TEST_ADMIN_PASS }),
  });
  const { sessionToken } = await loginRes.json();

  for (const path of ["/admin/register", "/admin/create", "/admin/signup", "/admin/grant"]) {
    const res = await fetch(`${baseUrl}${path}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${sessionToken}`,
      },
      body: JSON.stringify({ username: "second_admin", password: "NewPassword#2026!" }),
    });
    assert.equal(res.status, 403);
  }
});

test("TEST 9: Logout & inactivity timeout invalidate admin session tokens immediately", async () => {
  const loginRes = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: TEST_ADMIN_PASS }),
  });
  const { sessionToken } = await loginRes.json();

  const logoutRes = await fetch(`${baseUrl}/admin/logout`, {
    method: "POST",
    headers: { Authorization: `Bearer ${sessionToken}` },
  });
  assert.equal(logoutRes.status, 200);

  const afterLogoutSession = await fetch(`${baseUrl}/admin/session`, {
    headers: { Authorization: `Bearer ${sessionToken}` },
  });
  assert.equal(afterLogoutSession.status, 401);

  const afterLogoutRefresh = await fetch(`${baseUrl}/admin/refresh`, {
    method: "POST",
    headers: { Authorization: `Bearer ${sessionToken}` },
  });
  assert.equal(afterLogoutRefresh.status, 401);

  // New login expires after 30 minutes of inactivity
  const login2 = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: TEST_ADMIN_PASS }),
  });
  const { sessionToken: token2 } = await login2.json();
  currentNow += 31 * 60 * 1000;
  const expiredRes = await fetch(`${baseUrl}/admin/session`, {
    headers: { Authorization: `Bearer ${token2}` },
  });
  assert.equal(expiredRes.status, 401);
});

test("TEST 10: Repeated failed logins trigger rate-limit lockout (429) even against subsequent valid password until expiry", async () => {
  for (let i = 0; i < 4; i++) {
    const fail = await fetch(`${baseUrl}/admin/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: TEST_ADMIN_USER, password: `BadPass#${i}` }),
    });
    assert.equal(fail.status, 401);
  }

  // 5th failure triggers lockout (429)
  const fifth = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: "BadPass#5" }),
  });
  assert.equal(fifth.status, 429);
  assert.ok(fifth.headers.get("retry-after"));

  // Even the valid password is blocked during active lockout
  const blockedValid = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: TEST_ADMIN_PASS }),
  });
  assert.equal(blockedValid.status, 429);

  // Advance clock past lockout (15+ minutes) -> valid password succeeds again
  currentNow += 16 * 60 * 1000;
  const unlocked = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: TEST_ADMIN_PASS }),
  });
  assert.equal(unlocked.status, 200);
});

test("Owner-only password recovery updates hash and invalidates all active sessions", async () => {
  const loginRes = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: TEST_ADMIN_PASS }),
  });
  const { sessionToken } = await loginRes.json();

  // Bad recovery key rejected
  const badRecover = await fetch(`${baseUrl}/admin/recover`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      username: TEST_ADMIN_USER,
      recoveryKey: "WrongRecoveryKey",
      newPassword: "RotatedStrong#2027!",
    }),
  });
  assert.equal(badRecover.status, 401);

  // Valid owner recovery rotates password and revokes existing session
  const goodRecover = await fetch(`${baseUrl}/admin/recover`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      username: TEST_ADMIN_USER,
      recoveryKey: TEST_RECOVERY_KEY,
      newPassword: "RotatedStrong#2027!",
    }),
  });
  assert.equal(goodRecover.status, 200);

  const oldSession = await fetch(`${baseUrl}/admin/session`, {
    headers: { Authorization: `Bearer ${sessionToken}` },
  });
  assert.equal(oldSession.status, 401);

  const oldPassLogin = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: TEST_ADMIN_PASS }),
  });
  assert.equal(oldPassLogin.status, 401);

  const newPassLogin = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: TEST_ADMIN_USER, password: "RotatedStrong#2027!" }),
  });
  assert.equal(newPassLogin.status, 200);
});

test("TEST 11: Write-only audit logger captures success/failure/deviceInfo/timestamp and strips all sensitive credentials", async () => {
  const { stripSensitiveFieldsDeep } = require("./audit-logger");

  const injectedDevice = {
    platform: "Android",
    manufacturer: "Samsung",
    brand: "samsung",
    model: "SM-A546B",
    device: "a54x",
    osVersion: "14",
    sdkInt: 34,
    appVersion: "1.0",
    appPackage: "com.aistudio.jycomputers.mrxqkp",
    // Attempted credential pollution inside deviceInfo:
    password: "leaked_password_attempt",
    username: TEST_ADMIN_USER,
    token: "leaked_token_attempt",
    secret: "leaked_secret",
    recoveryKey: TEST_RECOVERY_KEY,
    userAgent: "Bearer supersecrettoken123",
  };

  // 1. Failed attempt
  const failRes = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "User-Agent": "scrypt$16384$fakehash" },
    body: JSON.stringify({
      username: TEST_ADMIN_USER,
      password: "WrongAttempt#Password1",
      deviceInfo: injectedDevice,
    }),
  });
  assert.equal(failRes.status, 401);

  // 2. Successful login attempt
  const okRes = await fetch(`${baseUrl}/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "User-Agent": "okhttp/4.12.0 JYComputers/1.0" },
    body: JSON.stringify({
      username: TEST_ADMIN_USER,
      password: TEST_ADMIN_PASS,
      deviceInfo: injectedDevice,
    }),
  });
  assert.equal(okRes.status, 200);
  const okJson = await okRes.json();

  assert.equal(persistedAuditDocs.length, 2);
  const [failDoc, okDoc] = persistedAuditDocs;

  assert.equal(failDoc.eventType, "ADMIN_LOGIN_FAILED");
  assert.equal(failDoc.outcome, "failed");
  assert.equal(failDoc.usernameMatched, true);
  assert.equal(failDoc.deviceInfo.manufacturer, "Samsung");
  assert.equal(failDoc.deviceInfo.model, "SM-A546B");
  assert.equal(failDoc.deviceInfo.sdkInt, 34);
  assert.equal(failDoc.deviceInfo.userAgent, "[REDACTED]");
  assert.ok(failDoc.timestampIso);

  assert.equal(okDoc.eventType, "ADMIN_LOGIN_SUCCESS");
  assert.equal(okDoc.outcome, "success");
  assert.equal(okDoc.usernameMatched, true);
  assert.equal(okDoc.deviceInfo.platform, "Android");
  assert.equal(okDoc.deviceInfo.appPackage, "com.aistudio.jycomputers.mrxqkp");
  assert.equal(okDoc.deviceInfo.userAgent, "okhttp/4.12.0 JYComputers/1.0");

  // Confirm ZERO credential strings or tokens appear anywhere in persisted audit docs
  const serialized = JSON.stringify(persistedAuditDocs);
  for (const sensitive of [
    TEST_ADMIN_USER,
    TEST_ADMIN_PASS,
    "WrongAttempt#Password1",
    TEST_RECOVERY_KEY,
    TEST_SECRET,
    okJson.sessionToken,
    okJson.csrfToken,
    okJson.customToken,
    "leaked_password_attempt",
    "leaked_token_attempt",
    "leaked_secret",
    "scrypt$16384$fakehash",
  ]) {
    assert.ok(!serialized.includes(sensitive), `Sensitive string leaked into audit log: ${sensitive}`);
  }

  // Direct unit check on deep key stripper
  const scrubbed = stripSensitiveFieldsDeep({
    safe: "ok",
    nested: { password: "p", secret: "s", sessionToken: "t", keepMe: 42 },
  });
  assert.deepEqual(scrubbed, { safe: "ok", nested: { keepMe: 42 } });
});

test("TEST 12: Integration — admin-auth-server writes sanitized login audit entries directly to Firestore Emulator", async (t) => {
  const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
  const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
  if (!firestoreHost || !authHost) {
    t.skip("FIRESTORE_EMULATOR_HOST / FIREBASE_AUTH_EMULATOR_HOST not set");
    return;
  }

  const projectId = process.env.GCP_PROJECT || "demo-no-project";
  const databaseId = "ai-studio-android-jycomput-682cf32f-41f3-47bf-a6b2-6ad57154d3a5";

  // Clear emulator database before test
  await fetch(
    `http://${firestoreHost}/emulator/v1/projects/${projectId}/databases/${databaseId}/documents`,
    { method: "DELETE" }
  );

  const emuService = createAdminAuthService({
    adminUsername: TEST_ADMIN_USER,
    adminPasswordHash: hashSecret(TEST_ADMIN_PASS),
    adminRecoveryKeyHash: hashSecret(TEST_RECOVERY_KEY),
    sessionSecret: TEST_SECRET,
    firebaseProjectId: projectId,
    firestoreDatabaseId: databaseId,
  });
  const emuServer = emuService.createHttpServer();
  await new Promise((resolve) => emuServer.listen(0, "127.0.0.1", resolve));
  const emuBaseUrl = `http://127.0.0.1:${emuServer.address().port}`;

  try {
    const failed = await fetch(`${emuBaseUrl}/admin/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: "wrong_admin_probe",
        password: "WrongPassword#999",
        deviceInfo: { platform: "Android", model: "Pixel 8 Pro", sdkInt: 34 },
      }),
    });
    assert.equal(failed.status, 401);

    const succeeded = await fetch(`${emuBaseUrl}/admin/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: TEST_ADMIN_USER,
        password: TEST_ADMIN_PASS,
        deviceInfo: { platform: "Android", model: "Pixel 8 Pro", sdkInt: 34 },
      }),
    });
    assert.equal(succeeded.status, 200);
    const loginJson = await succeeded.json();

    // Verify persisted Firestore documents using owner privileges
    const listResp = await fetch(
      `http://${firestoreHost}/v1/projects/${projectId}/databases/${databaseId}/documents/admin_auth_audit`,
      { headers: { Authorization: "Bearer owner" } }
    );
    assert.equal(listResp.status, 200);
    const listData = await listResp.json();
    const docs = listData.documents || [];
    assert.equal(docs.length, 2);

    const eventTypes = docs.map((d) => d.fields.eventType.stringValue).sort();
    assert.deepEqual(eventTypes, ["ADMIN_LOGIN_FAILED", "ADMIN_LOGIN_SUCCESS"]);

    const serialized = JSON.stringify(docs);
    assert.ok(serialized.includes("Pixel 8 Pro"));
    assert.ok(!serialized.includes(TEST_ADMIN_USER));
    assert.ok(!serialized.includes(TEST_ADMIN_PASS));
    assert.ok(!serialized.includes("wrong_admin_probe"));
    assert.ok(!serialized.includes("WrongPassword#999"));
    assert.ok(!serialized.includes(loginJson.sessionToken));
    assert.ok(!serialized.includes(loginJson.customToken));
  } finally {
    await new Promise((resolve) => emuServer.close(resolve));
  }
});
