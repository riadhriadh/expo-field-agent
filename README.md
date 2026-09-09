# expo-field-agent

**English** · [Français](README.fr.md) · [العربية](README.ar.md)

> **A development build is required. This package does not work in Expo Go.**
>
> ```bash
> npx expo prebuild && npx expo run:android
> ```

Background GPS tracking that survives the app being closed, the screen being
off and the phone rebooting; a floating bubble over other applications; and a
full-screen alert that renders a React Native component **from your own app**,
including on a locked phone.

Android: complete. iOS: what the platform allows and nothing more — the limits
table below says exactly what is missing, in writing.

---

## Do you need this? Read this first

This module exists for one situation: **someone is out working, their phone is
in their pocket, and your server has to keep seeing them and be able to
interrupt them.** Everything in it follows from that. If that is not your
situation, a lighter tool will serve you better, and the tables below name it.

### Use it when

| Situation | What breaks without a native module |
|---|---|
| **Delivery rider / ride-hailing driver on duty** — a position every 15 s to dispatch, plus a job offer that has to reach them screen-locked, phone in pocket | The rider swipes the app away out of habit. JavaScript dies with the task; the positions stop and dispatch believes the rider went home. |
| **Field technician on a round** — proof of passage across a whole day, through basements and tunnels with no signal | Points recorded offline live in memory and vanish with the process. The round comes back with holes in it. |
| **Lone worker / security patrol** — proof of presence plus an urgent recall | A "still here" beat that stops when the CPU sleeps reports a missing person every night. |
| **On-call staff, ambulance, roadside assistance** — a call-out that must ring even in silent mode | A normal notification on a silenced phone is a call-out nobody answers. |
| **Fleet and logistics** — a batch replayed after a tunnel must not create duplicate rows | Removing points from a queue by count instead of by identifier duplicates or loses them the moment two uploads overlap. |

### Do **not** use it when — use these instead

| What you actually want | Use this |
|---|---|
| The position only while the app is open on screen | `expo-location` alone. No permission ladder, no permanent notification, no foreground service. |
| Occasional background position, best-effort, no guarantee of surviving a reboot | `expo-location` + `expo-task-manager` (`startLocationUpdatesAsync`). Far simpler. The guarantee is this module's entire reason to exist — if you do not need it, do not pay for it. |
| A normal push notification | `expo-notifications`. A full-screen intent used for content that is not urgent gets your app reported, and Android 14 puts it behind a special access for exactly that reason. |
| To ship inside Expo Go | Impossible. This is a native module, and Expo Go carries a fixed set of native code that yours is not part of. |
| A floating bubble on iOS | It does not exist and will not. Nothing in this package or any other will give it to you. Read the limits table before you promise it to anyone. |

### Why a native module at all

GPS acquisition itself is **not** rewritten — it stays the platform's
(`FusedLocationProviderClient`, `CLLocationManager`). What justifies native code
is the list of things JavaScript is structurally unable to do:

| What is needed | Why JS cannot do it |
|---|---|
| Survive the app being swiped out of recents | The JS engine is destroyed along with the task. Only an Android foreground service declared `stopWithTask="false"` keeps running. |
| Survive process death and reboot | `START_STICKY`, a `BOOT_COMPLETED` receiver and an `AlarmManager` watchdog are manifest and native constructs. After a reboot there is no JS entry point at all. |
| `foregroundServiceType="location"` | Android 14 crashes the service when the type is missing. It is a manifest attribute, resolved at build time. |
| Draw over other applications | A `TYPE_APPLICATION_OVERLAY` window. React Native renders inside your activity, never outside it. |
| Open a screen from the background on a locked phone | A full-screen intent, plus the background-activity-launch exemption granted by `SYSTEM_ALERT_WINDOW`. |
| Ring while the phone is silenced | The `USAGE_ALARM` audio stream, plus audio focus so the navigation app does not cover the sound. |
| A queue that survives being killed mid-upload | It has to be written to disk by the same process that performs the POST. |

