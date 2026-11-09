import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { VendorStoreProfileComponent } from './vendor-store-profile.component';
import { InventoryAdjustment, VendorAnalytics, VendorAnalyticsDaily, VendorInventory, VendorOrder, VendorOrderPage, VendorProduct, VendorProductRequest, VendorService } from '../../core/vendor/vendor.service';

type VendorTab = 'overview' | 'analytics' | 'products' | 'inventory' | 'orders' | 'store';

@Component({
  standalone: true,
  imports: [CurrencyPipe, DatePipe, FormsModule, VendorStoreProfileComponent],
  templateUrl: './vendor-portal-page.component.html'
})
export class VendorPortalPageComponent {
  private readonly api = inject(VendorService);
  readonly activeTab = signal<VendorTab>('overview');
  readonly products = signal<VendorProduct[]>([]);
  readonly inventory = signal<VendorInventory[]>([]);
  readonly orders = signal<VendorOrder[]>([]);
  readonly ordersLoading = signal(false);
  readonly ordersError = signal('');
  readonly orderOffset = signal(0);
  readonly orderLimit = 20;
  readonly ordersHasMore = signal(false);
  readonly orderStatusFilter = signal('ALL');
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly productCount = computed(() => this.products().length);
  readonly lowStockCount = computed(() => this.inventory().filter(x => x.availableQuantity <= 5).length);
  readonly openOrderCount = computed(() => this.orders().filter(x => x.status === 'PAID' || x.status === 'PROCESSING').length);

  readonly analytics = signal<VendorAnalytics | null>(null);
  readonly analyticsLoading = signal(false);
  readonly analyticsError = signal('');
  analyticsFrom = this.dateInput(29);
  analyticsTo = this.dateInput(0);

  productName = '';
  productSlug = '';
  productDescription = '';
  productPrice = 0;
  productCurrency = 'TND';
  readonly editingProduct = signal<VendorProduct | null>(null);

  readonly adjustmentTarget = signal<VendorInventory | null>(null);
  readonly historyTarget = signal<VendorInventory | null>(null);
  readonly adjustmentHistory = signal<InventoryAdjustment[]>([]);
  readonly historyLoading = signal(false);
  readonly historyError = signal('');
  quantityDelta = 1;
  adjustmentReason = 'RESTOCK';

  readonly dispatchTarget = signal<VendorOrder | null>(null);
  carrier = '';
  trackingNumber = '';
  trackingUrl = '';
  estimatedDeliveryDate = '';
  readonly dispatchError = signal('');

  constructor() { this.load(); }

  private dateInput(daysAgo: number): string {
    const date = new Date();
    date.setUTCHours(0, 0, 0, 0);
    date.setUTCDate(date.getUTCDate() - daysAgo);
    return date.toISOString().slice(0, 10);
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    let pending = 2;
    const done = (): void => { pending -= 1; if (pending === 0) this.loading.set(false); };
    this.api.products().subscribe({
      next: r => { this.products.set(r.data); done(); },
      error: () => { this.error.set('Could not load your products. Check your vendor access and try again.'); done(); }
    });
    this.api.inventory().subscribe({
      next: r => { this.inventory.set(r.data); done(); },
      error: () => { this.error.set('Could not load inventory.'); done(); }
    });
    this.loadOrders(0, done);
    this.loadAnalytics();
  }

  loadAnalytics(): void {
    if (!this.analyticsFrom || !this.analyticsTo || this.analyticsFrom > this.analyticsTo) {
      this.analyticsError.set('Choose a valid date range.');
      return;
    }
    const start = new Date(this.analyticsFrom + 'T00:00:00Z');
    const end = new Date(this.analyticsTo + 'T00:00:00Z');
    const days = Math.floor((end.getTime() - start.getTime()) / 86_400_000) + 1;
    if (days > 366) {
      this.analyticsError.set('Choose a date range of 366 days or fewer.');
      return;
    }
    this.analyticsLoading.set(true);
    this.analyticsError.set('');
    this.api.analytics(this.analyticsFrom, this.analyticsTo).subscribe({
      next: response => {
        this.analytics.set(response.data);
        this.analyticsLoading.set(false);
      },
      error: () => {
        this.analyticsLoading.set(false);
        this.analyticsError.set('Analytics could not be loaded. Check the date range and try again.');
      }
    });
  }

