export type PdfInkTool = 'pen' | 'highlighter';

export type PdfRotation = 0 | 90 | 180 | 270;

export interface InkPoint {
  /**
   * Normalized coordinates in the unrotated PDF-page space.
   * Values remain stable across zoom, resize and device orientation.
   */
  readonly x: number;
  readonly y: number;
  readonly pressure: number;
}

export interface InkStroke {
  readonly id: string;
  readonly pageNumber: number;
  readonly tool: PdfInkTool;
  readonly points: readonly InkPoint[];
}