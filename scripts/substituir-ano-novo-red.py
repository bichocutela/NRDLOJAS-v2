import copy, hashlib, json, os, sys, time, urllib.request
from pathlib import Path

PROJECT = 'projects/appcodigo-7f245/databases/(default)'
BASE = 'https://firestore.googleapis.com/v1/' + PROJECT + '/documents'
SETTING = PROJECT + '/documents/config/appSettings'
OLD = 'https://drive.google.com/uc?export=download&id=10g61yL1YHH_DtzoyRMv5FsAzPo3ozywT'
NEW = 'https://drive.google.com/uc?export=download&id=1ehG-KKXHCxtUwU_URDuXWrR87RRggYK3'
EXPECTED_SHA = '2cdc63a938e07e271e5b2dbe4a031fbabd8c234f3539f07a23bbef521bf30348'
def mask(url):
    return PROJECT + '/documents/config/banner-mask-' + hashlib.sha256(('red|' + url).encode()).hexdigest()[:24]
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
    entries = fields['mapValue']['fields']['red']['arrayValue']['values']
    matches = [x['mapValue']['fields'] for x in entries if x.get('mapValue',{}).get('fields',{}).get('id',{}).get('stringValue') == 'b30cf0dc-b132-49f0-99d1-45bf260af709']
    if len(matches) != 1: raise ValueError('Fundo não encontrado ou duplicado')
    return matches[0]
def expected(doc):
    return target(doc['fields']['appearanceThemeBackgrounds'])['url']['stringValue']
if sys.argv[1] == 'prepare':
    with urllib.request.urlopen(NEW, timeout=45) as r:
        if hashlib.sha256(r.read()).hexdigest() != EXPECTED_SHA: raise ValueError('A imagem publicada não corresponde à gerada')
    doc = request(BASE + '/config/appSettings')
    if expected(doc) != OLD: raise ValueError('Fundo agendado Ano Novo red foi alterado; não substituir')
    masks = batch([mask(OLD), mask(NEW)])
    if mask(NEW) in masks: raise ValueError('Já existe ajuste no novo endereço')
    backup = {'settingsUpdateTime': doc['updateTime'],
              'originalFields': {k: doc['fields'][k] for k in ('appearanceThemeBackgrounds','appearanceRevision') if k in doc['fields']},
              'masks': masks}
    Path('backup-ano-novo-red.json').write_text(json.dumps(backup, ensure_ascii=False))
    print('Referência agendado Ano Novo red e arquivo público confirmados.')
elif sys.argv[1] == 'apply':
    backup = json.loads(Path('backup-ano-novo-red.json').read_text())
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
        print('Fundo agendado Ano Novo red substituído. Máscara preservada:', len(writes) > 1)
    except Exception:
        try: request(BASE + ':rollback', {'transaction': txn})
        except Exception: pass
        raise
else:
    raise ValueError('Invalid mode')
