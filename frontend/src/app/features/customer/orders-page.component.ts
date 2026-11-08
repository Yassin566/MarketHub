import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe,DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {CustomerService,Order} from '../../core/customer/customer.service';
@Component({standalone:true,imports:[CurrencyPipe,DatePipe,RouterLink],templateUrl: './orders-page.component.html'})
export class OrdersPageComponent{private api=inject(CustomerService);readonly orders=signal<Order[]>([]);readonly loading=signal(true);readonly error=signal('');constructor(){this.api.orders().subscribe({next:r=>{this.orders.set(r.data.orders);this.loading.set(false);},error:()=>{this.error.set('Sign in with your customer account to view order history.');this.loading.set(false);}});}}
