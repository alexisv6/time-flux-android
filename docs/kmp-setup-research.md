# Kotlin Multiplatform (KMP) Setup Research — Time Flux

*Research date: May 2026 | Scope: KMP best practices for a production mobile app (Android + iOS)*

---

## Recommended Stack (Quick Reference)

| Layer | Library | Version | Notes |
|---|---|---|---|
| Kotlin | `org.jetbrains.kotlin` | 2.1.x / 2.3.x | K2 compiler, now stable and default |
| Gradle | — | 8.7–8.10 | Use version catalog (TOML) |
| AGP | `com.android.application` | 9.2+ | AGP 9 is a breaking change for KMP (see below) |
| Android KMP Library Plugin | `com.android.kotlin.multiplatform.library` | 9.2+ | New plugin required by AGP 9 for shared modules |
| Build convention | Gradle convention plugins in `build-logic/` | — | Centralizes Gradle config across modules |
| Database | SQLDelight 2.x | 2.0.2+ | `app.cash.sqldelight` — KMP-native choice |
| Networking | Ktor Client | 3.x | + `darwin` engine (iOS) + `okhttp` engine (Android) |
| Dependency Injection | Koin | 4.1.x | With Compiler Plugin for compile-time graph verification |
| Date/Time | kotlinx-datetime | 0.6–0.7 | JetBrains standard, no alternatives needed |
| Serialization | kotlinx-serialization | 1.7–1.8 | JSON + protobuf |
| Settings/Prefs | multiplatform-settings | 1.x | russhwolf — wraps SharedPreferences / NSUserDefaults |
| Logging | Kermit | 2.x | Touchlab — Logcat on Android, OSLog on iOS |
| Swift Interop | SKIE | latest | Touchlab — converts Flow → AsyncSequence automatically |
| ViewModel | lifecycle-viewmodel-compose | 2.10+ | `org.jetbrains.androidx.lifecycle` — KMP ViewModel |
| Architecture | Clean Architecture + MVI | — | Orbit MVI or MVIKotlin |
| Health data | KHealth or HealthKMP | latest | Wrap behind interface in `commonMain` |
| Notifications | KMPNotifier or custom | latest | Wrap behind interface in `commonMain` |
| Navigation (v1) | Native per platform | — | Jetpack Nav (Android) / NavigationStack (iOS) |

---

## 1. Project Setup & Tooling

### Scaffolding
The canonical starting point is the **JetBrains KMP Wizard** at kmp.jetbrains.com. It generates a project with your chosen targets (Android, iOS, Desktop, Web) and lets you choose native UI or Compose Multiplatform. Produces a valid `build.gradle.kts`, version catalog, and source-set hierarchy out of the box.

Run **KDoctor** (`brew install kdoctor`) immediately after setup — it verifies the full toolchain (JDK, Xcode, CocoaPods) faster than manual diagnosis.

The **KMP IDE plugin** is available in Android Studio Narwhal 2025.1+ and IntelliJ 2025.1+. Windows/Linux support is rolling out per the August 2025 roadmap.

### Critical: AGP 9 Breaking Change
AGP 9.0 drops compatibility between the old `com.android.library` plugin and the KMP plugin in the same module. You must now use the new dedicated plugin:

```
com.android.kotlin.multiplatform.library
```

Plan for this from day one. The Android entry point must be a separate module that depends on the KMP shared module.

### Recommended Project Structure

