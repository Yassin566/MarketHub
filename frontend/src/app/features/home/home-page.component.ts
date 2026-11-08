import {Component,inject} from '@angular/core';import {RouterLink} from '@angular/router';import {AuthService} from '../../core/auth/auth.service';
@Component({standalone:true,imports:[RouterLink],templateUrl: './home-page.component.html'})
export class HomePageComponent{readonly auth=inject(AuthService);}