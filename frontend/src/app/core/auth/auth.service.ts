import {inject,Injectable,signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable,tap} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiResponse,AuthResponse,SessionUser} from '../api/api.models';

const TOKEN_KEY='markethub.access-token';
const USER_KEY='markethub.session-user';
const EXPIRY_KEY='markethub.session-expires-at';

@Injectable({providedIn:'root'})
export class AuthService {
  private readonly http=inject(HttpClient);
  readonly user=signal<SessionUser|null>(this.restoreUser());

  login(email:string,password:string):Observable<ApiResponse<AuthResponse>> {
    return this.http.post<ApiResponse<AuthResponse>>(`${environment.apiBaseUrl}/auth/login`,{email:email.trim(),password}).pipe(tap(r=>this.saveSession(r.data)));
  }

  register(email:string,password:string):Observable<ApiResponse<AuthResponse>> {
    return this.http.post<ApiResponse<AuthResponse>>(`${environment.apiBaseUrl}/auth/register`,{email:email.trim(),password}).pipe(tap(r=>this.saveSession(r.data)));
  }

  isAuthenticated():boolean { return this.hasValidSession(); }

  logout():void {
    this.clearStoredSession();
    this.user.set(null);
  }

  accessToken():string|null {
    return this.hasValidSession()?sessionStorage.getItem(TOKEN_KEY):null;
  }

  private hasValidSession():boolean {
    const token=sessionStorage.getItem(TOKEN_KEY),user=this.user();
    if(!token||!user) return false;
    const expiresAt=Number(sessionStorage.getItem(EXPIRY_KEY));
    if(Number.isFinite(expiresAt)&&expiresAt>0&&Date.now()>=expiresAt) {
      this.logout();
      return false;
    }
    return true;
  }

  private saveSession(auth:AuthResponse):void {
    sessionStorage.setItem(TOKEN_KEY,auth.accessToken);
    sessionStorage.setItem(USER_KEY,JSON.stringify({userId:auth.userId,email:auth.email,role:auth.role} satisfies SessionUser));
    if(Number.isFinite(auth.expiresIn)&&auth.expiresIn>0) sessionStorage.setItem(EXPIRY_KEY,String(Date.now()+auth.expiresIn*1000));
    else sessionStorage.removeItem(EXPIRY_KEY);
    this.user.set({userId:auth.userId,email:auth.email,role:auth.role});
  }

  private restoreUser():SessionUser|null {
    try {
      const token=sessionStorage.getItem(TOKEN_KEY),raw=sessionStorage.getItem(USER_KEY);
      if(!token||!raw) return null;
      const user=JSON.parse(raw) as Partial<SessionUser>;
      if(typeof user.userId!=='string'||typeof user.email!=='string'||typeof user.role!=='string') {
        this.clearStoredSession();
        return null;
      }
      const expiry=Number(sessionStorage.getItem(EXPIRY_KEY));
      if(Number.isFinite(expiry)&&expiry>0&&Date.now()>=expiry) {
        this.clearStoredSession();
        return null;
      }
      return user as SessionUser;
    } catch {
      this.clearStoredSession();
      return null;
    }
  }

  private clearStoredSession():void {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(EXPIRY_KEY);
  }
}
