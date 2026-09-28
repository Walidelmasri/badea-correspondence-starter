export type NavigationKey =
  | 'inbox'
  | 'sent'
  | 'drafts'
  | 'completed';

export interface NavigationItem {
  readonly key: NavigationKey;
  readonly route: string;
}