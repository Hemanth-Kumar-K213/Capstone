import React from 'react';
import { Navigation2, AlertOctagon, Car, Footprints, Bus, Clock } from 'lucide-react';
import { JourneyData } from '../types/safety';

interface JourneyStatusProps {
  journey: JourneyData;
}

const JourneyStatus: React.FC<JourneyStatusProps> = ({ journey }) => {
  const isDeviated = journey.deviationDetected;
  const progressPercent = journey.remainingDistance !== undefined && journey.totalDistance > 0
    ? Math.max(5, Math.min(95, Math.round(((journey.totalDistance - journey.remainingDistance) / journey.totalDistance) * 100)))
    : 45;

  const getTravelModeIcon = (mode?: string) => {
    switch (mode) {
      case 'WALKING':
        return <Footprints className="w-4 h-4" />;
      case 'TRANSIT':
        return <Bus className="w-4 h-4" />;
      case 'CAB':
      default:
        return <Car className="w-4 h-4" />;
    }
  };

  return (
    <div className={`rounded-3xl p-6 shadow-xl border transition-all ${
      isDeviated 
        ? 'bg-gradient-to-br from-red-950/70 via-[#0B0F19] to-[#070A11] border-red-500/60 shadow-red-950/50 glow-red' 
        : 'glass-panel border-safety-border'
    }`}>
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-safety-border">
        <div className="flex items-center gap-2.5">
          <div className={`p-2.5 rounded-2xl border ${
            isDeviated 
              ? 'bg-red-500/20 text-red-400 border-red-500/30' 
              : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
          }`}>
            <Navigation2 className="w-5 h-5 animate-pulse" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-extrabold text-white">Live Route & Commute Corridor</h2>
              <span className="flex items-center gap-1 text-[11px] px-2.5 py-0.5 rounded-lg bg-slate-800 text-slate-300 font-bold border border-slate-700">
                {getTravelModeIcon(journey.travelMode)}
                <span>{journey.travelMode || 'CAB'}</span>
              </span>
            </div>
            <p className="text-xs text-slate-400">Continuous Path & Deviation Analysis</p>
          </div>
        </div>

        <span className={`px-3 py-1 rounded-xl text-xs font-black uppercase tracking-wider ${
          journey.status === 'ACTIVE'
            ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
            : 'bg-slate-700 text-slate-300'
        }`}>
          {journey.status}
        </span>
      </div>

      {/* Route Deviation Banner */}
      {isDeviated && (
        <div className="mt-4 p-4 bg-red-600/20 border border-red-500/60 rounded-2xl flex items-start gap-3 shadow-inner">
          <AlertOctagon className="w-5 h-5 text-red-400 shrink-0 mt-0.5 animate-bounce" />
          <div>
            <h4 className="text-sm font-extrabold text-red-300">Route Deviation Detected!</h4>
            <p className="text-xs text-red-200/90 mt-0.5 leading-relaxed">
              Device path has steered beyond the safe corridor towards destination. Geofence sensors triggered.
            </p>
          </div>
        </div>
      )}

      {/* Origin & Destination Grid */}
      <div className="mt-5 grid grid-cols-1 md:grid-cols-2 gap-3.5">
        {/* Source */}
        <div className="p-4 bg-[#090D16]/80 rounded-2xl border border-slate-800">
          <div className="flex items-center gap-1.5 text-xs text-slate-400 mb-1 font-semibold">
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
            <span>Origin Point</span>
          </div>
          <p className="text-sm font-bold text-white truncate" title={journey.source}>
            {journey.source}
          </p>
        </div>

        {/* Destination */}
        <div className="p-4 bg-[#090D16]/80 rounded-2xl border border-slate-800">
          <div className="flex items-center gap-1.5 text-xs text-slate-400 mb-1 font-semibold">
            <span className="w-2.5 h-2.5 rounded-full bg-rose-400"></span>
            <span>Destination</span>
          </div>
          <p className="text-sm font-bold text-white truncate" title={journey.destination}>
            {journey.destination}
          </p>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="mt-5 space-y-1.5">
        <div className="flex justify-between text-xs text-slate-400 font-semibold">
          <span>Corridor Progress</span>
          <span className="font-mono text-purple-400">{progressPercent}% complete</span>
        </div>
        <div className="w-full h-2.5 bg-[#090D16] rounded-full overflow-hidden p-0.5 border border-slate-800">
          <div
            className={`h-full rounded-full transition-all duration-700 ${
              isDeviated 
                ? 'bg-gradient-to-r from-amber-500 to-red-500' 
                : 'bg-gradient-to-r from-purple-500 to-emerald-400'
            }`}
            style={{ width: `${progressPercent}%` }}
          />
        </div>
      </div>

      {/* Distance and ETA Stats */}
      <div className="mt-4 pt-4 border-t border-safety-border grid grid-cols-3 gap-2 text-center">
        <div>
          <p className="text-[11px] text-slate-400 uppercase font-bold">Total Distance</p>
          <p className="text-sm font-extrabold text-white mt-0.5">{journey.totalDistance} km</p>
        </div>

        <div>
          <p className="text-[11px] text-slate-400 uppercase font-bold">Remaining</p>
          <p className="text-sm font-extrabold text-purple-400 mt-0.5">
            {journey.remainingDistance !== undefined ? `${journey.remainingDistance} km` : 'In Transit'}
          </p>
        </div>

        <div>
          <p className="text-[11px] text-slate-400 uppercase font-bold">Estimated ETA</p>
          <p className="text-sm font-extrabold text-emerald-400 mt-0.5 flex items-center justify-center gap-1">
            <Clock className="w-3.5 h-3.5" />
            <span>{journey.estimatedArrival}</span>
          </p>
        </div>
      </div>
    </div>
  );
};

export default JourneyStatus;
