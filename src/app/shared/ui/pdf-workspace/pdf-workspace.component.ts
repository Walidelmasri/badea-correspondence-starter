import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  signal,
} from '@angular/core';

import {
  NgxExtendedPdfViewerModule,
  NgxExtendedPdfViewerService,
  PdfSidebarView,
  PDFNotificationService,
  PdfDocumentInfo,
  PdfDocumentPropertiesExtractor,
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
import {
  OnDestroy,
} from '@angular/core';

import {
  IEventBus,
} from 'ngx-extended-pdf-viewer';
type PdfZoomSetting = 'page-width' | number | undefined;

const PDF_EDITOR_MODE_NONE = 0;

const PDF_EDITOR_MODES: Readonly<Record<PdfEditorTool, number>> = {
  text: 3,
  highlight: 9,
  draw: 15,
};

@Component({
  selector: 'app-pdf-workspace',
  standalone: true,
  imports: [
    NgxExtendedPdfViewerModule,
    PdfToolbarComponent,
    PdfPropertiesDialogComponent,
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

  // Document state

  readonly page = signal(1);

  readonly pageCount = signal(0);

  readonly zoom =
    signal<PdfZoomSetting>('page-width');

  private readonly currentZoomFactor =
    signal(1);

  // Editor state
  readonly canUndo = signal(false);
  readonly canRedo = signal(false);

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

    if (typeof details?.hasSomethingToUndo === 'boolean') {
      this.canUndo.set(details.hasSomethingToUndo);
    }

    if (typeof details?.hasSomethingToRedo === 'boolean') {
      this.canRedo.set(details.hasSomethingToRedo);
    }
  };
  readonly activeTool =
    signal<PdfEditorTool | null>(null);

  private readonly editorLayerReady =
    signal(false);

  private pendingTool: PdfEditorTool | null = null;

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
  undo(): void {
    if (!this.canUndo()) {
      return;
    }

    this.historyEventBus?.dispatch('editingaction', {
      source: this,
      name: 'undo',
    });
  }

  redo(): void {
    if (!this.canRedo()) {
      return;
    }

    this.historyEventBus?.dispatch('editingaction', {
      source: this,
      name: 'redo',
    });
  }

  private connectAnnotationHistory(): void {
    this.disconnectAnnotationHistory();

    const application =
      this.pdfNotificationService.onPDFJSInitSignal();

    if (!application?.pdfDocument) {
      return;
    }

    this.historyEventBus = application.eventBus;

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

    this.canUndo.set(false);
    this.canRedo.set(false);
  }

  ngOnDestroy(): void {
    this.disconnectAnnotationHistory();
  }
  // Page navigation

  previousPage(): void {
    this.page.update(
      (currentPage) => Math.max(1, currentPage - 1),
    );
  }

  nextPage(): void {
    const totalPages = this.pageCount();

    if (totalPages < 1) {
      return;
    }

    this.page.update(
      (currentPage) =>
        Math.min(totalPages, currentPage + 1),
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
    if (this.activeTool() === tool) {
      this.deactivateEditor();
      return;
    }

    if (!this.editorLayerReady()) {
      this.pendingTool = tool;
      return;
    }

    this.activateEditor(tool);
  }

  onAnnotationEditorLayerRendered(): void {
    if (this.editorLayerReady()) {
      return;
    }

    this.editorLayerReady.set(true);

    const pendingTool = this.pendingTool;

    if (!pendingTool) {
      return;
    }

    this.pendingTool = null;

    queueMicrotask(() => {
      this.activateEditor(pendingTool);
    });
  }

  private activateEditor(
    tool: PdfEditorTool,
  ): void {
    this.pdfViewerService.switchAnnotationEdtorMode(
      PDF_EDITOR_MODE_NONE,
    );

    queueMicrotask(() => {
      this.pdfViewerService.switchAnnotationEdtorMode(
        PDF_EDITOR_MODES[tool],
      );

      this.activeTool.set(tool);
    });
  }

  private deactivateEditor(): void {
    this.pdfViewerService.switchAnnotationEdtorMode(
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

    this.documentProperties.set(null);

    this.propertiesVisible.set(false);
    
    this.connectAnnotationHistory();
  }

  // More menu

  handleMoreAction(action: PdfMoreAction): void {
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
      (this.rotation() + degrees + 360) % 360;

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
      this.pdfNotificationService.onPDFJSInitSignal();

    const document = application?.pdfDocument;

    if (!application || !document) {
      return;
    }

    const basicProperties: PdfDocumentProperties = {
      filename: this.filename(),
      sizeBytes: null,
      pageCount: document.numPages,
    };

    this.documentProperties.set(basicProperties);

    try {
      const [rawMetadata, downloadInfo] =
        await Promise.all([
          this.propertiesExtractor.getDocumentProperties(
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
        this.pdfNotificationService.onPDFJSInitSignal()
          ?.pdfDocument !== document
      ) {
        return;
      }

      this.documentProperties.set({
        ...basicProperties,

        sizeBytes: downloadInfo.length,

        title: metadata.title ?? null,

        author: metadata.author ?? null,

        createdAt:
          metadata.creationDate instanceof Date &&
            Number.isFinite(metadata.creationDate.getTime())
            ? metadata.creationDate
            : null,

        modifiedAt:
          metadata.modificationDate instanceof Date &&
            Number.isFinite(metadata.modificationDate.getTime())
            ? metadata.modificationDate
            : null,
      });
    } catch {
      /*
       * Optional metadata must not prevent the user
       * from viewing basic document information.
       */
      if (
        this.pdfNotificationService.onPDFJSInitSignal()
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

  async exportDocument(): Promise<Blob | undefined> {
    this.deactivateEditor();

    await this.waitForNextFrame();

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