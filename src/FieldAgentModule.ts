import { NativeModule, requireOptionalNativeModule } from 'expo-modules-core';

import type {
  AlertPayload,
  AlertTrigger,
  BubbleState,
  FieldAgentEventMap,
  FlushResult,
  PermissionName,
  Permissions,
  TrackingOptions,
  TrackingState,
} from './types';

export declare class FieldAgentNativeModule extends NativeModule<FieldAgentEventMap> {
  getPermissions(): Promise<Permissions>;
  requestPermissions(skip: PermissionName[]): Promise<Permissions>;
  openSettings(which: PermissionName): Promise<void>;

  start(options: Partial<TrackingOptions> | null): Promise<void>;
  stop(): Promise<void>;
  isRunning(): Promise<boolean>;
  setAuthHeader(value: string | null): Promise<void>;
  setIntervalSeconds(seconds: number): Promise<void>;
  flush(): Promise<FlushResult>;
  getState(): Promise<TrackingState>;

  showBubble(): Promise<boolean>;
  hideBubble(): Promise<void>;
  setBubbleState(state: BubbleState, text: string | null): Promise<void>;

  triggerAlert(payload: AlertTrigger): Promise<void>;
  dismissAlert(): Promise<void>;
  setAlertSound(enabled: boolean): Promise<void>;

  /**
   * Synchronous on purpose. The full-screen activity starts the JS engine and
   * the alert must already be readable at the very first render, otherwise the
   * event and the bundle race each other and the screen comes up empty.
   */
  getPendingAlertSync(): AlertPayload | null;
};

const nativeModule = requireOptionalNativeModule<FieldAgentNativeModule>('FieldAgent');

export const MISSING_NATIVE_MODULE =
  'expo-field-agent est un module natif : il ne fonctionne pas dans Expo Go. Lance `npx expo prebuild && npx expo run:android`.';

export function requireModule(): FieldAgentNativeModule {
  if (!nativeModule) throw new Error(MISSING_NATIVE_MODULE);
  return nativeModule;
}

export default nativeModule;
