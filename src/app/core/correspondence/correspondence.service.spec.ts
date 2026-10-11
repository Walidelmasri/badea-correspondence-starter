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
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest';

import {
  BADEA_CORRESPONDENCE_DEPARTMENTS_URL,
  badeaCorrespondenceTransitionUrl,
} from './correspondence.constants';

import {
  CorrespondenceDepartment,
  CorrespondenceTransitionResponse,
} from './correspondence.model';

import {
  CorrespondenceService,
} from './correspondence.service';

describe(
  'CorrespondenceService',
  () => {
    let service:
      CorrespondenceService;

    let http:
      HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          CorrespondenceService,

          provideHttpClient(),

          provideHttpClientTesting(),
        ],
      });

      service =
        TestBed.inject(
          CorrespondenceService,
        );

      http =
        TestBed.inject(
          HttpTestingController,
        );
    });

    afterEach(() => {
      http.verify();
    });

    it('loads correspondence departments', () => {
      const next = vi.fn();

      const departments:
        readonly CorrespondenceDepartment[] = [
          {
            code: '101',
            nameEnglish:
              'Office of the President',
            nameArabic:
              'مكتب رئيس المصرف',
          },
          {
            code: '117',
            nameEnglish:
              'Digital Transformation Department',
            nameArabic:
              'إدارة التحول الرقمي',
          },
        ];

      service
        .getDepartments()
        .subscribe({
          next,
        });

      const request =
        http.expectOne(
          BADEA_CORRESPONDENCE_DEPARTMENTS_URL,
        );

      expect(
        request.request.method,
      ).toBe('GET');

      request.flush(
        departments,
      );

      expect(next)
        .toHaveBeenCalledOnce();

      expect(next)
        .toHaveBeenCalledWith(
          departments,
        );
    });

    it('transitions correspondence without a target department', () => {
      const technicalId =
        '4519dccf-c777-4514-b8b8-ac4c713ebd2b';

      const next = vi.fn();

      service
        .transition(
          technicalId,
          {
            action:
              'SEND_TO_OFFICE',
          },
        )
        .subscribe({
          next,
        });

      const request =
        http.expectOne(
          badeaCorrespondenceTransitionUrl(
            technicalId,
          ),
        );

      expect(
        request.request.method,
      ).toBe('POST');

      expect(
        request.request.body,
      ).toEqual({
        action:
          'SEND_TO_OFFICE',
      });

      const response:
        CorrespondenceTransitionResponse = {
          technicalId,
          state:
            'WITH_PRESIDENT_OFFICE',
        };

      request.flush(
        response,
      );

      expect(next)
        .toHaveBeenCalledOnce();

      expect(next)
        .toHaveBeenCalledWith(
          response,
        );
    });

    it('sends the selected department when routing to a director', () => {
      const technicalId =
        '4519dccf-c777-4514-b8b8-ac4c713ebd2b';

      service
        .transition(
          technicalId,
          {
            action:
              'SEND_TO_DIRECTOR',

            targetDepartmentCode:
              '117',
          },
        )
        .subscribe();

      const request =
        http.expectOne(
          badeaCorrespondenceTransitionUrl(
            technicalId,
          ),
        );

      expect(
        request.request.method,
      ).toBe('POST');

      expect(
        request.request.body,
      ).toEqual({
        action:
          'SEND_TO_DIRECTOR',

        targetDepartmentCode:
          '117',
      });

      request.flush({
        technicalId,
        state:
          'WITH_DIRECTOR',
      } satisfies
        CorrespondenceTransitionResponse);
    });
  },
);