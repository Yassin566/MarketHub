import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { VendorInventory, VendorOrder, VendorProduct, VendorProductRequest, VendorService } from '../../core/vendor/vendor.service';

type VendorTab = 'overview' | 'products' | 'inventory' | 'orders';

@Component({
  standalone: true,
  imports: [CurrencyPipe, DatePipe, FormsModule],
  templateUrl: './vendor-portal-page.component.html'
})
export class VendorPortalPageComponent {
  private readonly api = inject(VendorService);
  readonly activeTab = signal<VendorTab>('overview');
  readonly products = signal<VendorProduct[]>([]);
  readonly inventory = signal<VendorInventory[]>([]);
  readonly orders = signal<VendorOrder[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly productCount = computed(() => this.products().length);
  readonly lowStockCount = computed(() => this.inventory().filter(x => x.availableQuantity <= 5).length);
  readonly openOrderCount = computed(() => this.orders().filter(x => x.status === 'PAID' || x.status === 'PROCESSING').length);

  productName = '';
  productSlug = '';
  productDescription = '';
  productPrice = 0;
  productCurrency = 'TND';
  readonly editingProduct = signal<VendorProduct | null>(null);

  readonly adjustmentTarget = signal<VendorInventory | null>(null);
  quantityDelta = 1;
  adjustmentReason = 'RESTOCK';

  readonly dispatchTarget = signal<VendorOrder | null>(null);
  carrier = '';
  trackingNumber = '';
  trackingUrl = '';
  estimatedDeliveryDate = '';

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    let pending = 3;
    const done = (): void => { pending -= 1; if (pending === 0) this.loading.set(false); };
    this.api.products().subscribe({
      next: r => { this.products.set(r.data); done(); },
      error: () => { this.error.set('Could not load your products. Check your vendor access and try again.'); done(); }
    });
    this.api.inventory().subscribe({
      next: r => { this.inventory.set(r.data); done(); },
      error: () => { this.error.set('Could not load inventory.'); done(); }
    });
    this.api.orders(20, 0).subscribe({
      next: r => { this.orders.set(r.data.orders); done(); },
      error: () => { this.error.set('Could not load vendor orders.'); done(); }
    });
  }

  setTab(tab: VendorTab): void { this.activeTab.set(tab); this.notice.set(''); }

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
      },
      error: () => { this.busy.set(false); this.error.set('Inventory changed or the adjustment would use reserved stock. Reload and try again.'); this.reloadProductsAndInventory(); }
    });
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
  }

  dispatch(): void {
    const target = this.dispatchTarget();
    if (!target) return;
    this.busy.set(true);
    this.api.dispatchOrder(target.id, {
      carrier: this.carrier.trim(),
      trackingNumber: this.trackingNumber.trim(),
      trackingUrl: this.trackingUrl.trim() || null,
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
  private reloadOrders(): void {
    this.api.orders(20, 0).subscribe({ next: r => this.orders.set(r.data.orders), error: () => this.error.set('Could not refresh orders.') });
  }
}
