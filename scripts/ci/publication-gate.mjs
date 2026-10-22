import { pathToFileURL } from 'node:url';

export function canComplete({ event, scopeResult, checksResult, packageResult, apkRequired }) {
  if (!['push', 'pull_request', 'workflow_dispatch'].includes(event)) return false;
  if (scopeResult !== 'success' || checksResult !== 'success' || !['true', 'false'].includes(apkRequired)) return false;
  if (event === 'pull_request' || apkRequired === 'false') return packageResult === 'skipped';
  return packageResult === 'success';
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const p = process.env;
  if (!canComplete({ event: p.GITHUB_EVENT_NAME, scopeResult: p.SCOPE_RESULT, checksResult: p.CHECKS_RESULT, packageResult: p.PACKAGE_RESULT, apkRequired: p.APK_REQUIRED })) {
    console.error('Publication blocked: scope, tests or required APK packaging did not succeed.');
    process.exitCode = 1;
  }
}
