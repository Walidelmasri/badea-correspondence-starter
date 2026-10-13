export interface CorrespondenceDepartment {
  readonly code: string;
  readonly nameEnglish: string;
  readonly nameArabic: string | null;
}

export type CorrespondenceAction =
  | 'SEND_TO_OFFICE'
  | 'SEND_TO_PRESIDENT'
  | 'SEND_TO_DIRECTOR'
  | 'MARK_DONE'
  | 'ADMINISTRATIVE_CLOSE';

export type CorrespondenceState =
  | 'DRAFT'
  | 'WITH_PRESIDENT_OFFICE'
  | 'WITH_PRESIDENT'
  | 'WITH_DIRECTOR'
  | 'ARCHIVED';

export interface CorrespondenceTransitionRequest {
  readonly action:
    CorrespondenceAction;

  readonly targetDepartmentCode?:
    string;

  readonly explanation?:
    string;

  readonly closureReason?:
    string;
}

export interface CorrespondenceTransitionResponse {
  readonly technicalId:
    string;

  readonly state:
    CorrespondenceState;
}

export interface PresidentReviewSnapshot<
  TState extends object,
> {
  readonly technicalId:
    string;

  readonly revision:
    number;

  readonly state:
    TState | null;
}

export interface SavePresidentReviewRequest<
  TState extends object,
> {
  readonly expectedRevision:
    number;

  readonly state:
    TState;
}