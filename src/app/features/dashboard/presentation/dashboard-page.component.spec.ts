import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { TarasolSecretaryApiService } from '../../tarasol-secretary/tarasol-secretary-api.service';
import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceApiService } from '../../../shared/data-access/correspondence-api.service';
import { DashboardPageComponent } from './dashboard-page.component';

describe('Dashboard primary action card', () => {
  it('puts Needs Action before total Inbox and highlights it even at zero', async () => {
    const labels = {
      eyebrow: '', title: 'Dashboard', subtitle: '', scoped: '', refreshing: '', refresh: '',
      loadFailed: '', retry: '', openInbox: 'Open Inbox', openSent: '', openDrafts: '',
      focusTitle: '', focusHint: 'Needs your action', handledStatus: '', archivedStatus: '',
      actionStatus: '', noNumber: '',
      stats: { received: 'Total Inbox', needsAction: 'Needs Action',
        handled: 'Handled', archived: 'Archived', sent: 'Sent', drafts: 'Drafts' },
      role: { SECRETARY: 'Secretary' },
    };
    const page = (total: number) => ({ items: [], offset: 0, limit: 7, total });
    const api = { listSecretaryInbox: vi.fn((unit: string, offset: number, limit: number, view: string) =>
      of(page(view === 'all' ? 7 : view === 'handled' ? 3 : 0))) };
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: CorrespondenceApiService, useValue: api },
      { provide: TarasolSecretaryApiService, useValue: { list: vi.fn(() => of({ items: [], total: 0 })) } },
      { provide: AuthSessionStore, useValue: {
        secretaryMailbox: () => true, secretaryInboxUnitCode: () => '117', effectiveRole: () => 'SECRETARY',
      } },
      { provide: TranslationService, useValue: { language: () => 'ar', text: () => ({ dashboard: labels }) } },
    ] });
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const cards = Array.from(fixture.nativeElement.querySelectorAll('.dashboard__stats .stat')) as HTMLElement[];
    expect(cards.length).toBe(4);
    expect(cards[0].classList.contains('stat--action')).toBe(true);
    expect(cards[0].textContent).toContain('Needs Action');
    expect(cards[0].textContent).toContain('0');
    expect(cards[1].classList.contains('stat--received')).toBe(true);
    expect(cards[1].textContent).toContain('7');
    expect(api.listSecretaryInbox).toHaveBeenCalledWith('117', 0, 7, 'needs_action');
  });
});
