import { inject } from '@angular/core';

import {
  CanActivateFn,
  Router,
} from '@angular/router';

import {
  AuthSessionStore,
} from './auth-session.store';

/**
 * Single entry point for New Correspondence.
 *
 * The backend supplies the role from Alfresco groups.
 * This guard controls presentation only; server-side
 * authorization remains mandatory.
 */
export const composeRoleGuard: CanActivateFn = () => {
  const session = inject(AuthSessionStore);
  const router = inject(Router);

  const role = session.effectiveRole?.() ?? session.currentUser()?.employeeProfile?.correspondenceRole;

  switch (role) {
    case 'DIRECTOR':
      return router.createUrlTree(['/director/new']);

    case 'PRESIDENT_OFFICE':
      return router.createUrlTree(['/office/new']);

    case 'PRESIDENT':
      return true;

    default:
      // No role, ambiguous role, or old backend response.
      // Never infer a role from the department.
      return router.createUrlTree(['/inbox']);
  }
};

/**
 * Prevent other roles from opening the Director form.
 *
 * Alfresco still independently rejects unauthorized writes.
 */
export const directorRoleGuard: CanActivateFn = () => {
  const session = inject(AuthSessionStore);
  const router = inject(Router);

  const role = session.effectiveRole?.() ?? session.currentUser()?.employeeProfile?.correspondenceRole;

  if (role === 'DIRECTOR') {
    return true;
  }

  if (role === 'PRESIDENT_OFFICE') {
    return router.createUrlTree(['/office/new']);
  }
  if (role === 'PRESIDENT') {
    return router.createUrlTree(['/compose']);
  }

  return router.createUrlTree(['/inbox']);
};
/** A secretary has only the dedicated personal-draft/approval workspace. */
export const secretaryDashboardGuard: CanActivateFn = () => {
  const session = inject(AuthSessionStore);
  // A secretary with an active unit uses the existing Dashboard/Inbox.
  // Keep the legacy draft workspace for users without a resolved unit.
  if (session.secretaryMailbox()) return true;
  if (session.currentUser()?.employeeProfile?.correspondenceRole === 'SECRETARY') {
    return inject(Router).createUrlTree(['/tarasol/secretary']);
  }
  if (session.currentUser()?.employeeProfile?.correspondenceRole === 'TARASOL_DELEGATE') {
    return inject(Router).createUrlTree(['/tarasol/delegated']);
  }
  if (session.currentUser()?.employeeProfile?.correspondenceRole === 'TARASOL_ADMIN') {
    return inject(Router).createUrlTree(['/tarasol/admin/access']);
  }
  return true;
};

/** The Office's intake editor is never a President or Director form. */
export const officeRoleGuard: CanActivateFn = () => {
  const session = inject(AuthSessionStore);
  const router = inject(Router);
  const role = session.effectiveRole?.()
    ?? session.currentUser()?.employeeProfile?.correspondenceRole;
  if (role === 'PRESIDENT_OFFICE') return true;
  if (role === 'PRESIDENT') return router.createUrlTree(['/compose']);
  if (role === 'DIRECTOR') return router.createUrlTree(['/director/new']);
  return router.createUrlTree(['/inbox']);
};

/** Personal Sent/Drafts retain the legacy secretary workflow until their
 * data adapters are implemented. Never call Director endpoints as secretary. */
export const standardMailboxGuard: CanActivateFn = () => {
  const session = inject(AuthSessionStore);
  return session.secretaryMailbox() ? inject(Router).createUrlTree(['/inbox']) : true;
};
