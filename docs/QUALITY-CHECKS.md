# Quality checks: JaCoCo and SpotBugs

## Scope

PR CI runs `assembleDebug`, `testDebugUnitTest` and `lintDebug`, followed by:

```bash
./gradlew :app:createDebugUnitTestCoverageReport :app:spotbugsDebug --no-daemon --no-configuration-cache
```

- **JaCoCo** is enabled with Android Gradle Plugin's `enableUnitTestCoverage = true` for the debug build. The unit-test report is expected at `app/build/reports/coverage/test/debug/index.html`. It covers **local JVM unit tests only**, not device/UI tests.
- **SpotBugs** is configured using `com.github.spotbugs-base` and an explicit Android `spotbugsDebug` task. It analyzes compiled debug Java classes with the variant compile classpath and Android boot classpath. Reports are expected at `app/build/reports/spotbugs/debug.html` and `debug.xml`.
- GitHub Actions uploads available reports as the `android-pr-quality-reports` artifact, even on failure. Reports are not committed to Git.

## Initial policy

JaCoCo has **no minimum percentage requirement** yet. SpotBugs findings are reported with `ignoreFailures = true` during the baseline phase, but tool crashes and build failures should still fail the CI.

First inspect the reports and determine whether exclusions and rules are appropriate. Then consider a threshold for meaningful business logic coverage and blocking *new* high-confidence defects. Avoid superficial tests that only increase coverage.

## Notes

- `createDebugUnitTestCoverageReport` is Android Gradle Plugin's unit-test coverage report task. The CI run is the authoritative confirmation that both tasks resolve correctly with the actual AGP version.
- Instrumentation coverage is not included; `connectedDebugAndroidTest` remains a separate emulator/device check.

## Documentation

- [Android coverage](https://developer.android.com/studio/test/coverage-report)
- [SpotBugs Gradle plugin](https://github.com/spotbugs/spotbugs-gradle-plugin)
