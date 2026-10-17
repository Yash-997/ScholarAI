import React from 'react';
import { NavLink } from 'react-router-dom';
import { GraduationCap, ExternalLink } from 'lucide-react';

export default function Footer() {
  return (
    <footer className="border-t border-zinc-800/80 bg-zinc-950/90 py-8 text-xs text-zinc-400">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col md:flex-row items-center justify-between gap-5">
          {/* Brand & Mission */}
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-orange-500/10 border border-orange-500/20 text-orange-400">
              <GraduationCap className="w-4 h-4 stroke-[2.5]" />
            </div>
            <div>
              <p className="font-extrabold text-white text-sm tracking-tight font-mono">
                SCHOLAR<span className="text-orange-500">.AI</span>
              </p>
              <p className="text-[11px] text-zinc-400">
                AI-Powered Scholarship Recommendation Platform for Indian Students
              </p>
            </div>
          </div>

          {/* Quick Links */}
          <div className="flex items-center gap-6 text-xs text-zinc-300">
            <NavLink to="/" className="hover:text-orange-400 transition-colors">
              Eligibility Matcher
            </NavLink>
            <NavLink to="/chat" className="hover:text-orange-400 transition-colors">
              AI Scholarship Chat
            </NavLink>
            <NavLink to="/explore" className="hover:text-orange-400 transition-colors">
              Explore Schemes
            </NavLink>
            <a 
              href="https://mahadbt.maharashtra.gov.in" 
              target="_blank" 
              rel="noopener noreferrer" 
              className="hover:text-orange-400 transition-colors inline-flex items-center gap-1"
            >
              <span>MahaDBT</span>
              <ExternalLink className="w-3 h-3 opacity-60" />
            </a>
            <a 
              href="https://scholarships.gov.in" 
              target="_blank" 
              rel="noopener noreferrer" 
              className="hover:text-orange-400 transition-colors inline-flex items-center gap-1"
            >
              <span>NSP Portal</span>
              <ExternalLink className="w-3 h-3 opacity-60" />
            </a>
          </div>

          {/* Copyright */}
          <div className="text-[11px] text-zinc-400 text-center md:text-right">
            <p>© 2026 ScholarAI. All rights reserved.</p>
            <p className="text-zinc-500 mt-0.5">Free educational public service for higher education.</p>
          </div>
        </div>
      </div>
    </footer>
  );
}
