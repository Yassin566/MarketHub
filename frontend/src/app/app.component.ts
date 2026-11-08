import {Component,inject} from '@angular/core'; import {RouterLink,RouterOutlet} from '@angular/router'; import {AuthService} from './core/auth/auth.service';
@Component({selector:'mh-root',standalone:true,imports:[RouterLink,RouterOutlet],templateUrl: './app.component.html'})
export class AppComponent{readonly auth=inject(AuthService);logout():void{this.auth.logout();}}