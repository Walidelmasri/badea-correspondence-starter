export interface LoginCredentials {
  readonly userId: string;
  readonly password: string;
}

export interface AlfrescoTicket {
  readonly id: string;
  readonly userId: string;
}

export interface AlfrescoEntryResponse<T> {
  readonly entry: T;
}

export interface CurrentUserCapabilities {
  readonly isGuest: boolean;
  readonly isAdmin: boolean;
  readonly isMutable: boolean;
}

export interface EmployeeDepartment {
  readonly code: string;
  readonly nameEnglish: string;
  readonly nameArabic: string | null;
}

export interface EmployeeProfile {
  readonly employeeId: string;
  readonly username: string;
  readonly nameEnglish: string;
  readonly nameArabic: string | null;
  readonly department: EmployeeDepartment | null;
}

export interface CurrentUser {
  readonly id: string;
  readonly firstName: string;
  readonly lastName?: string;
  readonly displayName: string;
  readonly email?: string;
  readonly enabled: boolean;
  readonly capabilities: CurrentUserCapabilities;

  /**
   * BADEA employee-directory information.
   *
   * This is optional because authentication is owned by Alfresco/AD.
   * An authenticated user may not have a matching active Oracle
   * employee record.
   */
  readonly employeeProfile?: EmployeeProfile;
}