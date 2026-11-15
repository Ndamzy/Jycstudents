"use strict";

const http = require("node:http");
const crypto = require("node:crypto");
const {
  SINGLE_ADMIN_UID,
  hashSecret,
  verifySecret,
  constantTimeEquals,
  validateStrongPassword,
  signSessionToken,
  verifySessionToken,
  parseCookies,
  createEmulatorCustomToken,
} = require("./crypto-utils");
const {
  DEFAULT_FIRESTORE_DATABASE_ID,
  sanitizeDeviceInfo,
  buildAuditDocument,
  writeAuditToFirestoreEmulator,
} = require("./audit-logger");

const DEFAULT_MAX_FAILED_ATTEMPTS = 5;
const DEFAULT_ATTEMPT_WINDOW_MS = 15 * 60 * 1000; // 15 minutes
const DEFAULT_LOCKOUT_MS = 15 * 60 * 1000; // 15 minutes
const DEFAULT_SESSION_TTL_MS = 8 * 60 * 60 * 1000; // 8 hours absolute lifetime
const DEFAULT_INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes inactivity
const MAX_BODY_BYTES = 16 * 1024;

function normalizeIp(req) {
  const fwd = req.headers["x-forwarded-for"];
  if (typeof fwd === "string" && fwd.trim()) {
    return fwd.split(",")[0].trim();
  }
  return req.socket?.remoteAddress || "unknown";
}

function sendJson(res, statusCode, payload, extraHeaders = {}) {
  const body = JSON.stringify(payload);
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    Pragma: "no-cache",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    ...extraHeaders,
  });
  res.end(body);
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(Object.assign(new Error("Request body too large."), { statusCode: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      if (chunks.length === 0) {
        resolve({});
        return;
      }
      try {
        const parsed = JSON.parse(Buffer.concat(chunks).toString("utf8"));
        resolve(parsed && typeof parsed === "object" ? parsed : {});
      } catch {
        reject(Object.assign(new Error("Invalid JSON payload."), { statusCode: 400 }));
      }
    });
    req.on("error", reject);
  });
}

function buildCookieHeader(name, value, maxAgeSeconds, { httpOnly = true, secure = true } = {}) {
  const parts = [`${name}=${encodeURIComponent(value)}`, "Path=/", "SameSite=Strict", `Max-Age=${maxAgeSeconds}`];
  if (httpOnly) parts.push("HttpOnly");
  if (secure) parts.push("Secure");
  return parts.join("; ");
}

function buildClearCookieHeader(name, { httpOnly = true, secure = true } = {}) {
  const parts = [`${name}=`, "Path=/", "SameSite=Strict", "Max-Age=0"];
  if (httpOnly) parts.push("HttpOnly");
  if (secure) parts.push("Secure");
  return parts.join("; ");
}

function resolveDefaultFirebaseBridge(logger, options = {}) {
  const databaseId =
    options.firestoreDatabaseId ||
    process.env.FIRESTORE_DATABASE_ID ||
    DEFAULT_FIRESTORE_DATABASE_ID;
  const projectId =
    options.firebaseProjectId ||
    process.env.GOOGLE_CLOUD_PROJECT ||
    process.env.GCLOUD_PROJECT ||
    process.env.GCP_PROJECT ||
    "demo-no-project";

  if (process.env.FIREBASE_AUTH_EMULATOR_HOST) {
    return {
      mode: "emulator",
      async createCustomToken(uid, claims) {
        return createEmulatorCustomToken(uid, claims);
      },
      async revokeRefreshTokens(_uid) {
        // Emulator revoke is enforced via server session store + sessionVersion claims.
      },
      async writeAuditLog(entry) {
        if (process.env.FIRESTORE_EMULATOR_HOST) {
          await writeAuditToFirestoreEmulator(entry, {
            emulatorHost: process.env.FIRESTORE_EMULATOR_HOST,
            projectId,
            databaseId,
          });
        }
      },
    };
  }

  let adminSdk;
  try {
    adminSdk = require("firebase-admin");
  } catch (err) {
    logger.warn?.("firebase-admin is unavailable outside emulator mode; server will fail closed until installed.");
    return null;
  }

  if (!adminSdk.apps.length) {
    adminSdk.initializeApp();
  }

  const { getFirestore, FieldValue } = require("firebase-admin/firestore");
  const firestoreDb = getFirestore(adminSdk.app(), databaseId);

  return {
    mode: "firebase-admin",
    async createCustomToken(uid, claims) {
      return adminSdk.auth().createCustomToken(uid, claims);
    },
    async revokeRefreshTokens(uid) {
      await adminSdk.auth().revokeRefreshTokens(uid);
    },
    async writeAuditLog(entry) {
      if (process.env.FIRESTORE_EMULATOR_HOST) {
        await writeAuditToFirestoreEmulator(entry, {
          emulatorHost: process.env.FIRESTORE_EMULATOR_HOST,
          projectId,
          databaseId,
        });
        return;
      }
      await firestoreDb.collection("admin_auth_audit").add({
        ...entry,
        createdAt: FieldValue.serverTimestamp(),
      });
    },
  };
}

