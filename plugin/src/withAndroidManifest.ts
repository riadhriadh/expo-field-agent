import { AndroidConfig, ConfigPlugin, withAndroidManifest as withManifest } from 'expo/config-plugins';

import { META_CONFIG, ResolvedProps, serializeForNative } from './props';

type Manifest = AndroidConfig.Manifest.AndroidManifest;

/** The manifest is plain xml2js output; typing it loosely here keeps the merge readable. */
type Node = { $: Record<string, string>; [key: string]: unknown };
type LooseApplication = Record<string, Node[] | unknown> & { $: Record<string, string> };

const PKG = 'expo.modules.fieldagent';

/**
 * Every permission the plugin needs, each one load-bearing.
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is deliberately absent: Google Play
 * rejects it for almost every app, and the rejection pulls the app from the
 * store. The system list (ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) costs
 * the user two extra taps and endangers nobody.
 */
export const PERMISSIONS = [
  'android.permission.INTERNET',
  'android.permission.ACCESS_NETWORK_STATE',
  'android.permission.ACCESS_COARSE_LOCATION',
  'android.permission.ACCESS_FINE_LOCATION',
  'android.permission.ACCESS_BACKGROUND_LOCATION',
  'android.permission.FOREGROUND_SERVICE',
  // Android 14 refuses a location foreground service without this one.
  'android.permission.FOREGROUND_SERVICE_LOCATION',
  // Android 13 turned notifications into a runtime permission.
  'android.permission.POST_NOTIFICATIONS',
  // The bubble — and the background-activity-launch exemption the alert needs.
  'android.permission.SYSTEM_ALERT_WINDOW',
  // Android 14 requires it for setFullScreenIntent. Install-time granted only
  // for calling and alarm apps; everyone else gets it as a special access the
  // user grants, which is why `fullScreenIntent` is a reported permission state.
  'android.permission.USE_FULL_SCREEN_INTENT',
  // setBypassDnd() is only honoured when this is already granted at channel creation.
  'android.permission.ACCESS_NOTIFICATION_POLICY',
  'android.permission.RECEIVE_BOOT_COMPLETED',
  'android.permission.WAKE_LOCK',
  'android.permission.VIBRATE',
];

/**
 * Les paquets « gestionnaire de batterie » des constructeurs, miroir exact de
 * `Power.CANDIDATES` cote Kotlin.
 *
 * Sans cette liste dans <queries>, le filtrage de visibilite des paquets
 * d'Android 11 rend `resolveActivity()` nul pour chacun d'eux : `Power` ne
 * trouve jamais l'ecran d'autostart, `hasManufacturerScreen()` rend false et
 * `getPermissions().autostart` annonce "unsupported". Sur MIUI, EMUI, ColorOS
 * et FunTouch — exactement les ROMs qui tuent le service quand l'ecran
 * s'eteint — le seul levier efficace n'etait donc jamais propose.
 */
export const AUTOSTART_PACKAGES = [
  'com.miui.securitycenter',
  'com.huawei.systemmanager',
  'com.coloros.safecenter',
  'com.oppo.safe',
  'com.oneplus.security',
  'com.vivo.permissionmanager',
  'com.iqoo.secure',
  'com.samsung.android.lool',
  'com.samsung.android.sm_cn',
  'com.asus.mobilemanager',
  'com.letv.android.letvsafe',
  'com.meizu.safe',
  'com.transsion.phonemanager',
  'com.evenwell.powersaving.g3',
];

/** Fusionne, jamais ecrase : d'autres plugins declarent leurs propres <queries>. */
function ensureQueries(manifest: Manifest, packages: string[]): void {
  const root = manifest.manifest as unknown as Record<string, unknown>;
  const current = (root.queries as Node[] | undefined) ?? [{ $: {} } as Node];
  const block = current[0] ?? ({ $: {} } as Node);
  const declared = (block.package as Node[] | undefined) ?? [];
  const known = new Set(declared.map((item) => item.$?.['android:name']));

  for (const name of packages) {
    if (known.has(name)) continue;
    declared.push({ $: { 'android:name': name } });
    known.add(name);
  }

  block.package = declared;
  current[0] = block;
  root.queries = current;
}

function upsert(application: LooseApplication, tag: string, node: Node): void {
  const current = (application[tag] as Node[] | undefined) ?? [];
  const kept = current.filter((item) => item.$?.['android:name'] !== node.$['android:name']);
  kept.push(node);
  application[tag] = kept;
}

/**
 * The other half of upsert, and the one that is easy to forget.
 *
 * `expo prebuild` reuses an existing android/ directory, so not adding a node is
 * not the same as it being absent: whatever a previous run wrote is still there.
 * Turning an optional feature back off has to actively withdraw its declaration,
 * otherwise the host keeps shipping the very thing they just opted out of.
 */
function remove(application: LooseApplication, tag: string, name: string): void {
  const current = application[tag] as Node[] | undefined;
  if (!current) return;
  application[tag] = current.filter((item) => item.$?.['android:name'] !== name);
}

/** Same reasoning as `remove`, for a permission a previous prebuild granted. */
function removePermission(manifest: Manifest, name: string): void {
  const permissions = manifest.manifest['uses-permission'];
  if (!permissions) return;
  manifest.manifest['uses-permission'] = permissions.filter(
    (item) => item.$?.['android:name'] !== name
  );
}

