# Bubbel Minimal Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the text-free Bubbel home screen where one central bubble visually toggles the local listening state.

**Architecture:** `BubbelHomeScreen` retains local `isActive` state only; no audio integration belongs in this milestone. `BubbleToggle` renders the two emotional states and active ring. Every colour is supplied through `BubbelTheme`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Compose instrumented tests.

**Spec:** `docs/superpowers/specs/2026-08-31-bubbel-home-design.md`

## Global Constraints

- Centralize every app colour in `ui/theme/Color.kt`; UI code contains no `Color(0x...)` or hex values.
- Disable dynamic Android colours so the approved palette stays fixed.
- The homescreen has no visible text, borders, or heavy shadows.
- Keep the bubble optically centred, keep the settings action clear of system insets, and expose content descriptions for TalkBack.
- Do not add audio processing, permissions, a foreground service, settings content, or navigation in this UI milestone.

---

### Task 1: Establish the fixed theme

**Files:**
- Modify: `app/src/main/java/com/example/bubbel/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/example/bubbel/ui/theme/Theme.kt`
- Create: `app/src/test/java/com/example/bubbel/ui/theme/BubbelColorTest.kt`

**Interfaces:**
- Produces: `BubbelBackground`, `BubbelInactiveBubble`, `BubbelActiveBubble`, `BubbelActiveRing`, `BubbelSettings`, and `BubbelInk`.
- Produces: `BubbelTheme(content: @Composable () -> Unit)` with one fixed `lightColorScheme`.

- [ ] **Step 1: Write the failing palette test**

```kotlin
@Test
fun approvedPaletteHasExpectedArgbValues() {
    assertEquals(0xFFFFF3BD, BubbelBackground.value.toLong())
    assertEquals(0xFFE98D88, BubbelInactiveBubble.value.toLong())
    assertEquals(0xFF9AFA97, BubbelActiveBubble.value.toLong())
    assertEquals(0xFF8E8AE8, BubbelActiveRing.value.toLong())
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew testDebugUnitTest --tests com.example.bubbel.ui.theme.BubbelColorTest`

Expected: compilation fails because the semantic palette does not exist.

- [ ] **Step 3: Implement semantic colours and a fixed scheme**

Replace the starter purple/pink constants in `Color.kt` with the six names above and the exact values from the specification. In `Theme.kt`, remove dynamic colour, `LocalContext`, Android-version checks, and system dark-theme selection. Map `background` and `surface` to `BubbelBackground`, `primary` to `BubbelActiveBubble`, `secondary` to `BubbelActiveRing`, `tertiary` to `BubbelSettings`, and `on*` values to `BubbelInk`.

```kotlin
@Composable
fun BubbelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BubbelColorScheme, typography = Typography, content = content)
}
```

- [ ] **Step 4: Verify and commit**

Run: `./gradlew testDebugUnitTest`

Expected: PASS.

Commit: `git add app/src/main/java/com/example/bubbel/ui/theme app/src/test/java/com/example/bubbel/ui/theme/BubbelColorTest.kt && git commit -m "feat: add fixed Bubbel colour theme"`

### Task 2: Build the static accessible controls

**Files:**
- Modify: `app/src/main/java/com/example/bubbel/MainActivity.kt`
- Create: `app/src/androidTest/java/com/example/bubbel/MainActivityTest.kt`

**Interfaces:**
- Consumes: the `MaterialTheme.colorScheme` from Task 1.
- Produces: `BubbelHomeScreen()`, `BubbleToggle(isActive: Boolean, onToggle: () -> Unit)`, and `SettingsButton(onClick: () -> Unit)`.
- Produces: test tags `bubble_toggle` and `settings_button`.

- [ ] **Step 1: Write failing device tests**

Use `createAndroidComposeRule<ComponentActivity>()`, set `BubbelTheme { BubbelHomeScreen() }`, and test the inactive state, a tap to active, and the settings action.

```kotlin
composeRule.onNodeWithTag("bubble_toggle")
    .assert(hasContentDescription("Luistermodus uit"))
    .performClick()
composeRule.onNodeWithTag("bubble_toggle")
    .assert(hasContentDescription("Luistermodus aan"))
composeRule.onNodeWithContentDescription("Instellingen").assertExists()
```

- [ ] **Step 2: Run the device test and confirm it fails**

Run: `./gradlew connectedDebugAndroidTest --tests com.example.bubbel.MainActivityTest`

