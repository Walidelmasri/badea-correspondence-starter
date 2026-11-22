/** Display-only locale selection; never changes the persisted sender identity. */
export function mailboxSenderName(
  item: { readonly senderName: string; readonly senderNameArabic?: string | null },
  language: 'ar' | 'en',
): string {
  return language === 'ar' ? (item.senderNameArabic?.trim() || item.senderName) : item.senderName;
}
