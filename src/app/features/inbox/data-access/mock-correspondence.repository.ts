import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

import { CorrespondenceRepository } from '../domain/correspondence.repository';
import { CorrespondenceSummary } from '../domain/correspondence-summary.model';

@Injectable()
export class MockCorrespondenceRepository
  implements CorrespondenceRepository {

  private readonly correspondence: readonly CorrespondenceSummary[] = [
    {
      id: 'pc-2026-001',
      referenceNumber: 'PO/2026/001',
      senderName: 'مكتب الرئيس',
      senderDepartment: 'مكتب الرئيس',
      subject: 'مشروع تمويل برنامج تطوير البنية التحتية',
      instruction: 'للدراسة وإبداء الرأي',
      receivedAt: new Date('2026-09-27T10:42:00'),
      attachmentCount: 3,
      priority: 'urgent',
      status: 'unread',
    },
    {
      id: 'pc-2026-002',
      referenceNumber: 'PO/2026/002',
      senderName: 'مكتب الرئيس',
      senderDepartment: 'مكتب الرئيس',
      subject: 'متابعة تقرير الأداء المؤسسي',
      instruction: 'للمتابعة',
      receivedAt: new Date('2026-09-27T09:15:00'),
      attachmentCount: 1,
      priority: 'normal',
      status: 'read',
    },
    {
      id: 'pc-2026-003',
      referenceNumber: 'PO/2026/003',
      senderName: 'مكتب الرئيس',
      senderDepartment: 'مكتب الرئيس',
      subject: 'مراجعة مذكرة التعاون',
      instruction: 'للمراجعة والإفادة',
      receivedAt: new Date('2026-09-26T14:30:00'),
      attachmentCount: 2,
      priority: 'normal',
      status: 'read',
    },
  ];

  getInbox(): Observable<readonly CorrespondenceSummary[]> {
    return of(this.correspondence);
  }
}