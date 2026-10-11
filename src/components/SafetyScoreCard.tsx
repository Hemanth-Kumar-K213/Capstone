import React from 'react';
import { ShieldCheck, ShieldAlert, BatteryCharging, BatteryMedium, BatteryLow, Wifi, Gauge, Moon, Zap } from 'lucide-react';
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

  const getBatteryIcon = (level?: number, isCharging?: boolean) => {
    if (isCharging) return <BatteryCharging className="w-4 h-4 text-emerald-400" />;
    if (level === undefined || level > 50) return <BatteryMedium className="w-4 h-4 text-emerald-400" />;
    if (level > 20) return <BatteryMedium className="w-4 h-4 text-amber-400" />;
    return <BatteryLow className="w-4 h-4 text-red-400 animate-pulse" />;
  };

  return (
    <div className="glass-panel rounded-3xl p-6 shadow-xl border border-safety-border">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-safety-border">
        <div className="flex items-center gap-2.5">
          <div className="p-2.5 rounded-2xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
            {riskLevel === 'HIGH' ? (
              <ShieldAlert className="w-5 h-5 text-red-400 animate-pulse" />
            ) : (
              <ShieldCheck className="w-5 h-5 text-emerald-400" />
            )}
          </div>
          <div>
            <h2 className="text-base font-extrabold text-white">Situational Threat Index</h2>
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

      {/* Live Device Telemetry Matrix */}
      <div className="mt-4 grid grid-cols-3 gap-2.5">
        {/* Battery */}
        <div className="p-3 bg-[#090D16]/60 rounded-2xl border border-slate-800 flex items-center gap-2.5">
          {getBatteryIcon(location?.batteryLevel, location?.isCharging)}
          <div>
            <p className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Battery</p>
            <p className="text-xs font-bold text-slate-200 mt-0.5">
              {location?.batteryLevel !== undefined ? `${location.batteryLevel}%` : '85%'}
              {location?.isCharging && <span className="text-emerald-400 text-[10px] ml-1">⚡</span>}
            </p>
          </div>
        </div>

        {/* Speed */}
        <div className="p-3 bg-[#090D16]/60 rounded-2xl border border-slate-800 flex items-center gap-2.5">
          <Gauge className="w-4 h-4 text-purple-400" />
          <div>
            <p className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Speed</p>
            <p className="text-xs font-bold text-slate-200 mt-0.5">
              {location?.speed !== undefined ? `${location.speed} km/h` : '0 km/h'}
            </p>
          </div>
        </div>

        {/* Network */}
        <div className="p-3 bg-[#090D16]/60 rounded-2xl border border-slate-800 flex items-center gap-2.5">
          <Wifi className="w-4 h-4 text-blue-400" />
          <div>
            <p className="text-[10px] text-slate-400 uppercase font-bold tracking-wider">Signal</p>
            <p className="text-xs font-bold text-slate-200 mt-0.5">
              {location?.networkStatus || '5G Active'}
            </p>
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
