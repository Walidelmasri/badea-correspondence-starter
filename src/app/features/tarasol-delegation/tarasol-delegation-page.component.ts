import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { firstValueFrom } from 'rxjs';
import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { TranslationService } from '../../core/i18n/translation.service';
import { DelegatedCase, TarasolDelegationApiService } from './tarasol-delegation-api.service';

@Component({
  selector: 'app-tarasol-delegation-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tarasol-delegation-page.component.html',
  styleUrl: './tarasol-delegation-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TarasolDelegationPageComponent implements OnDestroy {
  private readonly api = inject(TarasolDelegationApiService);
  private readonly session = inject(AuthSessionStore);
  private readonly translation = inject(TranslationService);
  t(en: string, ar: string): string { return this.translation.language() === 'ar' ? ar : en; }
  readonly handoffCases = signal<readonly {technicalId: string; subject: string; state: string}[]>([]);
  private readonly sanitizer = inject(DomSanitizer);
  readonly units = this.session.currentUser()?.employeeProfile?.delegationUnits ?? [];
  unitCode = this.units[0]?.code ?? '';
  technicalId = '';
  readonly current = signal<DelegatedCase | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly info = signal('');
  readonly pendingConfirmation = signal<'SEND_TO_OFFICE' | 'MARK_DONE' | null>(null);
  readonly previewUrl = signal<SafeResourceUrl | null>(null);
  private objectUrl: string | null = null;

  async refreshHandoffs(): Promise<void> {
    this.handoffCases.set([]);
    if (!this.unitCode) return;
    try {
      const result = await firstValueFrom(this.api.handoffs(this.unitCode));
      this.handoffCases.set(result.items);
    } catch {
      this.error.set(this.t('Could not load active case handoffs.', 'تعذر تحميل الحالات المفوضة الحالية.'));
    }
  }

  chooseHandoff(id: string): void {
    this.technicalId = id;
    void this.lookup();
  }

  changeUnit(): void {
    this.reset();
    void this.refreshHandoffs();
  }

  constructor() { void this.refreshHandoffs(); }

  reset(): void {
    this.current.set(null);
    this.pendingConfirmation.set(null);
    this.closePreview();
  }

  ngOnDestroy(): void { this.closePreview(); }

  async lookup(): Promise<void> {
    this.reset();
    this.error.set('');
    this.info.set('');
    if (!this.unitCode || !this.technicalId.trim()) {
      this.error.set('Choose a unit and enter the case technical ID.'); return;
    }
    this.busy.set(true);
    try {
      this.current.set(await firstValueFrom(this.api.open(this.unitCode, this.technicalId.trim())));
    } catch {
      this.error.set('This case is not available under your current delegation and permissions.');
    } finally { this.busy.set(false); }
  }

  async preview(nodeId: string): Promise<void> {
    const current = this.current();
    if (!current) return;
    this.closePreview();
    this.error.set('');
    this.busy.set(true);
    try {
      const pdf = await firstValueFrom(this.api.pdf(current.unitCode, current.technicalId, nodeId));
      if (pdf.type.toLowerCase() !== 'application/pdf') throw new Error('Invalid PDF');
      this.objectUrl = URL.createObjectURL(pdf);
      this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl));
    } catch {
      this.error.set('PDF is unavailable or you no longer have access.');
    } finally { this.busy.set(false); }
  }

  closePreview(): void {
    this.previewUrl.set(null);
    if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
    this.objectUrl = null;
  }

  confirm(action: 'SEND_TO_OFFICE' | 'MARK_DONE'): void {
    this.pendingConfirmation.set(action);
  }

  async act(): Promise<void> {
    const current = this.current();
    const action = this.pendingConfirmation();
    if (!current || !action) return;
    this.busy.set(true);
    this.error.set('');
    this.info.set('');
    this.pendingConfirmation.set(null);
    try {
      const result = await firstValueFrom(this.api.act(current.unitCode, current.technicalId, action));
      this.reset();
      this.info.set(`Delegated action committed: ${result.state}.`);
    } catch {
      this.reset();
      this.error.set('Action not confirmed. Reload the case before trying again; do not send twice.');
    } finally { this.busy.set(false); }
  }
}
