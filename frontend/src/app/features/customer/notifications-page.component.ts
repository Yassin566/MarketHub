import {Component,inject,signal} from '@angular/core';
import {DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {CustomerNotification,CustomerService} from '../../core/customer/customer.service';

@Component({
  standalone:true,
  imports:[DatePipe,RouterLink],
  templateUrl:'./notifications-page.component.html'
})
export class NotificationsPageComponent {
  private readonly api=inject(CustomerService);
  readonly notifications=signal<CustomerNotification[]>([]);
  readonly loading=signal(true);
  readonly error=signal('');
  readonly unreadOnly=signal(false);
  readonly offset=signal(0);
  readonly hasMore=signal(false);
  readonly limit=20;
  readonly unreadCount=this.api.unreadCount;

  constructor(){this.load();}

  load():void {
    this.loading.set(true);this.error.set('');
    this.api.notifications(this.limit,this.offset(),this.unreadOnly()).subscribe({
      next:r=>{this.notifications.set(r.data.notifications);this.hasMore.set(r.data.hasMore);this.api.unreadCount.set(r.data.unreadCount);this.loading.set(false);},
      error:()=>{this.error.set('We could not load your notifications. Please try again.');this.loading.set(false);}
    });
  }

  setFilter(unread:boolean):void{if(this.unreadOnly()===unread)return;this.unreadOnly.set(unread);this.offset.set(0);this.load();}
  previous():void{this.offset.set(Math.max(0,this.offset()-this.limit));this.load();}
  next():void{if(this.hasMore()){this.offset.set(this.offset()+this.limit);this.load();}}

  markRead(notification:CustomerNotification):void {
    if(notification.readAt)return;
    this.api.markNotificationRead(notification.id).subscribe({
      next:r=>{this.notifications.update(items=>items.map(item=>item.id===r.data.id?r.data:item));this.api.unreadCount.update(n=>Math.max(0,n-1));},
      error:()=>this.error.set('This notification could not be marked as read. Please try again.')
    });
  }

  markAllRead():void {
    if(this.unreadCount()===0)return;
    this.api.markAllNotificationsRead().subscribe({
      next:()=>{this.notifications.update(items=>items.map(item=>({...item,readAt:item.readAt??new Date().toISOString()})));this.api.unreadCount.set(0);},
      error:()=>this.error.set('Notifications could not be marked as read. Please try again.')
    });
  }
}
