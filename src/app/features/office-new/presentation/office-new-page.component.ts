import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CorrespondenceDepartment } from '../../../core/correspondence/correspondence.model';
import { CorrespondenceService } from '../../../core/correspondence/correspondence.service';
import { ComposePageComponent } from '../../compose/presentation/compose-page.component';

/** Office 101 sends to President; the President selects the required action later. */
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
  private readonly correspondence = inject(CorrespondenceService);
  readonly departments = signal<readonly CorrespondenceDepartment[]>([]);

  constructor() {
    super();
    // Only the Office editor makes action optional. The President editor retains
    // its existing validation, and no action is pre-selected on Office initiation.
    this.form.controls.instruction.clearValidators();
    this.form.controls.instruction.updateValueAndValidity({ emitEvent: false });
  }

  protected override submissionAction(): 'SEND_TO_PRESIDENT' {
    return 'SEND_TO_PRESIDENT';
  }

  override ngOnInit(): void {
    if (!this.officeRoute.snapshot.queryParamMap.get('draft')) {
      this.form.controls.recipient.setValue('President');
      this.form.controls.instruction.setValue(null);
      this.form.controls.copyTo.setValue('');
    }
    super.ngOnInit();
    void this.loadDepartments();
  }

  private async loadDepartments(): Promise<void> {
    try {
      this.departments.set(await firstValueFrom(this.correspondence.getDepartments()));
    } catch {
      // The country/organization field remains free-text if lookup is unavailable.
      this.departments.set([]);
    }
  }

  clearInstruction(): void {
    if (this.busy() || this.readOnly()) return;
    this.form.controls.instruction.setValue(null);
    this.form.controls.instruction.markAsDirty();
  }

  override async saveDraft(): Promise<void> {
    // Reopened Office drafts must also retain the fixed institutional recipient.
    if (this.form.controls.recipient.value !== 'President') {
      this.form.controls.recipient.setValue('President');
      this.form.controls.recipient.markAsDirty();
    }
    // The Office-to-President editor has no CC; strip any legacy hidden CC
    // when a previously created Office draft is explicitly saved or sent.
    if (this.form.controls.copyTo.value !== '') {
      this.form.controls.copyTo.setValue('');
      this.form.controls.copyTo.markAsDirty();
    }
    await super.saveDraft();
  }
}
