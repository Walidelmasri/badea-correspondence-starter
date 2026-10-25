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

export interface ExecutiveComposeDraftFields {
  readonly recipient: string;
  readonly copyTo: string;
  readonly entity: string;
  readonly instruction: string;
  readonly explanation: string;
}

/** Executive fields are persisted together. The Director API remains unchanged. */
export interface ExecutiveCorrespondenceDraft extends CreateCorrespondenceDraft {
  readonly executiveCompose: ExecutiveComposeDraftFields;
}

export interface CreatedCorrespondenceDraft {
  readonly technicalId: string;
  readonly state: 'DRAFT';
}

export interface DepartmentAssignment {
  readonly assignmentId: string;
  readonly departmentCode: string;
  readonly recipientType: 'TO' | 'COPY';
  readonly status: 'PENDING' | 'COMPLETED';
  readonly assignedAt: string;
  readonly responseText: string | null;
  readonly completedAt: string | null;
}
export interface DepartmentAssignmentPage {
  readonly technicalId: string;
  readonly total: number;
  readonly items: readonly DepartmentAssignment[];
}

export interface CorrespondenceDocumentUpload {
  readonly nodeId: string;
  readonly filename: string;
  readonly mimeType: string;
  readonly sizeBytes: number;
}
export interface CorrespondenceGeneratedDocument extends CorrespondenceDocumentUpload {
  readonly artifactType: 'ANNOTATED_PDF' | 'HANDWRITTEN_EXPLANATION' | 'OFFICIAL_ACTION_SHEET' | 'CORRESPONDENCE_PACKET';
  readonly sourceDocumentNodeId: string | null;
  readonly reviewRevision: number;
  /** Null for President artifacts and older Office PDFs. */
  readonly officeRevision?: number | null;
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
  readonly generatedDocuments?: readonly CorrespondenceGeneratedDocument[];
  readonly executiveCompose?: ExecutiveComposeDraftFields;
}
export interface CorrespondenceDraftSummary {
  readonly state?: CorrespondenceDetails['state'];
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
export type CorrespondenceInboxView = 'all' | 'needs_action' | 'handled' | 'archived';

export interface CorrespondenceMailboxSummary extends CorrespondenceDraftSummary {
  readonly state: CorrespondenceDetails['state'];
  readonly originDepartmentCode: string;
  readonly senderName: string;
  readonly needsAction: boolean;
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


/** Native Alfresco public link; anyone with this link and server access can open it. */
export interface CorrespondenceShareLink {
  readonly sharedId: string;
  readonly url: string;
  readonly filename: string;
}
export interface ExistingCorrespondenceShareLink {
  readonly available: boolean;
  readonly sharedId?: string;
  readonly url?: string;
  readonly filename?: string;
}
export interface BatchShareEmailResult {
  readonly status: 'submitted';
  readonly recipient: string;
  readonly recipientName: string;
  readonly links: readonly (CorrespondenceShareLink & { readonly nodeId: string })[];
}
export interface ShareEmployeeResult {
  readonly username: string;
  readonly nameEnglish: string;
  readonly nameArabic: string | null;
}
export interface CorrespondenceShareEmailResult {
  readonly status: 'submitted';
  readonly recipient: string;
  readonly recipientName: string;
  readonly sharedId: string;
  readonly url: string;
}

@Injectable({
  providedIn: 'root',
})
export class CorrespondenceApiService {
  private readonly http =
    inject(HttpClient);


  searchShareRecipients(query: string): Observable<{ readonly employees: readonly ShareEmployeeResult[] }> {
    return this.http.get<{ readonly employees: readonly ShareEmployeeResult[] }>(
      `/alfresco/service/badea/correspondence-share/recipients?q=${encodeURIComponent(query)}`,
    );
  }

  existingShareLink(technicalId: string, nodeId: string): Observable<ExistingCorrespondenceShareLink> {
    return this.http.get<ExistingCorrespondenceShareLink>(
      `/alfresco/service/badea/correspondence-share/${encodeURIComponent(technicalId)}/${encodeURIComponent(nodeId)}/existing`,
    );
  }