**Zero native files to touch on your side.** Everything above is installed by
the config plugin, from `app.json`.

---

## Installation

```bash
npx expo install expo-field-agent
```

Then in `app.json`:

```json
["expo-field-agent", {
  "tracking": {
    "url": "https://api.example.com/api/positions",
    "batchUrl": "https://api.example.com/api/positions/batch",
    "intervalSeconds": 15,
    "idleIntervalSeconds": 60,
    "distanceFilterMeters": 15,
    "batchSize": 50,
    "queueSize": 1000,
    "heartbeatSeconds": 120
  },
  "notification": {
    "channelName": "On duty",
    "title": "On duty",
    "body": "Your position is shared while you are working.",
    "icon": "./assets/notif.png",
    "color": "#FF6B2C"
  },
  "alert": {
    "titlePattern": "New job",
    "sound": "./assets/alert.wav",
    "channelName": "New jobs",
    "route": "field-agent-alert",
    "ttlSeconds": 45,
    "torch": true
  },
  "bubble": {
    "icon": "./assets/bubble.png",
    "label": "Tracking",
    "colors": { "ok": "#1DB954", "warn": "#F5A623", "bad": "#E5484D" }
  }
}]
```

### Every key is optional

No key is required, and every one of them has a sensible default.
`["expo-field-agent"]` with no options object at all works: the plugin installs
in full. Only `tracking.url` cannot have a default — give it here, or at runtime
with `start({ url })`. A missing or malformed value **never** fails the build: it
prints a readable warning (`[expo-field-agent] …`) and the default applies.

That rule matters more than it sounds. A prebuild that dies because someone
mistyped a colour is a plugin that gets deleted from the project the same
afternoon.

| Key | Default | If you omit it |
| --- | --- | --- |
| `tracking.url` | `null` | Tracking refuses to start until `start({ url })` supplies one |
| `tracking.batchUrl` | `null` | Points go one by one to `url` |
| `tracking.intervalSeconds` | `15` | |
| `tracking.idleIntervalSeconds` | `60` | |
| `tracking.distanceFilterMeters` | `15` | |
| `tracking.batchSize` | `50` | |
| `tracking.queueSize` | `1000` | |
| `tracking.heartbeatSeconds` | `max(idleIntervalSeconds × 2, 120)` | So `120` with the defaults |
| `notification.channelName` | `"Suivi en service"` | |
| `notification.title` | `"En service"` | |
| `notification.body` | `"Ta position est partagee pendant tes courses."` | |
| `notification.icon` | `null` | The app icon |
| `notification.color` | `"#FF6B2C"` | |
| `alert.titlePattern` | `".*"` | Every title fires the alert |
| `alert.sound` | `null` | The system alarm ringtone |
| `alert.channelName` | `"Nouvelles courses"` | |
| `alert.route` | `"field-agent-alert"` | |
| `alert.ttlSeconds` | `45` | |
| `alert.torch` | `false` | |
| `alert.channelVersion` | `1` | |
| `bubble.icon` | `null` | A dot in the state colour |
| `bubble.label` | `"Suivi"` | |
| `bubble.colors.ok` | `"#1DB954"` | |
| `bubble.colors.warn` | `"#F5A623"` | |
| `bubble.colors.bad` | `"#E5484D"` | |
| `bubble.colors.urgent` | `"#E5484D"` | |
| `ios.locationWhenInUsePermission` | `"Ta position sert a t'affecter les courses proches."` | |
| `ios.locationAlwaysPermission` | `"Ta position continue a etre partagee pendant tes courses, meme application fermee."` | |
| `ios.criticalAlerts` | `false` | Needs Apple's entitlement to have any effect |
| `rootComponent` | `"main"` | What `registerRootComponent` and expo-router register |

The default user-facing strings are French, because that is the language this
module was built for. They are ordinary configuration: set
`notification.title`, `notification.body`, `alert.channelName` and the two
`ios.*` permission sentences to your own language and they are never seen.

