import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';

import {
  IEventBus,
  NgxExtendedPdfViewerModule,
  NgxExtendedPdfViewerService,
  PdfSidebarView,
  PDFNotificationService,
  PdfDocumentInfo,
  PdfDocumentPropertiesExtractor,
} from 'ngx-extended-pdf-viewer';

import { TranslationService } from '../../../core/i18n/translation.service';

import { PdfInkTool } from '../../annotation/domain/ink.model';
import { PdfInkOverlayDirective } from '../../annotation/pdf/pdf-ink-overlay.directive';

import {
  PdfEditorTool,
  PdfMoreAction,
  PdfToolbarComponent,
} from './pdf-toolbar/pdf-toolbar.component';

import {
  PdfDocumentProperties,
  PdfPropertiesDialogComponent,
} from './pdf-properties-dialog/pdf-properties-dialog.component';

type PdfZoomSetting = 'page-width' | number | undefined;

type HistoryOwner = 'pdf' | 'ink';

const PDF_EDITOR_MODE_NONE = 0;
const PDF_EDITOR_MODE_TEXT = 3;

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
  // Dependencies

  private readonly pdfViewerService =
    inject(NgxExtendedPdfViewerService);

  private readonly pdfNotificationService =
    inject(PDFNotificationService);

  private readonly propertiesExtractor =
    new PdfDocumentPropertiesExtractor();

  readonly translation = inject(TranslationService);

  // Inputs

  readonly src = input.required<string>();

  readonly filename = input.required<string>();

  /**
   * Logical document/attachment identity.
   *
   * Do not use src as the identity because separate attachments
   * can legitimately point at the same physical PDF during testing.
   */
  readonly documentKey = input.required<string>();

  // Document state

  readonly page = signal(1);

  readonly pageCount = signal(0);

  readonly zoom =
    signal<PdfZoomSetting>('page-width');

  private readonly currentZoomFactor =
    signal(1);

  // Editor state

  readonly activeTool =
    signal<PdfEditorTool | null>(null);

  private readonly editorLayerReady =
    signal(false);

  private pendingTool: PdfEditorTool | null = null;

  /**
   * Custom ink overlay rendered above the PDF.js page layers.
   */
  private readonly inkOverlay =
    viewChild(PdfInkOverlayDirective);

  /**
   * PDF.js owns history for the Text editor.
   */
  private readonly pdfCanUndo = signal(false);

  private readonly pdfCanRedo = signal(false);

  /**
   * Tracks which annotation engine most recently changed state.
   *
   * This allows the shared toolbar to expose one Undo/Redo pair
   * while Text remains PDF.js-based and Pen/Highlighter use our
   * custom ink engine.
   */
  private readonly lastHistoryOwner =
    signal<HistoryOwner>('pdf');

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

  readonly canUndo = computed(() => {
    return (
      this.pdfCanUndo() ||
      (this.inkOverlay()?.canUndo() ?? false)
    );
  });

  readonly canRedo = computed(() => {
    return (
      this.pdfCanRedo() ||
      (this.inkOverlay()?.canRedo() ?? false)
    );
  });

  // PDF.js annotation history

  private historyEventBus: IEventBus | null = null;

  private readonly onEditingStatesChanged = (
    event: {
      details?: {
        hasSomethingToUndo?: boolean;
        hasSomethingToRedo?: boolean;
      };
    },
  ): void => {
    const details = event?.details;

    if (
      typeof details?.hasSomethingToUndo ===
      'boolean'
    ) {
      this.pdfCanUndo.set(
        details.hasSomethingToUndo,
      );
    }

    if (
      typeof details?.hasSomethingToRedo ===
      'boolean'
    ) {
      this.pdfCanRedo.set(
        details.hasSomethingToRedo,
      );
    }
  };

  // Sidebar

  readonly sidebarVisible =
    signal<boolean | undefined>(false);

  readonly sidebarView = PdfSidebarView.THUMBS;

  // Search and rotation

  readonly findbarVisible = signal(false);

  readonly rotation =
    signal<0 | 90 | 180 | 270>(0);

  // Document properties

  readonly documentProperties =
    signal<PdfDocumentProperties | null>(null);

  readonly propertiesVisible = signal(false);

  // Sidebar actions

  toggleSidebar(): void {
    this.sidebarVisible.update(
      (visible) => !visible,
    );
  }

  // Undo / redo

  undo(): void {
    if (!this.canUndo()) {
      return;
    }

    const inkOverlay = this.inkOverlay();

    const preferInk =
      this.lastHistoryOwner() === 'ink';

    if (
      inkOverlay?.canUndo() &&
      (preferInk || !this.pdfCanUndo())
    ) {
      inkOverlay.undo();

      this.lastHistoryOwner.set('ink');

      return;
    }

    if (!this.pdfCanUndo()) {
      return;
    }

    this.historyEventBus?.dispatch(
      'editingaction',
      {
        source: this,
        name: 'undo',
      },
    );

    this.lastHistoryOwner.set('pdf');
  }

  redo(): void {
    if (!this.canRedo()) {
      return;
    }

    const inkOverlay = this.inkOverlay();

    const preferInk =
      this.lastHistoryOwner() === 'ink';

    if (
      inkOverlay?.canRedo() &&
      (preferInk || !this.pdfCanRedo())
    ) {
      inkOverlay.redo();

      this.lastHistoryOwner.set('ink');

      return;
    }

    if (!this.pdfCanRedo()) {
      return;
    }

    this.historyEventBus?.dispatch(
      'editingaction',
      {
        source: this,
        name: 'redo',
      },
    );

    this.lastHistoryOwner.set('pdf');
  }

  /**
   * Called whenever our custom ink engine commits a completed
   * Pen or Highlighter stroke.
   */
  onInkStrokeCommitted(): void {
    this.lastHistoryOwner.set('ink');
  }

  private connectAnnotationHistory(): void {
    this.disconnectAnnotationHistory();

    const application =
      this.pdfNotificationService
        .onPDFJSInitSignal();

    if (!application?.pdfDocument) {
      return;
    }

    this.historyEventBus =
      application.eventBus;

    this.historyEventBus.on(
      'editingstateschanged',
      this.onEditingStatesChanged,
    );
  }

  private disconnectAnnotationHistory(): void {
    this.historyEventBus?.off(
      'editingstateschanged',
      this.onEditingStatesChanged,
    );

    this.historyEventBus = null;

    this.pdfCanUndo.set(false);
    this.pdfCanRedo.set(false);
  }

  ngOnDestroy(): void {
    this.disconnectAnnotationHistory();
  }

  // Page navigation

  previousPage(): void {
    this.page.update(
      (currentPage) =>
        Math.max(1, currentPage - 1),
    );
  }

  nextPage(): void {
    const totalPages = this.pageCount();

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

  // Zoom

  zoomOut(): void {
    this.adjustZoom(-0.1);
  }

  zoomIn(): void {
    this.adjustZoom(0.1);
  }

  fitPageWidth(): void {
    this.zoom.set(undefined);

    requestAnimationFrame(() => {
      this.zoom.set('page-width');
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

    this.currentZoomFactor.set(zoomFactor);
  }

  private adjustZoom(delta: number): void {
    const currentZoom = this.zoom();

    const currentFactor =
      typeof currentZoom === 'number'
        ? currentZoom / 100
        : this.currentZoomFactor();

    const nextFactor = Math.min(
      4,
      Math.max(
        0.25,
        currentFactor + delta,
      ),
    );

    this.zoom.set(
      Math.round(nextFactor * 100),
    );
  }

  // Annotation tools

  selectTool(tool: PdfEditorTool): void {
    /**
     * Clicking the active tool again exits annotation mode.
     *
     * This is particularly important on iPad:
     *
     * Draw ON  -> canvas receives touch/Pencil input.
     * Draw OFF -> PDF receives normal scroll/navigation input.
     */
    if (this.activeTool() === tool) {
      this.deactivateEditor();

      return;
    }

    /**
     * Pen and Highlighter no longer use PDF.js's annotation
     * editor. They use our custom overlay.
     */
    if (
      tool === 'draw' ||
      tool === 'highlight'
    ) {
      this.activateInkEditor(tool);

      return;
    }

    /**
     * Text remains implemented by PDF.js because it already
     * behaved acceptably during the physical iPad test.
     */
    this.activateTextEditorWhenReady();
  }

  onAnnotationEditorLayerRendered(): void {
    if (this.editorLayerReady()) {
      return;
    }

    this.editorLayerReady.set(true);

    /**
     * Only Text requires PDF.js's annotation-editor layer.
     *
     * Pen and Highlighter are completely independent of it.
     */
    if (this.pendingTool !== 'text') {
      return;
    }

    this.pendingTool = null;

    queueMicrotask(() => {
      this.activateTextEditor();
    });
  }

  private activateInkEditor(
    tool: Extract<
      PdfEditorTool,
      'draw' | 'highlight'
    >,
  ): void {
    this.pendingTool = null;

    /**
     * Ensure PDF.js is not running its own annotation editor
     * while our canvas owns the interaction.
     */
    this.pdfViewerService
      .switchAnnotationEdtorMode(
        PDF_EDITOR_MODE_NONE,
      );

    this.lastHistoryOwner.set('ink');

    this.activeTool.set(tool);
  }

  private activateTextEditorWhenReady(): void {
    /**
     * Disable any existing PDF.js editor first so transitions
     * between our ink layer and PDF.js Text remain deterministic.
     */
    this.pdfViewerService
      .switchAnnotationEdtorMode(
        PDF_EDITOR_MODE_NONE,
      );

    /**
     * Setting activeTool to null immediately disables our custom
     * canvas interaction while Text is being activated.
     */
    this.activeTool.set(null);

    this.lastHistoryOwner.set('pdf');

    if (!this.editorLayerReady()) {
      this.pendingTool = 'text';

      return;
    }

    this.pendingTool = null;

    this.activateTextEditor();
  }

  private activateTextEditor(): void {
    this.pdfViewerService
      .switchAnnotationEdtorMode(
        PDF_EDITOR_MODE_TEXT,
      );

    this.activeTool.set('text');
  }

  private deactivateEditor(): void {
    this.pdfViewerService
      .switchAnnotationEdtorMode(
        PDF_EDITOR_MODE_NONE,
      );

    this.activeTool.set(null);

    this.pendingTool = null;
  }

  // Document lifecycle

  onPdfLoaded(): void {
    this.editorLayerReady.set(false);

    this.pendingTool = null;

    this.activeTool.set(null);

    this.lastHistoryOwner.set('pdf');

    this.documentProperties.set(null);

    this.propertiesVisible.set(false);

    this.connectAnnotationHistory();
  }

  // More menu

  handleMoreAction(
    action: PdfMoreAction,
  ): void {
    switch (action) {
      case 'search':
        this.findbarVisible.set(true);
        break;

      case 'rotate-clockwise':
        this.rotateDocument(90);
        break;

      case 'rotate-counterclockwise':
        this.rotateDocument(-90);
        break;

      case 'properties':
        void this.openDocumentProperties();
        break;

      case 'print':
        this.pdfViewerService.print();
        break;
    }
  }

  private rotateDocument(
    degrees: 90 | -90,
  ): void {
    const nextRotation =
      (this.rotation() +
        degrees +
        360) %
      360;

    if (
      nextRotation === 0 ||
      nextRotation === 90 ||
      nextRotation === 180 ||
      nextRotation === 270
    ) {
      this.rotation.set(nextRotation);
    }
  }

  // Custom document properties

  async openDocumentProperties(): Promise<void> {
    const application =
      this.pdfNotificationService
        .onPDFJSInitSignal();

    const document =
      application?.pdfDocument;

    if (!application || !document) {
      return;
    }

    const basicProperties: PdfDocumentProperties = {
      filename: this.filename(),
      sizeBytes: null,
      pageCount: document.numPages,
    };

    this.documentProperties.set(
      basicProperties,
    );

    try {
      const [
        rawMetadata,
        downloadInfo,
      ] = await Promise.all([
        this.propertiesExtractor
          .getDocumentProperties(
            application,
          ),

        document.getDownloadInfo(),
      ]);

      /*
       * The library's extractor returns Promise<any>.
       * Its published PdfDocumentInfo interface describes
       * the supported metadata fields.
       *
       * Keep that conversion at this integration boundary.
       */
      const metadata =
        rawMetadata as PdfDocumentInfo;

      // The user may have opened another document meanwhile.

      if (
        this.pdfNotificationService
          .onPDFJSInitSignal()
          ?.pdfDocument !== document
      ) {
        return;
      }

      this.documentProperties.set({
        ...basicProperties,

        sizeBytes: downloadInfo.length,

        title:
          metadata.title ?? null,

        author:
          metadata.author ?? null,

        createdAt:
          metadata.creationDate instanceof Date &&
          Number.isFinite(
            metadata.creationDate.getTime(),
          )
            ? metadata.creationDate
            : null,

        modifiedAt:
          metadata.modificationDate instanceof Date &&
          Number.isFinite(
            metadata.modificationDate.getTime(),
          )
            ? metadata.modificationDate
            : null,
      });
    } catch {
      /*
       * Optional metadata must not prevent the user
       * from viewing basic document information.
       */

      if (
        this.pdfNotificationService
          .onPDFJSInitSignal()
          ?.pdfDocument !== document
      ) {
        return;
      }

      this.documentProperties.set(
        basicProperties,
      );
    }

    this.propertiesVisible.set(true);
  }

  closeDocumentProperties(): void {
    this.propertiesVisible.set(false);
  }

  // Document export

  async exportDocument(): Promise<
    Blob | undefined
  > {
    this.deactivateEditor();

    await this.waitForNextFrame();

    /**
     * Important:
     *
     * At this stage this exports the PDF.js document and any
     * PDF.js Text annotations only.
     *
     * This custom ink/highlighter data is intentionally NOT
     * flattened into the PDF during this input-reliability spike.
     */
    return this.pdfViewerService
      .getCurrentDocumentAsBlob();
  }

  private waitForNextFrame(): Promise<void> {
    return new Promise((resolve) => {
      requestAnimationFrame(() => {
        resolve();
      });
    });
  }
}