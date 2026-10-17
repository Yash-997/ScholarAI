import React, { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { 
  GraduationCap, 
  Sparkles, 
  MessageSquare, 
  Compass, 
  MapPin, 
  ChevronDown,
  Check
} from 'lucide-react';
import { useRegion } from '../context/RegionContext';

export default function Navbar() {
  const { selectedRegion, setSelectedRegion, SUPPORTED_REGIONS } = useRegion();
  const [dropdownOpen, setDropdownOpen] = useState(false);

  const navItems = [
    { name: 'Eligibility Matcher', path: '/', icon: Sparkles },
    { name: 'AI Scholarship Chat', path: '/chat', icon: MessageSquare },
    { name: 'Explore Schemes', path: '/explore', icon: Compass }
  ];

  const currentRegionObj = SUPPORTED_REGIONS.find((r) => r.id === selectedRegion) || SUPPORTED_REGIONS[0];

  return (
    <header className="border-b border-zinc-800/80 bg-black/90 backdrop-blur-md sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-18">
          {/* Brand Logo */}
          <NavLink to="/" className="flex items-center gap-3 group">
            <div className="relative flex items-center justify-center w-10 h-10 rounded-xl bg-gradient-to-br from-orange-500 to-amber-600 shadow-[0_0_20px_rgba(249,115,22,0.45)] group-hover:scale-105 transition-transform">
              <GraduationCap className="w-5 h-5 text-black stroke-[2.5]" />
              <span className="absolute -bottom-1 -right-1 w-2.5 h-2.5 rounded-full bg-emerald-400 border-2 border-black" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xl font-extrabold tracking-tight text-white font-mono">
                  SCHOLAR<span className="text-orange-500">.AI</span>
                </span>
                <span className="hidden sm:inline px-2 py-0.5 text-[10px] font-semibold tracking-wider uppercase rounded-full bg-orange-500/10 text-orange-400 border border-orange-500/30">
                  Student Portal
                </span>
              </div>
            </div>
          </NavLink>

          {/* Center Navigation Tabs */}
          <nav className="hidden md:flex items-center gap-1.5 bg-zinc-900/90 p-1.5 rounded-2xl border border-zinc-800/90 shadow-inner">
            {navItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  end={item.path === '/'}
                  className={({ isActive }) =>
                    `flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold transition-all duration-200 cursor-pointer ${
                      isActive
                        ? 'bg-gradient-to-r from-orange-500 to-amber-500 text-black shadow-[0_0_20px_rgba(249,115,22,0.4)]'
                        : 'text-zinc-400 hover:text-white hover:bg-zinc-800/60'
                    }`
                  }
                >
                  <Icon className="w-3.5 h-3.5 stroke-[2.5]" />
                  <span>{item.name}</span>
                </NavLink>
              );
            })}
          </nav>

          {/* Right: State / Region Selector */}
          <div className="relative">
            <button
              type="button"
              onClick={() => setDropdownOpen(!dropdownOpen)}
              className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-zinc-900 hover:bg-zinc-800/90 border border-zinc-700/70 hover:border-orange-500/50 text-xs font-semibold text-white transition-all shadow-md group cursor-pointer"
            >
              <MapPin className="w-3.5 h-3.5 text-orange-400 shrink-0" />
              <span className="text-zinc-400 font-normal hidden sm:inline">State:</span>
              <span className="text-white font-bold">{currentRegionObj.name}</span>
              <ChevronDown className={`w-3.5 h-3.5 text-zinc-400 transition-transform duration-200 ${dropdownOpen ? 'rotate-180 text-orange-400' : ''}`} />
            </button>

            {/* Dropdown Menu */}
            {dropdownOpen && (
              <div 
                className="absolute right-0 mt-2 w-56 rounded-2xl bg-zinc-900 border border-zinc-700/80 shadow-2xl p-2 z-50 backdrop-blur-xl animate-in fade-in zoom-in-95 duration-150"
              >
                <div className="px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider text-zinc-400 border-b border-zinc-800 mb-1">
                  Filter by State / Domicile
                </div>
                {SUPPORTED_REGIONS.map((region) => {
                  const isSelected = region.id === selectedRegion;
                  return (
                    <button
                      key={region.id}
                      type="button"
                      onClick={() => {
                        setSelectedRegion(region.id);
                        setDropdownOpen(false);
                      }}
                      className={`w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium transition-colors cursor-pointer ${
                        isSelected
                          ? 'bg-orange-500/10 text-orange-400 border border-orange-500/30'
                          : 'text-zinc-300 hover:bg-zinc-800 hover:text-white'
                      }`}
                    >
                      <span>{region.name}</span>
                      {isSelected && <Check className="w-3.5 h-3.5 text-orange-400" />}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
