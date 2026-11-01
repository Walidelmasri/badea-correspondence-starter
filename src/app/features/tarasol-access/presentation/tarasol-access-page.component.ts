import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceService } from '../../../core/correspondence/correspondence.service';
import { CorrespondenceDepartment } from '../../../core/correspondence/correspondence.model';
import {
  AccessChange, AccessMode, AccessSnapshot, TarasolAccessApiService,
  TeamAssignment, UnitAppointment,
} from '../data-access/tarasol-access-api.service';

@Component({
  selector: 'app-tarasol-access-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './tarasol-access-page.component.html',
  styleUrl: './tarasol-access-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TarasolAccessPageComponent implements OnInit {
  private readonly api = inject(TarasolAccessApiService);
  private readonly directory = inject(CorrespondenceService);
  private readonly route = inject(ActivatedRoute);
  readonly session = inject(AuthSessionStore);
  readonly translation = inject(TranslationService);
  readonly mode: AccessMode = this.route.snapshot.data['mode'] === 'admin' ? 'admin' : 'team';
  readonly isAdmin = this.mode === 'admin';
  readonly arabic = computed(() => this.translation.language() === 'ar');
  readonly units = signal<readonly CorrespondenceDepartment[]>([]);
  readonly snapshot = signal<AccessSnapshot | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly feedback = signal<{ kind: 'success' | 'error'; message: string } | null>(null);
  readonly form = new FormGroup({
    unitCode: new FormControl('', { nonNullable: true }),
    employeeId: new FormControl('', { nonNullable: true }),
    capacity: new FormControl('MANAGER', { nonNullable: true }),
    managerAssignmentId: new FormControl('', { nonNullable: true }),
    teamAction: new FormControl('ASSIGN_SECRETARY', { nonNullable: true }),
    delegationType: new FormControl('LEAVE_COVER', { nonNullable: true }),
    caseTechnicalId: new FormControl('', { nonNullable: true }),
    startAt: new FormControl('', { nonNullable: true }),
    endAt: new FormControl('', { nonNullable: true }),
    appointmentRef: new FormControl('', { nonNullable: true }),
  });
  readonly activeAppointments = computed(() => this.snapshot()?.appointments.filter(a => a.active) ?? []);
  readonly activeSecretaries = computed(() => this.snapshot()?.secretaries.filter(a => a.active) ?? []);
  readonly activeDelegations = computed(() => this.snapshot()?.delegations.filter(a => a.active) ?? []);

  async ngOnInit(): Promise<void> {
    if (!this.isAdmin) {
      const workspace = this.session.activeWorkspace();
      if (!workspace) {
        this.feedback.set({ kind: 'error', message: 'Select an active managerial workspace first.' });
        return;
      }
      this.form.controls.unitCode.setValue(workspace.code);
      this.form.controls.unitCode.disable();
    } else {
      try {
        const available = await firstValueFrom(this.directory.getDepartments());
        this.units.set(available);
        if (available.length) this.form.controls.unitCode.setValue(available[0].code);
      } catch {
        this.feedback.set({ kind: 'error', message: 'Cannot load the organizational directory.' });
        return;
      }
    }
    await this.reload();
  }

  async reload(): Promise<void> {
    const unitCode = this.form.controls.unitCode.value;
    if (!unitCode.match(/^[0-9]{3}$/)) return;
    this.loading.set(true);
    this.feedback.set(null);
    try {
      this.snapshot.set(await firstValueFrom(this.api.read(this.mode, unitCode)));
    } catch {
      this.snapshot.set(null);
      this.feedback.set({ kind: 'error', message: 'Unable to load access. Check your appointment and permissions.' });
    } finally { this.loading.set(false); }
  }

  async submit(): Promise<void> {
    if (this.saving() || this.loading()) return;
    const raw = this.form.getRawValue();
    const unitCode = raw.unitCode;
    const employeeId = raw.employeeId.trim();
    if (!unitCode.match(/^[0-9]{3}$/) || !employeeId.match(/^[0-9]{1,20}$/)) {
      this.feedback.set({ kind: 'error', message: 'Select a unit and enter a valid active employee ID.' });
      return;
    }
    const action = this.isAdmin ? 'APPOINT' : raw.teamAction;
    const startAt = this.toIso(raw.startAt) ?? new Date().toISOString();
    const endAt = this.toIso(raw.endAt);
    if ((raw.endAt && !endAt) || (endAt !== null && endAt <= startAt)) {
      this.feedback.set({ kind: 'error', message: 'Invalid effective date range.' });
      return;
    }
    if (!this.isAdmin && (!raw.managerAssignmentId || Number(raw.managerAssignmentId) <= 0)) {
      this.feedback.set({ kind: 'error', message: 'Select your active manager assignment.' });
      return;
    }
    if (action === 'GRANT_DELEGATION' && !endAt) {
      this.feedback.set({ kind: 'error', message: 'Temporary delegation requires an end date.' });
      return;
    }
    let finalAction = action;
    let replacingId: number | undefined;
    let replacementReason: string | undefined;
    if (this.isAdmin && raw.capacity === 'MANAGER') {
      const official = this.activeAppointments().find(a => a.capacity === 'MANAGER');
      if (official) {
        if (!window.confirm(`Replace official manager ${official.employeeId} (#${official.id})? The change is atomic.`)) return;
        replacementReason = window.prompt('Reason for changing the official manager:')?.trim();
        if (!replacementReason) return;
        finalAction = 'REPLACE_MANAGER';
        replacingId = official.id;
      }
    }
    const payload: AccessChange = {
      action: finalAction, unitCode, employeeId, startsAt: startAt,
      ...(replacingId ? { assignmentId: replacingId, reason: replacementReason } : {}),
      endsAt: endAt,
      ...(this.isAdmin ? {
        capacity: raw.capacity, appointmentRef: raw.appointmentRef.trim(),
      } : {
        managerAssignmentId: Number(raw.managerAssignmentId),
        delegationType: raw.delegationType,
        caseTechnicalId: raw.delegationType === 'CASE_HANDOFF' ? raw.caseTechnicalId.trim() : undefined,
      }),
    };
    if (!window.confirm('Confirm this Tarasol access assignment? It will be recorded in the audit history.')) return;
    this.saving.set(true);
    this.feedback.set(null);
    try {
      await firstValueFrom(this.api.change(this.mode, payload));
      this.form.controls.employeeId.setValue('');
      this.feedback.set({ kind: 'success', message: 'Assignment recorded. Effective permissions follow the stored dates.' });
      await this.reload();
    } catch {
      this.feedback.set({ kind: 'error', message: 'Change not confirmed. Refresh the current records before retrying.' });
    } finally { this.saving.set(false); }
  }

  async revoke(kind: 'appointment' | 'secretary' | 'delegation', record: UnitAppointment | TeamAssignment): Promise<void> {
    if (this.saving()) return;
    const reason = window.prompt('Enter an audit reason for revocation:');
    if (!reason?.trim()) return;
    const unitCode = this.form.controls.unitCode.value;
    const payload: AccessChange = {
      unitCode, action: kind === 'appointment' ? 'REVOKE' :
        kind === 'secretary' ? 'REVOKE_SECRETARY' : 'REVOKE_DELEGATION',
      ...(kind === 'appointment'
        ? { assignmentId: record.id }
        : { recordId: record.id, managerAssignmentId: (record as TeamAssignment).managerAssignmentId }),
      reason: reason.trim(),
    };
    if (!window.confirm('Revoke this access? This cannot be undone.')) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.change(this.mode, payload));
      await this.reload();
    } catch {
      this.feedback.set({ kind: 'error', message: 'Revocation was not confirmed. Refresh before trying again.' });
    } finally { this.saving.set(false); }
  }

  private toIso(text: string): string | null {
    if (!text) return null;
    const date = new Date(text);
    return Number.isNaN(date.valueOf()) ? null : date.toISOString();
  }
}
