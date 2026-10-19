import {
  HttpClient,
  HttpHeaders,
} from '@angular/common/http';

import {
  inject,
  Injectable,
} from '@angular/core';

import {
  Observable,
} from 'rxjs';

const CORRESPONDENCE_URL =
  '/alfresco/service/badea/correspondence';

const OFFICE_COMPLETION_URL =
  '/alfresco/service/badea/office-completion';

export interface CreateCorrespondenceDraft {
  readonly subject: string;
  readonly bodyText: string | null;
  readonly documentNumber: string | null;
  readonly documentDate: string | null;
  readonly sourceText: string | null;
}

export interface CreatedCorrespondenceDraft {
  readonly technicalId: string;
  readonly state: 'DRAFT';
}

export interface CorrespondenceDocumentUpload {
  readonly nodeId: string;
  readonly filename: string;
  readonly mimeType: string;
  readonly sizeBytes: number;
}
export interface CorrespondenceDetails extends CreateCorrespondenceDraft {
  readonly technicalId: string;
  readonly state:
  | 'DRAFT'
  | 'WITH_PRESIDENT_OFFICE'
  | 'WITH_PRESIDENT'
  | 'WITH_DIRECTOR'
  | 'ARCHIVED';
  readonly originDepartmentCode: string;
  readonly createdByNameEnglish: string;
  readonly createdByNameArabic: string | null;
  readonly documents: readonly CorrespondenceDocumentUpload[];
}
export interface CorrespondenceDraftSummary {
  readonly technicalId: string;
  readonly subject: string;
  readonly documentNumber: string | null;
  readonly createdAt: string;
  readonly modifiedAt: string;
}

export interface CorrespondenceHistoryEntry {
  readonly sequence: number;
  readonly actionType: string;
  readonly actorRole: string;
  readonly actorNameEnglish: string;
  readonly actorNameArabic: string | null;
  readonly fromState: CorrespondenceDetails['state'];
  readonly toState: CorrespondenceDetails['state'];
  readonly actedAt: string;
  readonly explanation: string | null;
}

export interface CorrespondenceHistoryPage {
  readonly items: readonly CorrespondenceHistoryEntry[];
  readonly total: number;
}

export type CorrespondenceMailboxBox = 'inbox' | 'sent';

export interface CorrespondenceMailboxSummary extends CorrespondenceDraftSummary {
  readonly state: CorrespondenceDetails['state'];
  readonly originDepartmentCode: string;
  readonly senderName: string;
}

export interface CorrespondenceMailboxPage {
  readonly items: readonly CorrespondenceMailboxSummary[];
  readonly offset: number;
  readonly limit: number;
  readonly total: number;
}

export interface CorrespondenceDraftPage {
  readonly items: readonly CorrespondenceDraftSummary[];
  readonly offset: number;
  readonly limit: number;
  readonly total: number;
}
export type CorrespondenceTransitionAction =
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

