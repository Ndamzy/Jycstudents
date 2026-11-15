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
const ADMIN_UID = "jy_single_owner_admin";
const SINGLE_ADMIN_CLAIMS = {
  jySingleAdmin: true,
  role: "admin",
  firebase: { sign_in_provider: "custom" },
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

test("Google account & email spoofing test: Google Sign-In joshua.dangana@gmail.com is REJECTED from admin privileges", async () => {
  const googleAdminSpoofDb = testEnv.authenticatedContext("google_uid_1", {
    email: "joshua.dangana@gmail.com",
    email_verified: true,
    firebase: { sign_in_provider: "google.com" },
  }).firestore();
  const spoofClaimsDb = testEnv.authenticatedContext("other_uid_with_claims", {
    jySingleAdmin: true,
    role: "admin",
    firebase: { sign_in_provider: "google.com" },
  }).firestore();

  await assertFails(
    googleAdminSpoofDb.collection("programmes").doc("prog_1").set({
      title: "Hacked Course",
      description: "Spoofed",
      duration: "1 Month",
      feeNaira: 1000,
      category: "Foundation",
      level: "Beginner",
      active: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(googleAdminSpoofDb.collection("activity_logs").get());
  await assertFails(spoofClaimsDb.collection("activity_logs").get());
});

test("Single Owner Admin (custom-token jy_single_owner_admin) can manage programmes and append activity_logs", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, SINGLE_ADMIN_CLAIMS).firestore();

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
      actorUid: ADMIN_UID,
      actorName: "JY Owner Administrator",
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
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, SINGLE_ADMIN_CLAIMS).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Seed Alice profile
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validStudentProfile(ALICE_UID)));

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
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, SINGLE_ADMIN_CLAIMS).firestore();
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
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, SINGLE_ADMIN_CLAIMS).firestore();
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


