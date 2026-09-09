import fs from 'fs';
import path from 'path';

/**
 * Schema, defaults and validation of the `app.json` props.
 *
 * Rule of the house: a missing or malformed value NEVER fails the build. It
 * produces a readable warning and falls back to a sane default, because a
 * prebuild that dies on a typo in a colour is a plugin nobody keeps.
 */

export type TrackingProps = {
  /** POST target for a single position. Tracking refuses to start without it. */
  url?: string;
  /** Optional POST target for a replayed batch. Falls back to `url` one by one. */
  batchUrl?: string;
  intervalSeconds?: number;
  idleIntervalSeconds?: number;
  distanceFilterMeters?: number;
  batchSize?: number;
  queueSize?: number;
  /**
   * "I am still here" beat. Even motionless, a point is sent at least this
   * often, stamped now, bypassing the distance filter — otherwise a server that
   * judges freshness declares the agent missing.
   */
  heartbeatSeconds?: number;
};

export type NotificationProps = {
  channelName?: string;
  title?: string;
  body?: string;
  /** Monochrome 24dp PNG with alpha. Anything else renders as a white square. */
  icon?: string;
  color?: string;
};

export type AlertProps = {
  /** Plain string or regex source, matched case-insensitively against the title. */
  titlePattern?: string;
  /** Copied into res/raw and stored uncompressed. See README, piege 4. */
  sound?: string;
  channelName?: string;
  route?: string;
  ttlSeconds?: number;
  torch?: boolean;
  /** Bump this when channel attributes must be re-created on already-installed devices. */
  channelVersion?: number;
};

export type BubbleProps = {
  icon?: string;
  label?: string;
  colors?: { ok?: string; warn?: string; bad?: string; urgent?: string };
};

export type IosProps = {
  locationWhenInUsePermission?: string;
  locationAlwaysPermission?: string;
  /** Only useful if Apple granted the Critical Alerts entitlement. */
  criticalAlerts?: boolean;
};

export type FieldAgentPluginProps = {
  tracking?: TrackingProps;
  notification?: NotificationProps;
  alert?: AlertProps;
  bubble?: BubbleProps;
  ios?: IosProps;
  /**
   * Root component name registered with AppRegistry. `registerRootComponent`
   * and expo-router both register "main"; only change this if you registered
   * something else yourself.
   */
  rootComponent?: string;
};

export type ResolvedProps = {
  tracking: {
    url: string | null;
    batchUrl: string | null;
    intervalSeconds: number;
    idleIntervalSeconds: number;
    distanceFilterMeters: number;
    batchSize: number;
    queueSize: number;
    heartbeatSeconds: number;
  };
  notification: {
    channelName: string;
    title: string;
    body: string;
    icon: string | null;
    color: string;
  };
  alert: {
    titlePattern: string;
    sound: string | null;
    channelName: string;
    route: string;
    ttlSeconds: number;
    torch: boolean;
    channelVersion: number;
  };
  bubble: {
    icon: string | null;
    label: string;
    colors: { ok: string; warn: string; bad: string; urgent: string };
  };
  ios: {
    locationWhenInUsePermission: string;
    locationAlwaysPermission: string;
    criticalAlerts: boolean;
  };
  rootComponent: string;
};

const TAG = '[expo-field-agent]';

export function warn(message: string): void {
  // eslint-disable-next-line no-console
  console.warn(TAG + ' ' + message);
}

const HEX = /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/;

function num(value: unknown, fallback: number, key: string, min = 0): number {
  if (value === undefined || value === null) return fallback;
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    warn(key + ' doit etre un nombre, recu ' + JSON.stringify(value) + ' — defaut ' + fallback + ' utilise.');
    return fallback;
  }
  if (value < min) {
    warn(key + ' doit valoir au moins ' + min + ', recu ' + value + ' — defaut ' + fallback + ' utilise.');
    return fallback;
  }
  return value;
}

