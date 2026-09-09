import { ConfigPlugin, createRunOncePlugin } from 'expo/config-plugins';
import path from 'path';

import { FieldAgentPluginProps, resolveProps } from './props';
import { withFieldAgentManifest } from './withAndroidManifest';
import { withFieldAgentResources } from './withAndroidResources';
import { withFieldAgentIos } from './withIosInfoPlist';

const pkg = require('../../package.json');

const withFieldAgent: ConfigPlugin<FieldAgentPluginProps | void> = (config, rawProps) => {
  // `config._internal.projectRoot` is set by prebuild; the cwd fallback covers
  // `expo config` runs outside prebuild, where mods never execute anyway.
  const projectRoot = (config as { _internal?: { projectRoot?: string } })._internal?.projectRoot ?? process.cwd();
  const props = resolveProps(rawProps || undefined, projectRoot);

  const soundExtension = props.alert.sound
    ? path.extname(props.alert.sound).toLowerCase().replace('.', '')
    : null;

  config = withFieldAgentManifest(config, { props, soundExtension });
  config = withFieldAgentResources(config, { props });
  config = withFieldAgentIos(config, { props });

  return config;
};

export default createRunOncePlugin(withFieldAgent, pkg.name, pkg.version);
