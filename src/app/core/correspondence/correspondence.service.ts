import {
  HttpClient,
} from '@angular/common/http';

import {
  inject,
  Injectable,
} from '@angular/core';

import {
  Observable,
} from 'rxjs';

import {
  BADEA_CORRESPONDENCE_DEPARTMENTS_URL,
  badeaCorrespondenceTransitionUrl,
} from './correspondence.constants';

import {
  CorrespondenceDepartment,
  CorrespondenceTransitionRequest,
  CorrespondenceTransitionResponse,
} from './correspondence.model';

@Injectable({
  providedIn: 'root',
})
export class CorrespondenceService {
  private readonly http =
    inject(HttpClient);

  getDepartments():
    Observable<
      readonly CorrespondenceDepartment[]
    > {
    return this.http.get<
      readonly CorrespondenceDepartment[]
    >(
      BADEA_CORRESPONDENCE_DEPARTMENTS_URL,
    );
  }

  transition(
    technicalId: string,
    request:
      CorrespondenceTransitionRequest,
  ): Observable<
    CorrespondenceTransitionResponse
  > {
    return this.http.post<
      CorrespondenceTransitionResponse
    >(
      badeaCorrespondenceTransitionUrl(
        technicalId,
      ),
      request,
    );
  }
}