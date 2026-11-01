import { ChangeDetectionStrategy, Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceService } from '../../../core/correspondence/correspondence.service';
import { CorrespondenceDepartment } from '../../../core/correspondence/correspondence.model';
import {
  AccessChange, AccessMode, AccessSnapshot, TarasolAccessApiService,
  TarasolEmployee, TeamAssignment, UnitAppointment,
} from '../data-access/tarasol-access-api.service';

@Component({
  selector: 'app-tarasol-access-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './tarasol-access-page.component.html',
  styleUrl: './tarasol-access-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TarasolAccessPageComponent implements OnInit, OnDestroy {
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
  readonly employeeQuery = signal('');
  readonly employeeOptions = signal<readonly TarasolEmployee[]>([]);
  readonly selectedEmployee = signal<TarasolEmployee | null>(null);
  readonly employeeSearching = signal(false);
  readonly employeeSearchFailed = signal(false);
  private searchTimer?: ReturnType<typeof setTimeout>;
  private searchVersion = 0;
  private loadVersion = 0;

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
  readonly activeManagerAppointments = computed(() => this.activeAppointments().filter(a => a.capacity === 'MANAGER' || a.capacity === 'ACTING_MANAGER'));
  readonly activeSecretaries = computed(() => this.snapshot()?.secretaries.filter(a => a.active) ?? []);
  readonly activeDelegations = computed(() => this.snapshot()?.delegations.filter(a => a.active) ?? []);
  selectedUnitName(): string {
    const unit = this.units().find(item => item.code === this.form.controls.unitCode.value);
    return unit ? (this.arabic() ? unit.nameArabic || unit.nameEnglish : unit.nameEnglish) : this.form.controls.unitCode.value;
  }

  label(ar: string, en: string): string { return this.arabic() ? ar : en; }

  async ngOnInit(): Promise<void> {
    if (!this.isAdmin) {
      const workspace = this.session.activeWorkspace();
      if (!workspace) {
        this.feedback.set({ kind: 'error', message: this.label('اختر وحدة إدارية نشطة أولاً.', 'Select an active managerial workspace first.') });
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
        this.feedback.set({ kind: 'error', message: this.label('تعذر تحميل الوحدات التنظيمية.', 'Cannot load the organizational directory.') });
        return;
      }
    }
    await this.reload();
  }

  ngOnDestroy(): void {
    ++this.searchVersion;
    ++this.loadVersion;
    if (this.searchTimer) clearTimeout(this.searchTimer);
  }

  onUnitChange(): void {
    this.clearEmployee();
    this.snapshot.set(null);
    void this.reload();
  }

  async reload(): Promise<void> {
    const unitCode = this.form.controls.unitCode.value;
    if (!/^[0-9]{3}$/.test(unitCode)) return;
    const version = ++this.loadVersion;
    this.loading.set(true);
    this.feedback.set(null);
    try {
      const current = await firstValueFrom(this.api.read(this.mode, unitCode));
      if (version === this.loadVersion && unitCode === this.form.controls.unitCode.value) {
        this.snapshot.set(current);
      }
    } catch {
      if (version === this.loadVersion) {
        this.snapshot.set(null);
        this.feedback.set({ kind: 'error', message: this.label('تعذر تحميل الصلاحيات الحالية.', 'Unable to load access. Check your appointment and permissions.') });
      }
    } finally {
      if (version === this.loadVersion) this.loading.set(false);
    }
  }

  onEmployeeQuery(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.employeeQuery.set(value);
    this.selectedEmployee.set(null);
    this.form.controls.employeeId.setValue('');
    this.employeeOptions.set([]);
    this.employeeSearchFailed.set(false);
    ++this.searchVersion;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.employeeSearching.set(false);
    const unitCode = this.form.controls.unitCode.value;
    if (value.trim().length < 2 || !/^[0-9]{3}$/.test(unitCode)) return;
    this.employeeSearching.set(true);
    const version = this.searchVersion;
    this.searchTimer = setTimeout(() => void this.lookupEmployees(version, unitCode, value.trim()), 250);
  }

  private async lookupEmployees(version: number, unitCode: string, query: string): Promise<void> {
    if (version !== this.searchVersion) return;
    this.employeeSearching.set(true);
    try {
      const found = await firstValueFrom(this.api.searchEmployees(this.mode, unitCode, query));
      if (version === this.searchVersion && unitCode === this.form.controls.unitCode.value) {
        this.employeeOptions.set(found.employees);
      }
    } catch {
      if (version === this.searchVersion) this.employeeSearchFailed.set(true);
    } finally {
      if (version === this.searchVersion) this.employeeSearching.set(false);
    }
  }

  selectEmployee(employee: TarasolEmployee): void {
    ++this.searchVersion;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.selectedEmployee.set(employee);
    this.employeeQuery.set(this.employeeName(employee));
    this.form.controls.employeeId.setValue(employee.employeeId);
    this.employeeOptions.set([]);
    this.employeeSearching.set(false);
  }

  clearEmployee(): void {
    ++this.searchVersion;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.employeeQuery.set('');
    this.form.controls.employeeId.setValue('');
    this.selectedEmployee.set(null);
    this.employeeOptions.set([]);
    this.employeeSearchFailed.set(false);
    this.employeeSearching.set(false);
  }

  employeeName(employee: TarasolEmployee): string {
    return this.arabic() ? employee.nameArabic || employee.nameEnglish : employee.nameEnglish;
  }

  nameForId(employeeId: string): string | null {
    const employee = this.snapshot()?.employeesById?.[employeeId];
    return employee ? this.employeeName(employee) : null;
  }

  capacityName(capacity: string): string {
    switch (capacity) {
      case 'MANAGER': return this.label('مدير رسمي', 'Official manager');
      case 'ACTING_MANAGER': return this.label('مدير مكلف', 'Acting manager');
      case 'OFFICE_MEMBER': return this.label('عضو مكتب', 'Office member');
      case 'CONSULTANT': return this.label('مستشار', 'Consultant');
      default: return capacity;
    }
  }

  dateLabel(date: string | null): string {
    if (!date) return '—';
    const parts = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})/.exec(date);
    return parts ? `${parts[1]} · ${parts[2]}` : date;
  }

  async submit(): Promise<void> {
    if (this.saving() || this.loading()) return;
    const raw = this.form.getRawValue();
    const unitCode = raw.unitCode;
    const employeeId = raw.employeeId.trim();
    if (!/^[0-9]{3}$/.test(unitCode) || !this.selectedEmployee() ||
        this.selectedEmployee()!.employeeId !== employeeId) {
      this.feedback.set({ kind: 'error', message: this.label('اختر موظفاً من نتائج البحث.', 'Select an active employee from the search results.') });
      return;
    }
    const action = this.isAdmin ? 'APPOINT' : raw.teamAction;
    const startAt = this.toIso(raw.startAt) ?? new Date().toISOString();
    const endAt = this.toIso(raw.endAt);
    if ((raw.endAt && !endAt) || (endAt !== null && endAt <= startAt)) {
      this.feedback.set({ kind: 'error', message: this.label('تاريخ البداية أو النهاية غير صالح.', 'Invalid effective date range.') });
      return;
    }
    if (!this.isAdmin && (!raw.managerAssignmentId || Number(raw.managerAssignmentId) <= 0)) {
      this.feedback.set({ kind: 'error', message: this.label('اختر تكليف المدير النشط.', 'Select your active manager assignment.') });
      return;
    }
    if (action === 'GRANT_DELEGATION' && !endAt) {
      this.feedback.set({ kind: 'error', message: this.label('التفويض المؤقت يحتاج إلى تاريخ انتهاء.', 'Temporary delegation requires an end date.') });
      return;
    }
    let finalAction = action;
    let replacingId: number | undefined;
    let replacementReason: string | undefined;
    if (this.isAdmin && raw.capacity === 'MANAGER') {
      const official = this.activeAppointments().find(a => a.capacity === 'MANAGER');
      if (official) {
        if (!window.confirm(this.label(
          `استبدال المدير الحالي ${this.nameForId(official.employeeId) || official.employeeId}؟ سيتم التغيير في معاملة واحدة.`,
          `Replace official manager ${this.nameForId(official.employeeId) || official.employeeId} (#${official.id})? The change is atomic.`))) return;
        replacementReason = window.prompt(this.label('سبب تغيير المدير الرسمي:', 'Reason for changing the official manager:'))?.trim();
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
    if (!window.confirm(this.label('تأكيد التعيين؟ سيتم تسجيل العملية في سجل التدقيق.', 'Confirm this access assignment? It will be recorded in the audit history.'))) return;
    this.saving.set(true);
    this.feedback.set(null);
    try {
      await firstValueFrom(this.api.change(this.mode, payload));
      this.clearEmployee();
      await this.reload();
      this.feedback.set({ kind: 'success', message: this.label('تم تسجيل التعيين بنجاح.', 'Assignment recorded. Effective permissions follow the stored dates.') });
    } catch {
      this.feedback.set({ kind: 'error', message: this.label('لم يتم تأكيد التغيير. حدّث السجلات قبل المحاولة مجدداً.', 'Change not confirmed. Refresh current records before retrying.') });
    } finally { this.saving.set(false); }
  }

  async revoke(kind: 'appointment' | 'secretary' | 'delegation', record: UnitAppointment | TeamAssignment): Promise<void> {
    if (this.saving()) return;
    const reason = window.prompt(this.label('اذكر سبب إلغاء الصلاحية:', 'Enter an audit reason for revocation:'));
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
    if (!window.confirm(this.label('إلغاء هذه الصلاحية نهائياً؟', 'Revoke this access? This cannot be undone.'))) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.change(this.mode, payload));
      await this.reload();
    } catch {
      this.feedback.set({ kind: 'error', message: this.label('لم يتم تأكيد الإلغاء. حدّث السجلات قبل المحاولة مجدداً.', 'Revocation was not confirmed. Refresh before trying again.') });
    } finally { this.saving.set(false); }
  }

  private toIso(text: string): string | null {
    if (!text) return null;
    const date = new Date(text);
    return Number.isNaN(date.valueOf()) ? null : date.toISOString();
  }
}
