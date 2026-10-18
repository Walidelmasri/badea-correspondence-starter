import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';

import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import {
  TranslationService,
} from '../../../core/i18n/translation.service';

import {
  CorrespondenceApiService,
  CorrespondenceDetails,
  CorrespondenceDraftPage,
} from '../../../shared/data-access/correspondence-api.service';

import {
  CorrespondenceWorkspaceComponent,
} from '../../../shared/ui/correspondence-workspace/correspondence-workspace.component';

const PAGE_SIZE = 25;

@Component({
  selector: 'app-drafts-page',
  standalone: true,
  imports: [CorrespondenceWorkspaceComponent],
  templateUrl: './drafts-page.component.html',
  styleUrl: './drafts-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DraftsPageComponent implements OnInit {
  private readonly router = inject(Router);

  private readonly api =
    inject(CorrespondenceApiService);

  readonly translation = inject(TranslationService);

  // All translations come from the central translations.ts.
  readonly labels = computed(
    () => this.translation.text().drafts,
  );

  readonly page =
    signal<CorrespondenceDraftPage | null>(null);

  readonly loading = signal(false);
  readonly error = signal(false);

  readonly selectedId = signal<string | null>(null);

  readonly details =
    signal<CorrespondenceDetails | null>(null);

  readonly detailsLoading = signal(false);
  readonly detailsError = signal(false);

  private detailsRequestVersion = 0;

  readonly selectedDraft = computed(() => {
    const selectedId = this.selectedId();

    return this.page()?.items.find(
      (draft) => draft.technicalId === selectedId,
    ) ?? null;
  });

  readonly canGoBack = computed(() => {
    const page = this.page();

    return !!page &&
      page.offset > 0 &&
      !this.loading();
  });

  readonly canGoForward = computed(() => {
    const page = this.page();

    return !!page &&
      page.offset + page.limit < page.total &&
      !this.loading();
  });

  ngOnInit(): void {
    void this.loadPage(0);
  }

  async loadPage(offset: number): Promise<void> {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.error.set(false);

    // Invalidate any outstanding detail request.
    ++this.detailsRequestVersion;

    this.details.set(null);
    this.detailsLoading.set(false);
    this.detailsError.set(false);

    try {
      const page = await firstValueFrom(
        this.api.listDrafts(offset, PAGE_SIZE),
      );

      this.page.set(page);

      const previousSelection = this.selectedId();

      const selectionStillExists = page.items.some(
        (draft) =>
          draft.technicalId === previousSelection,
      );

      const nextSelection = selectionStillExists
        ? previousSelection
        : page.items[0]?.technicalId ?? null;

      this.selectedId.set(nextSelection);

      if (nextSelection) {
        void this.loadSelectedDetails(nextSelection);
      }
    } catch {
      this.error.set(true);
      this.page.set(null);
      this.selectedId.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  selectDraft(technicalId: string): void {
    if (this.selectedId() === technicalId) {
      return;
    }

    this.selectedId.set(technicalId);
    void this.loadSelectedDetails(technicalId);
  }

  retryDetails(): void {
    const technicalId = this.selectedId();

    if (technicalId) {
      void this.loadSelectedDetails(technicalId);
    }
  }

  private async loadSelectedDetails(
    technicalId: string,
  ): Promise<void> {
    const requestVersion = ++this.detailsRequestVersion;

    this.details.set(null);
    this.detailsError.set(false);
    this.detailsLoading.set(true);

    try {
      const result = await firstValueFrom(
        this.api.getDetails(technicalId),
      );

      // Prevent an old response from replacing a newer selection.
      if (
        requestVersion !== this.detailsRequestVersion ||
        this.selectedId() !== technicalId
      ) {
        return;
      }

      if (result.state !== 'DRAFT') {
        this.detailsError.set(true);
        return;
      }

      this.details.set(result);
    } catch {
      if (requestVersion === this.detailsRequestVersion) {
        this.detailsError.set(true);
      }
    } finally {
      if (requestVersion === this.detailsRequestVersion) {
        this.detailsLoading.set(false);
      }
    }
  }

  openDraft(technicalId: string): void {
    void this.router.navigate(
      ['/director/new'],
      {
        queryParams: {
          draft: technicalId,
        },
      },
    );
  }

  openCompose(): void {
    // Temporary until role-aware Compose routing is implemented.
    void this.router.navigate(['/director/new']);
  }

  previousPage(): void {
    const page = this.page();

    if (page && this.canGoBack()) {
      void this.loadPage(
        Math.max(0, page.offset - page.limit),
      );
    }
  }

  nextPage(): void {
    const page = this.page();

    if (page && this.canGoForward()) {
      void this.loadPage(
        page.offset + page.limit,
      );
    }
  }

  formatDate(value: string): string {
    const locale =
      this.translation.language() === 'ar'
        ? 'ar-SA-u-ca-gregory'
        : 'en-GB';

    return new Intl.DateTimeFormat(locale, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    }).format(new Date(value));
  }

  formatSize(bytes: number): string {
    return `${(bytes / (1024 * 1024)).toFixed(2)} MiB`;
  }
}