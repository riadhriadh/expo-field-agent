export type PermissionState = 'granted' | 'denied' | 'undetermined' | 'unsupported';

export type PermissionName =
  | 'location'
  | 'backgroundLocation'
  | 'notifications'
  | 'overlay'
  | 'batteryUnrestricted'
  | 'dndAccess'
  /**
   * Android 14 gates setFullScreenIntent behind a special access. Without it the
   * locked-screen alert quietly degrades to a heads-up notification, so it has
   * to be visible rather than assumed. Always 'granted' below Android 14,
   * 'unsupported' on iOS.
   */
  | 'fullScreenIntent'
  /**
   * The manufacturer autostart / task-killer screen (MIUI, EMUI, ColorOS...).
   * No API can read or grant it, so the state is 'granted' once the user has
   * been through it, 'undetermined' before, and 'unsupported' on a brand with
   * no known screen.
   */
  | 'autostart'
  /**
   * Notification access, for the opt-in `alert.notificationBridge`. It is the
   * only way to catch an FCM message that carries a `notification` block while
   * the app is not in the foreground, because the Firebase SDK posts those
   * itself without ever calling the app. 'unsupported' unless the host turned
   * the bridge on, and always 'unsupported' on iOS.
   */
  | 'notificationAccess';

export type Permissions = Record<PermissionName, PermissionState>;

export type TrackingOptions = {
  /** Overrides `tracking.url` from app.json for this run, and is persisted. */
  url: string;
  batchUrl: string | null;
  intervalSeconds: number;
  idleIntervalSeconds: number;
  distanceFilterMeters: number;
  batchSize: number;
  queueSize: number;
  heartbeatSeconds: number;
};

export type Position = {
  latitude: number;
  longitude: number;
  /** Horizontal accuracy in metres; -1 when the platform did not report one. */
  accuracy: number;
  altitude: number;
  /** Metres per second; -1 when unknown. */
  speed: number;
  /** Degrees from true north; -1 when unknown. */
  heading: number;
  /** Unix milliseconds, from the fix itself. */
  timestamp: number;
  /** Stable per-point id. The server should index it uniquely to de-duplicate replays. */
  clientId: string;
  /** True when the point was emitted by the heartbeat rather than by movement. */
  heartbeat: boolean;
};

export type TrackingState = {
  running: boolean;
  queued: number;
  lastFixAt: number | null;
  lastSentAt: number | null;
  lastError: string | null;
};

export type FlushResult = { sent: number; queued: number };

export type BubbleState = 'ok' | 'warn' | 'bad' | 'urgent';

/**
 * Every string the plugin shows to an end user, so the host can drive them from
 * its own translations. Each key is optional: whatever you leave out keeps the
 * value from `app.json`, and `setStrings(null)` puts all of them back.
 *
 * Android only in effect. iOS has no service notification, no channel and no
 * bubble, and its two location prompts are read from `Info.plist` by the system
 * in the phone's language — no runtime call can change those.
 */
export type FieldAgentStrings = {
  /** Channel name for the permanent "on duty" notification. */
  serviceChannelName?: string;
  /** Title of that notification. */
  serviceTitle?: string;
  /** Its body, also used as the channel description. */
  serviceBody?: string;
  /** Channel name for alerts. */
  alertChannelName?: string;
  /** Channel name for the muted alert variant. */
  alertChannelNameSilent?: string;
  /** The action that stops a ringing alert. */
  dismiss?: string;
  /** Text shown in the bubble when no per-state text was given. */
  bubbleLabel?: string;
  /** Bubble accessibility sentence; `%s` is replaced by the label. */
  bubbleAccessibility?: string;
};

export type AlertPayload = {
  title: string;
  body?: string;
  data?: Record<string, unknown>;
  /** Unix milliseconds at which the native side accepted the alert. */
  receivedAt: number;
  /** Native alert id, used to dismiss exactly this one. */
  id: string;
  /** `alert.route` from app.json, for hosts that navigate instead of overlaying. */
  route: string;
};

/**
 * What `triggerAlert` accepts. `alert.titlePattern` is tested against `title`,
 * `tag` and `channelId` — pass whichever your push pipeline actually fills in.
 */
export type AlertTrigger = {
  title: string;
  body?: string;
  data?: Record<string, unknown>;
  /** Notification tag, when your push routes by tag rather than by title. */
  tag?: string;
  /** Android notification channel id, when your push routes by channel. */
  channelId?: string;
};

export type AlertActions = {
  /** Stops the ringtone, the torch and the full-screen activity. */
  dismiss: () => Promise<void>;
};

export type SentEvent = { count: number; queued: number };

export type ErrorEvent = { code: string; message: string };

export type FieldAgentEventMap = {
  position: (position: Position) => void;
  sent: (result: SentEvent) => void;
  error: (error: ErrorEvent) => void;
  alert: (alert: AlertPayload) => void;
  bubblePress: () => void;
};

export type FieldAgentEventName = keyof FieldAgentEventMap;
