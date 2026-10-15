import React from 'react';
import { ShieldCheck, ShieldAlert, BatteryCharging, BatteryMedium, BatteryLow, Wifi, Moon, Zap, Database } from 'lucide-react';
import { RiskAssessment, LocationData } from '../types/safety';

interface SafetyScoreCardProps {
  assessment: RiskAssessment;
  location: LocationData | null;
}

const SafetyScoreCard: React.FC<SafetyScoreCardProps> = ({ assessment, location }) => {
  const { score, riskLevel, statusText, factors } = assessment;

  const getScoreBadge = () => {
    if (score >= 80) {
      return {
        bg: 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400',
        stroke: '#10B981',
        title: 'SECURE',
      };
    }
    if (score >= 60) {
      return {
        bg: 'bg-amber-500/15 border-amber-500/30 text-amber-400',
        stroke: '#F59E0B',
        title: 'CAUTION',
      };
    }
    return {
      bg: 'bg-rose-500/20 border-rose-500/40 text-rose-400 animate-pulse',
      stroke: '#EF4444',
      title: 'THREAT DETECTED',
    };
  };

  const badge = getScoreBadge();

  const getBatteryDetails = () => {
    const level = location?.batteryLevel;
    const isCharging = location?.isCharging;

    if (isCharging) {
      return {
        icon: <BatteryCharging className="w-5 h-5 text-emerald-400 shrink-0" />,
        statusTag: 'CHARGING',
        tagColor: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
        barColor: 'bg-emerald-400',
      };
    }
    if (level === undefined) {
      return {
        icon: <BatteryMedium className="w-5 h-5 text-slate-400 shrink-0" />,
        statusTag: 'SYNCING DB',
        tagColor: 'bg-slate-800 text-slate-300 border-slate-700',
        barColor: 'bg-purple-500',
      };
    }
    if (level > 50) {
      return {
        icon: <BatteryMedium className="w-5 h-5 text-emerald-400 shrink-0" />,
        statusTag: 'OPTIMAL',
        tagColor: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
        barColor: 'bg-emerald-400',
      };
    }
    if (level > 20) {
      return {
        icon: <BatteryMedium className="w-5 h-5 text-amber-400 shrink-0" />,
        statusTag: 'MODERATE',
        tagColor: 'bg-amber-500/15 text-amber-400 border-amber-500/30',
        barColor: 'bg-amber-400',
      };
    }
    return {
      icon: <BatteryLow className="w-5 h-5 text-rose-400 animate-pulse shrink-0" />,
      statusTag: 'CRITICAL LOW',
      tagColor: 'bg-rose-500/20 text-rose-300 border-rose-500/40 animate-pulse',
      barColor: 'bg-rose-500',
    };
  };

  const battery = getBatteryDetails();

  return (
    <div 
      className="glass-panel rounded-3xl p-6 shadow-xl border border-safety-border transition-all duration-300 hover:border-slate-700/80"
      role="region"
      aria-label="Situational Threat Index and Device Telemetry"
    >
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-safety-border">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-2xl bg-purple-500/10 text-purple-400 border border-purple-500/20 shadow-inner">
            {riskLevel === 'HIGH' ? (
              <ShieldAlert className="w-5 h-5 text-red-400 animate-pulse" />
            ) : (
              <ShieldCheck className="w-5 h-5 text-emerald-400" />
            )}
          </div>
          <div>
            <h2 className="text-base font-extrabold text-white tracking-tight">Situational Threat Index</h2>
            <p className="text-xs text-slate-400">Dynamic Multi-Sensor Risk Evaluation</p>
          </div>
        </div>

        <span className={`px-3 py-1 rounded-xl text-xs font-extrabold border uppercase tracking-wider ${badge.bg}`}>
          {badge.title}
        </span>
      </div>

      {/* Main Score Radial Meter */}
      <div className="mt-5 p-4 rounded-2xl bg-[#090D16]/80 border border-slate-800 flex items-center gap-5">
        <div className="relative flex items-center justify-center w-20 h-20 shrink-0">
          <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
            <path
              className="text-slate-800"
              strokeWidth="3.5"
              stroke="currentColor"
              fill="none"
              d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
            />
            <path
              className="transition-all duration-1000 ease-out"
              strokeWidth="3.5"
              strokeDasharray={`${score}, 100`}
              strokeLinecap="round"
              stroke={badge.stroke}
              fill="none"
              d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
            />
          </svg>
          <div className="absolute flex flex-col items-center justify-center">
            <span className="text-2xl font-black text-white">{score}</span>
            <span className="text-[9px] text-slate-400 -mt-1 font-mono">/100</span>
          </div>
        </div>

        <div>
          <h3 className="text-sm font-bold text-white leading-tight">{statusText}</h3>
          <p className="text-xs text-slate-400 mt-1 leading-relaxed">
            Composite score continuous rating derived from GPS vector stability, travel hours, and panic telemetry.
          </p>
        </div>
      </div>

      {/* Live Device Telemetry Matrix (Speed Removed • Strict Database Battery Tracking) */}
      <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-3">
        {/* Strictly Tracked Exact Database Battery */}
        <div 
          className="p-3.5 bg-[#090D16]/80 rounded-2xl border border-slate-800 flex flex-col justify-between shadow-sm relative overflow-hidden group hover:border-slate-700 transition"
          role="status"
          aria-label="Device Battery Telemetry"
        >
          <div className="flex items-center justify-between gap-1.5">
            <div className="flex items-center gap-2 min-w-0">
              {battery.icon}
              <span className="text-[11px] text-slate-300 uppercase font-extrabold tracking-wider truncate">
                Battery Level
              </span>
            </div>
            <span className={`px-2 py-0.5 rounded-lg text-[10px] font-extrabold border uppercase tracking-wider shrink-0 ${battery.tagColor}`}>
              {battery.statusTag}
            </span>
          </div>

          <div className="mt-2.5 flex items-baseline justify-between gap-2">
            <div className="flex items-baseline gap-1.5 min-w-0">
              <span className="text-2xl font-black text-white font-mono tracking-tight">
                {location?.batteryLevel !== undefined ? `${location.batteryLevel}%` : 'Syncing...'}
              </span>
              {location?.isCharging && (
                <span className="text-emerald-400 text-[10px] font-bold shrink-0">⚡ Plugged</span>
              )}
            </div>
            <span className="text-[10px] text-slate-400 font-mono flex items-center gap-1 shrink-0">
              <Database className="w-3 h-3 text-emerald-400" />
              <span>DB Synced</span>
            </span>
          </div>

          {/* Visual Battery Progress Bar */}
          <div 
            className="w-full bg-slate-800/90 rounded-full h-1.5 mt-2.5 overflow-hidden"
            role="progressbar"
            aria-label="Battery Percentage"
            aria-valuenow={location?.batteryLevel ?? 0}
            aria-valuemin={0}
            aria-valuemax={100}
          >
            <div
              className={`h-full transition-all duration-700 rounded-full ${battery.barColor}`}
              style={{ width: `${Math.max(4, Math.min(100, location?.batteryLevel ?? 0))}%` }}
            />
          </div>
        </div>

        {/* Signal & Cloud Real-Time Uplink */}
        <div 
          className="p-3.5 bg-[#090D16]/80 rounded-2xl border border-slate-800 flex flex-col justify-between shadow-sm hover:border-slate-700 transition overflow-hidden"
          role="status"
          aria-label="Connection Uplink Status"
        >
          <div className="flex items-center justify-between gap-1.5">
            <div className="flex items-center gap-2 min-w-0">
              <Wifi className="w-5 h-5 text-blue-400 shrink-0" />
              <span className="text-[11px] text-slate-300 uppercase font-extrabold tracking-wider truncate">
                Cloud Uplink
              </span>
            </div>
            <span className="px-2 py-0.5 rounded-lg text-[10px] font-extrabold bg-blue-500/15 text-blue-300 border border-blue-500/30 uppercase tracking-wider shrink-0">
              {location?.networkStatus || 'ONLINE'}
            </span>
          </div>

          <div className="mt-2.5 flex items-center justify-between gap-2">
            <div className="flex items-center gap-1.5 text-xs font-bold text-slate-200 min-w-0">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse shrink-0"></span>
              <span className="truncate text-slate-200 font-medium">Stream Active</span>
            </div>
            <span className="text-[10px] text-slate-400 font-mono shrink-0">
              Zero-Auth
            </span>
          </div>

          {/* Uplink Heartbeat Indicator */}
          <div className="w-full bg-slate-800/90 rounded-full h-1.5 mt-2.5 overflow-hidden flex">
            <div className="h-full bg-blue-400 w-full animate-pulse opacity-80" />
          </div>
        </div>
      </div>

      {/* Factor Matrix */}
      <div className="mt-4 pt-3.5 border-t border-slate-800/80 flex flex-wrap gap-2 text-xs">
        {factors.nightHours && (
          <span className="flex items-center gap-1.5 px-3 py-1 rounded-xl bg-indigo-950/60 text-indigo-300 border border-indigo-500/30">
            <Moon className="w-3.5 h-3.5" /> Night Hours
          </span>
        )}
        {factors.deviationFromRoute && (
          <span className="flex items-center gap-1.5 px-3 py-1 rounded-xl bg-red-950/60 text-red-300 border border-red-500/40">
            <Zap className="w-3.5 h-3.5" /> Route Deviation
          </span>
        )}
        {factors.batteryLow && (
          <span className="flex items-center gap-1.5 px-3 py-1 rounded-xl bg-amber-950/60 text-amber-300 border border-amber-500/40">
            <BatteryLow className="w-3.5 h-3.5" /> Low Battery Warning
          </span>
        )}
        {!factors.sosTriggered && !factors.deviationFromRoute && (
          <span className="flex items-center gap-1.5 px-3 py-1 rounded-xl bg-emerald-950/60 text-emerald-300 border border-emerald-500/30">
            <ShieldCheck className="w-3.5 h-3.5" /> Geofence Verified
          </span>
        )}
      </div>
    </div>
  );
};

export default SafetyScoreCard;
