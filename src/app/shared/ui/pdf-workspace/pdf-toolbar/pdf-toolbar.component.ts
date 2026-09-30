import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  HostListener,
  inject,
  input,
  output,
  signal,
} from '@angular/core';

import { TranslationService } from '../../../../core/i18n/translation.service';

export type PdfEditorTool =
  | 'highlight'
  | 'draw'
  | 'text';

export type PdfMoreAction =
  | 'search'
  | 'rotate-clockwise'
  | 'rotate-counterclockwise'
  | 'properties'
  | 'print';

@Component({
  selector: 'app-pdf-toolbar',
  standalone: true,
  templateUrl: './pdf-toolbar.component.html',
  styleUrl: './pdf-toolbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfToolbarComponent {
  private readonly element =
    inject<ElementRef<HTMLElement>>(ElementRef);

  readonly translation = inject(TranslationService);

  readonly page = input.required<number>();
  readonly pageCount = input.required<number>();
  readonly activeTool = input<PdfEditorTool | null>(null);

  readonly previousPage = output<void>();
  readonly nextPage = output<void>();

  readonly zoomOut = output<void>();
  readonly zoomIn = output<void>();
  readonly fitPageWidth = output<void>();

  readonly selectTool = output<PdfEditorTool>();

  readonly undo = output<void>();
  readonly redo = output<void>();

  readonly toggleSidebar = output<void>();

  readonly moreAction = output<PdfMoreAction>();
  readonly moreOpen = signal(false);
  readonly canUndo = input(false);
  readonly canRedo = input(false);
  
  private readonly numberFormatter = computed(
    () =>
      new Intl.NumberFormat(
        this.translation.language() === 'ar'
          ? 'ar-SA-u-nu-arab'
          : 'en-US',
        {
          useGrouping: false,
        },
      ),
  );

  formatNumber(value: number): string {
    return this.numberFormatter().format(value);
  }

  toggleMore(): void {
    this.moreOpen.update((open) => !open);
  }

  selectMoreAction(action: PdfMoreAction): void {
    this.moreOpen.set(false);
    this.moreAction.emit(action);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.moreOpen.set(false);
  }

  @HostListener('document:pointerdown', ['$event'])
  onDocumentPointerDown(event: PointerEvent): void {
    if (!this.moreOpen()) {
      return;
    }

    const target = event.target;

    if (
      target instanceof Node &&
      !this.element.nativeElement.contains(target)
    ) {
      this.moreOpen.set(false);
    }
  }
}