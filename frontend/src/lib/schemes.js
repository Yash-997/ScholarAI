// Pure helpers shared by the views. Status always comes from the backend (computed from verified dates);
// nothing here derives open/closed from the browser clock.

export function formatDate(iso) {
  if (!iso) return null;
  const d = new Date(`${String(iso).slice(0, 10)}T00:00:00`);
  return Number.isNaN(d.getTime()) ? String(iso) : d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

export const SECTIONS = [
  { status: 'OPEN', title: 'Open now', hint: 'Application window verified open' },
  { status: 'UPCOMING', title: 'Opening soon', hint: 'Verified opening date is in the future' },
  { status: 'UNKNOWN', title: 'Dates not verified', hint: 'You may match these, but their window is unconfirmed. Check the official site.' },
  { status: 'UNVERIFIED', title: 'Unreviewed records', hint: 'Not yet reviewed by a person' },
  { status: 'CLOSED', title: 'Closed', hint: 'The verified application window has ended' }
];

/** Groups items by status in display order, dropping empty groups. Unrecognised statuses are treated as UNKNOWN. */
export function groupSchemes(items) {
  const known = new Set(SECTIONS.map((s) => s.status));
  const buckets = new Map(SECTIONS.map((s) => [s.status, []]));
  (items || []).forEach((item) => {
    const status = known.has(item.status) ? item.status : 'UNKNOWN';
    buckets.get(status).push(item);
  });
  return SECTIONS.filter((s) => buckets.get(s.status).length > 0).map((s) => ({ ...s, items: buckets.get(s.status) }));
}

/** Heading and card entries in one list so a single grid can render both. */
export function flattenSections(items) {
  const out = [];
  groupSchemes(items).forEach((section) => {
    out.push({ heading: section.title, hint: section.hint, status: section.status, count: section.items.length, key: `h-${section.status}` });
    section.items.forEach((scheme) => out.push({ scheme, key: `s-${scheme.scholarship_id ?? scheme.id}` }));
  });
  return out;
}

/** The deadline as a fact only when it was verified; otherwise it is labelled as reported. */
export function deadlineLabel(s) {
  if (s.window_state === 'ROLLING_OPEN' && !s.deadline) return 'Rolling applications (stated on the official page)';
  if (s.window_state === 'CLOSED_NOTICE' && !s.deadline) return 'Closed (stated on the official page)';
  if (!s.deadline) return 'No deadline on record';
  const when = formatDate(s.deadline);
  return s.deadline_verified_on ? when : `${when} (reported, not verified)`;
}

/** Where the dates came from, in words a student can judge. */
export function provenanceText(s) {
  const fetched = s.deadline_fetched_at ? formatDate(s.deadline_fetched_at) : null;
  if (s.deadline_origin === 'SOURCE_AUTO') return `Read from the official/partner page${fetched ? ` on ${fetched}` : ''}`;
  if (s.deadline_origin === 'ADMIN') return `Checked by a reviewer${s.deadline_verified_on ? ` on ${formatDate(s.deadline_verified_on)}` : ''}`;
  if (s.deadline_origin === 'DATASET' && s.deadline_verified_on) return `From the curated dataset, checked ${formatDate(s.deadline_verified_on)}`;
  return null;
}

export function sourceLink(s) {
  return s.deadline_source_url || s.source_url || null;
}
