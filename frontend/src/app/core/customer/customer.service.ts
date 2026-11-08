import {inject,Injectable} from '@angular/core';
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
export interface Shipment{id:string;carrier:string;trackingNumber:string;trackingUrl:string;shippedAt:string;estimatedDeliveryDate:string|null}
export interface Fulfilment{vendorOrderId:string;vendorName:string;status:string;shipment:Shipment|null}
export interface Payment{id:string;orderId:string;status:string;providerCode:string;amount:number;currency:string;createdAt:string}
@Injectable({providedIn:'root'})
export class CustomerService{
 private http=inject(HttpClient);
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
