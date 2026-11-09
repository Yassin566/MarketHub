import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe,DatePipe} from '@angular/common';
import {ActivatedRoute,RouterLink} from '@angular/router';
import {CustomerService,Order,Fulfilment} from '../../core/customer/customer.service';

@Component({standalone:true,imports:[CurrencyPipe,DatePipe,RouterLink],templateUrl:'./order-detail-page.component.html'})
export class OrderDetailPageComponent {
  private readonly api=inject(CustomerService);
  private readonly route=inject(ActivatedRoute);
  readonly order=signal<Order|null>(null);
  readonly fulfilments=signal<Fulfilment[]>([]);
  readonly fulfilmentsLoading=signal(false);
  readonly fulfilmentsError=signal('');
  readonly loading=signal(true);
  readonly error=signal('');
  readonly paymentStatus=signal(this.route.snapshot.queryParamMap.get('payment')??'');
  readonly retryingPayment=signal(false);
  readonly paymentError=signal('');

  constructor(){
    const id=this.route.snapshot.paramMap.get('id')??'';
    this.api.order(id).subscribe({
      next:r=>{this.order.set(r.data);this.loading.set(false);this.loadFulfilments(id);},
      error:()=>{this.error.set('Order not found or you do not have access to it.');this.loading.set(false);}
    });
  }

  loadFulfilments(orderId=this.order()?.id??''):void {
    if(!orderId)return;
    this.fulfilmentsLoading.set(true);
    this.fulfilmentsError.set('');
    this.api.fulfilments(orderId).subscribe({
      next:response=>{this.fulfilments.set(response.data);this.fulfilmentsLoading.set(false);},
      error:()=>{this.fulfilmentsLoading.set(false);this.fulfilmentsError.set('Shipment details could not be loaded. Try again.');}
    });
  }

  retryPayment():void {
    const order=this.order();
    if(!order||order.status!=='PENDING_PAYMENT'||this.retryingPayment())return;
    this.retryingPayment.set(true);this.paymentError.set('');
    const keyName='markethub.payment.idempotency-key.'+order.id;
    let key=sessionStorage.getItem(keyName);
    if(!key){key=crypto.randomUUID();sessionStorage.setItem(keyName,key);}
    this.api.payment(order.id,key).subscribe({
      next:r=>{this.paymentStatus.set(r.data.status);this.retryingPayment.set(false);this.paymentError.set('');},
      error:()=>{this.retryingPayment.set(false);this.paymentError.set('The payment request could not be confirmed. You can retry safely; the same request key is reused.');}
    });
  }
}
