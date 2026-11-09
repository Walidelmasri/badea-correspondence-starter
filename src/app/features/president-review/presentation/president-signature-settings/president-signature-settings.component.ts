import { Component, ElementRef, HostListener, Input, inject, signal, ViewChild } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  CorrespondenceApiService,
  PresidentSignatureStatus,
} from '../../../../shared/data-access/correspondence-api.service';
import { TranslationService } from '../../../../core/i18n/translation.service';

type Coord = readonly [number, number];
type Stroke = Coord[];

@Component({
  selector: 'app-president-signature-settings',
  standalone: true,
  template: `
    <section class="signature-settings" [class.signature-settings--modal]="modal">
      <button type="button" class="signature-trigger" (click)="toggle()">
        {{ ar() ? 'توقيع الرئيس' : 'President signature' }}
      </button>
      @if (open()) {
        @if (modal) { <div class="signature-backdrop" aria-hidden="true"></div> }
        <div class="signature-panel" [class.signature-panel--modal]="modal"
          [attr.role]="modal ? 'dialog' : 'region'"
          [attr.aria-modal]="modal ? 'true' : null"
          [attr.aria-label]="ar() ? 'إدارة توقيع الرئيس' : 'Manage President signature'"
          [attr.dir]="ar() ? 'rtl' : 'ltr'">
          <div class="signature-heading">
            <strong>{{ ar() ? 'إدارة توقيع الرئيس' : 'Manage President signature' }}</strong>
            <button type="button" class="signature-close" (click)="close()" [disabled]="saving()"
              [attr.aria-label]="ar() ? 'إغلاق' : 'Close'">×</button>
          </div>
          <p class="signature-note">{{ ar()
            ? 'يُستخدم التوقيع المحفوظ في أوراق الإجراء الجديدة بعد اعتماد المراجعة. لا تتغير المستندات السابقة.'
            : 'The saved signature is applied to new action sheets after an approved review. Existing documents remain unchanged.' }}</p>
          @if (loadingStatus()) {
            <p role="status">{{ ar() ? 'جارٍ تحميل التوقيع...' : 'Loading signature...' }}</p>
          } @else if (!status()) {
            <p role="alert">{{ ar() ? 'تعذر تحميل حالة التوقيع.' : 'Could not load signature status.' }}</p>
            <button type="button" (click)="loadStatus()">{{ ar() ? 'إعادة المحاولة' : 'Retry' }}</button>
          } @else {
            @if (status()!.configured && !editing()) {
              <p class="signature-state">{{ ar() ? 'التوقيع محفوظ' : 'Signature saved' }}</p>
              @if (status()!.previewDataUrl) {
                <div class="signature-preview">
                  <img [src]="status()!.previewDataUrl" [alt]="ar() ? 'معاينة التوقيع المحفوظ' : 'Saved signature preview'" />
                </div>
                <div class="actions">
                  <button type="button" (click)="beginEdit()" [disabled]="saving()">
                    {{ ar() ? 'تغيير التوقيع' : 'Change signature' }}
                  </button>
                  <button type="button" class="signature-remove" (click)="confirmRemoval.set(true)"
                    [disabled]="saving()">
                    {{ ar() ? 'إزالة التوقيع' : 'Remove signature' }}
                  </button>
                </div>
                @if (confirmRemoval()) {
                  <div class="signature-confirm">
                    <p>{{ ar() ? 'إزالة التوقيع من الاستخدام المستقبلي؟ لن تتغير المستندات السابقة.'
                      : 'Disable the signature for future documents? Existing documents stay unchanged.' }}</p>
                    <div class="actions">
                      <button type="button" class="signature-remove" (click)="remove()" [disabled]="saving()">
                        {{ saving() ? (ar() ? 'جارٍ الإزالة...' : 'Removing...') : (ar() ? 'تأكيد الإزالة' : 'Confirm removal') }}
                      </button>
                      <button type="button" (click)="confirmRemoval.set(false)" [disabled]="saving()">
                        {{ ar() ? 'إلغاء' : 'Cancel' }}
                      </button>
                    </div>
                  </div>
                }
              } @else {
                <p role="alert">{{ ar()
                  ? 'لا يمكن عرض هذا التوقيع أو تعديله من هذا الحساب. تواصل مع مسؤول النظام.'
                  : 'This signature cannot be previewed or changed by this account. Contact the administrator.' }}</p>
              }
            } @else {
              <p class="signature-state">{{ status()!.configured
                ? (ar() ? 'ارسم توقيعاً جديداً لاستبدال الحالي' : 'Draw a replacement signature')
                : (ar() ? 'لم يُحفظ توقيع بعد. ارسم توقيعك أدناه.' : 'No saved signature. Draw your signature below.') }}</p>
              <canvas #pad width="520" height="135"
                (pointerdown)="start($event)" (pointermove)="move($event)"
                (pointerup)="finish($event)" (pointercancel)="finish($event)"
                [attr.aria-label]="ar() ? 'ارسم توقيعك هنا' : 'Draw your signature here'">
              </canvas>
              <label class="consent">
                <input type="checkbox" [checked]="consented()" [disabled]="saving()"
                  (change)="consented.set($any($event.target).checked)" />
                {{ ar()
                  ? 'أوافق على استخدام توقيعي المحفوظ في أوراق الإجراء المستقبلية المعتمدة.'
                  : 'I authorize my saved signature for future action sheets from approved reviews.' }}
              </label>
              <div class="actions">
                <button type="button" (click)="clear()" [disabled]="saving()">
                  {{ ar() ? 'مسح الرسم' : 'Clear drawing' }}
                </button>
                <button type="button" class="signature-primary" (click)="save()"
                  [disabled]="saving() || !consented() || strokes.length === 0">
                  {{ saving() ? (ar() ? 'جارٍ الحفظ...' : 'Saving...')
                    : (ar() ? 'حفظ التوقيع' : 'Save signature') }}
                </button>
                @if (status()!.configured) {
                  <button type="button" (click)="cancelEdit()" [disabled]="saving()">
                    {{ ar() ? 'إلغاء التغيير' : 'Cancel change' }}
                  </button>
                }
              </div>
            }
          }
          @if (feedback()) { <p role="status" class="signature-feedback">{{ feedback() }}</p> }
        </div>
      }
    </section>
  `,
  styles: [`
    .signature-settings { margin-block: 12px; }
    button { border: 1px solid #c5d4cf; background: #fff; color: #164435; border-radius: 8px;
      padding: 9px 14px; cursor: pointer; font: inherit; font-weight: 650; }
    button:disabled { opacity: .5; cursor: not-allowed; }
    .signature-trigger { width: 100%; }
    .signature-panel { margin-top: 10px; max-width: 580px; background: #f9fbfa;
      padding: 18px; border: 1px solid #d7e6df; border-radius: 12px; color: #182c29; }
    .signature-panel--modal { position: fixed; z-index: 1501; inset: 50% auto auto 50%;
      transform: translate(-50%, -50%); width: min(92vw, 620px); max-height: 90vh;
      overflow-y: auto; margin: 0; box-sizing: border-box; box-shadow: 0 22px 80px #0004; }
    .signature-backdrop { position: fixed; inset: 0; z-index: 1500; background: #102c28a6; }
    .signature-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
    .signature-heading strong { font-size: 1.05rem; }
    .signature-close { font-size: 1.35rem; line-height: 1; }
    .signature-note { color: #526c62; font-size: .85rem; line-height: 1.6; }
    .signature-state { font-weight: 750; font-size: .9rem; }
    .signature-preview { background: #fff; border: 1px solid #d7e6df; border-radius: 8px;
      min-height: 135px; display: grid; place-items: center; }
    .signature-preview img { width: 100%; max-width: 520px; height: auto; }
    canvas { touch-action: none; width: 100%; max-width: 520px; height: auto;
      background: #fff; border: 1px solid #9aaea7; border-radius: 6px; display: block; }
    .consent { display: flex; gap: 8px; align-items: start; margin: 12px 0; font-size: .83rem; }
    .actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 12px; }
    .signature-primary { background: #164435; color: white; }
    .signature-remove { color: #8b2e32; border-color: #e3c4c5; }
    .signature-confirm { border: 1px solid #e3c4c5; background: #fff7f7; padding: 12px;
      border-radius: 8px; margin-top: 12px; }
    .signature-feedback { font-size: .86rem; font-weight: 650; }
    @media (max-width: 600px) { .signature-panel--modal { width: 95vw; padding: 14px; } }
  `],
})
export class PresidentSignatureSettingsComponent {
  @Input() modal = false;
  private readonly api = inject(CorrespondenceApiService);
  private readonly translations = inject(TranslationService);
  @ViewChild('pad') private pad?: ElementRef<HTMLCanvasElement>;
  readonly open = signal(false);
  readonly loadingStatus = signal(false);
  readonly saving = signal(false);
  readonly editing = signal(false);
  readonly confirmRemoval = signal(false);
  readonly consented = signal(false);
  readonly feedback = signal('');
  readonly status = signal<PresidentSignatureStatus | null>(null);
  readonly strokes: Stroke[] = [];
  private current: Stroke | null = null;
  private loadVersion = 0;
  readonly ar = () => this.translations.language() === 'ar';

