import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient,withInterceptors,HttpClient} from '@angular/common/http';
import {HttpTestingController,provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter,Router} from '@angular/router';
import {afterEach,beforeEach,describe,expect,it} from 'vitest';
import {authInterceptor} from './auth.interceptor';
import {AuthService} from './auth.service';

@Component({standalone:true,template:''}) class EmptyComponent {}

describe('authInterceptor',()=>{
 let http:HttpTestingController,client:HttpClient,auth:AuthService,router:Router;
 beforeEach(()=>{
  sessionStorage.clear();
  TestBed.configureTestingModule({providers:[
   provideRouter([{path:'login',component:EmptyComponent},{path:'orders',component:EmptyComponent}]),
   provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting()
  ]});
  http=TestBed.inject(HttpTestingController);
  client=TestBed.inject(HttpClient);
  auth=TestBed.inject(AuthService);
  router=TestBed.inject(Router);
 });
 afterEach(()=>http.verify());

 const establishSession=()=>{
  sessionStorage.setItem('markethub.access-token','valid-token');
  sessionStorage.setItem('markethub.session-user',JSON.stringify({userId:'u1',email:'buyer@example.test',role:'CUSTOMER'}));
  auth.user.set({userId:'u1',email:'buyer@example.test',role:'CUSTOMER'});
 };

 it('attaches the bearer token to API requests',()=>{
  establishSession();
  client.get('/api/v1/customer/orders').subscribe();
  const request=http.expectOne('/api/v1/customer/orders');
  expect(request.request.headers.get('Authorization')).toBe('Bearer valid-token');
  request.flush({success:true,data:[],error:null,timestamp:'now'});
 });

 it('clears an expired server session and returns the user to login on 401',async()=>{
  establishSession();
  await router.navigateByUrl('/orders');
  client.get('/api/v1/customer/orders').subscribe({error:()=>undefined});
  http.expectOne('/api/v1/customer/orders').flush({message:'Unauthorized'},{status:401,statusText:'Unauthorized'});
  await Promise.resolve();
  expect(auth.user()).toBeNull();
  expect(sessionStorage.getItem('markethub.access-token')).toBeNull();
  expect(router.url).toContain('/login');
  expect(router.url).toContain('returnUrl');
 });

 it('does not clear the session for a bad login response',()=>{
  establishSession();
  client.post('/api/v1/auth/login',{email:'buyer@example.test',password:'wrong'}).subscribe({error:()=>undefined});
  http.expectOne('/api/v1/auth/login').flush({message:'Bad credentials'},{status:401,statusText:'Unauthorized'});
  expect(auth.user()?.email).toBe('buyer@example.test');
 });
});
