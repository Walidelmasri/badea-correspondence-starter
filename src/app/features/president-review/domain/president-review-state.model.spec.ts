import {
  describe,
  expect,
  it,
} from 'vitest';

import {
  createEmptyPresidentReviewDraftState,
  PresidentReviewDraftState,
} from './president-review-state.model';

describe(
  'PresidentReviewDraftState',
  () => {
    it(
      'creates an empty serializable draft',
      () => {
        const state =
          createEmptyPresidentReviewDraftState();

        expect(state)
          .toEqual({
            schemaVersion:
              1,

            selectedRecipientIds:
              [],

            selectedCopyRecipientIds:
              [],

            selectedActionCodes:
              [],

            otherRecipient:
              '',

            otherAction:
              '',

            explanationMode:
              'pen',

            typedExplanation:
              '',

            explanationInkDocument: {
              schemaVersion:
                1,

              pages: [
                {
                  id:
                    'explanation-page-1',

                  strokes:
                    [],
                },
              ],
            },

            pdfInkDocuments:
              [],
          } satisfies
            PresidentReviewDraftState);

        expect(
          JSON.parse(
            JSON.stringify(
              state,
            ),
          ),
        ).toEqual(
          state,
        );
      },
    );
  },
);