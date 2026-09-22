import { resolveProps, serializeForNative } from '../props';

const BASE = { tracking: { url: 'https://api.exemple.tn/api/positions' } };

/** Collects what the resolution complained about, without polluting the test output. */
function warnings(run: () => void): string[] {
  const spy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
  run();
  const messages = spy.mock.calls.map((call) => String(call[0]));
  spy.mockRestore();
  return messages;
}

describe('log props', () => {
  it('defaults to errors only and a week of history', () => {
    const props = resolveProps(BASE, __dirname);
    expect(props.logLevel).toBe('error');
    expect(props.logMaxDays).toBe(7);
  });

  it('keeps a level the native side understands', () => {
    expect(resolveProps({ ...BASE, logLevel: 'debug' }, __dirname).logLevel).toBe('debug');
  });

  it('falls back and warns on a level nobody implements', () => {
    let props!: ReturnType<typeof resolveProps>;
    const messages = warnings(() => {
      // @ts-expect-error deliberately outside the enum, the point of the test
      props = resolveProps({ ...BASE, logLevel: 'verbeux' }, __dirname);
    });
    expect(props.logLevel).toBe('error');
    expect(messages.some((m) => m.startsWith('[expo-field-agent]') && m.includes('logLevel'))).toBe(true);
    // The developer cannot guess the enum from the code, so the warning lists it.
    expect(messages.join(' ')).toContain('debug');
  });

  it('refuses a retention below a day', () => {
    let props!: ReturnType<typeof resolveProps>;
    const messages = warnings(() => {
      props = resolveProps({ ...BASE, logMaxDays: 0 }, __dirname);
    });
    expect(props.logMaxDays).toBe(7);
    expect(messages.some((m) => m.includes('logMaxDays'))).toBe(true);
  });

  it('refuses a retention that is not a number', () => {
    let props!: ReturnType<typeof resolveProps>;
    const messages = warnings(() => {
      // @ts-expect-error deliberately malformed input
      props = resolveProps({ ...BASE, logMaxDays: 'sept' }, __dirname);
    });
    expect(props.logMaxDays).toBe(7);
    expect(messages.some((m) => m.includes('logMaxDays'))).toBe(true);
  });

  it('keeps a valid retention as given', () => {
    expect(resolveProps({ ...BASE, logMaxDays: 30 }, __dirname).logMaxDays).toBe(30);
  });

  it('ships both at the top level of the native blob', () => {
    // Config.kt reads them next to rootComponent, not under tracking.
    const blob = JSON.parse(serializeForNative(resolveProps({ ...BASE, logLevel: 'info' }, __dirname), null));
    expect(blob.logLevel).toBe('info');
    expect(blob.logMaxDays).toBe(7);
  });
});

describe('resume notification props', () => {
  it('defaults to a message that names the action the user has to take', () => {
    const props = resolveProps(BASE, __dirname);
    expect(props.notification.resumeTitle).toBe('Suivi interrompu');
    expect(props.notification.resumeBody).toBe(
      "Android a refuse de relancer le suivi. Ouvre l'application pour reprendre."
    );
  });

  it('lets the host reword both', () => {
    const props = resolveProps(
      { ...BASE, notification: { resumeTitle: 'Suivi arrete', resumeBody: 'Rouvre l application.' } },
      __dirname
    );
    expect(props.notification.resumeTitle).toBe('Suivi arrete');
    expect(props.notification.resumeBody).toBe('Rouvre l application.');
  });

  it('falls back and warns on a non-string', () => {
    let props!: ReturnType<typeof resolveProps>;
    const messages = warnings(() => {
      // @ts-expect-error deliberately malformed input
      props = resolveProps({ ...BASE, notification: { resumeTitle: 42, resumeBody: '' } }, __dirname);
    });
    expect(props.notification.resumeTitle).toBe('Suivi interrompu');
    expect(props.notification.resumeBody).toBe(
      "Android a refuse de relancer le suivi. Ouvre l'application pour reprendre."
    );
    expect(messages.some((m) => m.includes('notification.resumeTitle'))).toBe(true);
    expect(messages.some((m) => m.includes('notification.resumeBody'))).toBe(true);
  });

  it('carries both to the native side', () => {
    const blob = JSON.parse(serializeForNative(resolveProps(BASE, __dirname), null));
    expect(blob.notification.resumeTitle).toBe('Suivi interrompu');
    expect(blob.notification.resumeBody).toContain('Ouvre');
  });
});

