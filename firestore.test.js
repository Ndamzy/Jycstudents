const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";
const INSTRUCTOR_UID = "instr_1";
const OTHER_INSTRUCTOR_UID = "instr_2";
const OWNER_ADMIN_UID = "msUb7EcbUye0HyBqCNMe7FCIKvA3";
const OWNER_ADMIN_EMAIL = "joshua.dangana@gmail.com";
const SINGLE_ADMIN_CLAIMS = {
  email: OWNER_ADMIN_EMAIL,
  email_verified: true,
  jySingleAdmin: true,
  role: "admin",
  firebase: { sign_in_provider: "google.com" },
};

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validStudentProfile(uid) {
  return {
    userId: uid,
    role: "student",
    studentIdNumber: "JYC-2026-1001",
    fullName: "Alice Johnson",
    gender: "Female",
    dateOfBirth: "2002-05-14",
    phoneNumber: "08012345678",
    email: "alice@example.com",
    residentialAddress: "12 Tech Avenue, Abuja",
    stateOfOrigin: "FCT Abuja",
    localGovernmentArea: "AMAC",
    nextOfKin: "Mary Johnson",
    nextOfKinPhone: "08087654321",
    passportPhotoBase64: "",
    selectedProgrammeId: "prog_coding",
    selectedProgrammeTitle: "Coding Programme",
    trainingLevel: "Beginner",
    registrationDate: "2026-11-13",
    approvalStatus: "pending",
    paymentStatus: "unpaid",
    courseProgressPercent: 0,
    status: "active",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read any protected collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("programmes").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("enrollments").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("payments").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("announcements").get());
  await assertFails(unauthDb.collection("activity_logs").get());
});

