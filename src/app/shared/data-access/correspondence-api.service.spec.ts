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
});