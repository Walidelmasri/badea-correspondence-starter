import {
  InkPoint,
  InkStroke,
  PdfInkTool,
} from '../../../shared/annotation/domain/ink.model';

import {
  InkSurfaceStroke,
} from '../../../shared/annotation/domain/ink-surface-stroke.model';

import {
  createEmptyPresidentExplanationInkDocument,
  PresidentExplanationInkDocument,
} from './president-explanation.model';

export type PresidentReviewExplanationMode =
  | 'pen'
  | 'keyboard';

export type PresidentReviewRequiredActionCode =
  | 'required-action'
  | 'study-and-opinion'
  | 'review-and-advise'
  | 'discuss-directly'
  | 'direct-response'
  | 'prepare-summary'
  | 'information-and-action'
  | 'follow-up'
  | 'information-and-file'
  | 'act-per-phone-call'
  | 'prepare-response'
  | 'apology'
  | 'return-later'
  | 'other';

export interface PresidentReviewPdfInkDocument {
  readonly documentKey: string;
  readonly strokes: readonly InkStroke[];
}

/**
 * Versioned, JSON-serializable state for an editable President Review.
 * Workflow routing, authorization and revisions are owned by the backend.
 */
export interface PresidentReviewDraftState {
  readonly schemaVersion: 1;
  readonly selectedRecipientIds: readonly string[];
  readonly selectedCopyRecipientIds: readonly string[];
  readonly selectedActionCodes:
    readonly PresidentReviewRequiredActionCode[];
  readonly otherRecipient: string;
  readonly otherAction: string;
  readonly explanationMode: PresidentReviewExplanationMode;
  readonly typedExplanation: string;
  readonly explanationInkDocument: PresidentExplanationInkDocument;
  readonly pdfInkDocuments: readonly PresidentReviewPdfInkDocument[];
}

const ACTION_CODES:
  ReadonlySet<string> = new Set([
    'required-action',
    'study-and-opinion',
    'review-and-advise',
    'discuss-directly',
    'direct-response',
    'prepare-summary',
    'information-and-action',
    'follow-up',
    'information-and-file',
    'act-per-phone-call',
    'prepare-response',
    'apology',
    'return-later',
    'other',
  ]);

export function createEmptyPresidentReviewDraftState():
  PresidentReviewDraftState {
  return {
    schemaVersion: 1,
    selectedRecipientIds: [],
    selectedCopyRecipientIds: [],
    selectedActionCodes: [],
    otherRecipient: '',
    otherAction: '',
    explanationMode: 'pen',
    typedExplanation: '',
    explanationInkDocument:
      createEmptyPresidentExplanationInkDocument(),
    pdfInkDocuments: [],
  };
}

/**
 * Older manually saved drafts can be missing the newer ink/text fields.
 * Normalize at the API boundary; never give unchecked JSON to a canvas.
 */
export function normalizePresidentReviewDraftState(
  value: unknown,
): PresidentReviewDraftState {
  const empty = createEmptyPresidentReviewDraftState();

  if (!isRecord(value) || value['schemaVersion'] !== 1) {
    return empty;
  }

  return {
    schemaVersion: 1,
    selectedRecipientIds: stringArray(value['selectedRecipientIds']),
    selectedCopyRecipientIds: stringArray(value['selectedCopyRecipientIds']),
    selectedActionCodes: stringArray(value['selectedActionCodes'])
      .filter((code): code is PresidentReviewRequiredActionCode =>
        ACTION_CODES.has(code)),
    otherRecipient: stringValue(value['otherRecipient']),
    otherAction: stringValue(value['otherAction']),
    explanationMode: value['explanationMode'] === 'keyboard'
      ? 'keyboard'
      : 'pen',
    typedExplanation: stringValue(value['typedExplanation']),
    explanationInkDocument: normalizeExplanationInkDocument(
      value['explanationInkDocument'],
      empty.explanationInkDocument,
    ),
    pdfInkDocuments: normalizePdfInkDocuments(value['pdfInkDocuments']),
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value);
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function stringArray(value: unknown): readonly string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string')
    : [];
}

function normalizeInkPoint(value: unknown): InkPoint | null {
  if (!isRecord(value) ||
      typeof value['x'] !== 'number' || !Number.isFinite(value['x']) ||
      typeof value['y'] !== 'number' || !Number.isFinite(value['y']) ||
      typeof value['pressure'] !== 'number' ||
      !Number.isFinite(value['pressure'])) {
    return null;
  }

  return {
    x: value['x'],
    y: value['y'],
    pressure: value['pressure'],
  };
}

function normalizeInkSurfaceStroke(
  value: unknown,
): InkSurfaceStroke | null {
  if (!isRecord(value) ||
      typeof value['id'] !== 'string' || !value['id'] ||
      !isPdfInkTool(value['tool']) ||
      !Array.isArray(value['points'])) {
    return null;
  }

  const points = value['points'].map(normalizeInkPoint);
  if (points.length === 0 || points.some((point) => point === null)) {
    return null;
  }

  return {
    id: value['id'],
    tool: value['tool'],
    points: points as InkPoint[],
  };
}

function isPdfInkTool(value: unknown): value is PdfInkTool {
  return value === 'pen' || value === 'highlighter';
}

function normalizeExplanationInkDocument(
  value: unknown,
  fallback: PresidentExplanationInkDocument,
): PresidentExplanationInkDocument {
  if (!isRecord(value) ||
      value['schemaVersion'] !== 1 ||
      !Array.isArray(value['pages']) ||
      value['pages'].length === 0) {
    return fallback;
  }

  const pages: PresidentExplanationInkDocument['pages'][number][] = [];

  for (const candidate of value['pages']) {
    if (!isRecord(candidate) ||
        typeof candidate['id'] !== 'string' ||
        !candidate['id'] ||
        !Array.isArray(candidate['strokes'])) {
      continue;
    }

    pages.push({
      id: candidate['id'],
      strokes: candidate['strokes']
        .map(normalizeInkSurfaceStroke)
        .filter((stroke): stroke is InkSurfaceStroke => stroke !== null),
    });
  }

  return pages.length > 0
    ? { schemaVersion: 1, pages }
    : fallback;
}

function normalizePdfInkDocuments(
  value: unknown,
): readonly PresidentReviewPdfInkDocument[] {
  if (!Array.isArray(value)) {
    return [];
  }

  const documents: PresidentReviewPdfInkDocument[] = [];
  const seen = new Set<string>();

  for (const candidate of value) {
    if (!isRecord(candidate) ||
        typeof candidate['documentKey'] !== 'string' ||
        !candidate['documentKey'] ||
        !Array.isArray(candidate['strokes']) ||
        seen.has(candidate['documentKey'])) {
      continue;
    }

    seen.add(candidate['documentKey']);

    documents.push({
      documentKey: candidate['documentKey'],
      strokes: candidate['strokes']
        .map(normalizePdfStroke)
        .filter((stroke): stroke is InkStroke => stroke !== null),
    });
  }

  return documents;
}

function normalizePdfStroke(value: unknown): InkStroke | null {
  if (!isRecord(value) ||
      typeof value['pageNumber'] !== 'number' ||
      !Number.isInteger(value['pageNumber']) ||
      (value['pageNumber'] as number) < 1) {
    return null;
  }

  const surfaceStroke = normalizeInkSurfaceStroke(value);

  return surfaceStroke
    ? { ...surfaceStroke, pageNumber: value['pageNumber'] as number }
    : null;
}