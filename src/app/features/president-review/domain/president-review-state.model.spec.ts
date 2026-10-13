import {
  describe,
  expect,
  it,
} from 'vitest';

import {
  createEmptyPresidentReviewDraftState,
  normalizePresidentReviewDraftState,
  PresidentReviewDraftState,
} from './president-review-state.model';

describe('PresidentReviewDraftState', () => {
  it('creates an empty serializable draft', () => {
    const state = createEmptyPresidentReviewDraftState();

    expect(state).toEqual({
      schemaVersion: 1,
      selectedRecipientIds: [],
      selectedCopyRecipientIds: [],
      selectedActionCodes: [],
      otherRecipient: '',
      otherAction: '',
      explanationMode: 'pen',
      typedExplanation: '',
      explanationInkDocument: {
        schemaVersion: 1,
        pages: [{ id: 'explanation-page-1', strokes: [] }],
      },
      pdfInkDocuments: [],
    } satisfies PresidentReviewDraftState);

    expect(JSON.parse(JSON.stringify(state))).toEqual(state);
  });

  it('normalizes the older partial revision-1 runtime state', () => {
    const state = normalizePresidentReviewDraftState({
      schemaVersion: 1,
      selectedRecipientIds: ['117'],
      typedExplanation: 'Existing direction',
    });

    expect(state.selectedRecipientIds).toEqual(['117']);
    expect(state.typedExplanation).toBe('Existing direction');
    expect(state.selectedCopyRecipientIds).toEqual([]);
    expect(state.selectedActionCodes).toEqual([]);
    expect(state.explanationMode).toBe('pen');
    expect(state.pdfInkDocuments).toEqual([]);
    expect(state.explanationInkDocument.pages).toHaveLength(1);
  });

  it('preserves valid strokes and discards malformed ink safely', () => {
    const state = normalizePresidentReviewDraftState({
      schemaVersion: 1,
      selectedActionCodes: ['follow-up', 'unknown-action'],
      explanationInkDocument: {
        schemaVersion: 1,
        pages: [{
          id: 'explanation-page-1',
          strokes: [{
            id: 'ex-1',
            tool: 'pen',
            points: [{ x: 0.1, y: 0.2, pressure: 0.5 }],
          }],
        }],
      },
      pdfInkDocuments: [{
        documentKey: 'main-letter',
        strokes: [
          {
            id: 'stroke-1',
            pageNumber: 2,
            tool: 'highlighter',
            points: [{ x: 0.25, y: 0.5, pressure: 0.8 }],
          },
          {
            id: 'invalid-stroke',
            pageNumber: -1,
            tool: 'pen',
            points: [],
          },
        ],
      }],
    });

    expect(state.selectedActionCodes).toEqual(['follow-up']);
    expect(state.explanationInkDocument.pages[0].strokes).toHaveLength(1);
    expect(state.pdfInkDocuments[0].strokes).toHaveLength(1);
    expect(state.pdfInkDocuments[0].strokes[0].pageNumber).toBe(2);
    expect(JSON.parse(JSON.stringify(state))).toEqual(state);
  });
});