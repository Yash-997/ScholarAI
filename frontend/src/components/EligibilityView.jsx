import React, { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import api from '../lib/api';
import {
  Sparkles,
  CheckCircle,
  AlertCircle,
  FileCheck2,
  IndianRupee,
  MapPin,
  Zap,
  Sliders,
  ExternalLink,
  Calendar,
  AlertTriangle
} from 'lucide-react';
import { useRegion, ALL_INDIAN_STATES } from '../context/RegionContext';
import StatusBadge from './StatusBadge';
import { flattenSections, deadlineLabel, provenanceText, sourceLink, formatDate } from '../lib/schemes';

// Sent with every request; there is deliberately no user-editable prompt.
const DEFAULT_RAG_PROMPT = 'Evaluate eligibility based strictly on the provided profile parameters and detail the financial benefits.';

const RECOMMEND_PATH = '/api/v1/scholarships/recommend';
const DEBOUNCE_MS = 350;

const COURSES = [
  { value: 'Engineering', label: 'B.E. / B.Tech / Engineering' },
  { value: 'Diploma', label: 'Diploma in Engineering' },
  { value: 'Medical', label: 'MBBS / Healthcare' },
  { value: 'Pharmacy', label: 'B.Pharm / Pharmacy' },
  { value: 'Degree', label: 'B.Sc / General Degree' }
];

const CATEGORIES = [
  { value: 'General', label: 'General / Open' },
  { value: 'EWS', label: 'EWS' },
  { value: 'OBC', label: 'OBC' },
  { value: 'SC', label: 'SC' },
  { value: 'ST', label: 'ST' },
  { value: 'VJNT', label: 'VJNT / DNT / SBC' },
  { value: 'Minority', label: 'Minority community' },
  { value: 'PWD', label: 'Person with disability (40%+)' },
  { value: 'Orphan', label: 'Orphan / martyr\'s ward' }
];

const PRESETS = [
  { label: 'MH B.Tech OBC (2.5 LPA)', income: 250000, marks: 80, state: 'Maharashtra', category: 'OBC', gender: 'All', course: 'Engineering' },
  { label: 'Girls in STEM (4.5 LPA)', income: 450000, marks: 85, state: 'Maharashtra', category: 'General', gender: 'Female', course: 'Engineering' },
  { label: 'Karnataka SSP SC (2.2 LPA)', income: 220000, marks: 70, state: 'Karnataka', category: 'SC', gender: 'All', course: 'Engineering' },
  { label: 'UP Minority (1.8 LPA)', income: 180000, marks: 75, state: 'Uttar Pradesh', category: 'Minority', gender: 'All', course: 'Degree' }
];

const selectClass = 'w-full bg-black/60 border border-zinc-700/70 focus:border-orange-500 rounded-xl px-3 py-2 text-xs text-white focus:outline-none';

const PROFILE_KEY = 'scholar_profile';

export default function EligibilityView() {
  const { selectedRegion, setSelectedRegion } = useRegion();

  const [profile, setProfile] = useState({
    annualFamilyIncome: 250000,
    marks: 80,
    state: selectedRegion || 'Maharashtra',
    category: 'OBC',
    gender: 'All',
    course: 'Engineering'
  });
  // Share the form with the chatbot (opt-in there) so it does not have to ask again.
  useEffect(() => {
    try {
      localStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
    } catch {
      // storage can be unavailable (private mode); the chatbot then simply has no profile to offer
    }
  }, [profile]);

  const [showClosed, setShowClosed] = useState(false);
  const [results, setResults] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const requestIdRef = useRef(0);

  // Follow the navbar region selector.
  useEffect(() => {
    if (selectedRegion && selectedRegion !== 'All India') {
      setProfile((prev) => (prev.state === selectedRegion ? prev : { ...prev, state: selectedRegion }));
    }
  }, [selectedRegion]);

  // Any form change re-runs the recommendation after a short pause.
  useEffect(() => {
    const requestId = ++requestIdRef.current;
    const timer = setTimeout(async () => {
      setIsLoading(true);
      setError(null);
      const body = {
        query: DEFAULT_RAG_PROMPT,
        annualFamilyIncome: profile.annualFamilyIncome,
        marks: profile.marks,
        state: profile.state,
        category: profile.category,
        course: profile.course,
        gender: profile.gender,
        include_closed: showClosed
      };
      try {
        const res = await api.post(RECOMMEND_PATH, body, { timeout: 45000 });
        if (requestId !== requestIdRef.current) return; // a newer request superseded this one
        setResults(Array.isArray(res.data) ? res.data : []);
      } catch (err) {
        if (requestId !== requestIdRef.current) return;
        console.error('Recommendation failed:', err);
        setError('Could not complete evaluation. Please ensure the backend is running.');
      } finally {
        if (requestId === requestIdRef.current) setIsLoading(false);
      }
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [profile, showClosed]);

  const updateProfile = (field, value) => {
    setProfile((prev) => ({ ...prev, [field]: value }));
    if (field === 'state' && value && value !== 'All India') setSelectedRegion(value);
  };

  const applyPreset = (p) => {
    setProfile({ annualFamilyIncome: p.income, marks: p.marks, state: p.state, category: p.category, gender: p.gender, course: p.course });
    setSelectedRegion(p.state);
  };

  return (
    <div className="space-y-8">
      {/* Quick Profiles */}
      <div className="flex flex-wrap items-center gap-2 p-3 bg-zinc-900/60 border border-zinc-800 rounded-2xl">
        <span className="text-xs font-semibold text-zinc-400 flex items-center gap-1.5 px-2">
          <Zap className="w-3.5 h-3.5 text-orange-400" />
          Quick Profiles:
        </span>
        {PRESETS.map((preset) => (
          <button
            key={preset.label}
            type="button"
            onClick={() => applyPreset(preset)}
            className="px-3 py-1.5 rounded-xl text-xs font-medium bg-zinc-800/80 hover:bg-zinc-800 text-zinc-300 hover:text-white border border-zinc-700/60 hover:border-orange-500/40 transition-all cursor-pointer"
          >
            {preset.label}
          </button>
        ))}
      </div>

      {/* Centered Student Criteria Panel */}
      <div className="w-full max-w-4xl mx-auto">
        <div className="p-6 rounded-3xl bg-zinc-900/90 border border-zinc-800 shadow-xl space-y-5">
          <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
            <h2 className="text-base font-bold text-white flex items-center gap-2">
              <Sliders className="w-4 h-4 text-orange-400" />
              Student Criteria
            </h2>
            <span className="text-[11px] text-zinc-500 font-medium">Updates live</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            {/* Sliders */}
            <div className="space-y-4">
              <div className="space-y-2">
                <div className="flex justify-between items-center text-xs">
                  <label htmlFor="income" className="text-zinc-300 font-medium">Annual Family Income</label>
                  <span className="text-orange-400 font-bold font-mono">
                    ₹{Number(profile.annualFamilyIncome).toLocaleString('en-IN')} ({(profile.annualFamilyIncome / 100000).toFixed(1)} LPA)
                  </span>
                </div>
                <input
                  id="income"
                  type="range"
                  min="50000"
                  max="1500000"
                  step="25000"
                  value={profile.annualFamilyIncome}
                  onChange={(e) => updateProfile('annualFamilyIncome', Number(e.target.value))}
                  className="w-full accent-orange-500 cursor-pointer"
                />
                <div className="flex justify-between text-[10px] text-zinc-500 font-mono">
                  <span>₹50K</span>
                  <span>₹2.5L</span>
                  <span>₹8.0L</span>
                  <span>₹15L</span>
                </div>
              </div>

              <div className="space-y-2">
                <div className="flex justify-between items-center text-xs">
                  <label htmlFor="marks" className="text-zinc-300 font-medium">Academic Marks / Percentage</label>
                  <span className="text-orange-400 font-bold font-mono">{profile.marks}%</span>
                </div>
                <input
                  id="marks"
                  type="range"
                  min="35"
                  max="100"
                  step="0.5"
                  value={profile.marks}
                  onChange={(e) => updateProfile('marks', Number(e.target.value))}
                  className="w-full accent-orange-500 cursor-pointer"
                />
                <div className="flex justify-between text-[10px] text-zinc-500 font-mono">
                  <span>35%</span>
                  <span>60%</span>
                  <span>80%</span>
                  <span>100%</span>
                </div>
              </div>
            </div>

            {/* Selects */}
            <div className="space-y-3">
              <div className="space-y-1.5">
                <label htmlFor="state" className="text-xs text-zinc-300 font-medium flex items-center gap-1.5">
                  <MapPin className="w-3.5 h-3.5 text-orange-400" />
                  State of Domicile
                </label>
                <select id="state" value={profile.state} onChange={(e) => updateProfile('state', e.target.value)} className={selectClass}>
                  {ALL_INDIAN_STATES.map((s) => (
                    <option key={s} value={s}>{s}</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label htmlFor="category" className="text-xs text-zinc-300 font-medium">Category</label>
                  <select id="category" value={profile.category} onChange={(e) => updateProfile('category', e.target.value)} className={selectClass}>
                    {CATEGORIES.map((c) => (
                      <option key={c.value} value={c.value}>{c.label}</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1.5">
                  <label htmlFor="gender" className="text-xs text-zinc-300 font-medium">Gender</label>
                  <select id="gender" value={profile.gender} onChange={(e) => updateProfile('gender', e.target.value)} className={selectClass}>
                    <option value="All">Any</option>
                    <option value="Female">Female</option>
                    <option value="Male">Male</option>
                  </select>
                </div>
              </div>

              <div className="space-y-1.5">
                <label htmlFor="course" className="text-xs text-zinc-300 font-medium">Enrolled Course</label>
                <select id="course" value={profile.course} onChange={(e) => updateProfile('course', e.target.value)} className={selectClass}>
                  {COURSES.map((c) => (
                    <option key={c.value} value={c.value}>{c.label}</option>
                  ))}
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Eligible Scholarships Heading & Results Section (Full Available Width) */}
      <div className="w-full space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-4 px-1">
          <div className="flex items-center gap-3">
            <h3 className="text-base sm:text-lg font-bold text-white flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-orange-400" />
              Eligible Scholarships
            </h3>
            {results && (
              <span className="px-3 py-0.5 rounded-full text-xs font-bold bg-orange-500/10 text-orange-400 border border-orange-500/30">
                {results.length} Matched
              </span>
            )}
          </div>
          <div className="flex items-center gap-4">
            <label className="text-xs text-zinc-400 flex items-center gap-2 cursor-pointer select-none">
              <input type="checkbox" checked={showClosed} onChange={(e) => setShowClosed(e.target.checked)} className="accent-orange-500" />
              Show closed schemes
            </label>
            {isLoading && (
              <span className="text-xs text-orange-400 flex items-center gap-2 animate-pulse bg-orange-500/10 px-3 py-1.5 rounded-xl border border-orange-500/20">
                <span className="w-2 h-2 rounded-full bg-orange-400" />
                Evaluating eligibility...
              </span>
            )}
          </div>
        </div>

        {error && (
          <div className="p-4 rounded-2xl bg-red-950/20 border border-red-800/40 text-red-300 text-xs flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-red-400 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <p className="text-[11px] text-zinc-500 px-1">
          Matching your income, marks, state, category, gender and course is separate from whether applications are open.
          Each scheme shows its application status and where its dates came from. Always confirm on the official site.
        </p>

        {results && results.length === 0 && !isLoading && (
          <div className="p-12 rounded-3xl bg-zinc-900/40 border border-zinc-800 text-center space-y-3">
            <AlertCircle className="w-10 h-10 text-zinc-600 mx-auto" />
            <h4 className="text-sm font-semibold text-white">No Schemes Found</h4>
            <p className="text-xs text-zinc-400 max-w-sm mx-auto">
              Try raising the income slider, changing your category, or switching your domicile state.
              {!showClosed && ' Closed schemes are hidden; use "Show closed schemes" to include them.'}
            </p>
          </div>
        )}

        {results && results.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
              {flattenSections(results).map((entry) => {
                if (entry.heading) {
                  return (
                    <div key={entry.key} className="col-span-full pt-2">
                      <h4 className="text-sm font-bold text-white">
                        {entry.heading} <span className="text-zinc-500 font-medium">({entry.count})</span>
                      </h4>
                      <p className="text-[11px] text-zinc-500">{entry.hint}</p>
                    </div>
                  );
                }
                const scheme = entry.scheme;
                const docs = scheme.required_documents || [];
                const shownDocs = docs.slice(0, 4);
                return (
                  <motion.div
                    key={scheme.scholarship_id}
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.25 }}
                    className="h-[30rem] flex flex-col p-5 rounded-3xl bg-zinc-900/90 border border-zinc-800 hover:border-orange-500/50 hover:shadow-[0_0_25px_rgba(249,115,22,0.12)] transition-all"
                  >
                    {/* Title + provider */}
                    <div className="flex items-start justify-between gap-3 h-[4.5rem] shrink-0">
                      <div className="flex-1 min-w-0">
                        <span className="text-[10px] font-semibold text-zinc-400 uppercase tracking-wider block truncate" title={scheme.provider}>
                          {scheme.provider}
                        </span>
                        <h4 className="text-sm font-bold text-white mt-0.5 line-clamp-2 leading-snug" title={scheme.scholarship_name}>
                          {scheme.scholarship_name}
                        </h4>
                      </div>
                      <StatusBadge status={scheme.status} note={scheme.status_note} needsReview={scheme.needs_review} />
                    </div>

                    {/* Benefits */}
                    <div className="mt-3 p-3 rounded-2xl bg-orange-500/5 border border-orange-500/15 flex items-start gap-2.5 h-[6.5rem] shrink-0 overflow-hidden">
                      <IndianRupee className="w-4 h-4 text-orange-400 shrink-0 mt-0.5" />
                      <div className="min-w-0">
                        <span className="text-[11px] font-semibold uppercase tracking-wider text-orange-400 block">Benefits</span>
                        <p className="text-xs text-zinc-200 font-medium line-clamp-3" title={scheme.benefits}>{scheme.benefits}</p>
                      </div>
                    </div>

                    {/* Why you qualify */}
                    <div className="mt-3 text-xs text-zinc-300 leading-relaxed bg-black/40 p-3 rounded-xl border border-zinc-800/80 h-[6rem] shrink-0 overflow-y-auto">
                      <span className="font-semibold text-orange-400">Why you match: </span>
                      {scheme.eligibility_reason}
                    </div>

                    {/* Documents */}
                    <div className="mt-3 flex-1 min-h-0 overflow-y-auto">
                      <div className="text-xs font-semibold text-zinc-400 flex items-center gap-1.5 mb-1.5">
                        <FileCheck2 className="w-3.5 h-3.5 text-orange-400" />
                        Documents ({docs.length})
                      </div>
                      <ul className="space-y-1">
                        {shownDocs.map((doc) => (
                          <li key={doc} className="flex items-start gap-2 text-xs text-zinc-300">
                            <span className="w-1.5 h-1.5 mt-1.5 rounded-full bg-orange-500 shrink-0" />
                            <span className="line-clamp-1" title={doc}>{doc}</span>
                          </li>
                        ))}
                        {docs.length > shownDocs.length && (
                          <li className="text-[11px] text-zinc-500 pl-3.5" title={docs.slice(4).join(', ')}>
                            +{docs.length - shownDocs.length} more
                          </li>
                        )}
                      </ul>
                    </div>

                    {/* Footer */}
                    <div className="mt-3 pt-3 border-t border-zinc-800 flex items-center justify-between gap-3 shrink-0">
                      <div className="flex flex-col text-xs text-zinc-400 min-w-0" title={scheme.status_note}>
                        <span className="flex items-center gap-1.5 min-w-0">
                          <Calendar className="w-3.5 h-3.5 text-zinc-500 shrink-0" />
                          <strong className="truncate text-zinc-200">{deadlineLabel(scheme)}</strong>
                        </span>
                        <span className="text-[10px] text-zinc-500 truncate">
                          {[scheme.academic_year ? `AY ${scheme.academic_year}` : null, provenanceText(scheme),
                            scheme.last_verified ? `Record checked ${formatDate(scheme.last_verified)}` : 'Verification date not recorded']
                            .filter(Boolean).join(' · ')}
                        </span>
                      </div>
                      {sourceLink(scheme) && (
                        <a href={sourceLink(scheme)} target="_blank" rel="noopener noreferrer" className="text-[10px] text-zinc-500 hover:text-zinc-300 underline shrink-0">
                          Source
                        </a>
                      )}
                      {scheme.application_url && (
                        <a
                          href={scheme.application_url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-orange-500 to-amber-500 text-black text-xs font-bold hover:shadow-[0_0_15px_rgba(249,115,22,0.4)] transition-all flex items-center gap-1.5 shrink-0"
                        >
                          <span>Official site</span>
                          <ExternalLink className="w-3.5 h-3.5 stroke-[2.5]" />
                        </a>
                      )}
                    </div>
                  </motion.div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    );
  }
