import copy, hashlib, json, os, sys, time, urllib.request
from pathlib import Path

PROJECT = 'projects/appcodigo-7f245/databases/(default)'
BASE = 'https://firestore.googleapis.com/v1/' + PROJECT + '/documents'
SETTING = PROJECT + '/documents/config/appSettings'
OLD = 'https://drive.google.com/uc?export=download&id=1lsSnHBCJbd-6nCE3xnMHIt95sy79MgCm'
NEW = 'https://drive.google.com/uc?export=download&id=1eph1fM8JYjfOE49cJu_Z6aiBf3Awg-J8'
EXPECTED_SHA = '57b9ecb1ffeebe8f3af153b4392d076a8e332c3a217599259057a82973443ff5'
def mask(url):
    return PROJECT + '/documents/config/banner-mask-' + hashlib.sha256(('blue|' + url).encode()).hexdigest()[:24]
def request(url, data=None, authorized=True):
    headers = {'Authorization': 'Bearer ' + os.environ['TOKEN']} if authorized else {}
    if data is not None: headers['Content-Type'] = 'application/json'
    with urllib.request.urlopen(urllib.request.Request(url, data=None if data is None else json.dumps(data).encode(), headers=headers), timeout=45) as r:
        return json.load(r)
def batch(names, transaction=None):
    data = {'documents': names}
    if transaction: data['transaction'] = transaction
    return {x['found']['name']: x['found'] for x in request(BASE + ':batchGet', data) if 'found' in x}
def stamp(d, name):
    return d[name].get('updateTime') if name in d else None
def target(fields):
    entries = fields['mapValue']['fields']['blue']['arrayValue']['values']
    matches = [x['mapValue']['fields'] for x in entries if x.get('mapValue',{}).get('fields',{}).get('id',{}).get('stringValue') == 'eddf741b-6970-4103-a260-8fb7cd4e4f88']
    if len(matches) != 1: raise ValueError('Fundo não encontrado ou duplicado')
    return matches[0]
def expected(doc):
    return target(doc['fields']['appearanceThemeBackgrounds'])['url']['stringValue']
if sys.argv[1] == 'prepare':
    with urllib.request.urlopen(NEW, timeout=45) as r:
        if hashlib.sha256(r.read()).hexdigest() != EXPECTED_SHA: raise ValueError('A imagem publicada não corresponde à gerada')
    doc = request(BASE + '/config/appSettings')
    if expected(doc) != OLD: raise ValueError('Fundo agendado Dia dos Namorados foi alterado; não substituir')
    masks = batch([mask(OLD), mask(NEW)])
    if mask(NEW) in masks: raise ValueError('Já existe ajuste no novo endereço')
    backup = {'settingsUpdateTime': doc['updateTime'],
              'originalFields': {k: doc['fields'][k] for k in ('appearanceThemeBackgrounds','appearanceRevision') if k in doc['fields']},
              'masks': masks}
    Path('backup-lote10b-5.json').write_text(json.dumps(backup, ensure_ascii=False))
    print('Referência agendado Dia dos Namorados e arquivo público confirmados.')
elif sys.argv[1] == 'apply':
    backup = json.loads(Path('backup-lote10b-5.json').read_text())
    txn = request(BASE + ':beginTransaction', {'options': {'readWrite': {}}})['transaction']
    try:
        docs = batch([SETTING, mask(OLD), mask(NEW)], txn)
        current = docs[SETTING]
        if current['updateTime'] != backup['settingsUpdateTime'] or expected(current) != OLD: raise ValueError('A configuração mudou')
        if any(stamp(docs, n) != stamp(backup['masks'], n) for n in (mask(OLD), mask(NEW))): raise ValueError('Os ajustes mudaram')
        fields = copy.deepcopy(current['fields']['appearanceThemeBackgrounds'])
        target(fields)['url']['stringValue'] = NEW
        revision = int(current['fields'].get('appearanceRevision',{}).get('integerValue', 0))
        writes = []
        if mask(OLD) in docs:
            oldfields = copy.deepcopy(docs[mask(OLD)]['fields'])
            oldfields['backgroundUrl'] = {'stringValue': NEW}
            writes.append({'update': {'name': mask(NEW), 'fields': oldfields}, 'currentDocument': {'exists': False}})
        updated = {'appearanceThemeBackgrounds': fields, 'appearanceRevision': {'integerValue': str(max(int(time.time()*1000), revision+1))}}
        writes.append({'update': {'name': SETTING, 'fields': updated}, 'updateMask': {'fieldPaths': list(updated)},
                       'currentDocument': {'updateTime': current['updateTime']}})
        result = request(BASE + ':commit', {'transaction': txn, 'writes': writes})
        if len(result.get('writeResults', [])) != len(writes): raise ValueError('Resposta de gravação inesperada')
        current = request(BASE + '/config/appSettings')
        if expected(current) != NEW: raise ValueError('Não confirmou a nova imagem')
        print('Fundo agendado Dia dos Namorados substituído. Máscara preservada:', len(writes) > 1)
    except Exception:
        try: request(BASE + ':rollback', {'transaction': txn})
        except Exception: pass
        raise
else:
    raise ValueError('Invalid mode')

