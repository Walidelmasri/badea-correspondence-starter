import {
  InkPoint,
  InkStroke,
  PdfInkTool,
  PdfRotation,
} from '../domain/ink.model';
import {
  clearInkCanvas,
  renderInkStroke,
  resizeInkCanvas,
  toBaseInkPoint,
} from '../rendering/ink-canvas-renderer';

interface InkPageSurfaceCallbacks {
  readonly getTool: () => PdfInkTool | null;
  readonly getRotation: () => PdfRotation;
  readonly getCommittedStrokes: () => readonly InkStroke[];
  readonly onStrokeCommitted: (stroke: InkStroke) => void;
}

interface ActiveStrokeDraft {
  readonly id: string;
  readonly tool: PdfInkTool;
  readonly points: InkPoint[];
  readonly touchIdentifier: number | null;
}

/**
 * One interactive ink canvas bound to one rendered PDF.js page.
 *
 * Touch events are intentionally used for the iPad drawing path.
 * While ink mode is active, the canvas owns the page interaction;
 * when ink mode is off, pointer events are disabled so PDF.js keeps
 * normal scrolling/selection behaviour.
 */
export class PdfInkPageSurface {
  private readonly canvas: HTMLCanvasElement;
  private readonly context: CanvasRenderingContext2D;
  private readonly resizeObserver: ResizeObserver;

  private activeStroke: ActiveStrokeDraft | null = null;
  private strokeSequence = 0;

  constructor(
    private readonly pageElement: HTMLElement,
    readonly pageNumber: number,
    private readonly callbacks: InkPageSurfaceCallbacks,
  ) {
    this.canvas = document.createElement('canvas');

    const context = this.canvas.getContext('2d');

    if (!context) {
      throw new Error(
        'Unable to create the PDF ink canvas context.',
      );
    }

    this.context = context;

    this.configureCanvas();
    this.registerInputEvents();

    this.pageElement.append(this.canvas);

    this.resizeObserver = new ResizeObserver(() => {
      this.resizeAndRedraw();
    });
    this.resizeObserver.observe(this.pageElement);

    this.resizeAndRedraw();
  }

  setInteractionEnabled(enabled: boolean): void {
    this.canvas.style.pointerEvents = enabled ? 'auto' : 'none';
    this.canvas.style.touchAction = enabled ? 'none' : 'auto';
    this.canvas.style.cursor = enabled ? 'crosshair' : 'default';

    if (!enabled) {
      this.cancelActiveStroke();
    }
  }

  isAttached(): boolean {
    return (
      this.canvas.isConnected &&
      this.pageElement.contains(this.canvas)
    );
  }

  redraw(): void {
    const rect = this.pageElement.getBoundingClientRect();

    if (rect.width <= 0 || rect.height <= 0) {
      return;
    }

    clearInkCanvas(this.canvas, this.context);

    for (const stroke of this.callbacks.getCommittedStrokes()) {
      renderInkStroke(
        this.context,
        rect,
        this.callbacks.getRotation(),
        stroke,
      );
    }

    if (this.activeStroke) {
      renderInkStroke(
        this.context,
        rect,
        this.callbacks.getRotation(),
        this.activeStroke,
      );
    }
  }

  destroy(): void {
    this.cancelActiveStroke();
    this.resizeObserver.disconnect();
    this.unregisterInputEvents();
    this.canvas.remove();
  }

  private configureCanvas(): void {
    this.canvas.className = 'badea-pdf-ink-layer';
    this.canvas.setAttribute('aria-hidden', 'true');

    Object.assign(this.canvas.style, {
      position: 'absolute',
      inset: '0',
      width: '100%',
      height: '100%',
      zIndex: '40',
      display: 'block',
      pointerEvents: 'none',
      touchAction: 'auto',
      userSelect: 'none',
      WebkitUserSelect: 'none',
      WebkitTapHighlightColor: 'transparent',
    });
  }

  private readonly onTouchStart = (event: TouchEvent): void => {
    const tool = this.callbacks.getTool();

    if (!tool) {
      return;
    }

    if (event.touches.length !== 1) {
      event.preventDefault();
      this.cancelActiveStroke();
      return;
    }

    const touch = event.changedTouches.item(0);

    if (!touch) {
      return;
    }

    event.preventDefault();
    this.startStroke(
      tool,
      touch.clientX,
      touch.clientY,
      touch.force,
      touch.identifier,
    );
  };

  private readonly onTouchMove = (event: TouchEvent): void => {
    const active = this.activeStroke;

    if (!active || active.touchIdentifier === null) {
      return;
    }

    const touch = this.findTouch(
      event.touches,
      active.touchIdentifier,
    );

    if (!touch) {
      return;
    }

    event.preventDefault();
    this.extendStroke(
      touch.clientX,
      touch.clientY,
      touch.force,
    );
  };

  private readonly onTouchEnd = (event: TouchEvent): void => {
    const active = this.activeStroke;

    if (!active || active.touchIdentifier === null) {
      return;
    }

    const touch = this.findTouch(
      event.changedTouches,
      active.touchIdentifier,
    );

    if (!touch) {
      return;
    }

    event.preventDefault();
    this.extendStroke(
      touch.clientX,
      touch.clientY,
      touch.force,
    );
    this.finishActiveStroke();
  };

