import {HttpErrorResponse,HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from '@angular/router';
import {catchError,throwError} from 'rxjs';
import {AuthService} from './auth.service';

export const authInterceptor:HttpInterceptorFn=(request,next)=>{
  const auth=inject(AuthService),router=inject(Router),token=auth.accessToken();
  const isApi=request.url.startsWith('/api/');
  const isAuthenticationRequest=/\/auth\/(login|register)(\?|$)/.test(request.url);
  const outgoing=token&&isApi?request.clone({setHeaders:{Authorization:`Bearer ${token}`}}):request;
  return next(outgoing).pipe(catchError((failure:unknown)=>{
    if(failure instanceof HttpErrorResponse&&failure.status===401&&token&&isApi&&!isAuthenticationRequest&&auth.accessToken()===token) {
      auth.logout();
      void router.navigate(['/login'],{queryParams:{returnUrl:router.url}});
    }
    return throwError(()=>failure);
  }));
};
