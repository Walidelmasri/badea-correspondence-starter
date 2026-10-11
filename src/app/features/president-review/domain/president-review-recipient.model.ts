import {
  CorrespondenceDepartment,
} from '../../../core/correspondence/correspondence.model';

export interface PresidentReviewRecipientOption {
  readonly id: string;
  readonly nameAr: string;
  readonly nameEn: string;
}

const PRESIDENT_OFFICE_DEPARTMENT_CODE =
  '101';

const OTHER_RECIPIENT:
  PresidentReviewRecipientOption = {
    id: 'other',
    nameAr: 'أخرى',
    nameEn: 'Other',
  };

export function toPresidentReviewRecipients(
  departments:
    readonly CorrespondenceDepartment[],
): readonly PresidentReviewRecipientOption[] {
  const departmentRecipients =
    departments
      .filter(
        (department) =>
          department.code !==
          PRESIDENT_OFFICE_DEPARTMENT_CODE,
      )
      .map(
        (
          department,
        ): PresidentReviewRecipientOption => ({
          id: department.code,

          nameAr:
            department.nameArabic?.trim() ||
            department.nameEnglish,

          nameEn:
            department.nameEnglish,
        }),
      );

  return [
    ...departmentRecipients,
    OTHER_RECIPIENT,
  ];
}