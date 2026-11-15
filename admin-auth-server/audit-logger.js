"use strict";

const DEFAULT_FIRESTORE_DATABASE_ID =
  "ai-studio-android-jycomput-682cf32f-41f3-47bf-a6b2-6ad57154d3a5";

const SAFE_DEVICE_STRING_FIELDS = [
  "platform",
  "manufacturer",
  "brand",
  "model",
  "device",
  "osVersion",
  "appVersion",
  "appPackage",
];

const FORBIDDEN_KEY_PATTERN =
  /^(password|pass|passwd|pwd|secret|token|customtoken|sessiontoken|csrftoken|recoverykey|hash|salt|cookie|authorization|username|credential|credentials|apikey)$/i;

function sanitizeScalarString(value, maxLen = 120) {
  if (typeof value !== "string") return "";
  const cleaned = value.replace(/[\r\n\t\u0000-\u001f]+/g, " ").trim().slice(0, maxLen);
  if (!cleaned) return "";
  // Scrub accidental token/hash material if submitted inside device metadata
  if (/^scrypt\$|^bearer\s+|^eyJ[A-Za-z0-9_-]+\./i.test(cleaned)) {
    return "[REDACTED]";
  }
  return cleaned;
}

function sanitizeDeviceInfo(rawDeviceInfo, req) {
  const input =
    rawDeviceInfo && typeof rawDeviceInfo === "object" && !Array.isArray(rawDeviceInfo)
      ? rawDeviceInfo
      : {};

  const safe = {};
  for (const field of SAFE_DEVICE_STRING_FIELDS) {
    const val = sanitizeScalarString(input[field], 120);
    if (val) safe[field] = val;
  }

  if (Number.isInteger(input.sdkInt) && input.sdkInt >= 1 && input.sdkInt <= 1000) {
    safe.sdkInt = input.sdkInt;
  }

  const uaHeader = req?.headers?.["user-agent"];
  const userAgent = sanitizeScalarString(
    typeof uaHeader === "string" ? uaHeader : input.userAgent,
    200
  );
  if (userAgent) {
    safe.userAgent = userAgent;
  }

  if (!safe.platform) {
    safe.platform = "unknown";
  }

  return safe;
}

function stripSensitiveFieldsDeep(value) {
  if (value === null || value === undefined) return value;
  if (Array.isArray(value)) {
    return value.map(stripSensitiveFieldsDeep);
  }
  if (typeof value === "object") {
    const out = {};
    for (const [k, v] of Object.entries(value)) {
      if (FORBIDDEN_KEY_PATTERN.test(k)) {
        continue;
      }
      out[k] = stripSensitiveFieldsDeep(v);
    }
    return out;
  }
  return value;
}

function buildAuditDocument({
  eventType,
  outcome,
  usernameMatched = false,
  reason,
  clientIp,
  deviceInfo,
  nowMs = Date.now(),
}) {
  const doc = {
    eventType: sanitizeScalarString(String(eventType || "UNKNOWN_EVENT"), 64),
    outcome: sanitizeScalarString(String(outcome || "unknown"), 32),
    targetUid: "jy_single_owner_admin",
    usernameMatched: Boolean(usernameMatched),
    reason: sanitizeScalarString(String(reason || ""), 240),
    clientIp: sanitizeScalarString(String(clientIp || "unknown"), 64) || "unknown",
    deviceInfo: stripSensitiveFieldsDeep(deviceInfo || { platform: "unknown" }),
    timestampIso: new Date(nowMs).toISOString(),
  };
  return stripSensitiveFieldsDeep(doc);
}

function toFirestoreRestValue(val) {
  if (val === null || val === undefined) return { nullValue: null };
  if (typeof val === "boolean") return { booleanValue: val };
  if (typeof val === "number") {
    if (Number.isInteger(val)) return { integerValue: String(val) };
    return { doubleValue: val };
  }
  if (typeof val === "string") return { stringValue: val };
  if (val instanceof Date) return { timestampValue: val.toISOString() };
  if (Array.isArray(val)) {
    return { arrayValue: { values: val.map(toFirestoreRestValue) } };
  }
  if (typeof val === "object") {
    const fields = {};
    for (const [k, v] of Object.entries(val)) {
      if (v !== undefined) {
        fields[k] = toFirestoreRestValue(v);
      }
    }
    return { mapValue: { fields } };
  }
  return { stringValue: String(val) };
}

async function writeAuditToFirestoreEmulator(entry, { emulatorHost, projectId, databaseId }) {
  const host = String(emulatorHost || "").replace(/^https?:\/\//, "").trim();
  if (!host) return false;
  const url = `http://${host}/v1/projects/${encodeURIComponent(projectId)}/databases/${encodeURIComponent(databaseId)}/documents/admin_auth_audit`;

  const fields = {};
  for (const [k, v] of Object.entries(entry)) {
    if (v !== undefined) {
      fields[k] = toFirestoreRestValue(v);
    }
  }
  fields.createdAt = { timestampValue: entry.timestampIso || new Date().toISOString() };

  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: "Bearer owner",
    },
    body: JSON.stringify({ fields }),
  });

  if (!resp.ok) {
    const errText = await resp.text().catch(() => "");
    throw new Error(`Firestore emulator audit write failed (${resp.status}): ${errText}`);
  }
  return true;
}

module.exports = {
  DEFAULT_FIRESTORE_DATABASE_ID,
  sanitizeDeviceInfo,
  stripSensitiveFieldsDeep,
  buildAuditDocument,
  writeAuditToFirestoreEmulator,
};
