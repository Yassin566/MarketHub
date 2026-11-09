import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe} from '@angular/common';
import {Router,RouterLink} from '@angular/router';
import {CustomerService,CheckoutValidation,Order} from '../../core/customer/customer.service';

@Component({standalone:true,imports:[CurrencyPipe,RouterLink],templateUrl:'./checkout-page.component.html'})
export class CheckoutPageComponent {
  private readonly api=inject(CustomerService);
  private readonly router=inject(Router);
  readonly review=signal<CheckoutValidation|null>(null);
  readonly loading=signal(true);
  readonly busy=signal(false);
  readonly error=signal('');
  readonly notice=signal('');
  readonly step=signal<'review'|'order'|'payment'>('review');

  constructor(){this.refresh();}

  refresh():void {
    this.loading.set(true);this.error.set('');this.notice.set('');
    this.api.validate().subscribe({
      next:r=>{this.review.set(r.data);this.loading.set(false);},
      error:()=>{this.error.set('Could not validate checkout. Please sign in and try again.');this.loading.set(false);}
    });
  }

  placeOrder():void {
    if(this.busy()||!this.review()?.valid)return;
    this.busy.set(true);this.error.set('');this.notice.set('');
    const orderKey=this.getOrCreateKey('markethub.checkout.idempotency-key');
    this.api.checkout(orderKey).subscribe({
      next:r=>{
        sessionStorage.removeItem('markethub.checkout.idempotency-key');
        this.requestPayment(r.data);
      },
      error:()=>{
        this.busy.set(false);
        this.error.set('We could not confirm whether the order was created. Retry safely: the same request key will be reused to prevent a duplicate order.');
      }
    });
  }

  private requestPayment(order:Order):void {
    this.step.set('payment');
    const paymentKey=this.getOrCreateKey('markethub.payment.idempotency-key.'+order.id);
    this.api.payment(order.id,paymentKey).subscribe({
      next:p=>this.router.navigate(['/orders',order.id],{queryParams:{payment:p.data.status}}),
      error:()=>{
        this.busy.set(false);
        this.router.navigate(['/orders',order.id],{queryParams:{payment:'retry'}});
      }
    });
  }

  private getOrCreateKey(storageKey:string):string {
    let key=sessionStorage.getItem(storageKey);
    if(!key){key=crypto.randomUUID();sessionStorage.setItem(storageKey,key);}
    return key;
  }
}
