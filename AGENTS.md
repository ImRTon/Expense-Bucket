# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android expense-tracking app. Application code is in `app/src/main/java/com/rton/expensebucket`, organized by responsibility: `data/` (Room entities, DAOs, repository, DataStore), `ui/` (screens, reusable components, theme, view models), `ocr/`, `service/`, `navigation/`, and `di/`. Android resources live in `app/src/main/res`; database migration schemas are versioned in `app/schemas`. Unit tests are in `app/src/test`, while device/emulator tests are in `app/src/androidTest`. Keep one-off data conversion scripts and CSV inputs at the repository root unless they belong in the app module.

## Build, Test, and Development Commands

Run commands from the repository root on Windows:

- `./gradlew.bat :app:assembleDebug` builds a debug APK.
- `./gradlew.bat :app:compileDebugKotlin` checks Kotlin compilation quickly.
- `./gradlew.bat :app:testDebugUnitTest` runs JVM unit tests.
- `./gradlew.bat :app:connectedDebugAndroidTest` runs instrumentation tests on a connected emulator/device.

Use Android Studio for normal local runs. If Gradle cannot locate Java, configure `JAVA_HOME` to Android Studio's bundled JBR.

## Coding Style & Naming Conventions

Write idiomatic Kotlin with four-space indentation and explicit, descriptive names. Use `PascalCase` for classes, composables, and test classes; `camelCase` for functions, properties, and variables; and `UPPER_SNAKE_CASE` for constants. Follow the existing suffixes: `*Screen`, `*ViewModel`, `*Dao`, `*Repository`, and `*Test`. Keep UI state and business logic out of composables where a view model or utility is appropriate. Use resource IDs in `snake_case`.

## Testing Guidelines

Add focused JVM tests for parsers, amount calculations, and other deterministic logic in the matching `com.rton.expensebucket` package. Name test files `SubjectTest.kt` and test methods for the behavior under test, e.g. `parseInvoice_returnsMerchantAndAmount`. Use instrumentation tests only when Android framework, Room integration, or UI/device behavior is essential. Run the relevant test task before submitting changes.

## Commit & Pull Request Guidelines

Use short, imperative commit subjects with a clear scope, such as `fix: invoice QR amount parsing` or `feat: project timeline filter`. Keep unrelated refactors separate. Pull requests should explain the user-visible change and testing performed, link the relevant issue when available, and include screenshots or a short recording for UI changes. Call out database schema changes, migrations, permissions, and configuration changes explicitly.

## Security & Configuration

Do not commit secrets, local SDK paths, or real exported financial data. Keep machine-specific values in `local.properties`, and redact sample CSVs and screenshots before sharing them.
