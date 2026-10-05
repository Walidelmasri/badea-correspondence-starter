import {
  ChangeDetectionStrategy,
  Component,
  input,
} from '@angular/core';

export interface CorrespondenceSummaryViewModel {
  readonly subject: string;

  readonly senderName: string | null;

  readonly senderDepartment: string | null;

  readonly message: string | null;

  readonly reference: string;

  readonly dateLabel: string;
}

@Component({
  selector: 'app-correspondence-summary',

  standalone: true,

  templateUrl:
    './correspondence-summary.component.html',

  styleUrl:
    './correspondence-summary.component.scss',

  changeDetection:
    ChangeDetectionStrategy.OnPush,
})
export class CorrespondenceSummaryComponent {
  readonly correspondence =
    input.required<CorrespondenceSummaryViewModel>();

  readonly labels = input.required<{
    readonly correspondence: string;
    readonly from: string;
    readonly reference: string;
    readonly date: string;
  }>();
}