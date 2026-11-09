import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe,DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {CustomerService,Order,CustomerNotification} from '../../core/customer/customer.service';
@Component({
 standalone:true,imports:[CurrencyPipe,DatePipe,RouterLink],
 templateUrl:'./account-dashboard-page.component.html',
 styles:[`
 .account-shortcuts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:28px 0 40px}
 .account-shortcut{display:flex;flex-direction:column;gap:9px;padding:22px;border:1px solid var(--border-color,#e5e7eb);border-radius:16px;color:inherit;text-decoration:none;background:var(--surface,#fff)}
 .account-shortcut strong{font-size:1.1rem}.account-shortcut>span:last-child{font-size:.9rem;color:var(--muted,#667085)}
 .account-section-heading{display:flex;align-items:end;justify-content:space-between;gap:16px;margin:32px 0 16px}
 .account-section-heading h2{margin:5px 0 0}.account-notification{display:flex;justify-content:space-between;align-items:center;gap:18px;padding:18px 0;border-bottom:1px solid var(--border-color,#e5e7eb)}
 .account-notification.unread{border-left:3px solid var(--accent,#334155);padding-left:14px}.account-notification p{margin:6px 0}.account-notification time{font-size:.82rem;color:var(--muted,#667085)}
 @media(max-width:720px){.account-shortcuts{grid-template-columns:1fr}.account-section-heading{align-items:start}.account-notification{align-items:start;flex-direction:column}}
 `]
})
export class AccountDashboardPageComponent {
 private readonly api=inject(CustomerService);
 readonly orders=signal<Order[]>([]);
 readonly notifications=signal<CustomerNotification[]>([]);
 readonly loading=signal(true);
 readonly error=signal('');
 readonly unreadCount=this.api.unreadCount;
 constructor(){this.load();}
 load():void {
  this.loading.set(true);this.error.set('');let completed=0;
  const finish=()=>{completed++;if(completed===2)this.loading.set(false);};
  this.api.orders(5,0).subscribe({next:r=>{this.orders.set(r.data.orders);finish();},error:()=>{this.error.set('Recent orders could not be loaded.');finish();}});
  this.api.notifications(5,0).subscribe({next:r=>{this.notifications.set(r.data.notifications);this.api.unreadCount.set(r.data.unreadCount);finish();},error:()=>{this.error.set('Recent notifications could not be loaded.');finish();}});
 }
}