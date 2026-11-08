import {Routes} from '@angular/router';
import {authGuard,guestGuard,roleGuard} from './core/auth/auth.guard';
export const routes:Routes=[
 {path:'',loadComponent:()=>import('./features/home/home-page.component').then(m=>m.HomePageComponent)},
 {path:'products',loadComponent:()=>import('./features/catalog/catalog-page.component').then(m=>m.CatalogPageComponent)},
 {path:'products/:slug',loadComponent:()=>import('./features/catalog/product-detail-page.component').then(m=>m.ProductDetailPageComponent)},
 {path:'cart',canActivate:[authGuard,roleGuard(['CUSTOMER'])],loadComponent:()=>import('./features/customer/cart-page.component').then(m=>m.CartPageComponent)},
 {path:'checkout',canActivate:[authGuard,roleGuard(['CUSTOMER'])],loadComponent:()=>import('./features/customer/checkout-page.component').then(m=>m.CheckoutPageComponent)},
 {path:'orders',canActivate:[authGuard,roleGuard(['CUSTOMER'])],loadComponent:()=>import('./features/customer/orders-page.component').then(m=>m.OrdersPageComponent)},
 {path:'orders/:id',canActivate:[authGuard,roleGuard(['CUSTOMER'])],loadComponent:()=>import('./features/customer/order-detail-page.component').then(m=>m.OrderDetailPageComponent)},
 {path:'login',canActivate:[guestGuard],data:{mode:'login'},loadComponent:()=>import('./features/auth/auth-page.component').then(m=>m.AuthPageComponent)},
 {path:'register',canActivate:[guestGuard],data:{mode:'register'},loadComponent:()=>import('./features/auth/auth-page.component').then(m=>m.AuthPageComponent)},
 {path:'vendor',canActivate:[authGuard,roleGuard(['VENDOR'])],loadComponent:()=>import('./features/vendor/vendor-portal-page.component').then(m=>m.VendorPortalPageComponent)},
 {path:'account',redirectTo:'orders'},
 {path:'**',redirectTo:''}
];