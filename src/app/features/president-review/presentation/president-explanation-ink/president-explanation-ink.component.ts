import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

import { TranslationService } from '../../../../core/i18n/translation.service';

import {
  InkSurfaceTool,
} from '../../../../shared/annotation/domain/ink-surface-stroke.model';

import {
  InkSurfaceComponent,
} from '../../../../shared/annotation/ui/ink-surface/ink-surface.component';

import {
  PresidentExplanationInkDocument,
} from '../../domain/president-explanation.model';

type ExplanationInkTool =
  Extract<
    InkSurfaceTool,
    'pen' | 'eraser'
  >;

@Component({
  selector: 'app-president-explanation-ink',
  standalone: true,
  imports: [
    InkSurfaceComponent,
  ],
  templateUrl:
    './president-explanation-ink.component.html',
  styleUrl:
    './president-explanation-ink.component.scss',
  changeDetection:
    ChangeDetectionStrategy.OnPush,
})
export class PresidentExplanationInkComponent {
  readonly translation =
    inject(TranslationService);

  readonly document =
    input.required<PresidentExplanationInkDocument>();

  readonly documentChange =
    output<PresidentExplanationInkDocument>();

  readonly activePageIndex =
    signal(0);

  readonly tool =
    signal<ExplanationInkTool>('pen');

  private readonly inkSurface =
    viewChild(InkSurfaceComponent);

  private pageSequence = 1;

  readonly pageCount =
    computed(
      () =>
        this.document().pages.length,
    );

  readonly activePage =
    computed(() => {
      const pages =
        this.document().pages;

      const index =
        Math.min(
          this.activePageIndex(),
          Math.max(
            pages.length - 1,
            0,
          ),
        );

      return pages[index] ?? null;
    });

  readonly canGoPrevious =
    computed(
      () =>
        this.activePageIndex() > 0,
    );

  readonly canGoNext =
    computed(
      () =>
        this.activePageIndex() <
        this.pageCount() - 1,
    );

  readonly canUndo =
    computed(
      () =>
        this.inkSurface()
          ?.canUndo() ??
        false,
    );

  readonly canRedo =
    computed(
      () =>
        this.inkSurface()
          ?.canRedo() ??
        false,
    );

  setTool(
    tool: ExplanationInkTool,
  ): void {
    this.tool.set(tool);
  }

  undo(): void {
    this.inkSurface()?.undo();
  }

  redo(): void {
    this.inkSurface()?.redo();
  }

  previousPage(): void {
    if (!this.canGoPrevious()) {
      return;
    }

    this.activePageIndex.update(
      (index) => index - 1,
    );
  }

  nextPage(): void {
    if (!this.canGoNext()) {
      return;
    }

    this.activePageIndex.update(
      (index) => index + 1,
    );
  }

  addPage(): void {
    const current =
      this.document();

    const pageNumber =
      current.pages.length + 1;

    const nextPage = {
      id:
        `explanation-page-${pageNumber}-` +
        `${++this.pageSequence}`,

      strokes: [],
    };

    const next:
      PresidentExplanationInkDocument = {
        ...current,

        pages: [
          ...current.pages,
          nextPage,
        ],
      };

    this.documentChange.emit(next);

    this.activePageIndex.set(
      next.pages.length - 1,
    );

    this.tool.set('pen');
  }

  onStrokesChange(
    strokes:
      PresidentExplanationInkDocument[
        'pages'
      ][number]['strokes'],
  ): void {
    const active =
      this.activePage();

    if (!active) {
      return;
    }

    const current =
      this.document();

    const pages =
      current.pages.map(
        (page) =>
          page.id === active.id
            ? {
                ...page,
                strokes,
              }
            : page,
      );

    this.documentChange.emit({
      ...current,
      pages,
    });
  }
}