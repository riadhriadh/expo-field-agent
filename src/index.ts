import type { EventSubscription } from 'expo-modules-core';
import { Image, Platform } from 'react-native';

import nativeModule, { MISSING_NATIVE_MODULE, requireModule } from './FieldAgentModule';
import { normalizeAlert } from './normalize';
import type {
  AlertPayload,
  AlertTrigger,
  BubbleState,
  FieldAgentEventMap,
  FieldAgentStrings,
  FieldAgentEventName,
  FlushResult,
  PermissionName,
  Permissions,
  TrackingOptions,
  TrackingState,
} from './types';

export { AlertHost } from './AlertHost';
export { normalizeAlert } from './normalize';
export * from './types';

const UNSUPPORTED: Permissions = {
  location: 'unsupported',
  backgroundLocation: 'unsupported',
  notifications: 'unsupported',
  overlay: 'unsupported',
  batteryUnrestricted: 'unsupported',
  dndAccess: 'unsupported',
  fullScreenIntent: 'unsupported',
  autostart: 'unsupported',
  notificationAccess: 'unsupported',
};

let warnedAboutExpoGo = false;

function warnOnce(): void {
  if (warnedAboutExpoGo) return;
  warnedAboutExpoGo = true;
  // eslint-disable-next-line no-console
  console.warn(MISSING_NATIVE_MODULE);
}

// --- Permissions -----------------------------------------------------------

export async function getPermissions(): Promise<Permissions> {
  if (!nativeModule) {
    warnOnce();
    return UNSUPPORTED;
  }
  return nativeModule.getPermissions();
}

/**
 * Walks the whole permission ladder in the order Android imposes, including the
 * system screens no API can bypass. Idempotent: anything already granted is
 * skipped rather than re-asked, because a permission refused out of fatigue is
 * never asked again.
 */
export async function requestPermissions(opts?: { skip?: PermissionName[] }): Promise<Permissions> {
  if (!nativeModule) {
    warnOnce();
    return UNSUPPORTED;
  }
  return nativeModule.requestPermissions(opts?.skip ?? []);
}

export async function openSettings(which: PermissionName): Promise<void> {
  return requireModule().openSettings(which);
}

// --- Tracking --------------------------------------------------------------

export async function start(options?: Partial<TrackingOptions>): Promise<void> {
  return requireModule().start(options ?? null);
}

export async function stop(): Promise<void> {
  return requireModule().stop();
}

export async function isRunning(): Promise<boolean> {
  if (!nativeModule) return false;
  return nativeModule.isRunning();
}

/** Stored with the platform keystore; pass null to forget it (logout). */
export async function setAuthHeader(value: string | null): Promise<void> {
  return requireModule().setAuthHeader(value);
}

/** Applied to the running service without restarting it. */
export async function setInterval(seconds: number): Promise<void> {
  if (!Number.isFinite(seconds) || seconds < 1) {
    throw new Error('setInterval attend un nombre de secondes >= 1.');
  }
  return requireModule().setIntervalSeconds(seconds);
}

export async function flush(): Promise<FlushResult> {
  return requireModule().flush();
}

export async function getState(): Promise<TrackingState> {
  if (!nativeModule) {
    return { running: false, queued: 0, lastFixAt: null, lastSentAt: null, lastError: MISSING_NATIVE_MODULE };
  }
  return nativeModule.getState();
}

// --- Bubble ----------------------------------------------------------------

/** Returns false on iOS and whenever SYSTEM_ALERT_WINDOW has not been granted. */
export async function showBubble(): Promise<boolean> {
  if (!nativeModule || Platform.OS !== 'android') return false;
  return nativeModule.showBubble();
}

export async function hideBubble(): Promise<void> {
  if (!nativeModule || Platform.OS !== 'android') return;
  return nativeModule.hideBubble();
}

export async function setBubbleState(state: BubbleState, text?: string): Promise<void> {
  if (!nativeModule || Platform.OS !== 'android') return;
  return nativeModule.setBubbleState(state, text ?? null);
}