test("Student registration & PII isolation: Alice creates & updates own profile + directory; Bob cannot read Alice's PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validStudentProfile(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("directory").doc(ALICE_UID).set({
    userId: ALICE_UID,
    fullName: "Alice Johnson",
    studentIdNumber: "JYC-2026-1001",
    role: "student",
    selectedProgrammeTitle: "Coding Programme",
    isOnline: true,
    lastSeenAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));

  const batch = aliceDb.batch();
  batch.update(aliceDb.collection("users").doc(ALICE_UID), {
    localGovernmentArea: "Bwari",
    trainingLevel: "Intermediate",
    updatedAt: serverTimestamp(),
  });
  batch.update(aliceDb.collection("directory").doc(ALICE_UID), {
    fullName: "Alice Johnson",
    isOnline: true,
    lastSeenAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(batch.commit());

  // Bob cannot read Alice's private PII profile
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Privilege escalation blocked: student cannot self-assign admin role, approvalStatus, or ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({ ...validStudentProfile(ALICE_UID), role: "admin" }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({ ...validStudentProfile(ALICE_UID), approvalStatus: "approved" }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({ ...validStudentProfile(ALICE_UID), paymentStatus: "paid" }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({ ...validStudentProfile(ALICE_UID), courseProgressPercent: 50 }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({ ...validStudentProfile(ALICE_UID), isVerified: true }));
});

test("Option A Owner Google Auth spoofing checks: rejects missing claims, unverified email, non-Google provider, and other Google accounts", async () => {
  const ownerNoClaimsDb = testEnv.authenticatedContext("google_uid_no_claims", {
    email: OWNER_ADMIN_EMAIL,
    email_verified: true,
    firebase: { sign_in_provider: "google.com" },
  }).firestore();
  const ownerUnverifiedDb = testEnv.authenticatedContext("google_uid_unverified", {
    email: OWNER_ADMIN_EMAIL,
    email_verified: false,
    jySingleAdmin: true,
    role: "admin",
    firebase: { sign_in_provider: "google.com" },
  }).firestore();
  const ownerPasswordProviderDb = testEnv.authenticatedContext("owner_password_uid", {
    email: OWNER_ADMIN_EMAIL,
    email_verified: true,
    jySingleAdmin: true,
    role: "admin",
    firebase: { sign_in_provider: "password" },
  }).firestore();
  const spoofOtherEmailClaimsDb = testEnv.authenticatedContext("other_uid_with_claims", {
    email: "attacker@gmail.com",
    email_verified: true,
    jySingleAdmin: true,
    role: "admin",
    firebase: { sign_in_provider: "google.com" },
  }).firestore();
  const legacyAdminEmailDb = testEnv.authenticatedContext("legacy_admin_uid", {
    email: "admin@jy-computers.com",
    email_verified: true,
    jySingleAdmin: true,
    role: "admin",
    firebase: { sign_in_provider: "password" },
  }).firestore();
  const wrongUidWithOwnerClaimsDb = testEnv.authenticatedContext("wrong_uid_owner_spoof", {
    email: OWNER_ADMIN_EMAIL,
    email_verified: true,
    jySingleAdmin: true,
    role: "admin",
    firebase: { sign_in_provider: "google.com" },
  }).firestore();

  const progData = {
    title: "Hacked Course",
    description: "Spoofed",
    duration: "1 Month",
    feeNaira: 1000,
    category: "Foundation",
    level: "Beginner",
    active: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  for (const deniedDb of [
    ownerNoClaimsDb,
    ownerUnverifiedDb,
    ownerPasswordProviderDb,
    spoofOtherEmailClaimsDb,
    legacyAdminEmailDb,
    wrongUidWithOwnerClaimsDb,
  ]) {
    await assertFails(deniedDb.collection("programmes").doc("prog_spoof").set(progData));
    await assertFails(deniedDb.collection("activity_logs").get());
  }
});

test("Single Owner Google Admin (joshua.dangana@gmail.com + claims) can manage programmes and append activity_logs", async () => {
  const adminDb = testEnv.authenticatedContext(OWNER_ADMIN_UID, SINGLE_ADMIN_CLAIMS).firestore();

  await assertSucceeds(
    adminDb.collection("programmes").doc("basic_computer").set({
      title: "Basic Computer Course",
      description: "Foundation computing skills",
      duration: "1 Month",
      feeNaira: 25000,
      category: "Foundation",
      level: "Beginner",
      instructorUid: "",
      instructorName: "Staff Instructor",
      scheduleText: "Mon - Wed • 9:00 AM",
      active: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    adminDb.collection("activity_logs").doc("log_1").set({
      actorUid: OWNER_ADMIN_UID,
      actorName: "Joshua Dangana",
      action: "CREATE_COURSE",
      targetId: "basic_computer",
      details: "Created Basic Computer Course",
      createdAt: serverTimestamp(),
    })
  );
  // Activity logs are append-only (update/delete forbidden even for admin)
  await assertFails(adminDb.collection("activity_logs").doc("log_1").update({ action: "TAMPERED" }));
  await assertFails(adminDb.collection("activity_logs").doc("log_1").delete());

  // Student cannot read or write activity_logs
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("activity_logs").get());
  await assertFails(
    aliceDb.collection("activity_logs").doc("log_2").set({
      actorUid: ALICE_UID,
      actorName: "Alice",
      action: "SPOOF",
      targetId: "",
      details: "Should fail",
      createdAt: serverTimestamp(),
    })
  );
});

test("Single-Administrator Isolation: cannot create 2nd admin, escalate student role to admin, or read/write server-only security collections", async () => {
  const adminDb = testEnv.authenticatedContext(OWNER_ADMIN_UID, SINGLE_ADMIN_CLAIMS).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Seed Alice profile
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validStudentProfile(ALICE_UID)));

  // Owner Admin CAN write own /admins/{OWNER_ADMIN_UID} directory doc
  await assertSucceeds(
    adminDb.collection("admins").doc(OWNER_ADMIN_UID).set({
      uid: OWNER_ADMIN_UID,
      email: OWNER_ADMIN_EMAIL,
      fullName: "Joshua Dangana",
      createdAt: serverTimestamp(),
    })
  );

  // Attempting to create a second admin doc in /admins/{otherUid} is strictly rejected even by Admin
  await assertFails(
    adminDb.collection("admins").doc("second_admin_uid").set({
      uid: "second_admin_uid",
      email: "second@example.com",
      fullName: "Second Admin",
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      fullName: "Alice",
      createdAt: serverTimestamp(),
    })
  );

  // Escalating a student account's role to 'admin' is rejected even for Admin & Student
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ role: "admin", updatedAt: serverTimestamp() }));
  await assertFails(adminDb.collection("users").doc(ALICE_UID).update({ role: "admin", updatedAt: serverTimestamp() }));
  await assertFails(adminDb.collection("users").doc(ALICE_UID).set({ ...validStudentProfile(ALICE_UID), role: "admin" }));

  // Instructor role update by admin IS allowed for student
  await assertSucceeds(adminDb.collection("users").doc(ALICE_UID).update({ role: "instructor", updatedAt: serverTimestamp() }));

  // Client access to server-only collections is completely denied
  await assertFails(adminDb.collection("admin_security").doc("credentials").get());
  await assertFails(adminDb.collection("admin_sessions").get());
  await assertFails(adminDb.collection("admin_login_throttle").get());
  await assertFails(adminDb.collection("admin_auth_audit").get());
});

