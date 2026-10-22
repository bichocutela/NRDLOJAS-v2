import { execFileSync } from 'node:child_process';
import { appendFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export function classify(paths, force = false) {
  let android = force, rules = force;
  for (const path of paths) {
    if (path.startsWith('app/') || path.startsWith('gradle/')) { android = true; continue; }
    if (path === 'firestore.rules') { rules = true; continue; }
    if (path.startsWith('docs/') || /(^|\/)README(?:\.[^/]*)?$/i.test(path) || /^[^/]+\.md$/i.test(path)) continue;
    if (path.startsWith('supabase/') || path.startsWith('tests/days-off/') || path === 'scripts/check-account-admin-permissions.mjs') continue;
    // Application, build logic, CI contracts and unclassified paths fail open
    // to a real Android build. Line counts and commit messages never skip checks.
    android = true;
  }
  return { android, rules };
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  let scope;
  try {
    const base = process.env.DIFF_BASE;
    if (process.env.GITHUB_EVENT_NAME === 'workflow_dispatch' || !/^[0-9a-f]{40}$/.test(base ?? '') || /^0+$/.test(base)) scope = classify([], true);
    else {
      try { execFileSync('git', ['cat-file', '-e', base + '^{commit}'], { stdio: 'ignore' }); }
      catch { execFileSync('git', ['fetch', '--no-tags', '--depth=1', 'origin', base], { stdio: 'ignore', timeout: 60000 }); }
      const paths = execFileSync('git', ['diff', '--no-renames', '--name-only', '-z', base, 'HEAD'], { encoding: 'utf8' }).split('\0').filter(Boolean);
      scope = classify(paths);
    }
  } catch {
    console.log('Could not establish changed paths; full validation and rules deployment required.');
    scope = classify([], true);
  }
  console.log(`Android APK required: ${scope.android}; Firestore rules changed: ${scope.rules}`);
  for (const [key, value] of Object.entries(scope)) appendFileSync(process.env.GITHUB_OUTPUT, `${key}=${value}\n`);
}
