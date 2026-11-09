import {expect,test} from '@playwright/test';

const productId='55555555-5555-4555-8555-555555555555';
const vendorId='66666666-6666-4666-8666-666666666666';
const apiResponse=<T>(data:T)=>({success:true,data,error:null,timestamp:new Date().toISOString()});

test('admin can search products and deactivate with a recorded reason',async({page})=>{
  await page.addInitScript(()=>{
    sessionStorage.setItem('markethub.access-token','e2e-admin-token');
    sessionStorage.setItem('markethub.session-user',JSON.stringify({
      userId:'33333333-3333-4333-8333-333333333333',email:'admin@example.test',role:'ADMIN'
    }));
  });
  let patchBody:Record<string,unknown>|undefined;
  await page.route('**/api/v1/admin/products**',async route=>{
    if(route.request().method()==='PATCH'){
      patchBody=route.request().postDataJSON() as Record<string,unknown>;
      await route.fulfill({json:apiResponse({
        id:productId,vendorId,vendorName:'Test vendor',name:'Test product',slug:'test-product',
        price:12.5,currency:'USD',status:'INACTIVE',createdAt:'2026-11-09T10:00:00Z',
        lastActorEmail:'admin@example.test',lastReason:'Policy review 123',lastActionAt:'2026-11-09T11:00:00Z'
      })});
      return;
    }
    await route.fulfill({json:apiResponse({items:[{
      id:productId,vendorId,vendorName:'Test vendor',name:'Test product',slug:'test-product',
      price:12.5,currency:'USD',status:'ACTIVE',createdAt:'2026-11-09T10:00:00Z',
      lastActorEmail:null,lastReason:null,lastActionAt:null
    }],total:1,limit:20,offset:0})});
  });
  await page.route('**/api/v1/admin/payments**',route=>route.fulfill({json:apiResponse({items:[],total:0,limit:100,offset:0})}));
  await page.route('**/api/v1/admin/reviews**',route=>route.fulfill({json:apiResponse({items:[],total:0,limit:100,offset:0})}));
  await page.goto('/admin');
  await expect(page.getByRole('heading',{name:'Product catalogue management'})).toBeVisible();
  await expect(page.getByText('Test product')).toBeVisible();
  page.on('dialog',dialog=>dialog.accept('Policy review 123'));
  await page.getByRole('button',{name:'Deactivate'}).click();
  await expect(page.getByText('Product deactivated and moderation history recorded.')).toBeVisible();
  expect(patchBody).toEqual({reason:'Policy review 123'});
  await expect(page.getByText('INACTIVE',{exact:true})).toBeVisible();
});