export function applyManifest(
  manifest: Manifest,
  props: ResolvedProps,
  soundExtension: string | null
): Manifest {
  for (const permission of PERMISSIONS) {
    AndroidConfig.Permissions.ensurePermission(manifest, permission);
  }

  // USE_EXACT_ALARM is never declared, opted in or not: Google Play reserves it
  // for alarm and calendar apps and pulls everyone else from the store, exactly
  // like REQUEST_IGNORE_BATTERY_OPTIMIZATIONS above. SCHEDULE_EXACT_ALARM is the
  // one a user can grant, so it is the only one on offer — and only on demand,
  // because a watchdog on inexact alarms is enough for a service that is alive.
  if (props.tracking.exactAlarms) {
    AndroidConfig.Permissions.ensurePermission(manifest, 'android.permission.SCHEDULE_EXACT_ALARM');
  } else {
    removePermission(manifest, 'android.permission.SCHEDULE_EXACT_ALARM');
  }

  ensureQueries(manifest, AUTOSTART_PACKAGES);

  const application = manifest.manifest.application?.[0] as unknown as LooseApplication | undefined;
  if (!application) {
    throw new Error('[expo-field-agent] AndroidManifest.xml sans <application> : projet invalide.');
  }

  upsert(application, 'service', {
    $: {
      'android:name': `${PKG}.TrackingService`,
      'android:exported': 'false',
      // Declared here AND passed to startForeground(): Android 14 crashes the
      // service at launch when the two disagree.
      'android:foregroundServiceType': 'location',
      // The user swiping the app away is exactly when they believe tracking
      // keeps running. Killing the service there is the bug, not the feature.
      'android:stopWithTask': 'false',
    },
  });

  upsert(application, 'receiver', {
    $: {
      'android:name': `${PKG}.BootReceiver`,
      'android:exported': 'true',
      'android:enabled': 'true',
    },
    'intent-filter': [
      {
        action: [
          { $: { 'android:name': 'android.intent.action.BOOT_COMPLETED' } },
          // Some ROMs only ever send a quickboot variant.
          { $: { 'android:name': 'android.intent.action.QUICKBOOT_POWERON' } },
          { $: { 'android:name': 'com.htc.intent.action.QUICKBOOT_POWERON' } },
          { $: { 'android:name': 'android.intent.action.MY_PACKAGE_REPLACED' } },
        ],
      },
    ],
  });

  upsert(application, 'receiver', {
    $: { 'android:name': `${PKG}.WatchdogReceiver`, 'android:exported': 'false' },
  });

  upsert(application, 'receiver', {
    $: { 'android:name': `${PKG}.AlertActionReceiver`, 'android:exported': 'false' },
  });

  // PROVIDERS_CHANGED is a protected system broadcast, so exported=false costs
  // nothing and keeps any other app from faking a location outage.
  upsert(application, 'receiver', {
    $: { 'android:name': `${PKG}.ProvidersChangedReceiver`, 'android:exported': 'false' },
    'intent-filter': [
      { action: [{ $: { 'android:name': 'android.location.PROVIDERS_CHANGED' } }] },
    ],
  } as unknown as Node);

  // Only when asked for. Declaring a notification listener the host did not
  // request would put their release through a Play review they never signed up
  // for, over a feature they are not using.
  if (props.alert.notificationBridge) {
    upsert(application, 'service', {
      $: {
        'android:name': `${PKG}.NotificationBridge`,
        'android:label': '@string/field_agent_bridge_label',
        // The system binds it from outside the app, so it is exported — and the
        // BIND permission is what keeps anything else from binding it.
        'android:exported': 'true',
        'android:permission': 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE',
      },
      'intent-filter': [
        {
          action: [{ $: { 'android:name': 'android.service.notification.NotificationListenerService' } }],
        },
      ],
    } as unknown as Node);
  } else {
    remove(application, 'service', `${PKG}.NotificationBridge`);
  }

  upsert(application, 'activity', {
    $: {
      'android:name': `${PKG}.AlertActivity`,
      'android:exported': 'false',
      'android:launchMode': 'singleTask',
      // It must never look like a normal screen of the app in the recents list.
      'android:excludeFromRecents': 'true',
      'android:taskAffinity': `${PKG}.alert`,
      'android:showWhenLocked': 'true',
      'android:turnScreenOn': 'true',
      'android:theme': '@style/Theme.FieldAgent.Alert',
      'android:configChanges':
        'keyboard|keyboardHidden|orientation|screenLayout|screenSize|smallestScreenSize|uiMode|locale|layoutDirection|fontScale|density',
      'android:windowSoftInputMode': 'adjustResize',
    },
  });

  upsert(application, 'meta-data', {
    $: { 'android:name': META_CONFIG, 'android:value': serializeForNative(props, soundExtension) },
  });

  return manifest;
}

export const withFieldAgentManifest: ConfigPlugin<{
  props: ResolvedProps;
  soundExtension: string | null;
}> = (config, { props, soundExtension }) =>
  withManifest(config, (cfg) => {
    cfg.modResults = applyManifest(cfg.modResults, props, soundExtension);
    return cfg;
  });