test("Write-only Admin Auth Audit Log (/admin_auth_audit): denies ALL client reads, creates, updates, and deletes (including Single Owner Admin)", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admin_auth_audit").doc("audit_seed_1").set({
      eventType: "ADMIN_LOGIN_SUCCESS",
      outcome: "success",
      targetUid: OWNER_ADMIN_UID,
      usernameMatched: true,
      reason: "Seeded by server Admin SDK",
      clientIp: "127.0.0.1",
      deviceInfo: { platform: "Android", model: "Pixel 8" },
      timestampIso: "2026-11-15T12:00:00.000Z",
      createdAt: serverTimestamp(),
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = testEnv.authenticatedContext(OWNER_ADMIN_UID, SINGLE_ADMIN_CLAIMS).firestore();

  const forgedEntry = {
    eventType: "ADMIN_LOGIN_SUCCESS",
    outcome: "success",
    targetUid: OWNER_ADMIN_UID,
    usernameMatched: true,
    reason: "Client-forged audit attempt",
    clientIp: "0.0.0.0",
    deviceInfo: { platform: "Android" },
    timestampIso: "2026-11-15T12:05:00.000Z",
    createdAt: serverTimestamp(),
  };

  for (const db of [unauthDb, aliceDb, adminDb]) {
    await assertFails(db.collection("admin_auth_audit").doc("audit_seed_1").get());
    await assertFails(db.collection("admin_auth_audit").get());
    await assertFails(db.collection("admin_auth_audit").doc("forged_1").set(forgedEntry));
    await assertFails(db.collection("admin_auth_audit").doc("audit_seed_1").update({ outcome: "tampered" }));
    await assertFails(db.collection("admin_auth_audit").doc("audit_seed_1").delete());
  }
});

