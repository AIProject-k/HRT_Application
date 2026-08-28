# HormoneLog Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local-first Android HRT record and E2/TT estimation application with traceable evidence, uncertainty, and staged personal calibration.

**Architecture:** Compose UI calls use cases; a pure Kotlin model engine returns immutable estimates; encrypted Room repositories store records, evidence versions, and calculation snapshots. Measured laboratory values and estimated values are different types.

**Tech Stack:** Kotlin, Android native, Jetpack Compose BOM `2026.08.00`, Material 3, Room, SQLCipher, Android Keystore, Kotlinx Serialization, JUnit, Compose UI Test, AndroidX Test.

---

## File structure

```text
app/                   Compose UI, navigation, Android integration
core/domain/           immutable entities and use-case contracts
core/model-engine/     pure E2/TT estimates, calibration, uncertainty
core/data/             SQLCipher Room, Keystore, repositories, export
core/evidence/         versioned evidence bundles and provenance
docs/evidence/         reviewed parameter-source inventory
docs/verification/     AVD/device test evidence
```

### Task 1: Foundation and encrypted storage

**Files:**

- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/com/hormonelog/app/HormoneLogApplication.kt`
- Create: `core/data/src/main/kotlin/com/hormonelog/data/security/DatabasePassphraseProvider.kt`
- Test: `core/data/src/test/kotlin/com/hormonelog/data/security/DatabasePassphraseProviderTest.kt`

- [ ] **Step 1: Write the failing passphrase stability test.**

```kotlin
@Test fun `same stored secret returns same passphrase`() {
    val provider = DatabasePassphraseProvider(InMemoryEncryptedSecretStore(), FixedSecureRandom(7))
    assertContentEquals(provider.getOrCreate(), provider.getOrCreate())
}
```

- [ ] **Step 2: Verify the test fails.**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*DatabasePassphraseProviderTest'`

Expected: compilation failure because `DatabasePassphraseProvider` does not exist.

- [ ] **Step 3: Implement the Keystore-backed contract.**

```kotlin
interface EncryptedSecretStore { fun read(alias: String): ByteArray?; fun write(alias: String, value: ByteArray) }
class DatabasePassphraseProvider(private val store: EncryptedSecretStore, private val random: SecureRandom = SecureRandom()) {
    fun getOrCreate() = store.read(ALIAS) ?: ByteArray(32).also { random.nextBytes(it); store.write(ALIAS, it) }
    private companion object { const val ALIAS = "hormonelog.sqlcipher.passphrase.v1" }
}
```

Use AES/GCM key material from Android Keystore. Throw `DatabaseSecurityException` if it cannot initialize; never fall back to plaintext. Configure Compose BOM `2026.08.00`, minSdk 28, targetSdk 36, JDK 17, Room and SQLCipher.

- [ ] **Step 4: Verify and commit.**

Run: `./gradlew :core:data:testDebugUnitTest :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

```bash
git add settings.gradle.kts build.gradle.kts gradle app core
git commit -m "feat: create encrypted Android application foundation"
```

### Task 2: Immutable record and unit contracts

**Files:**

- Create: `core/domain/src/main/kotlin/com/hormonelog/domain/HormoneTypes.kt`
- Create: `core/domain/src/main/kotlin/com/hormonelog/domain/DoseEvent.kt`
- Create: `core/domain/src/main/kotlin/com/hormonelog/domain/LabResult.kt`
- Create: `core/domain/src/main/kotlin/com/hormonelog/domain/Regimen.kt`
- Test: `core/domain/src/test/kotlin/com/hormonelog/domain/RecordContractsTest.kt`

- [ ] **Step 1: Write failing normalization and eligibility tests.**

```kotlin
@Test fun `lab without collection time is calibration ineligible`() {
    assertFalse(LabEligibility.evaluate(labWithoutCollectionTime).eligible)
}
@Test fun `historical reconstruction excludes a skipped dose`() {
    assertEquals(emptyList<DoseEvent>(), CurveInput(listOf(skippedEvent), weeklyRegimen).historicalAdministrations())
}
```

- [ ] **Step 2: Verify failure.**

Run: `./gradlew :core:domain:testDebugUnitTest --tests '*RecordContractsTest'`

Expected: compilation failure for `LabEligibility` and `CurveInput`.

- [ ] **Step 3: Implement immutable entities.**

```kotlin
data class DoseEvent(val id: UUID, val occurredAt: Instant, val sourceZoneId: String, val route: Route, val amount: BigDecimal, val enteredUnit: DoseUnit, val normalizedMilligrams: BigDecimal?, val status: DoseStatus, val revision: Int)
data class LabResult(val id: UUID, val collectedAt: Instant?, val sourceZoneId: String?, val assay: Assay, val analytes: List<LabAnalyteValue>)
```

Preserve raw and normalized units; eligibility returns an explicit reason for missing time, unsupported route, incomplete dose history, unit conflict, or regimen instability. Regimens only produce forecast events.

- [ ] **Step 4: Verify and commit.**

Run: `./gradlew :core:domain:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

