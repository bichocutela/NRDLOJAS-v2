import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdtempSync, writeFileSync, chmodSync, rmSync, mkdirSync, copyFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { canComplete } from '../../scripts/ci/publication-gate.mjs';
import { classify } from '../../scripts/ci/change-scope.mjs';
const workflow = readFileSync('.github/workflows/main.yml', 'utf8');
const filters = readFileSync('.github/ci/android-test-filters.txt', 'utf8').trim().split('\n');
const jobs = Object.fromEntries([...workflow.matchAll(/^  ([a-z]+):\n([\s\S]*?)(?=^  [a-z]+:|$(?![\s\S]))/gm)].map(m => [m[1], m[2]]));
test('publication fails closed for failed, missing, cancelled and skipped required work', () => {
  const good = { event: 'push', scopeResult: 'success', checksResult: 'success', packageResult: 'success', apkRequired: 'true' };
  assert.equal(canComplete(good), true);
  for (const field of ['scopeResult', 'checksResult', 'packageResult']) for (const result of ['failure', 'cancelled', 'skipped', undefined])
    assert.equal(canComplete({ ...good, [field]: result }), false);
  assert.equal(canComplete({ ...good, event: 'pull_request', packageResult: 'skipped' }), true);
  assert.equal(canComplete({ ...good, apkRequired: 'false', packageResult: 'skipped' }), true);
  assert.equal(canComplete({ ...good, apkRequired: undefined }), false);
  assert.equal(canComplete({ ...good, event: 'pull_request_target' }), false);
});
test('small non-APK changes skip Android, while app, CI and unknown paths require it', () => {
  assert.deepEqual(classify(['README.md', 'docs/api.md']), { android: false, rules: false });
  assert.deepEqual(classify(['supabase/functions/send-fcm/index.ts']), { android: false, rules: false });
  assert.deepEqual(classify(['firestore.rules']), { android: false, rules: true });
  for (const path of ['app/src/main/res/values/strings.xml', 'app/src/main/assets/README.md', 'app/src/main/java/X.kt', 'app/src/test/X.kt', '.github/workflows/main.yml', 'gradle.properties', '.github/ci/android-test-filters.txt', '.env.example', 'unknown/file'])
    assert.equal(classify([path]).android, true, path);
  assert.deepEqual(classify([], true), { android: true, rules: true });
  assert.equal(classify(['docs/old.md', 'app/new.kt']).android, true);
});
test('all existing Android regression suites remain in the central check', () => {
  const required = ['com.example.data.GlobalProductUsageTest', 'com.example.data.ProductCategoriesTest', 'com.example.data.ProductRepositorySearchTest',
    'com.example.data.RestrictedAccessLoginTest', 'com.example.data.UpdatePolicyTest', 'com.example.data.training.*', 'com.example.ui.training.*',
    'com.example.data.StoreEncarteTest', 'com.example.data.DynamicPageScheduleTest', 'com.example.data.DynamicPageCodecTest',
    'com.example.data.acp.*', 'com.example.data.promotions.*', 'com.example.data.timeclock.*', 'com.example.data.flyer.*',
    'com.example.data.NossaGenteApiTest', 'com.example.ui.PromotionSortingTest', 'com.example.ui.EncartePdfReaderTest', 'com.example.ui.ProductPhotoViewerTest',
    'com.example.ui.VisualMixOrderSessionTest', 'com.example.ui.DynamicMediaUrlTest', 'com.example.ui.theme.GlassSoftThemeTest', 'com.example.ui.theme.ExpressiveThemeTest',
    'com.example.util.EncarteNotificationsTest', 'com.example.util.NativeNotificationColorsTest', 'com.example.util.ProductNotificationContentTest',
    'com.example.util.XiaomiProgressPolicyTest', 'com.example.util.ProfileNotificationRoutingTest', 'com.example.util.InstallationNotificationPolicyTest', 'com.example.data.NordestaoStoresTest', 'com.example.ui.NordestaoStoresScreenTest'];
  assert.deepEqual(filters, required);
  assert.match(jobs.checks, /bash scripts\/ci\/android-checks.sh/);
});
test('parallel packaging cannot publish before the validation gate', () => {
  assert.match(jobs.package, /needs: scope/);
  assert.doesNotMatch(jobs.package, /needs:.*checks/);
  assert.match(jobs.build, /needs: \[scope, checks, package\]/);
  assert.match(jobs.build, /always\(\) && !cancelled\(\)/);
  assert.match(jobs.build, /node scripts\/ci\/publication-gate.mjs/);
  assert.ok(jobs.build.indexOf('publication-gate.mjs') < jobs.build.indexOf('Deploy Firestore rules'));
  assert.ok(jobs.build.indexOf('publication-gate.mjs') < jobs.build.indexOf('Create GitHub Release'));
  assert.match(jobs.package, /if: github.event_name != 'pull_request' && needs.scope.outputs.android == 'true'/);
  assert.doesNotMatch(jobs.package, /softprops\/action-gh-release|Deploy Firestore/);
  assert.match(jobs.build, /name: app-release/);
  assert.match(jobs.build, /needs.scope.outputs.android == 'true' && github.ref == 'refs\/heads\/main'/);
});
test('Gradle cache is maintained per commit with no competing Java cache', () => {
  for (const job of [jobs.checks, jobs.package]) {
    assert.match(job, /gradle\/actions\/setup-gradle@v4/);
    assert.doesNotMatch(job, /cache: gradle/);
    assert.match(job, /cache-read-only: \$\{\{ github.ref != 'refs\/heads\/main' \}\}/);
  }
  const oldBudget = readFileSync('.github/workflows/firestore-budget.yml', 'utf8');
  assert.doesNotMatch(oldBudget, /gradlew/);
  assert.match(oldBudget, /node --test tests\/ci/);
  assert.match(oldBudget, /cancel-in-progress: true/);
  const flyer = readFileSync('.github/workflows/flyer-validation.yml', 'utf8');
  assert.match(flyer, /name: Compile and run flyer tests\n        if: github.event_name != 'pull_request'/);
  assert.match(flyer, /name: Verify shared Android test coverage\n        if: github.event_name == 'pull_request'/);
  assert.match(workflow, /- "supabase\/\*\*"/);
  assert.match(workflow, /firestore-budget.yml/);
  assert.match(workflow, /flyer-validation.yml/);
  assert.doesNotMatch(readFileSync('.github/workflows/branch-apk.yml', 'utf8'), /cache: gradle/);
});
test('PR validates tests and APK in one invocation; main tests never assemble debug APK', () => {
  const root = mkdtempSync(join(tmpdir(), 'nrd-ci-'));
  try {
    mkdirSync(join(root, '.github/ci'), { recursive: true });
    copyFileSync('.github/ci/android-test-filters.txt', join(root, '.github/ci/android-test-filters.txt'));
    writeFileSync(join(root, 'gradlew'), '#!/usr/bin/env bash\nprintf "%s\\n" "$@"\n'); chmodSync(join(root, 'gradlew'), 0o755);
    for (const mode of ['pr', 'main']) {
      const r = spawnSync('bash', [join(process.cwd(), 'scripts/ci/android-checks.sh'), mode], { cwd: root, encoding: 'utf8' });
      assert.equal(r.status, 0, r.stderr);
      const args = r.stdout.trim().split('\n');
      assert.equal(args[0], 'testDebugUnitTest');
      assert.equal(args.filter(a => a === '--tests').length, filters.length);
      assert.equal(args.includes('assembleDebug'), mode === 'pr');
      assert.equal(args.includes('assembleRelease'), false);
    }
    assert.equal(spawnSync('bash', [join(process.cwd(), 'scripts/ci/android-checks.sh'), 'unknown'], { cwd: root }).status, 2);
  } finally { rmSync(root, { recursive: true, force: true }); }
});
