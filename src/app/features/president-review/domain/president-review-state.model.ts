import {
    InkStroke,
} from '../../../shared/annotation/domain/ink.model';

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
    readonly documentKey:
    string;

    readonly strokes:
    readonly InkStroke[];
}

export interface PresidentReviewDraftState {
    readonly schemaVersion:
    1;

    readonly selectedRecipientIds:
    readonly string[];

    readonly selectedCopyRecipientIds:
    readonly string[];

    readonly selectedActionCodes:
    readonly PresidentReviewRequiredActionCode[];

    readonly otherRecipient:
    string;

    readonly otherAction:
    string;

    readonly explanationMode:
    PresidentReviewExplanationMode;

    readonly typedExplanation:
    string;

    readonly explanationInkDocument:
    PresidentExplanationInkDocument;

    readonly pdfInkDocuments:
    readonly PresidentReviewPdfInkDocument[];
}

export function createEmptyPresidentReviewDraftState():
    PresidentReviewDraftState {
    return {
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

        explanationInkDocument:
            createEmptyPresidentExplanationInkDocument(),

        pdfInkDocuments:
            [],
    };
}