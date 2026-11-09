import {Component, OnInit, inject, signal} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {DatePipe, DecimalPipe} from '@angular/common';
import {environment} from '../../environments/environment';
import {ApiResponse} from '../../core/api/api.models';

interface ReconciliationItem {
  attemptId: string; paymentId: string; orderId: string; providerCode: string;
  providerReference: string | null; outcome: string; detail: string;
  observedPaymentStatus: string; createdAt: string;
}
interface ReconciliationPage {items: ReconciliationItem[]; total: number; limit: number; offset: number;}

@Component({
  selector: 'mh-admin-portal-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './admin-portal-page.component.html',
  styleUrl: './admin-portal-page.component.css'
})
export class AdminPortalPageComponent implements OnInit {
  private readonly http = inject(HttpClient);
  readonly items = signal<ReconciliationItem[]>([]);
  readonly total = signal(0);
  readonly offset = signal(0);
  readonly limit = 20;
  readonly loading = signal(false);
  readonly error = signal('');
  readonly pageCount = () => Math.max(1, Math.ceil(this.total() / this.limit));
  readonly currentPage = () => Math.floor(this.offset() / this.limit) + 1;

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    const params = new HttpParams().set('limit', this.limit).set('offset', this.offset());
    this.http.get<ApiResponse<ReconciliationPage>>(
      `${environment.apiBaseUrl}/admin/payments/reconciliation`, {params}
    ).subscribe({
      next: response => {
        this.items.set(response.data?.items ?? []);
        this.total.set(response.data?.total ?? 0);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load payment review history. Check the API connection and your admin access.');
        this.loading.set(false);
      }
    });
  }

  previous(): void { if (this.offset() > 0) { this.offset.set(Math.max(0, this.offset() - this.limit)); this.load(); } }
  next(): void { if (this.offset() + this.limit < this.total()) { this.offset.update(v => v + this.limit); this.load(); } }
  trackByAttempt(_index: number, item: ReconciliationItem): string { return item.attemptId; }
}
