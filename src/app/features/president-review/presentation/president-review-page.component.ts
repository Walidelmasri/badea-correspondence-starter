import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';

import { TranslationService } from '../../../core/i18n/translation.service';

import { PdfWorkspaceComponent } from '../../../shared/ui/pdf-workspace/pdf-workspace.component';
import {
  createEmptyPresidentExplanationInkDocument,
  PresidentExplanationInkDocument,
} from '../domain/president-explanation.model';
import {
  CorrespondenceSummaryComponent,
  CorrespondenceSummaryViewModel,
} from '../../../shared/ui/correspondence-summary/correspondence-summary.component';
import {
  PresidentExplanationInkComponent,
} from './president-explanation-ink/president-explanation-ink.component';
type ExplanationMode = 'pen' | 'keyboard';

type RequiredActionCode =
  | 'required-action'
  | 'study-and-opinion'
  | 'review-and-advise'
  | 'discuss-directly'
  | 'direct-response'
  | 'prepare-summary'
  | 'information-and-action'
  | 'follow-up'
  | 'information-and-file'
  | 'act-per-phone-call'
  | 'prepare-response'
  | 'apology'
  | 'return-later'
  | 'other';

interface PresidentReviewAttachment {
  readonly id: string;
  readonly filename: string;
  readonly nameAr: string;
  readonly nameEn: string;
  readonly sizeLabel: string;
  readonly src: string;
}

interface RecipientOption {
  readonly id: string;
  readonly nameAr: string;
  readonly nameEn: string;
}

interface RequiredActionOption {
  readonly code: RequiredActionCode;
  readonly labelAr: string;
  readonly labelEn: string;
}

