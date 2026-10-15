export interface EmergencyContact {
  name: string;
  phone: string;
  relationship: string;
  isPrimary?: boolean;
}

export interface UserData {
  userId: string;
  name: string;
  phone: string;
  email?: string;
  avatarUrl?: string;
  bloodGroup?: string;
  emergencyContacts: EmergencyContact[];
  createdAt?: number;
}

export interface WeeklyUIDDoc {
  uid: string;
  userId: string;
  userName: string;
  active: boolean;
  expiresAt: number; // Unix timestamp in ms
  createdAt: number;
  rotationWeek?: number;
}

export interface LocationData {
  latitude: number;
  longitude: number;
  accuracy: number;
  timestamp: number;
  address?: string;
  speed?: number; // km/h
  bearing?: number; // heading degrees 0-360
  altitude?: number;
  batteryLevel?: number; // 0 - 100
  isCharging?: boolean;
  temperature?: number; // Celsius
  voltage?: number; // mV
  deviceId?: string;
  technology?: string;
  chargingStatus?: number;
  networkStatus?: '5G' | '4G' | 'WiFi' | 'Offline';
  status?: string; // 'ONLINE' | 'OFFLINE'
  isSOSLocation?: boolean;
  lastUpdated?: any;
}

export interface DeviceBatteryStatus {
  userId: string;
  deviceId?: string;
  batteryPercentage?: number;
  latestBattery?: number;
  isCharging?: boolean;
  latestCharging?: boolean;
  chargingStatus?: number; // 0: unknown, 1: AC, 2: USB, 4: Wireless
  latestChargingStatus?: number;
  temperature?: number; // Celsius
  health?: number;
  voltage?: number; // mV
  technology?: string;
  timestamp?: number;
  lastUpdated?: any;
}

export interface ChildLocationDoc {
  uid: string;
  userId?: string;
  userName?: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  speed?: number;
  bearing?: number;
  altitude?: number;
  address?: string;
  timestamp: number;
  lastUpdated?: any;
  status?: string;
  isSOSLocation?: boolean;
}

export type SOSTriggerMethod = 
  | 'PANIC_BUTTON' 
  | 'VOICE_KEYWORD' 
  | 'FALL_DETECTION' 
  | 'SHAKE_GESTURE'
  | 'POWER_BUTTON_TRIPLE_PRESS'
  | 'SCHEDULED_CHECKIN_MISSED';

export interface SOSEvent {
  id: string;
  userId: string;
  triggeredAt: number;
  location: { 
    latitude: number; 
    longitude: number;
  };
  address?: string;
  status: 'ACTIVE' | 'RESOLVED';
  triggerMethod: SOSTriggerMethod;
  notificationsSent: number;
  audioRecordingUrl?: string;
  batteryLevelAtTrigger?: number;
  resolvedAt?: number;
  notes?: string;
}

export interface JourneyData {
  id: string;
  userId: string;
  source: string;
  destination: string;
  status: 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
  totalDistance: number; // in km
  remainingDistance?: number; // in km
  estimatedArrival: string;
  deviationDetected: boolean;
  startTimestamp: number;
  sourceCoords?: [number, number];
  destinationCoords?: [number, number];
  waypoints?: [number, number][];
  travelMode?: 'CAB' | 'WALKING' | 'TRANSIT' | 'DRIVING';
}

export interface RiskAssessment {
  score: number; // 0 - 100 (higher means safer)
  riskLevel: 'LOW' | 'MODERATE' | 'ELEVATED' | 'HIGH';
  statusText: string;
  factors: {
    nightHours: boolean;
    batteryLow: boolean;
    deviationFromRoute: boolean;
    networkPoor: boolean;
    sosTriggered: boolean;
  };
}
