import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { CorrespondenceService } from '../../../core/correspondence/correspondence.service';
import { UnreadInboxStore } from '../../../core/notifications/unread-inbox.store';
import { TranslationService } from '../../../core/i18n/translation.service';
import { TRANSLATIONS } from '../../../core/i18n/translations';
import { TarasolSecretaryApiService } from '../../../features/tarasol-secretary/tarasol-secretary-api.service';
import { TarasolRoundContinuationApiService } from '../../../features/tarasol-secretary/tarasol-round-continuation-api.service';
import { CorrespondenceApiService, CorrespondenceDetails } from '../../data-access/correspondence-api.service';
import { RealMailboxComponent } from './real-mailbox.component';

const technicalId = 'president-review-test-id';

function createMailbox(role = 'PRESIDENT') {
  const language = signal<'ar' | 'en'>('ar');
  const navigate = vi.fn<(commands: unknown[]) => Promise<boolean>>();
  TestBed.configureTestingModule({ providers: [
    { provide: TranslationService, useValue: {
      language: language.asReadonly(), text: computed(() => TRANSLATIONS[language()]),
    } },
    { provide: AuthSessionStore, useValue: { effectiveRole: () => role } },
    { provide: UnreadInboxStore, useValue: {} },
    { provide: CorrespondenceService, useValue: {} },
    { provide: CorrespondenceApiService, useValue: {} },
    { provide: TarasolSecretaryApiService, useValue: {} },
    { provide: TarasolRoundContinuationApiService, useValue: {} },
    { provide: Router, useValue: { navigate } },
    { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
  ] });
  const mailbox = TestBed.runInInjectionContext(() => new RealMailboxComponent());
  Object.defineProperty(mailbox, 'box', { value: () => 'inbox' });
  mailbox.selectedId.set(technicalId);
  mailbox.details.set({ state: 'WITH_PRESIDENT' } as CorrespondenceDetails);
  return { mailbox, navigate, language };
}

describe('Open President Review loading state', () => {
  it('shows loading and blocks repeat navigation until the route succeeds', async () => {
    const { mailbox, navigate } = createMailbox();
    let resolveNavigation!: (result: boolean) => void;
    navigate.mockReturnValue(new Promise<boolean>(resolve => { resolveNavigation = resolve; }));

    const opening = mailbox.openPresidentReview();
    expect(mailbox.presidentReviewOpening()).toBe(true);
    expect(mailbox.labels().openingPresidentReview).toBe('جارٍ فتح مراجعة الرئيس...');
    await mailbox.openPresidentReview();
    expect(navigate).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/president-review', technicalId]);

    resolveNavigation(true);
    await opening;
    expect(mailbox.presidentReviewOpening()).toBe(false);
    expect(mailbox.presidentReviewOpenFailed()).toBe(false);
  });

  it('ends loading and shows an error if the lazy route fails to load', async () => {
    const { mailbox, navigate, language } = createMailbox();
    navigate.mockRejectedValue(new Error('Failed to fetch dynamically imported module'));

    await mailbox.openPresidentReview();
    expect(mailbox.presidentReviewOpening()).toBe(false);
    expect(mailbox.presidentReviewOpenFailed()).toBe(true);
    expect(mailbox.labels().presidentReviewOpenFailed).toContain('تعذر فتح');
    language.set('en');
    expect(mailbox.labels().presidentReviewOpenFailed).toContain('Could not open');
  });

  it('ends loading when a route guard cancels navigation, and allows retry', async () => {
    const { mailbox, navigate } = createMailbox();
    navigate.mockResolvedValueOnce(false).mockResolvedValueOnce(true);
    await mailbox.openPresidentReview();
    expect(mailbox.presidentReviewOpenFailed()).toBe(true);
    expect(mailbox.presidentReviewOpening()).toBe(false);
    await mailbox.openPresidentReview();
    expect(mailbox.presidentReviewOpenFailed()).toBe(false);
    expect(navigate).toHaveBeenCalledTimes(2);
  });

  it('does not navigate without President authorization or correspondence selection', async () => {
    const unauthorized = createMailbox('DIRECTOR');
    await unauthorized.mailbox.openPresidentReview();
    expect(unauthorized.navigate).not.toHaveBeenCalled();
    expect(unauthorized.mailbox.presidentReviewOpening()).toBe(false);
  });
});
