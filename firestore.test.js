const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_cafe_123";
const BOB_UID = "bob_cafe_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write config, menuItems, or orders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("config").doc("settings").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("menuItems").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("orders").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("orders").doc("ord_1").set({
      ticketNumber: 1,
      dayKey: "2026-11-22",
      staffName: "ሰራተኛ 1",
      status: "PENDING",
      items: [{ c: 1, n: "ማኪያቶ", q: 2, p: 50 }],
      totalAmount: 100,
      unpaid: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: config create, settings update, and ticket sequence update succeed; cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  const settingsRef = aliceDb.collection("users").doc(ALICE_UID).collection("config").doc("settings");
  await assertSucceeds(
    settingsRef.set({
      userId: ALICE_UID,
      cashierPin: "1111",
      ticketerPin: "2222",
      pinsCustomized: false,
      alias78To79: true,
      staff: ["ሰራተኛ 1", "ሰራተኛ 2"],
      nextTicketSeq: 1,
      lastTicketDay: "2026-11-22",
      linkedEmail: "alice@gmail.com",
      appPasswordHash: "",
      appPasswordSalt: "",
      appPasswordSet: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    settingsRef.update({
      cashierPin: "4321",
      ticketerPin: "2222",
      pinsCustomized: true,
      alias78To79: true,
      staff: ["ሰራተኛ 1", "ሰራተኛ 2", "ሰራተኛ 3"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    settingsRef.update({
      linkedEmail: "alice@gmail.com",
      appPasswordHash: "pbkdf2_sha256$abcdef0123456789",
      appPasswordSalt: "0123456789abcdef0123456789abcdef",
      appPasswordSet: true,
      updatedAt: new Date(Date.now() - 800),
    })
  );

  await assertSucceeds(
    settingsRef.update({
      nextTicketSeq: 1,
      lastTicketDay: "2026-11-22",
      dayEpoch: 2,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("config").doc("settings").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("config").doc("settings").update({
      cashierPin: "9999",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("config").doc("settings").update({
      appPasswordHash: "hijacked",
      appPasswordSalt: "salt",
      appPasswordSet: true,
      linkedEmail: "bob@gmail.com",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    settingsRef.update({
      cashierPin: "12",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    settingsRef.update({
      ghostField: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Menu items 1..1000 validation: valid create/update/delete succeed; code 0, 1001, or mismatched code fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  const item79Ref = aliceDb.collection("users").doc(ALICE_UID).collection("menuItems").doc("79");
  await assertSucceeds(
    item79Ref.set({
      code: 79,
      name: "ልዩ ማኪያቶ",
      price: 65,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    item79Ref.update({
      name: "ልዩ ማኪያቶ 2",
      price: 70,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const item1000Ref = aliceDb.collection("users").doc(ALICE_UID).collection("menuItems").doc("1000");
  await assertSucceeds(
    item1000Ref.set({
      code: 1000,
      name: "Special",
      price: 120,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("menuItems").doc("1001").set({
      code: 1001,
      name: "Too High",
      price: 50,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("menuItems").doc("1").set({
      code: 2,
      name: "Mismatch",
      price: 50,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("menuItems").doc("2").set({
      id: "2",
      code: 2,
      name: "Bad ID",
      price: 40,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(item1000Ref.delete());
});

test("Orders lifecycle: PENDING edit & cancel allowed, COMPLETED edit/delete denied, note <= 15, unpaid -> paid works", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 10000);

  // 1. Unpaid + urgent + short note order sent to ticketer
  const ordUnpaidRef = aliceDb.collection("users").doc(ALICE_UID).collection("orders").doc("order_unpaid_1");
  await assertSucceeds(
    ordUnpaidRef.set({
      ticketNumber: 1,
      dayKey: "2026-11-22",
      dayEpoch: 1,
      staffName: "ሰራተኛ 1",
      status: "PENDING",
      items: [{ c: 1, n: "ማኪያቶ", q: 2, p: 50 }],
      totalAmount: 100,
      unpaid: true,
      urgent: true,
      note: "ያለ ስኳር",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Over-length note (> 15 chars) rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("orders").doc("order_long_note").set({
      ticketNumber: 9,
      dayKey: "2026-11-22",
      staffName: "ሰራተኛ 1",
      status: "PENDING",
      items: [{ c: 1, n: "ማኪያቶ", q: 1, p: 50 }],
      totalAmount: 50,
      unpaid: false,
      note: "This note is way too long for 15 chars",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Cashier CAN edit items & totalAmount while status is PENDING
  await assertSucceeds(
    ordUnpaidRef.update({
      items: [
        { c: 1, n: "ማኪያቶ", q: 3, p: 50 },
        { c: 2, n: "ቡና", q: 1, p: 40 },
      ],
      totalAmount: 190,
      urgent: false,
      note: "በቶሎ",
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  // Matches Kotlin repository dayKey and unpaid queries
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("orders")
      .where("dayKey", "==", "2026-11-22")
      .get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("orders")
      .where("unpaid", "==", true)
      .get()
  );

  // Bob cannot list or delete Alice's orders
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("orders")
      .where("unpaid", "==", true)
      .get()
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("orders").doc("order_unpaid_1").delete());

  // Ticketer marks COMPLETED ("ወጥቷል") — unpaid remains true
  await assertSucceeds(
    ordUnpaidRef.update({
      status: "COMPLETED",
      completedAt: new Date(Date.now() - 2000),
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Served (COMPLETED) order CANNOT be deleted OR have its items edited
  await assertFails(ordUnpaidRef.delete());
  await assertFails(
    ordUnpaidRef.update({
      items: [{ c: 1, n: "ማኪያቶ", q: 1, p: 50 }],
      totalAmount: 50,
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Cashier marks unpaid order as paid ("ተከፍሏል") after it was served
  await assertSucceeds(
    ordUnpaidRef.update({
      unpaid: false,
      paidAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Mark RETURNED ("ተመልሷል") and verify terminal lock
  await assertSucceeds(
    ordUnpaidRef.update({
      status: "RETURNED",
      completedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    ordUnpaidRef.update({
      status: "COMPLETED",
      completedAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // 2. Unserved PENDING order CAN be cancelled/deleted by owner
  const ordPendingRef = aliceDb.collection("users").doc(ALICE_UID).collection("orders").doc("order_pending_2");
  await assertSucceeds(
    ordPendingRef.set({
      ticketNumber: 2,
      dayKey: "2026-11-22",
      dayEpoch: 2,
      staffName: "ሰራተኛ 2",
      status: "PENDING",
      items: [{ c: 79, n: "ልዩ ማኪያቶ", q: 1, p: 65 }],
      totalAmount: 65,
      unpaid: false,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(ordPendingRef.delete());
});
