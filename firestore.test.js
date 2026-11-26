const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-fiaovent-49a106c7-c385-4463-bec5-191bb78d3716";
const STORE_CODE = "ANICAR2026";
const OTHER_STORE = "OTRA2026";

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

test("Root enumeration and invalid store codes are blocked", async () => {
  const db = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(db.collection("tiendas").get());
  await assertFails(db.collection("clientes").get());
  await assertFails(db.doc("tiendas/ab/configuracion/tienda").get()); // < 4 chars
  await assertFails(db.doc("tiendas/anicar/configuracion/tienda").get()); // lowercase rejected
  await assertFails(
    db.doc("tiendas/ab/clientes/c1").set({
      name: "Bad Store",
      phone: "",
      notes: "",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Store config (tiendas/{codigoTienda}/configuracion/tienda): create, read, update, and schema checks", async () => {
  const db = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  const tiendaRef = db.doc(`tiendas/${STORE_CODE}/configuracion/tienda`);
  const now = new Date(Date.now() - 1000);

  // Initial empty get succeeds cleanly (returns non-existing)
  const initialSnap = await assertSucceeds(tiendaRef.get());
  assert.equal(initialSnap.exists, false);

  // Mismatched storeCode field fails
  await assertFails(
    tiendaRef.set({
      storeCode: OTHER_STORE,
      exchangeRate: 45.0,
      rateLabel: "Tasa BCV",
      rateUpdatedAtMillis: 0,
      businessName: "Abasto Anicar",
      seededDemo: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid store config create succeeds
  await assertSucceeds(
    tiendaRef.set({
      storeCode: STORE_CODE,
      exchangeRate: 45.0,
      rateLabel: "Tasa BCV",
      rateUpdatedAtMillis: 0,
      businessName: "Abasto Anicar",
      seededDemo: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid update of exchangeRate & businessName succeeds
  await assertSucceeds(
    tiendaRef.update({
      exchangeRate: 52.4,
      rateUpdatedAtMillis: 1764100000000,
      businessName: "Bodega Anicar",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Mutating storeCode or adding unknown ghost field fails
  await assertFails(
    tiendaRef.update({
      storeCode: OTHER_STORE,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    tiendaRef.update({
      ghostField: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Clientes & Inventario under tiendas/{codigoTienda}: create, list, update, category enum, and store isolation", async () => {
  const db = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  const now = new Date(Date.now() - 1000);

  // Valid client create
  const custRef = db.doc(`tiendas/${STORE_CODE}/clientes/demo_cust_1`);
  await assertSucceeds(
    custRef.set({
      name: "María Rojas",
      phone: "0414-3829104",
      notes: "Vecina",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid client partial update
  await assertSucceeds(
    custRef.update({
      phone: "0414-0000000",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Valid product create
  const prodRef = db.doc(`tiendas/${STORE_CODE}/inventario/demo_prod_1`);
  await assertSucceeds(
    prodRef.set({
      name: "Empanada",
      priceCents: 150,
      costCents: 80,
      stockQuantity: 0,
      unlimitedStock: true,
      category: "Comidas",
      unit: "u",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid category fails
  await assertFails(
    db.doc(`tiendas/${STORE_CODE}/inventario/bad_cat`).set({
      name: "Otra cosa",
      priceCents: 100,
      costCents: 50,
      stockQuantity: 5,
      unlimitedStock: false,
      category: "Electronica",
      unit: "u",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Stock adjustment update succeeds
  await assertSucceeds(
    prodRef.update({
      stockQuantity: 4,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Listing same store returns docs; listing another store is isolated (empty)
  const sameStoreSnap = await assertSucceeds(db.collection(`tiendas/${STORE_CODE}/clientes`).get());
  assert.equal(sameStoreSnap.size, 1);

  const otherStoreSnap = await assertSucceeds(db.collection(`tiendas/${OTHER_STORE}/clientes`).get());
  assert.equal(otherStoreSnap.size, 0);
});

test("Movimientos under tiendas/{codigoTienda}: create with existing customerId check and line items", async () => {
  const db = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    db.doc(`tiendas/${STORE_CODE}/clientes/cust_1`).set({
      name: "Carlos Mendoza",
      phone: "0412-5519082",
      notes: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Non-existent customerId rejected by exists() guard
  await assertFails(
    db.doc(`tiendas/${STORE_CODE}/movimientos/mov_orphan`).set({
      customerId: "non_existent",
      type: "CREDIT",
      totalCents: 500,
      paymentMethod: "",
      note: "Orphan",
      rateUsed: 45.0,
      activityAtMillis: 1764100000000,
      items: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid movement referencing existing customerId and items succeeds
  await assertSucceeds(
    db.doc(`tiendas/${STORE_CODE}/movimientos/mov_ok`).set({
      customerId: "cust_1",
      type: "CREDIT",
      totalCents: 300,
      paymentMethod: "",
      note: "2 empanadas",
      rateUsed: 45.0,
      activityAtMillis: 1764100000000,
      items: [
        {
          productId: "prod_1",
          productName: "Empanada",
          unitPriceCents: 150,
          quantity: 2,
          subtotalCents: 300,
        },
      ],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid movement type fails
  await assertFails(
    db.doc(`tiendas/${STORE_CODE}/movimientos/mov_bad_type`).set({
      type: "UNKNOWN",
      totalCents: 100,
      paymentMethod: "Efectivo $",
      note: "",
      rateUsed: 45.0,
      activityAtMillis: 1764100000000,
      items: [],
      createdAt: now,
      updatedAt: now,
    })
  );
});
