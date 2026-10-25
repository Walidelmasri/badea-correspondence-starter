/** Client-side pending attachments only. Never mutates Alfresco documents. */
export function queueFollowUpFiles(
  current: readonly File[], incoming: readonly File[],
): readonly File[] | null {
  const next = [...current];
  for (const file of incoming) {
    if (!file.name.toLowerCase().endsWith('.pdf')
      || file.size < 5 || file.size > 25 * 1024 * 1024) return null;
    if (!next.some(existing => existing.name === file.name
        && existing.size === file.size && existing.lastModified === file.lastModified)) {
      next.push(file);
    }
  }
  return next.length <= 5 ? next : null;
}
