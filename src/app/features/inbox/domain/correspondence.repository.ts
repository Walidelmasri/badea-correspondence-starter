import { Observable } from 'rxjs';

import { CorrespondenceSummary } from './correspondence-summary.model';

export abstract class CorrespondenceRepository {
  abstract getInbox(): Observable<
    readonly CorrespondenceSummary[]
  >;
}