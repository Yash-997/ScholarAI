import StatusBadge from './StatusBadge';
import { formatDate, deadlineLabel, provenanceText, sourceLink } from '../lib/schemes';
import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import axios from 'axios';
import { 
  Search, 
  ExternalLink, 
  IndianRupee, 
  Loader2, 
  RefreshCw, 
  Info, 
  X, 
  Filter, 
  Building2, 
  FileCheck2 
} from 'lucide-react';
import { useRegion } from '../context/RegionContext';

/** Dataset columns such as state/category are ';'-separated token lists; empty means unrestricted. */
const tokenList = (v) => String(v || '').split(';').map((t) => t.trim().toLowerCase()).filter(Boolean);

export default function ExploreSchemesView() {
  const { selectedRegion } = useRegion();
  const [scholarships, setScholarships] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedType, setSelectedType] = useState('ALL');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [selectedGender, setSelectedGender] = useState('ALL');
  const [activeModalItem, setActiveModalItem] = useState(null);
  const [showClosed, setShowClosed] = useState(false);
  const [tab, setTab] = useState('DIRECTORY');
  const [discovered, setDiscovered] = useState([]);

  const fetchScholarships = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await axios.get(`/api/v1/scholarships?size=100&includeClosed=${showClosed}`);
      const data = res.data.content || res.data || [];
      setScholarships(data);
      try {
        const d = await axios.get('/api/v1/scholarships/discovered');
        setDiscovered(Array.isArray(d.data) ? d.data : []);
      } catch {
        setDiscovered([]);
      }
    } catch (err) {
      console.error('Failed to load scholarships:', err);
      setError('Could not load official directory from the knowledge base.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchScholarships();
  }, [showClosed]);

  const formatINR = (val) => {
    if (!val && val !== 0) return 'No ceiling';
    return `₹${Number(val).toLocaleString('en-IN')}`;
  };

  const filtered = scholarships.filter((s) => {
    const term = searchTerm.toLowerCase();
    const name = (s.name || '').toLowerCase();
    const provider = (s.provider || '').toLowerCase();
    const benefits = (s.benefits || '').toLowerCase();
    const course = (s.eligibleCourse || s.eligible_course || s.course || '').toLowerCase();
    const description = (s.description || '').toLowerCase();

    const matchesSearch = !searchTerm ||
      name.includes(term) || provider.includes(term) || benefits.includes(term) || course.includes(term) || description.includes(term);

    const schemeStates = tokenList(s.eligibleStates || s.eligible_states || s.state);
    let matchesState = true;
    if (selectedRegion && selectedRegion !== 'All India') {
      matchesState = schemeStates.includes('all') || schemeStates.includes('all india') || schemeStates.includes(selectedRegion.toLowerCase());
    }

    const sourceType = (s.sourceType || s.source_type || s.scheme_type || s.schemeType || '').toUpperCase();
    const providerLower = provider.toLowerCase();
    let matchesType = true;
    if (selectedType === 'STATE_GOVT') {
      matchesType = sourceType.includes('STATE') || providerLower.includes('mahadbt') || providerLower.includes('ssp') || providerLower.includes('state');
    } else if (selectedType === 'CENTRAL_GOVT') {
      matchesType = sourceType.includes('CENTRAL') || providerLower.includes('nsp') || providerLower.includes('aicte') || providerLower.includes('ministry');
    } else if (selectedType === 'PRIVATE_CSR') {
      matchesType = sourceType.includes('PRIVATE') || sourceType.includes('CSR') || providerLower.includes('tata') || providerLower.includes('siemens') || providerLower.includes('reliance') || providerLower.includes('foundation');
    }

    const catTokens = tokenList(s.categoryRequirements || s.category_requirements || s.category_criteria || s.categoryCriteria);
    let matchesCategory = true;
    if (selectedCategory !== 'ALL') {
      const wanted = selectedCategory === 'General' ? 'gen' : selectedCategory.toLowerCase();
      matchesCategory = catTokens.length === 0 || catTokens.includes('all') || catTokens.includes(wanted);
    }

    const genderReq = (s.genderRequirements || s.gender_requirements || s.gender_criteria || s.genderCriteria || '').toLowerCase();
    let matchesGender = true;
    if (selectedGender === 'Female') {
      matchesGender = genderReq.includes('female') || genderReq.includes('girl') || genderReq.includes('all') || genderReq === '';
    }

    return matchesSearch && matchesState && matchesType && matchesCategory && matchesGender;
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 pb-4 border-b border-zinc-800">
        <div>
          <div className="flex items-center gap-2 mb-2">
            <span className="px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider rounded-full bg-orange-500/10 text-orange-400 border border-orange-500/20">
              Curated Records
            </span>
            <span className="text-zinc-500 text-xs">
              • {filtered.length} of {scholarships.length} Schemes Showing{showClosed ? '' : ' (closed hidden)'}
            </span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
            Explore Scholarship Directory
          </h1>
          <p className="text-xs sm:text-sm text-zinc-400 mt-1 max-w-2xl">
            Browse official schemes from Central Ministries, State Scholarship Portals (MahaDBT, SSP, UP), and premier Corporate CSR trusts across India.
          </p>
        </div>

        <button
          onClick={fetchScholarships}
          disabled={isLoading}
          className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-zinc-900 hover:bg-zinc-800 border border-zinc-700/80 text-xs font-semibold text-zinc-300 hover:text-white transition-all cursor-pointer self-start md:self-auto disabled:opacity-50"
        >
          <RefreshCw className={`w-3.5 h-3.5 text-orange-400 ${isLoading ? 'animate-spin' : ''}`} />
          <span>Refresh Directory</span>
        </button>
      </div>

      {/* Filter Dock */}
      <div className="p-5 rounded-2xl bg-zinc-900/80 border border-zinc-800 shadow-xl space-y-4">
        <div className="relative">
          <Search className="w-4 h-4 text-zinc-400 absolute left-4 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search by scheme name, provider, engineering branch, tuition waiver, or allowance..."
            className="w-full pl-11 pr-4 py-3 bg-black/60 border border-zinc-700/70 focus:border-orange-500 rounded-xl text-sm text-white placeholder-zinc-500 focus:outline-none transition-all"
          />
          {searchTerm && (
            <button
              onClick={() => setSearchTerm('')}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-white cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-12 gap-4 items-center">
          <div className="md:col-span-7 flex flex-wrap items-center gap-1.5">
            <span className="text-[11px] font-bold uppercase tracking-wider text-zinc-400 mr-1 flex items-center gap-1">
              <Filter className="w-3 h-3 text-orange-400" />
              Source:
            </span>
            {[
              { id: 'ALL', label: 'All Sources' },
              { id: 'STATE_GOVT', label: 'State Government' },
              { id: 'CENTRAL_GOVT', label: 'Central (NSP/AICTE)' },
              { id: 'PRIVATE_CSR', label: 'Corporate CSR' }
            ].map((type) => (
              <button
                key={type.id}
                type="button"
                onClick={() => setSelectedType(type.id)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                  selectedType === type.id
                    ? 'bg-orange-500 text-black shadow-[0_0_15px_rgba(249,115,22,0.35)]'
                    : 'bg-zinc-800/80 hover:bg-zinc-800 text-zinc-300 hover:text-white border border-zinc-700/60'
                }`}
              >
                {type.label}
              </button>
            ))}
          </div>

          <div className="md:col-span-3">
            <select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              className="w-full bg-black/60 border border-zinc-700/70 text-zinc-200 text-xs rounded-xl px-3 py-2 focus:border-orange-500 focus:outline-none"
            >
              <option value="ALL">Category: All Categories</option>
              <option value="General">General / Open</option>
              <option value="OBC">OBC</option>
              <option value="SC">SC</option>
              <option value="ST">ST</option>
              <option value="EWS">EWS</option>
            </select>
          </div>

          <div className="md:col-span-2">
            <select
              value={selectedGender}
              onChange={(e) => setSelectedGender(e.target.value)}
              className="w-full bg-black/60 border border-zinc-700/70 text-zinc-200 text-xs rounded-xl px-3 py-2 focus:border-orange-500 focus:outline-none"
            >
              <option value="ALL">Gender: All</option>
              <option value="Female">Female Only</option>
            </select>
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-1.5" role="tablist">
          {[['DIRECTORY', 'Directory'], ['DISCOVERED', `Discovered, unreviewed (${discovered.length})`]].map(([id, label]) => (
            <button
              key={id}
              role="tab"
              aria-selected={tab === id}
              onClick={() => setTab(id)}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold cursor-pointer ${tab === id ? 'bg-orange-500 text-black' : 'bg-zinc-800/80 text-zinc-300 border border-zinc-700/60'}`}
            >
              {label}
            </button>
          ))}
        </div>
        {tab === 'DIRECTORY' && (
          <label className="text-xs text-zinc-400 flex items-center gap-2 cursor-pointer select-none">
            <input type="checkbox" checked={showClosed} onChange={(e) => setShowClosed(e.target.checked)} className="accent-orange-500" />
            Show closed schemes
          </label>
        )}
      </div>

      {tab === 'DISCOVERED' && (
        <div className="space-y-3" data-testid="discovered-list">
          <p className="text-xs text-zinc-400 max-w-3xl">
            These programmes were found automatically on partner pages. Their eligibility was read from free text and has not been
            reviewed, so they are not used for matching. Dates are shown as the source states them; confirm on the official site.
          </p>
          {discovered.length === 0 && <p className="text-sm text-zinc-500">Nothing awaiting review.</p>}
          {discovered.map((d) => (
            <div key={d.id} className="p-4 rounded-2xl bg-zinc-900/80 border border-zinc-800 flex flex-wrap items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="text-sm font-bold text-white">{d.name}</div>
                <div className="text-[11px] text-zinc-400">{d.provider}</div>
                <div className="text-[11px] text-zinc-500 mt-1">
                  {d.deadline ? `Source states deadline ${formatDate(d.deadline)}` : 'No deadline stated'}
                  {d.academic_year ? ` · AY ${d.academic_year}` : ''}
                  {provenanceText(d) ? ` · ${provenanceText(d)}` : ''}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className="px-2.5 py-1 rounded-full text-[10px] font-bold border bg-zinc-500/10 text-zinc-300 border-zinc-500/30">Awaiting review</span>
                {(d.application_url || sourceLink(d)) && (
                  <a href={d.application_url || sourceLink(d)} target="_blank" rel="noopener noreferrer" className="text-xs text-orange-400 hover:text-orange-300">
                    Official page
                  </a>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Directory Grid */}
      {tab === 'DISCOVERED' ? null : isLoading ? (
        <div className="py-20 flex flex-col items-center justify-center text-center">
          <Loader2 className="w-10 h-10 text-orange-500 animate-spin mb-4" />
          <p className="text-zinc-300 text-sm font-semibold">Indexing scholarship directory...</p>
        </div>
      ) : filtered.length === 0 ? (
        <div className="py-16 text-center bg-zinc-900/30 border border-zinc-800/80 rounded-2xl p-8">
          <Building2 className="w-12 h-12 text-zinc-600 mx-auto mb-3" />
          <h3 className="text-base font-bold text-white">No scholarships match your active filters</h3>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filtered.map((scheme) => {
            const incCeiling = scheme.maximumFamilyIncome ?? scheme.maximum_family_income ?? scheme.income_limit;
            const minMarks = scheme.minimumMarksOrCgpa ?? scheme.minimum_marks_or_cgpa;
            const stateTokens = String(scheme.eligibleStates || scheme.eligible_states || scheme.state || 'ALL').split(';').map((t) => t.trim()).filter(Boolean);
            const state = stateTokens.length === 0 || stateTokens.some((t) => t.toLowerCase() === 'all') ? 'All India' : stateTokens.join(', ');
            const cat = scheme.categoryRequirements || scheme.category_requirements || scheme.category_criteria || 'All';
            const appUrl = scheme.officialApplicationUrl || scheme.official_application_url || scheme.application_url;

            return (
              <div
                key={scheme.id}
                className="group flex flex-col justify-between p-5 rounded-2xl bg-zinc-900/90 border border-zinc-800 hover:border-orange-500/50 hover:shadow-[0_0_25px_rgba(249,115,22,0.15)] transition-all duration-300"
              >
                <div>
                  <div className="flex items-center justify-between gap-2 mb-3">
                    <span className="px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-zinc-800 text-zinc-300 border border-zinc-700/60 truncate max-w-[65%]">
                      {scheme.provider || 'Official Authority'}
                    </span>
                    <span className="px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-orange-500/10 text-orange-400 border border-orange-500/20 shrink-0">
                      {state}
                    </span>
                  </div>

                  <h3 className="text-base font-bold text-white group-hover:text-orange-400 transition-colors line-clamp-2">
                    {scheme.name}
                  </h3>
                  <div className="mt-2">
                    <StatusBadge status={scheme.status} note={scheme.status_note} needsReview={scheme.needs_review} />
                    <p className="mt-1 text-[10px] text-zinc-500">
                      {deadlineLabel(scheme)}
                      {scheme.academic_year ? ` · AY ${scheme.academic_year}` : ''}
                      {' · '}
                      {scheme.last_verified ? `checked ${formatDate(scheme.last_verified)}` : 'verification date not recorded'}
                      {provenanceText(scheme) ? ` · ${provenanceText(scheme)}` : ''}
                      {sourceLink(scheme) ? <> · <a href={sourceLink(scheme)} target="_blank" rel="noopener noreferrer" className="underline hover:text-zinc-300">source</a></> : null}
                    </p>
                  </div>

                  <div className="mt-3 p-3 rounded-xl bg-orange-500/5 border border-orange-500/15">
                    <div className="flex items-start gap-2">
                      <IndianRupee className="w-4 h-4 text-orange-400 shrink-0 mt-0.5" />
                      <div>
                        <span className="text-[11px] font-semibold uppercase tracking-wider text-orange-400 block">
                          Financial Grant
                        </span>
                        <p className="text-xs text-zinc-200 font-medium line-clamp-2">
                          {scheme.benefits || 'Tuition reimbursement and academic grant'}
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="mt-4 grid grid-cols-2 gap-2 text-xs">
                    <div className="p-2 rounded-lg bg-black/40 border border-zinc-800">
                      <span className="text-[10px] text-zinc-500 block">Income Limit</span>
                      <span className="font-semibold text-zinc-200 truncate block">
                        {incCeiling ? `≤ ${formatINR(incCeiling)}` : 'No Limit'}
                      </span>
                    </div>

                    <div className="p-2 rounded-lg bg-black/40 border border-zinc-800">
                      <span className="text-[10px] text-zinc-500 block">Min Marks</span>
                      <span className="font-semibold text-zinc-200 truncate block">
                        {minMarks ? `≥ ${minMarks}%` : 'Passing'}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="mt-5 pt-3 border-t border-zinc-800 flex items-center justify-between gap-3">
                  <button
                    onClick={() => setActiveModalItem(scheme)}
                    className="text-xs font-semibold text-orange-400 hover:text-orange-300 transition-colors cursor-pointer flex items-center gap-1"
                  >
                    <Info className="w-3.5 h-3.5" />
                    <span>Guidelines</span>
                  </button>

                  {appUrl ? (
                    <a
                      href={appUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="px-3 py-1.5 rounded-xl bg-orange-500 hover:bg-orange-400 text-black text-xs font-bold transition-all shadow-md flex items-center gap-1.5"
                    >
                      <span>Apply</span>
                      <ExternalLink className="w-3 h-3 stroke-[2.5]" />
                    </a>
                  ) : (
                    <span className="text-[11px] text-zinc-500 italic">Apply via College</span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Guidelines Modal */}
      <AnimatePresence>
        {activeModalItem && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="relative w-full max-w-2xl bg-zinc-900 border border-zinc-700 rounded-3xl p-6 sm:p-8 shadow-2xl overflow-y-auto max-h-[90vh]"
            >
              <button
                onClick={() => setActiveModalItem(null)}
                className="absolute top-5 right-5 p-2 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-400 hover:text-white transition-all cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>

              <div className="flex items-center gap-2 mb-2">
                <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-orange-500/10 text-orange-400 border border-orange-500/30">
                  {activeModalItem.provider || 'Official Guidelines'}
                </span>
                <span className="text-zinc-500 text-xs">
                  • {activeModalItem.eligibleStates || activeModalItem.state || 'All India'}
                </span>
              </div>

              <h2 className="text-xl font-bold text-white mb-4 pr-10">
                {activeModalItem.name}
              </h2>

              <div className="p-4 rounded-2xl bg-orange-500/10 border border-orange-500/20 mb-6">
                <div className="flex items-center gap-2 text-xs font-bold text-orange-400 mb-1 uppercase tracking-wider">
                  <IndianRupee className="w-4 h-4" />
                  Financial Grant Benefits
                </div>
                <p className="text-sm font-medium text-zinc-100">
                  {activeModalItem.benefits}
                </p>
              </div>

              {activeModalItem.requiredDocuments && (
                <div className="mb-6">
                  <h4 className="text-xs font-bold text-zinc-300 uppercase tracking-wider mb-2 flex items-center gap-1.5">
                    <FileCheck2 className="w-4 h-4 text-orange-400" />
                    Required Documents
                  </h4>
                  <div className="p-4 rounded-2xl bg-black/60 border border-zinc-800">
                    <p className="text-xs text-zinc-300 leading-relaxed">
                      {activeModalItem.requiredDocuments}
                    </p>
                  </div>
                </div>
              )}

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-zinc-800">
                <button
                  onClick={() => setActiveModalItem(null)}
                  className="px-4 py-2.5 rounded-xl text-xs font-semibold text-zinc-400 hover:text-white bg-zinc-800 hover:bg-zinc-700 transition-all cursor-pointer"
                >
                  Close
                </button>
                {activeModalItem.applicationUrl && (
                  <a
                    href={activeModalItem.applicationUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="px-5 py-2.5 rounded-xl bg-gradient-to-r from-orange-500 to-amber-500 text-black text-xs font-bold transition-all flex items-center gap-2"
                  >
                    <span>Proceed to Portal</span>
                    <ExternalLink className="w-4 h-4 stroke-[2.5]" />
                  </a>
                )}
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
