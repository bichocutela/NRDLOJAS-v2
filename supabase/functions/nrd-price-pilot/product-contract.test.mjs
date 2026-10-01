import {test} from "node:test";
import assert from "node:assert/strict";
import {normalizeProductPage} from "./product-contract.mjs";
test("preserves all current commercial fields and named relations without metadata",()=>{
 const item={id:"p",code:"251783",barCode:"123",description:"Produto",value:"149.99",previousValue:"159.99",clubValue:"140",wholesaleValue:"130",wholesaleQuantity:3,quantityTake:4,quantityPay:3,cashback:10,cashbackValue:2,secondUnitDiscount:50,unitLimitPerCPF:6,stockQuantity:12,dueDate:"2026-12-31",packageQuantity:2,characteristic:"A",contentQuantity:190,contentUnit:"g",startDate:"2026-10-01",endDate:"2026-10-31",unit:{description:"KG",url:"supplier"},productCategories:[{description:"De-Por",token:"secret"}],packageType:{description:"Pacote"},productFamily:{description:"Alimentos"},auxDescriptions:["Complemento"],accessToken:"secret"};
 const page=normalizeProductPage({items:[item],pageIndex:2,totalPages:10,totalCount:180},2);
 assert.deepEqual({...page.products[0],accessToken:undefined}, {...item,accessToken:undefined,unit:{description:"KG"},productCategories:[{description:"De-Por"}]});
 assert.equal(page.totalPages,10);assert.equal(page.totalCount,180);assert.equal(page.page,2);
});
test("malformed metadata and oversized pages fail instead of silently truncating",()=>{
 assert.throws(()=>normalizeProductPage({items:[],totalPages:-1},0));
 assert.throws(()=>normalizeProductPage({items:Array.from({length:21},()=>({description:"P"}))},0));
 assert.throws(()=>normalizeProductPage({items:[{description:"P",productCategories:"bad"}]},0));
});
