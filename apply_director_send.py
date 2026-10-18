#!/usr/bin/env python3
"""BADEA Presidency Correspondence: fail-closed Director Send checkpoint.
Run at repo root: python3 apply_director_send.py
Only touches four named Angular files + adds a component test.
No git commit, Docker operation, network call, or overwrite of backups.
"""
from pathlib import Path
import re
import sys

ROOT = Path.cwd()
API = Path('src/app/shared/data-access/correspondence-api.service.ts')
API_TEST = Path('src/app/shared/data-access/correspondence-api.service.spec.ts')
DIRECTOR = Path('src/app/features/director/presentation/director-new-page.component.ts')
HTML = Path('src/app/features/director/presentation/director-new-page.component.html')
TRANS = Path('src/app/core/i18n/translations.ts')
COMPONENT_TEST = Path('src/app/features/director/presentation/director-new-page.component.spec.ts')

for filename in (API, API_TEST, DIRECTOR, HTML, TRANS):
    if not (ROOT / filename).is_file():
        sys.exit(f'ERROR: missing {filename}. Run at repository root.')

changes = {path: (ROOT / path).read_text(encoding='utf-8') for path in (API, API_TEST, DIRECTOR, HTML, TRANS)}

def once(s, needle, replacement, where):
    count = s.count(needle)
    if count != 1:
        raise ValueError(f'{where}: expected 1 occurrence of anchor ({count} found): {needle[:90]!r}')
    return s.replace(needle, replacement, 1)

