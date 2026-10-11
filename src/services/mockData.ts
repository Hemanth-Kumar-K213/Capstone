import { UserData, WeeklyUIDDoc, LocationData, SOSEvent, JourneyData } from '../types/safety';

export interface MockProfile {
  uidDoc: WeeklyUIDDoc;
  user: UserData;
  initialLocation: LocationData;
  activeJourney?: JourneyData;
  sosEvents: SOSEvent[];
  locationHistory: LocationData[];
}

const now = Date.now();

export const DEMO_UID_ACTIVE = '202501153742';
export const DEMO_UID_CHILD = '202610111234';
export const DEMO_UID_SOS = '202610109999';
export const DEMO_UID_EXPIRED = '202401010000';

export const MOCK_DATABASE: Record<string, MockProfile> = {
  // 0. Dedicated Child Tracker Demo (Matches Android LocationTrackingService 10s sync)
  [DEMO_UID_CHILD]: {
    uidDoc: {
      uid: DEMO_UID_CHILD,
      userId: 'auth_uid_202610111234',
      userName: 'Jane Doe',
      active: true,
      expiresAt: now + (7 * 24 * 60 * 60 * 1000),
      createdAt: now,
      rotationWeek: 41,
    },
    user: {
      userId: 'auth_uid_202610111234',
      name: 'Jane Doe',
      phone: '+91 98401 23456',
      bloodGroup: 'O+',
      emergencyContacts: [
        { name: 'Parent Guardian', phone: '+91 98400 99999', relationship: 'Parent', isPrimary: true },
        { name: '112 Unified Emergency Helpline', phone: '112', relationship: 'Police / Emergency' },
      ],
    },
    initialLocation: {
      latitude: 13.0827,
      longitude: 80.2707,
      accuracy: 8.5,
      speed: 1.2,
      bearing: 45.0,
      altitude: 10.0,
      address: 'Main St, Chennai, Tamil Nadu',
      timestamp: now,
      status: 'ONLINE',
      isSOSLocation: false,
      batteryLevel: 92,
      networkStatus: '5G',
    },
    sosEvents: [],
    locationHistory: [
      { latitude: 13.0815, longitude: 80.2690, accuracy: 8.0, timestamp: now - 30000, speed: 1.4, bearing: 42, address: 'Main St, Chennai' },
      { latitude: 13.0820, longitude: 80.2700, accuracy: 8.2, timestamp: now - 20000, speed: 1.3, bearing: 44, address: 'Main St, Chennai' },
      { latitude: 13.0827, longitude: 80.2707, accuracy: 8.5, timestamp: now - 10000, speed: 1.2, bearing: 45, address: 'Main St, Chennai' },
    ],
  },
  // 1. Regular active tracking user (Traveling on Cab / Metro route)
  [DEMO_UID_ACTIVE]: {
    uidDoc: {
      uid: DEMO_UID_ACTIVE,
      userId: 'usr_ananya_sharma_987',
      userName: 'Ananya Sharma',
      active: true,
      expiresAt: now + (4 * 24 * 60 * 60 * 1000), // expires in 4 days
      createdAt: now - (3 * 24 * 60 * 60 * 1000),
      rotationWeek: 41,
    },
    user: {
      userId: 'usr_ananya_sharma_987',
      name: 'Ananya Sharma',
      phone: '+91 98765 43210',
      email: 'ananya.sharma@example.com',
      bloodGroup: 'O+ Positive',
      emergencyContacts: [
        { name: 'Dr. Ramesh Sharma (Father)', phone: '+91 98111 22334', relationship: 'Father', isPrimary: true },
        { name: 'Sunita Sharma (Mother)', phone: '+91 98222 33445', relationship: 'Mother' },
        { name: 'Pooja Verma (Flatmate)', phone: '+91 98333 44556', relationship: 'Friend' },
        { name: 'National Emergency Response', phone: '112', relationship: 'Police / SOS' },
      ],
    },
    initialLocation: {
      latitude: 12.971598,
      longitude: 77.594562,
      accuracy: 6,
      timestamp: now,
      address: 'MG Road, Ashok Nagar, Bengaluru, Karnataka 560001',
      speed: 24.5,
      altitude: 920,
      batteryLevel: 78,
      isCharging: false,
      networkStatus: '5G',
    },
    activeJourney: {
      id: 'jrn_live_109283',
      userId: 'usr_ananya_sharma_987',
      source: 'Indiranagar Metro Station, Bengaluru',
      destination: 'Electronic City Phase 1, Tech Corridor',
      status: 'ACTIVE',
      totalDistance: 18.4,
      remainingDistance: 8.2,
      estimatedArrival: '28 mins (08:45 PM)',
      deviationDetected: false,
      startTimestamp: now - (25 * 60 * 1000),
      travelMode: 'CAB',
      sourceCoords: [12.9784, 77.6408],
      destinationCoords: [12.8452, 77.6602],
      waypoints: [
        [12.9784, 77.6408],
        [12.9716, 77.5946],
        [12.9352, 77.6245],
        [12.8988, 77.6481],
        [12.8452, 77.6602],
      ],
    },
    sosEvents: [
      {
        id: 'sos_resolved_1',
        userId: 'usr_ananya_sharma_987',
        triggeredAt: now - (3 * 24 * 60 * 60 * 1000),
        location: { latitude: 12.9352, longitude: 77.6245 },
        address: 'Koramangala 4th Block, 80 Feet Road',
        status: 'RESOLVED',
        triggerMethod: 'SHAKE_GESTURE',
        notificationsSent: 3,
        resolvedAt: now - (3 * 24 * 60 * 60 * 1000) + (10 * 60 * 1000),
        notes: 'False alarm test resolved by user PIN',
      }
    ],
    locationHistory: [
      { latitude: 12.9784, longitude: 77.6408, accuracy: 5, timestamp: now - (20 * 60 * 1000), address: 'Indiranagar 100ft Rd', speed: 20 },
      { latitude: 12.9750, longitude: 77.6180, accuracy: 6, timestamp: now - (15 * 60 * 1000), address: 'Ulsoor Lake Junction', speed: 32 },
      { latitude: 12.9716, longitude: 77.5946, accuracy: 6, timestamp: now, address: 'MG Road, Ashok Nagar', speed: 24.5 },
    ]
  },

  // 2. High Alert / Active SOS Demo
  [DEMO_UID_SOS]: {
    uidDoc: {
      uid: DEMO_UID_SOS,
      userId: 'usr_priya_nair_552',
      userName: 'Priya Nair',
      active: true,
      expiresAt: now + (6 * 24 * 60 * 60 * 1000),
      createdAt: now - (1 * 24 * 60 * 60 * 1000),
      rotationWeek: 41,
    },
    user: {
      userId: 'usr_priya_nair_552',
      name: 'Priya Nair',
      phone: '+91 97654 32109',
      emergencyContacts: [
        { name: 'Kavita Nair (Sister)', phone: '+91 97111 88990', relationship: 'Sister', isPrimary: true },
        { name: 'Arjun Menon (Guardian)', phone: '+91 97222 77889', relationship: 'Brother' },
        { name: 'Cyber & Women Helpline', phone: '1091', relationship: 'Emergency' },
      ],
    },
    initialLocation: {
      latitude: 19.0760,
      longitude: 72.8777,
      accuracy: 12,
      timestamp: now,
      address: 'Near Bandra Kurla Complex (BKC), Mumbai, Maharashtra 400051',
      speed: 0,
      batteryLevel: 21,
      isCharging: false,
      networkStatus: '4G',
    },
    activeJourney: {
      id: 'jrn_sos_9921',
      userId: 'usr_priya_nair_552',
      source: 'Andheri West Railway Station',
      destination: 'Bandra Reclamation, Mumbai',
      status: 'ACTIVE',
      totalDistance: 12.1,
      remainingDistance: 4.8,
      estimatedArrival: 'Delayed / Route Deviated',
      deviationDetected: true,
      startTimestamp: now - (45 * 60 * 1000),
      travelMode: 'CAB',
    },
    sosEvents: [
      {
        id: 'sos_active_urgent',
        userId: 'usr_priya_nair_552',
        triggeredAt: now - (2 * 60 * 1000), // 2 mins ago
        location: { latitude: 19.0760, longitude: 72.8777 },
        address: 'Bandra Kurla Complex (BKC) Outer Ring Rd',
        status: 'ACTIVE',
        triggerMethod: 'PANIC_BUTTON',
        notificationsSent: 4,
        batteryLevelAtTrigger: 22,
        notes: 'Emergency SOS activated via Rapid Power Button Press. Location broadcasted to all trusted contacts.',
      }
    ],
    locationHistory: [
      { latitude: 19.1197, longitude: 72.8464, accuracy: 8, timestamp: now - (40 * 60 * 1000), address: 'Andheri West', speed: 15 },
      { latitude: 19.0896, longitude: 72.8656, accuracy: 10, timestamp: now - (20 * 60 * 1000), address: 'Santacruz East', speed: 18 },
      { latitude: 19.0760, longitude: 72.8777, accuracy: 12, timestamp: now, address: 'BKC Outer Ring Rd', speed: 0 },
    ]
  },

  // 3. Expired UID demo
  [DEMO_UID_EXPIRED]: {
    uidDoc: {
      uid: DEMO_UID_EXPIRED,
      userId: 'usr_sneha_patel_110',
      userName: 'Sneha Patel',
      active: true,
      expiresAt: now - (2 * 24 * 60 * 60 * 1000), // Expired 2 days ago
      createdAt: now - (9 * 24 * 60 * 60 * 1000),
      rotationWeek: 40,
    },
    user: {
      userId: 'usr_sneha_patel_110',
      name: 'Sneha Patel',
      phone: '+91 99887 76655',
      emergencyContacts: [],
    },
    initialLocation: {
      latitude: 28.6139,
      longitude: 77.2090,
      accuracy: 10,
      timestamp: now - (2 * 24 * 60 * 60 * 1000),
      address: 'Connaught Place, New Delhi',
    },
    sosEvents: [],
    locationHistory: [],
  }
};
