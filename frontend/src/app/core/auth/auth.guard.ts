import {inject} from '@angular/core';
import {CanActivateFn,Router} from '@angular/router';
import {AuthService} from './auth.service';

export const roleHome=(role:string|undefined):string=>{
  if(role==='VENDOR') return '/vendor';
  if(role==='ADMIN'||role==='SUPER_ADMIN') return '/admin';
  if(role==='CUSTOMER') return '/account';
  return '/';
};

export const authGuard:CanActivateFn=(_route,state)=>{
  const auth=inject(AuthService);
  return auth.isAuthenticated()?true:inject(Router).createUrlTree(['/login'],{queryParams:{returnUrl:state.url}});
};

export const guestGuard:CanActivateFn=()=>{
  const auth=inject(AuthService);
  return auth.isAuthenticated()?inject(Router).parseUrl(roleHome(auth.user()?.role)) : true;
};

export const roleGuard=(roles:string[]):CanActivateFn=>()=>{
  const auth=inject(AuthService),user=auth.user();
  if(!auth.isAuthenticated()) return inject(Router).createUrlTree(['/login']);
  return user&&roles.includes(user.role)?true:inject(Router).parseUrl(roleHome(user?.role));
};
