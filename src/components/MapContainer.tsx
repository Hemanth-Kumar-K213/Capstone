import React, { useEffect, useRef, useState, useCallback } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Crosshair, Navigation, AlertTriangle, Maximize, Minimize, ExternalLink, Compass, MapPin, BatteryMedium, BatteryCharging } from 'lucide-react';
import { JourneyData, SOSEvent } from '../types/safety';

interface MapContainerProps {
  latitude: number;
  longitude: number;
  accuracy: number;
  address?: string;
  userName?: string;
  uid?: string;
  bearing?: number;
  altitude?: number;
  status?: string;
  batteryLevel?: number;
  isCharging?: boolean;
  activeSOS?: SOSEvent | null;
  activeJourney?: JourneyData | null;
  onMapCenterChange?: (lat: number, lng: number, isUserLocation: boolean) => void;
}

const MapContainer: React.FC<MapContainerProps> = ({
  latitude,
  longitude,
  accuracy,
  address,
  userName = 'User',
  uid,
  bearing,
  altitude,
  status = 'ONLINE',
  batteryLevel,
  isCharging,
  activeSOS,
  activeJourney,
  onMapCenterChange,
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const markerRef = useRef<L.Marker | null>(null);
  const userVectorDotRef = useRef<L.CircleMarker | null>(null);
  const circleRef = useRef<L.Circle | null>(null);
  const routeLayerRef = useRef<L.Polyline | null>(null);
  const trailLayerRef = useRef<L.Polyline | null>(null);
  const sosIncidentMarkerRef = useRef<L.Marker | null>(null);
  const journeyMarkersRef = useRef<L.Layer[]>([]);

  const [mapReady, setMapReady] = useState(false);
  const [autoCenter, setAutoCenter] = useState(true);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [lastSyncSeconds, setLastSyncSeconds] = useState(0);

  // Sync tick counter for 10-second interval indication
  useEffect(() => {
    setLastSyncSeconds(0);
    const interval = setInterval(() => {
      setLastSyncSeconds((prev) => (prev < 10 ? prev + 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, [latitude, longitude]);

  // Initialize Leaflet Map with OpenStreetMap Standard
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const initialLat = (latitude && !isNaN(latitude)) ? latitude : 12.9716;
    const initialLng = (longitude && !isNaN(longitude)) ? longitude : 77.5946;

    const map = L.map(mapContainerRef.current, {
      center: [initialLat, initialLng],
      zoom: 16,
      zoomControl: false,
      attributionControl: false,
    });

    L.control.zoom({ position: 'bottomright' }).addTo(map);

    // Reliable OpenStreetMap Standard Tile Layer
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
      subdomains: ['a', 'b', 'c'],
    }).addTo(map);

    mapInstanceRef.current = map;
    setMapReady(true);

    // Invalidate map size multiple times to ensure full container coverage
    const timers = [50, 150, 400, 1000].map(delay =>
      setTimeout(() => {
        if (mapInstanceRef.current) {
          mapInstanceRef.current.invalidateSize();
        }
      }, delay)
    );

    map.on('dragstart', () => {
      setAutoCenter(false);
    });

    map.on('moveend', () => {
      if (onMapCenterChange && mapInstanceRef.current) {
        const center = mapInstanceRef.current.getCenter();
        const dist = center.distanceTo(L.latLng(latitude || 0, longitude || 0));
        onMapCenterChange(center.lat, center.lng, dist < 25);
      }
    });

    return () => {
      timers.forEach(clearTimeout);
      map.remove();
      mapInstanceRef.current = null;
      setMapReady(false);
    };
  }, []);

  // Update Live Marker, Lively Radar Pulse Overlay, and Accuracy Circle
  const updateMapMarker = useCallback(() => {
    const map = mapInstanceRef.current;
    if (!map || !mapReady) return;
    if (!latitude || !longitude || isNaN(latitude) || isNaN(longitude) || (latitude === 0 && longitude === 0)) {
      return;
    }

    const position: L.LatLngTuple = [latitude, longitude];
    const isSOS = Boolean(activeSOS && activeSOS.status === 'ACTIVE');

    // Smooth Camera Gliding: Glides to new coordinates without screen jumping
    if (autoCenter) {
      const currentCenter = map.getCenter();
      const distance = currentCenter.distanceTo(L.latLng(latitude, longitude));
      if (distance > 3000) {
        map.flyTo(position, 16, { duration: 1.5 });
      } else {
        map.panTo(position, { animate: true, duration: 0.9, easeLinearity: 0.25 });
      }
    }

    // Lively Radar Marker HTML matching Android LivelyPulseOverlay specifications
    const headingDegrees = bearing !== undefined ? Math.round(bearing) : undefined;
    const markerHtml = `
      <div class="lively-radar-wrapper">
        ${headingDegrees !== undefined ? `
          <div class="lively-orientation-cone" style="transform: rotate(${headingDegrees}deg);">
            <svg viewBox="0 0 110 110" width="110" height="110">
              <defs>
                <radialGradient id="orientationConeGrad" cx="50%" cy="50%" r="50%">
                  <stop offset="0%" stop-color="${isSOS ? '#EF4444' : '#10B981'}" stop-opacity="0.65" />
                  <stop offset="65%" stop-color="${isSOS ? '#EF4444' : '#10B981'}" stop-opacity="0.2" />
                  <stop offset="100%" stop-color="${isSOS ? '#EF4444' : '#10B981'}" stop-opacity="0" />
                </radialGradient>
              </defs>
              <path d="M 55 55 L 26 5 A 55 55 0 0 1 84 5 Z" fill="url(#orientationConeGrad)" />
              <polygon points="55,8 49,20 61,20" fill="${isSOS ? '#EF4444' : '#10B981'}" stroke="#FFFFFF" stroke-width="1.5" />
            </svg>
          </div>
        ` : ''}

        <!-- Continuous 60fps expanding dual sonar radar waves -->
        <div class="lively-sonar-wave wave-1 ${isSOS ? 'sos-wave' : 'emerald-wave'}"></div>
        <div class="lively-sonar-wave wave-2 ${isSOS ? 'sos-wave' : 'emerald-wave'}"></div>

        <!-- Radiant emerald aura halo (#10B981) -->
        <div class="lively-emerald-halo ${isSOS ? 'sos-halo' : ''}"></div>

        <!-- High-visibility central beacon with crisp white ring & glint reflection dot -->
        <div class="lively-beacon-anchor">
          <div class="lively-beacon-core ${isSOS ? 'sos-core' : ''}">
            <div class="lively-glint-dot"></div>
          </div>
        </div>

        <!-- Live Radar Badge Overlay -->
        <div class="lively-radar-badge ${isSOS ? 'sos-badge' : ''}">
          <span class="lively-pulse-dot ${isSOS ? 'sos-dot' : ''}"></span>
          <span>LIVE RADAR • 10s SYNC</span>
          ${uid ? `<span style="opacity: 0.8; font-family: monospace; border-left: 1px solid rgba(255,255,255,0.25); padding-left: 4px;">${uid}</span>` : ''}
        </div>
      </div>
    `;

    const customIcon = L.divIcon({
      html: markerHtml,
      className: 'safety-pin-leaflet-icon',
      iconSize: [120, 120],
      iconAnchor: [60, 60],
      popupAnchor: [0, -35],
    });

    const popupContent = `
      <div style="font-family: system-ui, -apple-system, sans-serif; padding: 6px; min-width: 240px;">
        <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px;">
          <span style="font-weight: 800; color: #0F172A; font-size: 14px;">📍 ${userName}</span>
          <span style="font-size: 10px; font-weight: 800; padding: 2px 7px; border-radius: 9999px; background: ${isSOS ? '#FEE2E2; color: #DC2626' : '#ECFDF5; color: #059669'}; border: 1px solid ${isSOS ? '#FECACA' : '#A7F3D0'};">
            ${isSOS ? '🚨 SOS ACTIVE' : 'LIVE RADAR 10s'}
          </span>
        </div>
        ${uid ? `<div style="font-size: 11px; font-family: monospace; color: #7C3AED; font-weight: 700; margin-bottom: 4px;">UID: ${uid}</div>` : ''}
        ${address ? `<div style="font-size: 11px; font-weight: 700; color: #059669; margin-bottom: 4px; line-height: 1.3;">📍 ${address}</div>` : ''}
        <div style="font-size: 10px; color: #64748B; font-family: monospace; margin-bottom: 6px;">
          ${latitude.toFixed(6)}° N, ${longitude.toFixed(6)}° E
        </div>
        <div style="padding-top: 6px; border-top: 1px solid #E2E8F0; font-size: 11px; color: #334155; display: grid; grid-template-columns: 1fr 1fr; gap: 4px;">
          <span>Accuracy: <b>±${Math.round(accuracy || 8)}m</b></span>
          <span>Battery: <b style="color: #059669;">${batteryLevel !== undefined ? `${batteryLevel}%` : 'Syncing...'}</b></span>
          ${headingDegrees !== undefined ? `<span>Bearing: <b>${headingDegrees}°</b></span>` : ''}
          ${altitude !== undefined ? `<span>Altitude: <b>${Math.round(altitude)}m</b></span>` : ''}
        </div>
        <div style="margin-top: 8px; padding-top: 6px; border-top: 1px solid #E2E8F0; display: flex; gap: 6px;">
          <a href="https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=17/${latitude}/${longitude}" target="_blank" rel="noopener noreferrer" style="flex: 1; text-align: center; background: #059669; color: white; padding: 4px 8px; border-radius: 6px; font-size: 10px; font-weight: 700; text-decoration: none;">
            OpenStreetMap
          </a>
          <a href="https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}" target="_blank" rel="noopener noreferrer" style="flex: 1; text-align: center; background: #3B82F6; color: white; padding: 4px 8px; border-radius: 6px; font-size: 10px; font-weight: 700; text-decoration: none;">
            Google Maps
          </a>
        </div>
      </div>
    `;

    // 1. Vector Anchor Dot
    if (!userVectorDotRef.current) {
      userVectorDotRef.current = L.circleMarker(position, {
        radius: 6,
        fillColor: isSOS ? '#DC2626' : '#10B981',
        color: '#FFFFFF',
        weight: 2.5,
        fillOpacity: 1,
      }).addTo(map);
    } else {
      userVectorDotRef.current.setLatLng(position);
      userVectorDotRef.current.setStyle({
        fillColor: isSOS ? '#DC2626' : '#10B981',
      });
    }

    // 2. Custom Lively Radar Marker
    if (!markerRef.current) {
      markerRef.current = L.marker(position, {
        icon: customIcon,
        zIndexOffset: 10000,
      })
        .bindPopup(popupContent)
        .addTo(map);
    } else {
      markerRef.current.setLatLng(position);
      markerRef.current.setIcon(customIcon);
      markerRef.current.setZIndexOffset(10000);
      markerRef.current.setPopupContent(popupContent);
    }

    // 3. Accuracy Radius Circle
    const circleColor = isSOS ? '#EF4444' : '#10B981';
    const radiusMeters = Math.max(accuracy || 10, 15);
    if (!circleRef.current) {
      circleRef.current = L.circle(position, {
        radius: radiusMeters,
        color: circleColor,
        fillColor: circleColor,
        fillOpacity: isSOS ? 0.3 : 0.12,
        weight: 1.5,
        dashArray: '4, 6',
      }).addTo(map);
    } else {
      circleRef.current.setLatLng(position);
      circleRef.current.setRadius(radiusMeters);
      circleRef.current.setStyle({
        color: circleColor,
        fillColor: circleColor,
        fillOpacity: isSOS ? 0.3 : 0.12,
      });
    }

    // 4. Remove any extra trail/breadcrumb line extending from the marker
    if (trailLayerRef.current) {
      map.removeLayer(trailLayerRef.current);
      trailLayerRef.current = null;
    }

    // 5. SOS Ground-Zero Incident Marker
    if (activeSOS && activeSOS.location) {
      const sosCoord: L.LatLngTuple = [activeSOS.location.latitude, activeSOS.location.longitude];
      if (!sosIncidentMarkerRef.current) {
        const sosIncidentIcon = L.divIcon({
          html: `
            <div style="background: #DC2626; color: white; width: 34px; height: 34px; border-radius: 50%; display: flex; align-items: center; justify-content: center; border: 2.5px solid #FFFFFF; box-shadow: 0 0 20px #EF4444; font-size: 16px;">
              🚨
            </div>
          `,
          className: '',
          iconSize: [34, 34],
          iconAnchor: [17, 17],
        });

        sosIncidentMarkerRef.current = L.marker(sosCoord, { icon: sosIncidentIcon })
          .bindPopup(`
            <div style="font-family: system-ui; padding: 4px;">
              <b style="color: #DC2626;">🚨 SOS Ground Zero</b><br/>
              <span style="font-size: 11px; color: #64748B;">Triggered: ${new Date(activeSOS.triggeredAt).toLocaleTimeString()}</span><br/>
              <span style="font-size: 11px; color: #334155;">Method: ${activeSOS.triggerMethod}</span>
            </div>
          `)
          .addTo(map);
      } else {
        sosIncidentMarkerRef.current.setLatLng(sosCoord);
      }
    } else if (sosIncidentMarkerRef.current) {
      map.removeLayer(sosIncidentMarkerRef.current);
      sosIncidentMarkerRef.current = null;
    }
  }, [latitude, longitude, accuracy, activeSOS, userName, uid, bearing, batteryLevel, isCharging, altitude, autoCenter, mapReady, address]);

  // Execute marker update on props change or map ready
  useEffect(() => {
    updateMapMarker();
  }, [updateMapMarker]);

  // Render Journey Route Polylines
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map || !mapReady) return;

    if (routeLayerRef.current) {
      map.removeLayer(routeLayerRef.current);
      routeLayerRef.current = null;
    }
    journeyMarkersRef.current.forEach((m) => map.removeLayer(m));
    journeyMarkersRef.current = [];

    if (activeJourney && activeJourney.waypoints && activeJourney.waypoints.length > 1) {
      const polyline = L.polyline(activeJourney.waypoints, {
        color: activeJourney.deviationDetected ? '#EF4444' : '#10B981',
        weight: 4.5,
        opacity: 0.9,
        dashArray: activeJourney.deviationDetected ? '8, 8' : undefined,
      }).addTo(map);

      routeLayerRef.current = polyline;

      const startCoord = activeJourney.waypoints[0];
      const startMarker = L.circleMarker(startCoord, {
        radius: 6,
        fillColor: '#10B981',
        color: '#FFFFFF',
        weight: 2,
        fillOpacity: 1,
      }).bindPopup(`<b>Journey Start:</b> ${activeJourney.source}`).addTo(map);

      const endCoord = activeJourney.waypoints[activeJourney.waypoints.length - 1];
      const endMarker = L.circleMarker(endCoord, {
        radius: 6,
        fillColor: '#F43F5E',
        color: '#FFFFFF',
        weight: 2,
        fillOpacity: 1,
      }).bindPopup(`<b>Destination:</b> ${activeJourney.destination}`).addTo(map);

      journeyMarkersRef.current = [startMarker, endMarker];
    }
  }, [activeJourney, mapReady]);

  const handleRecenter = () => {
    setAutoCenter(true);
    if (mapInstanceRef.current && latitude && longitude && !isNaN(latitude) && !isNaN(longitude)) {
      mapInstanceRef.current.setView([latitude, longitude], 16, { animate: true });
      if (onMapCenterChange) {
        onMapCenterChange(latitude, longitude, true);
      }
    }
  };

  const toggleFullscreen = () => {
    setIsFullscreen((prev) => !prev);
    setTimeout(() => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.invalidateSize();
      }
    }, 250);
  };

  return (
    <div className={`relative rounded-3xl overflow-hidden shadow-2xl border border-safety-border bg-slate-950 transition-all ${
      isFullscreen ? 'fixed inset-4 z-50 rounded-2xl h-[calc(100vh-32px)]' : 'w-full h-full'
    }`}>
      {/* Leaflet Map Canvas */}
      <div ref={mapContainerRef} className="w-full h-full z-0" />

      {/* Floating Status Stream Banner */}
      <div className="absolute top-4 left-4 z-10 flex flex-wrap gap-2 pointer-events-none">
        <div className="flex items-center gap-2.5 bg-[#090D16]/95 backdrop-blur-md px-4 py-2.5 rounded-2xl border border-slate-700/80 shadow-2xl text-xs font-semibold pointer-events-auto">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-ping"></span>
          <span className="text-white tracking-wide font-extrabold flex items-center gap-1.5">
            <span className="text-emerald-400">LIVE RADAR</span>
            <span className="text-slate-400">•</span>
            <span className="text-slate-200">10s SYNC</span>
          </span>
          {uid && (
            <span className="text-purple-300 font-mono text-[11px] bg-purple-500/15 px-2 py-0.5 rounded-lg border border-purple-500/30">
              {uid}
            </span>
          )}
          <span className="text-emerald-400 font-mono text-[11px] border-l border-slate-700 pl-2">
            tick in {10 - lastSyncSeconds}s
          </span>
          <span className="text-slate-400 font-mono text-[11px] border-l border-slate-700 pl-2">
            ±{Math.round(accuracy || 8)}m
          </span>
        </div>

        {activeSOS && activeSOS.status === 'ACTIVE' && (
          <div className="flex items-center gap-2 bg-red-600/95 text-white px-4 py-2 rounded-2xl shadow-xl text-xs font-bold animate-bounce ring-2 ring-red-400/50 pointer-events-auto">
            <AlertTriangle className="w-4 h-4" />
            <span>SOS BEACON ACTIVE</span>
          </div>
        )}
      </div>

      {/* Top Right Tool Action Bar */}
      <div className="absolute top-4 right-4 z-10 flex items-center gap-2">
        {/* Recenter Button */}
        <button
          onClick={handleRecenter}
          className={`p-2.5 rounded-2xl backdrop-blur-md border shadow-xl transition flex items-center justify-center ${
            autoCenter
              ? 'bg-purple-600 text-white border-purple-500 shadow-purple-500/30 ring-2 ring-purple-400/50'
              : 'bg-[#090D16]/90 text-slate-300 border-slate-700/80 hover:text-white hover:bg-slate-800'
          }`}
          title="Recenter & Snap Camera onto Live User Location"
        >
          <Crosshair className="w-4 h-4" />
        </button>

        {/* Open in OpenStreetMap */}
        <a
          href={`https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=17/${latitude}/${longitude}`}
          target="_blank"
          rel="noopener noreferrer"
          className="p-2.5 rounded-2xl bg-[#090D16]/90 backdrop-blur-md border border-slate-700/80 text-emerald-400 hover:text-white hover:bg-emerald-600/30 shadow-xl transition flex items-center justify-center"
          title="Open in OpenStreetMap"
        >
          <Compass className="w-4 h-4" />
        </a>

        {/* Open in Google Maps */}
        <a
          href={`https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}`}
          target="_blank"
          rel="noopener noreferrer"
          className="p-2.5 rounded-2xl bg-[#090D16]/90 backdrop-blur-md border border-slate-700/80 text-blue-400 hover:text-white hover:bg-blue-600/30 shadow-xl transition flex items-center justify-center"
          title="Open in Google Maps"
        >
          <ExternalLink className="w-4 h-4" />
        </a>

        {/* Fullscreen Toggle */}
        <button
          onClick={toggleFullscreen}
          className="p-2.5 rounded-2xl bg-[#090D16]/90 backdrop-blur-md border border-slate-700/80 text-slate-300 hover:text-white hover:bg-slate-800 shadow-xl transition flex items-center justify-center"
          title={isFullscreen ? 'Exit Fullscreen' : 'Expand Map'}
        >
          {isFullscreen ? <Minimize className="w-4 h-4" /> : <Maximize className="w-4 h-4" />}
        </button>
      </div>

      {/* Bottom Coordinates & Telemetry Bar */}
      <div className="absolute bottom-4 left-4 right-4 sm:right-auto z-10 flex flex-wrap items-center gap-3 bg-[#090D16]/95 backdrop-blur-md px-4 py-2.5 rounded-2xl border border-slate-700/80 text-xs text-slate-300 font-mono shadow-2xl">
        <div className="flex items-center gap-1.5 text-purple-400 shrink-0">
          <Navigation className="w-4 h-4 shrink-0" />
          <span className="text-slate-200">
            {latitude ? latitude.toFixed(6) : '0.000000'}° N, {longitude ? longitude.toFixed(6) : '0.000000'}° E
          </span>
        </div>
        {address && (
          <div className="flex items-center gap-1.5 text-emerald-300 border-l border-slate-700 pl-3 font-sans font-medium text-xs max-w-[260px] sm:max-w-[420px] truncate" title={address}>
            <MapPin className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
            <span className="truncate">{address}</span>
          </div>
        )}
        {batteryLevel !== undefined && (
          <div className="flex items-center gap-1.5 text-emerald-300 border-l border-slate-700 pl-3 font-mono text-xs">
            {isCharging ? (
              <BatteryCharging className="w-3.5 h-3.5 text-emerald-400" />
            ) : (
              <BatteryMedium className="w-3.5 h-3.5 text-emerald-400" />
            )}
            <span>{batteryLevel}% Battery</span>
          </div>
        )}
        {bearing !== undefined && (
          <div className="flex items-center gap-1 text-slate-400 border-l border-slate-700 pl-3">
            <Compass className="w-3.5 h-3.5 text-emerald-400" />
            <span>{Math.round(bearing)}° heading</span>
          </div>
        )}
        {status && (
          <div className="flex items-center gap-1.5 text-emerald-400 border-l border-slate-700 pl-3 font-sans font-bold text-[11px]">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
            <span>{status}</span>
          </div>
        )}
      </div>
    </div>
  );
};

export default MapContainer;

