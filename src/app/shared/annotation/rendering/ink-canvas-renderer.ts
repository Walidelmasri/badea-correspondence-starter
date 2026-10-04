import {
  InkPoint,
  InkStroke,
  PdfInkTool,
  PdfRotation,
} from '../domain/ink.model';

export interface CanvasSize {
  readonly width: number;
  readonly height: number;
}

export function resizeInkCanvas(
  canvas: HTMLCanvasElement,
  context: CanvasRenderingContext2D,
  size: CanvasSize,
): void {
  if (size.width <= 0 || size.height <= 0) {
    return;
  }

  const pixelRatio = Math.min(
    window.devicePixelRatio || 1,
    2,
  );

  const width = Math.max(
    1,
    Math.round(size.width * pixelRatio),
  );
  const height = Math.max(
    1,
    Math.round(size.height * pixelRatio),
  );

  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }

  context.setTransform(
    pixelRatio,
    0,
    0,
    pixelRatio,
    0,
    0,
  );
}

export function clearInkCanvas(
  canvas: HTMLCanvasElement,
  context: CanvasRenderingContext2D,
): void {
  context.save();
  context.setTransform(1, 0, 0, 1, 0, 0);
  context.clearRect(0, 0, canvas.width, canvas.height);
  context.restore();
}

export function renderInkStroke(
  context: CanvasRenderingContext2D,
  size: CanvasSize,
  rotation: PdfRotation,
  stroke: Pick<InkStroke, 'tool' | 'points'>,
): void {
  if (stroke.points.length === 0) {
    return;
  }

  const points = stroke.points.map((point) => {
    const visual = baseToVisual(
      point.x,
      point.y,
      rotation,
    );

    return {
      x: visual.x * size.width,
      y: visual.y * size.height,
    };
  });

  const scale = Math.min(size.width, size.height);

  context.save();
  context.lineCap = 'round';
  context.lineJoin = 'round';

  applyToolStyle(context, stroke.tool, scale);

  context.beginPath();

  const first = points[0];
  context.moveTo(first.x, first.y);

  if (points.length === 1) {
    context.lineTo(first.x + 0.01, first.y + 0.01);
  } else {
    for (
      let index = 1;
      index < points.length - 1;
      index += 1
    ) {
      const current = points[index];
      const next = points[index + 1];

      context.quadraticCurveTo(
        current.x,
        current.y,
        (current.x + next.x) / 2,
        (current.y + next.y) / 2,
      );
    }

    const last = points[points.length - 1];
    context.lineTo(last.x, last.y);
  }

  context.stroke();
  context.restore();
}

export function toBaseInkPoint(
  rect: DOMRect,
  clientX: number,
  clientY: number,
  pressure: number,
  rotation: PdfRotation,
): InkPoint {
  const visualX = clamp01(
    (clientX - rect.left) / rect.width,
  );
  const visualY = clamp01(
    (clientY - rect.top) / rect.height,
  );

  const base = visualToBase(
    visualX,
    visualY,
    rotation,
  );

  return {
    x: base.x,
    y: base.y,
    pressure: normalizePressure(pressure),
  };
}

function applyToolStyle(
  context: CanvasRenderingContext2D,
  tool: PdfInkTool,
  scale: number,
): void {
  if (tool === 'highlighter') {
    context.strokeStyle = 'rgb(248 204 70)';
    context.globalAlpha = 0.28;
    context.globalCompositeOperation = 'multiply';
    context.lineWidth = Math.max(8, scale * 0.018);
    return;
  }

  context.strokeStyle = 'rgb(24 54 48)';
  context.globalAlpha = 1;
  context.globalCompositeOperation = 'source-over';
  context.lineWidth = Math.max(1.5, scale * 0.0028);
}

function visualToBase(
  x: number,
  y: number,
  rotation: PdfRotation,
): { readonly x: number; readonly y: number } {
  switch (rotation) {
    case 90:
      return { x: y, y: 1 - x };
    case 180:
      return { x: 1 - x, y: 1 - y };
    case 270:
      return { x: 1 - y, y: x };
    default:
      return { x, y };
  }
}

function baseToVisual(
  x: number,
  y: number,
  rotation: PdfRotation,
): { readonly x: number; readonly y: number } {
  switch (rotation) {
    case 90:
      return { x: 1 - y, y: x };
    case 180:
      return { x: 1 - x, y: 1 - y };
    case 270:
      return { x: y, y: 1 - x };
    default:
      return { x, y };
  }
}

function normalizePressure(value: number): number {
  if (!Number.isFinite(value) || value <= 0) {
    return 0.5;
  }

  return clamp01(value);
}

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}
