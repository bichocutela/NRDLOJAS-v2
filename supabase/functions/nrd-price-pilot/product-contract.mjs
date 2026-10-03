const scalarFields = ["id","code","barCode","description","value","previousValue","clubValue","wholesaleValue","wholesaleQuantity","quantityTake","quantityPay","secondUnitDiscount","unitLimitPerCPF","stockQuantity","packageQuantity","characteristic","contentQuantity","contentUnit","cashback","cashbackValue","validOffer","dueDate","showOffersExpirationDate","startDate","initialDate","validFrom","startAt","offerStartDate","endDate","finalDate","validTo","endAt","offerEndDate"];
const scalar = value => value === null || typeof value === "string" || typeof value === "boolean" || (typeof value === "number" && Number.isFinite(value));
const project = (source, fields) => Object.fromEntries(fields.filter(key=>Object.hasOwn(source,key) && scalar(source[key])).map(key=>[key,source[key]]));
function named(source) { return source && typeof source==="object" && !Array.isArray(source) ? project(source,["description"]) : null; }
export function normalizeProduct(item) {
 if (!item || typeof item!=="object" || Array.isArray(item) || typeof item.description!=="string") throw Error("INVALID_PRODUCT");
 const product=project(item,scalarFields);
 for(const key of ["unit","packageType","productFamily"]) if(Object.hasOwn(item,key)) product[key]=named(item[key]);
 if(Object.hasOwn(item,"productCategories")) {
   if(!Array.isArray(item.productCategories)) { if(item.productCategories!==null)throw Error("INVALID_CATEGORIES");product.productCategories=null; }
   else product.productCategories=item.productCategories.map(x=>{const category=named(x);if(!category)throw Error("INVALID_CATEGORIES");return category;});
 }
 if(Object.hasOwn(item,"auxDescriptions")) {
   if(item.auxDescriptions===null)product.auxDescriptions=null;
   else if(Array.isArray(item.auxDescriptions) && item.auxDescriptions.every(x=>typeof x==="string"))product.auxDescriptions=item.auxDescriptions.slice(0,100);
   else throw Error("INVALID_AUX");
 }
 return product;
}
export function normalizeProductPage(payload,requestedPage,maxItems=20) {
 const items=Array.isArray(payload)?payload:payload?.items ?? payload?.data;
 if(!Array.isArray(items) || items.length>maxItems)throw Error("INVALID_PAGE");
 const integer=(key,fallback)=> {
   if(!Object.hasOwn(payload,key))return fallback;
   const value=payload[key];if(!Number.isSafeInteger(value)||value<0)throw Error("INVALID_PAGINATION");return value;
 };
 const products=items.map(normalizeProduct);
 return {products,page:integer("pageIndex",requestedPage),totalPages:integer("totalPages",products.length?requestedPage+1:0),totalCount:integer("totalCount",products.length)};
}