  @HostListener('document:keydown.escape')
  onEscape(): void { if (this.open()) this.close(); }

  async toggle(): Promise<void> {
    if (this.open()) { this.close(); return; }
    this.open.set(true);
    await this.loadStatus();
  }

  close(): void {
    if (this.saving()) return;
    this.loadVersion++;
    this.open.set(false);
    this.loadingStatus.set(false);
    this.editing.set(false);
    this.confirmRemoval.set(false);
    this.clear();
  }

  async loadStatus(): Promise<void> {
    const version = ++this.loadVersion;
    this.loadingStatus.set(true);
    this.status.set(null);
    this.feedback.set('');
    try {
      const status = await firstValueFrom(this.api.getPresidentSignatureStatus());
      if (version !== this.loadVersion || !this.open()) return;
      this.status.set(status);
      this.editing.set(!status.configured);
      this.confirmRemoval.set(false);
      this.clear();
    } catch {
      if (version === this.loadVersion && this.open()) {
        this.feedback.set(this.ar() ? 'تعذر تحميل التوقيع. أعد المحاولة.' : 'Could not load signature. Try again.');
      }
    } finally {
      if (version === this.loadVersion) this.loadingStatus.set(false);
    }
  }

  beginEdit(): void {
    if (this.saving() || !this.status()?.previewDataUrl) return;
    this.clear();
    this.confirmRemoval.set(false);
    this.editing.set(true);
  }