> **SDK 52 only, and unrelated to this module:** some `expo-modules-core`
> versions ship a Compose Compiler that refuses Expo 52's default Kotlin 1.9.24
> (`This version (1.5.15) of the Compose Compiler requires Kotlin version
> 1.9.25`). The fix belongs to the app:
>
> ```json
> ["expo-build-properties", { "android": { "kotlinVersion": "1.9.25" } }]
> ```
>
> The `example/` app includes it for that reason.

> **iOS, Homebrew machines:** CocoaPods 1.16 on Ruby 3.4 breaks when the locale
> is not UTF-8 (`Unicode Normalization not appropriate for ASCII-8BIT`). Also
> nothing to do with this module:
>
> ```bash
> LANG=en_US.UTF-8 npx expo run:ios
> ```

The plugin handles the whole native side: permissions, the `type="location"`
foreground service, the boot and watchdog receivers, the alert activity, copying
your sound into `res/raw` with `noCompress`, `UIBackgroundModes` and the
`NSLocation*UsageDescription` strings on iOS. **No native file to touch.**

---

## Full integration

```tsx
import * as FieldAgent from 'expo-field-agent';
import { AlertHost } from 'expo-field-agent';
import { useEffect } from 'react';

export default function App() {
  useEffect(() => {
    const sub = FieldAgent.addListener('error', (e) => console.warn(e.code, e.message));
    return () => sub.remove();
  }, []);

  async function goOnDuty(token: string) {
    await FieldAgent.requestPermissions();             // in the order Android imposes
    await FieldAgent.setAuthHeader(`Bearer ${token}`); // encrypted (Keystore / Keychain)
    await FieldAgent.start();                          // idempotent
    await FieldAgent.showBubble();                     // false on iOS, by design
  }

  return (
    <>
      <MyApp onGoOnDuty={goOnDuty} />
      <AlertHost
        render={(alert, actions) => (
          <MyOfferScreen alert={alert} onAccept={actions.dismiss} onDecline={actions.dismiss} />
        )}
      />
    </>
  );
}
```

`AlertHost` mounts once and displays over everything. It works for both ways an
alert can arrive:

- **app already open** — the `alert` event fires and the screen appears;
- **app closed or phone locked** — the full-screen activity opens on its own,
  starts the React Native engine, and `AlertHost` reads the alert
  **synchronously** on its very first render (`getPendingAlertSync()`). There is
  no race between the bundle loading and the event firing: native *holds* the
  alert and makes it readable, it does not merely emit it.

---

## API

```ts
// Permissions -------------------------------------------------------------
FieldAgent.getPermissions(): Promise<Permissions>;
FieldAgent.requestPermissions(opts?: { skip?: (keyof Permissions)[] }): Promise<Permissions>;
FieldAgent.openSettings(which: keyof Permissions): Promise<void>;

// Tracking ----------------------------------------------------------------
FieldAgent.start(options?: Partial<TrackingOptions>): Promise<void>;   // idempotent
FieldAgent.stop(): Promise<void>;
FieldAgent.isRunning(): Promise<boolean>;
FieldAgent.setAuthHeader(value: string | null): Promise<void>;
FieldAgent.setInterval(seconds: number): Promise<void>;                // at runtime
FieldAgent.flush(): Promise<{ sent: number; queued: number }>;
FieldAgent.getState(): Promise<TrackingState>;

// Bubble ------------------------------------------------------------------
FieldAgent.showBubble(): Promise<boolean>;                             // false on iOS, or without the permission
FieldAgent.hideBubble(): Promise<void>;
FieldAgent.setBubbleState(s: 'ok' | 'warn' | 'bad' | 'urgent', text?: string): Promise<void>;

// Alert -------------------------------------------------------------------
FieldAgent.triggerAlert({ title, body?, data?, tag?, channelId? }): Promise<void>;
FieldAgent.dismissAlert(): Promise<void>;
FieldAgent.setAlertSound(enabled: boolean): Promise<void>;             // host-side mute
FieldAgent.getPendingAlert(): Promise<AlertPayload | null>;
FieldAgent.getPendingAlertSync(): AlertPayload | null;

// Events ------------------------------------------------------------------
FieldAgent.addListener('position' | 'sent' | 'error' | 'alert' | 'bubblePress', cb): Subscription;
```

