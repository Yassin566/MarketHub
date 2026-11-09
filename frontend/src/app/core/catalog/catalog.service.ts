import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {ApiResponse} from '../api/api.models';

export interface Product {
  id: string;
  vendorId: string;
  name: string;
  slug: string;
  price: number;
  currency: string;
  createdAt: string;
  averageRating: number | null;
  reviewCount: number;
}
export interface ProductPage {
  content: Product[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
@Injectable({providedIn: 'root'})
export class CatalogService {
  private readonly http = inject(HttpClient);
  list(page = 0, search = ''): Observable<ApiResponse<ProductPage>> {
    let params = new HttpParams().set('page', page).set('size', 12);
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<ApiResponse<ProductPage>>('/api/v1/products', {params});
  }
  get(slug: string): Observable<ApiResponse<Product>> {
    return this.http.get<ApiResponse<Product>>('/api/v1/products/' + encodeURIComponent(slug));
  }
}
