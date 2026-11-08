import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe,DatePipe} from '@angular/common';
import {ActivatedRoute,RouterLink} from '@angular/router';
import {CustomerService,Order,Fulfilment} from '../../core/customer/customer.service';
@Component({standalone:true,imports:[CurrencyPipe,DatePipe,RouterLink],templateUrl: './order-detail-page.component.html'})
export class OrderDetailPageComponent{private api=inject(CustomerService);private route=inject(ActivatedRoute);readonly order=signal<Order|null>(null);readonly fulfilments=signal<Fulfilment[]>([]);readonly loading=signal(true);readonly error=signal('');constructor(){const id=this.route.snapshot.paramMap.get('id')??'';this.api.order(id).subscribe({next:r=>{this.order.set(r.data);this.loading.set(false);this.api.fulfilments(id).subscribe({next:f=>this.fulfilments.set(f.data),error:()=>this.fulfilments.set([])});},error:()=>{this.error.set('Order not found or you do not have access to it.');this.loading.set(false);}});}}
