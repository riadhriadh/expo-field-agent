import { AndroidConfig } from 'expo/config-plugins';

import { META_CONFIG, resolveProps, serializeForNative } from '../props';
import { applyManifest } from '../withAndroidManifest';

type Manifest = AndroidConfig.Manifest.AndroidManifest;

function emptyManifest(): Manifest {
  return {
    manifest: {
      $: { 'xmlns:android': 'http://schemas.android.com/apk/res/android' },
      'uses-permission': [],
      application: [{ $: { 'android:name': '.MainApplication' } }],
    },
  } as unknown as Manifest;
}

function names(nodes: unknown): string[] {
  return ((nodes ?? []) as { $: Record<string, string> }[]).map((node) => node.$['android:name']);
}

describe('applyManifest', () => {
  const props = resolveProps({ tracking: { url: 'https://api.exemple.tn/api/positions' } }, __dirname);
  const manifest = applyManifest(emptyManifest(), props, null);
  const application = manifest.manifest.application![0] as unknown as Record<string, unknown>;

  it('declares every permission the runtime asks for', () => {
    const declared = names(manifest.manifest['uses-permission']);
    for (const permission of [
      'android.permission.ACCESS_FINE_LOCATION',
      'android.permission.ACCESS_BACKGROUND_LOCATION',
      'android.permission.FOREGROUND_SERVICE',
      'android.permission.FOREGROUND_SERVICE_LOCATION',
      'android.permission.POST_NOTIFICATIONS',
      'android.permission.SYSTEM_ALERT_WINDOW',
      'android.permission.USE_FULL_SCREEN_INTENT',
      'android.permission.ACCESS_NOTIFICATION_POLICY',
      'android.permission.RECEIVE_BOOT_COMPLETED',
    ]) {
      expect(declared).toContain(permission);
    }
  });

  it('forces the alarm volume to the device maximum by default', () => {
    // An alert nobody hears is not an alert: the default has to be loud.
    expect(props.alert.forceVolume).toBe(true);
    expect(props.alert.volumeLevel).toBe(1);
  });

  it('clamps a volume level outside 0..1 instead of trusting it', () => {
    const loud = resolveProps(
      { tracking: { url: 'https://api.exemple.tn/api/positions' }, alert: { volumeLevel: 7 } },
      __dirname
    );
    expect(loud.alert.volumeLevel).toBe(1);

    const negative = resolveProps(
      { tracking: { url: 'https://api.exemple.tn/api/positions' }, alert: { volumeLevel: -2 } },
      __dirname
    );
    expect(negative.alert.volumeLevel).toBe(0);
  });

  it('keeps a partial volume level as given', () => {
    const half = resolveProps(
      { tracking: { url: 'https://api.exemple.tn/api/positions' }, alert: { volumeLevel: 0.6 } },
      __dirname
    );
    expect(half.alert.volumeLevel).toBe(0.6);
    expect(half.alert.forceVolume).toBe(true);
  });

  it('lets the host opt out of touching the volume at all', () => {
    const quiet = resolveProps(
      { tracking: { url: 'https://api.exemple.tn/api/positions' }, alert: { forceVolume: false } },
      __dirname
    );
    expect(quiet.alert.forceVolume).toBe(false);
  });

  it('carries both volume keys through to the native config blob', () => {
    const half = resolveProps(
      { tracking: { url: 'https://api.exemple.tn/api/positions' }, alert: { volumeLevel: 0.5 } },
      __dirname
    );
    const blob = JSON.parse(serializeForNative(half, null));
    expect(blob.alert.forceVolume).toBe(true);
    expect(blob.alert.volumeLevel).toBe(0.5);
  });

  it('leaves the notification listener out unless the host asked for it', () => {
    // Declaring it unasked would put the host's release through a Play review
    // for a feature they are not using.
    expect(names(application.service)).not.toContain('expo.modules.fieldagent.NotificationBridge');
  });

  it('declares the notification listener, guarded, when the host opts in', () => {
    const opted = resolveProps(
      { tracking: { url: 'https://api.exemple.tn/api/positions' }, alert: { notificationBridge: true } },
      __dirname
    );
    const withBridge = applyManifest(emptyManifest(), opted, null);
    const app = withBridge.manifest.application![0] as unknown as Record<string, unknown>;
    const bridge = (app.service as { $: Record<string, string>; 'intent-filter'?: unknown[] }[]).find(
      (node) => node.$['android:name'] === 'expo.modules.fieldagent.NotificationBridge'
    );

    expect(bridge).toBeDefined();
    // Without the BIND permission any app on the device could bind it.
    expect(bridge!.$['android:permission']).toBe(
      'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE'
    );
    expect(bridge!.$['android:exported']).toBe('true');
    expect(bridge!['intent-filter']).toBeDefined();
  });

  it('keeps the tracking service when the bridge is added next to it', () => {
    const opted = resolveProps(
      { tracking: { url: 'https://api.exemple.tn/api/positions' }, alert: { notificationBridge: true } },
      __dirname
    );
    const app = applyManifest(emptyManifest(), opted, null).manifest
      .application![0] as unknown as Record<string, unknown>;
    expect(names(app.service)).toContain('expo.modules.fieldagent.TrackingService');
    expect(names(app.service)).toContain('expo.modules.fieldagent.NotificationBridge');
  });

  it('never asks for the battery-optimization dialog that gets apps delisted', () => {
    expect(names(manifest.manifest['uses-permission'])).not.toContain(
      'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS'
    );
  });

  it('declares the tracking service as a location foreground service that survives task removal', () => {
    const service = (application.service as { $: Record<string, string> }[])[0];
    expect(service.$['android:name']).toBe('expo.modules.fieldagent.TrackingService');
    expect(service.$['android:foregroundServiceType']).toBe('location');
    expect(service.$['android:stopWithTask']).toBe('false');
  });

  it('declares a boot receiver that also listens to the quickboot variants', () => {
    const receivers = application.receiver as { $: Record<string, string>; 'intent-filter'?: unknown }[];
    const boot = receivers.find((node) => node.$['android:name'].endsWith('BootReceiver'))!;
    expect(boot.$['android:exported']).toBe('true');
    const actions = (boot['intent-filter'] as { action: { $: Record<string, string> }[] }[])[0].action.map(
      (a) => a.$['android:name']
    );
    expect(actions).toEqual(
      expect.arrayContaining([
        'android.intent.action.BOOT_COMPLETED',
        'android.intent.action.QUICKBOOT_POWERON',
        'android.intent.action.MY_PACKAGE_REPLACED',
      ])
    );
    expect(names(receivers)).toEqual(expect.arrayContaining(['expo.modules.fieldagent.WatchdogReceiver']));
  });

  it('declares the alert activity so it can draw over the lock screen', () => {
    const activity = (application.activity as { $: Record<string, string> }[])[0];
    expect(activity.$['android:name']).toBe('expo.modules.fieldagent.AlertActivity');
    expect(activity.$['android:showWhenLocked']).toBe('true');
    expect(activity.$['android:turnScreenOn']).toBe('true');
    expect(activity.$['android:excludeFromRecents']).toBe('true');
    expect(activity.$['android:launchMode']).toBe('singleTask');
  });

  it('ships the resolved configuration to the native side', () => {
    const meta = (application['meta-data'] as { $: Record<string, string> }[]).find(
      (node) => node.$['android:name'] === META_CONFIG
    )!;
    const parsed = JSON.parse(meta.$['android:value']);
    expect(parsed.tracking.url).toBe('https://api.exemple.tn/api/positions');
    expect(parsed.tracking.intervalSeconds).toBe(15);
    expect(parsed.rootComponent).toBe('main');
  });

  it('is idempotent: a second pass does not duplicate nodes', () => {
    const twice = applyManifest(applyManifest(emptyManifest(), props, null), props, null);
    const app = twice.manifest.application![0] as unknown as Record<string, unknown>;
    expect((app.service as unknown[]).length).toBe(1);
    expect((app.activity as unknown[]).length).toBe(1);
    expect((app.receiver as unknown[]).length).toBe(3);
  });
});

describe('resolveProps', () => {
  it('falls back to defaults and warns instead of throwing', () => {
    const spy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    const props = resolveProps(
      // @ts-expect-error deliberately malformed input, the point of the test
      { tracking: { url: 'pas-une-url', intervalSeconds: 'quinze' }, notification: { color: 'orange' }, oups: 1 },
      __dirname
    );
    expect(props.tracking.url).toBeNull();
    expect(props.tracking.intervalSeconds).toBe(15);
    expect(props.notification.color).toBe('#FF6B2C');
    expect(spy).toHaveBeenCalled();
    spy.mockRestore();
  });

  it('keeps a broken title pattern from reaching the native regex', () => {
    const spy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    expect(resolveProps({ alert: { titlePattern: '[' } }, __dirname).alert.titlePattern).toBe('.*');
    spy.mockRestore();
  });
});
