import React from 'react';
import { Phone, MessageSquare, Shield, User, HeartHandshake, PhoneCall, ShieldAlert } from 'lucide-react';
import { EmergencyContact } from '../types/safety';

interface EmergencyContactsProps {
  contacts: EmergencyContact[];
  userName?: string;
}

const EmergencyContacts: React.FC<EmergencyContactsProps> = ({ contacts, userName = 'the user' }) => {
  const cleanPhoneForWhatsApp = (phone: string) => {
    return phone.replace(/[^0-9]/g, '');
  };

  return (
    <div className="glass-panel rounded-3xl p-6 shadow-xl border border-safety-border flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-safety-border">
        <div className="flex items-center gap-2.5">
          <div className="p-2.5 rounded-2xl bg-rose-500/10 text-rose-400 border border-rose-500/20">
            <HeartHandshake className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-extrabold text-white">Designated Guardians & SOS</h2>
            <p className="text-xs text-slate-400">Direct Hotline Lines Configured by {userName}</p>
          </div>
        </div>
      </div>

      {/* Contacts List */}
      <div className="mt-4 space-y-3">
        {contacts && contacts.length > 0 ? (
          contacts.map((contact, idx) => {
            const cleanPhone = cleanPhoneForWhatsApp(contact.phone);
            const isNationalHelpline = contact.phone === '112' || contact.phone === '1091';

            return (
              <div
                key={idx}
                className={`p-4 rounded-2xl border transition-all flex flex-col gap-3 shadow-md ${
                  isNationalHelpline
                    ? 'bg-rose-950/40 border-rose-500/40 ring-1 ring-rose-500/20'
                    : 'bg-[#090D16]/80 border-slate-800/90 hover:border-slate-700'
                }`}
              >
                {/* Contact Header: Avatar + Name + Badges */}
                <div className="flex items-start justify-between gap-2.5">
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className={`w-10 h-10 rounded-2xl flex items-center justify-center shrink-0 shadow-md ${
                        isNationalHelpline
                          ? 'bg-rose-600 text-white shadow-rose-600/30'
                          : 'bg-purple-600/20 text-purple-400 border border-purple-500/30'
                      }`}
                    >
                      {isNationalHelpline ? <ShieldAlert className="w-5 h-5" /> : <User className="w-5 h-5" />}
                    </div>

                    <div className="min-w-0">
                      <div className="flex items-center gap-1.5 flex-wrap">
                        <p className="text-sm font-bold text-white tracking-tight truncate">{contact.name}</p>
                        {contact.isPrimary && (
                          <span className="px-2 py-0.5 rounded-full text-[9px] font-extrabold bg-purple-500/20 text-purple-300 border border-purple-500/30 uppercase tracking-wider">
                            Primary
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-2 mt-0.5">
                        {contact.relationship && (
                          <span className="text-[10px] text-slate-400 bg-slate-800/80 px-2 py-0.5 rounded-md border border-slate-700/50">
                            {contact.relationship}
                          </span>
                        )}
                        <span className="text-xs text-slate-300 font-mono font-medium">{contact.phone}</span>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Direct Action Buttons */}
                <div className="grid grid-cols-2 gap-2 pt-2 border-t border-slate-800/60">
                  {!isNationalHelpline && cleanPhone.length >= 10 ? (
                    <a
                      href={`https://wa.me/${cleanPhone}?text=Hello%2C%20I%20am%20monitoring%20safety%20status%20for%20${encodeURIComponent(userName)}.`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="py-2.5 px-3 rounded-xl bg-emerald-600/15 hover:bg-emerald-600 text-emerald-400 hover:text-white border border-emerald-500/30 transition flex items-center justify-center gap-1.5 text-xs font-bold shadow-sm"
                      title="Direct WhatsApp Message"
                    >
                      <MessageSquare className="w-3.5 h-3.5" />
                      <span>WhatsApp</span>
                    </a>
                  ) : (
                    <div className="py-2.5 px-3 rounded-xl bg-slate-900/60 border border-slate-800 text-slate-500 flex items-center justify-center text-xs font-medium">
                      Helpline
                    </div>
                  )}

                  <a
                    href={`tel:${contact.phone}`}
                    className={`py-2.5 px-3 rounded-xl font-bold text-xs transition flex items-center justify-center gap-1.5 shadow-md ${
                      isNationalHelpline
                        ? 'bg-rose-600 hover:bg-rose-500 text-white shadow-rose-600/30 col-span-2'
                        : 'bg-purple-600 hover:bg-purple-500 text-white shadow-purple-600/30'
                    }`}
                  >
                    <PhoneCall className="w-3.5 h-3.5" />
                    <span>Call Direct</span>
                  </a>
                </div>
              </div>
            );
          })
        ) : (
          <div className="text-center py-8 text-slate-400 text-xs bg-[#090D16]/50 rounded-2xl border border-slate-800">
            No emergency guardians configured in user profile.
          </div>
        )}

        {/* National Emergency 112 Command Card */}
        <div className="mt-2 p-4 bg-gradient-to-r from-red-950/80 to-purple-950/60 rounded-2xl border border-red-500/40 flex items-center justify-between shadow-xl">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-red-600/30 border border-red-500/40 flex items-center justify-center text-red-400 shrink-0">
              <Shield className="w-5 h-5" />
            </div>
            <div>
              <span className="text-sm font-black text-white tracking-wide">112 Unified Emergency</span>
              <p className="text-[11px] text-red-200/80">Police Dispatch, Ambulance & Fire</p>
            </div>
          </div>
          <a
            href="tel:112"
            className="px-4 py-2.5 rounded-xl bg-red-600 hover:bg-red-500 text-white text-xs font-black transition flex items-center gap-1.5 shadow-lg shadow-red-600/30 shrink-0"
          >
            <Phone className="w-3.5 h-3.5" />
            <span>Dial 112</span>
          </a>
        </div>
      </div>
    </div>
  );
};

export default EmergencyContacts;
