import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import axios from '../lib/api';
import { RegionProvider } from '../context/RegionContext';
import EligibilityView from './EligibilityView';
import ExploreSchemesView from './ExploreSchemesView';
import ScholarshipChatView from './ScholarshipChatView';

vi.mock('../lib/api', () => ({ default: { get: vi.fn(), post: vi.fn() } }));

const wrap = (ui) => render(<RegionProvider>{ui}</RegionProvider>);

const rec = (id, name, status, extra = {}) => ({
  scholarship_id: id,
  scholarship_name: name,
  provider: 'Provider',
  benefits: 'Rs. 10,000',
  eligibility_reason: 'You qualify because your income is within the ceiling.',
  required_documents: ['Income Certificate'],
  application_url: 'https://apply.example/' + id,
  status,
  status_note: 'note',
  ...extra
});

beforeEach(() => {
  vi.resetAllMocks();
  localStorage.clear();
});

describe('Eligibility view', () => {
  const open = rec(1, 'Open Scheme', 'OPEN', {
    deadline: '2026-10-26', deadline_verified_on: '2026-10-17', academic_year: '2026-27', deadline_origin: 'SOURCE_AUTO',
    deadline_fetched_at: '2026-10-17T10:00:00+05:30', deadline_source_url: 'https://www.buddy4study.com/page/x', last_verified: '2026-10-17'
  });
  const unknown = rec(2, 'Unverified Date Scheme', 'UNKNOWN', { deadline: '2026-09-30', deadline_verified_on: null });
  const closed = rec(3, 'Closed Scheme', 'CLOSED', { deadline: '2026-10-05', deadline_verified_on: '2026-10-17' });

  it('shows open schemes first, hides closed ones by default, and states provenance', async () => {
    axios.post.mockResolvedValue({ data: [open, unknown] });
    wrap(<EligibilityView />);

    await screen.findByText('Open Scheme', {}, { timeout: 4000 });
    const headings = screen.getAllByRole('heading', { level: 4 }).map((h) => h.textContent);
    expect(headings[0]).toMatch(/^Open now/);
    expect(headings.some((h) => /^Dates not verified/.test(h))).toBe(true);
    expect(screen.queryByText('Closed Scheme')).not.toBeInTheDocument();

    // verified deadline is a plain fact; the unverified one is labelled
    expect(screen.getByText(/\(reported, not verified\)/)).toBeInTheDocument();
    expect(screen.getByText(/AY 2026-27/)).toBeInTheDocument();
    expect(screen.getByText(/Read from the official\/partner page on/)).toBeInTheDocument();
    expect(screen.getByText('Dates not verified', { selector: 'span' })).toBeInTheDocument();
    expect(screen.getAllByText('Source')[0]).toHaveAttribute('href', 'https://www.buddy4study.com/page/x');
    // the request does not ask for closed schemes
    expect(axios.post.mock.calls[0][1].include_closed).toBe(false);
  });

  it('asks for closed schemes only when the student opts in, and shows them in their own section', async () => {
    axios.post.mockImplementation((url, body) => Promise.resolve({ data: body.include_closed ? [open, closed] : [open] }));
    wrap(<EligibilityView />);
    await screen.findByText('Open Scheme', {}, { timeout: 4000 });

    fireEvent.click(screen.getByLabelText('Show closed schemes'));

    await screen.findByText('Closed Scheme', {}, { timeout: 4000 });
    expect(axios.post.mock.calls.at(-1)[1].include_closed).toBe(true);
    const headings = screen.getAllByRole('heading', { level: 4 }).map((h) => h.textContent);
    expect(headings.at(-1)).toMatch(/^Closed/);
  });

  it('explains that matching is separate from an open window', async () => {
    axios.post.mockResolvedValue({ data: [] });
    wrap(<EligibilityView />);
    await screen.findByText(/separate from whether applications are open/i, {}, { timeout: 4000 });
    await screen.findByText(/Closed schemes are hidden/, {}, { timeout: 4000 });
  });

  it('reports a failure instead of showing stale or invented results', async () => {
    axios.post.mockRejectedValue(new Error('network'));
    wrap(<EligibilityView />);
    await screen.findByText(/Could not complete evaluation/, {}, { timeout: 4000 });
  });
});

