import { AuthSessionStore } from './auth-session.store';

/** An active Oracle appointment is required; a role label alone is insufficient. */
export function managerApprovalUnit(session: AuthSessionStore): string | null {
  const selected = session.activeManagerWorkspace();
  if (selected) return selected.code;
  const workspaces = session.availableWorkspaces();
  if (workspaces.length === 1) return workspaces[0].code;
  const departmentCode = session.effectiveDepartmentCode();
  return workspaces.find(workspace => workspace.code === departmentCode)?.code ?? null;
}
