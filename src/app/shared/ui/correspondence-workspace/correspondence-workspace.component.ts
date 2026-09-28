import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';

@Component({
  selector: 'app-correspondence-workspace',
  standalone: true,
  templateUrl: './correspondence-workspace.component.html',
  styleUrl: './correspondence-workspace.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CorrespondenceWorkspaceComponent {
  readonly eyebrow = input.required<string>();
  readonly title = input.required<string>();
  readonly description = input.required<string>();

  readonly searchLabel = input.required<string>();
  readonly searchPlaceholder = input.required<string>();

  readonly primaryActionLabel = input.required<string>();

  readonly primaryAction = output<void>();
}