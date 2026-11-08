import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CustomerService } from './customer.service';

const emptyCart = { id: 'cart-1', status: 'ACTIVE', version: 1, items: [], createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' };
const response = <T>(data: T) => ({ success: true, data, error: null, timestamp: '2026-01-01T00:00:00Z' });

describe('CustomerService', () => {
  let service: CustomerService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CustomerService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the authenticated customer cart', () => {
    service.cart().subscribe(result => expect(result.data).toEqual(emptyCart));
    const request = http.expectOne('/api/v1/customer/cart');
    expect(request.request.method).toBe('GET');
    request.flush(response(emptyCart));
  });

  it('adds or updates a cart item using the product and quantity', () => {
    service.putItem('product-7', 3).subscribe();
    const request = http.expectOne('/api/v1/customer/cart/items');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ productId: 'product-7', quantity: 3 });
    request.flush(response(emptyCart));
  });

  it('validates checkout on the server without sending client totals', () => {
    service.validate().subscribe();
    const request = http.expectOne('/api/v1/customer/cart/checkout/validate');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(response({ cartId: 'cart-1', valid: true, lines: [], subtotal: 0, currency: null, issues: [] }));
  });

  it('creates checkout with the supplied idempotency key', () => {
    service.checkout('checkout-key-123').subscribe();
    const request = http.expectOne('/api/v1/customer/orders/checkout');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('checkout-key-123');
    expect(request.request.body).toEqual({});
    request.flush(response({ id: 'order-1', status: 'PENDING_PAYMENT', currency: 'TND', subtotal: 0, total: 0, expiresAt: null, createdAt: '2026-01-01T00:00:00Z', items: [] }));
  });

  it('requests order history with the requested pagination', () => {
    service.orders(10, 20).subscribe();
    const request = http.expectOne('/api/v1/customer/orders?limit=10&offset=20');
    expect(request.request.method).toBe('GET');
    request.flush(response({ orders: [], limit: 10, offset: 20, hasMore: false }));
  });

  it('URL-encodes order identifiers when loading fulfilments', () => {
    service.fulfilments('order/with space').subscribe();
    const request = http.expectOne('/api/v1/customer/orders/order%2Fwith%20space/fulfilments');
    expect(request.request.method).toBe('GET');
    request.flush(response([]));
  });
});