```bash
git add core/domain
git commit -m "feat: add immutable hormone record contracts"
```

### Task 3: Evidence bundles and route enablement

**Files:**

- Create: `core/evidence/src/main/kotlin/com/hormonelog/evidence/EvidenceBundle.kt`
- Create: `core/evidence/src/main/kotlin/com/hormonelog/evidence/RouteModelAvailability.kt`
- Create: `core/evidence/src/main/resources/evidence/bundle-v1.json`
- Create: `docs/evidence/README.md`
- Test: `core/evidence/src/test/kotlin/com/hormonelog/evidence/RouteModelAvailabilityTest.kt`

- [ ] **Step 1: Write the failing availability test.**

```kotlin
@Test fun `unsupported route remains recordable but is not estimate enabled`() {
    val availability = RouteModelAvailability(EvidenceBundle.empty("v1"))
    assertTrue(availability.canRecord(Route.GEL))
    assertFalse(availability.canEstimate(Route.GEL))
}
```

- [ ] **Step 2: Implement source-linked parameters.**

```kotlin
data class ModelParameter(val id: String, val route: Route, val value: Double, val unit: String, val evidenceIds: Set<String>)
data class EvidenceBundle(val version: String, val sources: Map<String, EvidenceSource>, val parameters: List<ModelParameter>) {
    fun supports(route: Route) = parameters.filter { it.route == route }.all { it.evidenceIds.isNotEmpty() && it.evidenceIds.all(sources::containsKey) }
}
```

The JSON bundle contains only reviewed sources and parameter metadata. Never invent clinical values: routes without an approved bundle render `Model unavailable`.

- [ ] **Step 3: Verify and commit.**

