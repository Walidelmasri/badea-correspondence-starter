import {
  provideHttpClient,
} from '@angular/common/http';

import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import {
  TestBed,
} from '@angular/core/testing';

import {
  CorrespondenceApiService,
  CreateCorrespondenceDraft,
} from './correspondence-api.service';

import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest';

describe('CorrespondenceApiService', () => {
  let service: CorrespondenceApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(
      CorrespondenceApiService,
    );

    http = TestBed.inject(
      HttpTestingController,
    );
  });

  afterEach(() => {
    http.verify();
  });

  it('creates a draft with document metadata', () => {
    const payload: CreateCorrespondenceDraft = {
      subject: 'Quarterly Review',
      bodyText: 'Please review the attached document.',
      documentNumber: 'DOC-2026-001',
      documentDate: '2026-10-13',
      sourceText: 'BADEA',
    };

    service.createDraft(payload).subscribe(
      (result) => {
        expect(result.technicalId).toBe(
          '4519dccf-c777-4514-b8b8-ac4c713ebd2b',
        );

        expect(result.state).toBe('DRAFT');
      },
    );

    const request = http.expectOne(
      '/alfresco/service/badea/correspondence',
    );

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);

    request.flush(
      {
        technicalId:
          '4519dccf-c777-4514-b8b8-ac4c713ebd2b',
        state: 'DRAFT',
      },
      {
        status: 201,
        statusText: 'Created',
      },
    );
  });

  it('uploads the original PDF bytes', () => {
    const file = new File(
      ['%PDF-1.7\nTest content'],
      'Board Report 2026.pdf',
      {
        type: 'application/pdf',
      },
    );

    service.uploadDocument(
      '4519dccf-c777-4514-b8b8-ac4c713ebd2b',
      file,
    ).subscribe((result) => {
      expect(result.filename).toBe(
        'Board Report 2026.pdf',
      );

      expect(result.mimeType).toBe(
        'application/pdf',
      );
    });

    const request = http.expectOne(
      '/alfresco/service/badea/correspondence/' +
      '4519dccf-c777-4514-b8b8-ac4c713ebd2b/' +
      'documents?filename=Board%20Report%202026.pdf',
    );

    expect(request.request.method).toBe('POST');

    expect(
      request.request.headers.get('Content-Type'),
    ).toBe('application/pdf');

    expect(request.request.body).toBe(file);

    request.flush(
      {
        nodeId:
          '22222222-2222-2222-2222-222222222222',
        filename: 'Board Report 2026.pdf',
        mimeType: 'application/pdf',
        sizeBytes: file.size,
      },
      {
        status: 201,
        statusText: 'Created',
      },
    );
  });
  it('retrieves an existing draft and its attachments', () => {
    const technicalId = '4519dccf-c777-4514-b8b8-ac4c713ebd2b';

    service.getDetails(technicalId).subscribe((details) => {
      expect(details.state).toBe('DRAFT');
      expect(details.subject).toBe('Quarterly Review');
      expect(details.documents).toHaveLength(1);
      expect(details.documents[0].filename).toBe('report.pdf');
    });

    const request = http.expectOne(
      `/alfresco/service/badea/correspondence/${technicalId}`,
    );

    expect(request.request.method).toBe('GET');

    request.flush({
      technicalId,
      state: 'DRAFT',
      subject: 'Quarterly Review',
      bodyText: 'Please review.',
      documentNumber: 'DOC-2026-001',
      documentDate: '2026-10-13',
      sourceText: 'BADEA',
      originDepartmentCode: '117',
      createdByNameEnglish: 'Test Employee',
      createdByNameArabic: null,
      documents: [
        {
          nodeId: '22222222-2222-2222-2222-222222222222',
          filename: 'report.pdf',
          mimeType: 'application/pdf',
          sizeBytes: 1024,
        },
      ],
    });
  });

  it('updates the same draft instead of creating another', () => {
    const technicalId = '4519dccf-c777-4514-b8b8-ac4c713ebd2b';

    const metadata: CreateCorrespondenceDraft = {
      subject: 'Updated Quarterly Review',
      bodyText: 'Updated description',
      documentNumber: null,
      documentDate: '2026-10-14',
      sourceText: 'BADEA',
    };

    service.updateDraft(technicalId, metadata).subscribe((result) => {
      expect(result.technicalId).toBe(technicalId);
      expect(result.state).toBe('DRAFT');
    });

    const request = http.expectOne(
      `/alfresco/service/badea/correspondence/${technicalId}/draft`,
    );

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(metadata);

    request.flush({
      technicalId,
      state: 'DRAFT',
    });
  });

  it('loads real drafts with pagination', () => {
    service.listDrafts(0, 25).subscribe((page) => {
      expect(page.total).toBe(1);
      expect(page.items).toHaveLength(1);

      expect(page.items[0].subject).toBe(
        'Test Draft 1',
      );
    });

    const request = http.expectOne(
      (req) =>
        req.url ===
        '/alfresco/service/badea/correspondence' &&
        req.params.get('box') === 'drafts' &&
        req.params.get('offset') === '0' &&
        req.params.get('limit') === '25',
    );

    expect(request.request.method).toBe('GET');

    request.flush({
      items: [
        {
          technicalId:
            '03e1930c-de08-46d1-8d29-18ae7d2add1d',
          subject: 'Test Draft 1',
          documentNumber: 'Test',
          createdAt: '2026-10-18T08:00:00Z',
          modifiedAt: '2026-10-18T08:10:00Z',
        },
      ],
      offset: 0,
      limit: 25,
      total: 1,
    });
  });
  it('deletes a saved draft attachment', () => {
    let completed = false;

    service.deleteDocument(
      'draft-123',
      'document-456',
    ).subscribe({
      complete: () => {
        completed = true;
      },
    });

    const request = http.expectOne(
      '/alfresco/service/badea/correspondence/' +
      'draft-123/documents/document-456',
    );

    expect(request.request.method).toBe('DELETE');

    request.flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    expect(completed).toBe(true);
  });

  it('preserves attachment deletion errors', () => {
    let status: number | null = null;

    service.deleteDocument(
      'draft-123',
      'document-456',
    ).subscribe({
      error: (error: { status: number }) => {
        status = error.status;
      },
    });

    const request = http.expectOne(
      '/alfresco/service/badea/correspondence/' +
      'draft-123/documents/document-456',
    );

    request.flush(
      { code: 'CORRESPONDENCE_FORBIDDEN' },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );

    expect(status).toBe(403);
  });
  it('requests subsequent draft pages correctly', () => {
    service.listDrafts(25, 25).subscribe((page) => {
      expect(page.offset).toBe(25);
      expect(page.items).toEqual([]);
    });

    const request = http.expectOne(
      (req) =>
        req.url ===
        '/alfresco/service/badea/correspondence' &&
        req.params.get('box') === 'drafts' &&
        req.params.get('offset') === '25' &&
        req.params.get('limit') === '25',
    );

    expect(request.request.method).toBe('GET');

    request.flush({
      items: [],
      offset: 25,
      limit: 25,
      total: 25,
    });
  });
  it('preserves upload errors for the caller', () => {
    const file = new File(
      ['%PDF-1.7'],
      'document.pdf',
      {
        type: 'application/pdf',
      },
    );

    let status: number | null = null;

    service.uploadDocument(
      'test-id',
      file,
    ).subscribe({
      error: (error: { status: number }) => {
        status = error.status;
      },
    });

    const request = http.expectOne(
      '/alfresco/service/badea/correspondence/' +
      'test-id/documents?filename=document.pdf',
    );

    request.flush(
      {
        code: 'CORRESPONDENCE_FORBIDDEN',
      },
      {
        status: 403,
        statusText: 'Forbidden',
      },
    );

    expect(status).toBe(403);
  });
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

});