import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe} from '@angular/common';
import {Router,RouterLink} from '@angular/router';
import {CustomerService,CheckoutValidation} from '../../core/customer/customer.service';
@Component({standalone:true,imports:[CurrencyPipe,RouterLink],templateUrl: './checkout-page.component.html'})
export class CheckoutPageComponent{
 private api=inject(CustomerService);private router=inject(Router);readonly review=signal<CheckoutValidation|null>(null);readonly loading=signal(true);readonly busy=signal(false);readonly error=signal('');
 constructor(){this.api.validate().subscribe({next:r=>{this.review.set(r.data);this.loading.set(false);},error:()=>{this.error.set('Could not validate checkout. Please sign in and try again.');this.loading.set(false);}});}
 placeOrder(){this.busy.set(true);this.error.set('');this.api.checkout(crypto.randomUUID()).subscribe({next:r=>{const order=r.data;this.api.payment(order.id,crypto.randomUUID()).subscribe({next:p=>this.router.navigate(['/orders',order.id],{queryParams:{payment:p.data.status}}),error:()=>this.router.navigate(['/orders',order.id],{queryParams:{payment:'not-requested'}})});},error:()=>{this.error.set('Order creation failed. Your cart has not been cleared unless checkout succeeded. Refresh to validate current stock and prices.');this.busy.set(false);}});}
}
