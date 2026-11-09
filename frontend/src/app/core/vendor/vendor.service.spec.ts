import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { VendorService } from './vendor.service';

const response = <T>(data: T) => ({ success: true, data, error: null, timestamp: '2026-01-01T00:00:00Z' });

describe('VendorService', () => {
  let service: VendorService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(VendorService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the signed-in vendor product catalogue', () => {
    service.products().subscribe();
    const request = http.expectOne('/api/v1/vendor/products');
    expect(request.request.method).toBe('GET');
    request.flush(response([]));
  });

  it('creates products without sending a browser-supplied version', () => {
    const body = { name: 'Desk Lamp', slug: 'desk-lamp', description: null, price: 45, currency: 'TND' };
    service.createProduct(body).subscribe();
    const request = http.expectOne('/api/v1/vendor/products');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush(response({ id: 'product-1', ...body, status: 'ACTIVE', version: 0, createdAt: '', updatedAt: '' }));
  });

  it('updates a product with its optimistic-lock version', () => {
    const body = { name: 'Desk Lamp', slug: 'desk-lamp', description: null, price: 48, currency: 'TND', expectedVersion: 4 };
    service.updateProduct('product/1', body).subscribe();
    const request = http.expectOne('/api/v1/vendor/products/product%2F1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.expectedVersion).toBe(4);
    request.flush(response({ id: 'product/1', ...body, status: 'ACTIVE', version: 5, createdAt: '', updatedAt: '' }));
  });

  it('archives a product using its vendor-scoped endpoint', () => {
    service.archiveProduct('product-2').subscribe();
    const request = http.expectOne('/api/v1/vendor/products/product-2');
    expect(request.request.method).toBe('DELETE');
    request.flush(response(null));
  });

  it('loads the vendor store profile', () => {
    service.storeProfile().subscribe();
    const request = http.expectOne('/api/v1/vendor/store');
    expect(request.request.method).toBe('GET');
    request.flush(response({ id: 'store-1', vendorId: 'vendor-1', storeName: 'MarketHub Store', slug: 'markethub-store', description: null, logoUrl: null, supportEmail: null, supportPhone: null, createdAt: '', updatedAt: '' }));
  });

  it('creates and updates the store profile through the matching endpoint', () => {
    const body = { storeName: 'MarketHub Store', slug: 'markethub-store', description: null, logoUrl: null, supportEmail: 'help@example.com', supportPhone: null };
    service.saveStoreProfile(body, false).subscribe();
    const create = http.expectOne('/api/v1/vendor/store');
    expect(create.request.method).toBe('POST');
    create.flush(response({ id: 'store-1', vendorId: 'vendor-1', ...body, createdAt: '', updatedAt: '' }));
    service.saveStoreProfile(body, true).subscribe();
    const update = http.expectOne('/api/v1/vendor/store');
    expect(update.request.method).toBe('PUT');
    update.flush(response({ id: 'store-1', vendorId: 'vendor-1', ...body, createdAt: '', updatedAt: '' }));
  });

  it('loads inventory adjustment history for a product', () => {
    service.adjustmentHistory('product-3').subscribe();
    const request = http.expectOne('/api/v1/vendor/inventory/product-3/adjustments');
    expect(request.request.method).toBe('GET');
    request.flush(response([]));
  });

  it('loads vendor inventory', () => {
    service.inventory().subscribe();
    const request = http.expectOne('/api/v1/vendor/inventory');
    expect(request.request.method).toBe('GET');
    request.flush(response([]));
  });

  it('submits inventory adjustments with the expected version', () => {
    service.adjustInventory('product-3', 12, 'RESTOCK', 7).subscribe();
    const request = http.expectOne('/api/v1/vendor/inventory/product-3/adjustments');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ quantityDelta: 12, reason: 'RESTOCK', expectedVersion: 7 });
    request.flush(response({ id: 'adjustment-1', productId: 'product-3', quantityDelta: 12, quantityBefore: 2, quantityAfter: 14, reason: 'RESTOCK', versionAfter: 8, createdAt: '' }));
  });

  it('requests paginated vendor orders', () => {
    service.orders(10, 20).subscribe();
    const request = http.expectOne('/api/v1/vendor/orders?limit=10&offset=20');
    expect(request.request.method).toBe('GET');
    request.flush(response({ orders: [], limit: 10, offset: 20, hasMore: false }));
  });

  it('starts order processing with an audit note', () => {
    service.updateOrderStatus('order-4', 'PROCESSING', 'Accepted').subscribe();
    const request = http.expectOne('/api/v1/vendor/orders/order-4/status');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'PROCESSING', note: 'Accepted' });
    request.flush(response({ id: 'order-4' }));
  });

  it('records shipment details through the dispatch endpoint', () => {
    const shipment = { carrier: 'Carrier', trackingNumber: 'TRACK-123', trackingUrl: 'https://tracking.example/123', estimatedDeliveryDate: '2026-12-01' };
    service.dispatchOrder('order-5', shipment).subscribe();
    const request = http.expectOne('/api/v1/vendor/orders/order-5/shipment');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(shipment);
    request.flush(response({ id: 'order-5' }));
  });
  it('requests vendor analytics for the selected date range', () => {
    service.analytics('2026-10-01', '2026-10-31').subscribe();
    const request = http.expectOne('/api/v1/vendor/analytics?from=2026-10-01&to=2026-10-31');
    expect(request.request.method).toBe('GET');
    request.flush(response({
      from: '2026-10-01', to: '2026-10-31',
      currencies: [{ currency: 'TND', orderCount: 3, paidOrderCount: 2, processingOrderCount: 1, fulfilledOrderCount: 1, cancelledOrderCount: 0, recognizedRevenue: 120 }],
      daily: [{ date: '2026-10-02', currency: 'TND', orderCount: 1, recognizedRevenue: 40 }],
      topProducts: [{ productId: 'product-1', productName: 'Desk Lamp', currency: 'TND', unitsSold: 2, recognizedRevenue: 80 }]
    }));
  });

});
