import React from 'react';
import { X, History, MapPin, Clock, Gauge, Download } from 'lucide-react';
import { LocationData } from '../types/safety';

interface LocationHistoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  history: LocationData[];
  userName?: string;
}

const LocationHistoryModal: React.FC<LocationHistoryModalProps> = ({
  isOpen,
  onClose,
  history,
  userName = 'User'
}) => {
  if (!isOpen) return null;

  const handleDownloadCSV = () => {
    if (history.length === 0) return;
    const headers = 'Timestamp,Latitude,Longitude,Accuracy(m),Speed(km/h),Address\n';
    const rows = history
      .map(
        (h) =>
          `"${new Date(h.timestamp).toISOString()}",${h.latitude},${h.longitude},${h.accuracy},${
            h.speed || 0
          },"${(h.address || '').replace(/"/g, '""')}"`
      )
      .join('\n');

    const blob = new Blob([headers + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `safety_history_${userName.replace(/\s+/g, '_')}_${Date.now()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md animate-in fade-in duration-150">
      <div className="relative w-full max-w-2xl bg-[#0D1322] border border-safety-border rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
        {/* Modal Header */}
        <div className="flex items-center justify-between p-6 border-b border-slate-800 bg-[#090D16]/80">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-2xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
              <History className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-lg font-extrabold text-white tracking-tight">
                Breadcrumb Location Trail
              </h3>
              <p className="text-xs text-slate-400">Timestamped GPS Points for {userName}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-white rounded-xl hover:bg-slate-800 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 overflow-y-auto space-y-4">
          {history.length === 0 ? (
            <div className="text-center py-16 text-slate-400 text-sm">
              No historical waypoints recorded in this session yet.
            </div>
          ) : (
            <div className="relative border-l-2 border-slate-800 ml-3.5 space-y-5">
              {history.map((loc, idx) => (
                <div key={idx} className="relative pl-6">
                  {/* Waypoint Dot */}
                  <span
                    className={`absolute -left-[9px] top-2 w-4 h-4 rounded-full border-2 border-[#0D1322] ${
                      idx === 0
                        ? 'bg-purple-500 ring-4 ring-purple-500/30'
                        : 'bg-slate-600'
                    }`}
                  />

                  {/* Waypoint Card */}
                  <div className="p-4 rounded-2xl bg-[#090D16]/90 border border-slate-800/90 hover:border-slate-700 transition shadow-md">
                    <div className="flex items-center justify-between text-xs text-slate-400 mb-1.5">
                      <span className="flex items-center gap-1 font-mono text-slate-300">
                        <Clock className="w-3.5 h-3.5 text-purple-400" />
                        {new Date(loc.timestamp).toLocaleTimeString([], {
                          hour: '2-digit',
                          minute: '2-digit',
                          second: '2-digit',
                        })}{' '}
                        • {new Date(loc.timestamp).toLocaleDateString()}
                      </span>
                      {idx === 0 && (
                        <span className="px-2.5 py-0.5 rounded-md text-[10px] font-black bg-purple-500/20 text-purple-300 border border-purple-500/30 uppercase tracking-wider">
                          Current Pin
                        </span>
                      )}
                    </div>

                    <div className="flex items-start gap-2 mt-2 text-sm font-bold text-white">
                      <MapPin className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                      <span className="leading-snug">
                        {loc.address || `${loc.latitude.toFixed(6)}, ${loc.longitude.toFixed(6)}`}
                      </span>
                    </div>

                    <div className="mt-3 pt-2.5 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400 font-mono">
                      <span>{loc.latitude.toFixed(5)}° N, {loc.longitude.toFixed(5)}° E</span>
                      <span className="flex items-center gap-1">
                        <Gauge className="w-3.5 h-3.5 text-purple-400" />
                        {loc.speed !== undefined ? `${loc.speed} km/h` : '0 km/h'}
                      </span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="p-4 bg-[#090D16]/90 border-t border-slate-800 flex items-center justify-between">
          <button
            onClick={handleDownloadCSV}
            disabled={history.length === 0}
            className="px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 text-xs font-bold transition flex items-center gap-1.5"
          >
            <Download className="w-4 h-4 text-purple-400" />
            <span>Export CSV</span>
          </button>

          <button
            onClick={onClose}
            className="px-6 py-2.5 rounded-xl bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold transition shadow-lg shadow-purple-600/20"
          >
            Close History
          </button>
        </div>
      </div>
    </div>
  );
};

export default LocationHistoryModal;
