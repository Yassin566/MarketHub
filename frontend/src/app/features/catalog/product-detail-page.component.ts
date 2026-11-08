import {Component, inject, signal} from '@angular/core';
import {CurrencyPipe} from '@angular/common';
import {ActivatedRoute,RouterLink} from '@angular/router';
import {CatalogService,Product} from '../../core/catalog/catalog.service';
import {AuthService} from '../../core/auth/auth.service';
import {CustomerService} from '../../core/customer/customer.service';
@Component({standalone:true,imports:[CurrencyPipe,RouterLink],templateUrl: './product-detail-page.component.html'})
export class ProductDetailPageComponent{
 private catalog=inject(CatalogService);private route=inject(ActivatedRoute);private customer=inject(CustomerService);readonly auth=inject(AuthService);
 readonly product=signal<Product|null>(null);readonly loading=signal(true);readonly error=signal('');readonly quantity=signal(1);readonly adding=signal(false);readonly message=signal('');
 constructor(){const slug=this.route.snapshot.paramMap.get('slug')??'';this.catalog.get(slug).subscribe({next:r=>{this.product.set(r.data);this.loading.set(false);},error:()=>{this.error.set('This product is unavailable or has been removed.');this.loading.set(false);}});}
 addToCart(item:Product){const q=this.quantity();if(!Number.isInteger(q)||q<1||q>1000){this.message.set('Choose a quantity between 1 and 1000.');return;}this.adding.set(true);this.message.set('');this.customer.putItem(item.id,q).subscribe({next:()=>{this.adding.set(false);this.message.set('Added to your cart.');},error:()=>{this.adding.set(false);this.message.set('Could not add this product. Please check stock and try again.');}});}
}