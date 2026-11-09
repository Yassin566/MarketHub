import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe} from '@angular/common';
import {Router,RouterLink} from '@angular/router';
import {CustomerService,Cart} from '../../core/customer/customer.service';

@Component({standalone:true,imports:[CurrencyPipe,RouterLink],templateUrl:'./cart-page.component.html'})
export class CartPageComponent {
  private readonly api=inject(CustomerService);
  private readonly router=inject(Router);
  readonly cart=signal<Cart|null>(null);
  readonly loading=signal(true);
  readonly busy=signal(false);
  readonly busyProduct=signal('');
  readonly error=signal('');
  readonly notice=signal('');

  constructor(){this.load();}

  load():void {
    this.loading.set(true);
    this.error.set('');
    this.api.cart().subscribe({
      next:r=>{this.cart.set(r.data);this.loading.set(false);},
      error:()=>{this.error.set('We could not load your cart. Please sign in again or retry.');this.loading.set(false);}
    });
  }

  setQuantity(productId:string,event:Event):void {
    const quantity=Number((event.target as HTMLInputElement).value);
    if(!Number.isInteger(quantity)||quantity<1||quantity>1000){
      this.error.set('Quantity must be a whole number between 1 and 1000.');
      const previous=this.cart()?.items.find(item=>item.productId===productId)?.quantity;
      (event.target as HTMLInputElement).value=String(previous??1);
      return;
    }
    this.busy.set(true);this.busyProduct.set(productId);this.error.set('');this.notice.set('');
    this.api.putItem(productId,quantity).subscribe({
      next:r=>{this.cart.set(r.data);this.busy.set(false);this.busyProduct.set('');this.notice.set('Cart quantity updated.');},
      error:()=>{this.busy.set(false);this.busyProduct.set('');this.error.set('The quantity could not be updated. Your existing cart is unchanged. Please try again.');}
    });
  }

  remove(productId:string):void {
    this.busy.set(true);this.busyProduct.set(productId);this.error.set('');this.notice.set('');
    this.api.removeItem(productId).subscribe({
      next:r=>{this.cart.set(r.data);this.busy.set(false);this.busyProduct.set('');this.notice.set('Item removed from your cart.');},
      error:()=>{this.busy.set(false);this.busyProduct.set('');this.error.set('The item could not be removed. Please try again.');}
    });
  }

  review():void {
    this.busy.set(true);this.error.set('');this.notice.set('');
    this.api.validate().subscribe({
      next:r=>{
        this.busy.set(false);
        if(r.data.valid) this.router.navigate(['/checkout']);
        else {this.error.set(r.data.issues.join(' · ')||'Please review your cart.');}
      },
      error:()=>{this.busy.set(false);this.error.set('Checkout validation is temporarily unavailable. Your cart is unchanged; try again.');}
    });
  }
}