try:
    # Typed transport for all workflow roles, not a Director-only API.
    s = changes[API]
    if 'transitionCorrespondence(' in s:
        raise ValueError(f'{API}: transitionCorrespondence already exists; review manually to avoid duplicates')
    s = once(s, '@Injectable({', '''export type CorrespondenceTransitionAction =
  | 'SEND_TO_OFFICE'
  | 'SEND_TO_PRESIDENT'
  | 'SEND_TO_DIRECTOR'
  | 'MARK_DONE'
  | 'ADMINISTRATIVE_CLOSE';

export interface CorrespondenceTransitionRequest {
  readonly action: CorrespondenceTransitionAction;
  readonly targetDepartmentCode?: string;
  readonly explanation?: string;
  readonly closureReason?: string;
}

export interface CorrespondenceTransitionResult {
  readonly technicalId: string;
  readonly state: CorrespondenceDetails['state'];
}

@Injectable({''', API)
    needle = '  uploadDocument(\n'
    snippet = '''  transitionCorrespondence(
    technicalId: string,
    request: CorrespondenceTransitionRequest,
  ): Observable<CorrespondenceTransitionResult> {
    return this.http.post<CorrespondenceTransitionResult>(
      `${CORRESPONDENCE_URL}/${encodeURIComponent(technicalId)}/transition`,
      request,
    );
  }

'''
    s = once(s, needle, snippet + needle, API)
    changes[API] = s

    # API test: verifies exact POST request, HTTP payload, and state.
    s = changes[API_TEST]
    if "it('posts SEND_TO_OFFICE" in s:
        raise ValueError(f'{API_TEST}: SEND_TO_OFFICE test already present')
    pos = s.rfind('\n});')
    if pos == -1:
        raise ValueError(f'{API_TEST}: could not locate closing describe block')
    test = '''
  it('posts SEND_TO_OFFICE to the existing workflow endpoint', () => {
    const id = '03e1930c-de08-46d1-8d29-18ae7d2add1d';

    service.transitionCorrespondence(id, {
      action: 'SEND_TO_OFFICE',
    }).subscribe((result) => {
      expect(result.state).toBe('WITH_PRESIDENT_OFFICE');
      expect(result.technicalId).toBe(id);
    });

    const request = http.expectOne(
      `/alfresco/service/badea/correspondence/${id}/transition`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ action: 'SEND_TO_OFFICE' });
    request.flush({
      technicalId: id,
      state: 'WITH_PRESIDENT_OFFICE',
    });
  });
'''
    changes[API_TEST] = s[:pos] + test + s[pos:]

    # Central bilingual i18n (two director blocks required).
    s = changes[TRANS]
    matches = list(re.finditer(r'(?m)^\s*director:\s*\{', s))
    if len(matches) != 2:
        raise ValueError(f'{TRANS}: expected 2 centralized director dictionaries, found {len(matches)}')
    for match in reversed(matches):
        start = match.end()
        # Find matching brace, accounting for nested objects (strings here don't contain braces).
        depth = 1
        end = start
        while depth:
            if end >= len(s):
                raise ValueError(f'{TRANS}: unterminated director dictionary')
            if s[end] == '{': depth += 1
            if s[end] == '}': depth -= 1
            end += 1
        block = s[start:end-1]
        if 'confirmSend:' in block:
            raise ValueError(f'{TRANS}: send translations already present')
        arabic = 'مساحة عمل المدير' in block
        addition = ('''
    send: 'إرسال إلى مكتب الرئيس',
    sending: 'جارٍ الإرسال…',
    confirmSend: 'هل تريد إرسال المراسلة إلى مكتب الرئيس؟ لن تتمكن من تعديلها كمسودة بعد الإرسال.',
    sent: 'تم إرسال المراسلة إلى مكتب الرئيس بنجاح.',
    sendBlocked: 'لم يتم إرسال المراسلة. يوجد مستند لم يتم رفعه بنجاح أو يحتاج إلى التحقق. راجع المرفقات أولاً.',
    sendFailed: 'تعذر إرسال المراسلة. يرجى إعادة تحميل المسودات والتحقق من حالتها قبل المحاولة مجدداً.',
    sendUncertain: 'تعذر تأكيد نتيجة الإرسال. لا تحاول الإرسال مرة ثانية قبل التحقق من المراسلة في المسودات أو المرسل.',
''' if arabic else '''
    send: 'Send to President’s Office',
    sending: 'Sending…',
    confirmSend: 'Send this correspondence to the President’s Office? You will no longer be able to edit it as a draft.',
    sent: 'Correspondence sent to the President’s Office.',
    sendBlocked: 'Not sent. At least one attachment failed to upload or needs verification. Resolve the attachments first.',
    sendFailed: 'The correspondence could not be sent. Reload Drafts and check its status before trying again.',
    sendUncertain: 'The send result could not be confirmed. Do not send again until you have checked Drafts or Sent.',
''')
        s = s[:end-1] + addition + s[end-1:]
    changes[TRANS] = s

    # Director component: keep a single save/upload pipeline and optionally transition.
    s = changes[DIRECTOR]
    s = once(s, '  readonly deletionUncertain = signal(false);',
             '''  readonly deletionUncertain = signal(false);
  readonly sending = signal(false);
  readonly submissionUncertain = signal(false);''', DIRECTOR)
    s = once(s, '  async saveDraft(): Promise<void> {',
             '''  async sendDraft(): Promise<void> {
    await this.saveDraft(true);
  }

  async saveDraft(sendAfterSave = false): Promise<void> {''', DIRECTOR)
    start = s.index('  async saveDraft(sendAfterSave = false): Promise<void> {')
    before, body = s[:start], s[start:]
    was_creating_pattern = re.compile(r'(?m)^\s*const wasCreating\s*=\s*this\.technicalId\(\)\s*===\s*null;')
    creating = was_creating_pattern.search(body)
    if creating is None:
        raise ValueError(f'{DIRECTOR}: cannot find wasCreating assignment')
    body = body[:creating.start()] + '''
    if (sendAfterSave && !window.confirm(this.labels().confirmSend)) {
      return;
    }

    const wasCreating = this.technicalId() === null;''' + body[creating.end():]
    body = once(body, '    this.busy.set(true);',
                '''    this.busy.set(true);
    this.sending.set(sendAfterSave);''', DIRECTOR)
    # Existing success notice, still used for Save Draft and failed uploads.
    notice_pattern = re.compile(r'''      this\.notice\.set\(\{\s*
\s*type:\s*failed\s*\|\|\s*uncertain\s*\?\s*'warning'\s*:\s*'success',(?s:.*?)\n\s*\}\);''')
    hit = notice_pattern.search(body)
    if not hit:
        raise ValueError(f'{DIRECTOR}: cannot find the post-upload save notice')
    indented_notice = '\n'.join('  ' + line for line in hit.group(0).splitlines())
    replacement = '''      if (!sendAfterSave || failed || uncertain) {
''' + indented_notice + '''
      }

      if (sendAfterSave) {
        // All local files must have received a confirmed upload response.
        if (this.documents().length !== 0) {
          this.notice.set({
            type: 'warning',
            message: this.labels().sendBlocked,
          });
          return;
        }

        // Read back persisted attachments. This is read-only: no duplicate writes.
        let persisted;
        try {
          persisted = await firstValueFrom(this.api.getDetails(technicalId));
        } catch {
          this.submissionUncertain.set(true);
          this.notice.set({
            type: 'warning',
            message: this.labels().sendUncertain,
          });
          return;
        }

        const allAttachmentsPersisted = this.savedDocuments().every(
          (uploaded) => persisted.documents.some(
            (stored) => stored.nodeId === uploaded.nodeId,
          ),
        );

        if (persisted.state !== 'DRAFT' || !allAttachmentsPersisted) {
          this.submissionUncertain.set(true);
          this.notice.set({
            type: 'warning',
            message: this.labels().sendUncertain,
          });
          return;
        }

        this.savedDocuments.set(persisted.documents);

        try {
          const result = await firstValueFrom(
            this.api.transitionCorrespondence(technicalId, {
              action: 'SEND_TO_OFFICE',
            }),
          );

          if (result.state !== 'WITH_PRESIDENT_OFFICE') {
            this.submissionUncertain.set(true);
            this.notice.set({
              type: 'warning',
              message: this.labels().sendUncertain,
            });
            return;
          }

          this.readOnly.set(true);
          this.form.disable({ emitEvent: false });
          this.notice.set({
            type: 'success',
            message: this.labels().sent,
          });
        } catch (error: unknown) {
          // Never retry a transition automatically: it may have committed.
          this.submissionUncertain.set(true);
          const transportUncertain =
            error instanceof HttpErrorResponse &&
            (error.status === 0 || error.status >= 500);
          this.notice.set({
            type: transportUncertain ? 'warning' : 'error',
            message: transportUncertain
              ? this.labels().sendUncertain
              : this.labels().sendFailed,
          });
        }
      }'''
    body = body[:hit.start()] + replacement + body[hit.end():]
    body = once(body, '        !this.deletionUncertain()',
                '''        !this.deletionUncertain() &&
        !this.submissionUncertain()''', DIRECTOR)
    body = once(body, '      this.busy.set(false);',
                '''      this.busy.set(false);
      this.sending.set(false);''', DIRECTOR)
    s = before + body
    mod_idx = s.find('  private modificationsBlocked(): boolean')
    if mod_idx == -1:
        raise ValueError(f'{DIRECTOR}: missing existing modificationsBlocked() guard')
    s = s[:mod_idx] + once(
        s[mod_idx:], 'this.deletionUncertain()',
        'this.deletionUncertain() || this.submissionUncertain()', DIRECTOR,
    )
    changes[DIRECTOR] = s

    # Director HTML: use existing styling, maintain the normal submit path.
    s = changes[HTML]
    start = s.find('<footer class="compose-actions">')
    if start < 0 or s.find('<footer class="compose-actions">', start+1) >= 0:
        raise ValueError(f'{HTML}: expected exactly one compose-actions footer')
    end = s.find('</footer>', start)
    if end < 0:
        raise ValueError(f'{HTML}: footer not closed')
    end += len('</footer>')
    footer = '''<footer class="compose-actions">
      <span class="director-form__footer-note">
        {{ labels().destination }}
      </span>

      <div class="compose-actions__primary-group">
        <button
          class="compose-actions__draft"
          type="submit"
          [disabled]="
            busy() || readOnly() || creationUncertain() ||
            deletingDocumentId() !== null || deletionUncertain() ||
            submissionUncertain()
          "
        >
          {{ busy() && !sending() ? labels().saving : labels().save }}
        </button>

        <button
          class="compose-actions__send"
          type="button"
          [disabled]="
            busy() || readOnly() || creationUncertain() ||
            deletingDocumentId() !== null || deletionUncertain() ||
            submissionUncertain()
          "
          (click)="sendDraft()"
        >
          {{ sending() ? labels().sending : labels().send }}
        </button>
      </div>
    </footer>'''
    changes[HTML] = s[:start] + footer + s[end:]
