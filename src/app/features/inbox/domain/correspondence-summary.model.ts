export type CorrespondencePriority =
  | 'normal'
  | 'urgent';

export type CorrespondenceStatus =
  | 'unread'
  | 'read'
  | 'completed';

export interface CorrespondenceSummary {
  readonly id: string;
  readonly referenceNumber: string;

  readonly senderName: string;
  readonly senderDepartment: string;

  readonly subject: string;
  readonly instruction: string;

  readonly receivedAt: Date;

  readonly attachmentCount: number;

  readonly priority: CorrespondencePriority;
  readonly status: CorrespondenceStatus;
}