describe('exact alarm opt-in', () => {
  it('is off unless the host asks', () => {
    expect(resolveProps(BASE, __dirname).tracking.exactAlarms).toBe(false);
  });

  it('is on when the host asks', () => {
    expect(
      resolveProps({ tracking: { ...BASE.tracking, exactAlarms: true } }, __dirname).tracking.exactAlarms
    ).toBe(true);
  });

  it('falls back and warns on a non-boolean', () => {
    let props!: ReturnType<typeof resolveProps>;
    const messages = warnings(() => {
      // @ts-expect-error deliberately malformed input
      props = resolveProps({ tracking: { ...BASE.tracking, exactAlarms: 'oui' } }, __dirname);
    });
    expect(props.tracking.exactAlarms).toBe(false);
    expect(messages.some((m) => m.includes('tracking.exactAlarms'))).toBe(true);
  });
});

describe('point quality props', () => {
  it('defaults to the thresholds Geo.kt ships with', () => {
    const props = resolveProps(BASE, __dirname);
    expect(props.tracking.maxAccuracyMeters).toBe(100);
    expect(props.tracking.maxSpeedMps).toBe(60);
    expect(props.tracking.rejectMock).toBe(false);
  });

  it('keeps host thresholds as given', () => {
    const props = resolveProps(
      { tracking: { ...BASE.tracking, maxAccuracyMeters: 30, maxSpeedMps: 70, rejectMock: true } },
      __dirname
    );
    expect(props.tracking.maxAccuracyMeters).toBe(30);
    expect(props.tracking.maxSpeedMps).toBe(70);
    expect(props.tracking.rejectMock).toBe(true);
  });

  it('refuses a threshold at zero, which would reject every point', () => {
    let props!: ReturnType<typeof resolveProps>;
    const messages = warnings(() => {
      props = resolveProps(
        { tracking: { ...BASE.tracking, maxAccuracyMeters: 0, maxSpeedMps: 0 } },
        __dirname
      );
    });
    expect(props.tracking.maxAccuracyMeters).toBe(100);
    expect(props.tracking.maxSpeedMps).toBe(60);
    expect(messages.some((m) => m.includes('tracking.maxAccuracyMeters'))).toBe(true);
    expect(messages.some((m) => m.includes('tracking.maxSpeedMps'))).toBe(true);
  });

  it('falls back and warns on a non-boolean rejectMock', () => {
    let props!: ReturnType<typeof resolveProps>;
    const messages = warnings(() => {
      // @ts-expect-error deliberately malformed input
      props = resolveProps({ tracking: { ...BASE.tracking, rejectMock: 1 } }, __dirname);
    });
    expect(props.tracking.rejectMock).toBe(false);
    expect(messages.some((m) => m.includes('tracking.rejectMock'))).toBe(true);
  });

  it('ships the three to the native side under tracking', () => {
    const blob = JSON.parse(serializeForNative(resolveProps(BASE, __dirname), null));
    expect(blob.tracking.maxAccuracyMeters).toBe(100);
    expect(blob.tracking.maxSpeedMps).toBe(60);
    expect(blob.tracking.rejectMock).toBe(false);
  });
});

describe('unknown nested keys', () => {
  it('names a typo inside a namespace instead of swallowing it', () => {
    const messages = warnings(() => {
      // @ts-expect-error deliberately misspelled keys, the point of the test
      resolveProps({ tracking: { ...BASE.tracking, intervalSecond: 20 }, alert: { tourch: true } }, __dirname);
    });
    expect(messages.some((m) => m.includes('tracking.intervalSecond'))).toBe(true);
    expect(messages.some((m) => m.includes('alert.tourch'))).toBe(true);
  });

  it('stays silent on a configuration that uses every namespace correctly', () => {
    const messages = warnings(() => {
      resolveProps(
        {
          tracking: { ...BASE.tracking, heartbeatSeconds: 120 },
          notification: { title: 'En service' },
          alert: { torch: true },
          bubble: { label: 'Suivi', colors: { ok: '#1DB954' } },
          ios: { criticalAlerts: false },
          rootComponent: 'main',
        },
        __dirname
      );
    });
    expect(messages).toEqual([]);
  });
});
