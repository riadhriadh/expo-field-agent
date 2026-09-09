import * as FieldAgent from 'expo-field-agent';
import { AlertHost } from 'expo-field-agent';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';

/**
 * L'ordre de l'échelle Android, figé : le natif rend un dictionnaire dont
 * l'ordre des clés n'est pas garanti, et une liste qui saute à chaque
 * rafraîchissement est intestable.
 */
const PERMISSION_ORDER: FieldAgent.PermissionName[] = [
  'location',
  'backgroundLocation',
  'notifications',
  'overlay',
  'fullScreenIntent',
  'dndAccess',
  'batteryUnrestricted',
  'autostart',
];

/**
 * Exercises every scenario in the README's acceptance table.
 * Deliberately ugly and deliberately complete.
 */
export default function App() {
  const [permissions, setPermissions] = useState<FieldAgent.Permissions | null>(null);
  const [state, setState] = useState<FieldAgent.TrackingState | null>(null);
  const [log, setLog] = useState<string[]>([]);
  const [sound, setSound] = useState(true);
  const [fast, setFast] = useState(false);
  const positions = useRef(0);

  const append = useCallback((line: string) => {
    setLog((current) => [`${new Date().toLocaleTimeString()}  ${line}`, ...current].slice(0, 60));
  }, []);

  const refresh = useCallback(async () => {
    setPermissions(await FieldAgent.getPermissions());
    setState(await FieldAgent.getState());
  }, []);

  useEffect(() => {
    const subscriptions = [
      FieldAgent.addListener('position', (p) => {
        positions.current += 1;
        // Coordinates are personal data: the demo shows a count, not a track.
        append(`position #${positions.current} (±${Math.round(p.accuracy)} m${p.heartbeat ? ', battement' : ''})`);
      }),
      FieldAgent.addListener('sent', (r) => append(`envoye ${r.count}, reste ${r.queued}`)),
      FieldAgent.addListener('error', (e) => append(`erreur ${e.code} — ${e.message}`)),
      FieldAgent.addListener('alert', (a) => append(`alerte "${a.title}"`)),
      FieldAgent.addListener('bubblePress', () => append('bulle touchee')),
    ];
    void refresh();
    const timer = setInterval(refresh, 3000);
    return () => {
      subscriptions.forEach((s) => s.remove());
      clearInterval(timer);
    };
  }, [append, refresh]);

  const run = useCallback(
    (label: string, action: () => Promise<unknown>) => async () => {
      try {
        const result = await action();
        append(`${label} → ${result === undefined ? 'ok' : JSON.stringify(result)}`);
      } catch (error) {
        append(`${label} ✗ ${(error as Error).message}`);
      }
      void refresh();
    },
    [append, refresh]
  );

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>expo-field-agent</Text>

        <Section title="Permissions">
          {permissions &&
            PERMISSION_ORDER.map((name) => (
              <Pressable
                key={name}
                style={styles.row}
                accessibilityRole="button"
                accessibilityLabel={`Ouvrir les reglages pour ${name}`}
                onPress={run(`openSettings(${name})`, () => FieldAgent.openSettings(name))}>
                <Text style={styles.rowLabel}>{name}</Text>
                <Text style={[styles.badge, styles[permissions[name]]]}>{permissions[name]}</Text>
              </Pressable>
            ))}
          <Button
            label="Tout demander (dans l'ordre)"
            onPress={run('requestPermissions', () => FieldAgent.requestPermissions())}
          />
          <Button
            label="Demander sans les ecrans systeme"
            onPress={run('requestPermissions(skip)', () =>
              FieldAgent.requestPermissions({
                skip: ['overlay', 'dndAccess', 'batteryUnrestricted', 'autostart', 'fullScreenIntent'],
              })
            )}
          />
        </Section>

        <Section title="Suivi">
          <Line label="running" value={String(state?.running ?? false)} />
          <Line label="queued" value={String(state?.queued ?? 0)} />
          <Line label="lastFixAt" value={format(state?.lastFixAt)} />
          <Line label="lastSentAt" value={format(state?.lastSentAt)} />
          <Line label="lastError" value={state?.lastError ?? '—'} />

          <Button label="start()" onPress={run('start', () => FieldAgent.start())} />
          {/* 10.0.2.2 = la machine hôte vue depuis l'émulateur. Le serveur de
              démo peut renvoyer une alerte sur la réponse d'un POST : c'est le
              seul chemin d'alerte qui ne demande aucun push. */}
          <Button
            label="start() vers le serveur de demo local"
            onPress={run('start(demo)', () =>
              FieldAgent.start({ url: 'http://10.0.2.2:8787/positions', intervalSeconds: 5 })
            )}
          />
          <Button label="stop()" onPress={run('stop', () => FieldAgent.stop())} />
          <Button label="flush()" onPress={run('flush', () => FieldAgent.flush())} />
          <Button
            label="setAuthHeader('Bearer demo')"
            onPress={run('setAuthHeader', () => FieldAgent.setAuthHeader('Bearer demo'))}
          />
          <Button
            label="setAuthHeader(null) — deconnexion"
            onPress={run('setAuthHeader(null)', () => FieldAgent.setAuthHeader(null))}
          />
          <Toggle
            label={fast ? 'cadence 5 s' : 'cadence 15 s'}
            value={fast}
            onChange={(next) => {
              setFast(next);
              void run('setInterval', () => FieldAgent.setInterval(next ? 5 : 15))();
            }}
          />
        </Section>

        <Section title="Bulle">
          <Button label="showBubble()" onPress={run('showBubble', () => FieldAgent.showBubble())} />
          <Button label="hideBubble()" onPress={run('hideBubble', () => FieldAgent.hideBubble())} />
          {(['ok', 'warn', 'bad', 'urgent'] as const).map((s) => (
            <Button
              key={s}
              label={`setBubbleState('${s}')`}
              onPress={run('setBubbleState', () => FieldAgent.setBubbleState(s, s.toUpperCase()))}
            />
          ))}
          {Platform.OS === 'ios' && <Text style={styles.note}>iOS : showBubble() rend false, par conception.</Text>}
        </Section>

        <Section title="Alerte">
          <Toggle
            label="son de l'alerte"
            value={sound}
            onChange={(next) => {
              setSound(next);
              void run('setAlertSound', () => FieldAgent.setAlertSound(next))();
            }}
          />
          <Button
            label="triggerAlert('Nouvelle course')"
            onPress={run('triggerAlert', () =>
              FieldAgent.triggerAlert({
                title: 'Nouvelle course',
                body: 'Lac 2 → Ariana, 14,500 DT',
                data: { orderId: 'CMD-1042', pickup: 'Lac 2' },
              })
            )}
          />
          <Button
            label="triggerAlert (titre hors motif)"
            onPress={run('triggerAlert(hors motif)', () =>
              FieldAgent.triggerAlert({ title: 'Message du support' })
            )}
          />
          {/* Le seul moyen de rejouer le scénario « app fermée, écran verrouillé » :
              armer l'alerte, puis verrouiller avant qu'elle ne parte. */}
          <Button
            label="triggerAlert dans 10 s (verrouille l'écran entre-temps)"
            onPress={() => {
              append('alerte armee pour dans 10 s');
              setTimeout(() => {
                void FieldAgent.triggerAlert({
                  title: 'Nouvelle course',
                  body: 'Ecran verrouille — Menzah 6 → Marsa, 22,000 DT',
                  data: { orderId: 'CMD-2077', pickup: 'Menzah 6' },
                });
              }, 10000);
            }}
          />
          <Button label="dismissAlert()" onPress={run('dismissAlert', () => FieldAgent.dismissAlert())} />
        </Section>

        <Section title="Journal">
          {log.map((line, index) => (
            <Text key={`${index}-${line}`} style={styles.logLine}>
              {line}
            </Text>
          ))}
        </Section>
      </ScrollView>

      {/* Monte une fois. S'affiche par-dessus tout quand une alerte arrive,
          y compris quand c'est l'alerte qui a ouvert l'application. */}
      <AlertHost
        render={(alert, actions) => (
          <View style={styles.alert}>
            <Text style={styles.alertTitle}>{alert.title}</Text>
            {!!alert.body && <Text style={styles.alertBody}>{alert.body}</Text>}
            <Text style={styles.alertData}>{JSON.stringify(alert.data ?? {}, null, 2)}</Text>
            <View style={styles.alertActions}>
              <Pressable style={[styles.alertButton, styles.accept]} onPress={actions.dismiss}>
                <Text style={styles.alertButtonText}>Accepter</Text>
              </Pressable>
              <Pressable style={[styles.alertButton, styles.refuse]} onPress={actions.dismiss}>
                <Text style={styles.alertButtonText}>Refuser</Text>
              </Pressable>
            </View>
          </View>
        )}
      />
    </View>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {children}
    </View>
  );
}

