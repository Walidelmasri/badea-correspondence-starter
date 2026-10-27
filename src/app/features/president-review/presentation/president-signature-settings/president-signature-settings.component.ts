import { Component, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CorrespondenceApiService } from '../../../../shared/data-access/correspondence-api.service';
import { TranslationService } from '../../../../core/i18n/translation.service';

type Coord = readonly [number, number];
type Stroke = Coord[];

/** Signature registration is globally scoped to the President, not to this correspondence. */
@Component({
  selector: 'app-president-signature-settings',
  standalone: true,
  template: `
    <section class="signature-settings">
      <button type="button" (click)="toggle()">
        {{ ar() ? 'توقيع الرئيس المحفوظ' : 'Saved President signature' }}
      </button>
      @if (open()) {
        <div class="signature-panel">
          <p>{{ ar()
            ? 'يسمح بحفظ توقيعك مرة واحدة وإضافته تلقائياً إلى أوراق الإجراء الجديدة التي تعتمد مراجعتك. لا يُعدل المستندات القديمة.'
            : 'Register your mark once for automatic stamping of newly issued action sheets from your reviews. Existing PDFs are unchanged.' }}</p>
          @if (status()) {
            <p role="status">{{ status()!.configured
              ? (ar() ? 'التوقيع محفوظ ويمكن استبداله' : 'Signature registered; replacement is available')
              : (ar() ? 'لم يُحفظ توقيع بعد' : 'No signature registered') }}</p>
          }
          <canvas #pad width="520" height="135"
            (pointerdown)="start($event)" (pointermove)="move($event)"
            (pointerup)="finish($event)" (pointercancel)="finish($event)"
            [attr.aria-label]="ar() ? 'ارسم توقيعك هنا' : 'Draw your signature here'">
          </canvas>
          <label class="consent">
            <input type="checkbox" [checked]="consented()"
              (change)="consented.set($any($event.target).checked)" />
            {{ ar()
              ? 'أوافق على استخدام توقيعي المحفوظ تلقائياً في أوراق الإجراء المستقبلية المعتمدة.'
              : 'I authorize my saved signature to be automatically applied to future action sheets from my approved reviews.' }}
          </label>
          <div class="actions">
            <button type="button" (click)="clear()" [disabled]="saving()">
              {{ ar() ? 'مسح الرسم' : 'Clear drawing' }}
            </button>
            <button type="button" (click)="save()"
              [disabled]="saving() || !consented() || strokes.length === 0 || !status()">
              {{ saving() ? (ar() ? 'جارٍ الحفظ…' : 'Saving…')
                : (ar() ? 'حفظ توقيع الرئيس' : 'Save President signature') }}
            </button>
          </div>
          @if (feedback()) { <p role="status">{{ feedback() }}</p> }
        </div>
      }
    </section>
  `,
  styles: [`
    .signature-settings { margin-block: 12px; }
    button { border: 1px solid #c5d4cf; background: #fff; color: #164435; border-radius: 8px;
      padding: 9px 14px; cursor: pointer; }
    button:disabled { opacity: .5; cursor: not-allowed; }
    .signature-panel { margin-top: 10px; max-width: 580px; background: #f9fbfa;
      padding: 16px; border: 1px solid #d7e6df; border-radius: 10px; }
    p { font-size: .88rem; }
    canvas { touch-action: none; width: 100%; max-width: 520px; height: auto;
      background: #fff; border: 1px solid #9aaea7; border-radius: 6px; display: block; }
    .consent { display: flex; gap: 8px; align-items: start; margin: 10px 0; font-size: .82rem; }
    .actions { display: flex; gap: 8px; flex-wrap: wrap; }
  `],
})
export class PresidentSignatureSettingsComponent {
  private readonly api = inject(CorrespondenceApiService);
  private readonly translations = inject(TranslationService);
  @ViewChild('pad') private pad?: ElementRef<HTMLCanvasElement>;
  readonly open = signal(false);
  readonly saving = signal(false);
  readonly consented = signal(false);
  readonly feedback = signal('');
  readonly status = signal<{configured: boolean; revision: number; updatedAt: string | null} | null>(null);
  readonly strokes: Stroke[] = [];
  private current: Stroke | null = null;
  readonly ar = () => this.translations.language() === 'ar';

  async toggle(): Promise<void> {
    this.open.update(x => !x);
    this.consented.set(false);
    if (this.open()) {
      try { this.status.set(await firstValueFrom(this.api.getPresidentSignatureStatus())); }
      catch { this.feedback.set(this.ar() ? 'تعذر تحميل حالة التوقيع' : 'Cannot load signature status'); }
    }
  }

  private point(event: PointerEvent, canvas: HTMLCanvasElement): Coord {
    const rect = canvas.getBoundingClientRect();
    return [Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
      Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height))];
  }

  start(event: PointerEvent): void {
    if (this.saving()) return;
    const canvas = event.currentTarget as HTMLCanvasElement;
    canvas.setPointerCapture(event.pointerId);
    const point = this.point(event, canvas);
    this.current = [point];
    this.strokes.push(this.current);
    event.preventDefault();
  }

  move(event: PointerEvent): void {
    if (!this.current) return;
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
    this.strokes.length = 0; this.current = null;
    const canvas = this.pad?.nativeElement;
    canvas?.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height);
    this.consented.set(false);
    this.feedback.set('');
  }

  async save(): Promise<void> {
    const current = this.status();
    if (!current || !this.consented() || this.strokes.length === 0 || this.saving()) return;
    this.saving.set(true);
    try {
      const result = await firstValueFrom(this.api.registerPresidentSignature({
        expectedRevision: current.revision,
        authorizeReuse: true,
        drawing: {schemaVersion: 1, strokes: this.strokes.map(s => [...s])},
      }));
      this.status.set(result);
      this.feedback.set(this.ar() ? 'تم حفظ توقيع الرئيس' : 'President signature saved');
      this.clear();
      this.feedback.set(this.ar() ? 'تم حفظ توقيع الرئيس' : 'President signature saved');
    } catch {
      this.feedback.set(this.ar() ? 'تعذر الحفظ. أعد تحميل الحالة قبل المحاولة.'
        : 'Save failed. Refresh the signature status before retrying.');
    } finally { this.saving.set(false); }
  }
}