@Component({
  selector: 'app-president-review-page',
  standalone: true,
  imports: [
    PdfWorkspaceComponent,
    PresidentExplanationInkComponent,
    CorrespondenceSummaryComponent,
  ],
  templateUrl: './president-review-page.component.html',
  styleUrl: './president-review-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PresidentReviewPageComponent {
  readonly translation = inject(TranslationService);
  readonly correspondence =
    computed<CorrespondenceSummaryViewModel>(
      () => {
        const arabic =
          this.translation.language() === 'ar';

        return {
          reference:
            'PR-2026-0184',

          dateLabel:
            '30 Sep 2026',

          subject:
            arabic
              ? 'طلب مراجعة المستندات المرفقة'
              : 'Review of attached documents',

          senderName:
            null,

          senderDepartment:
            arabic
              ? 'إدارة الاستراتيجية'
              : 'Strategy Department',

          message:
            arabic
              ? 'يرجى التكرم بالاطلاع على المراسلة والمستندات المرفقة والتوجيه بما ترونه مناسباً.'
              : 'Please review the attached correspondence and supporting documents and provide your direction.',
        };
      },
    );
  readonly attachments: readonly PresidentReviewAttachment[] = [
    {
      id: 'main-letter',
      filename: 'report-badea.pdf',
      nameAr: 'الخطاب الرئيسي',
      nameEn: 'Main Letter',
      sizeLabel: '5.7 MB',
      src: '/pdf/local/report-badea.pdf',
    },
    {
      id: 'annex-a',
      filename: 'annex-a.pdf',
      nameAr: 'المرفق أ',
      nameEn: 'Annex A',
      sizeLabel: '1.8 MB',
      src: '/pdf/local/report-badea.pdf',
    },
    {
      id: 'supporting-document',
      filename: 'supporting-document.pdf',
      nameAr: 'مستند داعم',
      nameEn: 'Supporting Document',
      sizeLabel: '2.4 MB',
      src: '/pdf/local/report-badea.pdf',
    },
  ];

  /*
   * Temporary UI data only.
   *
   * Production recipients will come from the controlled
   * Alfresco/AD-backed recipient catalogue.
   */
  readonly recipients: readonly RecipientOption[] = [
    {
      id: 'legal',
      nameAr: 'الإدارة القانونية',
      nameEn: 'Legal Department',
    },
    {
      id: 'strategy',
      nameAr: 'إدارة الاستراتيجية',
      nameEn: 'Strategy',
    },
    {
      id: 'human-resources',
      nameAr: 'الموارد البشرية',
      nameEn: 'Human Resources',
    },
    {
      id: 'digital-transformation',
      nameAr: 'التحول الرقمي',
      nameEn: 'Digital Transformation',
    },
    {
      id: 'treasury-investment',
      nameAr: 'الخزينة والاستثمار',
      nameEn: 'Treasury & Investment',
    },
    {
      id: 'risk',
      nameAr: 'إدارة المخاطر',
      nameEn: 'Risk Management',
    },
    {
      id: 'audit',
      nameAr: 'المراجعة الداخلية',
      nameEn: 'Internal Audit',
    },
    {
      id: 'other',
      nameAr: 'أخرى',
      nameEn: 'Other',
    },
  ];

  readonly actions: readonly RequiredActionOption[] = [
    {
      code: 'required-action',
      labelAr: 'لإجراء اللازم وفق النظام',
      labelEn: 'Take the required action',
    },
    {
      code: 'study-and-opinion',
      labelAr: 'للدراسة وإبداء الرأي',
      labelEn: 'Study and provide an opinion',
    },
    {
      code: 'review-and-advise',
      labelAr: 'للمراجعة والإفادة',
      labelEn: 'Review and advise',
    },
    {
      code: 'discuss-directly',
      labelAr: 'للتفاهم / هاتفياً / شخصياً',
      labelEn: 'Discuss directly',
    },
    {
      code: 'direct-response',
      labelAr: 'للإجابة المباشرة',
      labelEn: 'Respond directly',
    },
    {
      code: 'prepare-summary',
      labelAr: 'لإعداد خلاصة',
      labelEn: 'Prepare a summary',
    },
    {
      code: 'information-and-action',
      labelAr: 'للعلم وإجراء اللازم',
      labelEn: 'For information and necessary action',
    },
    {
      code: 'follow-up',
      labelAr: 'للمتابعة',
      labelEn: 'Follow up',
    },
    {
      code: 'information-and-file',
      labelAr: 'للعلم والحفظ',
      labelEn: 'For information and filing',
    },
    {
      code: 'act-per-phone-call',
      labelAr: 'للعمل حسب المكالمة الهاتفية',
      labelEn: 'Act according to the phone call',
    },
    {
      code: 'prepare-response',
      labelAr: 'لإعداد رد مناسب',
      labelEn: 'Prepare an appropriate response',
    },
    {
      code: 'apology',
      labelAr: 'للاعتذار',
      labelEn: 'Prepare an apology',
    },
    {
      code: 'return-later',
      labelAr: 'لإعادتها لي لاحقاً',
      labelEn: 'Return to me later',
    },
    {
      code: 'other',
      labelAr: 'أخرى',
      labelEn: 'Other',
    },
  ];

  readonly activeAttachmentId =
    signal(this.attachments[0].id);

  readonly explanationMode =
    signal<ExplanationMode>('pen');

  readonly typedExplanation = signal('');

  readonly explanationInkDocument =
    signal<PresidentExplanationInkDocument>(
      createEmptyPresidentExplanationInkDocument(),
    );

  readonly selectedRecipientIds =
    signal<ReadonlySet<string>>(new Set());

  readonly selectedCopyRecipientIds =
    signal<ReadonlySet<string>>(new Set());

  readonly selectedActionCodes =
    signal<ReadonlySet<RequiredActionCode>>(
      new Set(),
    );

  readonly showCopyTo = signal(false);

  readonly showAllActions = signal(false);

  readonly otherRecipient = signal('');

  readonly otherAction = signal('');

  readonly activeAttachment = computed(() => {
    return (
      this.attachments.find(
        (attachment) =>
          attachment.id === this.activeAttachmentId(),
      ) ?? this.attachments[0]
    );
  });

  readonly visibleActions = computed(() => {
    return this.showAllActions()
      ? this.actions
      : this.actions.slice(0, 6);
  });

  readonly otherRecipientSelected = computed(() =>
    this.selectedRecipientIds().has('other'),
  );

  readonly otherActionSelected = computed(() =>
    this.selectedActionCodes().has('other'),
  );

  /*
   * "To" is already satisfied by President Office,
   * which is always part of the workflow.
   *
   * At President stage the remaining mandatory
   * business decision is at least one Required Action.
   */
  readonly canHandoff = computed(() => {
    if (this.selectedActionCodes().size === 0) {
      return false;
    }

    if (
      this.otherActionSelected() &&
      !this.otherAction().trim()
    ) {
      return false;
    }

    if (
      this.otherRecipientSelected() &&
      !this.otherRecipient().trim()
    ) {
      return false;
    }

    return true;
  });

  selectAttachment(id: string): void {
    this.activeAttachmentId.set(id);
  }

  setExplanationMode(
    mode: ExplanationMode,
  ): void {
    this.explanationMode.set(mode);
  }

  toggleRecipient(id: string): void {
    this.selectedRecipientIds.update(
      (current) => this.toggleSetValue(current, id),
    );
  }

  toggleCopyRecipient(id: string): void {
    this.selectedCopyRecipientIds.update(
      (current) => this.toggleSetValue(current, id),
    );
  }

  toggleAction(
    code: RequiredActionCode,
  ): void {
    this.selectedActionCodes.update(
      (current) =>
        this.toggleSetValue(current, code),
    );
  }

  recipientSelected(id: string): boolean {
    return this.selectedRecipientIds().has(id);
  }

  copyRecipientSelected(id: string): boolean {
    return this.selectedCopyRecipientIds().has(id);
  }

  actionSelected(
    code: RequiredActionCode,
  ): boolean {
    return this.selectedActionCodes().has(code);
  }

  recipientLabel(
    recipient: RecipientOption,
  ): string {
    return this.translation.language() === 'ar'
      ? recipient.nameAr
      : recipient.nameEn;
  }

  actionLabel(
    action: RequiredActionOption,
  ): string {
    return this.translation.language() === 'ar'
      ? action.labelAr
      : action.labelEn;
  }

  attachmentLabel(
    attachment: PresidentReviewAttachment,
  ): string {
    return this.translation.language() === 'ar'
      ? attachment.nameAr
      : attachment.nameEn;
  }

  onExplanationInkDocumentChange(
    document: PresidentExplanationInkDocument,
  ): void {
    this.explanationInkDocument.set(
      document,
    );
  }

  onTypedExplanationInput(
    event: Event,
  ): void {
    const target = event.target;

    if (!(target instanceof HTMLTextAreaElement)) {
      return;
    }

    this.typedExplanation.set(target.value);
  }

  onOtherRecipientInput(
    event: Event,
  ): void {
    const target = event.target;

    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    this.otherRecipient.set(target.value);
  }

  onOtherActionInput(
    event: Event,
  ): void {
    const target = event.target;

    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    this.otherAction.set(target.value);
  }

  handoff(): void {
    if (!this.canHandoff()) {
      return;
    }

    /*
     * Backend transition comes later.
     *
     * For now this intentionally performs no fake
     * persistence or workflow behaviour.
     */
  }

  private toggleSetValue<T>(
    current: ReadonlySet<T>,
    value: T,
  ): ReadonlySet<T> {
    const next = new Set(current);

    if (next.has(value)) {
      next.delete(value);
    } else {
      next.add(value);
    }

    return next;
  }
}