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
  readonly getTool:
    () => PdfInkTool | null;

  readonly getRotation:
    () => PdfRotation;

  readonly getCommittedStrokes:
    () => readonly InkStroke[];

  readonly onStrokeCommitted:
    (stroke: InkStroke) => void;
}

interface ActiveStrokeDraft {
  readonly id: string;
  readonly tool: PdfInkTool;
  readonly points: InkPoint[];

  readonly touchIdentifier:
    number | null;

  readonly pointerId:
    number | null;
}

/**
 * One interactive ink canvas bound to a rendered
 * PDF.js page.
 *
 * iPad:
 *   Uses TouchEvents because this path has already
 *   been physically validated with Apple Pencil.
 *
 * Desktop:
 *   Uses PointerEvents with pointer capture so the
 *   drag remains owned by this canvas even if PDF.js
 *   DOM/layers move underneath the pointer.
 */
export class PdfInkPageSurface {
  private readonly canvas:
    HTMLCanvasElement;

  private readonly context:
    CanvasRenderingContext2D;

  private readonly resizeObserver:
    ResizeObserver;

  private activeStroke:
    ActiveStrokeDraft | null =
      null;

  private strokeSequence = 0;

  constructor(
    private readonly pageElement:
      HTMLElement,

    readonly pageNumber: number,

    private readonly callbacks:
      InkPageSurfaceCallbacks,
  ) {
    this.canvas =
      document.createElement(
        'canvas',
      );

    const context =
      this.canvas.getContext(
        '2d',
      );

    if (!context) {
      throw new Error(
        'Unable to create the PDF ink canvas context.',
      );
    }

    this.context = context;

    this.configureCanvas();

    this.registerInputEvents();

    this.pageElement.append(
      this.canvas,
    );

    this.resizeObserver =
      new ResizeObserver(
        () => {
          this.resizeAndRedraw();
        },
      );

    this.resizeObserver.observe(
      this.pageElement,
    );

    this.resizeAndRedraw();
  }

  setInteractionEnabled(
    enabled: boolean,
  ): void {
    this.canvas.style.pointerEvents =
      enabled
        ? 'auto'
        : 'none';

    this.canvas.style.touchAction =
      enabled
        ? 'none'
        : 'auto';

    this.canvas.style.cursor =
      enabled
        ? 'crosshair'
        : 'default';

    if (!enabled) {
      this.cancelActiveStroke();
    }
  }

  isAttached(): boolean {
    return (
      this.canvas.isConnected &&
      this.pageElement.contains(
        this.canvas,
      )
    );
  }

  redraw(): void {
    const rect =
      this.pageElement
        .getBoundingClientRect();

    if (
      rect.width <= 0 ||
      rect.height <= 0
    ) {
      return;
    }

    clearInkCanvas(
      this.canvas,
      this.context,
    );

    for (
      const stroke of
      this.callbacks
        .getCommittedStrokes()
    ) {
      renderInkStroke(
        this.context,
        rect,
        this.callbacks
          .getRotation(),
        stroke,
      );
    }

    if (this.activeStroke) {
      renderInkStroke(
        this.context,
        rect,
        this.callbacks
          .getRotation(),
        this.activeStroke,
      );
    }
  }

  destroy(): void {
    this.cancelActiveStroke();

    this.resizeObserver
      .disconnect();

    this.unregisterInputEvents();

    this.canvas.remove();
  }

  private configureCanvas():
    void {
    this.canvas.className =
      'badea-pdf-ink-layer';

    this.canvas.setAttribute(
      'aria-hidden',
      'true',
    );

    Object.assign(
      this.canvas.style,
      {
        position: 'absolute',
        inset: '0',

        width: '100%',
        height: '100%',

        zIndex: '40',

        display: 'block',

        pointerEvents: 'none',

        touchAction: 'auto',

        userSelect: 'none',

        WebkitUserSelect:
          'none',

        WebkitTapHighlightColor:
          'transparent',
      },
    );
  }

