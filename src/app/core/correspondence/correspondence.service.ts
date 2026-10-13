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
  badeaCorrespondencePresidentReviewUrl,
  badeaCorrespondenceTransitionUrl,
} from './correspondence.constants';

import {
  CorrespondenceDepartment,
  CorrespondenceTransitionRequest,
  CorrespondenceTransitionResponse,
  PresidentReviewSnapshot,
  SavePresidentReviewRequest,
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

  getPresidentReview<
    TState extends object,
  >(
    technicalId: string,
  ): Observable<
    PresidentReviewSnapshot<TState>
  > {
    return this.http.get<
      PresidentReviewSnapshot<TState>
    >(
      badeaCorrespondencePresidentReviewUrl(
        technicalId,
      ),
    );
  }

  savePresidentReview<
    TState extends object,
  >(
    technicalId: string,
    request:
      SavePresidentReviewRequest<TState>,
  ): Observable<
    PresidentReviewSnapshot<TState>
  > {
    return this.http.put<
      PresidentReviewSnapshot<TState>
    >(
      badeaCorrespondencePresidentReviewUrl(
        technicalId,
      ),
      request,
    );
  }
}