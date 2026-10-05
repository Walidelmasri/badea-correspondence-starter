import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';

import {
  NgxExtendedPdfViewerModule,
  NgxExtendedPdfViewerService,
  PdfDocumentInfo,
  PdfDocumentPropertiesExtractor,
  PdfSidebarView,
  PDFNotificationService,
} from 'ngx-extended-pdf-viewer';

import { TranslationService } from '../../../core/i18n/translation.service';

import {
  PdfEditorTool,
  PdfMoreAction,
  PdfToolbarComponent,
} from './pdf-toolbar/pdf-toolbar.component';

import {
  PdfDocumentProperties,
  PdfPropertiesDialogComponent,
} from './pdf-properties-dialog/pdf-properties-dialog.component';

import { PdfInkTool } from '../../annotation/domain/ink.model';
import { PdfInkOverlayDirective } from '../../annotation/pdf/pdf-ink-overlay.directive';

type PdfZoomSetting =
  | 'page-width'
  | number
  | undefined;

interface FindMatchesCount {
  readonly current: number;
  readonly total: number;
}

interface ElementScrollSnapshot {
  readonly element: HTMLElement;
  readonly scrollLeft: number;
  readonly scrollTop: number;
}

interface OuterScrollSnapshot {
  readonly windowX: number;
  readonly windowY: number;
  readonly elements: readonly ElementScrollSnapshot[];
}

const PDF_EDITOR_MODE_NONE = 0;

