import {
  AfterViewInit,
  Directive,
  ElementRef,
  OnDestroy,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';

import { InkDocumentStore } from '../domain/ink-document-store';
import {
  InkStroke,
  PdfInkTool,
  PdfRotation,
} from '../domain/ink.model';
import { PdfInkPageSurface } from './pdf-ink-page-surface';

/**
 * Bridges the reusable ink model to PDF.js rendered pages.
 *
 * PDF.js DOM knowledge is isolated here so PdfWorkspaceComponent and
 * feature pages never manipulate viewer internals directly.
 */
@Directive({
  selector: '[appPdfInkOverlay]',
  standalone: true,
  exportAs: 'pdfInkOverlay',
})
export class PdfInkOverlayDirective
  implements AfterViewInit, OnDestroy
{
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly store = new InkDocumentStore();

  readonly documentKey = input.required<string>();
  readonly inkTool = input<PdfInkTool | null>(null);
  readonly inkRotation = input<PdfRotation>(0);

  readonly strokeCommitted = output<void>();

  readonly canUndo = signal(false);
  readonly canRedo = signal(false);

  private readonly surfaces =
    new Map<HTMLElement, PdfInkPageSurface>();

  private readonly mutationObserver = new MutationObserver(() => {
    this.syncRenderedPages();
  });

  private viewReady = false;

  constructor() {
    effect(() => {
      this.documentKey();
      this.updateHistoryState();

      if (this.viewReady) {
        this.setInteractionEnabled(false);
        this.redrawAllPages();
        this.setInteractionEnabled(this.inkTool() !== null);
      }
    });

    effect(() => {
      const enabled = this.inkTool() !== null;

      if (this.viewReady) {
        this.setInteractionEnabled(enabled);
      }
    });

    effect(() => {
      this.inkRotation();

      if (this.viewReady) {
        this.redrawAllPages();
      }
    });
  }

  ngAfterViewInit(): void {
    this.viewReady = true;

    this.mutationObserver.observe(
      this.host.nativeElement,
      {
        childList: true,
        subtree: true,
      },
    );

    this.syncRenderedPages();
  }

  ngOnDestroy(): void {
    this.mutationObserver.disconnect();

    for (const surface of this.surfaces.values()) {
      surface.destroy();
    }

    this.surfaces.clear();
  }

  undo(): void {
    const stroke = this.store.undo(this.documentKey());

    if (!stroke) {
      return;
    }

    this.updateHistoryState();
    this.redrawPage(stroke.pageNumber);
  }

  redo(): void {
    const stroke = this.store.redo(this.documentKey());

    if (!stroke) {
      return;
    }

    this.updateHistoryState();
    this.redrawPage(stroke.pageNumber);
  }

  private syncRenderedPages(): void {
    this.removeDetachedSurfaces();

    const pages =
      this.host.nativeElement.querySelectorAll<HTMLElement>(
        '.page[data-page-number]',
      );

    for (const pageElement of pages) {
      if (this.surfaces.has(pageElement)) {
        continue;
      }

      const pageNumber = Number(
        pageElement.dataset['pageNumber'],
      );

      if (!Number.isInteger(pageNumber) || pageNumber < 1) {
        continue;
      }

      const surface = new PdfInkPageSurface(
        pageElement,
        pageNumber,
        {
          getTool: () => this.inkTool(),
          getRotation: () => this.inkRotation(),
          getCommittedStrokes: () =>
            this.store.pageStrokes(
              this.documentKey(),
              pageNumber,
            ),
          onStrokeCommitted: (stroke) =>
            this.commitStroke(stroke),
        },
      );

      surface.setInteractionEnabled(
        this.inkTool() !== null,
      );

      this.surfaces.set(pageElement, surface);
    }
  }

  private commitStroke(stroke: InkStroke): void {
    this.store.add(this.documentKey(), stroke);
    this.updateHistoryState();
    this.strokeCommitted.emit();
  }

  private setInteractionEnabled(enabled: boolean): void {
    for (const surface of this.surfaces.values()) {
      surface.setInteractionEnabled(enabled);
    }
  }

  private redrawAllPages(): void {
    for (const surface of this.surfaces.values()) {
      surface.redraw();
    }
  }

  private redrawPage(pageNumber: number): void {
    for (const surface of this.surfaces.values()) {
      if (surface.pageNumber === pageNumber) {
        surface.redraw();
      }
    }
  }

  private removeDetachedSurfaces(): void {
    for (const [pageElement, surface] of this.surfaces) {
      if (
        pageElement.isConnected &&
        this.host.nativeElement.contains(pageElement) &&
        surface.isAttached()
      ) {
        continue;
      }

      surface.destroy();
      this.surfaces.delete(pageElement);
    }
  }

  private updateHistoryState(): void {
    const key = this.documentKey();

    this.canUndo.set(this.store.canUndo(key));
    this.canRedo.set(this.store.canRedo(key));
  }
}