`Permissions` carries eight keys:

| key | what it is |
|---|---|
| `location` | `ACCESS_FINE_LOCATION` |
| `backgroundLocation` | "allow all the time" |
| `notifications` | `POST_NOTIFICATIONS` (Android 13+) |
| `overlay` | `SYSTEM_ALERT_WINDOW` — the bubble, **and** the background-launch exemption |
| `batteryUnrestricted` | the system battery-optimisation list |
| `dndAccess` | `ACCESS_NOTIFICATION_POLICY` |
| `fullScreenIntent` | **addition** — Android 14 puts `setFullScreenIntent` behind a special access. Without it the locked-screen alert silently degrades into an ordinary notification, so the state is made visible rather than assumed. |
| `autostart` | **addition** — the manufacturer's autostart screen. No API reads it: `granted` once the user has been sent there, `undetermined` before, `unsupported` on a brand with no known screen. |

Two keys beyond the original contract, because without them the alert and the
tracking break on Android 14+ and on MIUI/EMUI/ColorOS **without saying a word**.

### The alert payload

```ts
type AlertPayload = {
  id: string;            // so you can dismiss this one only
  title: string;
  body?: string;
  data?: Record<string, unknown>;
  receivedAt: number;    // Unix ms, stamped natively
  route: string;         // `alert.route` from app.json, verbatim
};
```

`route` is simply carried through: `AlertHost` does not need it, but a host that
prefers to navigate (expo-router) rather than overlay a screen has it at hand
without re-reading its own configuration.

### `data` and positions

`triggerAlert({ data })` accepts any object; it crosses to native as JSON and
comes back parsed in `alert.data`. `data.silent === true` mutes that one alert
only.

A position is **never** written to the logs, neither on Android nor on iOS: it
is personal data.

---

## Platform limits — the truth

| Capability | Android | iOS |
|---|---|---|
| Background tracking | ✅ `type="location"` foreground service | ✅ `UIBackgroundModes: location`, `allowsBackgroundLocationUpdates`, `pausesLocationUpdatesAutomatically = false` |
| Survives the app being swiped away | ✅ `stopWithTask=false`, `onTaskRemoved` stops nothing | ⚠️ yes, as long as the app is not *force quit* |
| Survives process death | ✅ `START_STICKY` + `AlarmManager` watchdog every ~15 min | ⚠️ **nothing restarts it**, except significant location changes (`startMonitoringSignificantLocationChanges`) |
| Survives a phone reboot | ✅ `BOOT_COMPLETED` + `QUICKBOOT_POWERON` | ❌ no |
| Floating bubble | ✅ `TYPE_APPLICATION_OVERLAY` | ❌ **impossible, no API exists.** `showBubble()` returns `false`. The closest equivalent is a Live Activity (Dynamic Island / lock screen), not provided here. |
| Rings while silenced | ✅ `USAGE_ALARM` stream, volume raised to maximum then restored | ⚠️ only with the **Critical Alerts** entitlement, granted by Apple on a justified request (`ios.criticalAlerts: true`). Without it: a normal notification. |
| Full screen on arrival | ✅ `setFullScreenIntent` + `CATEGORY_CALL` + an `IMPORTANCE_HIGH` channel, **and** the `SYSTEM_ALERT_WINDOW` exemption | ⚠️ no equivalent. A `.timeSensitive` notification (`.critical` with the entitlement). CallKit would give a real full screen, but Apple rejects the abuse: this is not a call, so it is not used. |
| Bypass Do Not Disturb | ⚠️ `setBypassDnd(true)` only if `ACCESS_NOTIFICATION_POLICY` is already granted **at the moment the channel is created** | ⚠️ `.timeSensitive` pierces Focus modes; anything beyond that needs Critical Alerts |
| Manufacturer autostart screens | ✅ a per-brand table with a fallback to the app info page | ❌ not applicable |