  dailyForCurrency(currency: string): VendorAnalyticsDaily[] {
    return (this.analytics()?.daily ?? []).filter(item => item.currency === currency);
  }

  dailyBarWidth(item: VendorAnalyticsDaily): number {
    const values = this.dailyForCurrency(item.currency).map(row => Number(row.recognizedRevenue));
    const max = Math.max(0, ...values);
    return max > 0 ? Math.max(2, Number(item.recognizedRevenue) / max * 100) : 0;
  }

  setTab(tab: VendorTab): void {
    this.activeTab.set(tab);
    this.notice.set('');
    if (tab === 'analytics' && !this.analytics() && !this.analyticsLoading()) this.loadAnalytics();
  }

  editProduct(product: VendorProduct): void {
    this.editingProduct.set(product);
    this.productName = product.name;
    this.productSlug = product.slug;
    this.productDescription = product.description ?? '';
    this.productPrice = product.price;
    this.productCurrency = product.currency;
    this.activeTab.set('products');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  resetProductForm(): void {
    this.editingProduct.set(null);
    this.productName = '';
    this.productSlug = '';
    this.productDescription = '';
    this.productPrice = 0;
    this.productCurrency = 'TND';
  }

  saveProduct(): void {
    this.busy.set(true);
    this.error.set('');
    const current = this.editingProduct();
    const request: VendorProductRequest = {
      name: this.productName.trim(),
      slug: this.productSlug.trim(),
      description: this.productDescription.trim() || null,
      price: Number(this.productPrice),
      currency: this.productCurrency.trim().toUpperCase(),
      ...(current ? { expectedVersion: current.version } : {})
    };
    const action = current
      ? this.api.updateProduct(current.id, request)
      : this.api.createProduct(request);
    action.subscribe({
      next: () => {
        this.busy.set(false);
        this.notice.set(current ? 'Product updated.' : 'Product created.');
        this.resetProductForm();
        this.reloadProductsAndInventory();
      },
      error: () => { this.busy.set(false); this.error.set('Product could not be saved. Reload and check the details before retrying.'); }
    });
  }

  archiveProduct(product: VendorProduct): void {
    if (!window.confirm('Archive "' + product.name + '"? It will no longer appear in your active product list.')) return;
    this.busy.set(true);
    this.api.archiveProduct(product.id).subscribe({
      next: () => { this.busy.set(false); this.notice.set('Product archived.'); this.reloadProductsAndInventory(); },
      error: () => { this.busy.set(false); this.error.set('Product could not be archived.'); }
    });
  }

  viewAdjustmentHistory(item: VendorInventory): void {
    this.historyTarget.set(item);
    this.adjustmentHistory.set([]);
    this.historyError.set('');
    this.historyLoading.set(true);
    this.api.adjustmentHistory(item.productId).subscribe({
      next: response => {
        if (this.historyTarget()?.productId !== item.productId) return;
        this.adjustmentHistory.set(response.data);
        this.historyLoading.set(false);
      },
      error: () => {
        if (this.historyTarget()?.productId !== item.productId) return;
        this.historyLoading.set(false);
        this.historyError.set('Adjustment history could not be loaded. Try again.');
      }
    });
  }

  closeAdjustmentHistory(): void {
    this.historyTarget.set(null);
    this.historyError.set('');
    this.historyLoading.set(false);
  }

  private reloadAdjustmentHistory(productId: string): void {
    const target = this.historyTarget();
    if (!target || target.productId !== productId) return;
    this.historyLoading.set(true);
    this.historyError.set('');
    this.api.adjustmentHistory(productId).subscribe({
      next: response => {
        if (this.historyTarget()?.productId !== productId) return;
        this.adjustmentHistory.set(response.data);
        this.historyLoading.set(false);
      },
      error: () => {
        if (this.historyTarget()?.productId !== productId) return;
        this.historyLoading.set(false);
        this.historyError.set('Adjustment history could not be refreshed. Try again.');
      }
    });
  }

  openAdjustment(item: VendorInventory): void {
    this.adjustmentTarget.set(item);
    this.quantityDelta = 1;
    this.adjustmentReason = 'RESTOCK';
  }

  applyAdjustment(): void {
    const target = this.adjustmentTarget();
    if (!target || !Number.isInteger(Number(this.quantityDelta)) || Number(this.quantityDelta) === 0) return;
    this.busy.set(true);
    this.api.adjustInventory(target.productId, Number(this.quantityDelta), this.adjustmentReason, target.version).subscribe({
      next: () => {
        this.busy.set(false);
        this.adjustmentTarget.set(null);
        this.notice.set('Inventory adjustment saved.');
        this.reloadProductsAndInventory();
        this.reloadAdjustmentHistory(target.productId);
      },
      error: () => { this.busy.set(false); this.error.set('Inventory changed or the adjustment would use reserved stock. Reload and try again.'); this.reloadProductsAndInventory(); }
    });
  }

  setOrderStatusFilter(status: string): void {
    if (this.orderStatusFilter() === status) return;
    this.orderStatusFilter.set(status);
    this.loadOrders(0);
  }

  loadOrders(offset = this.orderOffset(), complete?: () => void): void {
    this.orderOffset.set(offset);
    this.ordersLoading.set(true);
    this.ordersError.set('');
    const status = this.orderStatusFilter();
    this.api.orders(this.orderLimit, offset, status === 'ALL' ? undefined : status).subscribe({
      next: (response: { data: VendorOrderPage }) => {
        this.orders.set(response.data.orders);
        this.orderOffset.set(response.data.offset);
        this.ordersHasMore.set(response.data.hasMore);
        this.ordersLoading.set(false);
        complete?.();
      },
      error: () => {
        this.ordersLoading.set(false);
        this.ordersError.set('Orders could not be loaded. Check your connection and try again.');
        complete?.();
      }
    });
  }

  previousOrdersPage(): void {
    this.loadOrders(Math.max(0, this.orderOffset() - this.orderLimit));
  }

  nextOrdersPage(): void {
    if (this.ordersHasMore()) this.loadOrders(this.orderOffset() + this.orderLimit);
  }

  startProcessing(order: VendorOrder): void {
    this.busy.set(true);
    this.api.updateOrderStatus(order.id, 'PROCESSING', 'Vendor accepted order and started processing.').subscribe({
      next: () => { this.busy.set(false); this.notice.set('Order moved to processing.'); this.reloadOrders(); },
      error: () => { this.busy.set(false); this.error.set('Order could not be updated. Reload its latest status and retry.'); this.reloadOrders(); }
    });
  }

  openDispatch(order: VendorOrder): void {
    this.dispatchTarget.set(order);
    this.carrier = '';
    this.trackingNumber = '';
    this.trackingUrl = '';
    this.estimatedDeliveryDate = '';
    this.dispatchError.set('');
  }

  dispatch(): void {
    const target = this.dispatchTarget();
    if (!target || this.busy()) return;
    const trackingUrl = this.trackingUrl.trim();
    if (trackingUrl) {
      try {
        const parsed = new URL(trackingUrl);
        if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') throw new Error('Unsupported protocol');
      } catch {
        this.dispatchError.set('Enter a valid HTTP or HTTPS tracking URL, or leave the field empty.');
        return;
      }
    }
    this.dispatchError.set('');
    this.busy.set(true);
    this.api.dispatchOrder(target.id, {
      carrier: this.carrier.trim(),
      trackingNumber: this.trackingNumber.trim(),
      trackingUrl: trackingUrl || null,
      estimatedDeliveryDate: this.estimatedDeliveryDate || null
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.dispatchTarget.set(null);
        this.notice.set('Shipment recorded and order marked fulfilled.');
        this.reloadOrders();
      },
      error: () => { this.busy.set(false); this.error.set('Shipment could not be recorded. Check the order status and shipment details.'); this.reloadOrders(); }
    });
  }

  private reloadProductsAndInventory(): void {
    this.api.products().subscribe({ next: r => this.products.set(r.data), error: () => this.error.set('Could not refresh products.') });
    this.api.inventory().subscribe({ next: r => this.inventory.set(r.data), error: () => this.error.set('Could not refresh inventory.') });
  }
  private reloadOrders(): void { this.loadOrders(this.orderOffset()); }
}
