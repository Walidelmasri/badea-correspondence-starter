import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';

import { Router } from '@angular/router';
import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { composeRouteForRole, MAILBOX_PAGE_SIZE } from '../../../shared/ui/real-mailbox/mailbox-navigation';
import { CorrespondenceDocumentPreviewComponent } from '../../../shared/ui/correspondence-document-preview/correspondence-document-preview.component';
import { OfficeCompletionEditorComponent } from '../../../shared/ui/office-completion/office-completion-editor.component';
import { firstValueFrom } from 'rxjs';
import { TarasolSecretaryApiService } from '../../tarasol-secretary/tarasol-secretary-api.service';

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

const PAGE_SIZE = MAILBOX_PAGE_SIZE;

@Component({
  selector: 'app-drafts-page',
  standalone: true,
  imports: [CorrespondenceWorkspaceComponent,
    CorrespondenceDocumentPreviewComponent, OfficeCompletionEditorComponent],
  templateUrl: './drafts-page.component.html',
  styleUrl: './drafts-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DraftsPageComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly session = inject(AuthSessionStore);
  readonly currentRole = computed(() =>
    this.session.effectiveRole());
  readonly previewDocument = signal<CorrespondenceDetails['documents'][number] | null>(null);

  private readonly api =
    inject(CorrespondenceApiService);
  private readonly secretaryApi = inject(TarasolSecretaryApiService);
  readonly secretaryMode = computed(() => this.session.secretaryMailbox());
  readonly secretaryUnitCode = computed(() => this.session.secretaryInboxUnitCode());

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
    this.previewDocument.set(null);
    this.detailsLoading.set(false);
    this.detailsError.set(false);

    try {
      const unit = this.secretaryUnitCode();
      if (this.secretaryMode() && !unit) throw new Error('No active secretary unit');
      const page = this.secretaryMode() && unit
        ? await firstValueFrom(this.secretaryApi.list(unit, 'mine', offset, PAGE_SIZE, 'drafts'))
        : await firstValueFrom(this.api.listDrafts(offset, PAGE_SIZE));

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
    this.previewDocument.set(null);
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
      const unit = this.secretaryUnitCode();
      if (this.secretaryMode() && !unit) throw new Error('No active secretary unit');
      const result = this.secretaryMode() && unit
        ? await this.loadSecretaryDraftDetails(unit, technicalId)
        : await firstValueFrom(this.api.getDetails(technicalId));

      // Prevent an old response from replacing a newer selection.
      if (
        requestVersion !== this.detailsRequestVersion ||
        this.selectedId() !== technicalId
      ) {
        return;
      }

      const expectedState = this.currentRole() === 'PRESIDENT'
        ? 'WITH_PRESIDENT' : this.currentRole() === 'PRESIDENT_OFFICE'
          ? 'WITH_PRESIDENT_OFFICE' : 'DRAFT';
      const executiveDraft = result.state === 'DRAFT'
        && !!result.executiveCompose
        && (this.currentRole() === 'PRESIDENT' || this.currentRole() === 'PRESIDENT_OFFICE');
      if (!executiveDraft && result.state !== expectedState) {
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

  private async loadSecretaryDraftDetails(unit: string, technicalId: string): Promise<CorrespondenceDetails> {
    const [draft, documentPage] = await Promise.all([
      firstValueFrom(this.secretaryApi.personalDraft(unit, technicalId)),
      firstValueFrom(this.secretaryApi.documents(unit, technicalId, 'mine')),
    ]);
    if (!['DRAFT', 'RETURNED'].includes(draft.approvalStatus)) {
      throw new Error('Only editable personal drafts belong in Drafts');
    }
    return {
      technicalId: draft.technicalId, state: 'DRAFT', subject: draft.subject,
      bodyText: draft.bodyText, documentNumber: draft.documentNumber,
      documentDate: draft.documentDate, sourceText: draft.sourceText,
      originDepartmentCode: draft.unitCode,
      createdByNameEnglish: this.session.currentUser()?.employeeProfile?.nameEnglish ?? '',
      createdByNameArabic: this.session.currentUser()?.employeeProfile?.nameArabic ?? null,
      documents: documentPage.items,
    };
  }

  openDraft(technicalId: string): void {
    const details = this.details();
    if (this.secretaryMode()) {
      void this.router.navigate(['/secretary/new'], { queryParams: { draft: technicalId } });
      return;
    }
    // Existing presidential reviews and Office completions keep their existing editors.
    if (details?.technicalId === technicalId && details.state === 'DRAFT'
        && details.executiveCompose
        && (this.currentRole() === 'PRESIDENT' || this.currentRole() === 'PRESIDENT_OFFICE')) {
      void this.router.navigate([this.currentRole() === 'PRESIDENT_OFFICE' ? '/office/new' : '/compose'],
        { queryParams: { draft: technicalId } });
    } else if (this.currentRole() === 'PRESIDENT') {
      void this.router.navigate(['/president-review', technicalId]);
    } else if (this.currentRole() === 'DIRECTOR') {
      void this.router.navigate(['/director/new'],
        { queryParams: { draft: technicalId } });
    }
    // Existing Office completion remains embedded in the selected detail.
  }

  openDocument(document: CorrespondenceDetails['documents'][number]): void {
    if (document.mimeType === 'application/pdf' && this.details())
      this.previewDocument.set(document);
  }

  closePreview(): void { this.previewDocument.set(null); }

  openCompose(): void {
    void this.router.navigate([this.secretaryMode()
      ? '/secretary/new' : composeRouteForRole(this.currentRole())]);
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