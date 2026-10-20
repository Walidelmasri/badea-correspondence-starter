export type NavigationKey =
  | 'dashboard'
  | 'inbox'
  | 'sent'
  | 'drafts'
  | 'presidentReview';

export interface NavigationItem {
  readonly key: NavigationKey;
  readonly route: string;
}