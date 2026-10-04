import { InkSurfaceStroke } from '../../../shared/annotation/domain/ink-surface-stroke.model';

export interface PresidentExplanationInkPage {
  readonly id: string;

  readonly strokes:
    readonly InkSurfaceStroke[];
}

export interface PresidentExplanationInkDocument {
  readonly schemaVersion: 1;

  readonly pages:
    readonly PresidentExplanationInkPage[];
}

export function createEmptyPresidentExplanationInkDocument():
  PresidentExplanationInkDocument {
  return {
    schemaVersion: 1,

    pages: [
      {
        id: 'explanation-page-1',
        strokes: [],
      },
    ],
  };
}