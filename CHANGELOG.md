# Changelog

Versions before 1.6.0 shipped without a changelog. Rather than reconstruct their
history from memory, this file starts here; `git log` is the only honest record
of what came before.

Dates are release dates. Anything marked **Android** does not exist on iOS, and
the iOS row of the platform-limits table in the README says so in writing.

---

## 1.7.0

### Fixed — le suivi se dégradait dès que l'écran s'éteignait

- **L'écran d'autostart constructeur n'était jamais proposé. Android** —
  `Power` cherche l'écran « démarrage automatique » de MIUI, EMUI, ColorOS,
  FunTouch et les autres avec `PackageManager.resolveActivity`. Depuis Android 11
  le filtrage de visibilité des paquets rend cet appel nul pour tout paquet non
  déclaré dans `<queries>`, et le plugin n'en déclarait aucun. Résultat :
  `hasManufacturerScreen()` rendait toujours `false`, `manufacturerIntent()`
  retombait toujours sur la fiche de l'application, et
  `getPermissions().autostart` annonçait `unsupported` sur les ROMs où c'est
  précisément le seul levier qui empêche le service d'être tué quelques minutes
  après le verrouillage. Le plugin écrit maintenant un bloc `<queries>` avec les
  quatorze paquets, fusionné avec celui des autres plugins plutôt qu'écrasé.

- **Le battement de cœur et les envois suivaient les réveils du système, pas
  leur cadence. Android** — un service au premier plan n'empêche pas la
  suspension du CPU. Écran éteint, téléphone immobile, le `Handler` qui cadence
  le battement gèle avec `uptimeMillis` : plus rien ne partait avant l'alarme du
  watchdog, ~15 min plus tard, et un serveur qui juge la fraîcheur déclarait
  l'agent disparu. Un `PARTIAL_WAKE_LOCK` est désormais tenu pendant toute la
  session de suivi, relâché à l'arrêt et à la destruction du service, et
  ré-acquis à chaque réveil du watchdog — un `acquire` refusé une fois ne se
  retentait jamais. Nouvelle clé `tracking.wakeLock`, à `true` par défaut, pour
  rendre l'échange à qui préfère la batterie.

### Added

- **Le service dit au démarrage ce qui va le tuer. Android** — il démarrait sans
  un mot dans une configuration dont la lib sait qu'elle ne tiendra pas :
  optimisation de batterie active, gestionnaire constructeur jamais autorisé. Le
  suivi s'arrêtait deux heures plus tard et le journal ne portait qu'un
  `service detruit` sans cause. `startTracking` écrit désormais une ligne par
  risque, une fois par run : `BATTERY_RESTRICTED`, `AUTOSTART_UNCONFIRMED`, et
  `NO_RESTART_EXEMPTION` quand ni la batterie, ni la superposition, ni l'alarme
  exacte ne donnent l'exemption dont le watchdog a besoin pour relancer un
  service tué sous Android 12+. Rien n'empêche le démarrage : ce sont des
  réglages que seul l'utilisateur accorde, et un suivi dégradé vaut mieux que
  pas de suivi.

---

## 1.6.0

### Fixed — the three that shipped in 1.5.1

These are not refactors. Each one was wrong on a phone in someone's pocket.

- **The "tracking interrupted" notification said the opposite of the truth, and
  never went away.** When Android refused to bring the service back, the plugin
  posted `buildServiceNotification` — the ongoing *on duty, your position is
  being shared* message — at the exact moment nothing was being shared. The
  rider read a green light over a dead tracker, and since the notification was
  ongoing and nobody cancelled it, it stayed there for the rest of the day.
  There is now a separate notification with its own strings
  (`notification.resumeTitle` / `notification.resumeBody`), its own channel, a
  high priority, `setOngoing(false)`, `setAutoCancel(true)`, and a tap that
  opens the app. It is cancelled the moment the service reaches the foreground,
  and on an explicit `stop()`.

- **`start()` resolved when Android had refused the start.** `TrackingService.request`
  already returned a Boolean saying the system said no; every caller threw it
  away. The host's promise kept, its "on duty" switch flipped, and no code path
  anywhere could tell that nothing was running. `start()` now rejects with the
  reason. The persisted intention is still recorded first, so boot and the
  watchdog resume on their own — the call fails, the intent survives. The same
  refusal inside `promoteToForeground()` is no longer swallowed either: the
  service stops itself cleanly and `onStartCommand` returns `START_NOT_STICKY`,
  instead of leaving Android to kill a notification-less foreground service with
  a `ForegroundServiceDidNotStartInTime` crash.

