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
}