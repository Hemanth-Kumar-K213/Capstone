/**
 * High-Reliability Dual-Provider Reverse Geocoding Helper
 * Converts latitude & longitude into formatted physical street addresses matching the current map.
 * Uses OpenStreetMap Nominatim with English localization & Indic road transliteration,
 * falling back to BigDataCloud and coordinates.
 */

const geocodeCache = new Map<string, string>();

function cleanKannadaRoad(raw: string): string {
  if (!raw) return '';
  const numMap: Record<string, string> = {
    '೦': '0', '೧': '1', '೨': '2', '೩': '3', '೪': '4',
    '೫': '5', '೬': '6', '೭': '7', '೮': '8', '೯': '9'
  };
  let s = raw;
  for (const [k, v] of Object.entries(numMap)) {
    s = s.split(k).join(v);
  }
  s = s.replace(/(\d+)\s*ನೇ\s*ಅಡ್ಡ\s*ರಸ್ತೆ/gi, (_, p1) => `${p1}th Cross Road`);
  s = s.replace(/(\d+)\s*ನೇ\s*ಮುಖ್ಯ\s*ರಸ್ತೆ/gi, (_, p1) => `${p1}th Main Road`);
  s = s.replace(/ಅಡ್ಡ\s*ರಸ್ತೆ/gi, 'Cross Road');
  s = s.replace(/ಮುಖ್ಯ\s*ರಸ್ತೆ/gi, 'Main Road');
  s = s.replace(/ರಸ್ತೆ/gi, 'Road');
  s = s.replace(/ನಗರ/gi, 'Nagar');
  return s.trim();
}

export async function reverseGeocode(lat: number, lon: number): Promise<string> {
  if (!lat || !lon || isNaN(lat) || isNaN(lon) || (lat === 0 && lon === 0)) {
    return 'Acquiring GPS coordinates...';
  }

  // 5-decimal place resolution (~1 meter precision)
  const key = `${lat.toFixed(5)},${lon.toFixed(5)}`;
  if (geocodeCache.has(key)) {
    return geocodeCache.get(key)!;
  }

  // Provider 1: OpenStreetMap Nominatim (Matches the Leaflet map layer directly)
  try {
    const nomRes = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lon}&zoom=18&addressdetails=1`,
      {
        headers: {
          'Accept-Language': 'en-US,en;q=0.9',
          'User-Agent': 'WomenSafetyDashboard/1.0',
        },
      }
    );

    if (nomRes.ok) {
      const data = await nomRes.json();
      const addr = data.address || {};
      const road = cleanKannadaRoad(addr.road || addr.pedestrian || addr.footway || addr.street || addr.amenity || '');
      const neighbourhood = cleanKannadaRoad(addr.neighbourhood || addr.suburb || addr.residential || addr.subdistrict || '');
      const city = addr.city || addr.town || addr.village || addr.municipality || addr.county || '';
      const state = addr.state || '';

      const parts = [road, neighbourhood, city, state].filter(Boolean);
      const uniqueParts = Array.from(new Set(parts));

      if (uniqueParts.length > 0) {
        const address = uniqueParts.join(', ');
        geocodeCache.set(key, address);
        return address;
      }

      if (data.display_name) {
        const parts = data.display_name.split(', ').slice(0, 4);
        const compactAddress = parts.join(', ');
        geocodeCache.set(key, compactAddress);
        return compactAddress;
      }
    }
  } catch {
    // Fall back to BigDataCloud
  }

  // Provider 2: BigDataCloud Client Reverse Geocoding
  try {
    const bdcRes = await fetch(
      `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${lat}&longitude=${lon}&localityLanguage=en`,
      { headers: { 'Accept': 'application/json' } }
    );
    if (bdcRes.ok) {
      const data = await bdcRes.json();
      const parts = [
        data.locality,
        data.city,
        data.principalSubdivision,
        data.countryName,
      ].filter(Boolean);

      const uniqueParts = Array.from(new Set(parts));
      if (uniqueParts.length > 0) {
        const address = uniqueParts.join(', ');
        geocodeCache.set(key, address);
        return address;
      }
    }
  } catch {
    // Fall back to coordinate string
  }

  const fallback = `${lat.toFixed(6)}° N, ${lon.toFixed(6)}° E`;
  geocodeCache.set(key, fallback);
  return fallback;
}
