import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type AccessMode = 'admin' | 'team';
export interface UnitAppointment {
  readonly id: number;
  readonly unitCode: string;
  readonly employeeId: string;
  readonly capacity: 'MANAGER' | 'ACTING_MANAGER' | 'OFFICE_MEMBER' | 'CONSULTANT';
  readonly startsAt: string;
  readonly endsAt: string | null;
  readonly revokedAt: string | null;
  readonly active: boolean;
}
export interface TeamAssignment {
  readonly id: number;
  readonly managerAssignmentId: number;
  readonly employeeId: string;
  readonly type: string | null;
  readonly caseTechnicalId: string | null;
  readonly startsAt: string;
  readonly endsAt: string | null;
  readonly revokedAt: string | null;
  readonly active: boolean;
}
export interface AccessSnapshot {
  readonly appointments: readonly UnitAppointment[];
  readonly secretaries: readonly TeamAssignment[];
  readonly delegations: readonly TeamAssignment[];
}
export interface AccessChange {
  readonly action: string;
  readonly unitCode: string;
  readonly managerAssignmentId?: number;
  readonly assignmentId?: number;
  readonly recordId?: number;
  readonly employeeId?: string;
  readonly capacity?: string;
  readonly startsAt?: string;
  readonly endsAt?: string | null;
  readonly delegationType?: string;
  readonly caseTechnicalId?: string;
  readonly reason?: string;
  readonly appointmentRef?: string;
}

@Injectable({ providedIn: 'root' })
export class TarasolAccessApiService {
  private readonly http = inject(HttpClient);
  private url(mode: AccessMode): string {
    return `/alfresco/service/badea/tarasol/${mode}/access`;
  }
  read(mode: AccessMode, unitCode: string): Observable<AccessSnapshot> {
    return this.http.get<AccessSnapshot>(this.url(mode), { params: { unitCode } });
  }
  change(mode: AccessMode, payload: AccessChange): Observable<{ readonly id: number; readonly ok: boolean }> {
    return this.http.post<{ readonly id: number; readonly ok: boolean }>(this.url(mode), payload);
  }
}
