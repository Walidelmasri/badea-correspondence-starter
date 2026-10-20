import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom, forkJoin } from 'rxjs';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { TranslationService } from '../../../core/i18n/translation.service';
import {
  CorrespondenceApiService,
  CorrespondenceMailboxSummary,
} from '../../../shared/data-access/correspondence-api.service';
import { buildDashboardSnapshot, DashboardSnapshot } from './dashboard-metrics';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard-page.component.html',
  styleUrl: './dashboard-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPageComponent implements OnInit {
  private readonly api = inject(CorrespondenceApiService);
  private readonly session = inject(AuthSessionStore);
  readonly translation = inject(TranslationService);
  readonly labels = computed(() => this.translation.text().dashboard);
  readonly role = computed(() =>
    this.session.currentUser()?.employeeProfile?.correspondenceRole ?? null);
  readonly stats = signal<DashboardSnapshot | null>(null);
  readonly loading = signal(false);
  readonly failed = signal(false);

  readonly roleLabel = computed(() => {
    const role = this.role();
    return role ? this.labels().role[role] : '';
  });

  ngOnInit(): void {
    void this.refresh();
  }

  async refresh(): Promise<void> {
    if (this.loading()) return;
    this.loading.set(true);
    this.failed.set(false);
    this.stats.set(null);
    try {
      const pages = await firstValueFrom(forkJoin({
        all: this.api.listMailbox('inbox', 0, 7, 'all'),
        urgent: this.api.listMailbox('inbox', 0, 4, 'needs_action'),
        handled: this.api.listMailbox('inbox', 0, 1, 'handled'),
        archived: this.api.listMailbox('inbox', 0, 1, 'archived'),
        sent: this.api.listMailbox('sent', 0, 1),
        drafts: this.api.listDrafts(0, 1),
      }));
      this.stats.set(buildDashboardSnapshot(
        pages.all, pages.urgent, pages.handled,
        pages.archived, pages.sent, pages.drafts,
      ));
    } catch {
      // Incomplete data is not substituted with zero or made-up KPI values.
      this.failed.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  correspondenceStatus(item: CorrespondenceMailboxSummary): string {
    const labels = this.labels();
    if (item.state === 'ARCHIVED') return labels.archivedStatus;
    return item.needsAction ? labels.actionStatus : labels.handledStatus;
  }

  formattedDate(date: string): string {
    const timestamp = new Date(date);
    if (Number.isNaN(timestamp.getTime())) return '—';
    return new Intl.DateTimeFormat(
      this.translation.language() === 'ar' ? 'ar-SA' : 'en-GB',
      { day: 'numeric', month: 'short', year: 'numeric' },
    ).format(timestamp);
  }
}