function str(value: unknown, fallback: string, key: string): string {
  if (value === undefined || value === null) return fallback;
  if (typeof value !== 'string' || value.length === 0) {
    warn(key + ' doit etre une chaine non vide — defaut "' + fallback + '" utilise.');
    return fallback;
  }
  return value;
}

function bool(value: unknown, fallback: boolean, key: string): boolean {
  if (value === undefined || value === null) return fallback;
  if (typeof value !== 'boolean') {
    warn(key + ' doit etre un booleen — defaut ' + fallback + ' utilise.');
    return fallback;
  }
  return value;
}

function color(value: unknown, fallback: string, key: string): string {
  if (value === undefined || value === null) return fallback;
  if (typeof value !== 'string' || !HEX.test(value)) {
    warn(key + ' doit etre une couleur hexadecimale (#RGB, #RRGGBB, #AARRGGBB) — ' + fallback + ' utilisee.');
    return fallback;
  }
  return value;
}

function url(value: unknown, key: string): string | null {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'string') {
    warn(key + ' doit etre une URL — ignoree.');
    return null;
  }
  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    warn(key + " n'est pas une URL valide : " + value + ' — ignoree.');
    return null;
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    warn(key + ' doit etre en http(s) — ignoree.');
    return null;
  }
  if (parsed.protocol === 'http:') {
    warn(key + ' est en clair (http) : Android bloque le trafic clair par defaut. Passe en https ou ajoute une network security config.');
  }
  return value;
}

/** Returns the project-relative path, or null with a warning when unusable. */
function asset(value: unknown, key: string, projectRoot: string, extensions: string[]): string | null {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'string') {
    warn(key + ' doit etre un chemin de fichier — ignore.');
    return null;
  }
  const absolute = path.resolve(projectRoot, value);
  if (!fs.existsSync(absolute)) {
    warn(key + ' introuvable : ' + absolute + ' — ignore, le natif retombera sur la ressource systeme.');
    return null;
  }
  const extension = path.extname(absolute).toLowerCase().replace('.', '');
  if (!extensions.includes(extension)) {
    warn(key + ' doit avoir une extension parmi ' + extensions.join(', ') + ' — recu .' + extension + ', ignore.');
    return null;
  }
  return value;
}

const KNOWN_ROOTS = ['tracking', 'notification', 'alert', 'bubble', 'ios', 'rootComponent'];

export const SOUND_EXTENSIONS = ['wav', 'mp3', 'ogg', 'm4a', 'aac'];

