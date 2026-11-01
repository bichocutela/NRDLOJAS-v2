import os, sys, json, time, hashlib, urllib.parse, urllib.request
from pathlib import Path

PROJECT = 'projects/appcodigo-7f245/databases/(default)'
BASE = 'https://firestore.googleapis.com/v1/' + PROJECT + '/documents'
PREFIX = PROJECT + '/documents/config/banner-mask-'
SETTING = PROJECT + '/documents/config/appSettings'
MAP_URL = 'https://drive.google.com/uc?export=download&id=1FSjXtxKCZk6uZmNal-W12hbXKXH82yUH'
MAP_SHA = 'e4d150a933e1ba1cc0e5f2738913cd35354da8bf46f8d9e16142aee5e70552b2'
TARGETS = ('appearanceDefaultThemeBackgrounds', 'appearanceThemeBackgrounds')

def request(url, body=None):
    headers = {'Authorization': 'Bearer ' + os.environ['TOKEN']}
    if body is not None: headers['Content-Type'] = 'application/json'
    data = None if body is None else json.dumps(body).encode()
    with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers), timeout=60) as r:
        return json.load(r)

def mapping():
    with urllib.request.urlopen(MAP_URL, timeout=45) as response: data = response.read()
    if hashlib.sha256(data).hexdigest() != MAP_SHA: raise ValueError('Mapping integrity check failed')
    items = json.loads(data)['items']
    result = {x['original']: x['driveId'] for x in items}
    if len(result) != len(items) or len(result) != 332: raise ValueError('Invalid mapping')
    return result

def make_plan(doc, files):
    fields = json.loads(json.dumps(doc['fields']))
    pairs = {}
    count = 0
    def visit(theme, value):
        nonlocal count
        if isinstance(value, dict):
            v = value.get('mapValue', {}).get('fields', {}).get('url', {})
            old = v.get('stringValue', '')
            prefix = 'https://kkayksyzksexoarpfxyj.supabase.co/storage/v1/object/public/nrdlojas-images/'
            if old.startswith(prefix):
                path = urllib.parse.unquote(old[len(prefix):].split('?', 1)[0])
                if path in files:
                    new = 'https://drive.google.com/uc?export=download&id=' + files[path]
                    themes = {theme.strip().lower()}
                    if theme.strip().lower() == 'expressive': themes.add('multicolor')
                    for mask_theme in themes:
                        h = lambda s: hashlib.sha256((mask_theme + '|' + s.strip()).encode()).hexdigest()[:24]
                        pair = (PREFIX + h(old), PREFIX + h(new))
                        if pair in pairs and pairs[pair] != (mask_theme, old, new): raise ValueError('Mask hash collision')
                        pairs[pair] = (mask_theme, old, new)
                    v['stringValue'] = new
                    count += 1
            for item in value.values(): visit(theme, item)
        elif isinstance(value, list):
            for item in value: visit(theme, item)
    for field in TARGETS:
        for theme, value in fields.get(field, {}).get('mapValue', {}).get('fields', {}).items(): visit(theme, value)
    return fields, pairs, count

def batch_get(names, transaction=None):
    found = {}
    for start in range(0, len(names), 80):
        body = {'documents': names[start:start+80]}
        if transaction: body['transaction'] = transaction
        for entry in request(BASE + ':batchGet', body):
            if 'found' in entry: found[entry['found']['name']] = entry['found']
    return found

def stamp(found, name):
    doc = found.get(name)
    return doc.get('updateTime') if doc else None

if sys.argv[1] == 'prepare':
    doc = request(BASE + '/config/appSettings')
    fields, pairs, count = make_plan(doc, mapping())
    if count == 0 or count > 400 or len(pairs) > 500: raise ValueError('Unexpected number of links')
    names = sorted({n for pair in pairs for n in pair})
    masks = batch_get(names)
    payload = {'settingsUpdateTime': doc['updateTime'],
               'settingsFields': {k: doc['fields'][k] for k in (*TARGETS, 'appearanceRevision') if k in doc['fields']},
               'maskNames': names, 'masks': masks}
    Path('appearance-migration-backup.json').write_text(json.dumps(payload, ensure_ascii=False))
    print('Referências preparadas:', count, 'máscaras existentes:', len(masks))
elif sys.argv[1] == 'apply':
    backup = json.loads(Path('appearance-migration-backup.json').read_text())
    txn = request(BASE + ':beginTransaction', {'options': {'readWrite': {}}})['transaction']
    try:
        current = batch_get([SETTING], txn).get(SETTING)
        if not current or current['updateTime'] != backup['settingsUpdateTime']: raise ValueError('Appearance changed after backup')
        fields, pairs, count = make_plan(current, mapping())
        names = sorted({n for pair in pairs for n in pair})
        if names != backup['maskNames']: raise ValueError('Mask mapping changed after backup')
        masks = batch_get(names, txn)
        if any(stamp(masks, n) != stamp(backup['masks'], n) for n in names):
            raise ValueError('A mask changed after backup')
        writes = []
        for (old_name, new_name), (theme, old, new) in pairs.items():
            source, target = masks.get(old_name), masks.get(new_name)
            if source and target:
                raise ValueError('Destination mask already exists')
            if source:
                payload = json.loads(json.dumps(source['fields']))
                payload['backgroundUrl'] = {'stringValue': new}
                writes.append({'update': {'name': new_name, 'fields': payload},
                               'currentDocument': {'exists': False}})
        rev = int(current['fields'].get('appearanceRevision', {}).get('integerValue', 0))
        fields['appearanceRevision'] = {'integerValue': str(max(int(time.time()*1000), rev+1))}
        patch = {k: fields[k] for k in (*TARGETS, 'appearanceRevision') if k in fields}
        writes.append({'update': {'name': SETTING, 'fields': patch},
                       'updateMask': {'fieldPaths': list(patch)},
                       'currentDocument': {'updateTime': current['updateTime']}})
        result = request(BASE + ':commit', {'transaction': txn, 'writes': writes})
        if len(result.get('writeResults', [])) != len(writes): raise ValueError('Unexpected commit response')
        fresh = request(BASE + '/config/appSettings')
        _, _, remaining = make_plan(fresh, mapping())
        if remaining: raise ValueError('Published configuration still contains mapped source links')
        print('Referências atualizadas:', count, 'máscaras preservadas:', len(writes)-1)
    except Exception:
        try: request(BASE + ':rollback', {'transaction': txn})
        except Exception: pass
        raise
else:
    raise ValueError('Expected prepare or apply')
