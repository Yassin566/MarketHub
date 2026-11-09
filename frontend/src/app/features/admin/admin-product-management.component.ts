import {Component, inject, signal} from '@angular/core';
import {DatePipe, DecimalPipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpClient, HttpParams} from '@angular/common/http';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../../core/api/api.models';

interface AdminProduct {
  id:string; vendorId:string; vendorName:string; name:string; slug:string;
  price:number; currency:string; status:string; createdAt:string;
  lastActorEmail:string|null; lastReason:string|null; lastActionAt:string|null;
}
interface ProductPage {items:AdminProduct[];total:number;limit:number;offset:number;}

@Component({
  selector:'mh-admin-product-management',
  standalone:true,
  imports:[DatePipe,DecimalPipe,FormsModule],
  template: `
  <section class="panel">
    <div class="panel-heading"><div><h2>Product catalogue management</h2><p>Search products and control public catalogue visibility. Products are never deleted.</p></div><span class="count">{{total()}} products</span></div>
    <div class="product-admin-filters">
      <label>Search products or vendors<input aria-label="Search products or vendors" [ngModel]="search()" (ngModelChange)="search.set($event)" (keyup.enter)="load()"></label>
      <label>Status<select aria-label="Product status" [ngModel]="status()" (ngModelChange)="status.set($event);load()"><option value="all">All statuses</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option value="DRAFT">Draft</option></select></label>
      <button type="button" class="secondary-button" (click)="load()" [disabled]="loading()">Search</button>
    </div>
    @if(error()){<div class="notice error" role="alert">{{error()}}</div>}
    @if(notice()){<div class="success-notice" role="status">{{notice()}}</div>}
    @if(loading()){<div class="state" role="status">Loading products…</div>}
    @else if(products().length===0){<div class="state empty"><strong>No products found</strong><span>Try another search or status filter.</span></div>}
    @else {
      <div class="table-wrap"><table>
        <thead><tr><th>Product</th><th>Vendor</th><th>Price</th><th>Status</th><th>Moderation history</th><th>Action</th></tr></thead>
        <tbody>@for(product of products();track product.id){
          <tr>
            <td><strong>{{product.name}}</strong><small>{{product.slug}}</small></td>
            <td>{{product.vendorName}}</td>
            <td>{{product.price|number:'1.2-2'}} {{product.currency}}</td>
            <td><span class="status-pill">{{product.status}}</span></td>
            <td>@if(product.lastReason){<small>{{product.lastReason}}</small><small>{{product.lastActorEmail}} · {{product.lastActionAt|date:'short'}}</small>}@else{<small>No moderation actions recorded</small>}</td>
            <td><button type="button" class="resolve-button" [disabled]="busyId()===product.id || product.status==='DRAFT'" (click)="change(product)">{{busyId()===product.id?'Saving…':product.status==='ACTIVE'?'Deactivate':'Activate'}}</button></td>
          </tr>
        }</tbody>
      </table></div>
      <div class="pagination"><span>Showing {{offset()+1}}–{{offset()+products().length}} of {{total()}}</span><div><button type="button" (click)="move(-20)" [disabled]="offset()===0">Previous</button><button type="button" (click)="move(20)" [disabled]="offset()+products().length>=total()">Next</button></div></div>
    }
  </section>`,
  styles:[`
    .product-admin-filters{display:flex;align-items:end;gap:1rem;flex-wrap:wrap;padding:1rem 0}
    .product-admin-filters label{display:grid;gap:.35rem;color:#64748b;font-size:.875rem}
    .product-admin-filters input,.product-admin-filters select{min-width:12rem;padding:.6rem .75rem;border:1px solid #cbd5e1;border-radius:.5rem;background:#fff;color:inherit}
  `]
})
export class AdminProductManagementComponent {
  private readonly http=inject(HttpClient);
  readonly products=signal<AdminProduct[]>([]); readonly total=signal(0); readonly offset=signal(0);
  readonly search=signal(''); readonly status=signal('all'); readonly loading=signal(false);
  readonly error=signal(''); readonly notice=signal(''); readonly busyId=signal('');
  constructor(){this.load();}
  load():void{
    this.loading.set(true);this.error.set('');
    let params=new HttpParams().set('limit',20).set('offset',this.offset());
    if(this.search().trim())params=params.set('search',this.search().trim());
    if(this.status()!=='all')params=params.set('status',this.status());
    this.http.get<ApiResponse<ProductPage>>(`${environment.apiBaseUrl}/admin/products`,{params}).subscribe({
      next:r=>{this.products.set(r.data?.items??[]);this.total.set(r.data?.total??0);this.loading.set(false);},
      error:()=>{this.error.set('Could not load products. Verify the API connection and admin access.');this.loading.set(false);}
    });
  }
  move(delta:number):void{this.offset.set(Math.max(0,this.offset()+delta));this.load();}
  change(product:AdminProduct):void{
    const target=product.status==='ACTIVE'?'deactivate':'activate';
    const reason=globalThis.prompt(`Enter the reason to ${target} this product (required, max 500 characters):`)?.trim()??'';
    if(!reason||reason.length>500)return;
    if(!globalThis.confirm(`Confirm ${target} for "${product.name}"? The product and order history will be retained.`))return;
    this.busyId.set(product.id);this.notice.set('');this.error.set('');
    this.http.patch<ApiResponse<AdminProduct>>(`${environment.apiBaseUrl}/admin/products/${product.id}/${target==='activate'?'activation':'deactivation'}`,{reason}).subscribe({
      next:r=>{if(r.data)this.products.update(items=>items.map(p=>p.id===r.data!.id?r.data!:p));this.notice.set(`Product ${target==='activate'?'activated':'deactivated'} and moderation history recorded.`);this.busyId.set('');},
      error:e=>{this.error.set(e.error?.error?.message||'The product status could not be changed.');this.busyId.set('');}
    });
  }
}