  private readonly onTouchCancel = (event: TouchEvent): void => {
    if (!this.activeStroke) {
      return;
    }

    event.preventDefault();
    this.finishActiveStroke();
  };

  private readonly onMouseDown = (event: MouseEvent): void => {
    const tool = this.callbacks.getTool();

    if (!tool || event.button !== 0) {
      return;
    }

    event.preventDefault();
    this.startStroke(
      tool,
      event.clientX,
      event.clientY,
      0.5,
      null,
    );
  };

  private readonly onMouseMove = (event: MouseEvent): void => {
    if (!this.activeStroke || this.activeStroke.touchIdentifier !== null) {
      return;
    }

    event.preventDefault();
    this.extendStroke(
      event.clientX,
      event.clientY,
      0.5,
    );
  };

  private readonly onMouseUp = (event: MouseEvent): void => {
    if (!this.activeStroke || this.activeStroke.touchIdentifier !== null) {
      return;
    }

    event.preventDefault();
    this.extendStroke(
      event.clientX,
      event.clientY,
      0.5,
    );
    this.finishActiveStroke();
  };

  private registerInputEvents(): void {
    this.canvas.addEventListener(
      'touchstart',
      this.onTouchStart,
      { passive: false },
    );
    this.canvas.addEventListener(
      'touchmove',
      this.onTouchMove,
      { passive: false },
    );
    this.canvas.addEventListener(
      'touchend',
      this.onTouchEnd,
      { passive: false },
    );
    this.canvas.addEventListener(
      'touchcancel',
      this.onTouchCancel,
      { passive: false },
    );

    this.canvas.addEventListener(
      'mousedown',
      this.onMouseDown,
    );
    this.canvas.addEventListener(
      'mousemove',
      this.onMouseMove,
    );
    this.canvas.addEventListener(
      'mouseup',
      this.onMouseUp,
    );
    this.canvas.addEventListener(
      'mouseleave',
      this.onMouseUp,
    );
  }

  private unregisterInputEvents(): void {
    this.canvas.removeEventListener(
      'touchstart',
      this.onTouchStart,
    );
    this.canvas.removeEventListener(
      'touchmove',
      this.onTouchMove,
    );
    this.canvas.removeEventListener(
      'touchend',
      this.onTouchEnd,
    );
    this.canvas.removeEventListener(
      'touchcancel',
      this.onTouchCancel,
    );

    this.canvas.removeEventListener(
      'mousedown',
      this.onMouseDown,
    );
    this.canvas.removeEventListener(
      'mousemove',
      this.onMouseMove,
    );
    this.canvas.removeEventListener(
      'mouseup',
      this.onMouseUp,
    );
    this.canvas.removeEventListener(
      'mouseleave',
      this.onMouseUp,
    );
  }

  private startStroke(
    tool: PdfInkTool,
    clientX: number,
    clientY: number,
    pressure: number,
    touchIdentifier: number | null,
  ): void {
    this.activeStroke = {
      id: `ink-${Date.now()}-${++this.strokeSequence}`,
      tool,
      touchIdentifier,
      points: [
        this.pointFromClientCoordinates(
          clientX,
          clientY,
          pressure,
        ),
      ],
    };

    this.redraw();
  }

  private extendStroke(
    clientX: number,
    clientY: number,
    pressure: number,
  ): void {
    const active = this.activeStroke;

    if (!active) {
      return;
    }

    const point = this.pointFromClientCoordinates(
      clientX,
      clientY,
      pressure,
    );

    const previous = active.points[active.points.length - 1];

    if (
      previous &&
      Math.abs(previous.x - point.x) < 0.00025 &&
      Math.abs(previous.y - point.y) < 0.00025
    ) {
      return;
    }

    active.points.push(point);
    this.redraw();
  }

  private finishActiveStroke(): void {
    const active = this.activeStroke;

    if (!active) {
      return;
    }

    this.activeStroke = null;

    if (active.points.length < 2) {
      this.redraw();
      return;
    }

    this.callbacks.onStrokeCommitted({
      id: active.id,
      pageNumber: this.pageNumber,
      tool: active.tool,
      points: active.points.map((point) => ({ ...point })),
    });

    this.redraw();
  }

  private cancelActiveStroke(): void {
    if (!this.activeStroke) {
      return;
    }

    this.activeStroke = null;
    this.redraw();
  }

  private resizeAndRedraw(): void {
    const rect = this.pageElement.getBoundingClientRect();

    resizeInkCanvas(this.canvas, this.context, rect);
    this.redraw();
  }

  private pointFromClientCoordinates(
    clientX: number,
    clientY: number,
    pressure: number,
  ): InkPoint {
    return toBaseInkPoint(
      this.pageElement.getBoundingClientRect(),
      clientX,
      clientY,
      pressure,
      this.callbacks.getRotation(),
    );
  }

  private findTouch(
    touches: TouchList,
    identifier: number,
  ): Touch | null {
    for (let index = 0; index < touches.length; index += 1) {
      const touch = touches.item(index);

      if (touch?.identifier === identifier) {
        return touch;
      }
    }

    return null;
  }
}
