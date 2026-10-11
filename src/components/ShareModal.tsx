import React, { useState } from 'react';
import { X, Copy, Check, Shield, Share2 } from 'lucide-react';

interface ShareModalProps {
  isOpen: boolean;
  onClose: () => void;
  uid: string;
  userName: string;
}

const ShareModal: React.FC<ShareModalProps> = ({ isOpen, onClose, uid, userName }) => {
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const currentUrl = window.location.href;
  const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(
    currentUrl
  )}&bgcolor=0B0F19&color=8B5CF6`;

  const handleCopy = () => {
    navigator.clipboard.writeText(currentUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-150">
      <div className="relative w-full max-w-md bg-[#0D1322] border border-safety-border rounded-3xl shadow-2xl overflow-hidden p-6 sm:p-8">
        {/* Modal Top */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
              <Share2 className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-white">Share Live Safety Portal</h3>
              <p className="text-xs text-slate-400">UID: {uid}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-white rounded-xl hover:bg-slate-800 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* QR Code Container */}
        <div className="mt-6 flex flex-col items-center justify-center p-6 rounded-2xl bg-[#070A11] border border-slate-800 text-center">
          <div className="p-3 bg-[#0B0F19] rounded-2xl border border-purple-500/30 shadow-xl">
            <img
              src={qrUrl}
              alt="Live Safety Portal QR Code"
              className="w-44 h-44 rounded-xl object-contain"
            />
          </div>
          <p className="text-xs font-semibold text-slate-300 mt-4">
            Scan to instantly monitor {userName}
          </p>
          <p className="text-[11px] text-slate-500 mt-0.5">
            Works on any smartphone browser • No app download required
          </p>
        </div>

        {/* Copy Link Input */}
        <div className="mt-5">
          <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">
            Direct Monitoring Link
          </label>
          <div className="flex items-center gap-2">
            <input
              type="text"
              readOnly
              value={currentUrl}
              className="w-full px-4 py-3 bg-[#070A11] border border-slate-800 rounded-xl text-xs font-mono text-slate-300 focus:outline-none select-all"
            />
            <button
              onClick={handleCopy}
              className={`px-4 py-3 rounded-xl text-xs font-bold transition flex items-center gap-1.5 shrink-0 shadow-lg ${
                copied
                  ? 'bg-emerald-600 text-white'
                  : 'bg-purple-600 hover:bg-purple-500 text-white'
              }`}
            >
              {copied ? (
                <>
                  <Check className="w-4 h-4" />
                  <span>Copied</span>
                </>
              ) : (
                <>
                  <Copy className="w-4 h-4" />
                  <span>Copy</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Footer info */}
        <div className="mt-6 pt-4 border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-500">
          <span className="flex items-center gap-1">
            <Shield className="w-3.5 h-3.5 text-emerald-400" /> End-to-End Encrypted
          </span>
          <button
            onClick={onClose}
            className="text-purple-400 hover:text-purple-300 font-semibold"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};

export default ShareModal;
