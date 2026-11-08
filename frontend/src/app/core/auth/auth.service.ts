import {computed,inject,Injectable,signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable,tap} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse,AuthResponse,SessionUser} from '../api/api.models';
const TOKEN_KEY='markethub.access-token'; const USER_KEY='markethub.session-user';
@Injectable({providedIn:'root'}) export class AuthService{
 private readonly http=inject(HttpClient); private readonly storedToken=sessionStorage.getItem(TOKEN_KEY); private readonly storedUser=this.readUser();
 readonly user=signal<SessionUser|null>(this.storedToken?this.storedUser:null); readonly isAuthenticated=computed(()=>this.user()!==null&&sessionStorage.getItem(TOKEN_KEY)!==null);
 login(email:string,password:string):Observable<ApiResponse<AuthResponse>>{return this.http.post<ApiResponse<AuthResponse>>(`${environment.apiBaseUrl}/auth/login`,{email,password}).pipe(tap(r=>this.saveSession(r.data)));}
 register(email:string,password:string):Observable<ApiResponse<AuthResponse>>{return this.http.post<ApiResponse<AuthResponse>>(`${environment.apiBaseUrl}/auth/register`,{email,password}).pipe(tap(r=>this.saveSession(r.data)));}
 logout():void{sessionStorage.removeItem(TOKEN_KEY);sessionStorage.removeItem(USER_KEY);this.user.set(null);}
 accessToken():string|null{return sessionStorage.getItem(TOKEN_KEY);}
 private saveSession(a:AuthResponse):void{sessionStorage.setItem(TOKEN_KEY,a.accessToken);const u:SessionUser={userId:a.userId,email:a.email,role:a.role};sessionStorage.setItem(USER_KEY,JSON.stringify(u));this.user.set(u);}
 private readUser():SessionUser|null{try{const v=sessionStorage.getItem(USER_KEY);return v?JSON.parse(v) as SessionUser:null;}catch{sessionStorage.removeItem(USER_KEY);return null;}}
}