The plugin **compiles and runs on iOS in every case**. Missing capabilities
return an explicit value (`false`, `"unsupported"`), never an exception.

---

## What the plugin handles for you

Every point below is a production bug, not a theoretical best practice.

1. **Foreground service** — `type="location"` declared in the manifest *and*
   passed to `startForeground()` (Android 14 crashes otherwise). `START_STICKY`.
   `onTaskRemoved` stops nothing: that is exactly the moment the user swipes the
   app away believing it keeps going.
2. **Survival** — `BOOT_COMPLETED` **and** `QUICKBOOT_POWERON` (some ROMs send
   only the second); a `setAndAllowWhileIdle` watchdog every ~15 min; the "on
   duty" state lives in preferences, not in memory. When Android 12+ refuses a
   background start, the failure is visible (an `error` with code
   `SERVICE_START`) and a "resume" notification is posted — it is not swallowed.
3. **Versioned notification channels** — an existing channel never re-reads its
   importance, sound, vibration or DND bypass. The ids carry the version
   (`fa_alert_v1`), plus a DND variant and a muted variant; the old ones are
   deleted at creation. Bump `alert.channelVersion` to force a re-creation across
   the installed base.
4. **Bundled sound** — the config plugin copies your file into `res/raw` and adds
   `noCompress` to `app/build.gradle` (without it `openRawResourceFd()` fails and
   `MediaPlayer` opens nothing). **Assumed deviation:** the host-supplied sound is
   tried *first*, with the system chain (alarm → ringtone → notification) as the
   fallback — not the other way round. You bundled it on purpose, and it is the
   only one that does not depend on a ROM. Each candidate is opened before being
   kept.
5. **Ringing while silenced** — the `USAGE_ALARM` stream; alarm volume raised to
   maximum, the old volume **saved to disk** (a process killed mid-alert would
   otherwise leave the morning alarm pinned at maximum) and restored on the next
   start; the volume is read back after writing, and a silently refused raise
   produces an `error` with code `VOLUME`. Audio focus
   `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`, otherwise the navigation app covers the
   alert and the sound goes to the earpiece. A mandatory duration ceiling
   (`ttlSeconds`).
6. **Opening a screen from the background** — both routes, not one:
   `setFullScreenIntent` + `CATEGORY_CALL` + an `IMPORTANCE_HIGH` channel,
   **and** the exemption that `SYSTEM_ALERT_WINDOW` grants. The activity is
   `showWhenLocked`, `turnScreenOn`, `excludeFromRecents`,
   `launchMode="singleTask"`, and handles its own insets (Android 15,
   edge-to-edge).
7. **Bubble** — `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE`, draggable,
   snapped to the edge on release, position persisted. **A tap brings the app to
   the front** (and emits `bubblePress`): the tap almost always comes from another
   app, where JavaScript is not running and could not do it; it is
   `SYSTEM_ALERT_WINDOW`, already required by the bubble, that makes this launch
   legal. An `ACTION_CANCEL` — the system taking the gesture away — is **not** a
   tap: counting it produced phantom presses. **It cannot show over the lock
   screen** — no overlay window can. On a locked screen the surface is the
   full-screen notification; the bubble comes back on unlock.
8. **Manufacturers** — `Power` opens the right screen on MIUI, EMUI, ColorOS,
   FunTouch, One UI, OxygenOS, Realme, Meizu, Letv, Asus, Transsion and Nokia,
   with several candidate components per brand and a fallback to the app info
   page. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is **not** requested: Google Play
   refuses it to most apps and a rejection gets the app delisted. The system list
   costs two extra taps.
9. **Battery** — a distance filter, separate active/idle cadences, batching
   (`setMaxUpdateDelayMillis`) while idle, a partial `WakeLock` only during an
   upload, an immediate `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` at start
   (otherwise the first point arrives three minutes later), and a heartbeat that
   bypasses the distance filter.