except ValueError as exc:
    sys.exit('NO FILES CHANGED — ' + str(exc))

# Focused Director Send behavior tests, with mocked transport and no Alfresco writes.
if (ROOT / COMPONENT_TEST).exists():
    sys.exit(f'NO FILES CHANGED — {COMPONENT_TEST} already exists; review it before updating')
component_tests = r"""import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthSessionStore } from '../../../core/auth/auth-session.store';
import { TranslationService } from '../../../core/i18n/translation.service';
import { CorrespondenceApiService } from '../../../shared/data-access/correspondence-api.service';
import { DirectorNewPageComponent } from './director-new-page.component';

const ID = '03e1930c-de08-46d1-8d29-18ae7d2add1d';
const NODE_ID = '22222222-2222-2222-2222-222222222222';
const PDF = new File(['%PDF-1.7 example'], 'sample.pdf', {
  type: 'application/pdf',
});
const uploaded = {
  nodeId: NODE_ID,
  filename: 'sample.pdf',
  mimeType: 'application/pdf',
  sizeBytes: PDF.size,
};

const labels = {
  confirmSend: 'Confirm send?',
  subjectRequired: 'Subject required',
  createUncertain: 'Creation uncertain',
  createFailed: 'Creation failed',
  updateUncertain: 'Update uncertain',
  updateFailed: 'Update failed',
  invalidFile: 'Invalid file',
  uploadUncertain: 'Upload uncertain',
  uploadFailed: 'Upload failed',
  saved: 'Draft saved',
  sent: 'Sent',
  sendBlocked: 'Cannot send with failed uploads',
  sendUncertain: 'Send uncertain',
  sendFailed: 'Send failed',
};

describe('Director Send', () => {
  const api = {
    createDraft: vi.fn(),
    updateDraft: vi.fn(),
    uploadDocument: vi.fn(),
    getDetails: vi.fn(),
    transitionCorrespondence: vi.fn(),
  };
  let page: DirectorNewPageComponent;
  let calls: string[];

  beforeEach(() => {
    calls = [];
    Object.values(api).forEach((mock) => mock.mockReset());
    api.createDraft.mockImplementation(() => {
      calls.push('create');
      return of({ technicalId: ID, state: 'DRAFT' });
    });
    api.uploadDocument.mockImplementation(() => {
      calls.push('upload');
      return of(uploaded);
    });
    api.getDetails.mockImplementation(() => {
      calls.push('verify');
      return of({
        technicalId: ID,
        state: 'DRAFT',
        subject: 'Test correspondence',
        documentNumber: null,
        documentDate: null,
        bodyText: null,
        sourceText: null,
        originDepartmentCode: '117',
        createdByNameEnglish: 'Test',
        createdByNameArabic: null,
        documents: [uploaded],
      });
    });
    api.transitionCorrespondence.mockImplementation(() => {
      calls.push('transition');
      return of({ technicalId: ID, state: 'WITH_PRESIDENT_OFFICE' });
    });

    vi.spyOn(window, 'confirm').mockReturnValue(true);

    TestBed.configureTestingModule({
      providers: [
        { provide: CorrespondenceApiService, useValue: api },
        { provide: AuthSessionStore, useValue: { currentUser: () => null } },
        {
          provide: TranslationService,
          useValue: { text: () => ({ director: labels }), language: () => 'en' },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => null } } },
        },
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
      ],
    });
    page = TestBed.runInInjectionContext(() => new DirectorNewPageComponent());
    page.form.controls.subject.setValue('Test correspondence');
  });

  afterEach(() => vi.restoreAllMocks());

  function selectPdf(): void {
    const input = { files: [PDF], value: 'selected' } as unknown as HTMLInputElement;
    page.onFilesSelected({ target: input } as unknown as Event);
  }

  it('persists draft and PDF before sending and locks the form on success', async () => {
    selectPdf();
    await page.sendDraft();
    expect(calls).toEqual(['create', 'upload', 'verify', 'transition']);
    expect(api.transitionCorrespondence).toHaveBeenCalledWith(ID, {
      action: 'SEND_TO_OFFICE',
    });
    expect(page.readOnly()).toBe(true);
    expect(page.form.disabled).toBe(true);
  });

  it('Save Draft never sends', async () => {
    selectPdf();
    await page.saveDraft();
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
    expect(page.readOnly()).toBe(false);
  });

  it('never sends after a definite PDF upload failure', async () => {
    selectPdf();
    api.uploadDocument.mockImplementation(() =>
      throwError(() => new HttpErrorResponse({ status: 400 }))
    );
    await page.sendDraft();
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
    expect(page.documents()[0]?.status).toBe('failed');
  });

  it('never sends after an uncertain PDF upload', async () => {
    selectPdf();
    api.uploadDocument.mockImplementation(() =>
      throwError(() => new HttpErrorResponse({ status: 0 }))
    );
    await page.sendDraft();
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
    expect(page.documents()[0]?.status).toBe('verify');
  });

  it('blocks duplicate Send attempts after an uncertain transition', async () => {
    api.getDetails.mockImplementation(() => of({
      technicalId: ID, state: 'DRAFT', subject: 'Test correspondence',
      documentNumber: null, documentDate: null, bodyText: null,
      sourceText: null, originDepartmentCode: '117',
      createdByNameEnglish: 'Test', createdByNameArabic: null,
      documents: [],
    }));
    api.transitionCorrespondence.mockImplementation(() =>
      throwError(() => new HttpErrorResponse({ status: 0 }))
    );
    await page.sendDraft();
    expect(page.submissionUncertain()).toBe(true);
    await page.sendDraft();
    expect(api.transitionCorrespondence).toHaveBeenCalledTimes(1);
  });

  it('cancelling the confirmation does not persist or send', async () => {
    vi.mocked(window.confirm).mockReturnValue(false);
    await page.sendDraft();
    expect(api.createDraft).not.toHaveBeenCalled();
    expect(api.transitionCorrespondence).not.toHaveBeenCalled();
  });
});
"""

# All validation succeeded; only now write files.
for path, text in changes.items():
    (ROOT / path).write_text(text, encoding='utf-8')
(ROOT / COMPONENT_TEST).write_text(component_tests, encoding='utf-8')
print('Applied Director Send in:')
for path in changes:
    print('  ', path)
print('  ', COMPONENT_TEST)
print('\nNext: npm test -- --watch=false && npm run build && git diff --check')
print('Do not commit or deploy until tests/build pass.')
