import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Shield, 
  LogOut, 
  History, 
  Share2, 
  AlertTriangle, 
  MapPin, 
  PhoneCall, 
  Volume2, 
  VolumeX, 
  Download,
  Clock,
  BatteryMedium,
  BatteryCharging,
  BatteryLow,
  Database
} from 'lucide-react';

import MapContainer from '../components/MapContainer';
import EventFeed from '../components/EventFeed';
import JourneyStatus from '../components/JourneyStatus';
import EmergencyContacts from '../components/EmergencyContacts';
import SafetyScoreCard from '../components/SafetyScoreCard';
import LocationHistoryModal from '../components/LocationHistoryModal';
import ShareModal from '../components/ShareModal';

import { 
  subscribeToLocation, 
  subscribeToLocationHistory,
  subscribeToSOSEvents, 
  subscribeToActiveJourney, 
  subscribeToDatabaseBattery,
  calculateRiskAssessment 
} from '../services/safetyService';
import { soundEffects } from '../utils/audioAlert';
import { reverseGeocode } from '../utils/geocoding';
import { UserData, LocationData, SOSEvent, JourneyData, RiskAssessment } from '../types/safety';
import { MOCK_DATABASE } from '../services/mockData';

const DashboardPage: React.FC = () => {
  const navigate = useNavigate();

  const uid = sessionStorage.getItem('safetyUID');
  const userId = sessionStorage.getItem('userId');
  const initialUserName = sessionStorage.getItem('userName') || 'Protected User';
  const isMockMode = sessionStorage.getItem('isMockMode') === 'true';

  const [userData] = useState<UserData | null>(() => {
    const cached = sessionStorage.getItem('cachedUserData');
    if (cached) {
      try { return JSON.parse(cached); } catch { return null; }
    }
    return null;
  });

  const [currentLocation, setCurrentLocation] = useState<LocationData>(() => {
    const demo = uid ? MOCK_DATABASE[uid] : null;
    return (
      demo?.initialLocation || {
        latitude: 13.0827,
        longitude: 80.2707,
        accuracy: 8,
        timestamp: Date.now(),
        address: 'Main St, Chennai, Tamil Nadu',
        speed: 1.2,
        bearing: 45.0,
        altitude: 10.0,
        status: 'ONLINE',
        isSOSLocation: false,
        batteryLevel: 92,
        networkStatus: '5G'
      }
    );
  });
  const [resolvedAddress, setResolvedAddress] = useState<string>(() => {
    return currentLocation.address || `${currentLocation.latitude.toFixed(5)}° N, ${currentLocation.longitude.toFixed(5)}° E`;
  });
  const [sosEvents, setSOSEvents] = useState<SOSEvent[]>([]);
  const [activeJourney, setActiveJourney] = useState<JourneyData | null>(null);
  const [locationHistory, setLocationHistory] = useState<LocationData[]>(() => {
    const demo = uid ? MOCK_DATABASE[uid] : null;
    return demo?.locationHistory || [currentLocation];
  });
  const [isHistoryModalOpen, setIsHistoryModalOpen] = useState(false);
  const [isShareModalOpen, setIsShareModalOpen] = useState(false);
  const [soundMuted, setSoundMuted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [lastPingTime, setLastPingTime] = useState<Date>(new Date());

  const [mapViewAddress, setMapViewAddress] = useState<string | null>(null);
  const [isCustomMapView, setIsCustomMapView] = useState(false);

  // Redirect if no session exists
  useEffect(() => {
    if (!uid || !userId) {
      navigate('/');
    }
  }, [uid, userId, navigate]);

  // Synchronize reverse-geocoded physical address with current map coordinates
  useEffect(() => {
    if (!currentLocation?.latitude || !currentLocation?.longitude) return;
    let isMounted = true;
    reverseGeocode(currentLocation.latitude, currentLocation.longitude).then((addr) => {
      if (isMounted && addr) {
        setResolvedAddress(addr);
      }
    });
    return () => {
      isMounted = false;
    };
  }, [currentLocation?.latitude, currentLocation?.longitude]);

  const handleMapCenterChange = useCallback((centerLat: number, centerLng: number, isUserLocation: boolean) => {
    if (isUserLocation) {
      setIsCustomMapView(false);
      setMapViewAddress(null);
    } else {
      setIsCustomMapView(true);
      reverseGeocode(centerLat, centerLng).then((addr) => {
        if (addr) setMapViewAddress(addr);
      });
    }
  }, []);

  // Subscribe to real-time location stream
  useEffect(() => {
    if (!userId || !uid) return;

    const unsubscribe = subscribeToLocation(userId, uid, isMockMode, (newLoc) => {
      setCurrentLocation(newLoc);
      setLastPingTime(new Date());
      soundEffects.playPing();

      // Immediately resolve physical address for current map coordinates
      reverseGeocode(newLoc.latitude, newLoc.longitude).then((addr) => {
        if (addr) {
          setResolvedAddress(addr);
        } else if (newLoc.address && newLoc.address.trim().length > 0) {
          setResolvedAddress(newLoc.address);
        }
      }).catch(() => {
        if (newLoc.address && newLoc.address.trim().length > 0) {
          setResolvedAddress(newLoc.address);
        }
      });

      setLocationHistory((prev) => {
        const isDuplicate = prev.length > 0 && 
          prev[0].latitude === newLoc.latitude && 
          prev[0].longitude === newLoc.longitude;
        if (isDuplicate) return prev;
        return [newLoc, ...prev.slice(0, 29)];
      });
      setLoading(false);
    });

    return () => unsubscribe();
  }, [userId, uid, isMockMode]);

  // Subscribe to real-time location history & breadcrumb trail
  useEffect(() => {
    if (!uid) return;
    const unsubscribe = subscribeToLocationHistory(uid, (history) => {
      if (history && history.length > 0) {
        setLocationHistory(history);
      }
    });
    return () => unsubscribe();
  }, [uid]);

  // Subscribe to real-time SOS events
  useEffect(() => {
    if (!userId || !uid) return;

    const unsubscribe = subscribeToSOSEvents(userId, uid, isMockMode, (events) => {
      setSOSEvents(events);
      if (events.some((e) => e.status === 'ACTIVE')) {
        soundEffects.playEmergencySiren();
      }
    });

    return () => unsubscribe();
  }, [userId, uid, isMockMode]);

  // Subscribe to active journey
  useEffect(() => {
    if (!userId || !uid) return;

    const unsubscribe = subscribeToActiveJourney(userId, uid, isMockMode, (journey) => {
      setActiveJourney(journey);
    });

    return () => unsubscribe();
  }, [userId, uid, isMockMode]);

  // Strictly track real-time battery updates through the database
  useEffect(() => {
    if (!uid) return;
    const unsubscribe = subscribeToDatabaseBattery(uid, userId, (batteryInfo) => {
      if (batteryInfo.batteryLevel !== undefined) {
        setCurrentLocation((prev) => ({
          ...prev,
          batteryLevel: batteryInfo.batteryLevel,
          isCharging: batteryInfo.isCharging ?? prev.isCharging,
        }));
      }
    });

    return () => unsubscribe();
  }, [uid, userId]);

  const toggleMute = () => {
    const nextState = !soundMuted;
    setSoundMuted(nextState);
    soundEffects.setMuted(nextState);
  };

  const handleExportGPX = () => {
    if (locationHistory.length === 0) return;
    const gpxPoints = locationHistory
      .map(
        (p) =>
          `<trkpt lat="${p.latitude}" lon="${p.longitude}"><time>${new Date(
            p.timestamp
          ).toISOString()}</time></trkpt>`
      )
      .join('\n      ');

    const gpxContent = `<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="WomenSafetySystem">
  <trk>
    <name>Safety Route for ${userData?.name || initialUserName}</name>
    <trkseg>
      ${gpxPoints}
    </trkseg>
  </trk>
</gpx>`;

    const blob = new Blob([gpxContent], { type: 'application/gpx+xml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `safety_track_${uid}_${Date.now()}.gpx`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleExitSession = () => {
    sessionStorage.clear();
    navigate('/');
  };

  const riskAssessment: RiskAssessment = calculateRiskAssessment(
    currentLocation,
    activeJourney,
    sosEvents
  );

  const activeSOSEvent = sosEvents.find((e) => e.status === 'ACTIVE') || null;

  return (
    <div className="min-h-screen bg-[#070A11] flex flex-col pb-16">
      {/* Top Fixed Operations Bar */}
      <header className="sticky top-0 z-40 bg-[#090D16]/95 backdrop-blur-2xl border-b border-safety-border shadow-xl">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3 flex flex-wrap items-center justify-between gap-3">
          {/* User Badge & Telemetry */}
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-2xl bg-gradient-to-tr from-purple-600 via-purple-500 to-rose-500 p-0.5 shrink-0 shadow-lg shadow-purple-600/20">
              <div className="w-full h-full bg-[#0B0F19] rounded-[14px] flex items-center justify-center">
                <Shield className="w-6 h-6 text-purple-400" />
              </div>
            </div>

            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="text-lg font-extrabold text-white tracking-tight">
                  {userData?.name || initialUserName}
                </h1>
                <span className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping"></span>
                  {loading ? 'CONNECTING...' : 'LIVE STREAM'}
                </span>
                {isMockMode && (
                  <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                    SIMULATOR
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2 text-xs text-slate-400 font-mono mt-0.5 flex-wrap">
                <span>UID: <strong className="text-slate-200">{uid}</strong></span>
                <span>•</span>
                <span className="flex items-center gap-1 text-slate-400">
                  <Clock className="w-3 h-3 text-purple-400" />
                  {lastPingTime.toLocaleTimeString()}
                </span>
                <span>•</span>
                {/* Live Database Battery Badge */}
                <span 
                  className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-bold text-slate-200"
                  title="Exact battery tracked through database"
                >
                  {currentLocation.isCharging ? (
                    <BatteryCharging className="w-3.5 h-3.5 text-emerald-400" />
                  ) : (currentLocation.batteryLevel ?? 100) <= 20 ? (
                    <BatteryLow className="w-3.5 h-3.5 text-rose-400 animate-pulse" />
                  ) : (
                    <BatteryMedium className="w-3.5 h-3.5 text-emerald-400" />
                  )}
                  <span>{currentLocation.batteryLevel !== undefined ? `${currentLocation.batteryLevel}%` : 'Syncing...'}</span>
                  {currentLocation.isCharging && <span className="text-amber-400 text-[10px]">⚡</span>}
                </span>
              </div>
            </div>
          </div>

          {/* Quick Operations Toolbar */}
          <div className="flex items-center gap-2">
            {/* Audio Toggle */}
            <button
              onClick={toggleMute}
              className={`p-2.5 rounded-2xl border transition shadow-md ${
                soundMuted
                  ? 'bg-slate-900 border-slate-800 text-slate-500'
                  : 'bg-purple-600/15 border-purple-500/30 text-purple-300 hover:bg-purple-600/25'
              }`}
              title={soundMuted ? 'Unmute Audio Cues' : 'Mute Audio Cues'}
            >
              {soundMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
            </button>

            {/* Export GPX / Track */}
            <button
              onClick={handleExportGPX}
              className="p-2.5 rounded-2xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-300 hover:text-white transition shadow-md hidden sm:flex"
              title="Download GPS Track (GPX format)"
            >
              <Download className="w-4 h-4 text-purple-400" />
            </button>

            {/* Location History */}
            <button
              onClick={() => setIsHistoryModalOpen(true)}
              className="px-3.5 py-2 rounded-2xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-bold text-slate-300 hover:text-white transition flex items-center gap-1.5 shadow-md"
              title="View GPS Breadcrumbs"
            >
              <History className="w-4 h-4 text-purple-400" />
              <span className="hidden sm:inline">History</span>
            </button>

            {/* Share Portal Modal */}
            <button
              onClick={() => setIsShareModalOpen(true)}
              className="px-3.5 py-2 rounded-2xl bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold transition flex items-center gap-1.5 shadow-lg shadow-purple-600/25 glow-purple"
              title="Share Live Portal Link & QR"
            >
              <Share2 className="w-4 h-4" />
              <span>Share</span>
            </button>

            {/* Disconnect / Exit */}
            <button
              onClick={handleExitSession}
              className="px-3.5 py-2 rounded-2xl bg-rose-600/20 hover:bg-rose-600 text-rose-300 hover:text-white border border-rose-500/30 text-xs font-bold transition flex items-center gap-1.5"
              title="Exit Monitoring Session"
            >
              <LogOut className="w-4 h-4" />
              <span className="hidden sm:inline">Exit</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 mt-6 w-full space-y-6">
        {/* Critical Emergency Banner if SOS is Active */}
        {activeSOSEvent && (
          <div className="p-5 sm:p-7 rounded-3xl bg-gradient-to-r from-red-950 via-rose-950 to-red-900 border-2 border-red-500 shadow-2xl shadow-red-950/80 animate-in fade-in flex flex-col md:flex-row items-start md:items-center justify-between gap-5 glow-red">
            <div className="flex items-start gap-4">
              <div className="w-14 h-14 rounded-2xl bg-red-600 text-white flex items-center justify-center shrink-0 shadow-xl animate-bounce mt-0.5">
                <AlertTriangle className="w-7 h-7" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-xl font-black text-white uppercase tracking-wide">
                    CRITICAL SOS EMERGENCY ACTIVE
                  </h2>
                  <span className="px-2.5 py-0.5 rounded-full bg-red-600 text-white text-[10px] font-extrabold animate-pulse">
                    PRIORITY 1
                  </span>
                </div>
                <p className="text-sm text-red-100 mt-1 leading-relaxed">
                  Triggered {new Date(activeSOSEvent.triggeredAt).toLocaleTimeString()} via{' '}
                  <strong className="text-white underline">{activeSOSEvent.triggerMethod}</strong>. Immediate guardian dispatch recommended.
                </p>
                {activeSOSEvent.address && (
                  <p className="text-xs text-red-200 font-mono mt-1.5 bg-red-950/70 p-2 rounded-xl border border-red-800">
                    📍 Incident Location: {activeSOSEvent.address}
                  </p>
                )}
              </div>
            </div>

            <div className="flex items-center gap-3 w-full md:w-auto shrink-0">
              <a
                href="tel:112"
                className="flex-1 md:flex-initial px-6 py-3.5 rounded-2xl bg-white hover:bg-slate-100 text-red-600 font-extrabold text-sm shadow-2xl transition flex items-center justify-center gap-2"
              >
                <PhoneCall className="w-5 h-5 text-red-600" />
                <span>Call Police (112)</span>
              </a>
            </div>
          </div>
        )}

        {/* 2-Column Responsive Operational Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left Column (2 Cols wide): Map, Live Geocoded Address, Journey Details */}
          <div className="lg:col-span-2 space-y-6">
            {/* OpenStreetMap Map Box */}
            <div className="h-[460px] sm:h-[500px]">
              {currentLocation && (
                <MapContainer
                  latitude={currentLocation.latitude}
                  longitude={currentLocation.longitude}
                  accuracy={currentLocation.accuracy}
                  address={isCustomMapView && mapViewAddress ? mapViewAddress : resolvedAddress}
                  userName={userData?.name || initialUserName}
                  uid={uid || undefined}
                  bearing={currentLocation.bearing}
                  altitude={currentLocation.altitude}
                  status={currentLocation.status}
                  batteryLevel={currentLocation.batteryLevel}
                  isCharging={currentLocation.isCharging}
                  activeSOS={activeSOSEvent}
                  activeJourney={activeJourney}
                  onMapCenterChange={handleMapCenterChange}
                />
              )}
            </div>

            {/* Live Reverse Geocoded Street Address Card */}
            {currentLocation && (
              <div className="glass-panel rounded-3xl p-6 shadow-xl border border-safety-border">
                <div className="flex items-center justify-between pb-3.5 border-b border-safety-border">
                  <div className="flex items-center gap-2.5">
                    <div className="p-2 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
                      <MapPin className="w-4 h-4" />
                    </div>
                    <h3 className="text-sm font-bold text-white uppercase tracking-wider">
                      Live Physical Geolocation
                    </h3>
                  </div>
                  <span className="text-xs text-emerald-400 font-semibold flex items-center gap-1">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                    Reverse Geocoded
                  </span>
                </div>

                <div className="mt-4">
                  <div className="flex items-center justify-between">
                    <p className="text-xs text-slate-400">
                      {isCustomMapView ? 'Current Map View Address' : 'Current Street Address'}
                    </p>
                    {isCustomMapView && (
                      <span className="text-[10px] font-bold text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded-md">
                        Map Panned
                      </span>
                    )}
                  </div>
                  <p className="text-base font-bold text-white mt-1 leading-snug">
                    {isCustomMapView && mapViewAddress
                      ? mapViewAddress
                      : (resolvedAddress || currentLocation.address || 'Resolving physical street address...')}
                  </p>
                </div>

                <div className="mt-4 grid grid-cols-2 sm:grid-cols-4 gap-3 pt-3.5 border-t border-slate-800/80">
                  <div className="p-3 rounded-2xl bg-[#090D16]/60 border border-slate-800">
                    <p className="text-[10px] text-slate-400 uppercase font-mono">Latitude</p>
                    <p className="text-xs font-bold text-slate-200 font-mono mt-0.5">
                      {currentLocation.latitude.toFixed(6)}° N
                    </p>
                  </div>
                  <div className="p-3 rounded-2xl bg-[#090D16]/60 border border-slate-800">
                    <p className="text-[10px] text-slate-400 uppercase font-mono">Longitude</p>
                    <p className="text-xs font-bold text-slate-200 font-mono mt-0.5">
                      {currentLocation.longitude.toFixed(6)}° E
                    </p>
                  </div>
                  <div className="p-3 rounded-2xl bg-[#090D16]/60 border border-slate-800">
                    <p className="text-[10px] text-slate-400 uppercase font-mono">Precision</p>
                    <p className="text-xs font-bold text-emerald-400 font-mono mt-0.5">
                      ±{Math.round(currentLocation.accuracy)} meters
                    </p>
                  </div>
                  <div className="p-3 rounded-2xl bg-[#090D16]/60 border border-slate-800">
                    <p className="text-[10px] text-slate-400 uppercase font-mono flex items-center justify-between">
                      <span>Battery (DB)</span>
                      <Database className="w-3 h-3 text-emerald-400" />
                    </p>
                    <p className="text-xs font-bold text-emerald-400 font-mono mt-0.5 flex items-center gap-1">
                      {currentLocation.batteryLevel !== undefined ? (
                        <>
                          <span>{currentLocation.batteryLevel}%</span>
                          {currentLocation.isCharging && <span className="text-amber-400 text-[10px]">⚡</span>}
                        </>
                      ) : (
                        <span className="text-slate-500">Syncing...</span>
                      )}
                    </p>
                  </div>
                </div>
              </div>
            )}

            {/* Active Commute / Journey Card */}
            {activeJourney && (
              <JourneyStatus journey={activeJourney} />
            )}
          </div>

          {/* Right Column (1 Col wide): Situational Safety Score, Emergency Guardians, Event Feed */}
          <div className="space-y-6">
            {/* Safety Score Meter */}
            <SafetyScoreCard
              assessment={riskAssessment}
              location={currentLocation}
            />

            {/* Emergency Contacts */}
            <EmergencyContacts
              contacts={userData?.emergencyContacts || []}
              userName={userData?.name || initialUserName}
            />

            {/* Real-time Event Feed */}
            <EventFeed events={sosEvents} />
          </div>
        </div>
      </main>

      {/* Historical Breadcrumb Waypoints Modal */}
      <LocationHistoryModal
        isOpen={isHistoryModalOpen}
        onClose={() => setIsHistoryModalOpen(false)}
        history={locationHistory}
        userName={userData?.name || initialUserName}
      />

      {/* Share Modal */}
      <ShareModal
        isOpen={isShareModalOpen}
        onClose={() => setIsShareModalOpen(false)}
        uid={uid || ''}
        userName={userData?.name || initialUserName}
      />
    </div>
  );
};

export default DashboardPage;
