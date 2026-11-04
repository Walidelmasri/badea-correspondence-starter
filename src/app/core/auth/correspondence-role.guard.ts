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
/** Secretary-only and Admin+Secretary accounts use the standard mailbox. */
export const secretaryDashboardGuard: CanActivateFn = () => {
  const session = inject(AuthSessionStore);
  // An active secretary assignment is required before any secretary mailbox read.
  // Do not send an unassigned secretary into a removed/unauthorized workflow.
  if (session.secretaryMailbox()) return true;
  if (session.currentUser()?.employeeProfile?.correspondenceRole === 'SECRETARY') {
    return inject(Router).createUrlTree(['/access-denied']);
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

/** Secretary compose uses the normal Director form but never Director write APIs. */
export const secretaryComposeGuard: CanActivateFn = () => {
  const session = inject(AuthSessionStore);
  return session.currentUser()?.employeeProfile?.secretaryUnits?.length
    ? true : inject(Router).createUrlTree(['/inbox']);
};

/** Drafts uses the same page with an owner-scoped secretary API. */
export const secretaryDraftsGuard: CanActivateFn = () => true;

/** Personal secretary Sent now has its own creator-scoped list/detail/PDF APIs. */
export const standardMailboxGuard: CanActivateFn = () => true;