10. **Queue** — bounded, on disk, removal **by identifier** and never by count.
    Each point carries a `client_id`: put a unique index on it server-side and a
    replayed batch creates no duplicates. A network failure (`OFFLINE`,
    `TIMEOUT`) discards nothing and disconnects nobody; only a definitive 4xx
    (not 401/403, not 408/429) is dropped, otherwise one poisoned point would
    block every point behind it.
11. **Main thread and leaks** — the torch beat runs on the main thread with an
    identity token, so a beat already queued cannot switch the torch back on
    after the stop. `MediaPlayer` is released even when `prepare()` fails.
    Listeners, `postDelayed` callbacks and overlay views are removed.
12. **Android 13/14/15** — `POST_NOTIFICATIONS` requested before relying on any
    notification; background location in two steps (`requestPermissions` asks for
    "while using" then sends the user to system settings from Android 11 on, as
    the platform imposes and as Google Play requires you to disclose);
    `FOREGROUND_SERVICE_LOCATION` in the manifest; edge-to-edge handled for
    API 35.

---

## Wiring an FCM notification to the full-screen alert

Yes, this is the intended use case. The `alert.titlePattern` filter is tested
against **the title, the tag and the channel id** — pass whichever one your
pipeline actually fills, you do not need to fabricate a fake title.

```json
"alert": { "titlePattern": "^(new-job|New job)$" }
```

**App open or backgrounded (JS alive)** — via `expo-notifications`:

```ts
import * as Notifications from 'expo-notifications';
import * as FieldAgent from 'expo-field-agent';

Notifications.addNotificationReceivedListener(({ request }) => {
  const { title, body, data } = request.content;
  FieldAgent.triggerAlert({
    title: title ?? '',
    body: body ?? undefined,
    data: data as Record<string, unknown>,
    tag: (data as any)?.tag,                        // your FCM tag
    channelId: (request.trigger as any)?.channelId, // the Android channel
  });
});
```

`triggerAlert` returns without doing anything when nothing matches the pattern —
so you can wire it to **all** your notifications without filtering yourself.

**App killed** — the same call, from a background task, which runs as headless JS
on Android without the app being launched:

```ts
import * as TaskManager from 'expo-task-manager';
import * as Notifications from 'expo-notifications';
import * as FieldAgent from 'expo-field-agent';

const TASK = 'field-agent-push';

TaskManager.defineTask(TASK, ({ data, error }) => {
  if (error || !data) return;
  const notification = (data as any).notification?.data ?? (data as any);
  FieldAgent.triggerAlert({
    title: notification.title ?? '',
    body: notification.body,
    data: notification,
    tag: notification.tag,
    channelId: notification.channelId,
  });
});

Notifications.registerTaskAsync(TASK);
```

Send a **data-only** message (no `notification` block), otherwise Android shows
its own notification on top of yours while the app is closed.

From there native takes over: an `IMPORTANCE_HIGH` channel,
`setFullScreenIntent`, `AlertActivity` over the lock screen, the ring on the
alarm stream, and your `AlertHost` component rendered on the first frame.

**What the plugin does not do:** it does not install its own
`FirebaseMessagingService`. Only one service can win the `MESSAGING_EVENT`
filter, and taking it would break `expo-notifications` in your app. Both wirings
above go through it, so nothing is stolen from anyone. If you want the native FCM
entry point anyway (one case: you do not use `expo-notifications` at all), ask —
it means an extra `firebase-messaging` dependency and a version coupling, not a
default worth choosing on your behalf.

---

## Where alerts come from

Native cannot intercept other apps' notifications: it exposes a single entry
point, `triggerAlert()`, filtered by `alert.titlePattern` (case-insensitive,
plain string or regex). Three sources reach it:

1. **Your server's response.** If a position POST returns
   `{"alert": {"title": "...", "body": "...", "data": {...}}}`, the service fires
   the alert. This is the one path that **needs no push at all** and works with
   the app closed, because the service is what made the request.
