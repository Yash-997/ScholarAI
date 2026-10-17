import React from 'react';
import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { RegionProvider } from './context/RegionContext';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import MatcherPage from './pages/MatcherPage';
import ChatPage from './pages/ChatPage';
import ExplorePage from './pages/ExplorePage';

function AnimatedRoutes() {
  const location = useLocation();

  return (
    <AnimatePresence mode="wait">
      <Routes location={location} key={location.pathname}>
        <Route path="/" element={<MatcherPage />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/explore" element={<ExplorePage />} />
      </Routes>
    </AnimatePresence>
  );
}

export default function App() {
  return (
    <RegionProvider>
      <BrowserRouter>
        <div className="min-h-screen bg-black text-zinc-100 flex flex-col font-sans bg-radial-grid">
          <Navbar />
          <main className="flex-1 max-w-[1600px] w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
            <AnimatedRoutes />
          </main>
          <Footer />
        </div>
      </BrowserRouter>
    </RegionProvider>
  );
}
