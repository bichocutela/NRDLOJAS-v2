"""Read-only Cloud Monitoring usage report. Credentials stay inside the runner."""
import datetime as dt
import json
import os
import pathlib
import subprocess
import urllib.error
import urllib.parse
import urllib.request

PROJECT = 'appcodigo-7f245'
end = dt.datetime.now(dt.timezone.utc)
start = end - dt.timedelta(days=7)
token = subprocess.check_output(['gcloud', 'auth', 'print-access-token'], text=True).strip()


def timeseries(suffix, gauge=False):
    params = {
        'filter': f'metric.type="firestore.googleapis.com/{suffix}"',
        'interval.startTime': start.isoformat(), 'interval.endTime': end.isoformat(),
        'aggregation.alignmentPeriod': '3600s',
        'aggregation.perSeriesAligner': 'ALIGN_MAX' if gauge else 'ALIGN_SUM',
        'pageSize': '10000',
    }
    series = []
    while True:
        req = urllib.request.Request(f'https://monitoring.googleapis.com/v3/projects/{PROJECT}/timeSeries?' + urllib.parse.urlencode(params), headers={'Authorization': 'Bearer ' + token})
        try:
            with urllib.request.urlopen(req, timeout=45) as response:
                body = json.load(response)
        except urllib.error.HTTPError as error:
            body = json.load(error)
            return {'error': {'http': error.code, 'status': body.get('error', {}).get('status', 'UNKNOWN'), 'message': body.get('error', {}).get('message', ''), 'reasons': [detail.get('reason') for detail in body.get('error', {}).get('details', []) if detail.get('reason')]}, 'series': []}
        series.extend(body.get('timeSeries', []))
        if not body.get('nextPageToken'):
            break
        params['pageToken'] = body['nextPageToken']
    return {'series': series}


metrics = {}
for name, suffixes, gauge in [
    ('reads', ['document/read_ops_count', 'document/read_count'], False),
    ('writes', ['document/write_ops_count', 'document/write_count'], False),
    ('deletes', ['document/delete_ops_count', 'document/delete_count'], False),
    ('connections', ['network/active_connections'], True),
    ('listeners', ['network/snapshot_listeners'], True),
]:
    attempts = []
    for suffix in suffixes:
        result = timeseries(suffix, gauge)
        attempts.append({'metric': suffix, 'error': result.get('error'), 'series_count': len(result['series'])})
        if result['series']:
            hourly, labels = {}, {}
            for series in result['series']:
                label = json.dumps(series.get('metric', {}).get('labels', {}), sort_keys=True)
                for point in series.get('points', []):
                    value = float(point['value'].get('int64Value', point['value'].get('doubleValue', 0)))
                    hour = point['interval']['endTime']
                    hourly[hour] = hourly.get(hour, 0) + value
                    labels[label] = labels.get(label, 0) + value
            metrics[name] = {'metric': suffix, 'hourly': dict(sorted(hourly.items())), 'labels': labels, 'total' if not gauge else 'peak_hour': sum(hourly.values()) if not gauge else max(hourly.values()), 'attempts': attempts}
            break
    else:
        metrics[name] = {'attempts': attempts, 'unavailable': True}
report = {'project': PROJECT, 'start_utc': start.isoformat(), 'end_utc': end.isoformat(), 'note': 'Observed monitoring operations; not a billing invoice. No caller or collection attribution. Gauge hourly maxima can occur at different times.', 'metrics': metrics}
pathlib.Path('firestore-usage.json').write_text(json.dumps(report, indent=2))
lines = ['## Firestore usage (last 7 days)', '', f'Project: `{PROJECT}`. UTC interval: {start.isoformat()} to {end.isoformat()}.', '', '| Metric | Observed value |', '|---|---:|']
for name, metric in metrics.items():
    lines.append(f'| {name} | {metric.get("total", metric.get("peak_hour", "unavailable"))} |')
    if metric.get('unavailable'):
        print(name + ': ' + json.dumps(metric['attempts']))
reads = metrics['reads'].get('hourly', {})
if reads:
    lines += ['', '### Largest hourly read totals', '']
    lines += [f'- {hour}: {int(value)} reads' for hour, value in sorted(reads.items(), key=lambda item: item[1], reverse=True)[:12]]
lines += ['', report['note'], '', 'If unavailable, inspect HTTP status in the attached JSON. The audit never grants IAM permissions or changes Firebase settings.']
summary = '\n'.join(lines) + '\n'
pathlib.Path('firestore-usage.md').write_text(summary)
print(summary)
with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
    output.write(summary)
