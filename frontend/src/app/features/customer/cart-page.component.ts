import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {CustomerService,Cart} from '../../core/customer/customer.service';
@Component({standalone:true,imports:[CurrencyPipe,RouterLink],templateUrl: './cart-page.component.html'})
export class CartPageComponent{
 private api=inject(CustomerService);readonly cart=signal<Cart|null>(null);readonly loading=signal(true);readonly busy=signal(false);readonly error=signal('');
 constructor(){this.load();}
 load(){this.api.cart().subscribe({next:r=>{this.cart.set(r.data);this.loading.set(false);},error:()=>{this.error.set('Sign in with a customer account to view your cart.');this.loading.set(false);}});}
 setQuantity(id:string,event:Event){const quantity=Number((event.target as HTMLInputElement).value);if(!Number.isInteger(quantity)||quantity<1||quantity>1000){this.error.set('Quantity must be between 1 and 1000.');return;}this.busy.set(true);this.api.putItem(id,quantity).subscribe({next:r=>{this.cart.set(r.data);this.busy.set(false);this.error.set('');},error:()=>{this.error.set('Could not update this item.');this.busy.set(false);}});}
 remove(id:string){this.busy.set(true);this.api.removeItem(id).subscribe({next:r=>{this.cart.set(r.data);this.busy.set(false);},error:()=>{this.error.set('Could not remove this item.');this.busy.set(false);}});}
 review(){this.busy.set(true);this.api.validate().subscribe({next:r=>{this.busy.set(false);if(r.data.valid)location.assign('/checkout');else this.error.set(r.data.issues.join(' · ')||'Please review your cart.');},error:()=>{this.busy.set(false);this.error.set('Checkout validation failed. Please try again.');}});}
}
