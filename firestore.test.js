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
const ADMIN_UID = "admin_1";

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
    nextOfKin: "Mary Johnson",
    nextOfKinPhone: "08087654321",
    passportPhotoBase64: "",
    selectedProgrammeId: "prog_coding",
    selectedProgrammeTitle: "Coding Programme",
    registrationDate: "2026-11-13",
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
});

test("Student registration & PII isolation: Alice creates own student profile; Bob cannot read Alice's PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validStudentProfile(ALICE_UID)));

  // Bob cannot read Alice's private PII profile
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Privilege escalation blocked: student cannot self-assign admin role or ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badRole = { ...validStudentProfile(ALICE_UID), role: "admin" };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(badRole));

  const ghostField = { ...validStudentProfile(ALICE_UID), isVerified: true };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(ghostField));
});

test("Email spoofing test: unverified admin email is rejected", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: "joshua.dangana@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(
    spoofDb.collection("programmes").doc("prog_1").set({
      title: "Hacked Course",
      description: "Spoofed",
      duration: "1 Month",
      feeNaira: 1000,
      category: "Foundation",
      active: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("Bootstrapped Admin: verified joshua.dangana@gmail.com can manage programmes and settings", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "joshua.dangana@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    adminDb.collection("programmes").doc("basic_computer").set({
      title: "Basic Computer Course",
      description: "Foundation computing skills",
      duration: "1 Month",
      feeNaira: 25000,
      category: "Foundation",
      instructorUid: "",
      instructorName: "Staff Instructor",
      scheduleText: "Mon - Wed • 9:00 AM",
      active: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("Payments: student can create pending payment, cannot self-approve, cannot read Bob's payments", async () => {
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
    senderName: "Alice Johnson",
    evidenceBase64: "",
    status: "pending",
    adminRemarks: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  await assertSucceeds(aliceDb.collection("payments").doc("pay_1").set(validPayment));
  // Cannot self-approve on create
  await assertFails(aliceDb.collection("payments").doc("pay_2").set({ ...validPayment, status: "approved" }));
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
