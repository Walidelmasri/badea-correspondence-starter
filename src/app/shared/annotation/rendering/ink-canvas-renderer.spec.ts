import {
    describe,
    expect,
    it,
    vi,
} from 'vitest';

import {
    PdfRotation,
} from '../domain/ink.model';

import {
    renderInkStroke,
    toBaseInkPoint,
} from './ink-canvas-renderer';

function createContext(): CanvasRenderingContext2D {
    return {
        save: vi.fn(),
        restore: vi.fn(),
        beginPath: vi.fn(),
        moveTo: vi.fn(),
        lineTo: vi.fn(),
        quadraticCurveTo: vi.fn(),
        stroke: vi.fn(),

        lineCap: 'butt',
        lineJoin: 'miter',
        strokeStyle: '',
        globalAlpha: 1,
        globalCompositeOperation: 'source-over',
        lineWidth: 1,
    } as unknown as CanvasRenderingContext2D;
}

describe('ink-canvas-renderer', () => {
    it.each([
        {
            rotation: 0 as PdfRotation,
            expectedX: 0.2,
            expectedY: 0.7,
        },
        {
            rotation: 90 as PdfRotation,
            expectedX: 0.7,
            expectedY: 0.8,
        },
        {
            rotation: 180 as PdfRotation,
            expectedX: 0.8,
            expectedY: 0.3,
        },
        {
            rotation: 270 as PdfRotation,
            expectedX: 0.3,
            expectedY: 0.2,
        },
    ])(
        'converts visual coordinates to stable base coordinates at $rotation°',
        ({
            rotation,
            expectedX,
            expectedY,
        }) => {
            const rect =
                new DOMRect(
                    100,
                    200,
                    400,
                    800,
                );

            const point =
                toBaseInkPoint(
                    rect,
                    180,
                    760,
                    0.75,
                    rotation,
                );

            expect(point.x)
                .toBeCloseTo(expectedX);

            expect(point.y)
                .toBeCloseTo(expectedY);

            expect(point.pressure)
                .toBe(0.75);
        },
    );

    it.each([
        {
            rotation: 0 as PdfRotation,
            expectedX: 40,
            expectedY: 70,
        },
        {
            rotation: 90 as PdfRotation,
            expectedX: 60,
            expectedY: 20,
        },
        {
            rotation: 180 as PdfRotation,
            expectedX: 160,
            expectedY: 30,
        },
        {
            rotation: 270 as PdfRotation,
            expectedX: 140,
            expectedY: 80,
        },
    ])(
        'renders stored base coordinates at the correct visual position at $rotation°',
        ({
            rotation,
            expectedX,
            expectedY,
        }) => {
            const context =
                createContext();

            renderInkStroke(
                context,
                {
                    width: 200,
                    height: 100,
                },
                rotation,
                {
                    tool: 'pen',
                    points: [
                        {
                            x: 0.2,
                            y: 0.7,
                            pressure: 0.5,
                        },
                    ],
                },
            );

            expect(
                context.moveTo,
            ).toHaveBeenCalledTimes(1);

            const [
                actualX,
                actualY,
            ] = vi.mocked(
                context.moveTo,
            ).mock.calls[0];

            expect(actualX)
                .toBeCloseTo(expectedX);

            expect(actualY)
                .toBeCloseTo(expectedY);
        },
    );

    it('clamps pointer coordinates and normalizes invalid pressure', () => {
        const rect =
            new DOMRect(
                100,
                200,
                400,
                800,
            );

        const point =
            toBaseInkPoint(
                rect,
                50,
                1100,
                Number.NaN,
                0,
            );

        expect(point).toEqual({
            x: 0,
            y: 1,
            pressure: 0.5,
        });
    });

    it('uses different rendering behaviour for pen and highlighter', () => {
        const penContext =
            createContext();

        const highlighterContext =
            createContext();

        const size = {
            width: 1000,
            height: 500,
        };

        const points = [
            {
                x: 0.25,
                y: 0.5,
                pressure: 0.5,
            },
        ];

        renderInkStroke(
            penContext,
            size,
            0,
            {
                tool: 'pen',
                points,
            },
        );

        renderInkStroke(
            highlighterContext,
            size,
            0,
            {
                tool: 'highlighter',
                points,
            },
        );

        expect(
            penContext.globalAlpha,
        ).toBe(1);

        expect(
            penContext.globalCompositeOperation,
        ).toBe('source-over');

        expect(
            highlighterContext.globalAlpha,
        ).toBe(0.28);

        expect(
            highlighterContext.globalCompositeOperation,
        ).toBe('multiply');

        expect(
            highlighterContext.lineWidth,
        ).toBeGreaterThan(
            penContext.lineWidth,
        );
    });
});