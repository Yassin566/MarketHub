import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api.models';
interface VendorItem {vendorId:string;ownerUserId:string;ownerEmail:string;displayName:string;status:'APPROVED'|'SUSPENDED';createdAt:string;storeName:string|null;storeSlug:string|null;supportEmail:string|null;lastActorEmail:string|null;lastReason:string|null;lastActionAt:string|null;}
interface VendorPage {items:VendorItem[];total:number;limit:number;offset:number;}
@Component({selector:'mh-admin-vendor-management',standalone:true,imports:[DatePipe,FormsModule],templateUrl:'./admin-vendor-management.component.html',styleUrl:'./admin-vendor-management.component.css'})
export class AdminVendorManagementComponent {
 private readonly http=inject(HttpClient);readonly vendors=signal<VendorItem[]>([]);readonly total=signal(0);readonly loading=signal(false);readonly error=signal('');readonly notice=signal('');readonly status=signal('APPROVED');readonly search=signal('');readonly offset=signal(0);readonly limit=20;readonly selected=signal<VendorItem|null>(null);readonly reason=signal('');readonly busyId=signal('');readonly validationError=signal('');readonly pageCount=()=>Math.max(1,Math.ceil(this.total()/this.limit));readonly currentPage=()=>Math.floor(this.offset()/this.limit)+1;
 constructor(){this.load();}
 load():void{this.loading.set(true);this.error.set('');let params=new HttpParams().set('limit',this.limit).set('offset',this.offset());if(this.status()!=='ALL')params=params.set('status',this.status());if(this.search().trim())params=params.set('search',this.search().trim());this.http.get<ApiResponse<VendorPage>>(`${environment.apiBaseUrl}/admin/vendors`,{params}).subscribe({next:r=>{this.vendors.set(r.data?.items??[]);this.total.set(r.data?.total??0);this.loading.set(false);},error:e=>{this.error.set(e.error?.error?.message||'Vendor records could not be loaded. Check your admin access and retry.');this.loading.set(false);}});}
 applyFilters():void{this.offset.set(0);this.load();}
 changePage(delta:number):void{const next=this.offset()+delta*this.limit;if(next<0||next>=this.total())return;this.offset.set(next);this.load();}
 openAction(v:VendorItem):void{this.selected.set(v);this.reason.set('');this.validationError.set('');}
 closeAction():void{if(!this.busyId()){this.selected.set(null);this.validationError.set('');}}
 submitAction():void{const vendor=this.selected(),reason=this.reason().trim();if(!vendor||this.busyId())return;if(!reason||reason.length>500){this.validationError.set('Enter a reason between 1 and 500 characters.');return;}const suspend=vendor.status==='APPROVED',action=suspend?'suspension':'reactivation';this.busyId.set(vendor.vendorId);this.validationError.set('');this.notice.set('');this.http.patch<ApiResponse<VendorItem>>(`${environment.apiBaseUrl}/admin/vendors/${encodeURIComponent(vendor.vendorId)}/${action}`,{reason}).subscribe({next:r=>{if(r.data)this.vendors.update(items=>items.map(i=>i.vendorId===vendor.vendorId?r.data!:i));this.notice.set(`${vendor.displayName} ${suspend?'suspended':'reactivated'}. The reason was recorded.`);this.selected.set(null);this.busyId.set('');this.load();},error:e=>{this.busyId.set('');this.validationError.set(e.status===409?'The vendor status changed before this action completed. Refresh the list and try again.':e.error?.error?.message||'The vendor status could not be changed.');}});}
}
