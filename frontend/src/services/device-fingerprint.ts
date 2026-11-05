const FINGERPRINT_KEY = 'guardpay.device-fingerprint';
const FINGERPRINT_PATTERN = /^gpf1-[0-9a-f]{8}$/;

function readStored(): string | null {
  try {
    return window.localStorage.getItem(FINGERPRINT_KEY);
  } catch {
    return null;
  }
}

function store(value: string): void {
  try {
    window.localStorage.setItem(FINGERPRINT_KEY, value);
  } catch {
    // Private mode / quota: the fingerprint still works for this session.
  }
}

/** FNV-1a (32-bit): small, dependency-free, and stable across browsers. */
function fnv1a(input: string): string {
  let hash = 0x811c9dc5;
  for (let i = 0; i < input.length; i += 1) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
}

function stableSignals(): string[] {
  const screenInfo =
    typeof window !== 'undefined' && window.screen !== undefined
      ? `${window.screen.width}x${window.screen.height}x${window.screen.colorDepth}`
      : 'no-screen';

  let timezone = 'no-timezone';
  try {
    timezone = Intl.DateTimeFormat().resolvedOptions().timeZone ?? 'no-timezone';
  } catch {
    // Older engines: keep the neutral value.
  }

  const languages =
    typeof navigator !== 'undefined' && Array.isArray(navigator.languages)
      ? navigator.languages.join(',')
      : '';

  return [
    typeof navigator !== 'undefined' ? navigator.userAgent : 'no-ua',
    typeof navigator !== 'undefined' ? navigator.language : 'no-language',
    languages,
    screenInfo,
    timezone,
    typeof navigator !== 'undefined' ? String(navigator.hardwareConcurrency) : 'no-cores',
    typeof navigator !== 'undefined' ? navigator.platform : 'no-platform',
    typeof navigator !== 'undefined' ? String(navigator.maxTouchPoints) : 'no-touch',
  ];
}

/**
 * A stable, non-authoritative device hint. It is stored once and reused so a
 * browser update does not silently create a "new device". The backend treats
 * it only as one piece of evidence and prefers the paired device id.
 */
export function getDeviceFingerprint(): string {
  const stored = readStored();
  if (stored !== null && FINGERPRINT_PATTERN.test(stored)) {
    return stored;
  }
  const fingerprint = `gpf1-${fnv1a(stableSignals().join('|'))}`;
  store(fingerprint);
  return fingerprint;
}