  cancelEdit(): void {
    if (this.saving()) return;
    this.clear();
    this.editing.set(false);
  }

  private point(event: PointerEvent, canvas: HTMLCanvasElement): Coord {
    const rect = canvas.getBoundingClientRect();
    return [Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
      Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height))];
  }

  start(event: PointerEvent): void {
    if (this.saving() || !this.editing()) return;
    const canvas = event.currentTarget as HTMLCanvasElement;
    canvas.setPointerCapture(event.pointerId);
    const point = this.point(event, canvas);
    this.current = [point];
    this.strokes.push(this.current);
    event.preventDefault();
  }

  move(event: PointerEvent): void {
    if (!this.current || !this.editing()) return;
    const canvas = event.currentTarget as HTMLCanvasElement;
    const point = this.point(event, canvas);
    const last = this.current[this.current.length - 1];
    if (Math.hypot(point[0] - last[0], point[1] - last[1]) < .002) return;
    this.current.push(point);
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.beginPath(); ctx.strokeStyle = '#142e39'; ctx.lineWidth = 2;
      ctx.lineCap = 'round'; ctx.lineJoin = 'round';
      ctx.moveTo(last[0] * canvas.width, last[1] * canvas.height);
      ctx.lineTo(point[0] * canvas.width, point[1] * canvas.height); ctx.stroke();
    }
    event.preventDefault();
  }

  finish(event: PointerEvent): void {
    if (this.current?.length === 1) this.strokes.pop();
    this.current = null;
    const canvas = event.currentTarget as HTMLCanvasElement;
    if (canvas.hasPointerCapture(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
  }

  clear(): void {
    this.strokes.length = 0;
    this.current = null;
    const canvas = this.pad?.nativeElement;
    canvas?.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height);
    this.consented.set(false);
    this.feedback.set('');
  }

  async save(): Promise<void> {
    const current = this.status();
    if (!current || !this.editing() || !this.consented() || this.strokes.length === 0 || this.saving()) return;
    this.saving.set(true);
    try {
      const result = await firstValueFrom(this.api.registerPresidentSignature({
        expectedRevision: current.revision,
        authorizeReuse: true,
        drawing: {schemaVersion: 1, strokes: this.strokes.map(s => [...s])},
      }));
      this.status.set(result);
      this.editing.set(false);
      this.clear();
      this.feedback.set(this.ar() ? 'تم حفظ التوقيع بنجاح.' : 'Signature saved successfully.');
    } catch {
      this.feedback.set(this.ar() ? 'تعذر تأكيد الحفظ. أعد تحميل حالة التوقيع قبل المحاولة مجدداً.'
        : 'Save could not be confirmed. Reload signature status before retrying.');
    } finally { this.saving.set(false); }
  }

  async remove(): Promise<void> {
    const current = this.status();
    if (!current?.configured || !current.previewDataUrl || !this.confirmRemoval() || this.saving()) return;
    this.saving.set(true);
    try {
      const result = await firstValueFrom(this.api.deactivatePresidentSignature(current.revision));
      this.status.set(result);
      this.editing.set(true);
      this.confirmRemoval.set(false);
      this.clear();
      this.feedback.set(this.ar() ? 'تم إيقاف استخدام التوقيع للمراسلات الجديدة.'
        : 'Signature disabled for future correspondence.');
    } catch {
      this.confirmRemoval.set(false);
      this.feedback.set(this.ar() ? 'تعذر تأكيد الإزالة. أعد تحميل الحالة قبل المحاولة مجدداً.'
        : 'Removal could not be confirmed. Reload status before retrying.');
    } finally { this.saving.set(false); }
  }
}
