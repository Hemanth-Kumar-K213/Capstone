import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Shield, 
  KeyRound, 
  ArrowRight, 
  Lock, 
  Clock, 
  Sparkles, 
  CheckCircle2, 
  AlertTriangle, 
  ShieldCheck,
  Globe2
} from 'lucide-react';
import { verifyWeeklyUID } from '../services/safetyService';
import { DEMO_UID_ACTIVE, DEMO_UID_CHILD, DEMO_UID_SOS, DEMO_UID_EXPIRED } from '../services/mockData';

const HomePage: React.FC = () => {
  const [uid, setUid] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleVerifyUID = async (overrideUid?: string) => {
    const targetUid = (overrideUid || uid).replace(/\s+/g, '');
    
    if (!targetUid) {
      setError('Please enter the 12-digit Safety UID provided by your contact.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const result = await verifyWeeklyUID(targetUid);

      if (!result.success || !result.uidDoc) {
        setError(result.error || 'Failed to verify Safety UID.');
        setLoading(false);
        return;
      }

      // Store in sessionStorage (zero persistent cookies / no login credentials)
      sessionStorage.setItem('safetyUID', targetUid);
      sessionStorage.setItem('userId', result.uidDoc.userId);
      sessionStorage.setItem('userName', result.uidDoc.userName || result.userData?.name || 'Protected User');
      sessionStorage.setItem('isMockMode', result.isMockMode ? 'true' : 'false');
      if (result.userData) {
        sessionStorage.setItem('cachedUserData', JSON.stringify(result.userData));
      }

      navigate('/dashboard');
    } catch (err: any) {
      console.error('UID verification error:', err);
      setError(err?.message || 'Error communicating with safety server. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !loading) {
      handleVerifyUID();
    }
  };

  const handleQuickFill = (demoUid: string) => {
    setUid(demoUid);
    setError('');
    handleVerifyUID(demoUid);
  };

  const formatInputDisplay = (val: string) => {
    return val.trim();
  };

  return (
    <div className="min-h-screen flex flex-col justify-between p-4 sm:p-6 lg:p-8 relative overflow-hidden bg-[#070A11]">
      {/* Background Ambient Glows */}
      <div className="absolute top-0 left-1/4 w-[500px] h-[500px] bg-purple-600/10 rounded-full blur-[120px] pointer-events-none" />
      <div className="absolute bottom-0 right-1/4 w-[500px] h-[500px] bg-rose-600/10 rounded-full blur-[120px] pointer-events-none" />
      <div className="absolute top-1/2 right-10 w-[300px] h-[300px] bg-blue-600/5 rounded-full blur-[100px] pointer-events-none" />

      {/* Top Header Navigation */}
      <header className="max-w-6xl w-full mx-auto flex items-center justify-between py-3 relative z-10">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-2xl bg-gradient-to-tr from-purple-600 via-purple-500 to-rose-500 p-0.5 shadow-xl shadow-purple-600/20">
            <div className="w-full h-full bg-[#0B0F19] rounded-[14px] flex items-center justify-center">
              <Shield className="w-6 h-6 text-purple-400" />
            </div>
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-extrabold tracking-tight text-white font-sans">
                SHIELD<span className="text-purple-400">GUARD</span>
              </h1>
              <span className="px-2 py-0.5 rounded-md bg-purple-500/10 text-purple-300 border border-purple-500/20 text-[10px] font-bold uppercase tracking-wider">
                Public Gateway
              </span>
            </div>
            <p className="text-[11px] text-slate-400 font-medium">
              Women Safety Network — Trusted Contact Dashboard
            </p>
          </div>
        </div>

        <div className="hidden sm:flex items-center gap-2.5 bg-slate-900/90 border border-slate-800/80 px-4 py-2 rounded-full text-xs text-slate-300 backdrop-blur-md shadow-lg">
          <Lock className="w-3.5 h-3.5 text-emerald-400" />
          <span className="font-semibold">Zero-Auth Architecture</span>
          <span className="w-1 h-1 rounded-full bg-slate-600"></span>
          <span className="text-slate-400">UID Only</span>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="max-w-xl w-full mx-auto my-6 relative z-10">
        <div className="glass-panel-glow rounded-3xl p-6 sm:p-10 shadow-2xl relative overflow-hidden">
          {/* Subtle Top Gradient Line */}
          <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-purple-500 via-rose-500 to-indigo-500" />

          {/* Header Pill & Expiry Indicator */}
          <div className="flex items-center justify-between mb-6">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-xl text-xs font-bold bg-purple-500/15 text-purple-300 border border-purple-500/30">
              <KeyRound className="w-3.5 h-3.5" />
              <span>Weekly Rotating Security UID</span>
            </div>
            <span className="text-xs text-slate-400 font-mono flex items-center gap-1.5 bg-slate-900/60 px-2.5 py-1 rounded-lg border border-slate-800">
              <Clock className="w-3.5 h-3.5 text-purple-400" /> 7-Day Auto-Rotation
            </span>
          </div>

          <h2 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight leading-tight">
            Live Guardian Tracking Portal
          </h2>
          <p className="text-slate-400 text-sm mt-2 leading-relaxed">
            Enter the 12-digit Safety UID or User ID shared by your loved one to stream real-time GPS coordinates, monitor SOS events, and review situational safety metrics.
          </p>

          {/* Error Notice */}
          {error && (
            <div className="mt-5 p-4 rounded-2xl bg-rose-950/70 border border-rose-500/60 text-rose-200 text-xs flex items-start gap-3 shadow-lg shadow-rose-950/40 animate-in fade-in">
              <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
              <div>
                <p className="font-bold text-rose-300">Access Notice</p>
                <p className="mt-0.5 text-rose-200/90">{error}</p>
              </div>
            </div>
          )}

          {/* UID Form Input */}
          <div className="mt-6 space-y-4">
            <div>
              <div className="flex items-center justify-between mb-2">
                <label htmlFor="safety-uid-input" className="text-xs font-bold text-slate-300 uppercase tracking-wider cursor-pointer">
                  Safety UID or User ID
                </label>
                <span className="text-[11px] font-mono text-purple-400">Child UID / User ID</span>
              </div>

              <div className="relative">
                <input
                  id="safety-uid-input"
                  name="safetyUID"
                  type="text"
                  value={uid}
                  onChange={(e) => {
                    setUid(formatInputDisplay(e.target.value));
                    setError('');
                  }}
                  onKeyDown={handleKeyPress}
                  placeholder="202610111234"
                  maxLength={64}
                  disabled={loading}
                  aria-label="Enter 12-digit Safety UID or User ID"
                  aria-required="true"
                  autoComplete="off"
                  className="w-full px-5 py-4 bg-[#0B0F19]/90 border-2 border-slate-700/80 rounded-2xl text-white text-lg font-mono placeholder:text-slate-600 focus:outline-none focus:border-purple-500 focus:ring-4 focus:ring-purple-500/15 transition shadow-inner tracking-wider"
                />
                <div className="absolute right-4 top-1/2 -translate-y-1/2 text-xs font-mono text-slate-400 pointer-events-none bg-slate-900/80 px-2 py-1 rounded-lg border border-slate-800">
                  {uid.length > 0 ? `${uid.length} chars` : 'UID'}
                </div>
              </div>
            </div>

            {/* Submit Action */}
            <button
              type="button"
              onClick={() => handleVerifyUID()}
              disabled={loading || uid.trim().length < 4}
              aria-label="Access Real-time Safety Console"
              className={`w-full py-4 px-6 rounded-2xl font-bold text-sm tracking-wide transition-all duration-200 flex items-center justify-center gap-2.5 shadow-xl ${
                loading || uid.trim().length < 4
                  ? 'bg-slate-800/80 text-slate-500 border border-slate-700/40 cursor-not-allowed'
                  : 'bg-gradient-to-r from-purple-600 via-purple-700 to-rose-600 hover:from-purple-500 hover:to-rose-500 text-white shadow-purple-600/30 hover:scale-[1.01] active:scale-[0.99] glow-purple focus-visible:ring-4 focus-visible:ring-purple-500/40 focus-visible:outline-none'
              }`}
            >
              {loading ? (
                <>
                  <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  <span>Connecting to Real-time Stream...</span>
                </>
              ) : (
                <>
                  <span>Access Real-time Safety Console</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </div>

          {/* Quick Demo Previews */}
          <div className="mt-8 pt-6 border-t border-slate-800/80">
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-purple-400" />
              <span>Instant Interactive Test Scenarios:</span>
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              <button
                type="button"
                onClick={() => handleQuickFill(DEMO_UID_CHILD)}
                aria-label={`Test Scenario: Jane Doe with 92% Battery`}
                className="p-3.5 rounded-2xl bg-gradient-to-br from-emerald-950/40 to-slate-900/90 hover:from-emerald-950/60 hover:to-slate-900 border border-emerald-500/40 hover:border-emerald-400 text-left transition group shadow-md focus-visible:ring-2 focus-visible:ring-emerald-400 focus-visible:outline-none"
              >
                <div className="flex items-center justify-between text-xs">
                  <span className="font-extrabold text-white group-hover:text-emerald-300">Jane Doe (Live Radar)</span>
                  <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-ping"></span>
                </div>
                <div className="text-[11px] font-mono text-emerald-400/90 font-bold mt-1">{DEMO_UID_CHILD}</div>
                <div className="text-[10px] text-emerald-300/80 font-medium mt-1">92% DB Battery • 10s Live Sync</div>
              </button>

              <button
                type="button"
                onClick={() => handleQuickFill(DEMO_UID_ACTIVE)}
                aria-label={`Test Scenario: Ananya with 78% Battery`}
                className="p-3.5 rounded-2xl bg-slate-900/70 hover:bg-slate-800/90 border border-slate-800 hover:border-purple-500/50 text-left transition group shadow-md focus-visible:ring-2 focus-visible:ring-purple-400 focus-visible:outline-none"
              >
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-white group-hover:text-purple-300">Ananya (Transit)</span>
                  <span className="w-2.5 h-2.5 rounded-full bg-purple-400 animate-pulse"></span>
                </div>
                <div className="text-[11px] font-mono text-slate-400 mt-1">{DEMO_UID_ACTIVE}</div>
                <div className="text-[10px] text-purple-300/80 font-medium mt-1">78% DB Battery • Normal Metro</div>
              </button>

              <button
                type="button"
                onClick={() => handleQuickFill(DEMO_UID_SOS)}
                aria-label={`Test Scenario: Priya with 21% Battery Critical SOS`}
                className="p-3.5 rounded-2xl bg-slate-900/70 hover:bg-slate-800/90 border border-slate-800 hover:border-rose-500/50 text-left transition group shadow-md focus-visible:ring-2 focus-visible:ring-rose-400 focus-visible:outline-none"
              >
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-white group-hover:text-rose-300">Priya (SOS Alert)</span>
                  <span className="w-2.5 h-2.5 rounded-full bg-red-500 animate-ping"></span>
                </div>
                <div className="text-[11px] font-mono text-slate-400 mt-1">{DEMO_UID_SOS}</div>
                <div className="text-[10px] text-rose-400/90 font-medium mt-1">21% DB Battery • SOS Beacon</div>
              </button>

              <button
                type="button"
                onClick={() => handleQuickFill(DEMO_UID_EXPIRED)}
                aria-label={`Test Scenario: Expired UID Key`}
                className="p-3.5 rounded-2xl bg-slate-900/70 hover:bg-slate-800/90 border border-slate-800 hover:border-amber-500/50 text-left transition group shadow-md focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:outline-none"
              >
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-white group-hover:text-amber-300">Expired Key</span>
                  <span className="w-2.5 h-2.5 rounded-full bg-slate-500"></span>
                </div>
                <div className="text-[11px] font-mono text-slate-400 mt-1">{DEMO_UID_EXPIRED}</div>
                <div className="text-[10px] text-amber-400/90 font-medium mt-1">Rotation Expiry Test</div>
              </button>
            </div>
          </div>

          {/* Security & Architecture Highlights */}
          <div className="mt-8 pt-6 border-t border-slate-800/80">
            <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider mb-3">
              Why Zero-Auth UID Access Protects Both Parties:
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 text-xs text-slate-400">
              <div className="flex items-center gap-2 p-2 rounded-xl bg-slate-900/40 border border-slate-800/60">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>Zero login credentials to hack</span>
              </div>
              <div className="flex items-center gap-2 p-2 rounded-xl bg-slate-900/40 border border-slate-800/60">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>Automatic 7-day key expiry</span>
              </div>
              <div className="flex items-center gap-2 p-2 rounded-xl bg-slate-900/40 border border-slate-800/60">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>Instant user-controlled revocation</span>
              </div>
              <div className="flex items-center gap-2 p-2 rounded-xl bg-slate-900/40 border border-slate-800/60">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>Zero app install for guardians</span>
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="max-w-6xl w-full mx-auto text-center py-4 border-t border-slate-800/60 text-xs text-slate-500 flex flex-col sm:flex-row items-center justify-between gap-3 relative z-10">
        <div className="flex items-center gap-2">
          <ShieldCheck className="w-4 h-4 text-purple-400" />
          <span>Women Safety System v2.4 — Public Guardians Incident Response Console</span>
        </div>
        <div className="flex items-center gap-4 text-[11px] text-slate-400 font-mono">
          <span className="flex items-center gap-1">
            <Globe2 className="w-3.5 h-3.5 text-emerald-400" /> OpenStreetMap Global Nodes
          </span>
          <span>•</span>
          <span>TLS 1.3 Encrypted</span>
        </div>
      </footer>
    </div>
  );
};

export default HomePage;
