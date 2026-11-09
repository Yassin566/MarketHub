import {Component, OnInit, inject, signal} from '@angular/core';
import {DatePipe, DecimalPipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpClient, HttpParams} from '@angular/common/http';
import {environment} from '../../../environments/environment';
import {ApiResponse} from '../../core/api/api.models';

interface ReconciliationItem {
  attemptId: string; paymentId: string; orderId: string; providerCode: string;
  providerReference: string | null; outcome: string; detail: string;
  observedPaymentStatus: string; createdAt: string;
}
interface ReconciliationPage {items: ReconciliationItem[]; total: number; limit: number; offset: number;}
interface PaymentReviewItem {
  paymentId: string; orderId: string; providerCode: string; providerReference: string | null;
  status: string; amount: number; currency: string; createdAt: string; updatedAt: string;
}
interface PaymentPage {items: PaymentReviewItem[]; total: number; limit: number; offset: number;}
interface ModerationReview {
  id: string; productId: string; productName: string; rating: number; reviewText: string | null;
  published: boolean; customerUserId: string; createdAt: string;
  lastAction: string | null; lastActorEmail: string | null; lastActionAt: string | null;
}
interface ModerationPage {items: ModerationReview[]; total: number; limit: number; offset: number;}
interface ResolutionView {
  id: string; paymentId: string; actorUserId: string; actorEmail: string; decision: string;
  previousPaymentStatus: string; resultingPaymentStatus: string;
  evidenceReference: string; reason: string; createdAt: string;
}

@Component({
  selector: 'mh-admin-portal-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule],
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
  readonly reviewPayments = signal<PaymentReviewItem[]>([]);
  readonly reviewTotal = signal(0);
  readonly reviewLoading = signal(false);
  readonly reviewError = signal('');
  readonly activePayment = signal<PaymentReviewItem | null>(null);
  readonly resolutionDialogOpen = signal(false);
  readonly confirming = signal(false);
  readonly submitting = signal(false);
  readonly resolutionError = signal('');
  readonly decision = signal<'CONFIRM_SUCCEEDED' | 'CONFIRM_FAILED'>('CONFIRM_SUCCEEDED');
  readonly evidenceReference = signal('');
  readonly reason = signal('');
  readonly resolution = signal<ResolutionView | null>(null);
  readonly moderationItems = signal<ModerationReview[]>([]);
  readonly moderationTotal = signal(0);
  readonly moderationLoading = signal(false);
  readonly moderationError = signal('');
  readonly moderationPublishedFilter = signal('all');
  readonly moderationRatingFilter = signal('all');
  readonly moderationBusyId = signal('');
  readonly moderationNotice = signal('');
  readonly pageCount = () => Math.max(1, Math.ceil(this.total() / this.limit));
  readonly currentPage = () => Math.floor(this.offset() / this.limit) + 1;

  ngOnInit(): void { this.load(); this.loadModeration(); }

  load(): void {
    this.loadReconciliation();
    this.loadReviewPayments();
  }

  private loadReconciliation(): void {
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
        this.error.set('Could not load payment reconciliation history. Check the API connection and your admin access.');
        this.loading.set(false);
      }
    });
  }

  private loadReviewPayments(): void {
    this.reviewLoading.set(true);
    this.reviewError.set('');
    const params = new HttpParams().set('status', 'REQUIRES_REVIEW').set('limit', 100).set('offset', 0);
    this.http.get<ApiResponse<PaymentPage>>(
      `${environment.apiBaseUrl}/admin/payments`, {params}
    ).subscribe({
      next: response => {
        this.reviewPayments.set(response.data?.items ?? []);
        this.reviewTotal.set(response.data?.total ?? 0);
        this.reviewLoading.set(false);
      },
      error: () => {
        this.reviewError.set('Could not load payments requiring review. Refresh or verify your admin access.');
        this.reviewLoading.set(false);
      }
    });
  }

  openResolution(payment: PaymentReviewItem): void {
    this.activePayment.set(payment);
    this.decision.set('CONFIRM_SUCCEEDED');
    this.evidenceReference.set('');
    this.reason.set('');
    this.resolutionError.set('');
    this.resolution.set(null);
    this.confirming.set(false);
    this.resolutionDialogOpen.set(true);
  }

  closeResolution(): void {
    if (this.submitting()) return;
    this.resolutionDialogOpen.set(false);
    this.activePayment.set(null);
    this.confirming.set(false);
  }

  prepareConfirmation(): void {
    if (!this.evidenceReference().trim() || this.evidenceReference().trim().length > 300) {
      this.resolutionError.set('Enter an evidence reference of 1–300 characters.');
      return;
    }
    if (!this.reason().trim() || this.reason().trim().length > 500) {
      this.resolutionError.set('Enter a reason of 1–500 characters.');
      return;
    }
    this.resolutionError.set('');
    this.confirming.set(true);
  }

  backToForm(): void { this.confirming.set(false); }

  applyResolution(): void {
    const payment = this.activePayment();
    if (!payment || this.submitting()) return;
    this.submitting.set(true);
    this.resolutionError.set('');
    this.http.post<ApiResponse<ResolutionView>>(
      `${environment.apiBaseUrl}/admin/payments/${payment.paymentId}/resolution`,
      {decision: this.decision(), evidenceReference: this.evidenceReference().trim(), reason: this.reason().trim()}
    ).subscribe({
      next: response => {
        this.resolution.set(response.data);
        this.confirming.set(false);
        this.submitting.set(false);
        this.loadReviewPayments();
      },
      error: error => {
        this.submitting.set(false);
        this.resolutionError.set(error.status === 409
          ? 'This payment can no longer be resolved in its current state. Refresh the payment list.'
          : error.error?.error?.message || 'The payment resolution could not be applied. No further action was taken.');
      }
    });
  }

  previous(): void {
    if (this.offset() > 0) {
      this.offset.set(Math.max(0, this.offset() - this.limit));
      this.loadReconciliation();
    }
  }
  next(): void {
    if (this.offset() + this.limit < this.total()) {
      this.offset.update(v => v + this.limit);
      this.loadReconciliation();
    }
  }
  trackByAttempt(_index: number, item: ReconciliationItem): string { return item.attemptId; }
}
