import {Component, inject, signal} from '@angular/core';
import {CurrencyPipe} from '@angular/common';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {CatalogService, Product} from '../../core/catalog/catalog.service';

@Component({
  standalone: true,
  imports: [CurrencyPipe, RouterLink],
  template: `
    <section class="detail-shell">
      <a class="back-link" routerLink="/products">← Back to discover</a>
      @if (loading()) { <div class="catalog-state">Loading product...</div> }
      @else if (error()) { <div class="catalog-state catalog-error" role="alert">{{ error() }} <a routerLink="/products">Return to catalogue</a></div> }
      @else if (product(); as item) {
        <div class="detail-layout"><div class="detail-art"><span>m.</span><small>MARKETHUB FIND</small></div>
          <div class="detail-copy"><span class="eyebrow">INDEPENDENT STORE</span><h1>{{ item.name }}</h1>
            <div class="detail-price">{{ item.price | currency:item.currency:'symbol':'1.2-2' }}</div>
            <p>Discover this find from the MarketHub marketplace. Store and product details are provided by the seller.</p>
            <div class="detail-note">Secure checkout and cart features are coming in the next customer experience phase.</div>
            <a class="primary-button" routerLink="/products">Continue exploring <span>↗</span></a>
          </div>
        </div>
      }
    </section>`
})
export class ProductDetailPageComponent {
  private readonly catalog = inject(CatalogService);
  private readonly route = inject(ActivatedRoute);
  readonly product = signal<Product | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  constructor() {
    const slug = this.route.snapshot.paramMap.get('slug') ?? '';
    this.catalog.get(slug).subscribe({
      next: response => { this.product.set(response.data); this.loading.set(false); },
      error: () => { this.error.set('This product is unavailable or has been removed.'); this.loading.set(false); }
    });
  }
}
