import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api.models';

interface VendorApplication {
  vendorId: string;
  ownerUserId: string;
  displayName: string;
  status: string;
  createdAt: string;
  rejectionReason: string | null;
}

@Component({
  selector: 'mh-admin-vendor-applications',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './admin-vendor-applications.component.html',
  styleUrl: './admin-vendor-applications.component.css'
})
export class AdminVendorApplicationsComponent {
  private readonly http = inject(HttpClient);
  readonly applications = signal<VendorApplication[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly busyId = signal('');
  readonly selected = signal<VendorApplication | null>(null);
  readonly dialogMode = signal<'approve' | 'reject' | null>(null);
  readonly rejectionReason = signal('');
  readonly validationError = signal('');

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.http.get<ApiResponse<VendorApplication[]>>(
      `${environment.apiBaseUrl}/admin/vendor-applications`
    ).subscribe({
      next: response => {
        this.applications.set(response.data ?? []);
        this.loading.set(false);
      },
      error: error => {
        this.error.set(error.error?.error?.message
          || 'Vendor applications could not be loaded. Check your admin access and try again.');
        this.loading.set(false);
      }
    });
  }

  openApprove(application: VendorApplication): void {
    this.selected.set(application);
    this.dialogMode.set('approve');
    this.validationError.set('');
  }

  openReject(application: VendorApplication): void {
    this.selected.set(application);
    this.dialogMode.set('reject');
    this.rejectionReason.set('');
    this.validationError.set('');
  }

  closeDialog(): void {
    if (this.busyId()) return;
    this.dialogMode.set(null);
    this.selected.set(null);
    this.validationError.set('');
  }

  submitDecision(): void {
    const application = this.selected();
    const mode = this.dialogMode();
    if (!application || !mode || this.busyId()) return;
    const reason = this.rejectionReason().trim();
    if (mode === 'reject' && (!reason || reason.length > 500)) {
      this.validationError.set('Enter a rejection reason between 1 and 500 characters.');
      return;
    }

    this.busyId.set(application.vendorId);
    this.validationError.set('');
    this.error.set('');
    this.notice.set('');
    const url = `${environment.apiBaseUrl}/admin/vendor-applications/${encodeURIComponent(application.vendorId)}/${mode}`;
    const request = mode === 'approve'
      ? this.http.post<ApiResponse<VendorApplication>>(url, {})
      : this.http.post<ApiResponse<VendorApplication>>(url, { reason });

    request.subscribe({
      next: response => {
        const updated = response.data;
        this.applications.update(items => items.filter(item => item.vendorId !== application.vendorId));
        this.notice.set(mode === 'approve'
          ? `${updated?.displayName ?? application.displayName} approved. Vendor access is now active.`
          : `${updated?.displayName ?? application.displayName} rejected. The decision and reason were recorded.`);
        this.dialogMode.set(null);
        this.selected.set(null);
        this.busyId.set('');
      },
      error: error => {
        this.busyId.set('');
        this.validationError.set(error.status === 409
          ? 'This application has already been reviewed. Refresh the list to see its latest status.'
          : error.error?.error?.message || 'The application could not be updated. No decision was recorded.');
      }
    });
  }
}