```
TimeFlux/
├── build-logic/                      # Convention plugins (Gradle precompiled scripts)
│   ├── settings.gradle.kts
│   └── src/main/kotlin/
│       ├── kmp-library-convention.gradle.kts
│       └── android-app-convention.gradle.kts
│
├── gradle/
│   └── libs.versions.toml            # Central version catalog — single source of truth
│
├── shared/                           # KMP shared module (core engine)
│   ├── build.gradle.kts              # Applies com.android.kotlin.multiplatform.library
│   └── src/
│       ├── commonMain/               # 80–90% of code lives here
│       │   └── kotlin/com/timeflux/
│       │       ├── data/
│       │       ├── domain/
│       │       ├── moduleregistry/
│       │       └── time/
│       ├── androidMain/              # Android-specific actuals
│       ├── iosMain/                  # iOS-specific actuals
│       └── commonTest/
│
├── feature/
│   ├── timeline/                     # Feature modules (KMP)
│   ├── onboarding/
│   └── settings/
│
├── androidApp/                       # Android entry point
│   └── src/main/                     # Jetpack Compose UI, Activities
│
└── iosApp/                           # Xcode project
    └── iosApp/
        └── ContentView.swift         # SwiftUI entry point
```

### Xcode Integration Gotchas
- **Build times** are the #1 pain point. Kotlin/Native compiles to machine code — iOS builds are slow. Mitigate by: (a) only building for the simulator architecture during development (`iosSimulatorArm64`), (b) enabling Gradle build cache.
- The KMP Wizard configures the `Run Script` build phase in Xcode automatically. Don't touch the framework name without updating both Gradle and Xcode.
- Prefer **Swift Package Manager** integration over CocoaPods for Kotlin framework embedding. More stable and less friction in modern Kotlin versions.
- **You cannot build the iOS target on Windows** — see Section 7.

---

## 2. Database: SQLDelight vs Room for KMP

