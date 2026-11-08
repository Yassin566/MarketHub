import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../api/api.models';

export interface VendorProduct {
  id: string; name: string; slug: string; description: string | null;
  price: number; currency: string; status: string; version: number;
  createdAt: string; updatedAt: string;
}
export interface VendorProductRequest {
  name: string; slug: string; description: string | null;
  price: number; currency: string; expectedVersion?: number;
}
export interface VendorInventory {
  productId: string; productName: string; onHandQuantity: number;
  reservedQuantity: number; availableQuantity: number; version: number; updatedAt: string;
}
export interface InventoryAdjustment {
  id: string; productId: string; quantityDelta: number; quantityBefore: number;
  quantityAfter: number; reason: string; versionAfter: number; createdAt: string;
}
export interface VendorOrderLine {
  id: string; productId: string; productName: string; quantity: number;
  unitPrice: number; currency: string; lineTotal: number;
}
export interface VendorOrder {
  id: string; customerOrderId: string; status: string; currency: string;
  subtotal: number; createdAt: string; items: VendorOrderLine[];
  statusHistory: { id: string; previousStatus: string; newStatus: string; note: string; createdAt: string }[];
  shipment: { id: string; carrier: string; trackingNumber: string; trackingUrl: string | null; shippedAt: string; estimatedDeliveryDate: string | null } | null;
}
export interface VendorOrderPage { orders: VendorOrder[]; limit: number; offset: number; hasMore: boolean }

export interface VendorAnalyticsCurrency {
  currency: string; orderCount: number; paidOrderCount: number;
  processingOrderCount: number; fulfilledOrderCount: number;
  cancelledOrderCount: number; recognizedRevenue: number;
}
export interface VendorAnalyticsDaily {
  date: string; currency: string; orderCount: number; recognizedRevenue: number;
}
export interface VendorAnalyticsProduct {
  productId: string; productName: string; currency: string;
  unitsSold: number; recognizedRevenue: number;
}
export interface VendorAnalytics {
  from: string; to: string; currencies: VendorAnalyticsCurrency[];
  daily: VendorAnalyticsDaily[]; topProducts: VendorAnalyticsProduct[];
}

@Injectable({ providedIn: 'root' })
export class VendorService {
  private readonly http = inject(HttpClient);

  products(): Observable<ApiResponse<VendorProduct[]>> {
    return this.http.get<ApiResponse<VendorProduct[]>>('/api/v1/vendor/products');
  }
  createProduct(request: VendorProductRequest): Observable<ApiResponse<VendorProduct>> {
    return this.http.post<ApiResponse<VendorProduct>>('/api/v1/vendor/products', request);
  }
  updateProduct(id: string, request: VendorProductRequest): Observable<ApiResponse<VendorProduct>> {
    return this.http.put<ApiResponse<VendorProduct>>('/api/v1/vendor/products/' + encodeURIComponent(id), request);
  }
  archiveProduct(id: string): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>('/api/v1/vendor/products/' + encodeURIComponent(id));
  }
  inventory(): Observable<ApiResponse<VendorInventory[]>> {
    return this.http.get<ApiResponse<VendorInventory[]>>('/api/v1/vendor/inventory');
  }
  adjustInventory(productId: string, quantityDelta: number, reason: string, expectedVersion: number): Observable<ApiResponse<InventoryAdjustment>> {
    return this.http.post<ApiResponse<InventoryAdjustment>>(
      '/api/v1/vendor/inventory/' + encodeURIComponent(productId) + '/adjustments',
      { quantityDelta, reason, expectedVersion }
    );
  }
  orders(limit = 20, offset = 0): Observable<ApiResponse<VendorOrderPage>> {
    const params = new HttpParams().set('limit', limit).set('offset', offset);
    return this.http.get<ApiResponse<VendorOrderPage>>('/api/v1/vendor/orders', { params });
  }
  analytics(from: string, to: string): Observable<ApiResponse<VendorAnalytics>> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<ApiResponse<VendorAnalytics>>('/api/v1/vendor/analytics', { params });
  }
  updateOrderStatus(id: string, status: 'PROCESSING', note: string): Observable<ApiResponse<VendorOrder>> {
    return this.http.patch<ApiResponse<VendorOrder>>(
      '/api/v1/vendor/orders/' + encodeURIComponent(id) + '/status', { status, note }
    );
  }
  dispatchOrder(id: string, request: { carrier: string; trackingNumber: string; trackingUrl: string | null; estimatedDeliveryDate: string | null }): Observable<ApiResponse<VendorOrder>> {
    return this.http.post<ApiResponse<VendorOrder>>(
      '/api/v1/vendor/orders/' + encodeURIComponent(id) + '/shipment', request
    );
  }
}
