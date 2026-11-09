import {expect,test} from '@playwright/test';
const envelope=(data:unknown)=>({success:true,data,error:null,timestamp:'2026-11-09T10:00:00Z'});
const order={id:'order-abc123',status:'PAID',currency:'EUR',subtotal:40,total:40,expiresAt:null,createdAt:'2026-11-08T10:00:00Z',items:[{productId:'p1',vendorId:'v1',productName:'Canvas tote',quantity:2,unitPrice:20,currency:'EUR',lineTotal:40}]};
test.describe('customer account dashboard and order history',()=>{
 test.beforeEach(async({page})=>page.addInitScript(()=>{
  sessionStorage.setItem('markethub.access-token','test-customer-token');
  sessionStorage.setItem('markethub.session-user',JSON.stringify({userId:'customer-1',email:'customer@example.test',role:'CUSTOMER'}));
 }));
 test('shows account shortcuts, recent orders and notification updates',async({page})=>{
  await page.route('**/api/v1/customer/orders**',route=>route.fulfill({json:envelope({orders:[order],limit:5,offset:0,hasMore:false})}));
  await page.route('**/api/v1/customer/notifications**',route=>route.fulfill({json:envelope({notifications:[{id:'n1',orderId:'order-abc123',type:'ORDER',title:'Order paid',message:'Your order is confirmed.',createdAt:'2026-11-08T11:00:00Z',readAt:null}],limit:5,offset:0,hasMore:false,unreadCount:1})}));
  await page.goto('/account');
  await expect(page.getByRole('heading',{name:'Your MarketHub.'})).toBeVisible();
  await expect(page.getByRole('link',{name:/Order history/})).toBeVisible();
  await expect(page.getByText('Canvas tote')).toBeVisible();
  await expect(page.getByText('Your order is confirmed.')).toBeVisible();
 });
 test('shows paginated order history and retries a failed read',async({page})=>{
  let reads=0;
  await page.route('**/api/v1/customer/orders**',async route=>{
   reads++;
   if(reads===1)await route.fulfill({status:503,json:{success:false,data:null,error:{message:'Unavailable'}}});
   else await route.fulfill({json:envelope({orders:[order],limit:20,offset:0,hasMore:true})});
  });
  await page.goto('/orders');
  await expect(page.getByRole('alert')).toContainText('could not load your orders');
  await page.getByRole('button',{name:'Try again'}).click();
  await expect(page.getByText('Canvas tote')).toBeVisible();
  await expect(page.getByRole('button',{name:'Next'})).toBeEnabled();
 });
});

test.describe('customer shipment tracking',()=>{
 test.beforeEach(async({page})=>page.addInitScript(()=>{
  sessionStorage.setItem('markethub.access-token','test-customer-token');
  sessionStorage.setItem('markethub.session-user',JSON.stringify({userId:'customer-1',email:'customer@example.test',role:'CUSTOMER'}));
 }));
 test('shows vendor shipment progress, dispatch time and a safe carrier tracking link',async({page})=>{
 await page.route('**/api/v1/customer/orders/order-abc123',route=>route.fulfill({json:envelope(order)}));
 await page.route('**/api/v1/customer/orders/order-abc123/fulfilments',route=>route.fulfill({json:envelope([{
  vendorOrderId:'vendor-order-1',vendorName:'Northstar Goods',status:'FULFILLED',
  shipment:{id:'shipment-1',carrier:'Example Express',trackingNumber:'ZX123456',trackingUrl:'https://carrier.example/track/ZX123456',shippedAt:'2026-11-09T09:30:00Z',estimatedDeliveryDate:'2026-11-12'}
 }])}));
 await page.goto('/orders/order-abc123');
 await expect(page.getByRole('heading',{name:'Fulfilment & tracking'})).toBeVisible();
 await expect(page.getByText('Northstar Goods')).toBeVisible();
 await expect(page.getByText('ZX123456')).toBeVisible();
 await expect(page.getByText(/Dispatched/)).toBeVisible();
 const tracking=page.getByRole('link',{name:'Track with Example Express ↗'});
 await expect(tracking).toHaveAttribute('href','https://carrier.example/track/ZX123456');
 await expect(tracking).toHaveAttribute('rel','noopener noreferrer');
});
test('shows a retry action when fulfilment tracking cannot be loaded',async({page})=>{
 let reads=0;
 await page.route('**/api/v1/customer/orders/order-abc123',route=>route.fulfill({json:envelope(order)}));
 await page.route('**/api/v1/customer/orders/order-abc123/fulfilments',async route=>{
  reads++;
  if(reads===1)await route.fulfill({status:503,json:{success:false,data:null,error:{message:'Unavailable'}}});
  else await route.fulfill({json:envelope([])});
 });
 await page.goto('/orders/order-abc123');
 await expect(page.getByRole('alert')).toContainText('Shipment details could not be loaded');
 await page.getByRole('button',{name:'Try again'}).click();
 await expect(page.getByText(/No fulfilment details are available yet/)).toBeVisible();
});
});
