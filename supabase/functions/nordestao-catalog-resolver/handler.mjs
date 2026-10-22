const STOP_WORDS = new Set([
  'a', 'as', 'o', 'os', 'um', 'uma', 'uns', 'umas', 'de', 'da', 'das', 'do', 'dos',
  'e', 'em', 'no', 'na', 'nos', 'nas', 'para', 'por', 'com', 'sem', 'tipo', 'pct',
  'pc', 'un', 'unid', 'unidade'
]);

const WEIGHT = Object.freeze({
  tokenJaccard: 0.58,
  importantTokenRecall: 0.22,
  brandAndSize: 0.20
});

const DEFAULT_MIN_SCORE = 0.86;
export const OFFICIAL_DEPARTMENTS = ['Alimentos','Açougue','Bazar','Bebês e crianças','Bebidas','Bebidas alcoólicas',
  'Congelados','Frios e embutidos','Higiene e beleza','Hortifruti','Leites e laticínios','Limpeza',
  'Padaria e confeitaria','Pet shop','Saudáveis','Snacks'];
export function normalizeEan(value) {
  const raw=String(value ?? '').trim();
  return /^\d{8,14}$/.test(raw) ? raw.replace(/^0+(?=\d)/,'') : null;
}


export function normalizeText(value) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

export function normalizeCode(value) {
  const raw = String(value ?? '').trim();
  if (!raw) return null;
  // Preserve leading zeroes: internal product codes are identifiers, not numbers.
  return raw.replace(/\s+/g, '').toUpperCase();
}

export function tokens(value) {
  return new Set(normalizeText(value).split(' ').filter(token => token && !STOP_WORDS.has(token)));
}

function canonicalSizes(value) {
  const text=String(value ?? '').toLowerCase().replace(/(\d),(\d)/g,'$1.$2');
  return [...text.matchAll(/(\d+(?:\.\d+)?)\s*(kg|mg|ml|cl|g|l)\b/g)].map(([,amount,unit])=>{
    const factor={kg:1000,mg:0.001,g:1,l:1000,cl:10,ml:1}[unit];
    return `${/^(kg|mg|g)$/.test(unit)?'mass':'volume'}:${Number(amount)*factor}`;
  });
}
function compatibleSizes(left,right) {
  const a=canonicalSizes(left),b=canonicalSizes(right);
  return !a.length || !b.length || a.every(size=>b.includes(size));
}

function numericAttributes(value) {
  const text = normalizeText(value);
  const sizes = [...text.matchAll(/\b\d+(?:[.,]\d+)?\s*(?:kg|g|mg|l|ml|cl|un|und|unid|unidade)\b/g)]
    .map(match => match[0].replace(',', '.').replace(/\s+/g, ''));
  const numbers = [...text.matchAll(/\b\d+(?:[.,]\d+)?\b/g)].map(match => match[0].replace(',', '.'));
  return {sizes: new Set(sizes), numbers: new Set(numbers)};
}

function brandTokenSet(input) {
  return tokens(input).size ? tokens(input) : new Set();
}

function jaccard(left, right) {
  if (!left.size || !right.size) return 0;
  let intersection = 0;
  for (const item of left) if (right.has(item)) intersection++;
  const union = left.size + right.size - intersection;
  return union ? intersection / union : 0;
}

function recall(left, right) {
  if (!left.size) return 0;
  let found = 0;
  for (const item of left) if (right.has(item)) found++;
  return found / left.size;
}

function attributeScore(query, candidate) {
  const queryAttrs = numericAttributes(query);
  const candidateAttrs = numericAttributes(candidate);
  const sizeScore = queryAttrs.sizes.size
    ? recall(queryAttrs.sizes, candidateAttrs.sizes)
    : 0.5;
  const numberScore = queryAttrs.numbers.size
    ? recall(queryAttrs.numbers, candidateAttrs.numbers)
    : 0.5;
  return sizeScore * 0.75 + numberScore * 0.25;
}

function toCatalogRecord(record) {
  if (!record || typeof record !== 'object') return null;
  const productId = String(record.produto_id ?? record.productId ?? record.id ?? '').trim();
  const description = String(record.descricao ?? record.description ?? record.nome ?? record.name ?? '').trim();
  if (!productId || !description) return null;
  const images = Array.isArray(record.imagens) ? record.imagens : [];
  const imageUrl = String(
    record.imagem ?? record.image ?? record.imageUrl ?? images.find(image => image?.src)?.src ?? ''
  ).trim() || null;
  const slug = String(record.slug ?? record.link ?? '').trim();
  const productUrl = String(record.productUrl ?? record.url ?? (slug
    ? `https://www.lojaonline.nordestao.com.br/produto/${encodeURIComponent(productId)}/${slug.replace(/^\/+/, '')}`
    : `https://www.lojaonline.nordestao.com.br/produto/${encodeURIComponent(productId)}`)).trim();
  const internalCode = normalizeCode(
    record.codigo_interno ?? record.codigoInterno ?? record.codigo ?? record.code ?? record.sku
  );
  return {
    productId,
    description,
    normalizedDescription: normalizeText(description),
    internalCode,
    ean:normalizeEan(record.codigo_barras ?? record.barCode ?? record.barcode ?? record.ean),
    department:OFFICIAL_DEPARTMENTS.includes(record.department) ? record.department : null,
    sectionId:record.sectionId ?? null,
    imageUrl,
    productUrl,
    raw: record
  };
}

