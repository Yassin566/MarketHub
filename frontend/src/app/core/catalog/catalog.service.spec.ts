import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { CatalogService } from './catalog.service';

describe('CatalogService', () => {
  let service: CatalogService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CatalogService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests the first page with the default page size', () => {
    service.list().subscribe();
    const request = http.expectOne('/api/v1/products?page=0&size=12');
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 12, first: true, last: true }, error: null, timestamp: '2026-01-01T00:00:00Z' });
  });

  it('trims and sends the catalogue search term', () => {
    service.list(2, '  camera  ').subscribe();
    const request = http.expectOne('/api/v1/products?page=2&size=12&search=camera');
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: { content: [], totalElements: 0, totalPages: 0, number: 2, size: 12, first: false, last: true }, error: null, timestamp: '2026-01-01T00:00:00Z' });
  });

  it('URL-encodes product slugs before requesting details', () => {
    service.get('blue camera/large').subscribe();
    const request = http.expectOne('/api/v1/products/blue%20camera%2Flarge');
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: { id: 'p-1', vendorId: 'v-1', name: 'Blue Camera', slug: 'blue-camera', price: 50, currency: 'TND', createdAt: '2026-01-01T00:00:00Z' }, error: null, timestamp: '2026-01-01T00:00:00Z' });
  });
});
