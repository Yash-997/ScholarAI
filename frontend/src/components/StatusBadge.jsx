import React from 'react';
import { CheckCircle, Clock, XCircle, HelpCircle, AlertTriangle } from 'lucide-react';

// Status is computed by the backend from verified dates; the UI never derives it from the browser clock.
const STYLES = {
  OPEN: { label: 'Open', icon: CheckCircle, cls: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' },
  UPCOMING: { label: 'Opens soon', icon: Clock, cls: 'bg-sky-500/10 text-sky-400 border-sky-500/20' },
  CLOSED: { label: 'Closed', icon: XCircle, cls: 'bg-red-500/10 text-red-400 border-red-500/20' },
  UNKNOWN: { label: 'Dates not verified', icon: HelpCircle, cls: 'bg-amber-500/10 text-amber-400 border-amber-500/20' },
  UNVERIFIED: { label: 'Unverified record', icon: AlertTriangle, cls: 'bg-zinc-500/10 text-zinc-400 border-zinc-500/30' }
};

export default function StatusBadge({ status, note, needsReview = false }) {
  const style = STYLES[status] || STYLES.UNKNOWN;
  const Icon = style.icon;
  return (
    <span className="inline-flex items-center gap-1.5 flex-wrap">
      <span
        title={note || ''}
        className={`px-2.5 py-1 rounded-full text-[10px] font-bold border flex items-center gap-1 shrink-0 ${style.cls}`}
      >
        <Icon className="w-3 h-3" />
        {style.label}
      </span>
      {needsReview && (
        <span
          title="The official source changed or could not be checked. Confirm details on the official site."
          className="px-2 py-1 rounded-full text-[10px] font-bold border bg-amber-500/10 text-amber-300 border-amber-500/30"
        >
          Under review
        </span>
      )}
    </span>
  );
}

export { formatDate } from '../lib/schemes';