- **The heartbeat published where a phone used to be as where it is.** It
  re-sent the last known position stamped *now*, with no age bound at all. A
  rider who lost GPS in an underground car park forty minutes earlier kept
  appearing at the entrance, fresh, indefinitely — and a dispatcher routing on
  that sends someone to an empty street. That is a fabricated point, not a stale
  one. The heartbeat now stays silent past `max(heartbeatSeconds × 4, 5 min)`
  measured from the moment *this process* accepted the fix (not from the
  provider's own timestamp, which is exactly what cannot be trusted here), and
  writes a `STALE` line to the log instead.

### Fixed — others

- `setInterval()` no longer evaporates on the first process death: the override
  is persisted and re-read in `onCreate`. The cadence a host chose used to come
  back as the default precisely when nobody was around to set it again.
- Fused `requestLocationUpdates` / `getCurrentLocation` tasks now carry an
  `addOnFailureListener`. A failed task used to fail in complete silence.
- A forward wall-clock change can no longer buy the "tunnel" exemption in the
  plausibility filter. When both fixes carry `elapsedRealtimeNanos`, elapsed
  time is measured on the device's uptime clock, which nothing on the phone can
  wind back. Wall-clock timing is still the fallback when either fix lacks it.

### Added — the on-device log (Android)

- `getLog({ limit?, sinceMs? })`, `clearLog()`, `exportLog()`. A SQLite table in
  the app's own storage, written by the **service**, so it keeps recording
  through a process death, a reboot and a whole shift with no JavaScript
  running. Newest first; `exportLog()` writes
  `cacheDir/field-agent/log-export.txt` oldest first, in UTC with a fixed
  locale, and returns the path.
- New keys `logLevel` (`off` | `error` | `warn` | `info` | `debug`, default
  `error`) and `logMaxDays` (default `7`, minimum 1). Retention is two bounds at
  once: the age window, and a hard non-configurable ceiling of 10 000 rows.
- `Bus.warn()` and `Bus.info()` write to the log without raising an `error`
  event. Only a real fault reaches JS, and only a real fault overwrites the
  `lastError` pair.
- **No position and no auth header ever enters the log.** The export leaves the
  device the moment someone taps a button.
- A disk-full or corrupt database never takes the service down; the write is
  lost and nothing else.
- Argument errors throw: a non-integer `limit`, a `limit` below 1, a negative or
  non-finite `sinceMs` — in Expo Go as everywhere.

### Added — no Google Play Services, no silence (Android)

- `LocationSource` abstracts acquisition behind one code path. `FusedSource`
  when Play Services answers, `ManagerSource` — `LocationManager` on GPS *and*
  network — when it does not. Recent Huawei handsets and stripped ROMs used to
  get a Fused client that delivered nothing, forever, without a word.
- The fallback writes `PROVIDER` to the log and never fails `start()`.
- `getState()` gains `provider` (`'fused' | 'manager' | 'none'`) and
  `locationEnabled`, so "why is this rider slow to update" and "is location even
  switched on" have answers from the outside.

### Added — location switched off (Android)

- New event `providerChange` with `{ enabled, gps, network }`, from a
  `PROVIDERS_CHANGED` receiver declared `exported="false"` (it is a protected
  system broadcast). A driver who taps the location tile or turns on airplane
  mode used to vanish from the map with the service still green, because fused
  location simply stops delivering with no callback and no exception.
- Going dark while tracking is wanted also raises an `error` of code
  `LOCATION_OFF`. Coming back re-requests updates on its own, since fused does
  not resume a request dropped while the providers were down.
- iOS has no equivalent broadcast and never emits this event.

### Added — point quality and the odometer (Android)

- New keys `tracking.maxAccuracyMeters` (100), `tracking.maxSpeedMps` (60) and
  `tracking.rejectMock` (false). The old constants become the defaults, so
  nothing changes for a host that sets none of them.
- Every point now carries `isMock` on the `position` event and `is_mock` in the
  POST payload, whatever `rejectMock` says — a server that bills by the
  kilometre decides for itself rather than having the decision made on the
  phone. With `rejectMock: true`, the first rejected mock fix raises
  `MOCK_LOCATION` once per run.
- `getOdometer()` / `resetOdometer()`: metres accumulated natively across
  process deaths and reboots, over accepted fixes only, ignoring any step
  shorter than the worse of the two accuracies. GPS noise on a scooter parked
  overnight would otherwise bill tens of kilometres by morning. It is a floor,
  not a billing meter.

### Added — exact alarms, opt-in and only half of them (Android)

- New key `tracking.exactAlarms` (default `false`). When true, and only then,
  `SCHEDULE_EXACT_ALARM` is added to the manifest and the watchdog arms
  `setExactAndAllowWhileIdle`.
- **`USE_EXACT_ALARM` is never declared, opted in or not.** Google Play reserves
  it for clocks, timers and calendars, and a delivery app that ships it gets the
  release pulled — the same refusal, for the same reason, as
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which this module has never requested
  either.
- What the opt-in buys is permission, not precision: an inexact
  `setAndAllowWhileIdle` alarm is not on Android 12's list of exemptions for
  starting a foreground service from the background; an exact one is. Without
  it the watchdog still fires and still tries, and is simply refused more often.
- New permission name `exactAlarm` in `getPermissions()` — `unsupported` until
  the host opts in, then `granted` below Android 12 and `granted`/`denied` from
  `canScheduleExactAlarms()` above it. `openSettings('exactAlarm')` opens
  `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
- The inexact path is a fallback, not an `else`: the permission can be revoked
  between two ticks, so it is re-checked at every arming and a
  `SecurityException` falls back rather than losing the watchdog.

### Added — visibility

- `getState()` gains `lastErrorAt`. An error with no date cannot be triaged:
  "queue full" from three days ago and from three seconds ago were the same
  string.
- The watchdog now notices silence. A service that is running and producing
  nothing looks healthy from the outside, and that is the failure the field
  actually reports; past twice the interval (floored at the watchdog's own
  ~15 min) it raises `NO_FIX` with the age.
- `Bus.attach(context)` is called from both the module and
  `TrackingService.onCreate`, so an error raised in a process the system
  restarted on its own — with no JavaScript anywhere — is still written down.

### Added — endurance harness

- `docs/ENDURANCE.md` and `scripts/endurance/`: a zero-dependency Node mock
  server that records `recorded_at` per received point and prints the largest
  gap on exit (non-zero when the threshold is passed, so CI can block), plus one
  adb script per scenario — Doze, `am kill`, background restricted through
  `appops`, reboot, rare standby bucket. Pass criterion: no gap larger than
  twice the configured interval. Written in French.
- Robolectric and `androidx.test:core` added as test-only dependencies, with
  `unitTests.includeAndroidResources = true`, so the SQLite half of the log is
  tested rather than asserted.

### Known gaps

- **The five new functions are Android-only and are not declared on the iOS
  module at all.** On iOS `getLog`, `clearLog`, `exportLog`, `getOdometer` and
  `resetOdometer` reject instead of returning a neutral value. Branch on
  `Platform.OS === 'android'` until they are implemented or stubbed. In Expo Go,
  where there is no native module, the documented neutral values do apply.
- `getState()` on iOS still returns the original five keys: `lastErrorAt`,
  `provider` and `locationEnabled` read as `undefined` there.
- `tracking.rejectMock` does nothing on iOS — `CLLocation` exposes no mock flag
  — and no `is_mock` is sent from it.

### Changed — toolchain moved to Expo SDK 57

The module is now developed and tested against the current stack. **`peerDependencies`
still says `expo >=52.0.0`**: nothing added here needs 57 to run, and dropping hosts
that have not migrated would be a breaking change for no gain.

| | before | after |
|---|---|---|
| expo | ^52.0.0 | ^57.0.24 |
| react | 18.3.1 | 19.2.3 |
| react-native | 0.76.5 | 0.86.3 |
| expo-module-scripts | ^4.0.4 | ^56.0.3 |
| typescript | ^5.3.3 | ^5.9.3 |
| robolectric | 4.13 | 4.15.1 |
| androidx.test:core | 1.6.1 | 1.7.0 |

Four things the upgrade required, each a real incompatibility rather than a version
number:

- `expo-module-scripts` 56 ships its Jest presets as `.cjs` and its
  `babel.config.base.cjs` resolves `babel-preset-expo` as a **sibling** in
  `node_modules`, but declares no dependency on it and `expo` 57 no longer hoists one.
  `babel-preset-expo` is now an explicit devDependency; without it every Jest suite
  fails to transform.
- Robolectric has no API 36 runtime yet, and SDK 57 compiles against 36, so every
  Robolectric class refused to start. `android/src/test/resources/robolectric.properties`
  pins the test runtime to `sdk=35`. This is a harness limit, not a runtime one.
- The example pinned `kotlinVersion: 1.9.25` through `expo-build-properties`; SDK 57
  requires Kotlin >= 2.1.20 and rejects the pin. The override is gone rather than
  renumbered, and `expo-build-properties` with it, since that was all it did.
- `android.edgeToEdgeEnabled` no longer exists — Android 16 makes edge-to-edge
  mandatory — so it is removed from the example, along with the explicit
  `compileSdkVersion` / `targetSdkVersion` pins the template now owns.

`example/android` and `example/ios` were regenerated from the SDK 57 template.
Run `pod install` in `example/ios` before building for iOS: the regeneration was
done with `--no-install`.

### Tests

107 Kotlin (`GeoTest` 35, `LogPolicyTest` 11, `LogTest` 9, `QueueTest` 9,
`WatchdogTest` 9, `VolumeTest` 8, `ConfigTest` 8, `ImagesTest` 6, `BusTest` 6,
`ManagerSourceTest` 4, `LocationSourceChoiceTest` 2) and 57 Jest, all green on
Expo SDK 57 / React 19.2.3 / React Native 0.86.3. The example builds and was
exercised on an Android 16 emulator.

### Compatibility

No API break. Every new `app.json` key is optional with a default that preserves
the previous behaviour, and an invalid value warns and falls back rather than
failing the build. The only behaviour change a host can notice without changing
their configuration is deliberate: `start()` now rejects when Android refuses,
where it used to resolve, and the heartbeat goes quiet past the staleness bound
where it used to keep publishing.
