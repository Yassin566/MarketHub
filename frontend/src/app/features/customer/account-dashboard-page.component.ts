import {Component,inject,signal} from '@angular/core';
import {CurrencyPipe,DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {CustomerService,Order,CustomerNotification} from '../../core/customer/customer.service';

@Component({
  standalone:true,
  imports:[CurrencyPipe,DatePipe,RouterLink],
  templateUrl:'./account-dashboard-page.component.html'
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
    this.loading.set(true);this.error.set('');
    let completed=0;
    const finish=()=>{completed++;if(completed===2)this.loading.set(false);};
    this.api.orders(5,0).subscribe({
      next:r=>{this.orders.set(r.data.orders);finish();},
      error:()=>{this.error.set('Recent orders could not be loaded.');finish();}
    });
    this.api.notifications(5,0).subscribe({
      next:r=>{this.notifications.set(r.data.notifications);this.api.unreadCount.set(r.data.unreadCount);finish();},
      error:()=>{this.error.set('Recent notifications could not be loaded.');finish();}
    });
  }
}