import { describe, it, expect } from 'vitest';
import { groupSchemes, flattenSections, deadlineLabel, provenanceText, sourceLink, formatDate } from './schemes';

const s = (id, status, extra = {}) => ({ scholarship_id: id, status, ...extra });

describe('groupSchemes', () => {
  it('orders sections open, upcoming, unknown, unverified, closed and drops empty ones', () => {
    const groups = groupSchemes([s(1, 'CLOSED'), s(2, 'UNKNOWN'), s(3, 'OPEN'), s(4, 'OPEN'), s(5, 'UPCOMING')]);
    expect(groups.map((g) => g.status)).toEqual(['OPEN', 'UPCOMING', 'UNKNOWN', 'CLOSED']);
    expect(groups[0].items.map((i) => i.scholarship_id)).toEqual([3, 4]);
  });

  it('treats an unrecognised or missing status as UNKNOWN, never as open', () => {
    const groups = groupSchemes([s(1, 'WHATEVER'), s(2, undefined)]);
    expect(groups).toHaveLength(1);
    expect(groups[0].status).toBe('UNKNOWN');
  });

  it('handles empty input', () => {
    expect(groupSchemes(null)).toEqual([]);
    expect(flattenSections([])).toEqual([]);
  });

  it('flattens into headings followed by their cards', () => {
    const flat = flattenSections([s(1, 'OPEN'), s(2, 'CLOSED')]);
    expect(flat.map((e) => (e.heading ? `H:${e.heading}` : `S:${e.scheme.scholarship_id}`))).toEqual(['H:Open now', 'S:1', 'H:Closed', 'S:2']);
    expect(flat[0].count).toBe(1);
  });
});

describe('deadlineLabel', () => {
  it('shows a verified deadline as a plain fact', () => {
    expect(deadlineLabel({ deadline: '2026-10-26', deadline_verified_on: '2026-10-17' })).toBe(formatDate('2026-10-26'));
  });

  it('labels an unverified deadline as reported, not verified', () => {
    expect(deadlineLabel({ deadline: '2026-09-30', deadline_verified_on: null })).toMatch(/\(reported, not verified\)$/);
  });

  it('never invents a date', () => {
    expect(deadlineLabel({})).toBe('No deadline on record');
  });

  it('describes official window notices only when there is no date', () => {
    expect(deadlineLabel({ window_state: 'ROLLING_OPEN' })).toMatch(/Rolling/);
    expect(deadlineLabel({ window_state: 'CLOSED_NOTICE' })).toMatch(/Closed/);
    expect(deadlineLabel({ window_state: 'CLOSED_NOTICE', deadline: '2026-11-01', deadline_verified_on: '2026-10-01' })).toBe(formatDate('2026-11-01'));
  });
});

describe('provenance', () => {
  it('names the origin of the dates', () => {
    expect(provenanceText({ deadline_origin: 'SOURCE_AUTO', deadline_fetched_at: '2026-10-17T10:00:00+05:30' })).toBe(`Read from the official/partner page on ${formatDate('2026-10-17')}`);
    expect(provenanceText({ deadline_origin: 'ADMIN', deadline_verified_on: '2026-10-10' })).toMatch(/Checked by a reviewer on/);
    expect(provenanceText({ deadline_origin: 'DATASET', deadline_verified_on: '2026-10-10' })).toMatch(/curated dataset/);
  });

  it('says nothing when there is nothing to say', () => {
    expect(provenanceText({})).toBeNull();
    expect(provenanceText({ deadline_origin: 'DATASET' })).toBeNull();
  });

  it('prefers the page the dates were read from as the source link', () => {
    expect(sourceLink({ deadline_source_url: 'https://a', source_url: 'https://b' })).toBe('https://a');
    expect(sourceLink({ source_url: 'https://b' })).toBe('https://b');
    expect(sourceLink({})).toBeNull();
  });
});
