import {
  computed,
  Injectable,
  signal,
} from '@angular/core';

import { CurrentUser } from './auth.model';

const SESSION_STORAGE_KEY =
  'badea-correspondence.alfresco-ticket';

@Injectable({
  providedIn: 'root',
})
export class AuthSessionStore {
  private readonly ticketState =
    signal<string | null>(
      this.readStoredTicket(),
    );

  private readonly currentUserState =
    signal<CurrentUser | null>(null);

  readonly ticket =
    this.ticketState.asReadonly();

  readonly currentUser =
    this.currentUserState.asReadonly();

  private readonly workspaceUnitState = signal<string | null>(null);
  readonly workspaceUnit = this.workspaceUnitState.asReadonly();
  readonly activeWorkspace = computed(() => this.currentUserState()
    ?.employeeProfile?.managerWorkspaces?.find(w => w.code === this.workspaceUnitState()) ?? null);
  readonly effectiveRole = computed(() => this.activeWorkspace()?.role
    ?? this.currentUserState()?.employeeProfile?.correspondenceRole ?? null);
  readonly effectiveDepartmentCode = computed(() => this.activeWorkspace()?.code
    ?? this.currentUserState()?.employeeProfile?.department?.code ?? null);

  selectWorkspace(code: string | null): void {
    if (code && !this.currentUserState()?.employeeProfile?.managerWorkspaces
      ?.some(workspace => workspace.code === code)) {
      throw new Error('Workspace not present in authenticated profile');
    }
    this.workspaceUnitState.set(code);
  }

  readonly isAuthenticated = computed(
    () =>
      this.ticketState() !== null &&
      this.currentUserState() !== null,
  );

  readonly initials = computed(() => {
    const user = this.currentUserState();

    if (!user) {
      return '';
    }

    const firstInitial =
      user.firstName
        .trim()
        .charAt(0);

    const lastInitial =
      user.lastName
        ?.trim()
        .charAt(0) ?? '';

    if (firstInitial || lastInitial) {
      return `${firstInitial}${lastInitial}`
        .toUpperCase();
    }

    return user.displayName
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part.charAt(0))
      .join('')
      .toUpperCase();
  });

  setTicket(ticket: string): void {
    this.ticketState.set(ticket);

    try {
      sessionStorage.setItem(
        SESSION_STORAGE_KEY,
        ticket,
      );
    } catch {
      // In-memory authentication still works
      // if sessionStorage is unavailable.
    }
  }

  setCurrentUser(
    user: CurrentUser,
  ): void {
    if (this.currentUserState()?.employeeProfile?.employeeId !== user.employeeProfile?.employeeId
      || !user.employeeProfile?.managerWorkspaces?.some(w => w.code === this.workspaceUnitState())) {
      this.workspaceUnitState.set(null);
    }
    this.currentUserState.set(user);
  }

  clear(): void {
    this.ticketState.set(null);
    this.currentUserState.set(null);
    this.workspaceUnitState.set(null);

    try {
      sessionStorage.removeItem(
        SESSION_STORAGE_KEY,
      );
    } catch {
      // Nothing else is required.
    }
  }

  private readStoredTicket(): string | null {
    try {
      return sessionStorage.getItem(
        SESSION_STORAGE_KEY,
      );
    } catch {
      return null;
    }
  }
}