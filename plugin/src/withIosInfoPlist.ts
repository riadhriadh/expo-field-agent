import { ConfigPlugin, IOSConfig, withDangerousMod, withInfoPlist, withXcodeProject } from 'expo/config-plugins';
import fs from 'fs';
import path from 'path';

import { IOS_SOUND_BASENAME, ResolvedProps, warn } from './props';

/** Kept in sync with the Swift side, which reads `EXFieldAgent` from the bundle. */
const PLIST_KEY = 'EXFieldAgent';

function iosSoundFilename(props: ResolvedProps): string | null {
  if (!props.alert.sound) return null;
  return `${IOS_SOUND_BASENAME}${path.extname(props.alert.sound).toLowerCase()}`;
}

export const withFieldAgentIos: ConfigPlugin<{ props: ResolvedProps }> = (config, { props }) => {
  const soundFilename = iosSoundFilename(props);

  config = withInfoPlist(config, (cfg) => {
    const plist = cfg.modResults;

    const modes = new Set<string>(Array.isArray(plist.UIBackgroundModes) ? plist.UIBackgroundModes : []);
    modes.add('location');
    plist.UIBackgroundModes = Array.from(modes);

    plist.NSLocationWhenInUseUsageDescription =
      plist.NSLocationWhenInUseUsageDescription ?? props.ios.locationWhenInUsePermission;
    plist.NSLocationAlwaysAndWhenInUseUsageDescription =
      plist.NSLocationAlwaysAndWhenInUseUsageDescription ?? props.ios.locationAlwaysPermission;
    // Still checked by App Store review on older submission paths.
    plist.NSLocationAlwaysUsageDescription =
      plist.NSLocationAlwaysUsageDescription ?? props.ios.locationAlwaysPermission;

    plist[PLIST_KEY] = {
      trackingUrl: props.tracking.url ?? '',
      trackingBatchUrl: props.tracking.batchUrl ?? '',
      intervalSeconds: props.tracking.intervalSeconds,
      idleIntervalSeconds: props.tracking.idleIntervalSeconds,
      distanceFilterMeters: props.tracking.distanceFilterMeters,
      batchSize: props.tracking.batchSize,
      queueSize: props.tracking.queueSize,
      heartbeatSeconds: props.tracking.heartbeatSeconds,
      alertTitlePattern: props.alert.titlePattern,
      alertSound: soundFilename ?? '',
      alertTtlSeconds: props.alert.ttlSeconds,
      alertRoute: props.alert.route,
      criticalAlerts: props.ios.criticalAlerts,
    };

    return cfg;
  });

  if (!soundFilename) return config;

  config = withDangerousMod(config, [
    'ios',
    (cfg) => {
      const source = path.resolve(cfg.modRequest.projectRoot, props.alert.sound as string);
      const destination = path.join(cfg.modRequest.platformProjectRoot, cfg.modRequest.projectName!, soundFilename);
      fs.mkdirSync(path.dirname(destination), { recursive: true });
      fs.copyFileSync(source, destination);
      return cfg;
    },
  ]);

  return withXcodeProject(config, (cfg) => {
    const project = cfg.modResults;
    const groupName = cfg.modRequest.projectName!;
    const filepath = `${groupName}/${soundFilename}`;

    // A second prebuild over an existing ios/ folder would otherwise add the
    // reference twice and break the build with a duplicate-output error.
    const alreadyReferenced = Object.values(project.pbxFileReferenceSection()).some(
      (entry) => typeof entry === 'object' && entry !== null && String((entry as { path?: string }).path ?? '').includes(soundFilename)
    );
    if (alreadyReferenced) return cfg;

    try {
      IOSConfig.XcodeUtils.addResourceFileToGroup({ filepath, groupName, project, isBuildFile: true });
    } catch (error) {
      warn(
        `impossible d'ajouter ${soundFilename} au projet Xcode (${String(error)}). Ajoute-le a la main dans Build Phases > Copy Bundle Resources.`
      );
    }
    return cfg;
  });
};
