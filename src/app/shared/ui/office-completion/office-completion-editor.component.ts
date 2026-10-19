import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CorrespondenceService } from '../../../core/correspondence/correspondence.service';
import { CorrespondenceDepartment } from '../../../core/correspondence/correspondence.model';
import { TranslationService } from '../../../core/i18n/translation.service';
import { PresidentReviewDraftState, normalizePresidentReviewDraftState } from '../../../features/president-review/domain/president-review-state.model';
import { CorrespondenceApiService, OfficeCompletionDraft } from '../../data-access/correspondence-api.service';

function empty(): OfficeCompletionDraft {
  return { schemaVersion: 1, selectedRecipientIds: [], selectedCopyRecipientIds: [],
    selectedActionCodes: [], otherRecipient: '', otherAction: '',
    typedExplanation: '', officeNotes: '' };
}
const ACTIONS = [
  'required-action', 'study-and-opinion', 'review-and-advise',
  'discuss-directly', 'direct-response', 'prepare-summary',
  'information-and-action', 'follow-up', 'information-and-file',
  'act-per-phone-call', 'prepare-response', 'apology',
  'return-later', 'other',
] as const;

@Component({
  selector: 'app-office-completion-editor', standalone: true,
  templateUrl: './office-completion-editor.component.html',
  styleUrl: './office-completion-editor.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OfficeCompletionEditorComponent {
  readonly technicalId = input.required<string>();
  private readonly api = inject(CorrespondenceApiService);
  private readonly correspondence = inject(CorrespondenceService);
  readonly translation = inject(TranslationService);
  readonly labels = computed(() => this.translation.text().officeCompletion);
  readonly departments = signal<readonly CorrespondenceDepartment[]>([]);
  readonly president = signal<PresidentReviewDraftState | null>(null);
  readonly office = signal<OfficeCompletionDraft | null>(null);
  readonly actions = ACTIONS;
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly locked = signal(false);
  readonly error = signal(false);
  readonly notice = signal<'saved' | 'failed' | 'conflict' | 'uncertain' | null>(null);
  private revision = 0;
  private presidentRevision = 0;
  private requestVersion = 0;

  constructor() {
    effect(() => {
      const id = this.technicalId();
      void this.load(id);
    });
  }

  async load(id: string): Promise<void> {
    const version = ++this.requestVersion;
    this.loading.set(true); this.error.set(false);
    this.office.set(null); this.president.set(null);
    this.notice.set(null); this.locked.set(false);
    try {
      const [president, office, departments] = await Promise.all([
        firstValueFrom(this.correspondence.getPresidentReview<PresidentReviewDraftState>(id)),
        firstValueFrom(this.api.getOfficeCompletion(id)),
        firstValueFrom(this.correspondence.getDepartments()),
      ]);
      if (version !== this.requestVersion) return;
      if (office.presidentReviewRevision !== president.revision) {
        throw new Error('President review revision mismatch');
      }
      this.president.set(normalizePresidentReviewDraftState(president.state));
      this.office.set(office.state ?? empty());
      this.revision = office.revision;
      this.presidentRevision = office.presidentReviewRevision;
      this.departments.set(departments);
    } catch {
      if (version === this.requestVersion) this.error.set(true);
    } finally {
      if (version === this.requestVersion) this.loading.set(false);
    }
  }

  name(code: string): string {
    const dept = this.departments().find(d => d.code === code);
    if (!dept) return code;
    return this.translation.language() === 'ar'
      ? (dept.nameArabic || dept.nameEnglish) : dept.nameEnglish;
  }
  presidentHas(field: 'selectedRecipientIds' | 'selectedCopyRecipientIds', id: string): boolean {
    return this.president()?.[field].includes(id) ?? false;
  }
  officeHas(field: 'selectedRecipientIds' | 'selectedCopyRecipientIds' | 'selectedActionCodes', id: string): boolean {
    return this.office()?.[field].includes(id) ?? false;
  }
  toggle(field: 'selectedRecipientIds' | 'selectedCopyRecipientIds' | 'selectedActionCodes', id: string): void {
    if (this.saving() || this.locked()) return;
    this.office.update(current => {
      if (!current) return null;
      const values = current[field];
      const next = values.includes(id) ? values.filter(item => item !== id) : [...values, id];
      return { ...current, [field]: next };
    });
    this.notice.set(null);
  }
  change(field: 'otherRecipient' | 'otherAction' | 'typedExplanation' | 'officeNotes', event: Event): void {
    if (this.saving() || this.locked()) return;
    const value = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.office.update(current => current ? { ...current, [field]: value } : null);
    this.notice.set(null);
  }
  actionLabel(code: typeof ACTIONS[number]): string {
    return this.labels().actions[code];
  }

  async save(): Promise<void> {
    const id = this.technicalId(); const state = this.office();
    if (!state || this.loading() || this.saving() || this.locked()) return;
    const version = this.requestVersion;
    this.saving.set(true); this.notice.set(null);
    try {
      const saved = await firstValueFrom(this.api.saveOfficeCompletion(
        id, this.revision, this.presidentRevision, state));
      if (version !== this.requestVersion) return;
      this.revision = saved.revision;
      this.office.set(saved.state);
      this.notice.set('saved');
    } catch (error: unknown) {
      if (version !== this.requestVersion) return;
      if (error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500)) {
        // The write may have committed. Never auto-retry a non-idempotent revision save.
        this.locked.set(true); this.notice.set('uncertain');
      } else if (error instanceof HttpErrorResponse && error.status === 409) {
        this.locked.set(true); this.notice.set('conflict');
      } else {
        this.notice.set('failed');
      }
    } finally {
      if (version === this.requestVersion) this.saving.set(false);
    }
  }
}