Run: `./gradlew :core:evidence:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

```bash
git add core/evidence docs/evidence
git commit -m "feat: add evidence-backed route availability"
```

### Task 4: Deterministic E2/TT model engine

**Files:**

- Create: `core/model-engine/src/main/kotlin/com/hormonelog/model/HormoneEstimate.kt`
- Create: `core/model-engine/src/main/kotlin/com/hormonelog/model/E2CurveEngine.kt`
- Create: `core/model-engine/src/main/kotlin/com/hormonelog/model/TtSuppressionEngine.kt`
- Create: `core/model-engine/src/main/kotlin/com/hormonelog/model/UncertaintyEngine.kt`
- Test: `core/model-engine/src/test/kotlin/com/hormonelog/model/ModelEngineTest.kt`

- [ ] **Step 1: Write failing superposition and safety tests.**

```kotlin
@Test fun `historical estimate sums only administered contributions`() {
    assertEquals(expectedContribution, engine.historicalAt(at, listOf(administered, skipped), bundle).estimate.median, 0.0001)
}
@Test fun `missing dose time blocks estimate`() {
    assertIs<EstimateResult.Blocked>(engine.historicalAt(at, listOf(eventWithoutTime), bundle))
}
```

- [ ] **Step 2: Implement result and model boundaries.**

```kotlin
sealed interface EstimateResult { data class Available(val estimate: HormoneEstimate) : EstimateResult; data class Unavailable(val reason: ModelUnavailableReason) : EstimateResult; data class Blocked(val reasons: Set<EstimateBlockReason>) : EstimateResult }
interface E2RouteModel { fun contributionAt(event: DoseEvent, time: Instant, parameters: RouteParameters): Double }
```

Dispatch injection, oral, sublingual, patch, and gel through distinct `E2RouteModel` implementations. Return a median and uncertainty interval. TT uses delayed E2 exposure plus gonadal status and medication-specific strategies; Bicalutamide and finasteride/dutasteride must not force a strong serum-T reduction.

- [ ] **Step 3: Verify and commit.**

Run: `./gradlew :core:model-engine:testDebugUnitTest detekt`

Expected: `BUILD SUCCESSFUL`.

```bash
git add core/model-engine
git commit -m "feat: add deterministic E2 and TT model engine"
```

### Task 5: Encrypted persistence and reproducible snapshots

**Files:**

- Create: `core/data/src/main/kotlin/com/hormonelog/data/db/HormoneLogDatabase.kt`
- Create: `core/data/src/main/kotlin/com/hormonelog/data/repository/RecordRepository.kt`
- Create: `core/data/src/main/kotlin/com/hormonelog/data/repository/SnapshotRepository.kt`
- Test: `core/data/src/androidTest/kotlin/com/hormonelog/data/SnapshotRepositoryTest.kt`

- [ ] **Step 1: Write the failing revision invalidation test.**

```kotlin
@Test fun editing_an_included_dose_marks_snapshot_stale() = runTest {
    snapshots.save(snapshotReferencing(doseId, revision = 1))
    records.save(dose.copy(revision = 2))
    assertTrue(snapshots.get(snapshot.id).isStale)
}
```

- [ ] **Step 2: Implement entities and transactional snapshot storage.**

```kotlin
@Transaction suspend fun saveSnapshot(snapshot: CalculationSnapshot) {
    snapshotDao.insert(snapshot.toEntity())
    snapshotInputDao.insertAll(snapshot.inputRevisions.map { it.toEntity(snapshot.id) })
}
```

Store engine version, evidence bundle version, calibration ID, input revisions, requested range, creation time, and serialized estimate result.

- [ ] **Step 3: Verify and commit.**

Run: `./gradlew :core:data:testDebugUnitTest :core:data:connectedDebugAndroidTest`

Expected: `BUILD SUCCESSFUL` on a configured AVD or connected device.

```bash
git add core/data
git commit -m "feat: persist encrypted records and snapshots"
```

### Task 6: Graph-first Compose record and laboratory UX

**Files:**

- Create: `app/src/main/kotlin/com/hormonelog/app/navigation/AppNavGraph.kt`
- Create: `app/src/main/kotlin/com/hormonelog/app/feature/dashboard/DashboardScreen.kt`
- Create: `app/src/main/kotlin/com/hormonelog/app/feature/dose/DoseEntryScreen.kt`
- Create: `app/src/main/kotlin/com/hormonelog/app/feature/lab/LabEntryScreen.kt`
- Create: `app/src/main/kotlin/com/hormonelog/app/feature/timeline/TimelineScreen.kt`
- Test: `app/src/androidTest/kotlin/com/hormonelog/app/DashboardFlowTest.kt`

- [ ] **Step 1: Write the failing measured/estimated separation test.**

```kotlin
@Test fun dashboard_labels_estimate_and_measured_values_separately() {
    composeRule.setContent { DashboardScreen(stateWithEstimateAndMeasuredLab) }
    composeRule.onNodeWithText("Estimated E2").assertExists()
    composeRule.onNodeWithText("Last measured E2").assertExists()
}
```

- [ ] **Step 2: Implement dashboard result states and inputs.**

```kotlin
when (val result = state.e2Result) {
    is EstimateResult.Available -> EstimateChart(result.estimate, state.labPoints)
    is EstimateResult.Blocked -> LimitedState(result.reasons)
    is EstimateResult.Unavailable -> ModelUnavailableState(result.reason)
}
```

Add route-adaptive dose fields and a lab form with collection time, assay, source unit, and reference range. Render history/forecast divider, uncertainty band, dose markers, measured points, and a text alternative for chart values. Add Korean/English strings, dark theme, and font-scale testing.

- [ ] **Step 3: Verify and commit.**

Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

Expected: `BUILD SUCCESSFUL`.

```bash
git add app
git commit -m "feat: add graph-first records and laboratory UI"
```

### Task 7: Calibration, validation, evidence explorer, and export

**Files:**

- Create: `core/model-engine/src/main/kotlin/com/hormonelog/model/CalibrationEngine.kt`
- Create: `core/model-engine/src/main/kotlin/com/hormonelog/model/ModelValidation.kt`
- Create: `core/data/src/main/kotlin/com/hormonelog/data/export/ExportService.kt`
- Create: `app/src/main/kotlin/com/hormonelog/app/feature/model/ModelStatusScreen.kt`
- Create: `app/src/main/kotlin/com/hormonelog/app/feature/evidence/EvidenceExplorerScreen.kt`
- Test: `core/model-engine/src/test/kotlin/com/hormonelog/model/CalibrationEngineTest.kt`

- [ ] **Step 1: Write failing calibration and export tests.**

```kotlin
@Test fun one_eligible_lab_selects_level_one_without_overfitting() { assertEquals(CalibrationLevel.LEVEL_1, engine.calibrate(listOf(eligibleLab)).level) }
@Test fun json_export_includes_evidence_version() { assertTrue(exportService.json(snapshot).contains("evidenceBundleVersion")) }
```

- [ ] **Step 2: Implement eligible-lab-only calibration.**

```kotlin
data class CalibrationDecision(val level: CalibrationLevel, val includedLabIds: Set<UUID>, val excluded: Map<UUID, LabIneligibilityReason>, val uncertaintyReasons: Set<UncertaintyReason>)
```

Persist a new calibration snapshot; calculate MAE, relative/median error, and applicable peak/trough timing error without changing raw results. Build Model Status and Evidence Explorer from the selected calculation snapshot. JSON/CSV/PDF export is user-triggered only and includes measured/estimated labels and versions.

- [ ] **Step 3: Verify and commit.**

Run: `./gradlew :core:model-engine:testDebugUnitTest :core:data:testDebugUnitTest :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

