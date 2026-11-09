import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe,DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {CustomerService,Order} from '../../core/customer/customer.service';
@Component({standalone:true,imports:[CurrencyPipe,DatePipe,RouterLink],templateUrl:'./orders-page.component.html'})
export class OrdersPageComponent {
 private readonly api=inject(CustomerService);
 readonly orders=signal<Order[]>([]);
 readonly loading=signal(true);
 readonly error=signal('');
 readonly offset=signal(0);
 readonly hasMore=signal(false);
 readonly limit=20;
 constructor(){this.load();}
 load():void {
  this.loading.set(true);this.error.set('');
  this.api.orders(this.limit,this.offset()).subscribe({
   next:r=>{this.orders.set(r.data.orders);this.hasMore.set(r.data.hasMore);this.loading.set(false);},
   error:()=>{this.error.set('We could not load your orders. Please try again.');this.loading.set(false);}
  });
 }
 previous():void{this.offset.set(Math.max(0,this.offset()-this.limit));this.load();}
 next():void{if(this.hasMore()){this.offset.set(this.offset()+this.limit);this.load();}}
}