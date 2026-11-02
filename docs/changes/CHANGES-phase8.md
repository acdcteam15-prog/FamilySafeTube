# Phase 8 — Gradle 9.4

Requested: use Gradle 9.4, not older.

## What changed
| | Before | Now |
|---|---|---|
| Gradle | 8.13 | **9.4.1** (the 9.4 line; also the minimum AGP 9.2 requires) |
| Android Gradle Plugin | 8.13.0 | **9.2.1** |
| Kotlin | `kotlin-android` plugin 2.2.20 | **built in to AGP** (Kotlin 2.3.10) |
| Compose compiler plugin | 2.2.20 | **2.3.10** (must equal AGP's bundled Kotlin) |
| Jetifier | on | removed (every dependency is AndroidX) |

## Why AGP had to change too
Gradle 9.4 is only supported by **AGP 9.2** (AGP 9.3 needs Gradle 9.5+, AGP 9.4 needs 9.6+; AGP 8.x targets Gradle 8).
Official table: https://developer.android.com/build/releases/about-agp

## Why AGP 9.2.1 and not 9.2.0
9.2.0 had an R8 bug (`ClassNotFoundException: com.android.tools.r8.RecordTag`) fixed in 9.2.1. This project's release build uses R8.

## Migration done (per the official "Migrate to built-in Kotlin" guide)
- Removed the `kotlin-android` plugin (root build file, app build file, version catalog).
- Removed the `kotlin { compilerOptions { jvmTarget } }` block: with built-in Kotlin the target follows `compileOptions` (17).
- Nothing else applied: the project has no kapt, no `kotlinOptions {}`, no custom Kotlin source sets.

## You need
- **Android Studio Panda 4 (2025.3.4) or newer.** Earlier versions cannot open AGP 9.2 projects.
- JDK 17 or newer (Android Studio bundles one). The wrapper downloads Gradle 9.4.1 on first sync.
- SDK Build Tools 36.0.0 (Android Studio offers to install it).

## Guard rails (tools/static_audit.py, "build tooling" check)
Fails if: Gradle is not 9.4.x; Gradle is older than the AGP minimum; AGP is the buggy 9.2.0; the catalog's Kotlin
differs from AGP's bundled Kotlin; any `kotlin-android` / `kotlinOptions` / kapt reference returns; the foojay resolver is too old for Gradle 9.
Each rule was proven by deliberately breaking it.

## Not verified here
A real Gradle sync/build (no Android SDK in my sandbox) and the AGP 9 "new DSL" behaviour on this exact build file.
If sync fails, the error will name the property or API; paste it and I will fix it.
Fallback: `FamilySafeTube-phase7.zip` is the same app on Gradle 8.13 / AGP 8.13.

## Test checklist
- [ ] Open in Android Studio Panda 4+; Gradle sync succeeds and shows Gradle 9.4.1
- [ ] `./gradlew assembleDebug` and `./gradlew assembleRelease` (R8) succeed
- [ ] `./gradlew testDebugUnitTest` passes (150 tests)
- [ ] App behaves as before (this phase changes no app code)