### SQLDelight 2.x
- Package: `app.cash.sqldelight` (changed from `com.squareup.sqldelight` in 2.0)
- **SQL is the source of truth.** Write `.sq` files with schema + named queries; SQLDelight generates type-safe Kotlin APIs at compile time.
- Migrations: incremental `.sqm` files (`2.sqm` migrates from v2 → v3); Gradle plugin verifies migrations at build time.
- KMP-native from the ground up — longer KMP heritage than Room.
- **Windows bug:** Migration verification fails with an `AccessDeniedException` on Windows 11 (tracked: sqldelight/sqldelight#5312). Avoid running migration verification on Windows CI.

### Room KMP
- Stable for KMP as of Google I/O 2025. Supports Android, iOS, JVM, Linux.
- Same annotation-driven API as Android Room (`@Entity`, `@Dao`, `@Database`) — near-zero learning curve for Android developers.
- Google used Room KMP in the Google Docs iOS app.
- Requires KSP2 (now stable).

### Recommendation for Time Flux
**SQLDelight** is the slightly stronger choice for a greenfield KMP project due to its longer KMP heritage and compile-time query validation. Room is fully valid if your team is fluent in it. Either decision is defensible — pick SQLDelight if you're comfortable writing SQL directly, Room if you want the familiar annotation API.

---

## 3. What Is Shared vs. Platform-Specific

### The `expect`/`actual` Pattern
Language-level mechanism for platform-specific implementations. Declare what an API looks like in `commonMain` (`expect`); provide the implementation in each platform source set (`actual`).

**Use `expect`/`actual` for:** small, isolated platform differences (file paths, platform name, context wrappers, SQLite driver factory).

**Use interface + DI injection for:** larger features (background work scheduling, health data, permissions). Define the interface in `commonMain`, implement in `androidMain`/`iosMain`, inject via Koin. Cleaner and more testable.

### Shareable vs. Platform-Specific

| Category | commonMain | Platform-specific |
|---|---|---|
| Data models / domain entities | ✅ | — |
| Repository interfaces | ✅ | — |
| Repository implementations | ✅ (SQLDelight + Ktor) | SQLite driver selection |
| Use cases / business logic | ✅ | — |
| ViewModels | ✅ (lifecycle-viewmodel) | Lifecycle hookup |
| Time navigation engine | ✅ | — |
| Module registry | ✅ | — |
| Serialization | ✅ | — |
| Database schema + migrations | ✅ | Driver factory |
| HTTP client | ✅ (Ktor core) | Engine (okhttp vs darwin) |
| Logging | ✅ (Kermit) | — |
| UI | ❌ | Compose (Android), SwiftUI (iOS) |
| Navigation | ❌ (for v1) | NavController / NavigationStack |
| Background work | ❌ | WorkManager (Android), BGTaskScheduler (iOS) |
| Health data | ❌ | Health Connect (Android), HealthKit (iOS) |
| Permissions | ❌ | ActivityCompat / CLLocationManager |

### Health Connect vs. HealthKit Pattern
Define a `HealthDataSource` interface in `commonMain`. Implement it in `androidMain` with Health Connect APIs and in `iosMain` with HealthKit. Inject via Koin.

Community libraries:
- **HealthKMP** — github.com/vitoksmile/HealthKMP
- **KHealth** — github.com/shubhamsinghshubham777/KHealth
- Reference sample: github.com/OlgaDery/health_connect_multiplatform

### Background Work / Notifications Pattern
Same interface + DI approach:

```kotlin
// commonMain
interface BackgroundScheduler {
    fun scheduleTimelineSync(intervalMinutes: Int)
    fun cancelAll()
}
// androidMain: WorkManager implementation
// iosMain: BGTaskScheduler implementation
```

Library: **KMPNotifier** — github.com/mirzemehdi/KMPNotifier

---

## 4. Architecture Pattern

### Recommended: Clean Architecture + MVI

Clean Architecture layers map cleanly onto what is shared vs. platform-specific:
- **Domain layer** (use cases, models, interfaces) → 100% `commonMain`
- **Data layer** (repositories, DB, network) → largely `commonMain`, platform actuals for drivers
- **Presentation layer** (ViewModels, UI state) → ViewModels in `commonMain`, UI in platform modules

MVI (Model-View-Intent) with unidirectional data flow suits both Jetpack Compose and SwiftUI.

MVI libraries:
- **Orbit MVI** (`org.orbit-mvi:orbit-core`) — clean, KMP-compatible, actively maintained
- **MVIKotlin** (arkivanov) — powerful, time-travel debugging support, steeper learning curve

### ViewModel Across KMP
Use **`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0`**.

- On Android: full AndroidX ViewModel with ViewModelStore and lifecycle-aware cancellation.
- On iOS: compatible implementation, clears ViewModel when screen is deinitialized.

Pattern: define ViewModels in `commonMain`, expose state as `StateFlow<UiState>`, use **SKIE** on iOS to consume them in SwiftUI as `AsyncSequence`. This is the Touchlab-recommended approach and maximizes shared code.

### Architecture Split

```
commonMain/
├── data/
│   ├── repository/       (TimelineRepository, ModuleRepository)
│   └── datasource/       (LocalDataSource interface, RemoteDataSource interface)
├── domain/
│   ├── model/            (TimelineEntry, LifeModule, TimeRange)
│   ├── repository/       (interfaces)
│   └── usecase/          (GetTimelineUseCase, AddModuleUseCase)
└── presentation/
    └── viewmodel/        (TimelineViewModel, ModuleViewModel)

androidMain/
└── data/datasource/      (AndroidHealthConnectDataSource, RoomDriverFactory)

iosMain/
└── data/datasource/      (IosHealthKitDataSource, SQLiteDriverFactory)

androidApp/
└── ui/                   (Compose screens, NavGraph)

iosApp/
└── Views/                (SwiftUI views, NavigationStack)
```

---

## 5. Compose Multiplatform Readiness

### Current Status
**CMP 1.8.0 (May 2025) declared iOS stable and production-ready.** Breaking changes now follow a deprecation cycle.

Stable in CMP 1.8.0:
- Feature parity with Jetpack Compose for common UI patterns
- Type-safe navigation with deep linking
- VoiceOver and accessibility support
- Native iOS scrolling physics and text editing gestures
- Interop: embed Compose in SwiftUI (`ComposeUIViewController`) or native views in Compose (`UIKitView`)

### Remaining Rough Edges
- **No native iOS components.** Everything renders via Skia on Metal — no Cupertino widget library from JetBrains. Third-party Cupertino libs exist but aren't first-party.
- **XCTest accessibility inspection not supported.** UI testing requires workarounds.
- **Performance on older devices.** Complex Canvas layouts can show jank; profiling required.
- **Deep platform integrations still native.** Home screen widgets, App Clips, watchOS, ShareSheet require UIKit/SwiftUI.

### For Timeline Visualization Specifically
CMP's `Canvas` API uses Skia/Skiko on iOS (Metal backend) — consistent, high-quality 2D rendering identical to Android. Custom `DrawScope`, `Path`, `drawLine`, `drawArc` all work. Timeline canvas rendering is a legitimate CMP use case.

### Recommendation for Time Flux
**Native UI first (your current plan) is correct for v1.** The biggest CMP adopters are doing gradual screen-by-screen migration. CMP's interop story (`ComposeUIViewController` in SwiftUI) means you're not locked in — migrate one screen at a time when ready.

---

## 6. Common Pitfalls

1. **Trying to do everything KMP at once.** Start with domain layer → data layer → ViewModels → UI migration. Incremental adoption avoids early friction.

2. **Overusing `expect`/`actual`.** Use it for small isolated differences. Use interface + DI for anything complex.

3. **Ignoring SKIE from day one.** If iOS developers will consume the KMP framework from SwiftUI, install SKIE immediately. Without it, consuming Kotlin `Flow` and `suspend` from Swift requires ugly manual wrappers.

4. **Neglecting build performance.** Configure `linkDebugFrameworkIosSimulatorArm64` as the only iOS target for local development. Enable Gradle build cache. Set this up early — it becomes painful to fix later.

5. **Version mismatches.** Kotlin, AGP, KSP, Room/SQLDelight, and CMP versions are interdependent. Always check the Kotlin compatibility guide before any upgrade. Use `libs.versions.toml` strictly.

6. **Single Kotlin/Native framework limitation.** You can only embed one Kotlin/Native framework into an iOS app. Multiple Gradle modules must compile into a single framework using the `umbrella`/`XCFramework` combining approach. Plan module boundaries with this in mind.

### Windows Development
- **Cannot compile iOS targets on Windows.** `iosX64`/`iosArm64` Kotlin/Native compilations require macOS.
- On Windows you can develop/test `commonMain`, `androidMain`, and run the Android app normally.
- **CI/CD must use macOS runners** for any iOS build, iOS test, or `.ipa` production. GitHub Actions macOS runners are standard.
- SQLDelight migration verification fails on Windows 11 due to a file locking bug (sqldelight#5312) — skip that task on Windows.

---

## 7. Reference Projects & Resources

### Official
- **KMP Wizard:** kmp.jetbrains.com
- **KMP Templates Gallery:** kmp.jetbrains.com/templates
- **August 2025 Roadmap:** blog.jetbrains.com/kotlin/2025/08/kmp-roadmap-aug-2025
- **CMP 1.8.0 announcement:** blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released
- **AGP 9 migration guide:** kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration

### Reference Apps
| Project | What it shows |
|---|---|
| **nowinkmp** (lihenggui) | "Now in Android" → CMP. Multi-module, Room KMP, Koin, Clean Architecture |
| **openMF/kmp-project-template** | Multi-module KMP with feature module scaffolding |
| **health_connect_multiplatform** (OlgaDery) | HealthKit + Health Connect, SQLDelight, coroutines, ViewModels — directly relevant |

### Key Libraries (GitHub)
- SKIE: github.com/touchlab/SKIE
- Kermit: github.com/touchlab/Kermit
- HealthKMP: github.com/vitoksmile/HealthKMP
- KMPNotifier: github.com/mirzemehdi/KMPNotifier
- multiplatform-settings: github.com/russhwolf/multiplatform-settings
- Orbit MVI: github.com/orbit-mvi/orbit-mvi
- kmp-awesome (full library index): github.com/terrakok/kmp-awesome
- klibs.io (searchable KMP library index with compatibility metadata)
