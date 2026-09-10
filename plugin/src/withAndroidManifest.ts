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

function upsert(application: LooseApplication, tag: string, node: Node): void {
  const current = (application[tag] as Node[] | undefined) ?? [];
  const kept = current.filter((item) => item.$?.['android:name'] !== node.$['android:name']);
  kept.push(node);
  application[tag] = kept;
}

export function applyManifest(
  manifest: Manifest,
  props: ResolvedProps,
  soundExtension: string | null
): Manifest {
  for (const permission of PERMISSIONS) {
    AndroidConfig.Permissions.ensurePermission(manifest, permission);
  }

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