Expected: failure because the home composables and semantic nodes do not exist.

- [ ] **Step 3: Replace the starter screen**

Replace `Scaffold` and `Greeting` in `MainActivity.kt` with `BubbelHomeScreen()` inside `BubbelTheme`. Draw a full-screen background from `colorScheme.background`. Place an icon-only `SettingsButton` at `Alignment.TopEnd` with `WindowInsets.safeDrawing`; use `Icons.Outlined.Settings` and `colorScheme.tertiary`. Keep its callback empty.

Centre `BubbleToggle` independently. Use `rememberSaveable` for `isActive`. Draw the face in a Canvas: inactive has angled eyes and a wavy mouth; active has relaxed eye arcs and a smile. Its semantic content description switches between `Luistermodus uit` and `Luistermodus aan`. It must only read colours from `MaterialTheme.colorScheme`.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew connectedDebugAndroidTest --tests com.example.bubbel.MainActivityTest`

Expected: PASS on a connected emulator/device.

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL.

Commit: `git add app/src/main/java/com/example/bubbel/MainActivity.kt app/src/androidTest/java/com/example/bubbel/MainActivityTest.kt && git commit -m "feat: add Bubbel home controls"`

### Task 3: Animate the active state

**Files:**
- Modify: `app/src/main/java/com/example/bubbel/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/example/bubbel/MainActivityTest.kt`

**Interfaces:**
- Consumes: `BubbleToggle` from Task 2.
- Produces: an active ring, selected semantics, and a reduced-motion fallback.

- [ ] **Step 1: Extend the failing test**

```kotlin
composeRule.onNodeWithTag("bubble_toggle")
    .performClick()
    .assertIsSelected()
    .assert(hasContentDescription("Luistermodus aan"))
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew connectedDebugAndroidTest --tests com.example.bubbel.MainActivityTest`

Expected: failure because selected semantics and active ring do not exist.

- [ ] **Step 3: Implement the restrained ring**

Draw the lavender ring behind the bubble only when active. Use `animateFloatAsState` for a 600 ms entrance, then `rememberInfiniteTransition` for a subtle scale and alpha cycle. If `ValueAnimator.areAnimatorsEnabled()` is false, leave the ring visible at a fixed scale and alpha. Apply `Modifier.selectable(selected = isActive, onClick = onToggle, role = Role.Button)`.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: BUILD SUCCESSFUL and unit tests PASS.

Run: `./gradlew connectedDebugAndroidTest --tests com.example.bubbel.MainActivityTest`

Expected: PASS.

Commit: `git add app/src/main/java/com/example/bubbel/MainActivity.kt app/src/androidTest/java/com/example/bubbel/MainActivityTest.kt && git commit -m "feat: animate Bubbel listening state"`

### Task 4: Run the design-improvement review

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-31-bubbel-home-design.md`

**Interfaces:**
- Consumes: the finished UI from Tasks 1–3.
- Produces: observed, actionable visual improvements for the next refinement pass.

- [ ] **Step 1: Review the UI on a physical Android device**

Check inactive and active states for optical centring, ring thickness, facial expression legibility, colour contrast, touch targets, system insets, animation pace, and behaviour when Android animations are disabled.

- [ ] **Step 2: Record only concrete improvement points**

Add only device-observed items to `Ontwikkelkwaliteit en volgende ontwerpiteratie` in the spec. Each item must be measurable or visually verifiable, for example `lower ring thickness from 12 dp to 10 dp`; do not add vague notes such as `make it nicer`.

- [ ] **Step 3: Update status and commit**

In `README.md`, state that the homescreen is a local visual prototype and audio processing remains unimplemented. Run `git diff --check README.md docs/superpowers/specs/2026-08-31-bubbel-home-design.md`; expected output is empty. Commit: `git add README.md docs/superpowers/specs/2026-08-31-bubbel-home-design.md && git commit -m "docs: record Bubbel UI review findings"`.

## Final Verification

- [ ] Run `./gradlew testDebugUnitTest assembleDebug` successfully.
- [ ] Run `./gradlew connectedDebugAndroidTest` successfully on a connected emulator/device.
- [ ] Confirm `MainActivity.kt` contains no visible `Text` composable or direct colour construction for the homescreen.
- [ ] Confirm the design-review section explicitly records real device feedback, or says that the physical-device review is pending.
