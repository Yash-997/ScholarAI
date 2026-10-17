import React, { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import api from '../lib/api';
import { Send, Sparkles, Trash2, ExternalLink, ShieldCheck, Bot, User, BookOpen, Info } from 'lucide-react';
import { useRegion } from '../context/RegionContext';
import StatusBadge, { formatDate } from './StatusBadge';

const STARTER_PROMPTS = [
  'Which scholarships can I get? I am a girl from Maharashtra, B.Tech, OBC, income 3 lakh, 78% marks',
  'What is the income limit for the Rajarshi Shahu Maharaj EBC scheme?',
  'Are there engineering scholarships for girls under AICTE?',
  'Which documents are required for MahaDBT OBC fee reimbursement?',
  'Which private CSR scholarships are available for engineering students?'
];

const PROFILE_KEY = 'scholar_profile';
const URL_RE = /(https?:\/\/[^\s)]+[^\s).,;:!?])/g;

const now = () => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

// Renders the small subset of Markdown the assistant uses (bold, bullets, links) as React elements, never as raw HTML.
function renderInline(text, keyPrefix) {
  const parts = [];
  text.split(/(\*\*[^*]+\*\*)/g).forEach((chunk, i) => {
    if (/^\*\*[^*]+\*\*$/.test(chunk)) {
      parts.push(<strong key={`${keyPrefix}-b${i}`} className="font-semibold text-white">{chunk.slice(2, -2)}</strong>);
      return;
    }
    chunk.split(URL_RE).forEach((piece, j) => {
      if (/^https?:\/\//.test(piece)) {
        parts.push(
          <a key={`${keyPrefix}-${i}-${j}`} href={piece} target="_blank" rel="noopener noreferrer" className="text-orange-400 underline underline-offset-2 break-all hover:text-orange-300">
            {piece}
          </a>
        );
      } else if (piece) {
        parts.push(<React.Fragment key={`${keyPrefix}-${i}-${j}`}>{piece}</React.Fragment>);
      }
    });
  });
  return parts;
}

function MessageText({ text }) {
  const blocks = text.split('\n\n');
  return (
    <div className="space-y-2">
      {blocks.map((block, bi) => {
        if (block.trim() === '---') return <hr key={bi} className="border-zinc-800" />;
        const lines = block.split('\n').filter((l) => l.trim() !== '' && l.trim() !== '---');
        const isList = lines.length > 0 && lines.every((l) => /^\s*[-•]\s+/.test(l));
        if (isList) {
          return (
            <ul key={bi} className="list-disc pl-5 space-y-1">
              {lines.map((l, li) => <li key={li}>{renderInline(l.replace(/^\s*[-•]\s+/, ''), `${bi}-${li}`)}</li>)}
            </ul>
          );
        }
        return (
          <p key={bi} className="leading-relaxed">
            {lines.map((l, li) => (
              <React.Fragment key={li}>
                {li > 0 && <br />}
                {/^\s*[-•]\s+/.test(l) ? <span className="block pl-4">• {renderInline(l.replace(/^\s*[-•]\s+/, ''), `${bi}-${li}`)}</span> : renderInline(l, `${bi}-${li}`)}
              </React.Fragment>
            ))}
          </p>
        );
      })}
    </div>
  );
}

