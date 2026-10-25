import { describe, expect, it } from 'vitest';
import { queueFollowUpFiles } from './follow-up-file-queue';

const pdf = (name: string, size = 100, lastModified = 1) =>
  ({ name, size, lastModified }) as File;

describe('queueFollowUpFiles', () => {
  it('appends separate selections without replacing pending files', () => {
    const first = pdf('first.pdf');
    const second = pdf('second.pdf');
    expect(queueFollowUpFiles([first], [second])).toEqual([first, second]);
  });
  it('ignores reselecting the same file', () => {
    const file = pdf('report.pdf');
    expect(queueFollowUpFiles([file], [pdf('report.pdf')])).toEqual([file]);
  });
  it('accepts different versions of the same filename', () => {
    const first = pdf('report.pdf');
    const second = pdf('report.pdf', 110);
    expect(queueFollowUpFiles([first], [second])).toEqual([first, second]);
  });
  it('retains prior selection on invalid additions', () => {
    expect(queueFollowUpFiles([pdf('safe.pdf')], [pdf('bad.txt')])).toBeNull();
    expect(queueFollowUpFiles([pdf('safe.pdf')], [pdf('empty.pdf', 0)])).toBeNull();
    expect(queueFollowUpFiles([pdf('safe.pdf')], [pdf('big.pdf', 25 * 1024 * 1024 + 1)])).toBeNull();
  });
  it('enforces the five PDF limit across multiple picks', () => {
    const first = [1, 2, 3, 4, 5].map(n => pdf(`${n}.pdf`));
    expect(queueFollowUpFiles(first, [pdf('six.pdf')])).toBeNull();
  });
  it('does not mutate original queued array', () => {
    const previous = [pdf('a.pdf')];
    queueFollowUpFiles(previous, [pdf('b.pdf')]);
    expect(previous).toHaveLength(1);
  });
});
