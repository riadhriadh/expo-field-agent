import * as React from 'react';
import { AppState, BackHandler, Modal, Platform, StatusBar, StyleSheet, View } from 'react-native';

// Imported straight from the native wrapper rather than from ./index: the
// barrel re-exports this component, and the cycle would bite on first render.
import nativeModule from './FieldAgentModule';
import { normalizeAlert } from './normalize';
import type { AlertActions, AlertPayload } from './types';

const getPendingAlertSync = (): AlertPayload | null =>
  normalizeAlert(nativeModule?.getPendingAlertSync());

export type AlertHostProps = {
  render: (alert: AlertPayload, actions: AlertActions) => React.ReactNode;
  /** Set false to keep the hardware back button from closing the alert. */
  dismissOnBack?: boolean;
};

/**
 * Mount once, anywhere in the tree.
 *
 * The initial state is read synchronously from the native side rather than
 * waited for as an event: when the full-screen activity is what started the JS
 * engine, the alert was accepted long before any listener could exist, and an
 * event-only design loses that race every time.
 */
export function AlertHost({ render, dismissOnBack = true }: AlertHostProps): React.ReactElement | null {
  const [alert, setAlert] = React.useState<AlertPayload | null>(() => getPendingAlertSync());

  React.useEffect(() => {
    const subscription = nativeModule?.addListener('alert', (raw) => setAlert(normalizeAlert(raw)));
    // Between the first render and this subscription the native side may have
    // accepted an alert; re-reading costs one JSI call and closes the window.
    const pending = getPendingAlertSync();
    if (pending) setAlert(pending);

    // The TTL can expire while the app sits in the background; coming back to
    // an alert the native side already forgot would strand the user on it.
    const appState = AppState.addEventListener('change', (state) => {
      if (state === 'active') setAlert(getPendingAlertSync());
    });

    return () => {
      subscription?.remove();
      appState.remove();
    };
  }, []);

  const dismiss = React.useCallback(async () => {
    setAlert(null);
    try {
      await nativeModule?.dismissAlert();
    } catch {
      // A dismissal that fails natively must still close the UI: the ringtone
      // has its own TTL, a stuck screen has nothing.
    }
  }, []);

  React.useEffect(() => {
    if (!alert || !dismissOnBack || Platform.OS !== 'android') return undefined;
    const handler = BackHandler.addEventListener('hardwareBackPress', () => {
      void dismiss();
      return true;
    });
    return () => handler.remove();
  }, [alert, dismiss, dismissOnBack]);

  const actions = React.useMemo<AlertActions>(() => ({ dismiss }), [dismiss]);

  if (!alert) return null;

  return (
    <Modal
      visible
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={dismissOnBack ? () => void dismiss() : undefined}
      accessibilityViewIsModal>
      <View style={[styles.root, { paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight ?? 0 : 0 }]}>
        {render(alert, actions)}
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
});
