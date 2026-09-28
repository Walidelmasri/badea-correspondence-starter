import {
  ChangeDetectionStrategy,
  Component,
  inject,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceInstruction } from '../../inbox/domain/correspondence-instruction.model';
import { CORRESPONDENCE_INSTRUCTION_LABELS } from '../../inbox/presentation/correspondence-instruction.labels';

interface ComposeForm {
  recipient: FormControl<string>;
  subject: FormControl<string>;
  documentNumber: FormControl<string>;
  documentDate: FormControl<string>;
  entity: FormControl<string>;
  instruction: FormControl<CorrespondenceInstruction | null>;
  explanation: FormControl<string>;
  copyTo: FormControl<string>;
}

@Component({
  selector: 'app-compose-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
  ],
  templateUrl: './compose-page.component.html',
  styleUrl: './compose-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComposePageComponent {
  readonly translation = inject(TranslationService);

  readonly instructionLabels =
    CORRESPONDENCE_INSTRUCTION_LABELS;

  readonly instructions: readonly CorrespondenceInstruction[] = [
    'required-action',
    'study-and-opinion',
    'review-and-advise',
    'discuss-directly',
    'direct-response',
    'prepare-summary',
    'information-and-action',
    'follow-up',
    'information-and-file',
    'act-per-phone-call',
    'prepare-response',
    'apology',
    'return-later',
  ];

  readonly form = new FormGroup<ComposeForm>({
    recipient: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
      ],
    }),

    subject: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
      ],
    }),

    documentNumber: new FormControl('', {
      nonNullable: true,
    }),

    documentDate: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
      ],
    }),

    entity: new FormControl('', {
      nonNullable: true,
    }),

    instruction:
      new FormControl<CorrespondenceInstruction | null>(
        null,
        {
          validators: [
            Validators.required,
          ],
        },
      ),

    explanation: new FormControl('', {
      nonNullable: true,
    }),

    copyTo: new FormControl('', {
      nonNullable: true,
    }),
  });
}