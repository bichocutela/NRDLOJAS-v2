import {readFile, writeFile} from 'node:fs/promises';

const sourceUrl = process.env.NORDESTAO_CATALOG_SOURCE_URL;
const outputPath = process.env.NORDESTAO_CATALOG_OUTPUT ?? 'nordestao-catalog.json';
const minItems = Number(process.env.NORDESTAO_CATALOG_MIN_ITEMS ?? '1');
const maxBytes = 8_000_000;

if (!sourceUrl || !sourceUrl.startsWith('https://')) throw new Error('NORDESTAO_CATALOG_SOURCE_URL must be an HTTPS URL');
if (!Number.isInteger(minItems) || minItems < 1) throw new Error('NORDESTAO_CATALOG_MIN_ITEMS must be a positive integer');

function normalizeCode(value) {
  const code = String(value ?? '').trim();
  return code ? code.replace(/\s+/g, '').toUpperCase() : null;
}

function normalizeRecord(record) {
  if (!record || typeof record !== 'object') return null;
  const productId = String(record.produto_id ?? record.productId ?? record.id ?? '').trim();
  const description = String(record.descricao ?? record.description ?? record.nome ?? record.name ?? '').trim();
  if (!productId || !description) return null;
  const images = Array.isArray(record.imagens) ? record.imagens : [];
  const imageUrl = String(record.imagem ?? record.image ?? record.imageUrl ?? images.find(image => image?.src)?.src ?? '').trim() || null;
  const slug = String(record.slug ?? record.link ?? '').trim().replace(/^\/+/, '');
  const productUrl = String(record.productUrl ?? record.url ?? (slug
    ? `https://www.lojaonline.nordestao.com.br/produto/${encodeURIComponent(productId)}/${slug}`
    : `https://www.lojaonline.nordestao.com.br/produto/${encodeURIComponent(productId)}`)).trim();
  return {
    produto_id: productId,
    codigo_interno: normalizeCode(record.codigo_interno ?? record.codigoInterno ?? record.codigo ?? record.code ?? record.sku),
    descricao: description,
    slug: slug || null,
    imagem: imageUrl,
    productUrl
  };
}

async function fetchJson(url) {
  const response = await fetch(url, {signal: AbortSignal.timeout(30_000), headers: {Accept: 'application/json'}});
  if (!response.ok) throw new Error(`CATALOG_SOURCE_HTTP_${response.status}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  if (bytes.length > maxBytes) throw new Error('CATALOG_SOURCE_TOO_LARGE');
  return JSON.parse(bytes.toString('utf8'));
}

const raw = await fetchJson(sourceUrl);
const records = Array.isArray(raw) ? raw : raw?.items;
if (!Array.isArray(records)) throw new Error('CATALOG_SOURCE_SHAPE_INVALID');
const products = records.map(normalizeRecord).filter(Boolean);
if (products.length < minItems) throw new Error(`CATALOG_TOO_SMALL_${products.length}_MIN_${minItems}`);

const productIds = new Set();
const codes = new Set();
for (const product of products) {
  if (productIds.has(product.produto_id)) throw new Error(`DUPLICATE_PRODUCT_ID_${product.produto_id}`);
  productIds.add(product.produto_id);
  if (product.codigo_interno) {
    if (codes.has(product.codigo_interno)) throw new Error(`DUPLICATE_INTERNAL_CODE_${product.codigo_interno}`);
    codes.add(product.codigo_interno);
  }
}

const snapshot = {
  schemaVersion: 1,
  source: 'nordestao-catalog-feed',
  generatedAt: new Date().toISOString(),
  itemCount: products.length,
  items: products
};
await writeFile(outputPath, JSON.stringify(snapshot, null, 2) + '\n', 'utf8');
console.log(`Validated ${products.length} catalog products -> ${outputPath}`);