function Button({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable style={styles.button} onPress={onPress} accessibilityRole="button" accessibilityLabel={label}>
      <Text style={styles.buttonText}>{label}</Text>
    </Pressable>
  );
}

function Toggle({
  label,
  value,
  onChange,
}: {
  label: string;
  value: boolean;
  onChange: (next: boolean) => void;
}) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Switch value={value} onValueChange={onChange} accessibilityLabel={label} />
    </View>
  );
}

function Line({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

function format(timestamp: number | null | undefined): string {
  if (!timestamp) return '—';
  return new Date(timestamp).toLocaleTimeString();
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#111315' },
  content: { padding: 16, paddingTop: 64, paddingBottom: 48, gap: 16 },
  title: { color: '#fff', fontSize: 24, fontWeight: '700' },
  section: { backgroundColor: '#1B1E21', borderRadius: 12, padding: 12, gap: 8 },
  sectionTitle: { color: '#9AA4AE', fontSize: 12, textTransform: 'uppercase', letterSpacing: 1 },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 48 },
  rowLabel: { color: '#E6EAEE', fontSize: 15, flexShrink: 1 },
  rowValue: { color: '#9AA4AE', fontSize: 14 },
  badge: { fontSize: 12, paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6, overflow: 'hidden', color: '#111' },
  granted: { backgroundColor: '#1DB954' },
  denied: { backgroundColor: '#E5484D', color: '#fff' },
  undetermined: { backgroundColor: '#F5A623' },
  unsupported: { backgroundColor: '#41474D', color: '#9AA4AE' },
  button: { minHeight: 48, justifyContent: 'center', backgroundColor: '#2A3036', borderRadius: 8, paddingHorizontal: 12 },
  buttonText: { color: '#E6EAEE', fontSize: 15 },
  note: { color: '#9AA4AE', fontSize: 13, fontStyle: 'italic' },
  logLine: { color: '#8FB8A0', fontSize: 12, fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace' },
  alert: { flex: 1, backgroundColor: '#0E1113', padding: 24, justifyContent: 'center', gap: 12 },
  alertTitle: { color: '#fff', fontSize: 30, fontWeight: '800' },
  alertBody: { color: '#E6EAEE', fontSize: 18 },
  alertData: { color: '#7C868F', fontSize: 12, fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace' },
  alertActions: { flexDirection: 'row', gap: 12, marginTop: 24 },
  alertButton: { flex: 1, minHeight: 56, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  accept: { backgroundColor: '#1DB954' },
  refuse: { backgroundColor: '#3A4046' },
  alertButtonText: { color: '#fff', fontSize: 17, fontWeight: '700' },
});
