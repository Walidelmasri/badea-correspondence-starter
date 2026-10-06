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

export interface CurrentUser {
  readonly id: string;
  readonly firstName: string;
  readonly lastName?: string;
  readonly displayName: string;
  readonly email?: string;
  readonly enabled: boolean;
  readonly capabilities: CurrentUserCapabilities;
}