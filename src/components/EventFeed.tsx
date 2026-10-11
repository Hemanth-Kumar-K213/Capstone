import React from 'react';
import { CheckCircle2, ShieldAlert, Radio, Clock, MapPin, Volume2, ShieldCheck } from 'lucide-react';
import { soundEffects } from '../utils/audioAlert';
import { SOSEvent } from '../types/safety';

interface EventFeedProps {
  events: SOSEvent[];
}

const formatTriggerMethod = (method: string) => {
  switch (method) {
    case 'PANIC_BUTTON':
      return '🚨 On-Screen Panic Button';
    case 'VOICE_KEYWORD':
      return '🎙️ Voice Distress Keyword Trigger';
    case 'SHAKE_GESTURE':
      return '📳 Violent Device Shake Gesture';
    case 'FALL_DETECTION':
      return '⚠️ Sudden Fall / Impact Sensor';
    case 'POWER_BUTTON_TRIPLE_PRESS':
      return '🔘 Rapid Power Button Press (3x)';
    case 'SCHEDULED_CHECKIN_MISSED':
      return '⏱️ Missed Safety Check-in Timer';
    default:
      return method;
  }
};

const formatTimeAgo = (timestamp: number) => {
  const diffMinutes = Math.floor((Date.now() - timestamp) / 60000);
  if (diffMinutes < 1) return 'Just now';
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}d ago`;
};

const EventFeed: React.FC<EventFeedProps> = ({ events }) => {
  return (
    <div className="glass-panel rounded-3xl p-6 shadow-xl border border-safety-border flex flex-col">
      {/* Feed Header */}
      <div className="flex items-center justify-between pb-4 border-b border-safety-border">
        <div className="flex items-center gap-2.5">
          <div className="p-2.5 rounded-2xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
            <Radio className="w-5 h-5 animate-pulse" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-extrabold text-white">Incident & Alarm Feed</h2>
              {events.some((e) => e.status === 'ACTIVE') && (
                <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-black bg-red-600 text-white animate-pulse">
                  ACTIVE SOS
                </span>
              )}
            </div>
            <p className="text-xs text-slate-400">Chronological Telemetry Log</p>
          </div>
        </div>

        <button
          onClick={() => soundEffects.playEmergencySiren()}
          className="p-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-400 hover:text-white border border-slate-800 text-xs transition flex items-center gap-1"
          title="Test Siren Sound"
        >
          <Volume2 className="w-3.5 h-3.5" />
          <span className="text-[10px] hidden sm:inline">Test Siren</span>
        </button>
      </div>

      {/* Events List */}
      <div className="mt-4 space-y-3.5 overflow-y-auto max-h-[320px] pr-1">
        {events.length === 0 ? (
          <div className="text-center py-6 px-4 rounded-2xl border border-dashed border-slate-800 bg-[#090D16]/40">
            <ShieldCheck className="w-10 h-10 text-emerald-400/70 mx-auto mb-2" />
            <p className="text-sm font-bold text-slate-200">All Clear — No Incidents</p>
            <p className="text-xs text-slate-500 mt-1 max-w-xs mx-auto leading-relaxed">
              Any panic alarms, sensor triggers, or corridor deviations will broadcast here in real-time.
            </p>
          </div>
        ) : (
          events.map((event) => {
            const isActive = event.status === 'ACTIVE';

            return (
              <div
                key={event.id}
                className={`p-4 rounded-2xl border transition-all ${
                  isActive
                    ? 'bg-red-950/40 border-red-500/70 shadow-lg shadow-red-950/60 ring-1 ring-red-500/40 glow-red'
                    : 'bg-[#090D16]/70 border-slate-800 hover:border-slate-700'
                }`}
              >
                {/* Status & Timestamp Header */}
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2">
                    {isActive ? (
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-black bg-red-600 text-white shadow-md animate-pulse">
                        <ShieldAlert className="w-3.5 h-3.5" />
                        EMERGENCY SOS
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold bg-emerald-950/80 text-emerald-400 border border-emerald-500/30">
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        Resolved
                      </span>
                    )}
                    <span className="text-xs font-bold text-slate-200">
                      {formatTriggerMethod(event.triggerMethod)}
                    </span>
                  </div>

                  <div className="flex items-center gap-1 text-[11px] text-slate-400 shrink-0 font-mono">
                    <Clock className="w-3 h-3" />
                    <span>{formatTimeAgo(event.triggeredAt)}</span>
                  </div>
                </div>

                {/* Event Address or Coordinates */}
                <div className="mt-2.5 flex items-start gap-1.5 text-xs text-slate-300">
                  <MapPin className="w-3.5 h-3.5 text-purple-400 shrink-0 mt-0.5" />
                  <span className="leading-snug font-medium">
                    {event.address || `${event.location.latitude.toFixed(5)}, ${event.location.longitude.toFixed(5)}`}
                  </span>
                </div>

                {/* Event Notes / Context */}
                {event.notes && (
                  <p className="mt-2 text-xs text-slate-300 bg-[#070A11]/80 p-2.5 rounded-xl border border-slate-800 font-mono leading-relaxed">
                    💬 {event.notes}
                  </p>
                )}

                {/* Dispatch Details Bar */}
                <div className="mt-3 pt-2.5 border-t border-slate-800/80 flex items-center justify-between text-[11px]">
                  <span className="text-slate-400">
                    Dispatched to <strong className="text-slate-200">{event.notificationsSent} guardians</strong>
                  </span>
                  <span className="text-slate-500 font-mono">
                    {new Date(event.triggeredAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                  </span>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

export default EventFeed;