  /*
   * iPad / Apple Pencil path.
   *
   * Do not change this unnecessarily:
   * this path already passed physical
   * iPad testing.
   */

  private readonly onTouchStart =
    (
      event: TouchEvent,
    ): void => {
      const tool =
        this.callbacks
          .getTool();

      if (!tool) {
        return;
      }

      if (
        event.touches.length !== 1
      ) {
        event.preventDefault();

        this.cancelActiveStroke();

        return;
      }

      const touch =
        event.changedTouches
          .item(0);

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

        null,
      );
    };

  private readonly onTouchMove =
    (
      event: TouchEvent,
    ): void => {
      const active =
        this.activeStroke;

      if (
        !active ||
        active.touchIdentifier ===
          null
      ) {
        return;
      }

      const touch =
        this.findTouch(
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

  private readonly onTouchEnd =
    (
      event: TouchEvent,
    ): void => {
      const active =
        this.activeStroke;

      if (
        !active ||
        active.touchIdentifier ===
          null
      ) {
        return;
      }

      const touch =
        this.findTouch(
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

  private readonly onTouchCancel =
    (
      event: TouchEvent,
    ): void => {
      const active =
        this.activeStroke;

      if (
        !active ||
        active.touchIdentifier ===
          null
      ) {
        return;
      }

      event.preventDefault();

      this.cancelActiveStroke();
    };

  /*
   * Desktop path.
   *
   * Pointer capture is deliberate.
   *
   * Once drawing starts, the canvas keeps
   * receiving the pointer until the stroke
   * finishes. This prevents PDF.js layers
   * from stealing the rest of a drag.
   */

  private readonly onPointerDown =
    (
      event: PointerEvent,
    ): void => {
      /*
       * Touch/Pencil continues through the
       * physically-tested TouchEvent path.
       */
      if (
        event.pointerType !==
        'mouse'
      ) {
        return;
      }

      const tool =
        this.callbacks
          .getTool();

      if (
        !tool ||
        event.button !== 0
      ) {
        return;
      }

      event.preventDefault();

      this.canvas
        .setPointerCapture(
          event.pointerId,
        );

      this.startStroke(
        tool,

        event.clientX,
        event.clientY,

        event.pressure > 0
          ? event.pressure
          : 0.5,

        null,

        event.pointerId,
      );
    };

  private readonly onPointerMove =
    (
      event: PointerEvent,
    ): void => {
      const active =
        this.activeStroke;

      if (
        !active ||
        active.pointerId ===
          null ||
        active.pointerId !==
          event.pointerId
      ) {
        return;
      }

      event.preventDefault();

      this.extendStroke(
        event.clientX,
        event.clientY,

        event.pressure > 0
          ? event.pressure
          : 0.5,
      );
    };

  private readonly onPointerUp =
    (
      event: PointerEvent,
    ): void => {
      const active =
        this.activeStroke;

      if (
        !active ||
        active.pointerId ===
          null ||
        active.pointerId !==
          event.pointerId
      ) {
        return;
      }

      event.preventDefault();

      this.extendStroke(
        event.clientX,
        event.clientY,

        event.pressure > 0
          ? event.pressure
          : 0.5,
      );

      if (
        this.canvas
          .hasPointerCapture(
            event.pointerId,
          )
      ) {
        this.canvas
          .releasePointerCapture(
            event.pointerId,
          );
      }

      this.finishActiveStroke();
    };

  private readonly onPointerCancel =
    (
      event: PointerEvent,
    ): void => {
      const active =
        this.activeStroke;

      if (
        !active ||
        active.pointerId ===
          null ||
        active.pointerId !==
          event.pointerId
      ) {
        return;
      }

      event.preventDefault();

      if (
        this.canvas
          .hasPointerCapture(
            event.pointerId,
          )
      ) {
        this.canvas
          .releasePointerCapture(
            event.pointerId,
          );
      }

      this.cancelActiveStroke();
    };

  private registerInputEvents():
    void {
    /*
     * Validated iPad path.
     */

    this.canvas.addEventListener(
      'touchstart',
      this.onTouchStart,
      {
        passive: false,
      },
    );

    this.canvas.addEventListener(
      'touchmove',
      this.onTouchMove,
      {
        passive: false,
      },
    );

    this.canvas.addEventListener(
      'touchend',
      this.onTouchEnd,
      {
        passive: false,
      },
    );

    this.canvas.addEventListener(
      'touchcancel',
      this.onTouchCancel,
      {
        passive: false,
      },
    );

    /*
     * Desktop path.
     */

    this.canvas.addEventListener(
      'pointerdown',
      this.onPointerDown,
    );

    this.canvas.addEventListener(
      'pointermove',
      this.onPointerMove,
    );

    this.canvas.addEventListener(
      'pointerup',
      this.onPointerUp,
    );

    this.canvas.addEventListener(
      'pointercancel',
      this.onPointerCancel,
    );
  }

  private unregisterInputEvents():
    void {
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
      'pointerdown',
      this.onPointerDown,
    );

    this.canvas.removeEventListener(
      'pointermove',
      this.onPointerMove,
    );

    this.canvas.removeEventListener(
      'pointerup',
      this.onPointerUp,
    );

    this.canvas.removeEventListener(
      'pointercancel',
      this.onPointerCancel,
    );
  }

  private startStroke(
    tool: PdfInkTool,

    clientX: number,
    clientY: number,

    pressure: number,

    touchIdentifier:
      number | null,

    pointerId:
      number | null,
  ): void {
    this.activeStroke = {
      id:
        `ink-${Date.now()}-${++this.strokeSequence}`,

      tool,

      touchIdentifier,

      pointerId,

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
    const active =
      this.activeStroke;

    if (!active) {
      return;
    }

    const point =
      this.pointFromClientCoordinates(
        clientX,
        clientY,
        pressure,
      );

    const previous =
      active.points[
        active.points.length - 1
      ];

    if (
      previous &&
      Math.abs(
        previous.x - point.x,
      ) < 0.00025 &&
      Math.abs(
        previous.y - point.y,
      ) < 0.00025
    ) {
      return;
    }

    active.points.push(point);

    this.redraw();
  }

  private finishActiveStroke():
    void {
    const active =
      this.activeStroke;

    if (!active) {
      return;
    }

    this.activeStroke = null;

    if (
      active.points.length < 2
    ) {
      this.redraw();

      return;
    }

    this.callbacks
      .onStrokeCommitted({
        id: active.id,

        pageNumber:
          this.pageNumber,

        tool:
          active.tool,

        points:
          active.points.map(
            (point) => ({
              ...point,
            }),
          ),
      });

    this.redraw();
  }

  private cancelActiveStroke():
    void {
    if (!this.activeStroke) {
      return;
    }

    this.activeStroke = null;

    this.redraw();
  }

  private resizeAndRedraw():
    void {
    const rect =
      this.pageElement
        .getBoundingClientRect();

    resizeInkCanvas(
      this.canvas,
      this.context,
      rect,
    );

    this.redraw();
  }

  private pointFromClientCoordinates(
    clientX: number,
    clientY: number,
    pressure: number,
  ): InkPoint {
    return toBaseInkPoint(
      this.pageElement
        .getBoundingClientRect(),

      clientX,
      clientY,

      pressure,

      this.callbacks
        .getRotation(),
    );
  }

  private findTouch(
    touches: TouchList,
    identifier: number,
  ): Touch | null {
    for (
      let index = 0;
      index <
      touches.length;
      index += 1
    ) {
      const touch =
        touches.item(index);

      if (
        touch?.identifier ===
        identifier
      ) {
        return touch;
      }
    }

    return null;
  }
}