export function resolveProps(raw: FieldAgentPluginProps | undefined, projectRoot: string): ResolvedProps {
  const props = raw ?? {};

  for (const key of Object.keys(props)) {
    if (!KNOWN_ROOTS.includes(key)) {
      warn('cle inconnue "' + key + '" dans la configuration du plugin — ignoree.');
    }
  }

  const tracking = props.tracking ?? {};
  const notification = props.notification ?? {};
  const alert = props.alert ?? {};
  const bubble = props.bubble ?? {};
  const ios = props.ios ?? {};

  const trackingUrl = url(tracking.url, 'tracking.url');
  if (!trackingUrl) {
    warn('tracking.url absente : le suivi refusera de demarrer tant que start({ url }) ne la fournit pas a chaud.');
  }

  const intervalSeconds = num(tracking.intervalSeconds, 15, 'tracking.intervalSeconds', 1);
  const idleIntervalSeconds = num(tracking.idleIntervalSeconds, 60, 'tracking.idleIntervalSeconds', 1);
  if (idleIntervalSeconds < intervalSeconds) {
    warn('tracking.idleIntervalSeconds < intervalSeconds : la cadence "au repos" n economisera rien.');
  }

  let titlePattern = str(alert.titlePattern, '.*', 'alert.titlePattern');
  try {
    // eslint-disable-next-line no-new
    new RegExp(titlePattern, 'i');
  } catch {
    warn('alert.titlePattern invalide (' + titlePattern + ') — remplacee par ".*".');
    titlePattern = '.*';
  }

  return {
    tracking: {
      url: trackingUrl,
      batchUrl: url(tracking.batchUrl, 'tracking.batchUrl'),
      intervalSeconds,
      idleIntervalSeconds,
      distanceFilterMeters: num(tracking.distanceFilterMeters, 15, 'tracking.distanceFilterMeters', 0),
      batchSize: num(tracking.batchSize, 50, 'tracking.batchSize', 1),
      queueSize: num(tracking.queueSize, 1000, 'tracking.queueSize', 1),
      heartbeatSeconds: num(
        tracking.heartbeatSeconds,
        Math.max(idleIntervalSeconds * 2, 120),
        'tracking.heartbeatSeconds',
        30
      ),
    },
    notification: {
      channelName: str(notification.channelName, 'Suivi en service', 'notification.channelName'),
      title: str(notification.title, 'En service', 'notification.title'),
      body: str(notification.body, 'Ta position est partagee pendant tes courses.', 'notification.body'),
      icon: asset(notification.icon, 'notification.icon', projectRoot, ['png']),
      color: color(notification.color, '#FF6B2C', 'notification.color'),
    },
    alert: {
      titlePattern,
      sound: asset(alert.sound, 'alert.sound', projectRoot, SOUND_EXTENSIONS),
      channelName: str(alert.channelName, 'Nouvelles courses', 'alert.channelName'),
      route: str(alert.route, 'field-agent-alert', 'alert.route'),
      ttlSeconds: num(alert.ttlSeconds, 45, 'alert.ttlSeconds', 1),
      torch: bool(alert.torch, false, 'alert.torch'),
      channelVersion: num(alert.channelVersion, 1, 'alert.channelVersion', 1),
    },
    bubble: {
      icon: asset(bubble.icon, 'bubble.icon', projectRoot, ['png']),
      label: str(bubble.label, 'Suivi', 'bubble.label'),
      colors: {
        ok: color(bubble.colors?.ok, '#1DB954', 'bubble.colors.ok'),
        warn: color(bubble.colors?.warn, '#F5A623', 'bubble.colors.warn'),
        bad: color(bubble.colors?.bad, '#E5484D', 'bubble.colors.bad'),
        urgent: color(bubble.colors?.urgent, '#E5484D', 'bubble.colors.urgent'),
      },
    },
    ios: {
      locationWhenInUsePermission: str(
        ios.locationWhenInUsePermission,
        "Ta position sert a t'affecter les courses proches.",
        'ios.locationWhenInUsePermission'
      ),
      locationAlwaysPermission: str(
        ios.locationAlwaysPermission,
        'Ta position continue a etre partagee pendant tes courses, meme application fermee.',
        'ios.locationAlwaysPermission'
      ),
      criticalAlerts: bool(ios.criticalAlerts, false, 'ios.criticalAlerts'),
    },
    rootComponent: str(props.rootComponent, 'main', 'rootComponent'),
  };
}

/** Android resource names accept only [a-z0-9_], so copies get fixed names. */
export const ANDROID_RESOURCES = {
  sound: 'field_agent_alert',
  notificationIcon: 'field_agent_notification',
  bubbleIcon: 'field_agent_bubble',
};

export const IOS_SOUND_BASENAME = 'FieldAgentAlert';

export const META_CONFIG = 'expo.modules.fieldagent.CONFIG';

/**
 * The whole configuration reaches the native side as one JSON blob.
 * One meta-data entry instead of twenty is not laziness: every extra entry is
 * one more thing a host manifest merge can silently drop.
 */
export function serializeForNative(props: ResolvedProps, soundExtension: string | null): string {
  return JSON.stringify({
    tracking: props.tracking,
    notification: {
      ...props.notification,
      icon: props.notification.icon ? ANDROID_RESOURCES.notificationIcon : null,
    },
    alert: {
      ...props.alert,
      sound: props.alert.sound ? ANDROID_RESOURCES.sound : null,
      soundExtension,
    },
    bubble: { ...props.bubble, icon: props.bubble.icon ? ANDROID_RESOURCES.bubbleIcon : null },
    rootComponent: props.rootComponent,
  });
}
