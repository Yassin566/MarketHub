import {Routes} from '@angular/router';
import {authGuard,guestGuard} from './core/auth/auth.guard';
export const routes:Routes=[
 {path:'',loadComponent:()=>import('./features/home/home-page.component').then(m=>m.HomePageComponent)},
 {path:'products',loadComponent:()=>import('./features/catalog/catalog-page.component').then(m=>m.CatalogPageComponent)},
 {path:'products/:slug',loadComponent:()=>import('./features/catalog/product-detail-page.component').then(m=>m.ProductDetailPageComponent)},
 {path:'login',canActivate:[guestGuard],data:{mode:'login'},loadComponent:()=>import('./features/auth/auth-page.component').then(m=>m.AuthPageComponent)},
 {path:'register',canActivate:[guestGuard],data:{mode:'register'},loadComponent:()=>import('./features/auth/auth-page.component').then(m=>m.AuthPageComponent)},
 {path:'account',canActivate:[authGuard],loadComponent:()=>import('./features/home/home-page.component').then(m=>m.HomePageComponent)},
 {path:'**',redirectTo:''}
];