import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ComposePageComponent } from '../../compose/presentation/compose-page.component';

/** Office 101 has its own intake form; President and Director editors are unaffected. */
@Component({
  selector: 'app-office-new-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './office-new-page.component.html',
  styleUrls: ['../../compose/presentation/compose-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OfficeNewPageComponent extends ComposePageComponent implements OnInit {
  private readonly officeRoute = inject(ActivatedRoute);

  protected override submissionAction(): 'SEND_TO_PRESIDENT' {
    return 'SEND_TO_PRESIDENT';
  }

  override ngOnInit(): void {
    // Office originates the correspondence in unit 101 and sends to President;
    // reopening an existing draft preserves its persisted fields.
    if (!this.officeRoute.snapshot.queryParamMap.get('draft')) {
      this.form.controls.recipient.setValue('President');
      this.form.controls.instruction.setValue('information-and-action');
    }
    super.ngOnInit();
  }
}