/**
 * Creates the Single-Administrator Authentication request handler & server state.
 * Dependency injection via `options` enables deterministic automated testing while defaulting to strict production env config.
 */
function createAdminAuthService(options = {}) {
  const nowFn = options.nowFn || (() => Date.now());
  const logger = options.logger || console;

  const config = {
    username: options.adminUsername ?? process.env.ADMIN_USERNAME ?? "",
    passwordHash: options.adminPasswordHash ?? process.env.ADMIN_PASSWORD_HASH ?? "",
    recoveryKeyHash: options.adminRecoveryKeyHash ?? process.env.ADMIN_RECOVERY_KEY_HASH ?? "",
    sessionSecret: options.sessionSecret ?? process.env.ADMIN_SESSION_SECRET ?? "",
    maxFailedAttempts: options.maxFailedAttempts ?? Number(process.env.ADMIN_MAX_FAILED_ATTEMPTS || DEFAULT_MAX_FAILED_ATTEMPTS),
    attemptWindowMs: options.attemptWindowMs ?? Number(process.env.ADMIN_ATTEMPT_WINDOW_MS || DEFAULT_ATTEMPT_WINDOW_MS),
    lockoutMs: options.lockoutMs ?? Number(process.env.ADMIN_LOCKOUT_MS || DEFAULT_LOCKOUT_MS),
    sessionTtlMs: options.sessionTtlMs ?? Number(process.env.ADMIN_SESSION_TTL_MS || DEFAULT_SESSION_TTL_MS),
    inactivityTimeoutMs: options.inactivityTimeoutMs ?? Number(process.env.ADMIN_INACTIVITY_TIMEOUT_MS || DEFAULT_INACTIVITY_TIMEOUT_MS),
    secureCookies: options.secureCookies ?? process.env.ADMIN_SECURE_COOKIES !== "false",
  };

  const firebaseBridge =
    options.firebaseBridge !== undefined
      ? options.firebaseBridge
      : resolveDefaultFirebaseBridge(logger, options);

  let currentPasswordHash = config.passwordHash;
  let sessionVersion = Number(options.initialSessionVersion || 1);
  const activeSessions = new Map(); // sessionId -> { sessionId, csrfToken, sessionVersion, issuedAtMs, expiresAtMs, lastActivityAtMs, ip }
  const throttleMap = new Map(); // key -> { failures: number[], lockedUntilMs: number }
  const auditLogs = []; // server-side sanitized audit trail mirror

  function isServerConfigured() {
    return (
      typeof config.username === "string" &&
      config.username.trim().length > 0 &&
      typeof currentPasswordHash === "string" &&
      currentPasswordHash.startsWith("scrypt$") &&
      typeof config.sessionSecret === "string" &&
      config.sessionSecret.length >= 16 &&
      firebaseBridge != null
    );
  }

  async function recordAudit({ eventType, outcome, usernameMatched = false, details, ip, deviceInfo }) {
    const doc = buildAuditDocument({
      eventType,
      outcome,
      usernameMatched,
      reason: details,
      clientIp: ip,
      deviceInfo,
      nowMs: nowFn(),
    });
    const entry = {
      id: crypto.randomUUID(),
      timestamp: doc.timestampIso,
      details: doc.reason,
      ip: doc.clientIp,
      ...doc,
    };
    auditLogs.unshift(entry);
    if (auditLogs.length > 500) auditLogs.length = 500;

    if (typeof firebaseBridge?.writeAuditLog === "function") {
      try {
        await Promise.race([
          firebaseBridge.writeAuditLog(doc),
          new Promise((_, reject) => setTimeout(() => reject(new Error("Audit write timeout")), 2500)),
        ]);
      } catch (err) {
        logger.warn?.(`Failed to persist audit entry (${doc.eventType}): ${err?.message || "unknown error"}`);
      }
    }
  }

  function getThrottleKeys(ip) {
    return [`ip:${ip}`, "admin:global"];
  }

  function checkLockout(ip) {
    const now = nowFn();
    let maxRetryMs = 0;
    for (const key of getThrottleKeys(ip)) {
      const state = throttleMap.get(key);
      if (state && state.lockedUntilMs > now) {
        maxRetryMs = Math.max(maxRetryMs, state.lockedUntilMs - now);
      }
    }
    return maxRetryMs;
  }

  function recordFailedAttempt(ip) {
    const now = nowFn();
    let triggeredLock = false;
    for (const key of getThrottleKeys(ip)) {
      const state = throttleMap.get(key) || { failures: [], lockedUntilMs: 0 };
      state.failures = state.failures.filter((ts) => now - ts <= config.attemptWindowMs);
      state.failures.push(now);
      if (state.failures.length >= config.maxFailedAttempts) {
        state.lockedUntilMs = now + config.lockoutMs;
        triggeredLock = true;
      }
      throttleMap.set(key, state);
    }
    return triggeredLock;
  }

  function clearThrottle(ip) {
    for (const key of getThrottleKeys(ip)) {
      throttleMap.delete(key);
    }
  }

  function authenticateRequest(req, { requireCsrfForCookie = true } = {}) {
    if (!config.sessionSecret) {
      return { ok: false, statusCode: 503, error: "Administrator service is not configured." };
    }
    const now = nowFn();
    const authHeader = req.headers.authorization || "";
    const cookies = parseCookies(req.headers.cookie);

    let presentedToken = null;
    let viaCookie = false;
    if (authHeader.startsWith("Bearer ")) {
      presentedToken = authHeader.slice(7).trim();
    } else if (cookies.jy_admin_sid) {
      presentedToken = cookies.jy_admin_sid;
      viaCookie = true;
    }

    if (!presentedToken) {
      return { ok: false, statusCode: 401, error: "Administrator authentication required." };
    }

    const decoded = verifySessionToken(presentedToken, config.sessionSecret);
    if (!decoded || !decoded.sid) {
      return { ok: false, statusCode: 401, error: "Invalid or expired administrator session." };
    }

    const session = activeSessions.get(decoded.sid);
    if (!session) {
      return { ok: false, statusCode: 401, error: "Session has been revoked or expired." };
    }

    if (decoded.uid !== SINGLE_ADMIN_UID || session.sessionVersion !== sessionVersion || decoded.sv !== sessionVersion) {
      activeSessions.delete(decoded.sid);
      return { ok: false, statusCode: 401, error: "Session invalidated due to security state change." };
    }

    if (now > session.expiresAtMs) {
      activeSessions.delete(decoded.sid);
      return { ok: false, statusCode: 401, error: "Administrator session expired." };
    }

    if (now - session.lastActivityAtMs > config.inactivityTimeoutMs) {
      activeSessions.delete(decoded.sid);
      return { ok: false, statusCode: 401, error: "Administrator session expired due to inactivity." };
    }

    if (viaCookie && requireCsrfForCookie) {
      const headerCsrf = String(req.headers["x-csrf-token"] || "");
      const cookieCsrf = String(cookies.jy_admin_csrf || "");
      if (!headerCsrf || !cookieCsrf || !constantTimeEquals(headerCsrf, session.csrfToken) || !constantTimeEquals(cookieCsrf, session.csrfToken)) {
        return { ok: false, statusCode: 403, error: "CSRF validation failed." };
      }
    }

    return { ok: true, session };
  }

  async function handler(req, res) {
    const ip = normalizeIp(req);
    const urlPath = (req.url || "/").split("?")[0];

    try {
      // Health check (exposes ZERO credentials or config details)
      if (req.method === "GET" && urlPath === "/health") {
        return sendJson(res, 200, { ok: true, configured: isServerConfigured() });
      }

      // Explicitly block any admin registration/creation attempts
      if (urlPath === "/admin/register" || urlPath === "/admin/create" || urlPath === "/admin/signup" || urlPath === "/admin/grant") {
        await recordAudit({
          eventType: "BLOCKED_SECOND_ADMIN_CREATION",
          outcome: "blocked",
          details: `Blocked ${req.method} request to disabled admin provisioning route`,
          ip,
          deviceInfo: sanitizeDeviceInfo(null, req),
        });
        return sendJson(res, 403, { error: "Single-administrator policy: creating additional administrator accounts is permanently disabled." });
      }

      // 1. POST /admin/login
      if (req.method === "POST" && urlPath === "/admin/login") {
        if (!isServerConfigured()) {
          return sendJson(res, 503, { error: "Administrator authentication backend is not provisioned on the server." });
        }

        const body = await readJsonBody(req);
        const deviceInfo = sanitizeDeviceInfo(body.deviceInfo, req);

        const retryMs = checkLockout(ip);
        if (retryMs > 0) {
          const retrySec = Math.max(1, Math.ceil(retryMs / 1000));
          await recordAudit({
            eventType: "ADMIN_LOGIN_LOCKED_OUT",
            outcome: "locked_out",
            usernameMatched: false,
            details: "Rejected login attempt during active lockout window",
            ip,
            deviceInfo,
          });
          return sendJson(
            res,
            429,
            {
              error: `Too many failed administrator login attempts. Try again in ${Math.ceil(retrySec / 60)} minute(s).`,
              retryAfterSeconds: retrySec,
            },
            { "Retry-After": String(retrySec) }
          );
        }

        const submittedUsername = typeof body.username === "string" ? body.username.trim() : "";
        const submittedPassword = typeof body.password === "string" ? body.password : "";

        // Always execute BOTH comparisons and scrypt work to eliminate timing/enumeration leaks
        const usernameMatches = constantTimeEquals(submittedUsername, config.username);
        const passwordMatches = verifySecret(submittedPassword, currentPasswordHash);

        if (!usernameMatches || !passwordMatches) {
          const lockedNow = recordFailedAttempt(ip);
          await recordAudit({
            eventType: lockedNow ? "ADMIN_LOGIN_LOCKED_OUT" : "ADMIN_LOGIN_FAILED",
            outcome: lockedNow ? "locked_out" : "failed",
            usernameMatched: usernameMatches,
            details: lockedNow ? "Invalid credentials (lockout triggered)" : "Invalid administrator credentials",
            ip,
            deviceInfo,
          });
          if (lockedNow) {
            const retrySec = Math.max(1, Math.ceil(config.lockoutMs / 1000));
            return sendJson(
              res,
              429,
              {
                error: "Too many failed login attempts. Administrator login is temporarily locked.",
                retryAfterSeconds: retrySec,
              },
              { "Retry-After": String(retrySec) }
            );
          }
          return sendJson(res, 401, { error: "Invalid administrator credentials." });
        }

        clearThrottle(ip);

        // Session fixation prevention: ALWAYS mint fresh 256-bit sessionId & CSRF token, ignoring any old cookie
        const now = nowFn();
        const sessionId = crypto.randomBytes(32).toString("hex");
        const csrfToken = crypto.randomBytes(32).toString("base64url");
        const expiresAtMs = now + config.sessionTtlMs;

        const sessionRecord = {
          sessionId,
          csrfToken,
          sessionVersion,
          issuedAtMs: now,
          expiresAtMs,
          lastActivityAtMs: now,
          ip,
        };
        activeSessions.set(sessionId, sessionRecord);

        const sessionToken = signSessionToken(
          { sid: sessionId, uid: SINGLE_ADMIN_UID, sv: sessionVersion, exp: expiresAtMs },
          config.sessionSecret
        );

        const customToken = await firebaseBridge.createCustomToken(SINGLE_ADMIN_UID, {
          jySingleAdmin: true,
          role: "admin",
          sessionVersion,
        });

        await recordAudit({
          eventType: "ADMIN_LOGIN_SUCCESS",
          outcome: "success",
          usernameMatched: true,
          details: "Single owner administrator authenticated",
          ip,
          deviceInfo,
        });

        const maxAgeSec = Math.max(1, Math.floor(config.sessionTtlMs / 1000));
        return sendJson(
          res,
          200,
          {
            uid: SINGLE_ADMIN_UID,
            role: "admin",
            customToken,
            sessionToken,
            csrfToken,
            expiresAtMs,
            inactivityTimeoutMs: config.inactivityTimeoutMs,
            sessionVersion,
          },
          {
            "Set-Cookie": [
              buildCookieHeader("jy_admin_sid", sessionToken, maxAgeSec, {
                httpOnly: true,
                secure: config.secureCookies,
              }),
              buildCookieHeader("jy_admin_csrf", csrfToken, maxAgeSec, {
                httpOnly: false,
                secure: config.secureCookies,
              }),
            ],
          }
        );
      }

      // 2. POST /admin/refresh
      if (req.method === "POST" && urlPath === "/admin/refresh") {
        const authCheck = authenticateRequest(req, { requireCsrfForCookie: true });
        if (!authCheck.ok) {
          return sendJson(res, authCheck.statusCode, { error: authCheck.error });
        }
        const now = nowFn();
        authCheck.session.lastActivityAtMs = now;
        const customToken = await firebaseBridge.createCustomToken(SINGLE_ADMIN_UID, {
          jySingleAdmin: true,
          role: "admin",
          sessionVersion,
        });
        return sendJson(res, 200, {
          uid: SINGLE_ADMIN_UID,
          role: "admin",
          customToken,
          expiresAtMs: authCheck.session.expiresAtMs,
          inactivityTimeoutMs: config.inactivityTimeoutMs,
          sessionVersion,
        });
      }

      // 3. GET /admin/session (admin-only verification endpoint)
      if (req.method === "GET" && urlPath === "/admin/session") {
        const authCheck = authenticateRequest(req, { requireCsrfForCookie: false });
        if (!authCheck.ok) {
          return sendJson(res, authCheck.statusCode, { error: authCheck.error });
        }
        return sendJson(res, 200, {
          authenticated: true,
          uid: SINGLE_ADMIN_UID,
          role: "admin",
          expiresAtMs: authCheck.session.expiresAtMs,
          sessionVersion,
        });
      }

      // 4. GET /admin/audit-logs (protected audit log viewer)
      if (req.method === "GET" && urlPath === "/admin/audit-logs") {
        const authCheck = authenticateRequest(req, { requireCsrfForCookie: false });
        if (!authCheck.ok) {
          return sendJson(res, authCheck.statusCode, { error: authCheck.error });
        }
        return sendJson(res, 200, { logs: auditLogs.slice(0, 100) });
      }

      // 5. POST /admin/logout
      if (req.method === "POST" && urlPath === "/admin/logout") {
        const authCheck = authenticateRequest(req, { requireCsrfForCookie: true });
        if (!authCheck.ok) {
          return sendJson(res, authCheck.statusCode, { error: authCheck.error });
        }
        const body = await readJsonBody(req).catch(() => ({}));
        const deviceInfo = sanitizeDeviceInfo(body.deviceInfo, req);
        activeSessions.delete(authCheck.session.sessionId);
        await firebaseBridge.revokeRefreshTokens(SINGLE_ADMIN_UID).catch(() => {});
        await recordAudit({
          eventType: "ADMIN_LOGOUT",
          outcome: "success",
          usernameMatched: true,
          details: "Administrator signed out and session invalidated",
          ip,
          deviceInfo,
        });
        return sendJson(
          res,
          200,
          { loggedOut: true },
          {
            "Set-Cookie": [
              buildClearCookieHeader("jy_admin_sid", { httpOnly: true, secure: config.secureCookies }),
              buildClearCookieHeader("jy_admin_csrf", { httpOnly: false, secure: config.secureCookies }),
            ],
          }
        );
      }

      // 6. POST /admin/recover (Owner-only password reset using private recovery key)
      if (req.method === "POST" && urlPath === "/admin/recover") {
        if (!isServerConfigured() || !config.recoveryKeyHash || !config.recoveryKeyHash.startsWith("scrypt$")) {
          return sendJson(res, 503, { error: "Owner recovery key is not configured on the server." });
        }

        const body = await readJsonBody(req);
        const deviceInfo = sanitizeDeviceInfo(body.deviceInfo, req);

        const retryMs = checkLockout(ip);
        if (retryMs > 0) {
          const retrySec = Math.max(1, Math.ceil(retryMs / 1000));
          return sendJson(
            res,
            429,
            { error: "Recovery temporarily locked due to repeated failed attempts.", retryAfterSeconds: retrySec },
            { "Retry-After": String(retrySec) }
          );
        }

        const submittedUsername = typeof body.username === "string" ? body.username.trim() : "";
        const submittedRecoveryKey = typeof body.recoveryKey === "string" ? body.recoveryKey.trim() : "";
        const newPassword = typeof body.newPassword === "string" ? body.newPassword : "";

        const userOk = constantTimeEquals(submittedUsername, config.username);
        const keyOk = verifySecret(submittedRecoveryKey, config.recoveryKeyHash);

        if (!userOk || !keyOk) {
          recordFailedAttempt(ip);
          await recordAudit({
            eventType: "ADMIN_RECOVERY_FAILED",
            outcome: "failed",
            usernameMatched: userOk,
            details: "Invalid owner recovery credentials",
            ip,
            deviceInfo,
          });
          return sendJson(res, 401, { error: "Invalid administrator recovery credentials." });
        }

        const pwErr = validateStrongPassword(newPassword);
        if (pwErr) {
          return sendJson(res, 400, { error: pwErr });
        }

        currentPasswordHash = hashSecret(newPassword);
        sessionVersion += 1;
        activeSessions.clear(); // Immediately invalidate ALL existing admin sessions
        clearThrottle(ip);
        await firebaseBridge.revokeRefreshTokens(SINGLE_ADMIN_UID).catch(() => {});
        await recordAudit({
          eventType: "ADMIN_PASSWORD_RECOVERED",
          outcome: "success",
          usernameMatched: true,
          details: "Password rotated via owner recovery key; all active sessions invalidated",
          ip,
          deviceInfo,
        });

        return sendJson(
          res,
          200,
          {
            recovered: true,
            message: "Administrator password updated and all previous sessions invalidated. Sign in with your new password.",
            sessionVersion,
          },
          {
            "Set-Cookie": [
              buildClearCookieHeader("jy_admin_sid", { httpOnly: true, secure: config.secureCookies }),
              buildClearCookieHeader("jy_admin_csrf", { httpOnly: false, secure: config.secureCookies }),
            ],
          }
        );
      }

      // Deny-by-default for any unrecognized endpoint
      return sendJson(res, 404, { error: "Endpoint not found." });
    } catch (err) {
      const code = err?.statusCode || 500;
      return sendJson(res, code, { error: code === 500 ? "Internal authentication server error." : err.message });
    }
  }

  return {
    handler,
    createHttpServer: () => http.createServer(handler),
    getAuditLogs: () => [...auditLogs],
    getActiveSessionCount: () => activeSessions.size,
  };
}

if (require.main === module) {
  const port = Number(process.env.PORT || 8080);
  const service = createAdminAuthService();
  const server = service.createHttpServer();
  server.listen(port, "0.0.0.0", () => {
    console.log(`JY-COMPUTERS Single-Admin Auth Server listening on port ${port}`);
  });
}

module.exports = { createAdminAuthService };
