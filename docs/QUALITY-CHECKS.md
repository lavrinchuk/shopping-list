# Quality checks: JaCoCo and SpotBugs

## Scope

PR CI runs `assembleDebug`, `testDebugUnitTest` and `lintDebug`, followed by:

```bash
./gradlew :app:createDebugUnitTestCoverageReport :app:spotbugsDebug --no-daemon --no-configuration-cache
```

- **JaCoCo** is enabled with Android Gradle Plugin's `enableUnitTestCoverage = true` for the debug build. The unit-test report is expected at `app/build/reports/coverage/test/debug/index.html`. It covers **local JVM unit tests only**, not device/UI tests.
- **SpotBugs** is configured using `com.github.spotbugs-base` and an explicit Android `spotbugsDebug` task. It packages compiled debug Java classes into a JAR (SpotBugs must analyze a real class root, not individual `.class` files) and uses the Android JavaCompile classpath, the runtime AndroidX classpath converted from AARs to Java class JARs, plus the Android boot classpath. A small analysis-only JAR supplies the genuine `java.rmi.Remote` interface read from the running JDK's `jrt:/modules/java.rmi` image (without requiring `jmods/`); it is not a runtime dependency of the app. Reports are expected at `app/build/reports/spotbugs/debug.html` and `debug.xml`.
- GitHub Actions uploads available reports as the `android-pr-quality-reports` artifact, even on failure. Reports are not committed to Git.

## Initial policy

JaCoCo has **no minimum percentage requirement** yet. SpotBugs findings are reported with `ignoreFailures = true` during the baseline phase, but tool crashes and build failures should still fail the CI.

The CI checks that the app's analysis JAR contains classes, that the XML references this JAR, and that SpotBugs findings refer to app classes. Its `FindBugsSummary.total_classes` can be zero even with concrete findings, so it is not used alone to judge scan completeness. `MissingClass` entries are printed as warnings; missing dependencies can make analysis incomplete and should be investigated.

In the initial report, SpotBugs found three potential null dereferences in `ListsActivity`, `ListsViewModel`, and `ShoppingViewModel`. Earlier it also could not resolve `androidx.customview.poolingcontainer.PoolingContainer`, `androidx.tracing.Trace`, and `java.rmi.Remote`. The classpath changes in this PR aim to address these specific missing classes; verify the actual CI XML before considering them fixed. The findings require review rather than automatic fixes.

First inspect the reports and determine whether exclusions and rules are appropriate. Then consider a threshold for meaningful business logic coverage and blocking *new* high-confidence defects. Avoid superficial tests that only increase coverage.

## Notes

- `createDebugUnitTestCoverageReport` is Android Gradle Plugin's unit-test coverage report task. The CI run is the authoritative confirmation that both tasks resolve correctly with the actual AGP version.
- Instrumentation coverage is not included; `connectedDebugAndroidTest` remains a separate emulator/device check.

## Documentation

- [Android coverage](https://developer.android.com/studio/test/coverage-report)
- [SpotBugs Gradle plugin](https://github.com/spotbugs/spotbugs-gradle-plugin)
