import {
  describe,
  expect,
  it,
} from 'vitest';

import {
  CorrespondenceDepartment,
} from '../../../core/correspondence/correspondence.model';

import {
  toPresidentReviewRecipients,
} from './president-review-recipient.model';

describe(
  'toPresidentReviewRecipients',
  () => {
    it('maps Oracle departments and excludes the President Office department', () => {
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
          {
            code: '119',
            nameEnglish:
              'Legal Affairs Department',
            nameArabic:
              'إدارة الشؤون القانونية',
          },
        ];

      expect(
        toPresidentReviewRecipients(
          departments,
        ),
      ).toEqual([
        {
          id: '117',
          nameEn:
            'Digital Transformation Department',
          nameAr:
            'إدارة التحول الرقمي',
        },
        {
          id: '119',
          nameEn:
            'Legal Affairs Department',
          nameAr:
            'إدارة الشؤون القانونية',
        },
        {
          id: 'other',
          nameEn: 'Other',
          nameAr: 'أخرى',
        },
      ]);
    });

    it('falls back to the English name when Arabic is unavailable', () => {
      const departments:
        readonly CorrespondenceDepartment[] = [
          {
            code: '117',
            nameEnglish:
              'Digital Transformation Department',
            nameArabic: null,
          },
        ];

      const recipients =
        toPresidentReviewRecipients(
          departments,
        );

      expect(
        recipients[0],
      ).toEqual({
        id: '117',
        nameEn:
          'Digital Transformation Department',
        nameAr:
          'Digital Transformation Department',
      });
    });
  },
);