import {
  InkPoint,
  PdfInkTool,
} from './ink.model';

export type InkSurfaceTool =
  | PdfInkTool
  | 'eraser';

export interface InkSurfaceStroke {
  readonly id: string;
  readonly tool: PdfInkTool;
  readonly points: readonly InkPoint[];
}