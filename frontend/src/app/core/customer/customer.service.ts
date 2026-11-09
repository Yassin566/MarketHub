import {inject,Injectable,signal} from '@angular/core';
import {HttpClient,HttpHeaders,HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {ApiResponse} from '../api/api.models';
export interface CartItem{productId:string;productName:string;quantity:number;unitPrice:number;currency:string;availableQuantity:number;productStatus:string;vendorStatus:string;updatedAt:string}
export interface Cart{ id:string;status:string;version:number;items:CartItem[];createdAt:string;updatedAt:string }
export interface CheckoutLine{productId:string;productName:string;quantity:number;unitPrice:number;currency:string;lineTotal:number;eligible:boolean;issue:string|null}
export interface CheckoutValidation{cartId:string;valid:boolean;lines:CheckoutLine[];subtotal:number;currency:string|null;issues:string[]}
export interface OrderLine{productId:string;vendorId:string;productName:string;quantity:number;unitPrice:number;currency:string;lineTotal:number}
export interface Order{id:string;status:string;currency:string;subtotal:number;total:number;expiresAt:string|null;createdAt:string;items:OrderLine[]}
export interface OrderPage{orders:Order[];limit:number;offset:number;hasMore:boolean}
export interface Shipment{id:string;carrier:string;trackingNumber:string;trackingUrl:string|null;shippedAt:string;estimatedDeliveryDate:string|null}
export interface Fulfilment{vendorOrderId:string;vendorName:string;status:string;shipment:Shipment|null}
export interface Payment{id:string;orderId:string;status:string;providerCode:string;amount:number;currency:string;createdAt:string}
export interface CustomerNotification{id:string;orderId:string|null;type:string;title:string;message:string;createdAt:string;readAt:string|null}
export interface NotificationPage{notifications:CustomerNotification[];limit:number;offset:number;hasMore:boolean;unreadCount:number}
export interface ProductReview{id:string;productId:string;rating:number;reviewText:string|null;createdAt:string;updatedAt:string}
export interface ProductReviewPage{reviews:ProductReview[];limit:number;offset:number;hasMore:boolean;averageRating:number|null;reviewCount:number}
@Injectable({providedIn:'root'})
export class CustomerService{
 private http=inject(HttpClient);
 readonly unreadCount=signal(0);
 myProductReview(productId:string):Observable<ApiResponse<ProductReview|null>>{return this.http.get<ApiResponse<ProductReview|null>>('/api/v1/customer/products/'+encodeURIComponent(productId)+'/reviews/mine');}
 productReviews(productId:string,limit=10,offset=0):Observable<ApiResponse<ProductReviewPage>>{const params=new HttpParams().set('limit',limit).set('offset',offset);return this.http.get<ApiResponse<ProductReviewPage>>('/api/v1/products/'+encodeURIComponent(productId)+'/reviews',{params});}
 createProductReview(productId:string,rating:number,reviewText:string):Observable<ApiResponse<ProductReview>>{return this.http.post<ApiResponse<ProductReview>>('/api/v1/customer/products/'+encodeURIComponent(productId)+'/reviews',{rating,reviewText});}
 updateProductReview(reviewId:string,rating:number,reviewText:string):Observable<ApiResponse<ProductReview>>{return this.http.put<ApiResponse<ProductReview>>('/api/v1/customer/reviews/'+encodeURIComponent(reviewId),{rating,reviewText});}
 deleteProductReview(reviewId:string):Observable<ApiResponse<void>>{return this.http.delete<ApiResponse<void>>('/api/v1/customer/reviews/'+encodeURIComponent(reviewId));}
 notifications(limit=20,offset=0,unreadOnly=false):Observable<ApiResponse<NotificationPage>>{const params=new HttpParams().set('limit',limit).set('offset',offset).set('unreadOnly',unreadOnly);return this.http.get<ApiResponse<NotificationPage>>('/api/v1/customer/notifications',{params});}
 markNotificationRead(id:string):Observable<ApiResponse<CustomerNotification>>{return this.http.patch<ApiResponse<CustomerNotification>>('/api/v1/customer/notifications/'+encodeURIComponent(id)+'/read',{});}
 markAllNotificationsRead():Observable<ApiResponse<number>>{return this.http.patch<ApiResponse<number>>('/api/v1/customer/notifications/read-all',{});}
 cart():Observable<ApiResponse<Cart>>{return this.http.get<ApiResponse<Cart>>('/api/v1/customer/cart');}
 putItem(productId:string,quantity:number):Observable<ApiResponse<Cart>>{return this.http.put<ApiResponse<Cart>>('/api/v1/customer/cart/items',{productId,quantity});}
 removeItem(productId:string):Observable<ApiResponse<Cart>>{return this.http.delete<ApiResponse<Cart>>('/api/v1/customer/cart/items/'+encodeURIComponent(productId));}
 validate():Observable<ApiResponse<CheckoutValidation>>{return this.http.post<ApiResponse<CheckoutValidation>>('/api/v1/customer/cart/checkout/validate',{});}
 checkout(key:string):Observable<ApiResponse<Order>>{return this.http.post<ApiResponse<Order>>('/api/v1/customer/orders/checkout',{}, {headers:new HttpHeaders({'Idempotency-Key':key})});}
 payment(orderId:string,key:string):Observable<ApiResponse<Payment>>{return this.http.post<ApiResponse<Payment>>('/api/v1/customer/orders/'+encodeURIComponent(orderId)+'/payments',{}, {headers:new HttpHeaders({'Idempotency-Key':key})});}
 orders(limit=20,offset=0):Observable<ApiResponse<OrderPage>>{const params=new HttpParams().set('limit',limit).set('offset',offset);return this.http.get<ApiResponse<OrderPage>>('/api/v1/customer/orders',{params});}
 order(id:string):Observable<ApiResponse<Order>>{return this.http.get<ApiResponse<Order>>('/api/v1/customer/orders/'+encodeURIComponent(id));}
 fulfilments(id:string):Observable<ApiResponse<Fulfilment[]>>{return this.http.get<ApiResponse<Fulfilment[]>>('/api/v1/customer/orders/'+encodeURIComponent(id)+'/fulfilments');}
}
