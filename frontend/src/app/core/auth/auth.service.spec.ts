import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController,provideHttpClientTesting} from '@angular/common/http/testing';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {AuthService} from './auth.service';

const response=(data:unknown)=>({success:true,data,error:null,timestamp:'2026-11-09T10:00:00Z'});
const auth={accessToken:'jwt-token',tokenType:'Bearer',expiresIn:3600,userId:'user-1',email:'buyer@example.test',role:'CUSTOMER'};

describe('AuthService',()=>{
 let service:AuthService,http:HttpTestingController;
 beforeEach(()=>{
  sessionStorage.clear();
  TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});
  service=TestBed.inject(AuthService);
  http=TestBed.inject(HttpTestingController);
 });
 afterEach(()=>{http.verify();vi.useRealTimers();});

 it('trims email and stores the returned session with its expiry',()=>{
  service.login(' buyer@example.test ','password123456').subscribe();
  const request=http.expectOne('/api/v1/auth/login');
  expect(request.request.body).toEqual({email:'buyer@example.test',password:'password123456'});
  request.flush(response(auth));
  expect(service.user()).toEqual({userId:'user-1',email:'buyer@example.test',role:'CUSTOMER'});
  expect(service.isAuthenticated()).toBe(true);
  expect(service.accessToken()).toBe('jwt-token');
  expect(Number(sessionStorage.getItem('markethub.session-expires-at'))).toBeGreaterThan(Date.now());
 });

 it('restores a valid session after service recreation',()=>{
  service.login('buyer@example.test','password123456').subscribe();
  http.expectOne('/api/v1/auth/login').flush(response(auth));
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});
  const restored=TestBed.inject(AuthService);
  expect(restored.user()?.email).toBe('buyer@example.test');
  expect(restored.isAuthenticated()).toBe(true);
 });

 it('clears an expired stored session during restoration',()=>{
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-11-09T10:00:00Z'));
  sessionStorage.setItem('markethub.access-token','expired-token');
  sessionStorage.setItem('markethub.session-user',JSON.stringify({userId:'user-1',email:'buyer@example.test',role:'CUSTOMER'}));
  sessionStorage.setItem('markethub.session-expires-at',String(Date.now()-1000));
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});
  const restored=TestBed.inject(AuthService);
  expect(restored.user()).toBeNull();
  expect(restored.isAuthenticated()).toBe(false);
  expect(sessionStorage.getItem('markethub.access-token')).toBeNull();
 });

 it('clears malformed stored user data instead of restoring a broken session',()=>{
  sessionStorage.setItem('markethub.access-token','token');
  sessionStorage.setItem('markethub.session-user','{invalid');
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});
  expect(TestBed.inject(AuthService).user()).toBeNull();
  expect(sessionStorage.getItem('markethub.access-token')).toBeNull();
 });

 it('clears all session data on logout',()=>{
  service.login('buyer@example.test','password123456').subscribe();
  http.expectOne('/api/v1/auth/login').flush(response(auth));
  service.logout();
  expect(service.user()).toBeNull();
  expect(service.isAuthenticated()).toBe(false);
  expect(sessionStorage.getItem('markethub.access-token')).toBeNull();
  expect(sessionStorage.getItem('markethub.session-expires-at')).toBeNull();
 });
});