2. **A push, app open** — wire `expo-notifications`:
   ```ts
   Notifications.addNotificationReceivedListener(({ request }) => {
     FieldAgent.triggerAlert({
       title: request.content.title ?? '',
       body: request.content.body ?? undefined,
       data: request.content.data,
     });
   });
   ```
3. **A push, app closed** — the same call from an `expo-notifications` +
   `expo-task-manager` background task (`Notifications.registerTaskAsync`), which
   runs as headless JS on Android even with the app killed.

---

## Verify — one scenario, one command

| # | Scenario | Verification command | Expected |
|---|---|---|---|
| 1 | Cold start, permissions granted, `start()` | `adb shell dumpsys activity services tn.exemple.fieldagent \| grep isForeground` | `isForeground=true`, and a first POST in under 10 s (`adb logcat -s OkHttp` server-side, or the `sent` event in the example's journal) |
| 2 | App swiped from recents | the same, after swiping | service still there, `position` events still coming |
| 3 | Process killed | `adb shell am crash tn.exemple.fieldagent` then `adb shell pidof tn.exemple.fieldagent` | new PID, service back, `getState().queued` resumes its descent |
| 4 | Phone reboot | `adb reboot` then, without opening the app, `adb shell dumpsys activity services tn.exemple.fieldagent` | service restarted by `BootReceiver` |
| 5 | Airplane mode for 2 min, then back | `adb shell cmd connectivity airplane-mode enable` … `disable` | `queued` rises then falls back to 0; **no duplicates** server-side (unique index on `client_id`) |
| 6 | Locked screen + alert | `adb shell input keyevent 26` then `triggerAlert` from the example | screen woken, `AlertActivity` in front, sound |
| 7 | Silent + alarm volume at 1 + alert | `adb shell media volume --stream 4 --set 1` then an alert, and `adb shell dumpsys audio \| grep -A3 STREAM_ALARM` | volume raised to maximum during the alert, restored afterwards; `dumpsys media.audio_flinger` shows an active `USAGE_ALARM` stream |
| 8 | Do Not Disturb on + alert | enable DND, `getPermissions().dndAccess` | rings if `granted`; otherwise the state says so plainly and this README explains what to do |
| 9 | Host-side mute + alert | `setAlertSound(false)` then an alert | no player, **no** volume raise (`dumpsys audio` unchanged), the screen still opens |
| 10 | Alert with the app closed | `adb shell am force-stop tn.exemple.fieldagent`, then an alert via the server response or a push task | the screen opens and renders the host's component with the right data |
| 11 | Bubble: drag, snap, tap | manual + `adb shell dumpsys window \| grep fieldagent` | `bubblePress` event received; position kept after `am crash` |
| 12 | 8 h of continuous tracking | `adb shell dumpsys meminfo tn.exemple.fieldagent` hourly; `adb shell dumpsys batterystats --charged tn.exemple.fieldagent` | `TOTAL PSS` stable; see "assumed limits" for consumption |

The `example/` app replays each of these scenarios from its home screen. Its
three assets (`assets/notif.png`, `assets/bulle.png`, `assets/alerte.wav`) are
generated placeholders: replace them with yours, the plugin only copies them.

---

## Automated tests

```bash
npm test
```

```bash
cd example && npx expo prebuild --platform android && cd android && ./gradlew :expo-field-agent:test
```

- **`Geo`** — the plausibility filter in pure JVM, no Android: absurd accuracy,
  out-of-order points, impossible jumps, the tunnel exemption, the distance
  filter against the heartbeat. 16 cases.
- **`Queue`** — add, ceiling, removal by identifier (including with points added
  "in flight"), survival across a restart, a file truncated by a kill. 9 cases.
- **config plugin** — the produced manifest does contain the permissions, the
  `type="location"` service with `stopWithTask=false`, the boot receiver with its
  quickboot variants, the `showWhenLocked` alert activity; the `build.gradle`
  insertion is idempotent; a malformed config warns instead of crashing.
  13 cases.

The rest is manual, assumed, and described in the table above.

### What was built, and not merely written

| Check | Result |
|---|---|
| `expo prebuild` (Android) → manifest, `res/raw`, `res/drawable`, `build.gradle` | ✅ |
| `expo prebuild` (iOS) → `Info.plist`, sound added to the Xcode project | ✅ |
| `:expo-field-agent:compileDebugKotlin` — Expo SDK 52 | ✅ zero warnings in the module's sources |
| `:expo-field-agent:test` — Geo + Queue | ✅ 25/25 |
| `:app:assembleDebug` — full APK, merged manifest | ✅ |
| `xcodebuild -target ExpoFieldAgent` (iOS simulator) | ✅ |
| `npm pack` → install into a fresh Expo **SDK 57** app, `expo prebuild`, compile | ✅ without a single manual edit |
| `tsc` on the package, on the config plugin and on the example | ✅ |

What has **not** been verified here, and can only be verified on a real phone:
the twelve scenarios in the previous table. That is what the commands are for.

---

## Choices and assumed limits

**Why a native service rather than `expo-location` alone.** GPS acquisition stays
the platform's (`FusedLocationProviderClient`, `CLLocationManager`) — it is not
rewritten. What JavaScript cannot do, and what justifies this module: declaring
`foregroundServiceType="location"` for Android 14, holding a bounded on-disk
queue, coming back after process death or a reboot, drawing an overlay, opening a
full-screen activity from the background, and ringing on the alarm stream.

**Dependencies added.** Exactly one:
`com.google.android.gms:play-services-location`, already present in any Expo
project that uses `expo-location`. `LocationManager` exposes batching
(`maxUpdateDelay`) and `getCurrentLocation` only from API 30/31; this module
targets `minSdk 24`. The auth header is encrypted with `AndroidKeyStore` +
`javax.crypto` (platform) rather than `androidx.security:security-crypto`, and
with the Keychain on iOS. Transport uses `HttpURLConnection` / `URLSession` — a
few kilobytes of JSON do not justify pinning an OkHttp version inside the host
project.

**The alert activity mounts the app's own root component.** Not a second
component to register: `AlertActivity` starts exactly the root that
`registerRootComponent` (and expo-router) registers under the name `main`, so
your `<AlertHost>` mounts as usual and reads the alert on the first render. If
you register your root under another name, tell the plugin:
`["expo-field-agent", { "rootComponent": "myName" }]`.

**Two React surfaces for a brief moment.** If the user opens the app by its icon
while an alert is showing, the alert activity closes itself
(`ActivityLifecycleCallbacks`) so two React trees are never durably mounted. The
window where both coexist is measured in milliseconds.

**Battery consumption: no figure is published.** It was not measured, so it is
not announced. The protocol to measure it on your own fleet:

```bash
adb shell dumpsys batterystats --reset
# ... 8 h on duty, screen off, a real route ...
adb shell dumpsys batterystats --charged tn.exemple.fieldagent > battery.txt
```

The order of magnitude depends entirely on `intervalSeconds`, signal quality and
the handset; a number measured on a Pixel says nothing about a Redmi.

**The heartbeat degrades in deep sleep.** It is driven by a main-thread
`Handler`, therefore by `uptimeMillis`, which **stops advancing when the CPU
sleeps**. Phone motionless, screen off, Doze: the beat does not fire at
`heartbeatSeconds`, it fires at the service's next wake-up — that is, at the
latest at the watchdog alarm, which is the floor the system imposes on
*while-idle* alarms, **~15 minutes**. Going below that would need an exact alarm,
which Google Play refuses to apps that are neither clocks nor calendars. On the
road the problem does not arise: every GPS fix wakes the CPU. Measured on an
emulator, not deduced.

**`flush()` without the service.** It works: the queue and the transport live in
`Outbox`, not in the service, so a manual `flush()` uploads even when tracking is
stopped.

**What the plugin does not do.** No iOS Live Activity, no CallKit, no
`NotificationListenerService` to intercept other apps' notifications, no reverse
geocoding. None of these is a TODO: they are non-goals, listed here so they are
not discovered during a demo.

---

## Licence

MIT.
