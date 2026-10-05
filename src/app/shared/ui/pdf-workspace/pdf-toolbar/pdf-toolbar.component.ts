import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

import { TranslationService } from '../../../../core/i18n/translation.service';

export type PdfEditorTool =
  | 'highlight'
  | 'draw';

export type PdfMoreAction =
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

  readonly translation =
    inject(TranslationService);

  private readonly searchInput =
    viewChild<ElementRef<HTMLInputElement>>(
      'searchInput',
    );

  readonly page =
    input.required<number>();

  readonly pageCount =
    input.required<number>();

  readonly activeTool =
    input<PdfEditorTool | null>(null);

  readonly canUndo =
    input(false);

  readonly canRedo =
    input(false);

  readonly searchVisible =
    input(false);

  readonly searchQuery =
    input('');

  readonly searchCurrent =
    input(0);

  readonly searchTotal =
    input(0);

  readonly previousPage =
    output<void>();

  readonly nextPage =
    output<void>();

  readonly zoomOut =
    output<void>();

  readonly zoomIn =
    output<void>();

  readonly fitPageWidth =
    output<void>();

  readonly selectTool =
    output<PdfEditorTool>();

  readonly undo =
    output<void>();

  readonly redo =
    output<void>();

  readonly toggleSidebar =
    output<void>();

  readonly moreAction =
    output<PdfMoreAction>();

  readonly openSearch =
    output<void>();

  readonly closeSearch =
    output<void>();

  readonly searchQueryChange =
    output<string>();

  readonly searchNext =
    output<void>();

  readonly searchPrevious =
    output<void>();

  readonly moreOpen =
    signal(false);

  private readonly numberFormatter =
    computed(
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

  constructor() {
    effect(() => {
      if (!this.searchVisible()) {
        return;
      }

      requestAnimationFrame(() => {
        const input =
          this.searchInput()
            ?.nativeElement;

        if (!input) {
          return;
        }

        input.focus();

        if (input.value.length > 0) {
          input.select();
        }
      });
    });
  }

  formatNumber(
    value: number,
  ): string {
    return this.numberFormatter().format(
      value,
    );
  }

  toggleMore(): void {
    this.moreOpen.update(
      (open) => !open,
    );
  }

  selectMoreAction(
    action:
      | PdfMoreAction
      | 'search',
  ): void {
    this.moreOpen.set(false);

    if (action === 'search') {
      this.openSearch.emit();
      return;
    }

    this.moreAction.emit(action);
  }

  onSearchInput(
    event: Event,
  ): void {
    const target =
      event.target;

    if (
      !(target instanceof HTMLInputElement)
    ) {
      return;
    }

    this.searchQueryChange.emit(
      target.value,
    );
  }

  onSearchKeydown(
    event: KeyboardEvent,
  ): void {
    if (event.key !== 'Enter') {
      return;
    }

    event.preventDefault();

    if (event.shiftKey) {
      this.searchPrevious.emit();
      return;
    }

    this.searchNext.emit();
  }

  @HostListener(
    'document:keydown.escape',
  )
  onEscape(): void {
    if (this.searchVisible()) {
      this.closeSearch.emit();
      return;
    }

    this.moreOpen.set(false);
  }

  @HostListener(
    'document:pointerdown',
    ['$event'],
  )
  onDocumentPointerDown(
    event: PointerEvent,
  ): void {
    if (!this.moreOpen()) {
      return;
    }

    const target =
      event.target;

    if (
      target instanceof Node &&
      !this.element.nativeElement.contains(
        target,
      )
    ) {
      this.moreOpen.set(false);
    }
  }
}