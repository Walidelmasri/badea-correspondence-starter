import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom, interval } from 'rxjs';
import { AuthSessionStore } from '../auth/auth-session.store';
import { CorrespondenceApiService, CorrespondenceMailboxSummary } from '../../shared/data-access/correspondence-api.service';

/** Notification panel is a projection of authorized unread inbox deliveries. */
@Injectable({ providedIn: 'root' })
export class UnreadInboxStore {
  private readonly api = inject(CorrespondenceApiService);
  private readonly session = inject(AuthSessionStore);
  private readonly itemsState = signal<readonly CorrespondenceMailboxSummary[]>([]);
  private readonly totalState = signal(0);
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
      this.invalidateForWorkspaceChange();
      if (employeeId && this.session.isAuthenticated()) {
        void this.refresh();
      }
    });
    // Modest polling for new deliveries; no notification transport or push server.
    interval(90_000).pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.session.isAuthenticated() &&
          (typeof document === 'undefined' || document.visibilityState === 'visible')) {
        void this.refresh();
      }
    });
  }

  async refresh(): Promise<void> {
    if (!this.session.isAuthenticated()) return;
    const employeeId = this.session.currentUser()?.employeeProfile?.employeeId;
    if (!employeeId) return;
    const workspaceUnit = this.session.workspaceUnit();
    const version = ++this.version;
    try {
      const result = await firstValueFrom(this.api.listMailbox('inbox', 0, 5, 'unread'));
      if (version !== this.version
          || this.session.currentUser()?.employeeProfile?.employeeId !== employeeId
          || this.session.workspaceUnit() !== workspaceUnit) return;
      this.itemsState.set(result.items);
      this.totalState.set(result.total);
      this.failed.set(false);
    } catch {
      if (version === this.version && this.session.workspaceUnit() === workspaceUnit) {
        this.failed.set(true);
        // Do not display stale notification counts as if they were current.
        this.itemsState.set([]);
        this.totalState.set(0);
      }
    }
  }

  // Also called synchronously from the selector to prevent one frame of
  // stale Office/Director subjects being shown during a workspace switch.
  invalidateForWorkspaceChange(): void {
    ++this.version;
    this.itemsState.set([]);
    this.totalState.set(0);
    this.failed.set(false);
    this.open.set(false);
  }

  toggle(): void {
    this.open.update(value => !value);
    if (this.open()) void this.refresh();
  }
  close(): void { this.open.set(false); }
}
