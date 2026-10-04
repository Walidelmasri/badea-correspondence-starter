import { InkStroke } from './ink.model';

interface InkDocumentState {
  readonly strokesByPage: Map<number, InkStroke[]>;
  readonly history: InkStroke[];
  readonly redo: InkStroke[];
}

/**
 * In-memory annotation state for the Pencil spike.
 *
 * Persistence is deliberately outside this class. The backend/Alfresco
 * integration will later serialize the same document/page stroke model.
 */
export class InkDocumentStore {
  private readonly documents =
    new Map<string, InkDocumentState>();

  pageStrokes(
    documentKey: string,
    pageNumber: number,
  ): readonly InkStroke[] {
    return (
      this.state(documentKey).strokesByPage.get(pageNumber) ?? []
    );
  }

  add(
    documentKey: string,
    stroke: InkStroke,
  ): void {
    const state = this.state(documentKey);
    const page = this.mutablePageStrokes(
      state,
      stroke.pageNumber,
    );

    page.push(stroke);
    state.history.push(stroke);
    state.redo.length = 0;
  }

  undo(documentKey: string): InkStroke | null {
    const state = this.state(documentKey);
    const stroke = state.history.pop();

    if (!stroke) {
      return null;
    }

    const page = state.strokesByPage.get(stroke.pageNumber);

    if (page) {
      const index = page.findIndex(
        (candidate) => candidate.id === stroke.id,
      );

      if (index >= 0) {
        page.splice(index, 1);
      }
    }

    state.redo.push(stroke);
    return stroke;
  }

  redo(documentKey: string): InkStroke | null {
    const state = this.state(documentKey);
    const stroke = state.redo.pop();

    if (!stroke) {
      return null;
    }

    this.mutablePageStrokes(
      state,
      stroke.pageNumber,
    ).push(stroke);

    state.history.push(stroke);
    return stroke;
  }

  canUndo(documentKey: string): boolean {
    return this.state(documentKey).history.length > 0;
  }

  canRedo(documentKey: string): boolean {
    return this.state(documentKey).redo.length > 0;
  }

  private state(documentKey: string): InkDocumentState {
    const existing = this.documents.get(documentKey);

    if (existing) {
      return existing;
    }

    const created: InkDocumentState = {
      strokesByPage: new Map(),
      history: [],
      redo: [],
    };

    this.documents.set(documentKey, created);
    return created;
  }

  private mutablePageStrokes(
    state: InkDocumentState,
    pageNumber: number,
  ): InkStroke[] {
    const existing = state.strokesByPage.get(pageNumber);

    if (existing) {
      return existing;
    }

    const created: InkStroke[] = [];
    state.strokesByPage.set(pageNumber, created);
    return created;
  }
}
