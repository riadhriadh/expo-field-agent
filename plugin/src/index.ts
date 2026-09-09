import withFieldAgent from './withFieldAgent';

export { applyManifest, PERMISSIONS } from './withAndroidManifest';
export { resolveProps } from './props';
export type { FieldAgentPluginProps } from './props';

// Expo's plugin resolver accepts either the module or its `.default` export.
export default withFieldAgent;
