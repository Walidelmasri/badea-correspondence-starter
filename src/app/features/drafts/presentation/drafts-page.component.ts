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
  CorrespondenceDraftPage,
} from '../../../shared/data-access/correspondence-api.service';

import {
  CorrespondenceWorkspaceComponent,
} from '../../../shared/ui/correspondence-workspace/correspondence-workspace.component';

const PAGE_SIZE = 25;

const COPY = {
  en: {
    loading: 'Loading drafts...',
    loadFailed: 'Unable to load drafts. Please try again.',
    retry: 'Retry',
    documentNumber: 'Document Number',
    createdAt: 'Created',
    modifiedAt: 'Last Modified',
    openDraft: 'Edit Draft',
    previous: 'Previous',
    next: 'Next',
    showing: 'Showing',
    of: 'of',
    noDocumentNumber: 'No document number',
  },

  ar: {
    loading: 'جارٍ تحميل المسودات...',
    loadFailed: 'تعذر تحميل المسودات. يرجى المحاولة مرة أخرى.',
    retry: 'إعادة المحاولة',
    documentNumber: 'رقم المستند',
    createdAt: 'تاريخ الإنشاء',
    modifiedAt: 'آخر تعديل',
    openDraft: 'تعديل المسودة',
    previous: 'السابق',
    next: 'التالي',
    showing: 'عرض',
    of: 'من',
    noDocumentNumber: 'لا يوجد رقم مستند',
  },
} as const;

@Component({
  selector: 'app-drafts-page',
  standalone: true,
  imports: [
    CorrespondenceWorkspaceComponent,
  ],
  templateUrl: './drafts-page.component.html',
  styleUrl: './drafts-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DraftsPageComponent implements OnInit {

  private readonly router = inject(Router);

  private readonly api =
    inject(CorrespondenceApiService);

  readonly translation =
    inject(TranslationService);

  readonly labels = computed(
    () => COPY[this.translation.language()],
  );

  readonly page =
    signal<CorrespondenceDraftPage | null>(null);

  readonly loading = signal(false);

  readonly error = signal(false);

  readonly selectedId =
    signal<string | null>(null);

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

    try {
      const page = await firstValueFrom(
        this.api.listDrafts(offset, PAGE_SIZE),
      );

      this.page.set(page);

      const previousSelection =
        this.selectedId();

      const selectionStillExists =
        page.items.some(
          (draft) =>
            draft.technicalId === previousSelection,
        );

      this.selectedId.set(
        selectionStillExists
          ? previousSelection
          : page.items[0]?.technicalId ?? null,
      );

    } catch {
      this.error.set(true);
      this.page.set(null);
      this.selectedId.set(null);

    } finally {
      this.loading.set(false);
    }
  }

  selectDraft(technicalId: string): void {
    this.selectedId.set(technicalId);
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
    // Role-aware Compose routing will replace this
    // once the backend exposes the authenticated role.
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
}