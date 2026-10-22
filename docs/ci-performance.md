# Android CI performance

## Audit (22 October 2026)

The 7 October optimization (`b85d587`, PR #175) remains active: PRs do not minify/sign/publish release APKs, and superseded PR runs are cancelled. It did not remove duplicated Android workflows or the serial debug-test/release-build path.

Measured baseline from the signed v1.0.832 run (39797937418): executor assignment about 4 seconds; Android tests 145 seconds; signed release assembly 341 seconds. The v1.0.831 run spent 104 seconds on tests and 248 seconds assembling release. In the latest PR, tests took 131 seconds, then debug packaging another 105 seconds. A separate Firestore budget Android job recompiled overlapping suites for 217 seconds in an independent runner.

The primary workflow restored a 1.4 GB setup-java Gradle cache under the same dependency key and reported `Cache hit occurred on the primary key ... not saving cache`. Kotlin compilation and R8 still ran, with only 8 of 50 release tasks restored from cache. Small source changes still affect a single Android module, and each new APK has a new version: task caching cannot guarantee instant compilation or reuse of the whole previous APK.

## Changes

- One Gradle-aware cache provider (`gradle/actions/setup-gradle@v4`) refreshes main-branch build state per commit. PRs only read it. Configuration-cache persistence is enabled only if the optional existing `GRADLE_ENCRYPTION_KEY` secret is configured; absent that key, the action does not store configuration-cache data. No secret setup is required for the normal build cache.
- Keep all 28 regression filters. PR testing and debug APK assembly share one Gradle invocation. Checks and signed release assembly run in parallel on independent runners.
- The existing required `build` check is the final gate: scope, all tests and required signed packaging must succeed for this exact commit before rules or releases can be published. APK signature verification, release minification, mapping retention and atomic version reservation remain intact.
- Firestore budget and flyer PR workflows verify central test coverage instead of recompiling Android. Their status names remain available. Standalone flyer push/manual validation still compiles and tests. Superseded budget/flyer runs cancel automatically.
- The official Gradle 9.3.1 wrapper already matches its published checksum. Validate it instead of replacing the committed JAR on every primary build.

## Changes that need no APK

A conservative path classifier recognizes documentation, Supabase server files and Firestore rules. These keep the server/CI checks but skip Android compilation and tag/APK publication. Firestore rule changes still deploy rules after validation. Any app change, build logic, CI contract, unknown path, missing comparison base or manual dispatch requires full Android validation. Bundled app assets, including README-named files, always require a new APK. File deletions and renames use both paths; line counts and commit messages never bypass checks.

A small native source change still needs a correctly versioned, optimized, signed APK. Training content changed through the Mestre panel already updates remotely and does not need a commit or APK.

## Verification

`node --test tests/ci/*.test.mjs` exercises failed/cancelled/missing publication prerequisites, conservative path classification, preservation of every regression suite and actual argument passing to a fake Gradle wrapper. Live PR/release runs verify the complete new pipeline. Compare actual job/step timestamps rather than GitHub run status alone, which can lag the step log. A first run warms the new cache; later runs give a better measure of cache benefit. No reduction percentage is guaranteed until measured.
