import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe,DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {CustomerService,Order,CustomerNotification,CustomerProfile} from '../../core/customer/customer.service';
@Component({
 standalone:true,imports:[CurrencyPipe,DatePipe,FormsModule,RouterLink],
 templateUrl:'./account-dashboard-page.component.html',
 styles:[`
 .account-shortcuts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:28px 0 40px}
 .account-shortcut{display:flex;flex-direction:column;gap:9px;padding:22px;border:1px solid var(--border-color,#e5e7eb);border-radius:16px;color:inherit;text-decoration:none;background:var(--surface,#fff)}
 .account-shortcut strong{font-size:1.1rem}.account-shortcut>span:last-child{font-size:.9rem;color:var(--muted,#667085)}
 .account-section-heading{display:flex;align-items:end;justify-content:space-between;gap:16px;margin:32px 0 16px}
 .account-section-heading h2{margin:5px 0 0}.account-notification{display:flex;justify-content:space-between;align-items:center;gap:18px;padding:18px 0;border-bottom:1px solid var(--border-color,#e5e7eb)}
 .account-notification.unread{border-left:3px solid var(--accent,#334155);padding-left:14px}.account-notification p{margin:6px 0}.account-notification time{font-size:.82rem;color:var(--muted,#667085)}
 .profile-panel{padding:24px;border:1px solid var(--border-color,#e5e7eb);border-radius:16px;background:var(--surface,#fff)}
 .profile-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.profile-field{display:flex;flex-direction:column;gap:7px}.profile-field label{font-weight:600;font-size:.9rem}.profile-field input{width:100%;box-sizing:border-box;padding:12px 13px;border:1px solid var(--border-color,#cbd5e1);border-radius:9px;background:var(--surface,#fff);color:inherit;font:inherit}
 .profile-actions{display:flex;align-items:center;gap:14px;margin-top:18px}.profile-feedback{font-size:.9rem}.profile-feedback.error{color:var(--danger,#b42318)}.profile-feedback.success{color:var(--success,#067647)}
 @media(max-width:720px){.account-shortcuts{grid-template-columns:1fr}.account-section-heading{align-items:start}.account-notification{align-items:start;flex-direction:column}.profile-grid{grid-template-columns:1fr}.profile-panel{padding:18px}}
 `]
})
export class AccountDashboardPageComponent {
 private readonly api=inject(CustomerService);
 readonly orders=signal<Order[]>([]);
 readonly notifications=signal<CustomerNotification[]>([]);
 readonly loading=signal(true);
 readonly error=signal('');
 readonly unreadCount=this.api.unreadCount;
 readonly profileSaving=signal(false);
 readonly profileMessage=signal('');
 readonly profileError=signal('');
 profileForm:CustomerProfile={firstName:'',lastName:'',phone:''};

 constructor(){this.load();}
 load():void {
  this.loading.set(true);this.error.set('');let completed=0;
  const finish=()=>{completed++;if(completed===3)this.loading.set(false);};
  this.api.orders(5,0).subscribe({next:r=>{this.orders.set(r.data.orders);finish();},error:()=>{this.error.set('Recent orders could not be loaded.');finish();}});
  this.api.notifications(5,0).subscribe({next:r=>{this.notifications.set(r.data.notifications);this.api.unreadCount.set(r.data.unreadCount);finish();},error:()=>{this.error.set('Recent notifications could not be loaded.');finish();}});
  this.api.profile().subscribe({next:r=>{this.profileForm={...r.data,phone:r.data.phone??''};finish();},error:()=>{this.profileError.set('Your profile details could not be loaded.');finish();}});
 }
 saveProfile():void {
  this.profileSaving.set(true);this.profileMessage.set('');this.profileError.set('');
  this.api.updateProfile(this.profileForm).subscribe({
   next:r=>{this.profileForm={...r.data,phone:r.data.phone??''};this.profileMessage.set('Your profile has been saved.');this.profileSaving.set(false);},
   error:()=>{this.profileError.set('Your profile could not be saved. Please try again.');this.profileSaving.set(false);}
  });
 }
}
