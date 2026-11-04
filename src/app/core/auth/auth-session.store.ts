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
  readonly activeWorkspace = computed(() => {
    const profile = this.currentUserState()?.employeeProfile;
    const code = this.workspaceUnitState();
    return profile?.managerWorkspaces?.find(w => w.code === code) ?? null;
  });
  // Only active Oracle manager appointments grant Manager Approvals or Team Access.
  readonly activeManagerWorkspace = computed(() => this.currentUserState()
    ?.employeeProfile?.managerWorkspaces?.find(w => w.code === this.workspaceUnitState()) ?? null);
  readonly availableWorkspaces = computed(() => {
    const profile = this.currentUserState()?.employeeProfile;
    return profile?.managerWorkspaces ?? [];
  });
  readonly showWorkspacePicker = computed(() => this.availableWorkspaces().length > 1
    || this.availableWorkspaces().some(w => w.role === 'DIRECTOR'));
  readonly effectiveRole = computed(() => this.activeWorkspace()?.role
    ?? this.currentUserState()?.employeeProfile?.correspondenceRole ?? null);
  readonly effectiveDepartmentCode = computed(() => this.activeWorkspace()?.code
    ?? this.currentUserState()?.employeeProfile?.department?.code ?? null);

  // Secretary is an additional Oracle capacity, independent of Tarasol Admin.
  // Never treat secretary group membership as a Director/Office appointment.
  private readonly secretaryUnitState = signal<string | null>(null);
  readonly secretaryMailbox = computed(() => {
    const role = this.effectiveRole();
    return (role === 'SECRETARY' || role === 'TARASOL_ADMIN')
      && !!this.currentUserState()?.employeeProfile?.secretaryUnits?.length;
  });
  readonly availableSecretaryUnits = computed(() => this.secretaryMailbox()
    ? (this.currentUserState()?.employeeProfile?.secretaryUnits ?? []) : []);
  readonly secretaryInboxUnitCode = computed(() => {
    if (!this.secretaryMailbox()) return null;
    const units = this.availableSecretaryUnits();
    const chosen = this.secretaryUnitState();
    return units.some(unit => unit.code === chosen) ? chosen : (units[0]?.code ?? null);
  });

  selectSecretaryUnit(code: string): void {
    if (!this.availableSecretaryUnits().some(unit => unit.code === code)) {
      throw new Error('Secretary unit not present in authenticated profile');
    }
    this.secretaryUnitState.set(code);
  }

  selectWorkspace(code: string | null): void {
    if (code && !this.availableWorkspaces()
      .some(workspace => workspace.code === code)) {
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
        || !(user.employeeProfile?.secretaryUnits ?? [])
          .some(unit => unit.code === this.secretaryUnitState())) {
      this.secretaryUnitState.set(null);
    }
    if (this.currentUserState()?.employeeProfile?.employeeId !== user.employeeProfile?.employeeId
      || !(user.employeeProfile?.managerWorkspaces ?? [])
          .some(w => w.code === this.workspaceUnitState())) {
      this.workspaceUnitState.set(null);
    }
    this.currentUserState.set(user);
    // A Director-only login must have a real manager unit selected before
    // the first mailbox request; Office users retain their shared Office default.
    if (this.workspaceUnitState() === null
        && user.employeeProfile?.correspondenceRole === 'DIRECTOR'
        && user.employeeProfile.managerWorkspaces?.length) {
      this.workspaceUnitState.set(user.employeeProfile.managerWorkspaces[0].code);
    }
  }

  clear(): void {
    this.ticketState.set(null);
    this.currentUserState.set(null);
    this.workspaceUnitState.set(null);
    this.secretaryUnitState.set(null);

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