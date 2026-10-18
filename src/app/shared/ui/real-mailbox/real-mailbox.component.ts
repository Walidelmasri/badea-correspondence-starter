import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { TranslationService } from '../../../core/i18n/translation.service';
import {
  CorrespondenceApiService,
  CorrespondenceDetails,
  CorrespondenceMailboxBox,
  CorrespondenceMailboxPage,
} from '../../data-access/correspondence-api.service';
import { CorrespondenceWorkspaceComponent } from '../correspondence-workspace/correspondence-workspace.component';

const PAGE_SIZE = 25;

@Component({
  selector: 'app-real-mailbox',
  standalone: true,
  imports: [CorrespondenceWorkspaceComponent],
  templateUrl: './real-mailbox.component.html',
  styleUrl: './real-mailbox.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RealMailboxComponent implements OnInit {
  readonly box = input.required<CorrespondenceMailboxBox>();
  private readonly api = inject(CorrespondenceApiService);
  private readonly router = inject(Router);
  private readonly session = inject(AuthSessionStore);
  readonly translation = inject(TranslationService);

  readonly headings = computed(() => this.box() === 'inbox'
    ? this.translation.text().inbox
    : this.translation.text().sent);
  readonly labels = computed(() => this.translation.text().mailbox);

  readonly page = signal<CorrespondenceMailboxPage | null>(null);
  readonly loading = signal(false);
  readonly listError = signal(false);
  readonly selectedId = signal<string | null>(null);
  readonly details = signal<CorrespondenceDetails | null>(null);
  readonly detailsLoading = signal(false);
  readonly detailsError = signal(false);
  private detailVersion = 0;
  private pageVersion = 0;

  readonly selected = computed(() => this.page()?.items.find(
    item => item.technicalId === this.selectedId()) ?? null);
  readonly canGoBack = computed(() => !!this.page()
    && this.page()!.offset > 0 && !this.loading());
  readonly canGoForward = computed(() => !!this.page()
    && this.page()!.offset + this.page()!.limit < this.page()!.total
    && !this.loading());
  readonly canReview = computed(() => this.box() === 'inbox'
    && this.details()?.state === 'WITH_PRESIDENT'
    && this.session.currentUser()?.employeeProfile?.correspondenceRole === 'PRESIDENT');

  ngOnInit(): void {
    void this.loadPage(0);
  }

  async loadPage(offset: number): Promise<void> {
    if (this.loading()) return;
    const version = ++this.pageVersion;
    ++this.detailVersion;
    this.loading.set(true);
    this.listError.set(false);
    this.details.set(null);
    this.detailsError.set(false);
    try {
      const page = await firstValueFrom(this.api.listMailbox(this.box(), offset, PAGE_SIZE));
      if (version !== this.pageVersion) return;
      this.page.set(page);
      const id = page.items.find(item => item.technicalId === this.selectedId())?.technicalId
        ?? page.items[0]?.technicalId ?? null;
      this.selectedId.set(id);
      if (id) void this.loadDetails(id);
    } catch {
      if (version === this.pageVersion) {
        this.page.set(null);
        this.selectedId.set(null);
        this.listError.set(true);
      }
    } finally {
      if (version === this.pageVersion) this.loading.set(false);
    }
  }

  select(id: string): void {
    if (id === this.selectedId()) return;
    this.selectedId.set(id);
    void this.loadDetails(id);
  }

  retryDetails(): void {
    const id = this.selectedId();
    if (id) void this.loadDetails(id);
  }

  private async loadDetails(id: string): Promise<void> {
    const version = ++this.detailVersion;
    this.details.set(null);
    this.detailsError.set(false);
    this.detailsLoading.set(true);
    try {
      const result = await firstValueFrom(this.api.getDetails(id));
      if (version !== this.detailVersion || this.selectedId() !== id) return;
      this.details.set(result);
    } catch {
      if (version === this.detailVersion) this.detailsError.set(true);
    } finally {
      if (version === this.detailVersion) this.detailsLoading.set(false);
    }
  }

  previous(): void {
    const p = this.page();
    if (p && this.canGoBack()) void this.loadPage(Math.max(0, p.offset - p.limit));
  }

  next(): void {
    const p = this.page();
    if (p && this.canGoForward()) void this.loadPage(p.offset + p.limit);
  }

  openCompose(): void {
    void this.router.navigate(['/compose']);
  }

  openPresidentReview(): void {
    if (!this.canReview()) return;
    const id = this.selectedId();
    if (id) void this.router.navigate(['/president-review', id]);
  }

  statusLabel(state: CorrespondenceDetails['state']): string {
    switch (state) {
      case 'DRAFT': return this.labels().states.draft;
      case 'WITH_PRESIDENT_OFFICE': return this.labels().states.office;
      case 'WITH_PRESIDENT': return this.labels().states.president;
      case 'WITH_DIRECTOR': return this.labels().states.director;
      case 'ARCHIVED': return this.labels().states.archived;
    }
  }

  formatDate(value: string): string {
    const locale = this.translation.language() === 'ar' ? 'ar-SA-u-ca-gregory' : 'en-GB';
    return new Intl.DateTimeFormat(locale, {
      day: 'numeric', month: 'short', year: 'numeric',
    }).format(new Date(value));
  }

  formatSize(bytes: number): string {
    return `${(bytes / (1024 * 1024)).toFixed(2)} MiB`;
  }
}
