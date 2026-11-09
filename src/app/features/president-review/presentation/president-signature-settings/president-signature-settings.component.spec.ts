import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { TranslationService } from '../../../../core/i18n/translation.service';
import { CorrespondenceApiService } from '../../../../shared/data-access/correspondence-api.service';
import { PresidentSignatureSettingsComponent } from './president-signature-settings.component';

describe('President signature popup', () => {
  function create(configured: boolean) {
    const api = {
      getPresidentSignatureStatus: vi.fn(() => of({
        configured, revision: 2, updatedAt: null,
        previewDataUrl: configured ? 'data:image/png;base64,AAAA' : null,
      })),
      registerPresidentSignature: vi.fn(() => of({
        configured: true, revision: 3, updatedAt: null,
        previewDataUrl: 'data:image/png;base64,AAAA',
      })),
      deactivatePresidentSignature: vi.fn(() => of({
        configured: false, revision: 3, updatedAt: null, previewDataUrl: null,
      })),
    };
    TestBed.configureTestingModule({providers: [
      {provide: CorrespondenceApiService, useValue: api},
      {provide: TranslationService, useValue: { language: () => 'en' }},
    ]});
    const fixture = TestBed.createComponent(PresidentSignatureSettingsComponent);
    fixture.componentInstance.modal = true;
    fixture.detectChanges();
    return {fixture, component: fixture.componentInstance, api};
  }

  it('opens a modal with a preview and Change and Remove controls when saved', async () => {
    const {fixture, component, api} = create(true);
    await component.toggle();
    fixture.detectChanges();
    const html = fixture.nativeElement as HTMLElement;
    expect(html.querySelector('[role="dialog"]')).not.toBeNull();
    expect(html.querySelector('img[alt="Saved signature preview"]')).not.toBeNull();
    expect(html.textContent).toContain('Change signature');
    expect(html.textContent).toContain('Remove signature');
    expect(api.getPresidentSignatureStatus).toHaveBeenCalledOnce();
  });

  it('shows Save rather than Change when no signature exists', async () => {
    const {fixture, component} = create(false);
    await component.toggle();
    fixture.detectChanges();
    const html = fixture.nativeElement as HTMLElement;
    expect(html.textContent).toContain('Save signature');
    expect(html.textContent).not.toContain('Change signature');
    expect(html.querySelector('canvas')).not.toBeNull();
  });

  it('uses optimistic revision for replacing a saved signature', async () => {
    const {component, api} = create(true);
    await component.toggle();
    component.beginEdit();
    component.strokes.push([[0.1, 0.1], [0.3, 0.5], [0.7, 0.8], [0.9, 0.9]]);
    component.consented.set(true);
    await component.save();
    expect(api.registerPresidentSignature).toHaveBeenCalledWith({
      expectedRevision: 2, authorizeReuse: true,
      drawing: {schemaVersion: 1, strokes: [[[0.1, 0.1], [0.3, 0.5], [0.7, 0.8], [0.9, 0.9]]]},
    });
    expect(component.editing()).toBe(false);
  });

  it('requires an explicit confirmation before deactivating the signature', async () => {
    const {component, api} = create(true);
    await component.toggle();
    await component.remove();
    expect(api.deactivatePresidentSignature).not.toHaveBeenCalled();
    component.confirmRemoval.set(true);
    await component.remove();
    expect(api.deactivatePresidentSignature).toHaveBeenCalledWith(2);
    expect(component.status()?.configured).toBe(false);
  });
});