@Component({
  selector: 'app-pdf-workspace',
  standalone: true,
  imports: [
    NgxExtendedPdfViewerModule,
    PdfToolbarComponent,
    PdfPropertiesDialogComponent,
    PdfInkOverlayDirective,
  ],
  templateUrl: './pdf-workspace.component.html',
  styleUrl: './pdf-workspace.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfWorkspaceComponent implements OnDestroy {
  private readonly pdfViewerService =
    inject(NgxExtendedPdfViewerService);

  private readonly pdfNotificationService =
    inject(PDFNotificationService);

  private readonly hostElement =
    inject<ElementRef<HTMLElement>>(ElementRef);

  private readonly propertiesExtractor =
    new PdfDocumentPropertiesExtractor();

  readonly translation =
    inject(TranslationService);

  readonly src =
    input.required<string>();

  readonly filename =
    input.required<string>();

  /**
   * Stable logical identity for annotation state.
   *
   * Do not use the source URL because multiple
   * mock attachments can share the same PDF.
   */
  readonly documentKey =
    input.required<string>();

  readonly page =
    signal(1);

  readonly pageCount =
    signal(0);

  readonly zoom =
    signal<PdfZoomSetting>('page-width');

  private readonly currentZoomFactor =
    signal(1);

  private readonly inkOverlay =
    viewChild(PdfInkOverlayDirective);

  readonly activeTool =
    signal<PdfEditorTool | null>(null);

  readonly inkTool =
    computed<PdfInkTool | null>(() => {
      switch (this.activeTool()) {
        case 'draw':
          return 'pen';

        case 'highlight':
          return 'highlighter';

        default:
          return null;
      }
    });

  readonly canUndo =
    computed(
      () =>
        this.inkOverlay()?.canUndo() ??
        false,
    );

  readonly canRedo =
    computed(
      () =>
        this.inkOverlay()?.canRedo() ??
        false,
    );

  readonly sidebarVisible =
    signal<boolean | undefined>(false);

  readonly sidebarView =
    PdfSidebarView.THUMBS;

  readonly rotation =
    signal<0 | 90 | 180 | 270>(0);

  /*
   * Search UI is owned by this component.
   *
   * PDF.js remains responsible for finding,
   * highlighting and navigating PDF text.
   */
  readonly searchVisible =
    signal(false);

  readonly searchQuery =
    signal('');

  readonly searchCurrent =
    signal(0);

  readonly searchTotal =
    signal(0);

  private searchTimer:
    ReturnType<typeof setTimeout> | null =
    null;

  /*
   * PDF.js legitimately scrolls its own internal
   * viewer to make a match visible.
   *
   * What we do not want is that navigation
   * dragging the surrounding President page.
   */
  private outerScrollSnapshot:
    OuterScrollSnapshot | null =
    null;

  private outerScrollRestoreFrame:
    number | null =
    null;

  private outerScrollRestoreTimer:
    ReturnType<typeof setTimeout> | null =
    null;

  readonly documentProperties =
    signal<PdfDocumentProperties | null>(
      null,
    );

  readonly propertiesVisible =
    signal(false);

  ngOnDestroy(): void {
    this.clearSearchTimer();
    this.cancelOuterScrollRestoreTasks();

    this.outerScrollSnapshot = null;
  }

  toggleSidebar(): void {
    this.sidebarVisible.update(
      (visible) => !visible,
    );
  }

  undo(): void {
    this.inkOverlay()?.undo();
  }

  redo(): void {
    this.inkOverlay()?.redo();
  }

  previousPage(): void {
    this.page.update(
      (currentPage) =>
        Math.max(
          1,
          currentPage - 1,
        ),
    );
  }

  nextPage(): void {
    const totalPages =
      this.pageCount();

    if (totalPages < 1) {
      return;
    }

    this.page.update(
      (currentPage) =>
        Math.min(
          totalPages,
          currentPage + 1,
        ),
    );
  }

  onPageChange(
    pageNumber: number | undefined,
  ): void {
    if (
      pageNumber === undefined ||
      !Number.isInteger(pageNumber) ||
      pageNumber < 1
    ) {
      return;
    }

    this.page.set(pageNumber);
  }

  zoomOut(): void {
    this.adjustZoom(-0.1);
  }

  zoomIn(): void {
    this.adjustZoom(0.1);
  }

  fitPageWidth(): void {
    this.zoom.set(undefined);

    requestAnimationFrame(() => {
      this.zoom.set(
        'page-width',
      );
    });
  }

  onCurrentZoomFactorChange(
    zoomFactor: number,
  ): void {
    if (
      !Number.isFinite(zoomFactor) ||
      zoomFactor <= 0
    ) {
      return;
    }

    this.currentZoomFactor.set(
      zoomFactor,
    );
  }

  private adjustZoom(
    delta: number,
  ): void {
    const currentZoom =
      this.zoom();

    const currentFactor =
      typeof currentZoom === 'number'
        ? currentZoom / 100
        : this.currentZoomFactor();

    const nextFactor =
      Math.min(
        4,
        Math.max(
          0.25,
          currentFactor + delta,
        ),
      );

    this.zoom.set(
      Math.round(
        nextFactor * 100,
      ),
    );
  }

  /*
   * Annotation tools
   */

  selectTool(
    tool: PdfEditorTool,
  ): void {
    if (
      this.activeTool() === tool
    ) {
      this.deactivateInkTool();
      return;
    }

    /*
     * Draw and Highlight use our custom canvas.
     *
     * Keep PDF.js annotation editing disabled so
     * it cannot compete for mouse/touch events.
     */
    this.pdfViewerService
      .switchAnnotationEdtorMode(
        PDF_EDITOR_MODE_NONE,
      );

    this.activeTool.set(tool);
  }

  private deactivateInkTool(): void {
    this.activeTool.set(null);

    this.pdfViewerService
      .switchAnnotationEdtorMode(
        PDF_EDITOR_MODE_NONE,
      );
  }

  /*
   * Document lifecycle
   */

  onPdfLoaded(): void {
    this.activeTool.set(null);

    this.documentProperties.set(
      null,
    );

    this.propertiesVisible.set(
      false,
    );

    this.resetSearchState();
  }

  /*
   * Search
   */

  openSearch(): void {
    this.searchVisible.set(true);

    /*
     * Deliberately do not autofocus.
     *
     * Safari/iPad can alter the viewport when
     * an input receives programmatic focus.
     */
  }

  closeSearch(): void {
    this.clearSearchTimer();

    this.searchVisible.set(false);

    this.searchQuery.set('');
    this.searchCurrent.set(0);
    this.searchTotal.set(0);

    if (!this.pdfReady()) {
      return;
    }

    /*
     * Empty query removes PDF.js highlights.
     */
    this.runPdfSearch('');
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

    this.onSearchQueryChange(
      target.value,
    );
  }

  onSearchQueryChange(
    query: string,
  ): void {
    this.searchQuery.set(query);

    this.searchCurrent.set(0);
    this.searchTotal.set(0);

    this.clearSearchTimer();

    const normalized =
      query.trim();

    if (!normalized) {
      if (this.pdfReady()) {
        this.runPdfSearch('');
      }

      return;
    }

    /*
     * Avoid running a full PDF text search
     * for every individual keystroke.
     */
    this.searchTimer =
      setTimeout(
        () => {
          this.searchTimer = null;

          this.runPdfSearch(
            normalized,
          );
        },
        180,
      );
  }

  searchNext(): void {
    const query =
      this.searchQuery().trim();

    if (!query) {
      return;
    }

    /*
     * Enter may be pressed before the debounce
     * timer fires. In that case perform the first
     * search rather than jumping to result #2.
     */
    if (
      this.flushPendingSearch()
    ) {
      return;
    }

    if (!this.pdfReady()) {
      return;
    }

    this.captureOuterScroll();

    this.pdfViewerService
      .findNext();

    this.scheduleOuterScrollRestore();
  }

  searchPrevious(): void {
    const query =
      this.searchQuery().trim();

    if (!query) {
      return;
    }

    if (
      this.flushPendingSearch()
    ) {
      return;
    }

    if (!this.pdfReady()) {
      return;
    }

    this.captureOuterScroll();

    this.pdfViewerService
      .findPrevious();

    this.scheduleOuterScrollRestore();
  }

  onFindMatchesCount(
    event: unknown,
  ): void {
    const count =
      this.extractFindMatchesCount(
        event,
      );

    if (!count) {
      return;
    }

    this.searchCurrent.set(
      count.current,
    );

    this.searchTotal.set(
      count.total,
    );
  }

  onFindStateChange(): void {
    /*
     * PDF.js can finish moving its internal
     * viewer after the original find call.
     *
     * Restore only the scroll positions outside
     * this PDF workspace.
     */
    this.scheduleOuterScrollRestore();
  }

  private runPdfSearch(
    query: string,
  ): void {
    if (!this.pdfReady()) {
      return;
    }

    this.captureOuterScroll();

    this.pdfViewerService.find(
      query,
    );

    this.scheduleOuterScrollRestore();
  }

  private flushPendingSearch():
    boolean {
    if (
      this.searchTimer === null
    ) {
      return false;
    }

    this.clearSearchTimer();

    const query =
      this.searchQuery().trim();

    if (
      query &&
      this.pdfReady()
    ) {
      this.runPdfSearch(
        query,
      );
    }

    return true;
  }

  private clearSearchTimer():
    void {
    if (
      this.searchTimer === null
    ) {
      return;
    }

    clearTimeout(
      this.searchTimer,
    );

    this.searchTimer = null;
  }

  private resetSearchState():
    void {
    this.clearSearchTimer();
    this.cancelOuterScrollRestoreTasks();

    this.outerScrollSnapshot =
      null;

    this.searchVisible.set(
      false,
    );

    this.searchQuery.set('');
    this.searchCurrent.set(0);
    this.searchTotal.set(0);
  }

  /*
   * Capture every scrollable ancestor outside
   * the PDF workspace.
   *
   * The PDF viewer itself is a descendant of
   * this component, so it is intentionally not
   * captured. PDF.js remains free to scroll it.
   */
  private captureOuterScroll():
    void {
    const elements:
      ElementScrollSnapshot[] =
      [];

    let current =
      this.hostElement
        .nativeElement
        .parentElement;

    while (current) {
      if (
        this.isScrollableElement(
          current,
        )
      ) {
        elements.push({
          element: current,

          scrollLeft:
            current.scrollLeft,

          scrollTop:
            current.scrollTop,
        });
      }

      current =
        current.parentElement;
    }

    this.outerScrollSnapshot = {
      windowX:
        window.scrollX,

      windowY:
        window.scrollY,

      elements,
    };
  }

  private isScrollableElement(
    element: HTMLElement,
  ): boolean {
    const style =
      getComputedStyle(
        element,
      );

    const permitsScroll = (
      overflow: string,
    ): boolean =>
      overflow === 'auto' ||
      overflow === 'scroll' ||
      overflow === 'overlay';

    const scrollableX =
      permitsScroll(
        style.overflowX,
      ) &&
      element.scrollWidth >
        element.clientWidth;

    const scrollableY =
      permitsScroll(
        style.overflowY,
      ) &&
      element.scrollHeight >
        element.clientHeight;

    return (
      scrollableX ||
      scrollableY
    );
  }

  private scheduleOuterScrollRestore():
    void {
    if (
      !this.outerScrollSnapshot
    ) {
      return;
    }

    this.cancelOuterScrollRestoreTasks();

    /*
     * First restoration catches the immediate
     * PDF.js find navigation.
     */
    this.outerScrollRestoreFrame =
      requestAnimationFrame(
        () => {
          this.outerScrollRestoreFrame =
            null;

          this.restoreOuterScroll();
        },
      );

    /*
     * PDF.js performs part of find navigation
     * asynchronously. Restore again after that
     * second phase.
     */
    this.outerScrollRestoreTimer =
      setTimeout(
        () => {
          this.outerScrollRestoreTimer =
            null;

          this.restoreOuterScroll();
        },
        80,
      );
  }

  private cancelOuterScrollRestoreTasks():
    void {
    if (
      this.outerScrollRestoreFrame !==
      null
    ) {
      cancelAnimationFrame(
        this.outerScrollRestoreFrame,
      );

      this.outerScrollRestoreFrame =
        null;
    }

    if (
      this.outerScrollRestoreTimer !==
      null
    ) {
      clearTimeout(
        this.outerScrollRestoreTimer,
      );

      this.outerScrollRestoreTimer =
        null;
    }
  }

  private restoreOuterScroll():
    void {
    const snapshot =
      this.outerScrollSnapshot;

    if (!snapshot) {
      return;
    }

    for (
      const entry of
      snapshot.elements
    ) {
      if (
        !entry.element.isConnected
      ) {
        continue;
      }

      entry.element.scrollLeft =
        entry.scrollLeft;

      entry.element.scrollTop =
        entry.scrollTop;
    }

    window.scrollTo(
      snapshot.windowX,
      snapshot.windowY,
    );
  }

  private extractFindMatchesCount(
    event: unknown,
  ): FindMatchesCount | null {
    if (
      !event ||
      typeof event !== 'object'
    ) {
      return null;
    }

    const wrapper =
      event as Record<
        string,
        unknown
      >;

    const nested =
      wrapper['matchesCount'];

    const source =
      nested &&
      typeof nested === 'object'
        ? nested as Record<
            string,
            unknown
          >
        : wrapper;

    const current =
      source['current'];

    const total =
      source['total'];

    if (
      typeof current !== 'number' ||
      typeof total !== 'number'
    ) {
      return null;
    }

    return {
      current:
        Math.max(
          0,
          Math.trunc(current),
        ),

      total:
        Math.max(
          0,
          Math.trunc(total),
        ),
    };
  }

  private pdfReady():
    boolean {
    return Boolean(
      this.pdfNotificationService
        .onPDFJSInitSignal()
        ?.pdfDocument,
    );
  }

  /*
   * More menu
   */

  handleMoreAction(
    action: PdfMoreAction,
  ): void {
    switch (action) {
      case 'rotate-clockwise':
        this.rotateDocument(
          90,
        );
        break;

      case 'rotate-counterclockwise':
        this.rotateDocument(
          -90,
        );
        break;

      case 'properties':
        void this
          .openDocumentProperties();

        break;

      case 'print':
        this.printDocument();
        break;
    }
  }

  /*
   * Print stays untouched for this change.
   * We fix iPad printing next.
   */
  private printDocument():
    void {
    if (!this.pdfReady()) {
      return;
    }

    this.pdfViewerService.print();
  }

  private rotateDocument(
    degrees: 90 | -90,
  ): void {
    const nextRotation =
      (
        this.rotation() +
        degrees +
        360
      ) % 360;

    if (
      nextRotation === 0 ||
      nextRotation === 90 ||
      nextRotation === 180 ||
      nextRotation === 270
    ) {
      this.rotation.set(
        nextRotation,
      );
    }
  }

  /*
   * Properties
   */

  async openDocumentProperties():
    Promise<void> {
    const application =
      this.pdfNotificationService
        .onPDFJSInitSignal();

    const document =
      application?.pdfDocument;

    if (
      !application ||
      !document
    ) {
      return;
    }

    const basicProperties:
      PdfDocumentProperties = {
      filename:
        this.filename(),

      sizeBytes: null,

      pageCount:
        document.numPages,
    };

    this.documentProperties.set(
      basicProperties,
    );

    try {
      const [
        rawMetadata,
        downloadInfo,
      ] =
        await Promise.all([
          this.propertiesExtractor
            .getDocumentProperties(
              application,
            ),

          document
            .getDownloadInfo(),
        ]);

      const metadata =
        rawMetadata as
        PdfDocumentInfo;

      /*
       * The attachment may have changed while
       * metadata was loading.
       */
      if (
        this.pdfNotificationService
          .onPDFJSInitSignal()
          ?.pdfDocument !==
        document
      ) {
        return;
      }

      this.documentProperties.set({
        ...basicProperties,

        sizeBytes:
          downloadInfo.length,

        title:
          metadata.title ??
          null,

        author:
          metadata.author ??
          null,

        createdAt:
          metadata.creationDate
            instanceof Date &&
          Number.isFinite(
            metadata.creationDate
              .getTime(),
          )
            ? metadata.creationDate
            : null,

        modifiedAt:
          metadata.modificationDate
            instanceof Date &&
          Number.isFinite(
            metadata.modificationDate
              .getTime(),
          )
            ? metadata.modificationDate
            : null,
      });
    } catch {
      /*
       * Optional metadata failure should never
       * prevent basic properties being shown.
       */
      if (
        this.pdfNotificationService
          .onPDFJSInitSignal()
          ?.pdfDocument !==
        document
      ) {
        return;
      }

      this.documentProperties.set(
        basicProperties,
      );
    }

    this.propertiesVisible.set(
      true,
    );
  }

  closeDocumentProperties():
    void {
    this.propertiesVisible.set(
      false,
    );
  }

  /*
   * Export
   */

  async exportDocument():
    Promise<Blob | undefined> {
    /*
     * Disable the custom drawing surface before
     * asking PDF.js for the underlying document.
     *
     * Custom ink persistence/flattening comes later.
     */
    this.deactivateInkTool();

    await this
      .waitForNextFrame();

    return this.pdfViewerService
      .getCurrentDocumentAsBlob();
  }

  private waitForNextFrame():
    Promise<void> {
    return new Promise(
      (resolve) => {
        requestAnimationFrame(
          () => {
            resolve();
          },
        );
      },
    );
  }
}