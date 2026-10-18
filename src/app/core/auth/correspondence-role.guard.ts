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

  const role =
    session.currentUser()
      ?.employeeProfile
      ?.correspondenceRole;

  switch (role) {
    case 'DIRECTOR':
      return router.createUrlTree(['/director/new']);

    case 'PRESIDENT_OFFICE':
    case 'PRESIDENT':
      // Both use the current executive Compose layout.
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

  const role =
    session.currentUser()
      ?.employeeProfile
      ?.correspondenceRole;

  if (role === 'DIRECTOR') {
    return true;
  }

  if (
    role === 'PRESIDENT' ||
    role === 'PRESIDENT_OFFICE'
  ) {
    return router.createUrlTree(['/compose']);
  }

  return router.createUrlTree(['/inbox']);
};