/** Office-only supplemental layer. These selections are additions, not replacements. */
export interface OfficeCompletionDraft {
  readonly schemaVersion: 1;
  readonly selectedRecipientIds: readonly string[];
  readonly selectedCopyRecipientIds: readonly string[];
  readonly selectedActionCodes: readonly string[];
  readonly otherRecipient: string;
  readonly otherAction: string;
  readonly typedExplanation: string;
  readonly officeNotes: string;
}
export interface OfficeCompletionSnapshot {
  readonly technicalId: string;
  readonly revision: number;
  readonly presidentReviewRevision: number;
  readonly state: OfficeCompletionDraft | null;
  readonly updatedByEmployeeId: string | null;
  readonly updatedByNameEnglish: string | null;
  readonly updatedAt: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class CorrespondenceApiService {
  private readonly http =
    inject(HttpClient);

  createDraft(
    request: CreateCorrespondenceDraft,
  ): Observable<CreatedCorrespondenceDraft> {
    return this.http.post<CreatedCorrespondenceDraft>(
      CORRESPONDENCE_URL,
      request,
    );
  }
  getOfficeCompletion(technicalId: string): Observable<OfficeCompletionSnapshot> {
    return this.http.get<OfficeCompletionSnapshot>(
      `${OFFICE_COMPLETION_URL}/${encodeURIComponent(technicalId)}`,
    );
  }

  saveOfficeCompletion(technicalId: string, expectedRevision: number,
      expectedPresidentReviewRevision: number, state: OfficeCompletionDraft):
      Observable<OfficeCompletionSnapshot> {
    return this.http.put<OfficeCompletionSnapshot>(
      `${OFFICE_COMPLETION_URL}/${encodeURIComponent(technicalId)}`,
      { expectedRevision, expectedPresidentReviewRevision, state },
    );
  }

  getHistory(technicalId: string): Observable<CorrespondenceHistoryPage> {
    return this.http.get<CorrespondenceHistoryPage>(
      `${CORRESPONDENCE_URL}/${encodeURIComponent(technicalId)}/history`,
    );
  }

  getDetails(
    technicalId: string,
  ): Observable<CorrespondenceDetails> {
    return this.http.get<CorrespondenceDetails>(
      `${CORRESPONDENCE_URL}/${encodeURIComponent(technicalId)}`,
    );
  }
  listMailbox(
    box: CorrespondenceMailboxBox,
    offset = 0,
    limit = 25,
  ): Observable<CorrespondenceMailboxPage> {
    return this.http.get<CorrespondenceMailboxPage>(
      CORRESPONDENCE_URL,
      { params: { box, offset, limit } },
    );
  }

  listDrafts(
    offset = 0,
    limit = 25,
  ): Observable<CorrespondenceDraftPage> {
    return this.http.get<CorrespondenceDraftPage>(
      CORRESPONDENCE_URL,
      {
        params: {
          box: 'drafts',
          offset,
          limit,
        },
      },
    );
  }
  updateDraft(
    technicalId: string,
    request: CreateCorrespondenceDraft,
  ): Observable<CreatedCorrespondenceDraft> {
    return this.http.put<CreatedCorrespondenceDraft>(
      `${CORRESPONDENCE_URL}/${encodeURIComponent(technicalId)}/draft`,
      request,
    );
  }

  transitionCorrespondence(
    technicalId: string,
    request: CorrespondenceTransitionRequest,
  ): Observable<CorrespondenceTransitionResult> {
    return this.http.post<CorrespondenceTransitionResult>(
      `${CORRESPONDENCE_URL}/${encodeURIComponent(technicalId)}/transition`,
      request,
    );
  }

  /** Fetches an authorized PDF through the Alfresco ticket interceptor. */
  getDocumentContent(
    technicalId: string,
    documentNodeId: string,
  ): Observable<Blob> {
    return this.http.get(
      `${CORRESPONDENCE_URL}/${encodeURIComponent(technicalId)}` +
      `/documents/${encodeURIComponent(documentNodeId)}/content`,
      { responseType: 'blob' },
    );
  }

  uploadDocument(
    technicalId: string,
    file: File,
  ): Observable<CorrespondenceDocumentUpload> {
    const encodedId =
      encodeURIComponent(technicalId);

    const encodedFilename =
      encodeURIComponent(file.name);

    const url =
      `${CORRESPONDENCE_URL}/${encodedId}` +
      `/documents?filename=${encodedFilename}`;

    return this.http.post<CorrespondenceDocumentUpload>(
      url,
      file,
      {
        headers: new HttpHeaders({
          'Content-Type': 'application/pdf',
        }),
      },
    );
  }
  deleteDocument(
    technicalId: string,
    nodeId: string,
  ): Observable<void> {
    return this.http.delete<void>(
      `${CORRESPONDENCE_URL}/${encodeURIComponent(technicalId)}` +
      `/documents/${encodeURIComponent(nodeId)}`,
    );
  }
}