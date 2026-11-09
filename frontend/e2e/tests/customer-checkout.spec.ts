import {expect,test} from '@playwright/test';

const envelope=(data:unknown)=>({success:true,data,error:null,timestamp:'2026-11-09T10:00:00Z'});
const cart={id:'cart-1',status:'ACTIVE',version:1,createdAt:'2026-11-09T09:00:00Z',updatedAt:'2026-11-09T09:00:00Z',items:[{productId:'product-1',productName:'Desk lamp',quantity:2,unitPrice:15,currency:'EUR',availableQuantity:5,productStatus:'ACTIVE',vendorStatus:'APPROVED',updatedAt:'2026-11-09T09:00:00Z'}]};
const valid={cartId:'cart-1',valid:true,lines:[{productId:'product-1',productName:'Desk lamp',quantity:2,unitPrice:15,currency:'EUR',lineTotal:30,eligible:true,issue:null}],subtotal:30,currency:'EUR',issues:[]};
const order={id:'order-1',status:'PENDING_PAYMENT',currency:'EUR',subtotal:30,total:30,expiresAt:'2026-11-09T10:15:00Z',createdAt:'2026-11-09T10:00:00Z',items:[{productId:'product-1',vendorId:'vendor-1',productName:'Desk lamp',quantity:2,unitPrice:15,currency:'EUR',lineTotal:30}]};

test.describe('customer checkout reliability',()=>{
 test.beforeEach(async({page})=>{
  await page.addInitScript(()=>{
   sessionStorage.setItem('markethub.access-token','test-customer-token');
   sessionStorage.setItem('markethub.session-user',JSON.stringify({userId:'customer-1',email:'customer@example.test',role:'CUSTOMER'}));
  });
 });
 test('shows cart errors and allows retrying a failed cart read',async({page})=>{
  let reads=0;
  await page.route('**/api/v1/customer/cart',async route=>{
   reads++;
   if(reads===1)await route.fulfill({status:503,json:{success:false,data:null,error:{message:'Unavailable'}}});
   else await route.fulfill({json:envelope(cart)});
  });
  await page.goto('/cart');
  await expect(page.getByRole('alert')).toContainText('could not load your cart');
  await page.getByRole('button',{name:'Retry'}).click();
  await expect(page.getByRole('heading',{name:'Your cart.'})).toBeVisible();
  await expect(page.getByText('Desk lamp')).toBeVisible();
 });
 test('revalidates cart and exposes line-specific stock issues',async({page})=>{
  await page.route('**/api/v1/customer/cart/checkout/validate',route=>route.fulfill({json:envelope({...valid,valid:false,issues:['Desk lamp: Insufficient available stock'],lines:[{...valid.lines[0],eligible:false,issue:'Insufficient available stock'}]})}));
  await page.route('**/api/v1/customer/cart',route=>route.fulfill({json:envelope(cart)}));
  await page.goto('/checkout');
  await expect(page.getByRole('heading',{name:'Your cart needs attention'})).toBeVisible();
  await expect(page.getByText('Desk lamp: Insufficient available stock')).toBeVisible();
  await expect(page.getByRole('link',{name:'Update cart'})).toHaveAttribute('href','/cart');
 });
 test('reuses order and payment idempotency keys when payment request must be retried',async({page})=>{
  let checkoutKey='';
  const paymentKeys:string[]=[];
  await page.route('**/api/v1/customer/cart/checkout/validate',route=>route.fulfill({json:envelope(valid)}));
  await page.route('**/api/v1/customer/orders/checkout',async route=>{
   checkoutKey=route.request().headers()['idempotency-key'];
   await route.fulfill({json:envelope(order)});
  });
  let paymentAttempts=0;
  await page.route('**/api/v1/customer/orders/order-1/payments',async route=>{
   paymentKeys.push(route.request().headers()['idempotency-key']);
   paymentAttempts++;
   if(paymentAttempts===1)await route.fulfill({status:503,json:{success:false,data:null,error:{message:'Temporary network error'}}});
   else await route.fulfill({json:envelope({id:'payment-1',orderId:'order-1',status:'REQUESTED',providerCode:'SANDBOX',amount:30,currency:'EUR',createdAt:'2026-11-09T10:00:00Z'})});
  });
  await page.route('**/api/v1/customer/orders/order-1',route=>route.fulfill({json:envelope(order)}));
  await page.route('**/api/v1/customer/orders/order-1/fulfilments',route=>route.fulfill({json:envelope([])}));
  await page.goto('/checkout');
  await page.getByRole('button',{name:/Place order and request sandbox payment/}).click();
  await expect(page).toHaveURL(/\/orders\/order-1\?payment=retry/);
  await expect(page.getByRole('button',{name:'Retry sandbox payment request'})).toBeVisible();
  await page.getByRole('button',{name:'Retry sandbox payment request'}).click();
  await expect(page.getByText('Payment request status:')).toBeVisible();
  expect(checkoutKey).toBeTruthy();
  expect(paymentKeys).toHaveLength(2);
  expect(paymentKeys[0]).toBeTruthy();
  expect(paymentKeys[1]).toBe(paymentKeys[0]);
 });
 test('reuses checkout idempotency key after ambiguous order creation failure',async({page})=>{
  const keys:string[]=[];
  let attempts=0;
  await page.route('**/api/v1/customer/cart/checkout/validate',route=>route.fulfill({json:envelope(valid)}));
  await page.route('**/api/v1/customer/orders/checkout',async route=>{
   keys.push(route.request().headers()['idempotency-key']);
   attempts++;
   if(attempts===1)await route.fulfill({status:503,json:{success:false,data:null,error:{message:'Gateway timeout'}}});
   else await route.fulfill({json:envelope(order)});
  });
  await page.route('**/api/v1/customer/orders/order-1/payments',route=>route.fulfill({json:envelope({id:'payment-1',orderId:'order-1',status:'REQUESTED',providerCode:'SANDBOX',amount:30,currency:'EUR',createdAt:'2026-11-09T10:00:00Z'})}));
  await page.route('**/api/v1/customer/orders/order-1',route=>route.fulfill({json:envelope(order)}));
  await page.route('**/api/v1/customer/orders/order-1/fulfilments',route=>route.fulfill({json:envelope([])}));
  await page.goto('/checkout');
  await page.getByRole('button',{name:/Place order and request sandbox payment/}).click();
  await expect(page.getByRole('alert')).toContainText('same request key');
  await page.getByRole('button',{name:/Place order and request sandbox payment/}).click();
  await expect(page).toHaveURL(/\/orders\/order-1\?payment=REQUESTED/);
  expect(keys).toHaveLength(2);
  expect(keys[1]).toBe(keys[0]);
 });
});