```bash
git add core app
git commit -m "feat: add calibration provenance and local export"
```

### Task 8: Android verification evidence

**Files:**

- Create: `docs/evidence/parameter-review-template.md`
- Create: `docs/verification/android-device-checklist.md`
- Create: `docs/verification/android-device-results.md`
- Create: `app/src/androidTest/kotlin/com/hormonelog/app/PrimaryFlowTest.kt`

- [ ] **Step 1: Write the primary flow test.**

```kotlin
@Test fun user_can_record_dose_add_timed_lab_and_see_measured_point() {
    recordDose(Route.IM_INJECTION, "4", "2026-08-17T21:10")
    addLab(e2 = "174", tt = "21", collectedAt = "2026-08-20T09:30", assay = "LC-MS/MS")
    composeRule.onNodeWithText("Last measured E2").assertExists()
}
```

- [ ] **Step 2: Run full AVD gate.**

Run: `./gradlew test lint assembleDebug connectedDebugAndroidTest`

Expected: `BUILD SUCCESSFUL`; retain the Android test report under `app/build/reports/androidTests/connected/`.

- [ ] **Step 3: Record device evidence and commit.**

Use the checklist to record encrypted first launch, multi-route record entry, unavailable-model state, graph labels, lab timing, calibration transition, time-zone/DST behavior, dark/large text, and three exports. Record a physical-device absence as `not run`, never as pass.

```bash
git add docs app/src/androidTest
git commit -m "test: document Android verification evidence"
```

## Plan self-review

- Tasks 1–2 provide encrypted local storage and immutable data; Task 3 gates models on evidence; Task 4 delivers E2/TT behavior and blocked states; Task 5 provides reproducible snapshots; Task 6 delivers the approved graph-first UI; Task 7 covers calibration, validation, and export; Task 8 produces AVD/device evidence.
- All named types are defined in or before the task that first consumes them.
- No task authorizes dose recommendations, plaintext fallback, server transfer, or invented clinical parameters.
