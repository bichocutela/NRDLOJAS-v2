import { createLiveCatalogLookup } from '../nrd-price-gateway/catalog-live.mjs';
import { boundedJson } from '../nrd-price-gateway/handler.mjs';
import { createImageHandler } from './handler.mjs';
// Public catalog metadata only. Upstream storefront tokens never leave this function.
Deno.serve(createImageHandler({lookup:createLiveCatalogLookup({boundedJson,maxItems:1,concurrency:1,budgetMs:12000,strict:true})}));
