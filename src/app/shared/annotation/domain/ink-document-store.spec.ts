import {
    describe,
    expect,
    it,
} from 'vitest';

import {
    InkDocumentStore,
} from './ink-document-store';

import {
    InkStroke,
    PdfInkTool,
} from './ink.model';

function createStroke(
    id: string,
    pageNumber: number,
    tool: PdfInkTool = 'pen',
): InkStroke {
    return {
        id,
        pageNumber,
        tool,
        points: [
            {
                x: 0.25,
                y: 0.4,
                pressure: 0.5,
            },
            {
                x: 0.5,
                y: 0.6,
                pressure: 0.75,
            },
        ],
    };
}

describe('InkDocumentStore', () => {
    it('keeps strokes isolated by document and page', () => {
        const store =
            new InkDocumentStore();

        const documentAPage1 =
            createStroke(
                'document-a-page-1',
                1,
            );

        const documentAPage2 =
            createStroke(
                'document-a-page-2',
                2,
                'highlighter',
            );

        const documentBPage1 =
            createStroke(
                'document-b-page-1',
                1,
            );

        store.add(
            'document-a',
            documentAPage1,
        );

        store.add(
            'document-a',
            documentAPage2,
        );

        store.add(
            'document-b',
            documentBPage1,
        );

        expect(
            store.pageStrokes(
                'document-a',
                1,
            ),
        ).toEqual([
            documentAPage1,
        ]);

        expect(
            store.pageStrokes(
                'document-a',
                2,
            ),
        ).toEqual([
            documentAPage2,
        ]);

        expect(
            store.pageStrokes(
                'document-b',
                1,
            ),
        ).toEqual([
            documentBPage1,
        ]);

        expect(
            store.pageStrokes(
                'document-b',
                2,
            ),
        ).toEqual([]);
    });

    it('undoes and redoes strokes in the correct order', () => {
        const store =
            new InkDocumentStore();

        const firstStroke =
            createStroke(
                'stroke-1',
                1,
            );

        const secondStroke =
            createStroke(
                'stroke-2',
                1,
                'highlighter',
            );

        store.add(
            'document',
            firstStroke,
        );

        store.add(
            'document',
            secondStroke,
        );

        expect(
            store.canUndo('document'),
        ).toBe(true);

        expect(
            store.canRedo('document'),
        ).toBe(false);

        expect(
            store.undo('document'),
        ).toBe(secondStroke);

        expect(
            store.pageStrokes(
                'document',
                1,
            ),
        ).toEqual([
            firstStroke,
        ]);

        expect(
            store.canRedo('document'),
        ).toBe(true);

        expect(
            store.redo('document'),
        ).toBe(secondStroke);

        expect(
            store.pageStrokes(
                'document',
                1,
            ),
        ).toEqual([
            firstStroke,
            secondStroke,
        ]);

        expect(
            store.canRedo('document'),
        ).toBe(false);
    });

    it('clears redo history when a new stroke is added after undo', () => {
        const store =
            new InkDocumentStore();

        const firstStroke =
            createStroke(
                'stroke-1',
                1,
            );

        const undoneStroke =
            createStroke(
                'stroke-2',
                1,
            );

        const replacementStroke =
            createStroke(
                'stroke-3',
                1,
                'highlighter',
            );

        store.add(
            'document',
            firstStroke,
        );

        store.add(
            'document',
            undoneStroke,
        );

        store.undo('document');

        expect(
            store.canRedo('document'),
        ).toBe(true);

        store.add(
            'document',
            replacementStroke,
        );

        expect(
            store.canRedo('document'),
        ).toBe(false);

        expect(
            store.redo('document'),
        ).toBeNull();

        expect(
            store.pageStrokes(
                'document',
                1,
            ),
        ).toEqual([
            firstStroke,
            replacementStroke,
        ]);
    });
});