# expo-field-agent

**English** · [Français](README.fr.md) · [العربية](README.ar.md)

> **A development build is required for anything to actually happen.**
>
> In Expo Go the package **degrades instead of crashing**: every call returns a
> neutral value, `isAvailable` is `false`, and nothing is tracked, shown or rung.
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

## A complete app built on it

**[riadhriadh/example-expo-field-agent](https://github.com/riadhriadh/example-expo-field-agent)** — a driver app in the Uber
Driver mould: going on duty, the permission onboarding, the bubble reflecting the
shift state, and the full-screen job offer. It was written from
[docs/prompt-rider-app.md](docs/prompt-rider-app.md), the brief in this
repository.

| The bubble over the app | The bubble over the launcher |
|---|---|
| <img src="https://raw.githubusercontent.com/riadhriadh/expo-field-agent/main/docs/screenshots/rider-online.png" width="240" alt="Driver app on duty, the bubble showing En ligne" /> | <img src="https://raw.githubusercontent.com/riadhriadh/expo-field-agent/main/docs/screenshots/bubble-home.png" width="240" alt="The bubble floating over the Android home screen" /> |

Left: the driver is on duty, the service is posting positions, and the bubble
sits over the app. Right: the same bubble **over the launcher**, app in the
background — that is the part JavaScript cannot do, and the reason this package
is native.

The map is off in these shots because no Google Maps key is configured; the
tracking underneath is running, which is why the coordinates keep updating.

---

## Expo Go — degraded, never blocking

The native half cannot exist in Expo Go: Expo Go ships a fixed set of native
code and yours is not in it. That is a platform fact, and no package changes it.

What this package does about it: **it degrades.** Importing it is safe, and
every call returns a neutral value instead of throwing, so you can build and
navigate your screens in Expo Go and keep a development build for the parts that
need a device.

```ts
import * as FieldAgent from 'expo-field-agent';

if (!FieldAgent.isAvailable) {
  // Expo Go: say so in the interface rather than shipping a dead switch.
}
```

| Call | In Expo Go |
|---|---|
| `isAvailable` | `false` |
| `getPermissions()` | every key `'unsupported'` |
| `start()`, `stop()`, `setAuthHeader()`, `setInterval()`, `openSettings()` | resolve, do nothing |
| `isRunning()` | `false` |
| `flush()` | `{ sent: 0, queued: 0 }` |
| `getState()` | `running:false`, `queued:0`, `provider:'none'`, `locationEnabled:false`, and `lastError` saying why |
| `getLog()` | `[]` |
| `exportLog()` | `null` |
| `getOdometer()` | `0` |
| `clearLog()`, `resetOdometer()` | resolve, do nothing |
| `showBubble()` | `false` |
| `triggerAlert()`, `dismissAlert()`, `setAlertSound()`, `setStrings()`, `setBubbleImage()` | resolve, do nothing |
| `getPendingAlert()` / `getPendingAlertSync()` | `null` |
| `addListener()` | a subscription that never fires; `.remove()` is safe |
| `<AlertHost>` | renders nothing |

A single `console.warn` fires the first time a degraded call happens — once, not
per call, so it stays readable.

**Argument errors still throw, in Expo Go as everywhere.** `triggerAlert({})`
without a title, `setInterval(0)`, an empty `setBubbleImage('')`: those are bugs
in your code, not platform limits. Swallowing them here would let them reach
production unnoticed.

**Do not mistake degradation for support.** Nothing is tracked, no bubble is
drawn, no alert rings. `isAvailable` is the honest signal — branch your
interface on it, and put the real testing on a development build.

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

## From zero to a tracking app

An empty folder to a phone posting its position while the app is closed and the
screen is off. Every command below is meant to be pasted as it is.

### The stack this guide is written against

| Package | Version |
| --- | --- |
| `expo` | `^57.0.24` |
| `react` | `19.2.3` |
| `react-native` | `0.86.3` |
| `expo-status-bar` | `~57.0.1` |
| `@types/react` | `~19.2.0` |
| `typescript` | `^5.9.3` |

Those are this repository's own versions — what the native code is compiled and
tested against. **SDK 57 is not a requirement.** The `peerDependencies` are
`"expo": ">=52.0.0"`, `"react": "*"`, `"react-native": "*"`: hosts from SDK 52
up are still supported, and 57 is simply where development happens.

`expo-build-properties` is deliberately absent. The Kotlin pin further down is
an SDK 52 workaround and nothing on this stack needs it — add the package only
if your own app has its own reason to.

### 0. Expo Go cannot run this — a development build is required

Expo Go ships a fixed set of native code and `expo.modules.fieldagent.*` is not
part of it. No amount of configuration changes that. What you need is a
**development build**: a native app you compile yourself, which is exactly what
steps 4 and 5 produce.

In Expo Go the package degrades instead of crashing — `isAvailable` is `false`,
`getPermissions()` answers `'unsupported'` on every key, `start()` resolves
without starting anything, `<AlertHost>` renders nothing, and a single
`console.warn` fires on the first degraded call. The full table is in the
Expo Go section above.

Two consequences for the app you are about to write:

- **Branch your interface on `FieldAgent.isAvailable`.** A dead toggle is worse
  than a "tracking unavailable on this build" banner.
- **Argument errors still throw in Expo Go.** `triggerAlert({})`,
  `setInterval(0)`, `setBubbleImage('')` are bugs in your code, not platform
  limits, and they are deliberately not degraded — otherwise they would reach
  production unnoticed.

### 1. Create the app

```bash
npx create-expo-app@latest my-field-app --template blank-typescript
cd my-field-app
```

### 2. Install the module

```bash
npx expo install expo-field-agent
```

Optional, only if you want the dev-menu launcher:

```bash
npx expo install expo-dev-client
```

The `example/` app in this repository does not install it, even though its start
script is `expo start --dev-client`: `npx expo run:android` on its own already
produces a working debug build.

### 3. Configure `app.json`

The smallest block that actually works:

```json
{
  "expo": {
    "name": "My Field App",
    "slug": "my-field-app",
    "scheme": "myfieldapp",
    "android": { "package": "com.example.myfieldapp" },
    "ios": { "bundleIdentifier": "com.example.myfieldapp" },
    "plugins": [
      [
        "expo-field-agent",
        {
          "tracking": {
            "url": "https://api.example.com/positions"
          },
          "notification": {
            "title": "On duty",
            "body": "Your position is shared while you are working."
          },
          "ios": {
            "locationWhenInUsePermission": "Your position is used to assign you nearby jobs.",
            "locationAlwaysPermission": "Your position keeps being shared during jobs, even when the app is closed."
          }
        }
      ]
    ]
  }
}
```

That is genuinely all of it. `["expo-field-agent"]` with no options object at
all installs the plugin in full — every key has a default. The only one that
cannot have a default is `tracking.url`: give it here, or pass it at runtime
with `start({ url })`. Without either, `start()` throws.

The four strings are in the minimal example for one reason: **the built-in
defaults are French** (`"En service"`, `"Ta position est partagee pendant tes
courses."`, and the two `ios.*` sentences). Set them to your language or your
users read French. Every other key, with its default, is in the
Installation table below.

Three things worth knowing before you paste anything larger:

- **A bad value never fails the build.** It prints `[expo-field-agent] …` on
  stderr and the default applies. Unknown keys warn too, at the root and one
  level down — `tracking.intervalSecond` is reported, not silently ignored. A
  typo and a broken feature look identical if you do not read the prebuild
  output.
- **`notification.icon` must be a 24dp monochrome PNG with an alpha channel.**
  The plugin only checks that the file exists and ends in `.png`; it cannot see
  what is inside. A full-colour icon renders as a white square in the status bar.
- **`http://` is a trap.** It warns at prebuild and works in your dev build,
  because the Expo template puts `usesCleartextTraffic="true"` in the *debug*
  manifest only. The same URL silently uploads nothing in release. Use `https://`.

### 4. Prebuild — mandatory, not optional

```bash
npx expo prebuild --clean
```

`expo-field-agent` is a config plugin **plus** native code, and everything it
needs lives in project files that do not exist until prebuild generates them.

Android (`android/app/src/main/AndroidManifest.xml`) receives:

- 14 `<uses-permission>` entries — `INTERNET`, `ACCESS_NETWORK_STATE`,
  `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`,
  `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`,
  `USE_FULL_SCREEN_INTENT`, `ACCESS_NOTIFICATION_POLICY`,
  `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `VIBRATE` — plus `SCHEDULE_EXACT_ALARM`
  only when `tracking.exactAlarms: true`;
- the service, `android:exported="false"`,
  `android:foregroundServiceType="location"`, `android:stopWithTask="false"`;
- four receivers — boot (`BOOT_COMPLETED`, `QUICKBOOT_POWERON`,
  `MY_PACKAGE_REPLACED`), watchdog, alert actions, and `PROVIDERS_CHANGED`;
- the alert activity with `showWhenLocked`, `turnScreenOn`,
  `excludeFromRecents`, `launchMode="singleTask"`;
- one `<meta-data>` carrying the whole resolved configuration as a single JSON
  blob;
- your `alert.sound`, copied into `res/raw/` with `noCompress` added to
  `app/build.gradle`.

iOS (`ios/<Project>/Info.plist`) receives `UIBackgroundModes: ["location"]`, the
three `NSLocation*UsageDescription` strings and an `EXFieldAgent` dictionary read
by the Swift side; the sound is copied into Copy Bundle Resources.

None of that is reachable from JavaScript. It is the entire reason the module is
native.

**Re-run it after every `app.json` plugin change.** `expo prebuild` reuses an
existing `android/` directory, so *not writing* a node is not the same as
*removing* it. The plugin actively withdraws what you opted out of — the
exact-alarm permission, the notification-listener service — but only when
prebuild actually runs. Turn one of them off without prebuilding and you keep
shipping the thing you just disabled.

### 5. Run on a real device

```bash
npx expo run:android          # builds, installs, starts Metro
```

```bash
npx expo run:ios
```

On a Homebrew machine with Ruby 3.4, CocoaPods 1.16 fails on a non-UTF-8 locale
(`Unicode Normalization not appropriate for ASCII-8BIT`) — unrelated to this
module:

```bash
LANG=en_US.UTF-8 npx expo run:ios
```

**Use a physical phone for anything meaningful.** An emulator has no real Doze
and no manufacturer task killer, and every fix it produces is flagged as coming
from a mock provider. With `tracking.rejectMock: true` that means *every single
point* is rejected, one `MOCK_LOCATION` error fires, the repeats are suppressed,
and the app looks like it is running while the queue stays empty. The default is
`false`, which keeps the point and flags it `isMock` so the server decides. Turn
it on for contractual traces only, and never while testing on an emulator.

### 6. Ask for permissions in the order Android imposes

`requestPermissions()` walks the whole ladder in one call, skipping anything
already granted: location, notifications (API 33+), background location, overlay,
Do Not Disturb access, full-screen intent (API 34+), battery, manufacturer
autostart. Every settings screen is awaited through an activity result, because
no API can await a settings page.

**Do not use that single call at signup.** On Android 11+ background location
cannot be granted by any dialog: the ladder walks straight from the foreground
dialog into the Android **App info** page, where the user lands with no idea why.
Google Play requires a prominent disclosure before that request. Split it with
`skip`:

```ts
import * as FieldAgent from 'expo-field-agent';

// 1 — foreground only: the location dialog, then the notifications one (API 33+)
let perms = await FieldAgent.requestPermissions({
  skip: ['backgroundLocation', 'overlay', 'dndAccess', 'fullScreenIntent',
         'batteryUnrestricted', 'autostart'],
});
if (perms.location !== 'granted') return;   // nothing else is worth asking

// 2 — your own disclosure screen, in your words, before the system takes over
await showDisclosure();

// 3 — background only: on API 30+ this opens the App info page and resolves
//     when the user comes back, granted or not
perms = await FieldAgent.requestPermissions({
  skip: ['location', 'notifications', 'overlay', 'dndAccess',
         'fullScreenIntent', 'batteryUnrestricted', 'autostart'],
});
if (perms.backgroundLocation !== 'granted') {
  await FieldAgent.openSettings('backgroundLocation');   // the same page, one tap
}

// 4 — later, at a calm moment: the survival screens, none of them dialogs
await FieldAgent.requestPermissions({
  skip: ['location', 'backgroundLocation', 'notifications'],
});
```

What the user actually sees in step 3 on Android 11+ is the **App info** page,
not the location page. They have to tap **Permissions → Location → Allow all the
time** themselves, which is why your disclosure in step 2 has to say those exact
words. On Android 10 a real dialog still appears and can return `'granted'`
directly.

| Trap | What to do about it |
| --- | --- |
| `'undetermined'` and `'denied'` are not interchangeable | `'denied'` only exists once the permission has been asked for. Branching "send them to Settings" on `!== 'granted'` sends a first-time user to Settings instead of showing them the dialog. Branch on `'denied'`. |
| `autostart: 'granted'` is not a real reading | No API can read the manufacturer autostart state. It flips to `'granted'` the moment the user has been *sent* to the screen, toggled or not. Read it as "we showed them". |
| Tracking that dies overnight is almost never GPS | It is battery optimisation or the manufacturer's task killer. Check `batteryUnrestricted` and `autostart` first, not the GPS. |
| `backgroundLocation` can be revoked mid-shift | A manual toggle, a restore from backup, Android's auto-reset for an unused app. The platform reports no failure — fused simply stops delivering. Listen for the `BACKGROUND_LOCATION_LOST` error. |
| The key order in the returned object is not guaranteed | Pin your own display order, the way `example/App.tsx` does with `PERMISSION_ORDER`. |

### 7. Go on duty, and catch what `start()` throws

```ts
await FieldAgent.setAuthHeader(`Bearer ${token}`);  // Keystore / Keychain, never a plist
await FieldAgent.start();                           // idempotent
await FieldAgent.showBubble();                      // false on iOS, by design
```

`start({ url })` overrides `app.json` **and is persisted**, so the URL survives a
restart. It also *stays* overridden: on Android a later `start()` with no options
does **not** put the `app.json` values back — it only drops the in-memory cache
and re-reads the very same stored overrides. To go back, pass the value you want
explicitly. (iOS clears the stored overrides on a bare `start()`; that is a
divergence, not a contract — do not write code that relies on either.)

`start()` throws in three cases, and all three deserve to be shown rather than
swallowed:

| What went wrong | What to do |
| --- | --- |
| `tracking.url` is missing | Put it in `app.json`, or pass `start({ url })`. |
| `ACCESS_FINE_LOCATION` is not granted | Call `requestPermissions()` and check the returned `location` — do not assume the dialog succeeded. |
| Android refused to start the service in the background | **Not permanent.** Surface it, do not treat it as fatal. |

That third case is the one worth understanding. Android 12+ restricts background
service starts, and when the start is refused the module makes the failure
visible instead of pretending: an `error` event with code `SERVICE_START`, a
*tracking interrupted* notification on its own channel using
`notification.resumeTitle` / `resumeBody` — which says tracking is **stopped**,
the opposite of the ongoing notification, and clears itself the moment the
service is back — and a rejected promise. The persisted intention stays `true`,
so the boot receiver, the ~15 min watchdog, and the app's next return to the
foreground all retry on their own.

While the service runs, the user sees the ongoing notification: your
`notification.title`, `notification.body`, icon and colour, `setOngoing(true)`,
silent, low priority. **It cannot be removed.** It is what Android demands in
exchange for background location, and the only honest signal the user has that
they are being tracked. You control its words, not its existence — including at
runtime and in the app's own language with `setStrings()`, which persists
natively so the service still speaks that language after a reboot, when no
JavaScript is running.

`stop()` does not throw. It clears the persisted intention, disarms the watchdog
and removes the notification.

### 8. The whole thing, in one file

```tsx
import { useEffect, useState } from 'react';
import { Alert, Button, Text, View } from 'react-native';
import * as FieldAgent from 'expo-field-agent';

// Settings pages, not dialogs. Ask for them at a calm moment, never at signup.
const LATER: FieldAgent.PermissionName[] = [
  'overlay', 'dndAccess', 'fullScreenIntent', 'batteryUnrestricted', 'autostart',
];

// Play requires a prominent disclosure before the background request.
// A real app shows a full screen of its own text; this is the smallest stand-in.
const showDisclosure = () =>
  new Promise<void>((resolve) =>
    Alert.alert(
      'Location while the app is closed',
      'On the next screen, choose Permissions > Location > Allow all the time, so dispatch keeps seeing you during a job.',
      [{ text: 'Continue', onPress: () => resolve() }],
    ),
  );

export default function App() {
  const [status, setStatus] = useState('off duty');
  const [fix, setFix] = useState('no fix yet');

  useEffect(() => {
    const position = FieldAgent.addListener('position', (p) =>
      setFix(`${p.latitude.toFixed(5)}, ${p.longitude.toFixed(5)} ±${Math.round(p.accuracy)} m`),
    );
    const error = FieldAgent.addListener('error', (e) => setStatus(`${e.code}: ${e.message}`));
    return () => { position.remove(); error.remove(); };
  }, []);

  async function goOnDuty() {
    if (!FieldAgent.isAvailable) return setStatus('development build required');

    let perms = await FieldAgent.requestPermissions({ skip: ['backgroundLocation', ...LATER] });
    if (perms.location !== 'granted') return setStatus('location refused');

    await showDisclosure();
    perms = await FieldAgent.requestPermissions({ skip: ['location', 'notifications', ...LATER] });
    if (perms.backgroundLocation !== 'granted') {
      setStatus('foreground only: tracking stops once the app is closed');
    }

    await FieldAgent.setAuthHeader('Bearer <your token>');
    try {
      await FieldAgent.start();          // or start({ url: '…' }) if app.json has none
      setStatus('on duty');
    } catch (e) {
      setStatus(String(e));              // missing url · location refused · background start refused
    }
  }

  async function goOffDuty() {
    await FieldAgent.stop();
    setStatus('off duty');
  }

  return (
    <View style={{ flex: 1, gap: 12, justifyContent: 'center', padding: 24 }}>
      <Text>{status}</Text>
      <Text>{fix}</Text>
      <Button title="Go on duty" onPress={goOnDuty} />
      <Button title="Go off duty" onPress={goOffDuty} />
    </View>
  );
}
```

### 9. Verify that it is really running

```bash
adb shell dumpsys activity services <your.package> | grep isForeground   # expect isForeground=true
```

The module deliberately writes **nothing to logcat** — logcat is a ring buffer
recycled in minutes, and the failures worth reading happen hours before anyone
plugs in a cable. Its log is the on-device one: read it with `getLog()`, ship it
with `exportLog()` (both Android only), and raise `logLevel` above its `error`
default in `app.json` if you want more than errors in it.

Then swipe the app out of recents: the points keep coming and the notification
stays. The Verify table further down lists
twelve scenarios with the command for each, all runnable from the `example/` app.
One warning while testing survival: `am force-stop` is **not** a test case — a
force-stopped Android app receives nothing at all, no broadcast, no alarm, no
boot receiver, until a human launches it. Use `am kill`, or swipe from recents.

### 10. What this walkthrough delivers on iOS, and what it does not

Everything compiles and runs on iOS, and missing capabilities return an explicit
value rather than throwing, so one code path serves both platforms. But the
promise above — tracking that survives anything — is Android's.

What iOS does give you: background location (`UIBackgroundModes`,
`allowsBackgroundLocationUpdates`, `pausesLocationUpdatesAutomatically = false`,
because a rider stopped at a light is not a rider who went home), the on-disk
queue, `flush()`, the heartbeat, `setAuthHeader()` in the Keychain, the
`BACKGROUND_LOCATION_LOST` error, and significant-location-change monitoring
while the authorization is "Always" — the only mechanism that can relaunch a
terminated app.

| Step of this guide | On iOS |
| --- | --- |
| 5 — survives a swipe from recents | ⚠️ yes, unless the app is force quit |
| 5 — survives process death or a reboot | ❌ nothing restarts it, except a significant location change. No reboot survival. |
| 6 — the permission ladder | ⚠️ same two-step shape, different mechanics: when-in-use first, then `requestAlwaysAuthorization()`, then notifications. Every wait has a 60-second deadline, because a dismissed prompt changes no status and a host stuck on a spinner is worse than a refusal. |
| 6 — `openSettings(which)` | ⚠️ the argument is ignored: there is exactly one destination. Do not promise a deep link to a toggle. |
| 6 — the permission object | ⚠️ **nine keys, not ten.** `exactAlarm` is absent, so it reads `undefined` rather than `'unsupported'`. Anything iterating a fixed ten-name list renders an empty row there. |
| 7 — the ongoing "on duty" notification | ❌ does not exist. No foreground service, no channel: nothing tells the user they are tracked except the system location indicator. |
| 7 — `setInterval(seconds)` | ⚠️ iOS delivers on movement, so it moves the **distance filter** instead of a cadence. |
| 7 — `setStrings()` | ❌ a no-op. There is no notification, channel or bubble to rename, and the two location prompts come from `Info.plist`, which the system reads in the phone's language. |
| 7 — the bubble | ❌ impossible, no API exists. `showBubble()` returns `false`. The nearest equivalent is a Live Activity, not provided here. |
| 3 — `tracking.rejectMock` | ❌ `CLLocation` exposes no mock flag: the key does nothing and no `is_mock` is sent. |
| 9 — the native log and odometer | ⚠️ declared as neutral stubs: `getLog()` returns `[]`, `exportLog()` `null`, `getOdometer()` `0`, `clearLog()` and `resetOdometer()` resolve. They do not reject, and they record nothing. |
| — the full-screen alert on a locked phone | ❌ no equivalent. A `.timeSensitive` notification, `.critical` with the Critical Alerts entitlement Apple grants on a justified request (`ios.criticalAlerts: true`). |

`start()` guards the same two things as Android — a configured URL and a
location authorization — and running on "While Using" starts tracking but
immediately warns `BACKGROUND_LOCATION_LOST`: no background updates, no
significant-change monitoring. In `getState()`, `provider` is `'manager'` while
running and `'none'` otherwise, and `lastErrorAt` is always `null`.

**Promise iOS users best-effort background location with a written list of what
the platform withholds. Never promise them the Android guarantee.**

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
| `tracking.exactAlarms` | `false` | `SCHEDULE_EXACT_ALARM` stays out of the manifest and the watchdog uses an inexact alarm — see the exact-alarm section |
| `tracking.maxAccuracyMeters` | `100` | A fix the platform reports as worse than this is dropped before the queue. Minimum `1` |
| `tracking.maxSpeedMps` | `60` | Two fixes implying more than this are a GPS jump, not a journey, and the second is dropped. Minimum `1` |
| `tracking.rejectMock` | `false` | A mock fix is kept and flagged `isMock` rather than dropped |
| `notification.channelName` | `"Suivi en service"` | |
| `notification.title` | `"En service"` | |
| `notification.body` | `"Ta position est partagee pendant tes courses."` | |
| `notification.icon` | `null` | The app icon |
| `notification.color` | `"#FF6B2C"` | |
| `notification.resumeTitle` | `"Suivi interrompu"` | |
| `notification.resumeBody` | `"Android a refuse de relancer le suivi. Ouvre l'application pour reprendre."` | |
| `alert.titlePattern` | `".*"` | Every title fires the alert |
| `alert.sound` | `null` | The system alarm ringtone |
| `alert.channelName` | `"Nouvelles courses"` | |
| `alert.route` | `"field-agent-alert"` | |
| `alert.ttlSeconds` | `45` | |
| `alert.torch` | `false` | |
| `alert.forceVolume` | `true` | The alarm stream is pushed up for the alert — the only stream Android still plays on silent |
| `alert.volumeLevel` | `1` | Pushed to the device maximum. A floor, never a ceiling: a louder user keeps their level |
| `alert.channelVersion` | `1` | |
| `alert.notificationBridge` | `false` | No notification listener is declared — see the FCM section for when to turn it on |
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
| `logLevel` | `"error"` | Only errors reach the on-device log. `off` writes nothing at all; `warn`, `info` and `debug` widen it |
| `logMaxDays` | `7` | Rows older than seven days are deleted on the next write. Minimum `1` |

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
> The `example/` app in this repository runs on SDK 57 and does not need it.

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
FieldAgent.getOdometer(): Promise<number>;                             // metres, Android only
FieldAgent.resetOdometer(): Promise<void>;                             // Android only

// Log (Android only) ------------------------------------------------------
FieldAgent.getLog(opts?: { limit?: number; sinceMs?: number }): Promise<LogEntry[]>;  // newest first
FieldAgent.clearLog(): Promise<void>;
FieldAgent.exportLog(): Promise<string | null>;                        // path of the written file

// Bubble ------------------------------------------------------------------
FieldAgent.showBubble(): Promise<boolean>;                             // false on iOS, or without the permission
FieldAgent.hideBubble(): Promise<void>;
FieldAgent.setBubbleState(s: 'ok' | 'warn' | 'bad' | 'urgent', text?: string): Promise<void>;
FieldAgent.setBubbleImage(source: string | number | null): Promise<void>;  // fichier local, null = bubble.icon

// Langue ------------------------------------------------------------------
FieldAgent.setStrings(values: FieldAgentStrings | null): Promise<void>;    // null = retour a app.json

// Alert -------------------------------------------------------------------
FieldAgent.triggerAlert({ title, body?, data?, tag?, channelId? }): Promise<void>;
FieldAgent.dismissAlert(): Promise<void>;
FieldAgent.setAlertSound(enabled: boolean): Promise<void>;             // host-side mute
FieldAgent.getPendingAlert(): Promise<AlertPayload | null>;
FieldAgent.getPendingAlertSync(): AlertPayload | null;

// Events ------------------------------------------------------------------
FieldAgent.addListener('position' | 'sent' | 'error' | 'alert' | 'bubblePress' | 'providerChange', cb): Subscription;
```

`Permissions` carries ten keys:

| key | what it is |
|---|---|
| `location` | `ACCESS_FINE_LOCATION` |
| `backgroundLocation` | "allow all the time" |
| `notifications` | `POST_NOTIFICATIONS` (Android 13+) |
| `overlay` | `SYSTEM_ALERT_WINDOW` — the bubble, **and** the background-launch exemption |
| `batteryUnrestricted` | the system battery-optimisation list |
| `dndAccess` | `ACCESS_NOTIFICATION_POLICY` |
| `fullScreenIntent` | **addition** — Android 14 puts `setFullScreenIntent` behind a special access. Without it the locked-screen alert silently degrades into an ordinary notification, so the state is made visible rather than assumed. |
| `notificationAccess` | **addition** — the system notification-access screen, needed only by the opt-in `alert.notificationBridge`. `unsupported` unless you turned the bridge on. |
| `autostart` | **addition** — the manufacturer's autostart screen. No API reads it: `granted` once the user has been sent there, `undetermined` before, `unsupported` on a brand with no known screen. |
| `exactAlarm` | **addition** — `SCHEDULE_EXACT_ALARM`, for the opt-in `tracking.exactAlarms`. `unsupported` until you opt in, because without the opt-in the permission is not even in the manifest and sending the user to grant it would grant nothing. Once opted in: `granted` below Android 12, where the alarm is exact without asking, and `granted` / `denied` from `canScheduleExactAlarms()` above it. |

### When background location disappears mid-shift

`backgroundLocation` can be revoked while the service is already running — a
manual toggle in Settings, a restore from backup, Android's own auto-reset for
an app left unused for months, or a driver picking "While Using" on iOS. The
platform does not report this as a failure: fused location simply stops
delivering fixes once the app leaves the foreground, and iOS clears
`allowsBackgroundLocationUpdates` on its own. No exception, no callback, no
error — the driver just vanishes from the map.

Both platforms now emit an `error` event with code **`BACKGROUND_LOCATION_LOST`**
the moment they notice, and only once until the permission comes back. Android
checks at every service start and every heartbeat; iOS checks on every
authorization change. Treat it as loud: the shift is no longer being recorded,
and only the driver can fix it from system settings.

Two keys beyond the original contract, because without them the alert and the
tracking break on Android 14+ and on MIUI/EMUI/ColorOS **without saying a word**.

### Exact alarms — `SCHEDULE_EXACT_ALARM` only, and only if you ask

Android has two exact-alarm permissions. This plugin will only ever declare one
of them, and that is a policy, not an oversight.

`USE_EXACT_ALARM` is granted at install time and asks the user nothing, which is
exactly why Google Play reserves it for clocks, timers and calendar apps. A
delivery app that ships it gets the release rejected. It is **never** declared
here, opted in or not — the same refusal, for the same reason, as
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which this module has never requested
either and never will.

`SCHEDULE_EXACT_ALARM` is the one a user grants from a settings screen. It goes
into the manifest **only** when you set `tracking.exactAlarms: true`, mirroring
`alert.notificationBridge`: a permission the host did not ask for is a store
review the host did not sign up for.

```json
["expo-field-agent", { "tracking": { "exactAlarms": true } }]
```

**What the opt-in actually buys.** The watchdog alarm is what wakes the process
every ~15 minutes to check that tracking is still alive and restart the service
if it is not. From Android 12, starting a foreground service from the background
is forbidden outside a short list of exemptions — and an inexact
`setAndAllowWhileIdle` alarm is **not** on that list, while an exact
`setExactAndAllowWhileIdle` one is. Without the opt-in the watchdog still fires
and still tries; it is simply refused more often, and the restart then waits for
the next time the app comes to the foreground. With it, the restart is allowed
on the spot. That is the whole difference: not accuracy, permission to act.

Walk the user through it like any other special access:

```ts
const { exactAlarm } = await FieldAgent.getPermissions();
if (exactAlarm === 'denied') await FieldAgent.openSettings('exactAlarm');
```

It is a build-time decision. Unlike the cadence keys, it cannot be switched on
from `start()`: the permission has to be in the manifest before the app ships,
and nothing at runtime can put it there.

The inexact path is a fallback and not an `else`: the permission can be revoked
between two ticks, so `canScheduleExactAlarms()` is re-checked at every arming
and a `SecurityException` falls straight back to the inexact alarm rather than
losing the watchdog altogether.

### What `getState()` returns

```ts
type TrackingState = {
  running: boolean;
  queued: number;                            // points waiting on disk
  lastFixAt: number | null;                  // Unix ms, taken from the fix
  lastSentAt: number | null;                 // Unix ms of the last accepted POST
  lastError: string | null;                  // "CODE: message" — the last error, not a history
  lastErrorAt: number | null;                // when that error happened
  provider: 'fused' | 'manager' | 'none';    // what is actually acquiring
  locationEnabled: boolean;                  // the system location switch
};
```

The last three are additions, and each of them answers a question that used to
have no answer from the outside:

- **`lastErrorAt`** — an error with no date cannot be triaged. "Queue full" from
  three days ago and "queue full" from three seconds ago are the same string,
  and only one of them is an incident. A warning never overwrites the pair; only
  a real error does.
- **`provider`** — `'fused'` is Google Play Services, `'manager'` is the
  `LocationManager` fallback on a device that has none (recent Huawei handsets,
  stripped ROMs), `'none'` when nothing is running. The two were never the same
  thing and nothing said which one you had; a coarser cadence has a cause now.
- **`locationEnabled`** — the system switch. `false` explains an absence of
  points on its own, and is the first thing worth checking before blaming the
  service.

**On iOS these three are absent from the payload and read as `undefined`.** The
iOS tracker returns the original five keys. Treat them as Android-only until
that changes.

### `providerChange` — location itself switched off

Android only. A driver who pulls down the shade and taps the location tile, or
turns on airplane mode, disappears from the map with the service still running
and still green: fused location simply stops delivering, with no callback and no
exception. The `PROVIDERS_CHANGED` broadcast is the only signal there is.

```ts
FieldAgent.addListener('providerChange', ({ enabled, gps, network }) => {
  if (!enabled) showBanner('Location is off — nothing is being recorded.');
});
```

`enabled` is `gps || network`; the two flags are there for the case where only
one provider went away. When location goes off while tracking is wanted, an
`error` of code **`LOCATION_OFF`** is raised as well. When it comes back the
service re-requests updates on its own — fused does not resume a request that
was dropped while the providers were down — so the host has nothing to do.

iOS has no equivalent broadcast and **never emits this event.** A listener there
is inert, not wrong.

### The native log — `getLog()`, `clearLog()`, `exportLog()`

Android only. The failures worth reading happen on a phone in a van, hours
before anyone plugs it into adb, and logcat is a ring buffer the system recycles
in minutes. This log is a SQLite table in the app's own storage, written by the
**service**, which means it keeps recording through a process death, a reboot and
a whole shift with no JavaScript running anywhere.

```ts
const entries = await FieldAgent.getLog({ limit: 100 });
// [{ at: 1758546185123, level: 'error', code: 'FOREGROUND', message: '…' }, …]

const path = await FieldAgent.exportLog();   // cache file, oldest first, or null
await FieldAgent.clearLog();
```

- Newest first, `limit` defaults to `500`, `sinceMs` to `0`. Both are validated
  before anything else: a non-integer `limit`, a `limit` below 1, a negative or
  non-finite `sinceMs` **throw**, in Expo Go as everywhere, because they are bugs
  in your code and not platform limits.
- `exportLog()` writes the whole log to `cacheDir/field-agent/log-export.txt`,
  one line per entry, oldest first — the order an incident is read in — and
  returns the absolute path, or `null` when the write failed. Timestamps are UTC
  with a fixed locale, so a phone set to Arabic does not hand out
  Eastern-Arabic digits and a phone in Tunis does not stamp +01 next to a server
  timeline in UTC.
- `logLevel` is the floor: `error` by default, `off` writes nothing at all. A log
  that records every fix is a log nobody reads and a database that grows on its
  own.
- Retention is two bounds at once: rows older than `logMaxDays` are deleted on
  the next write, and the table is trimmed to a hard **10 000 rows** that is not
  configurable. A disk-full or corrupt database never crashes the service; the
  write is simply lost.
- **No position is ever written to it**, and no auth header. The export leaves
  the device the moment someone taps a button, and a position is personal data.
  What goes in is codes and reasons: `FOREGROUND`, `LOCATION_OFF`, `NO_FIX`,
  `STALE`, `OFFLINE`, `PROVIDER`.

### The odometer

Android only. Metres accumulated natively since the last reset, across process
deaths and reboots:

```ts
const metres = await FieldAgent.getOdometer();
await FieldAgent.resetOdometer();            // e.g. at the start of a shift
```

It only counts steps between fixes the quality filter **kept**, and it ignores
any step shorter than the worse of the two accuracies: GPS noise on a scooter
parked overnight would otherwise bill tens of kilometres by morning. It is
therefore a floor, not a billing meter — a tunnel or a signal loss is distance
it does not claim to have seen.

### Point quality — accuracy, speed, mock locations

Three keys control what the filter accepts, and they are configuration rather
than constants because a scooter in a dense city and a van on a motorway do not
have the same idea of an impossible jump:

| key | default | what it rejects |
|---|---|---|
| `tracking.maxAccuracyMeters` | `100` | A fix the platform itself reports as worse than this: a cell-tower guess, not a position |
| `tracking.maxSpeedMps` | `60` | Two fixes implying a higher speed — 60 m/s is 216 km/h, and above that it is a GPS jump, not a vehicle |
| `tracking.rejectMock` | `false` | With `true`, every fix Android flags as coming from a mock provider |

Two things happen whatever you configure. Every point now carries `isMock` on the
`position` event and `is_mock` in the POST payload, so a server that bills by the
kilometre can decide for itself rather than having the decision made on the
phone. And the plausibility guards no longer measure elapsed time with the wall
clock when both fixes carry the device's own uptime: the wall clock is what a
driver can wind forward from the settings to buy the "tunnel" exemption and
smuggle a teleport through, and uptime is not.

With `rejectMock: true`, the first rejected mock fix raises an `error` of code
**`MOCK_LOCATION`** — once per run, because fraud has to be visible but a log
flood helps nobody.

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

## Language, and the bubble picture

### `setStrings()` — the app's language, not the phone's

Every string the plugin shows a driver can be replaced at runtime, from the
translations your app already has:

```ts
await FieldAgent.setStrings({
  serviceChannelName: 'On-duty tracking',
  serviceTitle: 'On duty',
  serviceBody: 'Your position is shared while you work.',
  alertChannelName: 'New jobs',
  alertChannelNameSilent: 'New jobs (muted)',
  dismiss: 'Dismiss',
  bubbleLabel: 'Tracking',
  bubbleAccessibility: '%s — tap to open the app',
  resumeTitle: 'Tracking stopped',
  resumeBody: 'Android would not restart tracking. Open the app to resume.',
});
```

Every key is optional: an omitted one keeps the `app.json` value, and
`setStrings(null)` drops all of them. Call it once at startup and again whenever
the user changes language.

**Why runtime rather than `values-ar/strings.xml`.** A per-locale resource
follows the *phone*. A rider app almost always carries its own language picker,
and a phone in French says nothing about a driver who chose Arabic in the app.
This is the only mechanism that follows the app.

**It is persisted on purpose.** The service comes back after a reboot with no
JavaScript running anywhere; a language held in memory would come back as the
default, and the driver would find a notification in a language they never
picked.

**Channels are renamed on the spot.** A notification channel freezes its
importance, sound and vibration at creation — but not its name, and re-creating
it with the same id updates exactly that. Without this pass a driver switching
to Arabic would keep a French channel name in system settings until they
uninstall. The ongoing service notification is rebuilt in the same call.

**What it cannot do.** The `error` event carries a stable `code` (`OFFLINE`,
`VOLUME`, `QUEUE_FULL`…) and a developer-facing `message`; translate from the
code, the message is for your logs. And on iOS this is a no-op: there is no
service notification, no channel and no bubble, and the two location prompts are
read from `Info.plist` by the system in the phone's language — localize those
with `InfoPlist.strings`, nothing at runtime can change them.

### `setBubbleImage()` — swapping the picture during a shift

`bubble.icon` in `app.json` is the picture bundled at build time. This changes it
while the app runs — the job type, a photo your code just downloaded:

```ts
await FieldAgent.setBubbleImage('file:///data/user/0/…/client.jpg');
await FieldAgent.setBubbleImage(null);   // back to bubble.icon
```

It accepts a `file://` uri, an absolute path, a `content://` uri, or the result
of `require('./x.png')`. **Local sources only** — downloading belongs to the host,
which owns the auth, the cache and the retry policy, and the bubble has to stay a
cheap window. An `http(s)` source is refused with an `error` of code
`BUBBLE_IMAGE` rather than silently ignored.

Bounds are read before the pixels are, so a 12 megapixel photo is sampled down
instead of decoded whole into a 24dp slot. A picture that fails to load falls
back to the plain state dot, never to an empty gap. The path is persisted, so the
bubble keeps it when the service restarts with no JS.

**In a development build, `require()` is served by Metro over http** and is
therefore refused, with an error saying so. Bundled images belong in
`bubble.icon`, which is a real drawable in every build type.

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
| Watchdog on exact alarms | ⚠️ opt-in `SCHEDULE_EXACT_ALARM` only, never `USE_EXACT_ALARM`; without it the restart is refused more often | ❌ not applicable, there is no `AlarmManager` and nothing to restart anyway |
| On-device log (`getLog`, `clearLog`, `exportLog`) | ✅ SQLite written by the service, survives process death and reboot | ⚠️ **records nothing**, but declared as neutral stubs: `getLog()` returns `[]`, `exportLog()` `null`, `clearLog()` resolves. They never reject, so one code path serves both platforms. |
| Odometer (`getOdometer`, `resetOdometer`) | ✅ accumulated over accepted fixes, persisted | ⚠️ same: `getOdometer()` returns `0`, `resetOdometer()` resolves, nothing is counted |
| `providerChange` event | ✅ `PROVIDERS_CHANGED` receiver, plus a `LOCATION_OFF` error and an automatic re-request when it comes back | ❌ no equivalent broadcast; iOS never emits it |
| `getState().provider` / `.locationEnabled` / `.lastErrorAt` | ✅ | ⚠️ answered, not omitted: `provider` is `'manager'` while running and `'none'` otherwise, `locationEnabled` is real, `lastErrorAt` is always `null` |
| Mock-location rejection (`tracking.rejectMock`) | ✅ `Location.isMock`, and `isMock` on every point | ❌ `CLLocation` exposes no such flag; the key does nothing and no `is_mock` is sent |

The plugin **compiles and runs on iOS in every case**. Missing capabilities
return an explicit value (`false`, `"unsupported"`), never an exception.

**Including the log and the odometer, as of 1.6.0.** The five functions for the
native log and the odometer are implemented on Android only, but they are
*declared* on the iOS module as neutral stubs, so a call there resolves with
`[]`, `null`, `0` or nothing at all instead of rejecting. Branching on
`Platform.OS === 'android'` is still worth doing — an empty log rendered as a
log is a lie — but it is a display choice now, not a crash to avoid. In Expo Go,
where there is no native module at all, the neutral values in the degradation
table apply instead.

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
   background start, the failure is visible — an `error` with code
   `SERVICE_START`, a **rejected `start()` promise**, and a *tracking
   interrupted* notification on its own channel, which says tracking is stopped
   rather than the opposite and clears itself the moment the service is back.
   `tracking.exactAlarms` makes the watchdog's own restart far less likely to be
   refused; the exact-alarm section says what that permission costs.
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
5. **Ringing while silenced** — the `USAGE_ALARM` stream. **This is the answer to
   "make it ring even on silent": the alarm stream is the only one Android keeps
   playing in silent and vibrate mode.** Raising the notification or the ring
   stream instead would change nothing, which is why neither is touched. How far
   it is pushed is `alert.volumeLevel` (0 to 1 of the device maximum, `1` by
   default), and `alert.forceVolume: false` turns the push off entirely for a
   host that would rather respect the user's own level. The raise is a **floor,
   never a ceiling** — someone who already keeps their alarm louder keeps it, and
   no previous value is recorded in that case. The old volume is **saved to disk** (a process killed mid-alert would
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

### Every arrival path, and what actually happens

| How the alert arrives | Full-screen screen? |
|---|---|
| Your server's response on `POST /positions` | ✅ needs no push at all — the service made the request |
| FCM **data-only**, app open or backgrounded | ✅ via `addNotificationReceivedListener` → `triggerAlert()` |
| FCM **data-only**, app killed | ✅ via a headless `expo-task-manager` task → `triggerAlert()` |
| FCM **with a `notification` block**, app backgrounded or killed | ✅ **only** with `alert.notificationBridge: true` — otherwise ❌ |
| App force-stopped from system settings | ❌ nothing reaches it, ever. Not fixable by any app |
| iOS, any path | ❌ no full-screen exists. A `.timeSensitive` notification, `.critical` with Apple's entitlement |

### The message should be data-only — and why no client code fixes it otherwise

When an FCM message carries a `notification` block and your app is not in the
foreground, the Firebase SDK posts that notification to the system tray
**itself** and never calls into your app. No `onMessageReceived`, no background
task, no `triggerAlert()`. Writing your own `FirebaseMessagingService` does not
help: the SDK short-circuits before any service you could register.

The free fix is on the sender, and it is a single key:

```json
{
  "message": {
    "token": "<device token>",
    "android": { "priority": "HIGH" },
    "data": {
      "title": "New job",
      "body": "3.2 km · 12 DT",
      "jobId": "1234"
    }
  }
}
```

No `notification` key anywhere — neither `message.notification` nor
`message.android.notification`. Every `data` value must be a string; that is an
FCM constraint, not ours.

**If you send through Expo's push service (`exp.host`) rather than raw FCM, this
is already handled for you:** Expo sends data-only under the hood and
`expo-notifications` renders the notification itself, so the background task
runs. The trap only bites when you talk to FCM directly.

### `alert.notificationBridge` — when you do not control the sender

If the push comes from a system you cannot change, one path remains: a
`NotificationListenerService`. It sees the notification *after* Android posted
it, which is the only vantage point left once the Firebase SDK has bypassed your
app.

```json
"alert": { "notificationBridge": true }
```

What it does: reads **only your own package's** notifications, matches them
against `alert.titlePattern` exactly like any other source, fires the full-screen
alert, and cancels the tray copy it replaced so the user does not get the same
event twice. Its own alert notification is excluded by id, otherwise it would
re-trigger itself forever.

What it costs, and you should weigh this before turning it on:

- It adds `BIND_NOTIFICATION_LISTENER_SERVICE` to your manifest. **Google Play
  reviews every app that carries it** and expects notification access to be core
  functionality. The plugin prints a warning at build time so this is never a
  surprise found at release.
- The user must grant notification access by hand, in a system settings screen —
  `openSettings('notificationAccess')` opens it, and `getPermissions()` reports
  `notificationAccess`. It is `unsupported` unless you turned the bridge on, and
  always `unsupported` on iOS.
- **The FCM `data` payload does not survive.** A posted notification carries its
  title, text, tag and channel — not the data map, which only ever reaches the
  app through the launch intent when the user taps. The alert arrives with
  `data.source === 'notificationBridge'` and nothing else, so the host must
  resolve the rest from its own API (`GET /api/jobs/active` in the rider app).
  This is a real limitation of the path, not of the implementation.

Off by default. Fix the sender if you can; use this when you cannot.

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
| 10 | Alert with the app closed | swipe the app from recents (or `adb shell am kill tn.exemple.fieldagent`), then an alert via the server response or a push task. **Not** `am force-stop`: a force-stopped app receives nothing until it is launched by hand | the screen opens and renders the host's component with the right data |
| 11 | Bubble: drag, snap, tap | manual + `adb shell dumpsys window \| grep fieldagent` | `bubblePress` event received; position kept after `am crash` |
| 12 | 8 h of continuous tracking | `adb shell dumpsys meminfo tn.exemple.fieldagent` hourly; `adb shell dumpsys batterystats --charged tn.exemple.fieldagent` | `TOTAL PSS` stable; see "assumed limits" for consumption |

The `example/` app replays each of these scenarios from its home screen. Its
three assets (`assets/notif.png`, `assets/bulle.png`, `assets/alerte.wav`) are
generated placeholders: replace them with yours, the plugin only copies them.

Those twelve are the functional pass. What they do not measure is **duration** —
the five states in which Android actually cuts the power, and which look fine
for the five minutes you watch them with the screen on. That harness is
separate: a zero-dependency Node mock server that records `recorded_at` for
every point received and prints the largest gap, plus one adb script per
scenario (Doze, `am kill`, background restricted through `appops`, reboot, rare
standby bucket). The verdict is a single number — **no gap larger than twice the
configured interval** — and the server exits non-zero when it is exceeded, so CI
can block on it. Everything is in
[docs/ENDURANCE.md](docs/ENDURANCE.md) and `scripts/endurance/`, including why
`am force-stop` is a case nobody comes back from. That document is in French.

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
  filter against the heartbeat, the uptime clock that a wound-forward wall clock
  cannot fool, the mock verdict, the odometer step, and the heartbeat staleness
  bound. 35 cases.
- **`Log`** — the severity ladder, the retention arithmetic and the one-line UTC
  format, with no Android at all (11 cases); then the SQLite half under
  Robolectric: rotation, the 10 000-row ceiling, the export order, and a
  database that refuses to open without taking the service down. 9 cases.
- **`Queue`** — add, ceiling, removal by identifier (including with points added
  "in flight"), survival across a restart, a file truncated by a kill. 9 cases.
- **`Volume` and `Images`** — the alarm volume as a floor and never a ceiling,
  and the bubble's bounds-before-pixels decoding. 14 cases.
- **`LocationSource`** — the fused/manager decision from availability alone
  (2 cases), then `ManagerSource` against a fake `LocationManager`: both
  providers requested, a disabled one skipped rather than thrown on, the fresher
  of the two last-known fixes kept. 4 cases.
- **`Bus`** — an error reaches the listener *and* the disk, a warning never
  erases the last error, and an error raised before `attach()` still gets out.
  6 cases.
- **`Config`** — the new keys come off the manifest, a silent host gets the
  shipped defaults, a ceiling of zero is raised to one, garbage falls back to
  errors-only, a `start()` option beats the manifest — and `exactAlarms` is
  deliberately not something a `start()` option can switch on, because the
  permission is decided at build time. 8 cases.
- **`Watchdog`** — `exactAlarm` reads `unsupported` until the host opts in, a
  refused alarm is written down instead of thrown, a provider change carries
  what is left enabled, location coming back only drags the service up when
  someone is on duty, and silence is dated only past the bound. 8 cases.
- **config plugin and the JS surface** — the produced manifest does contain the
  permissions, the `type="location"` service with `stopWithTask=false`, the boot
  receiver with its quickboot variants, the `showWhenLocked` alert activity, the
  `PROVIDERS_CHANGED` receiver, and `SCHEDULE_EXACT_ALARM` **only** when the host
  opted in; the `build.gradle` insertion is idempotent; every malformed prop
  warns and falls back instead of crashing; and Expo Go answers every call with
  its neutral value while argument errors still throw. 55 cases.

The rest is manual, assumed, and described in the table above.

### What was built, and not merely written

| Check | Result |
|---|---|
| `expo prebuild` (Android) → manifest, `res/raw`, `res/drawable`, `build.gradle` | ✅ |
| `expo prebuild` (iOS) → `Info.plist`, sound added to the Xcode project | ✅ |
| `:expo-field-agent:compileDebugKotlin` — Expo SDK 52 | ✅ zero warnings in the module's sources |
| `:expo-field-agent:testDebugUnitTest` — Geo, Log, Queue, Volume, Images, LocationSource, Bus, Config, Watchdog | ✅ 106/106 |
| `npm test` — config plugin, props, Expo Go degradation | ✅ 55/55 |
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
*while-idle* alarms, **~15 minutes**. Going below that needs an exact alarm.
`tracking.exactAlarms` opens exactly that door and no further: only
`SCHEDULE_EXACT_ALARM`, which the user grants and can take back, never the
install-time `USE_EXACT_ALARM` that Google Play reserves for clocks and
calendars. On the road the problem does not arise: every GPS fix wakes the CPU.
Measured on an emulator, not deduced.

**The heartbeat stays silent rather than lie.** Re-sending the last known
position under a fresh timestamp is the whole point of it — but past
`max(heartbeatSeconds × 4, 5 min)` measured from the moment this process
accepted that fix, it sends nothing and writes a `STALE` line to the log
instead. A phone that lost GPS in an underground car park forty minutes ago was
publishing where it used to be as where it is, and a dispatcher routing on that
sends someone to an empty street. Silence is the honest answer, and the server's
own freshness check does the rest.

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