export function buildCatalogIndex(records) {
  const byCode = new Map(), byEan=new Map();
  const catalog = [];
  for (const raw of records ?? []) {
    const record = toCatalogRecord(raw);
    if (!record) continue;
    catalog.push(record);
    for(const [map,key] of [[byCode,record.internalCode],[byEan,record.ean]]) if(key) map.set(key,[...(map.get(key) ?? []),record]);
  }
  return {catalog, byCode, byEan};
}

function scoreDescription(query, candidate) {
  const queryTokens = tokens(query);
  const candidateTokens = tokens(candidate.description);
  const importantQueryTokens = new Set([...queryTokens].filter(token => token.length >= 3));
  const brandAndSize = attributeScore(query, candidate.description);
  return WEIGHT.tokenJaccard * jaccard(queryTokens, candidateTokens)
    + WEIGHT.importantTokenRecall * recall(importantQueryTokens, candidateTokens)
    + WEIGHT.brandAndSize * brandAndSize;
}

export function resolveCatalogItem(item, index, options = {}) {
  const code = normalizeCode(item?.codproduto ?? item?.codigo ?? item?.code ?? item?.internalCode);
  const description = String(item?.desc_prod ?? item?.descricao ?? item?.description ?? item?.name ?? '').trim();
  const minScore = Number.isFinite(options.minScore) ? options.minScore : DEFAULT_MIN_SCORE;

  const ean=normalizeEan(item?.barCode ?? item?.barcode ?? item?.codigo_barras ?? item?.ean);
  for(const [map,key,type] of [[index.byCode,code,'internal_code'],[index.byEan,ean,'ean']]) {
    const candidates=key ? (map?.get(key) ?? []).filter(record=>!ean || !record.ean || record.ean===ean) : [];
    const unique=[...new Map(candidates.map(record=>[record.productId,record])).values()];
    if(unique.length===1)return {input:item,matched:true,matchType:type,score:1,product:unique[0]};
    if(unique.length>1)return {input:item,matched:false,matchType:'ambiguous',score:1,product:null};
  }

  if (!description) {
    return {input: item, matched: false, matchType: 'none', score: 0, product: null, candidates: []};
  }

  const candidates = index.catalog
    .filter(product=>!ean || !product.ean || product.ean===ean)
    .filter(product=>compatibleSizes(description,product.description))
    .map(product => ({product, score: scoreDescription(description, product)}))
    .sort((left, right) => right.score - left.score || left.product.productId.localeCompare(right.product.productId));
  const best = candidates[0];
  const second = candidates[1];
  // The margin prevents choosing between near-identical package sizes or flavours.
  const margin = best && second ? best.score - second.score : best?.score ?? 0;
  const accepted = Boolean(best && best.score >= minScore && margin >= 0.08);
  return {
    input: item,
    matched: accepted,
    matchType: accepted ? 'description' : 'ambiguous',
    score: best?.score ?? 0,
    margin,
    product: accepted ? best.product : null,
    candidates: candidates.slice(0, 3).map(candidate => ({
      productId: candidate.product.productId,
      description: candidate.product.description,
      score: candidate.score,
      imageUrl: candidate.product.imageUrl,
      productUrl: candidate.product.productUrl
    }))
  };
}

export function enrichPromotion(item, resolution) {
  if (!resolution?.matched || !resolution.product) return {...item};
  return {
    ...item,
    // Prefer the official Nordestão catalog image; retain the ACP image only
    // when the catalog match has no usable image URL.
    imagem: resolution.product.imageUrl || item.imagem,
    linkloja: resolution.product.productUrl || item.linkloja,
    catalog_match_type: resolution.matchType,
    catalog_match_score: Number(resolution.score.toFixed(4)),
    catalog_product_id: resolution.product.productId,
    ...(resolution.product.department ? {nrdCategory:resolution.product.department,catalog_department:resolution.product.department,
      catalog_category_source:'official',catalog_section_id:resolution.product.sectionId} : {})
  };
}

export function resolvePromotions(items, records, options = {}) {
  const index = buildCatalogIndex(records);
  return (items ?? []).map(item => {
    const resolution = resolveCatalogItem(item, index, options);
    return {resolution, promotion: enrichPromotion(item, resolution)};
  });
}

export function createHandler({loadCatalog, minScore = DEFAULT_MIN_SCORE} = {}) {
  if (typeof loadCatalog !== 'function') throw new TypeError('CATALOG_LOADER_REQUIRED');
  return async request => {
    if (request.method !== 'POST') return Response.json({error: 'METHOD_NOT_ALLOWED'}, {status: 405});
    let body;
    try {
      body = await request.json();
    } catch {
      return Response.json({error: 'INVALID_JSON'}, {status: 400});
    }
    if (!Array.isArray(body?.items) || body.items.length > 500) {
      return Response.json({error: 'INVALID_ITEMS'}, {status: 400});
    }
    try {
      const catalog = await loadCatalog();
      const results = resolvePromotions(body.items, catalog, {minScore});
      return Response.json({items: results.map(result => result.promotion), matches: results.map(result => result.resolution)}, {
        headers: {'Cache-Control': 'no-store'}
      });
    } catch {
      return Response.json({error: 'CATALOG_UNAVAILABLE'}, {status: 503});
    }
  };
}