function readSavedProfile() {
  try {
    const raw = localStorage.getItem(PROFILE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export default function ScholarshipChatView() {
  const { selectedRegion } = useRegion();
  const [messages, setMessages] = useState([
    {
      id: 'welcome',
      sender: 'assistant',
      text:
        "Hi! I can answer questions about the scholarships in our records: eligibility, benefits, documents, deadlines and how to apply.\n\n" +
        "Tell me your state, course, category, family income and marks and I can check which schemes fit. " +
        "I only state what is on record, and I will say when a date or detail is not verified, so always confirm on the official site before applying.",
      sources: [],
      timestamp: now()
    }
  ]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [useFormDetails, setUseFormDetails] = useState(false);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isTyping]);

  const handleClearChat = () => {
    setMessages([
      {
        id: 'welcome-' + Date.now(),
        sender: 'assistant',
        text: 'Conversation cleared. How can I help with your scholarship search?',
        sources: [],
        timestamp: now()
      }
    ]);
  };

  const handleSend = async (messageText = input) => {
    const text = messageText.trim();
    if (!text || isTyping) return;

    setMessages((prev) => [...prev, { id: Date.now().toString(), sender: 'user', text, timestamp: now() }]);
    setInput('');
    setIsTyping(true);

    const history = messages
      .filter((m) => m.id !== 'welcome' && !String(m.id).startsWith('welcome-'))
      .slice(-6)
      .map((m) => ({ sender: m.sender, text: m.text }));
    const body = { message: text, history };
    if (useFormDetails) {
      const saved = readSavedProfile();
      if (saved) {
        body.profile = {
          annualFamilyIncome: saved.annualFamilyIncome,
          marks: saved.marks,
          state: saved.state,
          category: saved.category,
          course: saved.course,
          gender: saved.gender
        };
      }
    }

    try {
      const res = await api.post('/api/v1/scholarships/chat', body, { timeout: 60000 });
      setMessages((prev) => [
        ...prev,
        {
          id: (Date.now() + 1).toString(),
          sender: 'assistant',
          text: res.data.reply || 'I could not find that in our records. Please check the official portal.',
          sources: res.data.sources || [],
          mode: res.data.mode,
          timestamp: now()
        }
      ]);
    } catch (err) {
      console.error('Chat error:', err);
      setMessages((prev) => [
        ...prev,
        {
          id: (Date.now() + 1).toString(),
          sender: 'assistant',
          text: 'I could not reach the server just now. Please check that the backend is running and try again.',
          sources: [],
          timestamp: now()
        }
      ]);
    } finally {
      setIsTyping(false);
    }
  };

  const savedProfile = readSavedProfile();

  return (
    <div className="flex flex-col h-[calc(100vh-140px)] max-h-[880px] bg-zinc-950 border border-zinc-800 rounded-3xl overflow-hidden shadow-2xl">
      <div className="px-6 py-4 border-b border-zinc-800/80 bg-zinc-900/60 backdrop-blur-md flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-orange-500/10 border border-orange-500/20 flex items-center justify-center text-orange-400">
            <Sparkles className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-white tracking-tight">AI Scholarship Assistant</h2>
            <p className="text-xs text-zinc-400">
              Answers come from our scholarship records, with their status and official links
              {selectedRegion && selectedRegion !== 'All India' ? ` • Prioritizing ${selectedRegion}` : ''}
            </p>
          </div>
        </div>

        <button
          onClick={handleClearChat}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium text-zinc-400 hover:text-white bg-zinc-800/40 hover:bg-zinc-800 border border-zinc-700/50 transition-all cursor-pointer"
          title="Clear conversation"
        >
          <Trash2 className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Reset Chat</span>
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 sm:p-6 space-y-5">
        <AnimatePresence initial={false}>
          {messages.map((msg) => {
            const isUser = msg.sender === 'user';
            return (
              <motion.div
                key={msg.id}
                initial={{ opacity: 0, y: 12, scale: 0.98 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, scale: 0.95 }}
                transition={{ duration: 0.25 }}
                className={`flex gap-3 sm:gap-4 ${isUser ? 'justify-end' : 'justify-start'}`}
              >
                {!isUser && (
                  <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-orange-500 to-amber-600 flex items-center justify-center text-black shrink-0 shadow-md">
                    <Bot className="w-4 h-4 stroke-[2.5]" />
                  </div>
                )}

                <div
                  className={`max-w-[85%] sm:max-w-[75%] rounded-2xl p-4 sm:p-5 text-sm leading-relaxed ${
                    isUser
                      ? 'bg-gradient-to-r from-orange-500 to-amber-500 text-black font-medium shadow-[0_0_20px_rgba(249,115,22,0.25)] rounded-tr-none'
                      : 'bg-zinc-900 border border-zinc-800 text-zinc-200 shadow-lg rounded-tl-none'
                  }`}
                >
                  {isUser ? <p className="whitespace-pre-wrap">{msg.text}</p> : <MessageText text={msg.text} />}

                  {!isUser && msg.sources && msg.sources.length > 0 && (
                    <div className="mt-4 pt-3 border-t border-zinc-800/80">
                      <div className="flex items-center gap-1.5 text-[11px] font-semibold text-orange-400 mb-2 uppercase tracking-wider">
                        <ShieldCheck className="w-3.5 h-3.5" />
                        Records used
                      </div>
                      <div className="space-y-2">
                        {msg.sources.map((src) => (
                          <div key={src.id} className="flex flex-wrap items-center gap-2 px-2.5 py-2 rounded-lg bg-zinc-800/60 border border-zinc-700/60 text-[11px] text-zinc-300">
                            <BookOpen className="w-3 h-3 text-orange-400 shrink-0" />
                            <span className="font-medium">{src.name}</span>
                            <StatusBadge status={src.status} />
                            <span className="text-zinc-500">
                              {src.lastVerified ? `checked ${formatDate(src.lastVerified)}` : 'verification date not recorded'}
                            </span>
                            {src.url && (
                              <a href={src.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-orange-400 hover:text-orange-300">
                                Official site <ExternalLink className="w-3 h-3" />
                              </a>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  <div className={`text-[10px] mt-2 text-right ${isUser ? 'text-black/70' : 'text-zinc-500'}`}>{msg.timestamp}</div>
                </div>

                {isUser && (
                  <div className="w-8 h-8 rounded-xl bg-zinc-800 border border-zinc-700 flex items-center justify-center text-zinc-300 shrink-0">
                    <User className="w-4 h-4" />
                  </div>
                )}
              </motion.div>
            );
          })}
        </AnimatePresence>

        {isTyping && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-orange-500/10 border border-orange-500/20 flex items-center justify-center text-orange-400 shrink-0">
              <Bot className="w-4 h-4" />
            </div>
            <div className="bg-zinc-900 border border-zinc-800 px-4 py-3 rounded-2xl rounded-tl-none flex items-center gap-1.5 shadow-md">
              <span className="w-2 h-2 rounded-full bg-orange-500 animate-bounce" style={{ animationDelay: '0ms' }} />
              <span className="w-2 h-2 rounded-full bg-orange-500 animate-bounce" style={{ animationDelay: '150ms' }} />
              <span className="w-2 h-2 rounded-full bg-orange-500 animate-bounce" style={{ animationDelay: '300ms' }} />
              <span className="text-xs text-zinc-400 ml-2 font-medium">Looking through the records...</span>
            </div>
          </motion.div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <div className="px-4 py-2 border-t border-zinc-800/60 bg-zinc-950/70 overflow-x-auto no-scrollbar">
        <div className="flex items-center gap-2 min-w-max">
          <span className="text-[11px] text-zinc-500 flex items-center gap-1 font-semibold uppercase tracking-wider">
            <Sparkles className="w-3 h-3 text-orange-400" />
            Try:
          </span>
          {STARTER_PROMPTS.map((prompt, idx) => (
            <button
              key={idx}
              onClick={() => handleSend(prompt)}
              disabled={isTyping}
              className="px-3 py-1.5 rounded-xl bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 hover:border-orange-500/40 text-xs text-zinc-300 hover:text-white transition-all cursor-pointer whitespace-nowrap disabled:opacity-50"
            >
              {prompt.length > 70 ? `${prompt.slice(0, 67)}...` : prompt}
            </button>
          ))}
        </div>
      </div>

      <div className="p-4 bg-zinc-900/90 border-t border-zinc-800 backdrop-blur-md">
        {savedProfile && (
          <label className="mb-3 flex items-center gap-2 text-xs text-zinc-400 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={useFormDetails}
              onChange={(e) => setUseFormDetails(e.target.checked)}
              className="accent-orange-500"
            />
            <Info className="w-3.5 h-3.5 text-orange-400" />
            Use my details from the Eligibility form ({savedProfile.state}, {savedProfile.category}, ₹{Number(savedProfile.annualFamilyIncome).toLocaleString('en-IN')}, {savedProfile.marks}%)
          </label>
        )}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSend();
          }}
          className="flex items-center gap-2"
        >
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            maxLength={1500}
            placeholder="Ask about eligibility, benefits, documents, deadlines or how to apply..."
            disabled={isTyping}
            aria-label="Your question"
            className="flex-1 bg-black/60 border border-zinc-700/70 focus:border-orange-500 rounded-2xl px-4 py-3 text-sm text-white placeholder-zinc-500 focus:outline-none transition-all"
          />
          <button
            type="submit"
            disabled={!input.trim() || isTyping}
            className="px-5 py-3 rounded-2xl bg-gradient-to-r from-orange-500 to-amber-500 text-black font-bold text-sm hover:shadow-[0_0_20px_rgba(249,115,22,0.4)] transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed shrink-0 flex items-center gap-2"
          >
            <span>Send</span>
            <Send className="w-4 h-4 stroke-[2.5]" />
          </button>
        </form>
      </div>
    </div>
  );
}