/**
 * Swaps the bubble picture while the app runs. `null` puts `bubble.icon` back.
 *
 * Takes a local source only: a `file://` path, an absolute path, a `content://`
 * uri, or the result of `require('./x.png')`. Downloading is the host's job —
 * it owns the auth, the cache and the retry policy, and the bubble has to stay
 * a cheap window.
 */
export async function setBubbleImage(source: string | number | null): Promise<void> {
  if (!nativeModule || Platform.OS !== 'android') return;
  if (source === null || source === undefined) return nativeModule.setBubbleImage(null);

  let uri: string | undefined;
  if (typeof source === 'number') {
    uri = Image.resolveAssetSource(source)?.uri;
    // In a dev build Metro serves bundled assets over http, and native refuses
    // remote sources by design. Bundled images belong in `bubble.icon`, which
    // is a real drawable in every build type.
    if (uri && /^https?:/.test(uri)) {
      throw new Error(
        "setBubbleImage: un require() est servi par Metro en http dans un development build. " +
          "Passe l'image bundlee par `bubble.icon` dans app.json, ou donne un chemin de fichier."
      );
    }
  } else {
    uri = source;
  }

  if (!uri || uri.length === 0) {
    throw new Error('setBubbleImage attend un chemin de fichier, un require(), ou null.');
  }
  return nativeModule.setBubbleImage(uri);
}

// --- Wording ---------------------------------------------------------------

/**
 * Hands the plugin the strings it shows to the driver, in whatever language the
 * app has chosen. Persisted natively, so the service still speaks that language
 * after a reboot, when no JavaScript is running to tell it again.
 *
 * `null` drops every override and goes back to `app.json`. Call it once at
 * startup and again whenever the user changes language — the channels get
 * renamed and the ongoing notification is rebuilt on the spot.
 */
export async function setStrings(values: FieldAgentStrings | null): Promise<void> {
  if (!nativeModule || Platform.OS !== 'android') return;
  if (values === null || values === undefined) return nativeModule.setStrings(null);

  // Undefined entries would cross the bridge as nulls and blank a label; the
  // contract is that an omitted key keeps the configured value.
  const cleaned: Record<string, string> = {};
  for (const [key, value] of Object.entries(values)) {
    if (typeof value === 'string' && value.length > 0) cleaned[key] = value;
  }
  return nativeModule.setStrings(cleaned);
}

// --- Alert -----------------------------------------------------------------

export async function triggerAlert(payload: AlertTrigger): Promise<void> {
  if (!payload || typeof payload.title !== 'string' || payload.title.length === 0) {
    throw new Error('triggerAlert attend un objet { title: string }.');
  }
  return requireModule().triggerAlert(payload);
}

export async function dismissAlert(): Promise<void> {
  return requireModule().dismissAlert();
}

/**
 * Host-side mute. When disabled, no player is created and the alarm volume is
 * left alone — a mute setting that still rings is a lie.
 */
export async function setAlertSound(enabled: boolean): Promise<void> {
  return requireModule().setAlertSound(enabled);
}

/** The alert the native side is holding, if any. Safe to call before mount. */
export function getPendingAlertSync(): AlertPayload | null {
  if (!nativeModule) return null;
  return normalizeAlert(nativeModule.getPendingAlertSync());
}

export async function getPendingAlert(): Promise<AlertPayload | null> {
  return getPendingAlertSync();
}

// --- Events ----------------------------------------------------------------

const NOOP_SUBSCRIPTION: EventSubscription = { remove() {} };

export function addListener<Name extends FieldAgentEventName>(
  name: Name,
  listener: FieldAgentEventMap[Name]
): EventSubscription {
  if (!nativeModule) {
    warnOnce();
    return NOOP_SUBSCRIPTION;
  }
  if (name === 'alert') {
    const wrapped = ((raw: unknown) => {
      const alert = normalizeAlert(raw);
      if (alert) (listener as FieldAgentEventMap['alert'])(alert);
    }) as FieldAgentEventMap[Name];
    return nativeModule.addListener(name, wrapped);
  }
  return nativeModule.addListener(name, listener);
}