describe('Explore view', () => {
  const dir = [
    { id: 1, name: 'Open Directory Scheme', provider: 'P', benefits: 'b', state: 'ALL', category_criteria: 'ALL', status: 'OPEN', status_note: '',
      deadline: '2026-10-26', deadline_verified_on: '2026-10-17', academic_year: '2026-27', last_verified: '2026-10-17', application_url: 'https://a.example' },
    { id: 2, name: 'Undated Scheme', provider: 'P', benefits: 'b', state: 'ALL', category_criteria: 'ALL', status: 'UNKNOWN', application_url: 'https://b.example' }
  ];
  const discovered = [{ id: 9, name: 'Discovered Programme', provider: 'Some Foundation', deadline: '2026-11-10', academic_year: '2026-27',
    deadline_origin: 'SOURCE_AUTO', deadline_fetched_at: '2026-10-17T10:00:00+05:30', application_url: 'https://d.example', status: 'UNVERIFIED' }];

  beforeEach(() => {
    axios.get.mockImplementation((url) => {
      if (url.includes('/discovered')) return Promise.resolve({ data: discovered });
      return Promise.resolve({ data: { content: dir } });
    });
  });

  it('requests schemes without closed ones by default and renders status, year and dates honestly', async () => {
    wrap(<ExploreSchemesView />);
    await screen.findByText('Open Directory Scheme');
    expect(axios.get.mock.calls[0][0]).toContain('includeClosed=false');
    expect(screen.getByText(/AY 2026-27/)).toBeInTheDocument();
    expect(screen.getByText(/No deadline on record/)).toBeInTheDocument();
    expect(screen.getByText(/closed hidden/)).toBeInTheDocument();
  });

  it('re-requests with closed schemes when asked', async () => {
    wrap(<ExploreSchemesView />);
    await screen.findByText('Open Directory Scheme');
    fireEvent.click(screen.getByLabelText('Show closed schemes'));
    await waitFor(() => expect(axios.get.mock.calls.some(([u]) => u.includes('includeClosed=true'))).toBe(true));
  });

  it('lists discovered programmes separately and labels them as unreviewed', async () => {
    wrap(<ExploreSchemesView />);
    await screen.findByText('Open Directory Scheme');
    fireEvent.click(screen.getByRole('tab', { name: /Discovered/ }));
    const list = await screen.findByTestId('discovered-list');
    expect(within(list).getByText('Discovered Programme')).toBeInTheDocument();
    expect(within(list).getByText('Awaiting review')).toBeInTheDocument();
    expect(within(list).getByText(/Source states deadline/)).toBeInTheDocument();
    expect(within(list).getByText(/not used for matching/)).toBeInTheDocument();
    expect(screen.queryByText('Open Directory Scheme')).not.toBeInTheDocument();
  });
});

describe('Chat view', () => {
  it('renders answers safely: links are anchors, markdown is formatted, markup is inert text', async () => {
    axios.post.mockResolvedValue({
      data: {
        reply: 'You can apply at **NSP**: https://scholarships.gov.in/ now.\n\n- first point\n- <script>alert(1)</script>\n\n---\n**Status and sources (from our records)**',
        mode: 'LLM',
        sources: [{ id: 1, name: 'Some Scheme', url: 'https://scholarships.gov.in/', status: 'OPEN', lastVerified: '2026-10-17' }]
      }
    });
    const { container } = wrap(<ScholarshipChatView />);
    fireEvent.change(screen.getByLabelText('Your question'), { target: { value: 'hello there' } });
    fireEvent.click(screen.getByRole('button', { name: /send/i }));

    const link = await screen.findByRole('link', { name: 'https://scholarships.gov.in/' });
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
    expect(link).toHaveAttribute('target', '_blank');
    expect(screen.getByText('NSP').tagName).toBe('STRONG');
    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
    expect(screen.getByText('Records used')).toBeInTheDocument();
    expect(screen.getByText('Some Scheme')).toBeInTheDocument();
    expect(screen.getByText('Open', { selector: 'span' })).toBeInTheDocument();
  });

  it('sends recent history and the opt-in eligibility profile only when ticked', async () => {
    localStorage.setItem('scholar_profile', JSON.stringify({ annualFamilyIncome: 300000, marks: 80, state: 'Maharashtra', category: 'OBC', course: 'Engineering', gender: 'All' }));
    axios.post.mockResolvedValue({ data: { reply: 'Here is what our records say about that topic.', sources: [] } });
    wrap(<ScholarshipChatView />);

    fireEvent.change(screen.getByLabelText('Your question'), { target: { value: 'first question' } });
    fireEvent.click(screen.getByRole('button', { name: /send/i }));
    await screen.findByText(/Here is what our records say/);
    expect(axios.post.mock.calls[0][1].profile).toBeUndefined();

    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.change(screen.getByLabelText('Your question'), { target: { value: 'second question' } });
    fireEvent.click(screen.getByRole('button', { name: /send/i }));
    await waitFor(() => expect(axios.post).toHaveBeenCalledTimes(2));
    const body = axios.post.mock.calls[1][1];
    expect(body.profile.state).toBe('Maharashtra');
    expect(body.history.map((h) => h.text)).toContain('first question');
  });

  it('shows a clear message when the server cannot be reached', async () => {
    axios.post.mockRejectedValue(new Error('down'));
    wrap(<ScholarshipChatView />);
    fireEvent.change(screen.getByLabelText('Your question'), { target: { value: 'hi' } });
    fireEvent.click(screen.getByRole('button', { name: /send/i }));
    await screen.findByText(/could not reach the server/i);
  });
});
