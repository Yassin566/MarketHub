import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { VendorStoreProfile, VendorStoreProfileRequest, VendorService } from '../../core/vendor/vendor.service';

@Component({
  selector: 'app-vendor-store-profile',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="vendor-form-panel" aria-labelledby="store-profile-title">
      <div class="vendor-section-heading"><div><span class="eyebrow">YOUR STOREFRONT</span><h2 id="store-profile-title">Store profile</h2><p>Manage the public identity and customer support details for your store.</p></div></div>
      @if(loading()){<p role="status">Loading store profile…</p>}
      @if(error()){<div class="vendor-alert" role="alert">{{error()}}</div>}
      @if(notice()){<div class="vendor-notice" role="status">{{notice()}}</div>}
      @if(!loading()){
        <form class="vendor-product-form" (ngSubmit)="save()">
          <label>Store name<input name="storeName" [(ngModel)]="storeName" required minlength="2" maxlength="160" /></label>
          <label>Store URL slug<input name="slug" [(ngModel)]="slug" required pattern="[a-z0-9]+(?:-[a-z0-9]+)*" maxlength="180" /></label>
          <label>Support email<input name="supportEmail" type="email" [(ngModel)]="supportEmail" maxlength="254" /></label>
          <label>Support phone<input name="supportPhone" type="tel" [(ngModel)]="supportPhone" maxlength="40" /></label>
          <label class="wide-field">Logo URL<input name="logoUrl" type="url" [(ngModel)]="logoUrl" maxlength="1000" placeholder="https://" /></label>
          <label class="wide-field">Store description<textarea name="description" [(ngModel)]="description" maxlength="2000" rows="4"></textarea></label>
          <div class="wide-field vendor-form-actions"><button class="primary-button" type="submit" [disabled]="saving()">{{saving()?'Saving…':'Save store profile'}}</button></div>
        </form>
      }
    </section>`,
})
export class VendorStoreProfileComponent {
  private readonly api = inject(VendorService);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  private exists = false;
  storeName = ''; slug = ''; description = ''; logoUrl = ''; supportEmail = ''; supportPhone = '';
  constructor() {
    this.api.storeProfile().subscribe({
      next: response => { this.populate(response.data); this.loading.set(false); },
      error: () => { this.loading.set(false); this.error.set('No store profile was found, or it could not be loaded. Complete the form to create your store profile.'); }
    });
  }
  private populate(profile: VendorStoreProfile): void {
    this.exists = true;
    this.storeName = profile.storeName;
    this.slug = profile.slug;
    this.description = profile.description ?? '';
    this.logoUrl = profile.logoUrl ?? '';
    this.supportEmail = profile.supportEmail ?? '';
    this.supportPhone = profile.supportPhone ?? '';
  }
  save(): void {
    const request: VendorStoreProfileRequest = {
      storeName: this.storeName.trim(), slug: this.slug.trim(),
      description: this.description.trim() || null, logoUrl: this.logoUrl.trim() || null,
      supportEmail: this.supportEmail.trim() || null, supportPhone: this.supportPhone.trim() || null
    };
    this.saving.set(true); this.error.set(''); this.notice.set('');
    this.api.saveStoreProfile(request, this.exists).subscribe({
      next: response => { this.populate(response.data); this.saving.set(false); this.notice.set('Store profile saved.'); },
      error: () => { this.saving.set(false); this.error.set('Store profile could not be saved. Check the fields and try again.'); }
    });
  }
}
