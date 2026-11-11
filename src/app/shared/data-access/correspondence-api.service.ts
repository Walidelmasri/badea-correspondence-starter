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
  map,
} from 'rxjs';

const CORRESPONDENCE_URL =
  '/alfresco/service/badea/correspondence';

const OFFICE_COMPLETION_URL =
  '/alfresco/service/badea/office-completion';

const SECRETARY_INBOX_URL =
  '/alfresco/service/badea/tarasol/secretary/inbox';

const SECRETARY_SENT_URL =
  '/alfresco/service/badea/tarasol/secretary/sent';

const SECRETARY_WORKFLOW_URL =
  '/alfresco/service/badea/tarasol/secretary/workflow';

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

/** Executive fields are persisted together. Eligible dual managers may attach these to Director-originated drafts. */
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
  /** Null for pre-metadata source PDFs; generated output has no source round. */
  readonly workflowCycle?: number | null;
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
  readonly currentWorkflowCycle?: number;
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
  readonly approvalStatus?: 'DRAFT' | 'PENDING' | 'RETURNED' | 'APPROVED';
  readonly returnReason?: string | null;
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
export type CorrespondenceInboxView = 'all' | 'needs_action' | 'handled' | 'archived' | 'unread';

export interface CorrespondenceMailboxSummary extends CorrespondenceDraftSummary {
  readonly state: CorrespondenceDetails['state'];
  readonly originDepartmentCode: string;
  readonly senderName: string;
  readonly needsAction: boolean;
  readonly unread: boolean;
  readonly incomingSequence: number;
  /** Only secretary Sent uses this additional presentation status. */
  readonly approvalStatus?: 'PENDING' | 'APPROVED';
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
  readonly status: 'submitted' | 'partial_or_uncertain';
  readonly recipients: readonly {
    readonly recipient: string;
    readonly recipientName: string;
    readonly smtpAccepted: boolean;
  }[];
  readonly links: readonly (CorrespondenceShareLink & { readonly nodeId: string })[];
}
export interface ShareSecretaryScope {
  readonly unitCode: string;
  readonly box: 'inbox' | 'sent';
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

export interface PresidentSignatureStatus {
  readonly configured: boolean;
  readonly revision: number;
  readonly updatedAt: string | null;
  readonly previewDataUrl: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class CorrespondenceApiService {
  private readonly http =
    inject(HttpClient);


  getPresidentSignatureStatus(): Observable<PresidentSignatureStatus> {
    return this.http.get<PresidentSignatureStatus>(
      '/alfresco/service/badea/president-signature',
    );
  }

  registerPresidentSignature(payload: {
    readonly expectedRevision: number;
    readonly authorizeReuse: boolean;
    readonly drawing: { readonly schemaVersion: 1; readonly strokes: readonly (readonly (readonly number[])[])[] };
  }): Observable<PresidentSignatureStatus> {
    return this.http.put<PresidentSignatureStatus>(
      '/alfresco/service/badea/president-signature', payload,
    );
  }

  deactivatePresidentSignature(expectedRevision: number): Observable<PresidentSignatureStatus> {
    return this.http.put<PresidentSignatureStatus>(
      '/alfresco/service/badea/president-signature',
      {action: 'deactivate', expectedRevision},
    );
  }

  searchShareRecipients(query: string, technicalId: string,
      scope?: ShareSecretaryScope): Observable<{ readonly employees: readonly ShareEmployeeResult[] }> {
    return this.http.get<{ readonly employees: readonly ShareEmployeeResult[] }>(
      `/alfresco/service/badea/correspondence-share/recipients`,
      { params: { ...this.shareRequestParams(scope), q: query, technicalId } },
    );
  }

  private shareRequestParams(scope?: ShareSecretaryScope): Record<string, string> {
    return scope ? { shareSecretaryUnit: scope.unitCode, shareSecretaryBox: scope.box } : {};
  }

  existingShareLink(technicalId: string, nodeId: string,
      scope?: ShareSecretaryScope): Observable<ExistingCorrespondenceShareLink> {
    return this.http.get<ExistingCorrespondenceShareLink>(
      `/alfresco/service/badea/correspondence-share/${encodeURIComponent(technicalId)}/${encodeURIComponent(nodeId)}/existing`,
      { params: this.shareRequestParams(scope) },
    );
  }

  createShareLink(technicalId: string, nodeId: string,
      scope?: ShareSecretaryScope): Observable<CorrespondenceShareLink> {
    return this.http.post<CorrespondenceShareLink>(
      `/alfresco/service/badea/correspondence-share/${encodeURIComponent(technicalId)}/${encodeURIComponent(nodeId)}/link`,
      {}, { params: this.shareRequestParams(scope) },
    );
  }

  sendShareBatchEmail(technicalId: string, nodeIds: readonly string[],
      recipientUsernames: readonly string[], message: string,
      scope?: ShareSecretaryScope): Observable<BatchShareEmailResult> {
    return this.http.post<BatchShareEmailResult>(
      `/alfresco/service/badea/correspondence-share/${encodeURIComponent(technicalId)}/email-batch`,
      { nodeIds, recipientUsernames, message },
      { params: this.shareRequestParams(scope) },
    );
  }

  sendShareEmail(technicalId: string, nodeId: string, recipientUsername: string,
      message: string): Observable<CorrespondenceShareEmailResult> {
    return this.http.post<CorrespondenceShareEmailResult>(
      `/alfresco/service/badea/correspondence-share/${encodeURIComponent(technicalId)}/${encodeURIComponent(nodeId)}/email`,
      { recipientUsername, message },
    );
  }

  revokeShareLink(technicalId: string, nodeId: string,
      scope?: ShareSecretaryScope): Observable<{status: 'revoked'}> {
    return this.http.post<{status: 'revoked'}>(
      `/alfresco/service/badea/correspondence-share/${encodeURIComponent(technicalId)}/${encodeURIComponent(nodeId)}/revoke`,
      {}, { params: this.shareRequestParams(scope) },
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

  /** Shared unit Inbox reads are distinct from Director operations. Backend
   * rechecks the secretary Alfresco group, active Oracle unit and case scope. */
  listSecretaryInbox(unitCode: string, offset = 0, limit = 25,
      view: CorrespondenceInboxView = 'needs_action'): Observable<CorrespondenceMailboxPage> {
    return this.http.get<CorrespondenceMailboxPage>(SECRETARY_INBOX_URL,
      { params: { unitCode, offset, limit, view } });
  }

  /** Personal Sent includes submitted and approved/forwarded cases only.
   * The backend verifies the creator, secretary group and effective Oracle unit. */
  listSecretarySent(unitCode: string, offset = 0, limit = 25): Observable<CorrespondenceMailboxPage> {
    interface SecretarySentItem {
      readonly technicalId: string;
      readonly subject: string;
      readonly documentNumber: string | null;
      readonly state: CorrespondenceDetails['state'];
      readonly senderName: string | null;
      readonly ownerEmployeeId: string;
      readonly approvalStatus: 'PENDING' | 'APPROVED';
      readonly createdAt: string;
      readonly modifiedAt: string;
    }
    interface SecretarySentPage {
      readonly items: readonly SecretarySentItem[];
      readonly offset: number;
      readonly limit: number;
      readonly total: number;
    }
    return this.http.get<SecretarySentPage>(SECRETARY_WORKFLOW_URL, {
      params: { unitCode, view: 'mine', box: 'sent', offset, limit },
    }).pipe(map(page => ({
      ...page,
      items: page.items.map(item => ({
        technicalId: item.technicalId,
        subject: item.subject,
        documentNumber: item.documentNumber,
        createdAt: item.createdAt,
        modifiedAt: item.modifiedAt,
        state: item.state,
        originDepartmentCode: unitCode,
        senderName: item.senderName || item.ownerEmployeeId,
        needsAction: false,
        unread: false,
        incomingSequence: 0,
        approvalStatus: item.approvalStatus,
      })),
    })));
  }

  getSecretarySentDetails(unitCode: string, technicalId: string): Observable<CorrespondenceDetails> {
    return this.http.get<CorrespondenceDetails>(
      `${SECRETARY_SENT_URL}/${encodeURIComponent(technicalId)}`, { params: { unitCode } });
  }

  getSecretarySentHistory(unitCode: string, technicalId: string): Observable<CorrespondenceHistoryPage> {
    return this.http.get<CorrespondenceHistoryPage>(
      `${SECRETARY_SENT_URL}/${encodeURIComponent(technicalId)}/history`, { params: { unitCode } });
  }

  getSecretarySentDocument(unitCode: string, technicalId: string, nodeId: string): Observable<Blob> {
    return this.http.get(
      `${SECRETARY_SENT_URL}/${encodeURIComponent(technicalId)}`
        + `/documents/${encodeURIComponent(nodeId)}/content`,
      { params: { unitCode }, responseType: 'blob' });
  }

  /** Persist only this secretary's read sequence within their authorized shared Inbox. */
  markSecretaryInboxRead(unitCode: string, technicalId: string,
      throughSequence: number): Observable<{ readonly readThroughSequence: number }> {
    return this.http.post<{ readonly readThroughSequence: number }>(
      `${SECRETARY_INBOX_URL}/${encodeURIComponent(technicalId)}/read`,
      { throughSequence }, { params: { unitCode } });
  }

  getSecretaryInboxDetails(unitCode: string, technicalId: string): Observable<CorrespondenceDetails> {
    return this.http.get<CorrespondenceDetails>(
      `${SECRETARY_INBOX_URL}/${encodeURIComponent(technicalId)}`, { params: { unitCode } });
  }

  getSecretaryInboxHistory(unitCode: string, technicalId: string): Observable<CorrespondenceHistoryPage> {
    return this.http.get<CorrespondenceHistoryPage>(
      `${SECRETARY_INBOX_URL}/${encodeURIComponent(technicalId)}/history`, { params: { unitCode } });
  }

  getSecretaryInboxDocument(unitCode: string, technicalId: string, nodeId: string): Observable<Blob> {
    return this.http.get(
      `${SECRETARY_INBOX_URL}/${encodeURIComponent(technicalId)}`
        + `/documents/${encodeURIComponent(nodeId)}/content`,
      { params: { unitCode }, responseType: 'blob' });
  }

  /** Personal draft PDF: the backend validates secretary membership, current unit and creator. */
  getSecretaryPersonalDraftDocument(unitCode: string, technicalId: string, nodeId: string): Observable<Blob> {
    return this.http.get(
      `/alfresco/service/badea/tarasol/secretary/${encodeURIComponent(technicalId)}`
        + `/documents/${encodeURIComponent(nodeId)}`,
      { params: { unitCode, view: 'mine' }, responseType: 'blob' });
  }

  getHistory(technicalId: string): Observable<CorrespondenceHistoryPage> {
    return this.http.get<CorrespondenceHistoryPage>(
      `${CORRESPONDENCE_URL}/${encodeURIComponent(technicalId)}/history`,
    );
  }

  markRead(technicalId: string, throughSequence: number): Observable<{readonly readThroughSequence: number}> {
    return this.http.post<{readonly readThroughSequence: number}>(
      `${CORRESPONDENCE_URL}/${encodeURIComponent(technicalId)}/read`,
      { throughSequence },
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
    followUp = false,
  ): Observable<CorrespondenceDocumentUpload> {
    const encodedId =
      encodeURIComponent(technicalId);

    const encodedFilename =
      encodeURIComponent(file.name);

    const url =
      `${CORRESPONDENCE_URL}/${encodedId}` +
      `/documents?filename=${encodedFilename}${followUp ? '&followUp=true' : ''}`;

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