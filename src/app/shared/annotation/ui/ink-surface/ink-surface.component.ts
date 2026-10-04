import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

import {
  InkPoint,
  PdfInkTool,
} from '../../domain/ink.model';

import {
  InkSurfaceStroke,
  InkSurfaceTool,
} from '../../domain/ink-surface-stroke.model';

import {
  clearInkCanvas,
  renderInkStroke,
  resizeInkCanvas,
  toBaseInkPoint,
} from '../../rendering/ink-canvas-renderer';

interface ActiveStrokeDraft {
  readonly id: string;
  readonly tool: PdfInkTool;
  readonly points: InkPoint[];
  readonly touchIdentifier: number | null;
}

interface ActiveEraserInput {
  readonly touchIdentifier: number | null;
}

@Component({
  selector: 'app-ink-surface',
  standalone: true,
  templateUrl: './ink-surface.component.html',
  styleUrl: './ink-surface.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InkSurfaceComponent
  implements AfterViewInit, OnDestroy {
  readonly strokes =
    input<readonly InkSurfaceStroke[]>([]);

  readonly tool =
    input<InkSurfaceTool>('pen');

  /**
   * Changes whenever this component starts displaying
   * a logically different writing surface/page.
   */
  readonly surfaceKey =
    input<string>('default');

  readonly ariaLabel =
    input.required<string>();

  readonly strokesChange =
    output<readonly InkSurfaceStroke[]>();

  readonly canUndo = signal(false);
  readonly canRedo = signal(false);

  private readonly canvasRef =
    viewChild.required<ElementRef<HTMLCanvasElement>>(
      'canvas',
    );

  private context:
    CanvasRenderingContext2D | null = null;

  private resizeObserver:
    ResizeObserver | null = null;

  private activeStroke:
    ActiveStrokeDraft | null = null;

  private activeEraser:
    ActiveEraserInput | null = null;

  private workingStrokes:
    readonly InkSurfaceStroke[] = [];

  private readonly undoStack:
    Array<readonly InkSurfaceStroke[]> = [];

  private readonly redoStack:
    Array<readonly InkSurfaceStroke[]> = [];

  private lastEmittedStrokes:
    readonly InkSurfaceStroke[] | null = null;

  private currentSurfaceKey:
    string | null = null;

  private strokeSequence = 0;
  private viewReady = false;

  constructor() {
    effect(() => {
      const surfaceKey =
        this.surfaceKey();

      const strokes =
        this.strokes();

      const surfaceChanged =
        surfaceKey !==
        this.currentSurfaceKey;

      const selfUpdate =
        strokes ===
        this.lastEmittedStrokes;

      this.currentSurfaceKey =
        surfaceKey;

      this.workingStrokes =
        strokes;

      if (
        surfaceChanged ||
        !selfUpdate
      ) {
        this.resetHistory();
      }

      if (selfUpdate) {
        this.lastEmittedStrokes =
          null;
      }

      if (this.viewReady) {
        this.redraw();
      }
    });
  }

  ngAfterViewInit(): void {
    const canvas =
      this.canvasRef().nativeElement;

    const context =
      canvas.getContext('2d');

    if (!context) {
      throw new Error(
        'Unable to create ink surface canvas context.',
      );
    }

    this.context = context;
    this.viewReady = true;

    this.registerInputEvents();

    this.resizeObserver =
      new ResizeObserver(() => {
        this.resizeAndRedraw();
      });

    this.resizeObserver.observe(
      canvas,
    );

    this.resizeAndRedraw();
  }

  ngOnDestroy(): void {
    this.cancelActiveInput();

    this.resizeObserver?.disconnect();

    this.resizeObserver = null;

    this.unregisterInputEvents();

    this.context = null;
    this.viewReady = false;
  }

  undo(): void {
    const previous =
      this.undoStack.pop();

    if (!previous) {
      return;
    }

    this.redoStack.push(
      this.workingStrokes,
    );

    this.applyStrokes(previous);

    this.updateHistoryState();
  }

  redo(): void {
    const next =
      this.redoStack.pop();

    if (!next) {
      return;
    }

    this.undoStack.push(
      this.workingStrokes,
    );

    this.applyStrokes(next);

    this.updateHistoryState();
  }

  clear(): void {
    if (
      this.workingStrokes.length === 0
    ) {
      return;
    }

    this.commitMutation([]);
  }

  private readonly onTouchStart = (
    event: TouchEvent,
  ): void => {
    if (event.touches.length !== 1) {
      event.preventDefault();

      this.cancelActiveInput();

      return;
    }

    const touch =
      event.changedTouches.item(0);

    if (!touch) {
      return;
    }

    event.preventDefault();

    const tool = this.tool();

    if (tool === 'eraser') {
      this.activeEraser = {
        touchIdentifier: touch.identifier,
      };

      this.eraseAt(
        touch.clientX,
        touch.clientY,
      );

      return;
    }

    this.startStroke(
      tool,
      touch.clientX,
      touch.clientY,
      touch.force,
      touch.identifier,
    );
  };

  private readonly onTouchMove = (
    event: TouchEvent,
  ): void => {
    if (this.activeEraser) {
      const identifier =
        this.activeEraser
          .touchIdentifier;

      if (identifier === null) {
        return;
      }

      const touch =
        this.findTouch(
          event.touches,
          identifier,
        );

      if (!touch) {
        return;
      }

      event.preventDefault();

      this.eraseAt(
        touch.clientX,
        touch.clientY,
      );

      return;
    }

    const active =
      this.activeStroke;

    if (
      !active ||
      active.touchIdentifier === null
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

  private readonly onTouchEnd = (
    event: TouchEvent,
  ): void => {
    if (this.activeEraser) {
      event.preventDefault();

      this.activeEraser = null;

      return;
    }

    const active =
      this.activeStroke;

    if (
      !active ||
      active.touchIdentifier === null
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

  private readonly onTouchCancel = (
    event: TouchEvent,
  ): void => {
    if (
      !this.activeStroke &&
      !this.activeEraser
    ) {
      return;
    }

    event.preventDefault();

    this.cancelActiveInput();
  };

  private readonly onMouseDown = (
    event: MouseEvent,
  ): void => {
    if (event.button !== 0) {
      return;
    }

    event.preventDefault();

    const tool = this.tool();

    if (tool === 'eraser') {
      this.activeEraser = {
        touchIdentifier: null,
      };

      this.eraseAt(
        event.clientX,
        event.clientY,
      );

      return;
    }

    this.startStroke(
      tool,
      event.clientX,
      event.clientY,
      0.5,
      null,
    );
  };

  private readonly onMouseMove = (
    event: MouseEvent,
  ): void => {
    if (
      this.activeEraser?.touchIdentifier ===
      null
    ) {
      event.preventDefault();

      this.eraseAt(
        event.clientX,
        event.clientY,
      );

      return;
    }

    if (
      !this.activeStroke ||
      this.activeStroke
        .touchIdentifier !== null
    ) {
      return;
    }

    event.preventDefault();

    this.extendStroke(
      event.clientX,
      event.clientY,
      0.5,
    );
  };

  private readonly onMouseUp = (
    event: MouseEvent,
  ): void => {
    if (
      this.activeEraser?.touchIdentifier ===
      null
    ) {
      event.preventDefault();

      this.activeEraser = null;

      return;
    }

    if (
      !this.activeStroke ||
      this.activeStroke
        .touchIdentifier !== null
    ) {
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

  private readonly onMouseLeave =
    (): void => {
      if (this.activeEraser) {
        this.activeEraser = null;
        return;
      }

      if (this.activeStroke) {
        this.finishActiveStroke();
      }
    };

  private registerInputEvents(): void {
    const canvas =
      this.canvasRef().nativeElement;

    canvas.addEventListener(
      'touchstart',
      this.onTouchStart,
      { passive: false },
    );

    canvas.addEventListener(
      'touchmove',
      this.onTouchMove,
      { passive: false },
    );

    canvas.addEventListener(
      'touchend',
      this.onTouchEnd,
      { passive: false },
    );

    canvas.addEventListener(
      'touchcancel',
      this.onTouchCancel,
      { passive: false },
    );

    canvas.addEventListener(
      'mousedown',
      this.onMouseDown,
    );

    canvas.addEventListener(
      'mousemove',
      this.onMouseMove,
    );

    canvas.addEventListener(
      'mouseup',
      this.onMouseUp,
    );

    canvas.addEventListener(
      'mouseleave',
      this.onMouseLeave,
    );
  }

  private unregisterInputEvents(): void {
    const canvas =
      this.canvasRef().nativeElement;

    canvas.removeEventListener(
      'touchstart',
      this.onTouchStart,
    );

    canvas.removeEventListener(
      'touchmove',
      this.onTouchMove,
    );

    canvas.removeEventListener(
      'touchend',
      this.onTouchEnd,
    );

    canvas.removeEventListener(
      'touchcancel',
      this.onTouchCancel,
    );

    canvas.removeEventListener(
      'mousedown',
      this.onMouseDown,
    );

    canvas.removeEventListener(
      'mousemove',
      this.onMouseMove,
    );

    canvas.removeEventListener(
      'mouseup',
      this.onMouseUp,
    );

    canvas.removeEventListener(
      'mouseleave',
      this.onMouseLeave,
    );
  }

  private startStroke(
    tool: PdfInkTool,
    clientX: number,
    clientY: number,
    pressure: number,
    touchIdentifier: number | null,
  ): void {
    const point =
      this.pointFromClientCoordinates(
        clientX,
        clientY,
        pressure,
      );

    this.activeStroke = {
      id:
        `ink-${Date.now()}-` +
        `${++this.strokeSequence}`,

      tool,

      touchIdentifier,

      points: [point],
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

  private finishActiveStroke(): void {
    const active =
      this.activeStroke;

    if (!active) {
      return;
    }

    this.activeStroke = null;

    if (active.points.length < 2) {
      this.redraw();

      return;
    }

    const stroke: InkSurfaceStroke = {
      id: active.id,
      tool: active.tool,

      points: active.points.map(
        (point) => ({
          ...point,
        }),
      ),
    };

    this.commitMutation([
      ...this.workingStrokes,
      stroke,
    ]);
  }

  private eraseAt(
    clientX: number,
    clientY: number,
  ): void {
    const index =
      this.findStrokeAt(
        clientX,
        clientY,
      );

    if (index < 0) {
      return;
    }

    const next =
      this.workingStrokes.filter(
        (_, currentIndex) =>
          currentIndex !== index,
      );

    this.commitMutation(next);
  }

  private findStrokeAt(
    clientX: number,
    clientY: number,
  ): number {
    const rect =
      this.canvasRef()
        .nativeElement
        .getBoundingClientRect();

    const x =
      clientX - rect.left;

    const y =
      clientY - rect.top;

    for (
      let index =
        this.workingStrokes.length - 1;
      index >= 0;
      index -= 1
    ) {
      const stroke =
        this.workingStrokes[index];

      const radius =
        stroke.tool === 'highlighter'
          ? 18
          : 14;

      if (
        this.strokeContainsPoint(
          stroke,
          x,
          y,
          radius,
          rect,
        )
      ) {
        return index;
      }
    }

    return -1;
  }

  private strokeContainsPoint(
    stroke: InkSurfaceStroke,
    x: number,
    y: number,
    radius: number,
    rect: DOMRect,
  ): boolean {
    if (
      stroke.points.length === 0
    ) {
      return false;
    }

    const points =
      stroke.points.map(
        (point) => ({
          x:
            point.x *
            rect.width,

          y:
            point.y *
            rect.height,
        }),
      );

    const radiusSquared =
      radius * radius;

    if (points.length === 1) {
      const dx =
        x - points[0].x;

      const dy =
        y - points[0].y;

      return (
        dx * dx + dy * dy <=
        radiusSquared
      );
    }

    for (
      let index = 0;
      index < points.length - 1;
      index += 1
    ) {
      const distanceSquared =
        this.distanceSquaredToSegment(
          x,
          y,
          points[index].x,
          points[index].y,
          points[index + 1].x,
          points[index + 1].y,
        );

      if (
        distanceSquared <=
        radiusSquared
      ) {
        return true;
      }
    }

    return false;
  }

  private distanceSquaredToSegment(
    px: number,
    py: number,
    x1: number,
    y1: number,
    x2: number,
    y2: number,
  ): number {
    const dx = x2 - x1;
    const dy = y2 - y1;

    if (
      dx === 0 &&
      dy === 0
    ) {
      const pointDx = px - x1;
      const pointDy = py - y1;

      return (
        pointDx * pointDx +
        pointDy * pointDy
      );
    }

    const t = Math.max(
      0,
      Math.min(
        1,
        (
          (px - x1) * dx +
          (py - y1) * dy
        ) /
        (
          dx * dx +
          dy * dy
        ),
      ),
    );

    const nearestX =
      x1 + t * dx;

    const nearestY =
      y1 + t * dy;

    const nearestDx =
      px - nearestX;

    const nearestDy =
      py - nearestY;

    return (
      nearestDx * nearestDx +
      nearestDy * nearestDy
    );
  }

  private commitMutation(
    next:
      readonly InkSurfaceStroke[],
  ): void {
    this.undoStack.push(
      this.workingStrokes,
    );

    this.redoStack.length = 0;

    this.applyStrokes(next);

    this.updateHistoryState();
  }

  private applyStrokes(
    strokes:
      readonly InkSurfaceStroke[],
  ): void {
    this.workingStrokes =
      strokes;

    this.lastEmittedStrokes =
      strokes;

    this.strokesChange.emit(
      strokes,
    );

    this.redraw();
  }

  private resetHistory(): void {
    this.undoStack.length = 0;
    this.redoStack.length = 0;

    this.updateHistoryState();
  }

  private updateHistoryState(): void {
    this.canUndo.set(
      this.undoStack.length > 0,
    );

    this.canRedo.set(
      this.redoStack.length > 0,
    );
  }

  private cancelActiveInput(): void {
    this.activeStroke = null;
    this.activeEraser = null;

    this.redraw();
  }

  private resizeAndRedraw(): void {
    const canvas =
      this.canvasRef().nativeElement;

    const context = this.context;

    if (!context) {
      return;
    }

    const rect =
      canvas.getBoundingClientRect();

    resizeInkCanvas(
      canvas,
      context,
      rect,
    );

    this.redraw();
  }

  private redraw(): void {
    const context =
      this.context;

    if (!context) {
      return;
    }

    const canvas =
      this.canvasRef().nativeElement;

    const rect =
      canvas.getBoundingClientRect();

    if (
      rect.width <= 0 ||
      rect.height <= 0
    ) {
      return;
    }

    clearInkCanvas(
      canvas,
      context,
    );

    for (
      const stroke of
      this.workingStrokes
    ) {
      renderInkStroke(
        context,
        rect,
        0,
        stroke,
      );
    }

    if (this.activeStroke) {
      renderInkStroke(
        context,
        rect,
        0,
        this.activeStroke,
      );
    }
  }

  private pointFromClientCoordinates(
    clientX: number,
    clientY: number,
    pressure: number,
  ): InkPoint {
    const rect =
      this.canvasRef()
        .nativeElement
        .getBoundingClientRect();

    return toBaseInkPoint(
      rect,
      clientX,
      clientY,
      pressure,
      0,
    );
  }

  private findTouch(
    touches: TouchList,
    identifier: number,
  ): Touch | null {
    for (
      let index = 0;
      index < touches.length;
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