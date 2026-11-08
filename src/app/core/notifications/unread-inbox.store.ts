import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom, interval } from 'rxjs';
import { AuthSessionStore } from '../auth/auth-session.store';
import { managerApprovalUnit } from '../auth/manager-approval-unit';
import { TarasolSecretaryApiService } from '../../features/tarasol-secretary/tarasol-secretary-api.service';
import { CorrespondenceApiService, CorrespondenceMailboxSummary } from '../../shared/data-access/correspondence-api.service';

export interface WorkflowActionNotification {
  readonly kind: 'returned' | 'manager_pending';
  readonly technicalId: string;
  readonly unitCode: string;
  readonly subject: string;
  readonly returnReason: string | null;
}

/** Authorized unread mail and live, actionable personal workflow queues. */
@Injectable({ providedIn: 'root' })
export class UnreadInboxStore {
  private readonly api = inject(CorrespondenceApiService);
  private readonly session = inject(AuthSessionStore);
  private readonly secretaryApi = inject(TarasolSecretaryApiService);
  private readonly itemsState = signal<readonly CorrespondenceMailboxSummary[]>([]);
  private readonly totalState = signal(0);
  private readonly actionItemsState = signal<readonly WorkflowActionNotification[]>([]);
  private readonly actionTotalState = signal(0);
  readonly actionItems = this.actionItemsState.asReadonly();
  readonly actionTotal = this.actionTotalState.asReadonly();
  readonly badgeTotal = computed(() => this.total() + this.actionTotal());
  readonly hasNotifications = computed(() => this.badgeTotal() > 0);
  readonly items = this.itemsState.asReadonly();
  readonly total = this.totalState.asReadonly();
  readonly open = signal(false);
  readonly failed = signal(false);
  readonly hasUnread = computed(() => this.total() > 0);
  private version = 0;

  constructor() {
    effect(() => {
      const employeeId = this.session.currentUser()?.employeeProfile?.employeeId ?? null;
      // Read the active unit here so switching capacity invalidates pending
      // notifications even when the signed-in employee has not changed.
      this.session.workspaceUnit();
      this.session.secretaryInboxUnitCode();
      this.session.effectiveRole();
      this.session.activeManagerWorkspace();
      this.session.availableWorkspaces();
      this.invalidateForWorkspaceChange();
      if (employeeId && this.session.isAuthenticated() && this.supportsMailbox()) {
        void this.refresh();
      }
    });
    // Modest polling for new deliveries; no notification transport or push server.
    interval(90_000).pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.session.isAuthenticated() && this.supportsMailbox() &&
          (typeof document === 'undefined' || document.visibilityState === 'visible')) {
        void this.refresh();
      }
    });
  }

  async refresh(): Promise<void> {
    if (!this.session.isAuthenticated() || !this.supportsMailbox()) return;
    const employeeId = this.session.currentUser()?.employeeProfile?.employeeId;
    if (!employeeId) return;
    const workspaceUnit = this.session.workspaceUnit();
    const secretaryUnit = this.session.secretaryMailbox()
      ? this.session.secretaryInboxUnitCode() : null;
    const managerUnit = managerApprovalUnit(this.session);
    const version = ++this.version;
    const isCurrent = () => version === this.version
      && this.session.currentUser()?.employeeProfile?.employeeId === employeeId
      && this.session.workspaceUnit() === workspaceUnit
      && (this.session.secretaryMailbox() ? this.session.secretaryInboxUnitCode() : null) === secretaryUnit
      && managerApprovalUnit(this.session) === managerUnit;

    // Each queue settles independently. A slow or failed approval endpoint must
    // never block ordinary unread mail or a returned secretary action.
    this.failed.set(false);
    let returnedActions: WorkflowActionNotification[] = [];
    let pendingActions: WorkflowActionNotification[] = [];
    let returnedCount = 0;
    let pendingCount = 0;
    const publishActions = () => {
      if (!isCurrent()) return;
      this.actionItemsState.set([...returnedActions, ...pendingActions]);
      this.actionTotalState.set(returnedCount + pendingCount);
    };
    const fail = () => {
      if (isCurrent()) this.failed.set(true);
    };

    const unreadTask = (async () => {
      if (this.session.secretaryMailbox() && !secretaryUnit) {
        if (isCurrent()) {
          this.itemsState.set([]);
          this.totalState.set(0);
        }
        return;
      }
      try {
        const page = await firstValueFrom(secretaryUnit
          ? this.api.listSecretaryInbox(secretaryUnit, 0, 5, 'unread')
          : this.api.listMailbox('inbox', 0, 5, 'unread'));
        if (!isCurrent()) return;
        this.itemsState.set(page.items ?? []);
        this.totalState.set(page.total ?? 0);
      } catch {
        if (!isCurrent()) return;
        this.itemsState.set([]);
        this.totalState.set(0);
        fail();
      }
    })();

    const returnedTask = (async () => {
      if (!secretaryUnit) {
        publishActions();
        return;
      }
      try {
        const page = await firstValueFrom(
          this.secretaryApi.list(secretaryUnit, 'mine', 0, 5, 'returned'));
        if (!isCurrent()) return;
        returnedActions = page.items.filter(row => row.approvalStatus === 'RETURNED'
          && row.unitCode === secretaryUnit && row.ownerEmployeeId === employeeId)
          .map(row => ({ kind: 'returned' as const, technicalId: row.technicalId,
            unitCode: row.unitCode, subject: row.subject, returnReason: row.returnReason ?? null }));
        returnedCount = page.total;
        publishActions();
      } catch {
        if (!isCurrent()) return;
        returnedActions = [];
        returnedCount = 0;
        publishActions();
        fail();
      }
    })();

    const pendingTask = (async () => {
      if (!managerUnit) {
        publishActions();
        return;
      }
      try {
        const page = await firstValueFrom(this.secretaryApi.list(managerUnit, 'pending', 0, 5));
        if (!isCurrent()) return;
        pendingActions = page.items.filter(row => row.approvalStatus === 'PENDING'
          && row.unitCode === managerUnit)
          .map(row => ({ kind: 'manager_pending' as const, technicalId: row.technicalId,
            unitCode: row.unitCode, subject: row.subject, returnReason: null }));
        pendingCount = page.total;
        publishActions();
      } catch {
        if (!isCurrent()) return;
        pendingActions = [];
        pendingCount = 0;
        publishActions();
        fail();
      }
    })();

    await Promise.all([unreadTask, returnedTask, pendingTask]);
  }

  readonly available = computed(() => {
    if (this.session.secretaryMailbox()) return !!this.session.secretaryInboxUnitCode() || !!managerApprovalUnit(this.session);
    const role = this.session.effectiveRole();
    return !!role && role !== 'SECRETARY' && role !== 'TARASOL_ADMIN'
      && role !== 'TARASOL_DELEGATE';
  });

  private supportsMailbox(): boolean { return this.available(); }

  // Also called synchronously from the selector to prevent one frame of
  // stale Office/Director subjects being shown during a workspace switch.
  invalidateForWorkspaceChange(): void {
    ++this.version;
    this.itemsState.set([]);
    this.totalState.set(0);
    this.actionItemsState.set([]);
    this.actionTotalState.set(0);
    this.failed.set(false);
    this.open.set(false);
  }

  toggle(): void {
    this.open.update(value => !value);
    if (this.open()) void this.refresh();
  }
  close(): void { this.open.set(false); }
}