test("Payments: student can create pending OPay transfer, cannot create cash/approved payment, cannot read Bob's payments", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const validPayment = {
    userId: ALICE_UID,
    studentName: "Alice Johnson",
    studentIdNumber: "JYC-2026-1001",
    enrollmentId: "enr_1",
    programmeId: "prog_coding",
    programmeTitle: "Coding Programme",
    amountNaira: 450000,
    method: "opay_transfer",
    reference: "OPAY-99887766",
    paymentDate: "2026-11-13",
    senderName: "Alice Johnson",
    evidenceBase64: "",
    status: "pending",
    adminRemarks: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  await assertSucceeds(aliceDb.collection("payments").doc("pay_1").set(validPayment));
  // Cannot self-approve or create cash payment as student
  await assertFails(aliceDb.collection("payments").doc("pay_2").set({ ...validPayment, status: "approved" }));
  await assertFails(aliceDb.collection("payments").doc("pay_3").set({ ...validPayment, method: "cash" }));
  // Cannot update status after creation
  await assertFails(aliceDb.collection("payments").doc("pay_1").update({ status: "approved" }));
  // Bob cannot read Alice's payment
  await assertFails(bobDb.collection("payments").doc("pay_1").get());
  // Alice can list her own payments with matching query
  await assertSucceeds(aliceDb.collection("payments").where("userId", "==", ALICE_UID).get());
  // Alice cannot list all payments without userId filter
  await assertFails(aliceDb.collection("payments").get());
});

