import {expect,test} from '@playwright/test';
const envelope=(data:unknown)=>({success:true,data,error:null,timestamp:'2026-11-09T10:00:00Z'});
const customer={accessToken:'customer-token',tokenType:'Bearer',expiresIn:3600,userId:'customer-1',email:'buyer@example.test',role:'CUSTOMER'};
const vendor={...customer,userId:'vendor-1',email:'seller@example.test',role:'VENDOR'};

test.describe('authentication and session experience',()=>{
 test('returns unauthenticated visitors to the requested page after sign-in',async({page})=>{
  await page.route('**/api/v1/auth/login',route=>route.fulfill({json:envelope(customer)}));
  await page.goto('/orders');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Forders/);
  await page.getByLabel('Email address').fill('buyer@example.test');
  await page.getByRole('textbox',{name:'Password'}).fill('password123456');
  await page.getByRole('button',{name:'Sign in'}).click();
  await expect(page).toHaveURL(/\/orders$/);
  await expect(page.getByText('buyer@example.test')).toBeVisible();
 });

 test('routes a signed-in vendor to the vendor workspace when visiting login',async({page})=>{
  await page.route('**/api/v1/auth/login',route=>route.fulfill({json:envelope(vendor)}));
  await page.goto('/login');
  await page.getByLabel('Email address').fill('seller@example.test');
  await page.getByRole('textbox',{name:'Password'}).fill('password123456');
  await page.getByRole('button',{name:'Sign in'}).click();
  await expect(page).toHaveURL(/\/vendor$/);
 });

 test('supports password visibility and reports authentication failures accessibly',async({page})=>{
  await page.route('**/api/v1/auth/login',route=>route.fulfill({status:401,json:{success:false,data:null,error:{message:'Invalid credentials'}}}));
  await page.goto('/login');
  await page.getByLabel('Email address').fill('buyer@example.test');
  const password=page.getByRole('textbox',{name:'Password'});
  await password.fill('password123456');
  await page.getByRole('button',{name:'Show password'}).click();
  await expect(password).toHaveAttribute('type','text');
  await page.getByRole('button',{name:'Hide password'}).click();
  await page.getByRole('button',{name:'Sign in'}).click();
  await expect(page.getByRole('alert')).toContainText('Invalid credentials');
 });

 test('clears a rejected session and sends the user back to login',async({page})=>{
  await page.addInitScript(()=>{
   sessionStorage.setItem('markethub.access-token','expired-token');
   sessionStorage.setItem('markethub.session-user',JSON.stringify({userId:'customer-1',email:'buyer@example.test',role:'CUSTOMER'}));
  });
  await page.route('**/api/v1/customer/orders**',route=>route.fulfill({status:401,json:{message:'Unauthorized'}}));
  await page.goto('/account');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Faccount/);
  await expect.poll(()=>page.evaluate(()=>sessionStorage.getItem('markethub.access-token'))).toBeNull();
 });
});
