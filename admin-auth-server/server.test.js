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

beforeEach(async () => {
  currentNow = 1_700_000_000_000;
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
