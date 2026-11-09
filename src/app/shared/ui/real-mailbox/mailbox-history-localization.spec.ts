import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { CorrespondenceService } from '../../../core/correspondence/correspondence.service';
import { UnreadInboxStore } from '../../../core/notifications/unread-inbox.store';
import { TranslationService } from '../../../core/i18n/translation.service';
import { TRANSLATIONS } from '../../../core/i18n/translations';
import { TarasolSecretaryApiService } from '../../../features/tarasol-secretary/tarasol-secretary-api.service';
import { TarasolRoundContinuationApiService } from '../../../features/tarasol-secretary/tarasol-round-continuation-api.service';
import { CorrespondenceApiService } from '../../data-access/correspondence-api.service';
import { RealMailboxComponent } from './real-mailbox.component';

const actions = [
  ['SEND_TO_OFFICE', 'أُرسلت إلى مكتب الرئيس', 'Sent to President’s Office'],
  ['SEND_TO_PRESIDENT', 'أُرسلت إلى الرئيس', 'Forwarded to President'],
  ['SEND_TO_DIRECTOR', 'أُرسلت إلى المدير', 'Sent to Director'],
  ['DISTRIBUTE_TO_DEPARTMENTS', 'توزيع إلى الإدارات', 'Distributed to departments'],
  ['COMPLETE_DEPARTMENT_ASSIGNMENT', 'اكتمل إجراء الإدارة', 'Department assignment completed'],
  ['MARK_DONE', 'اكتمل الإجراء', 'Marked complete'],
  ['ADMINISTRATIVE_CLOSE', 'أُغلقت المراسلة إدارياً', 'Administratively archived'],
  ['CONTINUE_CORRESPONDENCE', 'بدأت دورة متابعة جديدة', 'Another workflow round started'],
  ['DIRECTOR_CONTINUE_CORRESPONDENCE', 'طلبت الإدارة دورة متابعة جديدة', 'Director started another correspondence round'],
  ['SUBMIT_FOR_MANAGER_APPROVAL', 'إرسال المراسلة لموافقة المدير', 'Submitted for manager approval'],
  ['REVISE_SECRETARY_DRAFT', 'تعديل المراسلة المعادة', 'Revised returned correspondence'],
  ['RETURN_TO_SECRETARY', 'إعادة المراسلة إلى السكرتارية للتعديل', 'Returned to secretary for revision'],
] as const;

function createMailbox() {
  const language = signal<'ar' | 'en'>('ar');
  TestBed.configureTestingModule({ providers: [
    { provide: TranslationService, useValue: {
      language: language.asReadonly(), text: computed(() => TRANSLATIONS[language()]),
    } },
    { provide: AuthSessionStore, useValue: {} },
    { provide: UnreadInboxStore, useValue: {} },
    { provide: CorrespondenceService, useValue: {} },
    { provide: CorrespondenceApiService, useValue: {} },
    { provide: TarasolSecretaryApiService, useValue: {} },
    { provide: TarasolRoundContinuationApiService, useValue: {} },
    { provide: Router, useValue: {} },
    { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
  ] });
  const mailbox = TestBed.runInInjectionContext(() => new RealMailboxComponent());
  return { mailbox, language };
}

describe('Correspondence History localization', () => {
  it('translates all persisted correspondence action codes to Arabic', () => {
    const { mailbox } = createMailbox();
    for (const [action, arabic] of actions) {
      expect(mailbox.actionLabel(action), action).toBe(arabic);
    }
  });

  it('translates all persisted correspondence action codes to English', () => {
    const { mailbox, language } = createMailbox();
    language.set('en');
    for (const [action, , english] of actions) {
      expect(mailbox.actionLabel(action), action).toBe(english);
    }
  });

  it('updates an existing History label when the interface language changes', () => {
    const { mailbox, language } = createMailbox();
    expect(mailbox.actionLabel('RETURN_TO_SECRETARY')).toBe('إعادة المراسلة إلى السكرتارية للتعديل');
    language.set('en');
    expect(mailbox.actionLabel('RETURN_TO_SECRETARY')).toBe('Returned to secretary for revision');
    language.set('ar');
    expect(mailbox.actionLabel('RETURN_TO_SECRETARY')).toBe('إعادة المراسلة إلى السكرتارية للتعديل');
  });

  it('preserves unknown historical action identifiers instead of inventing a translation', () => {
    const { mailbox, language } = createMailbox();
    expect(mailbox.actionLabel('FUTURE_WORKFLOW_ACTION')).toBe('FUTURE_WORKFLOW_ACTION');
    language.set('en');
    expect(mailbox.actionLabel('FUTURE_WORKFLOW_ACTION')).toBe('FUTURE_WORKFLOW_ACTION');
  });

  it('uses the selected language for actor names without changing the audit source', () => {
    const { mailbox, language } = createMailbox();
    const actor = { actorNameArabic: 'مدير الإدارة', actorNameEnglish: 'Department Manager' };
    expect(mailbox.actorDisplayName(actor)).toBe('مدير الإدارة');
    language.set('en');
    expect(mailbox.actorDisplayName(actor)).toBe('Department Manager');
    expect(actor).toEqual({ actorNameArabic: 'مدير الإدارة', actorNameEnglish: 'Department Manager' });
  });
});