  createShareLink(technicalId: string, nodeId: string): Observable<CorrespondenceShareLink> {
    return this.http.post<CorrespondenceShareLink>(
      `/alfresco/service/badea/correspondence-share/${encodeURIComponent(technicalId)}/${encodeURIComponent(nodeId)}/link`,
      {},
    );
  }

  sendShareBatchEmail(technicalId: string, nodeIds: readonly string[], recipientUsername: string,
      message: string): Observable<BatchShareEmailResult> {
    return this.http.post<BatchShareEmailResult>(
      `/alfresco/service/badea/correspondence-share/${encodeURIComponent(technicalId)}/email-batch`,
      { nodeIds, recipientUsername, message },
    );
  }

  sendShareEmail(technicalId: string, nodeId: string, recipientUsername: string,
      message: string): Observable<CorrespondenceShareEmailResult> {
    return this.http.post<CorrespondenceShareEmailResult>(
      `/alfresco/service/badea/correspondence-share/${encodeURIComponent(technicalId)}/${encodeURIComponent(nodeId)}/email`,
      { recipientUsername, message },
    );
  }

  revokeShareLink(technicalId: string, nodeId: string): Observable<{status: 'revoked'}> {
    return this.http.post<{status: 'revoked'}>(
      `/alfresco/service/badea/correspondence-share/${encodeURIComponent(technicalId)}/${encodeURIComponent(nodeId)}/revoke`,
      {},
    );
  }

  createDraft(
    request: CreateCorrespondenceDraft | ExecutiveCorrespondenceDraft,
  ): Observable<CreatedCorrespondenceDraft> {
    return this.http.post<CreatedCorrespondenceDraft>(
      CORRESPONDENCE_URL,
      request,
    );
  }
  dispatchOffice(technicalId: string, expectedPresidentReviewRevision: number,
      expectedOfficeRevision: number): Observable<{technicalId: string; dispatchId: string; state: 'WITH_DIRECTOR'}> {
    return this.http.post<{technicalId: string; dispatchId: string; state: 'WITH_DIRECTOR'}>(
      `/alfresco/service/badea/office-dispatch/${encodeURIComponent(technicalId)}`,
      { expectedPresidentReviewRevision, expectedOfficeRevision },
    );
  }

  /** Office-only, transactional transition to a new workflow cycle. */
  continueOfficeCycle(technicalId: string): Observable<{
    readonly technicalId: string;
    readonly state: 'WITH_PRESIDENT_OFFICE';
    readonly currentWorkflowCycle: number;
  }> {
    return this.http.post<{
      readonly technicalId: string;
      readonly state: 'WITH_PRESIDENT_OFFICE';
      readonly currentWorkflowCycle: number;
    }>(
      `/alfresco/service/badea/office-continue/${encodeURIComponent(technicalId)}`,
      {},
    );
  }

  getDepartmentAssignments(technicalId: string): Observable<DepartmentAssignmentPage> {
    return this.http.get<DepartmentAssignmentPage>(
      `/alfresco/service/badea/department-assignments/${encodeURIComponent(technicalId)}`,
    );
  }

  completeDepartmentAssignment(technicalId: string, assignmentId: string, responseText: string):
      Observable<DepartmentAssignmentPage> {
    return this.http.post<DepartmentAssignmentPage>(
      `/alfresco/service/badea/department-assignments/${encodeURIComponent(technicalId)}/${encodeURIComponent(assignmentId)}/complete`,
      { responseText },
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
    view: CorrespondenceInboxView = 'all',
  ): Observable<CorrespondenceMailboxPage> {
    return this.http.get<CorrespondenceMailboxPage>(
      CORRESPONDENCE_URL,
      { params: { box, offset, limit, view } },
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
    request: CreateCorrespondenceDraft | ExecutiveCorrespondenceDraft,
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

  /** Audited cycle advancement; role and department come from the server session. */
  continueDirectorCycle(
    technicalId: string,
    reason: string,
  ): Observable<{ technicalId: string; state: 'WITH_PRESIDENT_OFFICE'; currentWorkflowCycle: number }> {
    return this.http.post<{ technicalId: string; state: 'WITH_PRESIDENT_OFFICE'; currentWorkflowCycle: number }>(
      `/alfresco/service/badea/director-continue/${encodeURIComponent(technicalId)}`,
      { reason },
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