import {Component,inject,signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder,ReactiveFormsModule,Validators} from '@angular/forms';
import {ActivatedRoute,Router,RouterLink} from '@angular/router';
import {AuthService} from '../../core/auth/auth.service';
import {roleHome} from '../../core/auth/auth.guard';

@Component({selector:'mh-auth-page',standalone:true,imports:[CommonModule,ReactiveFormsModule,RouterLink],templateUrl:'./auth-page.component.html'})
export class AuthPageComponent {
  private readonly fb=inject(FormBuilder);
  private readonly auth=inject(AuthService);
  private readonly router=inject(Router);
  private readonly route=inject(ActivatedRoute);
  readonly isRegister=this.route.snapshot.data['mode']==='register';
  readonly loading=signal(false);
  readonly error=signal('');
  readonly showPassword=signal(false);
  readonly form=this.fb.nonNullable.group({
    email:['',[Validators.required,Validators.email,Validators.maxLength(254)]],
    password:['',[Validators.required,Validators.minLength(12),Validators.maxLength(72)]]
  });

  submit():void {
    if(this.form.invalid||this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    const {email,password}=this.form.getRawValue();
    const request=this.isRegister?this.auth.register(email,password):this.auth.login(email,password);
    request.subscribe({
      next:response=>{
        this.loading.set(false);
        const requested=this.route.snapshot.queryParamMap.get('returnUrl');
        const safeTarget=requested?.startsWith('/')&&!requested.startsWith('//')&&!requested.includes('\\')?requested:roleHome(response.data.role);
        void this.router.navigateByUrl(safeTarget);
      },
      error:(failure:{error?:{error?:{message?:string};message?:string}})=>{
        this.loading.set(false);
        this.error.set(failure.error?.error?.message||failure.error?.message||'We could not sign you in. Check your details and try again.');
      }
    });
  }
}
