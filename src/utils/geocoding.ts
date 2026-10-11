/**
 * High-Reliability Dual-Provider Reverse Geocoding Helper
 * Converts latitude & longitude into formatted physical street addresses.
 * Uses BigDataCloud (Fast, CORS-free, zero-key) with OpenStreetMap Nominatim fallback.
 */

const geocodeCache = new Map<string, string>();

export async function reverseGeocode(lat: number, lon: number): Promise<string> {
  if (!lat || !lon || isNaN(lat) || isNaN(lon) || (lat === 0 && lon === 0)) {
    return 'Acquiring GPS coordinates...';
  }

  const key = `${lat.toFixed(4)},${lon.toFixed(4)}`;
  if (geocodeCache.has(key)) {
    return geocodeCache.get(key)!;
  }

  // Provider 1: BigDataCloud Client Reverse Geocoding (Free, instant, no API key, CORS enabled)
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
        data.countryName
      ].filter(Boolean);

      // De-duplicate parts
      const uniqueParts = Array.from(new Set(parts));
      if (uniqueParts.length > 0) {
        const address = uniqueParts.join(', ');
        geocodeCache.set(key, address);
        return address;
      }
    }
  } catch {
    // Fall back to Nominatim
  }

  // Provider 2: OpenStreetMap Nominatim
  try {
    const nomRes = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lon}&zoom=18&addressdetails=1`,
      {
        headers: {
          'Accept-Language': 'en',
        },
      }
    );

    if (nomRes.ok) {
      const data = await nomRes.json();
      const displayName = data.display_name;
      if (displayName) {
        const parts = displayName.split(', ').slice(0, 4);
        const compactAddress = parts.join(', ');
        geocodeCache.set(key, compactAddress);
        return compactAddress;
      }
    }
  } catch {
    // Fall back to coordinate string
  }

  const fallback = `${lat.toFixed(6)}° N, ${lon.toFixed(6)}° E`;
  geocodeCache.set(key, fallback);
  return fallback;
}

