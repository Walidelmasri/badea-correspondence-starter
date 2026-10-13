export const BADEA_CORRESPONDENCE_BASE_URL =
  '/alfresco/service/badea/correspondence';

export const BADEA_CORRESPONDENCE_DEPARTMENTS_URL =
  `${BADEA_CORRESPONDENCE_BASE_URL}/departments`;

export function badeaCorrespondenceTransitionUrl(
  technicalId: string,
): string {
  return (
    `${BADEA_CORRESPONDENCE_BASE_URL}/` +
    `${encodeURIComponent(technicalId)}/transition`
  );
}
export function badeaCorrespondencePresidentReviewUrl(
  technicalId: string,
): string {
  return `${BADEA_CORRESPONDENCE_BASE_URL}/${encodeURIComponent(
    technicalId,
  )}/president-review`;
}