test("Academic Results: student can only read own PUBLISHED results", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("results").doc("res_unpub").set({
      studentUid: ALICE_UID,
      studentName: "Alice Johnson",
      studentIdNumber: "JYC-2026-1001",
      programmeId: "prog_coding",
      programmeTitle: "Coding Programme",
      instructorUid: INSTRUCTOR_UID,
      session: "2026/2027",
      assignmentScore: 30,
      examScore: 55,
      totalScore: 85,
      grade: "A",
      remarks: "Draft",
      published: false,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    await ctx.firestore().collection("results").doc("res_pub").set({
      studentUid: ALICE_UID,
      studentName: "Alice Johnson",
      studentIdNumber: "JYC-2026-1001",
      programmeId: "prog_coding",
      programmeTitle: "Coding Programme",
      instructorUid: INSTRUCTOR_UID,
      session: "2026/2027",
      assignmentScore: 35,
      examScore: 55,
      totalScore: 90,
      grade: "A",
      remarks: "Excellent!",
      published: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertFails(aliceDb.collection("results").doc("res_unpub").get());
  await assertSucceeds(aliceDb.collection("results").doc("res_pub").get());
  await assertSucceeds(
    aliceDb.collection("results").where("studentUid", "==", ALICE_UID).where("published", "==", true).get()
  );
  await assertFails(bobDb.collection("results").doc("res_pub").get());
});

test("Chat Room & Messages: direct room isolated to members; public group room accessible", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext("charlie_uid").firestore();

  await assertSucceeds(
    aliceDb.collection("chat_rooms").doc("dm_alice_bob").set({
      roomType: "direct",
      title: "Alice & Bob",
      programmeId: "",
      memberUids: [ALICE_UID, BOB_UID],
      isGroupPublic: false,
      typingUids: [],
      lastMessage: "",
      lastSenderName: "",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("chat_rooms").doc("dm_alice_bob").collection("messages").doc("msg_1").set({
      roomId: "dm_alice_bob",
      senderUid: ALICE_UID,
      senderName: "Alice Johnson",
      text: "Hello Bob!",
      imageBase64: "",
      attachmentName: "",
      memberUids: [ALICE_UID, BOB_UID],
      isGroupPublic: false,
      readBy: [ALICE_UID],
      reported: false,
      deleted: false,
      createdAt: serverTimestamp(),
    })
  );

  // Bob can query messages with matching filter
  await assertSucceeds(
    bobDb.collection("chat_rooms").doc("dm_alice_bob").collection("messages")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  // Outsider cannot read DM room or messages
  await assertFails(outsiderDb.collection("chat_rooms").doc("dm_alice_bob").get());
  await assertFails(outsiderDb.collection("chat_rooms").doc("dm_alice_bob").collection("messages").get());
});

test("Services & Service Requests: signed-in read, admin manages services, customer creates unpaid pending request", async () => {
  const adminDb = testEnv.authenticatedContext(OWNER_ADMIN_UID, SINGLE_ADMIN_CLAIMS).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const validService = {
    title: "NYSC Registration Assistance",
    category: "NYSC & Academic Portal",
    description: "NYSC mobilization, biometric guidance, and portal registration.",
    defaultFeeNaira: 5000,
    paymentEnabled: true,
    allowPartialPayment: false,
    isTrainingCourse: false,
    linkedProgrammeId: "",
    active: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  // Non-admin cannot create/update service
  await assertFails(aliceDb.collection("services").doc("nysc_reg").set(validService));
  // Admin can create and toggle paymentEnabled
  await assertSucceeds(adminDb.collection("services").doc("nysc_reg").set(validService));
  await assertSucceeds(adminDb.collection("services").doc("nysc_reg").update({
    paymentEnabled: false,
    updatedAt: serverTimestamp(),
  }));
  // Signed-in user can read services
  await assertSucceeds(aliceDb.collection("services").get());

  const validRequest = {
    userId: ALICE_UID,
    customerName: "Alice Johnson",
    customerPhone: "08156879310",
    customerEmail: "alice@example.com",
    customerRegNumber: "JYC-2026-1001",
    requestNumber: "SRV-2026-1001",
    invoiceNumber: "INV-2026-1001",
    serviceId: "nysc_reg",
    serviceTitle: "NYSC Registration Assistance",
    serviceCategory: "NYSC & Academic Portal",
    details: "NYSC Batch B registration",
    totalFeeNaira: 5000,
    amountPaidNaira: 0,
    paymentStatus: "unpaid",
    status: "pending",
    adminNotes: "",
    invoiceDate: "2026-11-14",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  // Alice can create pending unpaid service request
  await assertSucceeds(aliceDb.collection("service_requests").doc("req_1").set(validRequest));
  // Alice cannot spoof pre-paid service request
  await assertFails(aliceDb.collection("service_requests").doc("req_bad1").set({
    ...validRequest,
    paymentStatus: "paid",
    amountPaidNaira: 5000,
  }));
  // Alice cannot update her own request status or amountPaidNaira
  await assertFails(aliceDb.collection("service_requests").doc("req_1").update({
    paymentStatus: "paid",
    amountPaidNaira: 5000,
    updatedAt: serverTimestamp(),
  }));
  // Bob cannot read Alice's service request or list without userId filter
  await assertFails(bobDb.collection("service_requests").doc("req_1").get());
  await assertFails(bobDb.collection("service_requests").get());
  await assertSucceeds(aliceDb.collection("service_requests").where("userId", "==", ALICE_UID).get());
  // Admin can update request status
  await assertSucceeds(adminDb.collection("service_requests").doc("req_1").update({
    status: "in_progress",
    updatedAt: serverTimestamp(),
  }));
});

test("Universal Service Payment & Payment References: serviceRequestId allowed, reference registry immutable & admin-only", async () => {
  const adminDb = testEnv.authenticatedContext(OWNER_ADMIN_UID, SINGLE_ADMIN_CLAIMS).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  const servicePayment = {
    userId: ALICE_UID,
    studentName: "Alice Johnson",
    studentIdNumber: "JYC-2026-1001",
    enrollmentId: "",
    serviceRequestId: "req_1",
    requestNumber: "SRV-2026-1001",
    invoiceNumber: "INV-2026-1001",
    programmeId: "nysc_reg",
    programmeTitle: "NYSC Registration Assistance",
    serviceCategory: "NYSC & Academic Portal",
    amountNaira: 5000,
    method: "bank_transfer",
    reference: "GTB-77665544",
    referenceNormalized: "GTB-77665544",
    paymentDate: "2026-11-14",
    senderName: "Alice Johnson",
    senderBank: "Guaranty Trust Bank (GTBank)",
    evidenceBase64: "",
    status: "pending",
    adminRemarks: "",
    verifiedByUid: "",
    verifiedByName: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  // Alice can submit bank_transfer for a serviceRequestId even with empty enrollmentId
  await assertSucceeds(aliceDb.collection("payments").doc("pay_srv_1").set(servicePayment));
  // Missing both enrollmentId and serviceRequestId must fail
  await assertFails(aliceDb.collection("payments").doc("pay_srv_empty").set({
    ...servicePayment,
    serviceRequestId: "",
  }));
  // Student cannot write directly to payment_references
  await assertFails(aliceDb.collection("payment_references").doc("GTB-77665544").set({
    referenceNormalized: "GTB-77665544",
    paymentId: "pay_srv_1",
    createdAt: serverTimestamp(),
  }));
  // Admin can create immutable payment_references entry, cannot update or delete it
  await assertSucceeds(adminDb.collection("payment_references").doc("GTB-77665544").set({
    referenceNormalized: "GTB-77665544",
    paymentId: "pay_srv_1",
    createdAt: serverTimestamp(),
  }));
  await assertFails(adminDb.collection("payment_references").doc("GTB-77665544").update({ paymentId: "tampered" }));
  await assertFails(adminDb.collection("payment_references").doc("GTB-77665544").delete());
  // Signed-in user can check existence of a single reference doc
  await assertSucceeds(aliceDb.collection("payment_references").doc("GTB-77665544").get());
  // Non-admin cannot list all payment_references
  await assertFails(aliceDb.collection("payment_references").get());
});

test("Chat rooms: non-admin can check existence of general lounge and DM rooms without Null value error", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Checking non-existent lounge & DM doc must succeed (not throw Null value error)
  await assertSucceeds(aliceDb.collection("chat_rooms").doc("general_student_lounge").get());
  const dmId = `dm_${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(aliceDb.collection("chat_rooms").doc(dmId).get());

  // Creating general lounge & sending message succeeds
  await assertSucceeds(aliceDb.collection("chat_rooms").doc("general_student_lounge").set({
    roomType: "group",
    title: "JY-Computers Student Lounge",
    programmeId: "",
    memberUids: [ALICE_UID],
    isGroupPublic: true,
    typingUids: [],
    lastMessage: "Welcome",
    lastSenderName: "Administration",
    lastMessageAt: serverTimestamp(),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(bobDb.collection("chat_rooms").doc("general_student_lounge").get());
});

test("Tightened Instructor Permissions: assigned-course isolation across enrollments, attendance, results, and notifications", async () => {
  const adminDb = testEnv.authenticatedContext(OWNER_ADMIN_UID, SINGLE_ADMIN_CLAIMS).firestore();
  const instr1Db = testEnv.authenticatedContext(INSTRUCTOR_UID).firestore();
  const instr2Db = testEnv.authenticatedContext(OTHER_INSTRUCTOR_UID).firestore();

  // Seed two instructors, two programmes, and an enrollment under prog_a (assigned to INSTRUCTOR_UID)
  await assertSucceeds(adminDb.collection("instructors").doc(INSTRUCTOR_UID).set({
    uid: INSTRUCTOR_UID,
    fullName: "Engr. Musa",
    email: "musa@jy-computers.com",
    specialization: "Software",
    assignedCourseIds: ["prog_a"],
    active: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(adminDb.collection("instructors").doc(OTHER_INSTRUCTOR_UID).set({
    uid: OTHER_INSTRUCTOR_UID,
    fullName: "Mrs. Grace",
    email: "grace@jy-computers.com",
    specialization: "Hardware",
    assignedCourseIds: ["prog_b"],
    active: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(adminDb.collection("programmes").doc("prog_a").set({
    title: "Coding A",
    description: "Course A",
    duration: "3 Months",
    feeNaira: 100000,
    category: "Software",
    level: "Beginner",
    instructorUid: INSTRUCTOR_UID,
    instructorName: "Engr. Musa",
    active: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(adminDb.collection("programmes").doc("prog_b").set({
    title: "Hardware B",
    description: "Course B",
    duration: "3 Months",
    feeNaira: 100000,
    category: "Hardware",
    level: "Beginner",
    instructorUid: OTHER_INSTRUCTOR_UID,
    instructorName: "Mrs. Grace",
    active: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(adminDb.collection("enrollments").doc("enr_prog_a").set({
    userId: ALICE_UID,
    studentName: "Alice Johnson",
    studentIdNumber: "JYC-2026-1001",
    programmeId: "prog_a",
    programmeTitle: "Coding A",
    instructorUid: INSTRUCTOR_UID,
    session: "2026/2027",
    totalFeeNaira: 100000,
    amountPaidNaira: 0,
    status: "active",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));

  // Assigned instructor CAN read assigned enrollment & record attendance/results for prog_a
  await assertSucceeds(instr1Db.collection("enrollments").doc("enr_prog_a").get());
  await assertSucceeds(instr1Db.collection("attendance").doc("prog_a_2026-11-16_alice").set({
    studentUid: ALICE_UID,
    studentName: "Alice Johnson",
    studentIdNumber: "JYC-2026-1001",
    programmeId: "prog_a",
    programmeTitle: "Coding A",
    instructorUid: INSTRUCTOR_UID,
    dateKey: "2026-11-16",
    status: "present",
    markedByUid: INSTRUCTOR_UID,
    remarks: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(instr1Db.collection("results").doc("prog_a_alice").set({
    studentUid: ALICE_UID,
    studentName: "Alice Johnson",
    studentIdNumber: "JYC-2026-1001",
    programmeId: "prog_a",
    programmeTitle: "Coding A",
    instructorUid: INSTRUCTOR_UID,
    session: "2026/2027",
    assignmentScore: 30,
    examScore: 50,
    totalScore: 80,
    grade: "A",
    remarks: "Good",
    published: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));

  // Assigned instructor CAN create result/attendance notifications, CANNOT forge payment/certificate/registration notifications
  await assertSucceeds(instr1Db.collection("notifications").doc("notif_res_1").set({
    userId: ALICE_UID,
    title: "Result Published",
    message: "Coding A: 80/100",
    type: "result",
    isRead: false,
    createdAt: serverTimestamp(),
  }));
  await assertFails(instr1Db.collection("notifications").doc("notif_pay_spoof").set({
    userId: ALICE_UID,
    title: "Payment Approved",
    message: "Spoofed payment confirmation",
    type: "payment",
    isRead: false,
    createdAt: serverTimestamp(),
  }));
  await assertFails(instr1Db.collection("notifications").doc("notif_cert_spoof").set({
    userId: ALICE_UID,
    title: "Certificate Ready",
    message: "Spoofed cert",
    type: "certificate",
    isRead: false,
    createdAt: serverTimestamp(),
  }));

  // Unassigned instructor (instr2) CANNOT read prog_a enrollment, attendance, results, or write attendance/results for prog_a
  await assertFails(instr2Db.collection("enrollments").doc("enr_prog_a").get());
  await assertFails(instr2Db.collection("attendance").doc("prog_a_2026-11-16_alice").get());
  await assertFails(instr2Db.collection("results").doc("prog_a_alice").get());
  await assertFails(instr2Db.collection("attendance").doc("prog_a_2026-11-17_alice").set({
    studentUid: ALICE_UID,
    studentName: "Alice Johnson",
    studentIdNumber: "JYC-2026-1001",
    programmeId: "prog_a",
    programmeTitle: "Coding A",
    instructorUid: OTHER_INSTRUCTOR_UID,
    dateKey: "2026-11-17",
    status: "present",
    markedByUid: OTHER_INSTRUCTOR_UID,
    remarks: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }));
  await assertFails(instr2Db.collection("results").doc("prog_a_alice").update({
    examScore: 59,
    totalScore: 89,
    updatedAt: serverTimestamp(),
  }));
});

test("Pending payment_references lock: atomic pending submission blocks cross-user duplicates, admin approval freezes, admin rejection releases", async () => {
  const adminDb = testEnv.authenticatedContext(OWNER_ADMIN_UID, SINGLE_ADMIN_CLAIMS).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const refKey1 = "OPAY-PEND-101";
  const aliceBatch = aliceDb.batch();
  aliceBatch.set(aliceDb.collection("payments").doc("pay_alice_101"), {
    userId: ALICE_UID,
    studentName: "Alice Johnson",
    studentIdNumber: "JYC-2026-1001",
    enrollmentId: "enr_1",
    serviceRequestId: "",
    programmeId: "prog_coding",
    programmeTitle: "Coding Programme",
    amountNaira: 25000,
    method: "opay_transfer",
    reference: refKey1,
    referenceNormalized: refKey1,
    paymentDate: "2026-11-16",
    senderName: "Alice Johnson",
    senderBank: "OPay",
    evidenceBase64: "",
    status: "pending",
    adminRemarks: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  aliceBatch.set(aliceDb.collection("payment_references").doc(refKey1), {
    referenceNormalized: refKey1,
    paymentId: "pay_alice_101",
    status: "pending",
    submittedByUid: ALICE_UID,
    createdAt: serverTimestamp(),
  });
  await assertSucceeds(aliceBatch.commit());

  // Bob CANNOT submit a second pending payment reusing OPAY-PEND-101
  const bobDupBatch = bobDb.batch();
  bobDupBatch.set(bobDb.collection("payments").doc("pay_bob_101"), {
    userId: BOB_UID,
    studentName: "Bob Smith",
    studentIdNumber: "JYC-2026-1002",
    enrollmentId: "enr_2",
    serviceRequestId: "",
    programmeId: "prog_coding",
    programmeTitle: "Coding Programme",
    amountNaira: 25000,
    method: "opay_transfer",
    reference: refKey1,
    referenceNormalized: refKey1,
    paymentDate: "2026-11-16",
    senderName: "Bob Smith",
    senderBank: "OPay",
    evidenceBase64: "",
    status: "pending",
    adminRemarks: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  bobDupBatch.set(bobDb.collection("payment_references").doc(refKey1), {
    referenceNormalized: refKey1,
    paymentId: "pay_bob_101",
    status: "pending",
    submittedByUid: BOB_UID,
    createdAt: serverTimestamp(),
  });
  await assertFails(bobDupBatch.commit());

  // Admin can promote pending -> approved, after which update & delete are forbidden
  await assertSucceeds(adminDb.collection("payment_references").doc(refKey1).update({ status: "approved" }));
  await assertFails(adminDb.collection("payment_references").doc(refKey1).update({ status: "pending" }));
  await assertFails(adminDb.collection("payment_references").doc(refKey1).delete());

  // Admin rejecting a pending reference CAN delete the pending lock so it can be reused
  const refKey2 = "OPAY-PEND-102";
  const bobBatch2 = bobDb.batch();
  bobBatch2.set(bobDb.collection("payments").doc("pay_bob_102"), {
    userId: BOB_UID,
    studentName: "Bob Smith",
    studentIdNumber: "JYC-2026-1002",
    enrollmentId: "enr_2",
    serviceRequestId: "",
    programmeId: "prog_coding",
    programmeTitle: "Coding Programme",
    amountNaira: 25000,
    method: "opay_transfer",
    reference: refKey2,
    referenceNormalized: refKey2,
    paymentDate: "2026-11-16",
    senderName: "Bob Smith",
    senderBank: "OPay",
    evidenceBase64: "",
    status: "pending",
    adminRemarks: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  bobBatch2.set(bobDb.collection("payment_references").doc(refKey2), {
    referenceNormalized: refKey2,
    paymentId: "pay_bob_102",
    status: "pending",
    submittedByUid: BOB_UID,
    createdAt: serverTimestamp(),
  });
  await assertSucceeds(bobBatch2.commit());
  await assertSucceeds(adminDb.collection("payment_references").doc(refKey2).delete());
});


