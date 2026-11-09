import {Component, inject, signal} from '@angular/core';
import {CurrencyPipe, DecimalPipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {CatalogService, Product} from '../../core/catalog/catalog.service';

@Component({
  standalone: true,
  imports: [CurrencyPipe, DecimalPipe, FormsModule, RouterLink],
  templateUrl: './catalog-page.component.html'
})
export class CatalogPageComponent {
  private readonly catalog = inject(CatalogService);
  private loadSequence = 0;
  readonly products = signal<Product[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly total = signal(0);
  readonly pages = signal(1);
  readonly page = signal(0);
  query = '';

  constructor() { this.load(); }

  load(): void {
    const request = ++this.loadSequence;
    this.loading.set(true);
    this.error.set('');
    this.catalog.list(this.page(), this.query).subscribe({
      next: response => {
        if (request !== this.loadSequence) return;
        this.products.set(response.data.content);
        this.total.set(response.data.totalElements);
        this.pages.set(Math.max(1, response.data.totalPages));
        this.loading.set(false);
      },
      error: () => {
        if (request !== this.loadSequence) return;
        this.error.set('We could not load the catalogue. Please try again.');
        this.loading.set(false);
      }
    });
  }

  searchProducts(): void {
    this.query = this.query.trim();
    this.page.set(0);
    this.load();
  }

  clearSearch(): void {
    this.query = '';
    this.page.set(0);
    this.load();
  }

  changePage(page: number): void {
    this.page.set(page);
    this.load();
    window.scrollTo({top: 0, behavior: 'smooth'});
  }
}
