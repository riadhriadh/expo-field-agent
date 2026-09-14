/**
 * What the package does when the native side is not there — Expo Go.
 *
 * The rule this file defends: importing and calling the API must never throw
 * because of the platform. A host that only wants to build its screens in Expo
 * Go should not have to guard every call, and an app that ships a stray call
 * should not crash a user's phone.
 *
 * The exception, also tested: argument validation still throws. A missing alert
 * title is a bug in the host's code, and swallowing it here would let it reach
 * production unnoticed.
 */

jest.mock('expo-modules-core', () => ({
  requireOptionalNativeModule: () => null,
  NativeModule: class {},
}));

jest.mock('react-native', () => ({
  Platform: { OS: 'android' },
  Image: { resolveAssetSource: () => null },
}));

// Pulls in React and a .tsx surface that has nothing to do with this contract.
jest.mock('../AlertHost', () => ({ AlertHost: () => null }));

import * as FieldAgent from '../index';

describe('sans module natif (Expo Go)', () => {
  beforeEach(() => {
    jest.spyOn(console, 'warn').mockImplementation(() => undefined);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('annonce son indisponibilite au lieu de la faire deviner', () => {
    expect(FieldAgent.isAvailable).toBe(false);
  });

  it('ne lance jamais sur une limite de plateforme', async () => {
    await expect(FieldAgent.start({ url: 'https://exemple.tn/p' })).resolves.toBeUndefined();
    await expect(FieldAgent.stop()).resolves.toBeUndefined();
    await expect(FieldAgent.setAuthHeader('Bearer x')).resolves.toBeUndefined();
    await expect(FieldAgent.setInterval(30)).resolves.toBeUndefined();
    await expect(FieldAgent.openSettings('location')).resolves.toBeUndefined();
    await expect(FieldAgent.triggerAlert({ title: 'Nouvelle course' })).resolves.toBeUndefined();
    await expect(FieldAgent.dismissAlert()).resolves.toBeUndefined();
    await expect(FieldAgent.setAlertSound(false)).resolves.toBeUndefined();
    await expect(FieldAgent.setStrings({ dismiss: 'Ignorer' })).resolves.toBeUndefined();
    await expect(FieldAgent.setBubbleImage('/tmp/x.png')).resolves.toBeUndefined();
    await expect(FieldAgent.setBubbleState('ok')).resolves.toBeUndefined();
    await expect(FieldAgent.hideBubble()).resolves.toBeUndefined();
  });

  it('rend des valeurs neutres, lisibles sans connaitre la cause', async () => {
    await expect(FieldAgent.isRunning()).resolves.toBe(false);
    await expect(FieldAgent.showBubble()).resolves.toBe(false);
    await expect(FieldAgent.flush()).resolves.toEqual({ sent: 0, queued: 0 });
    await expect(FieldAgent.getPendingAlert()).resolves.toBeNull();
    expect(FieldAgent.getPendingAlertSync()).toBeNull();
  });

  it('dit pourquoi dans getState, pour que l ecran de diagnostic le montre', async () => {
    const state = await FieldAgent.getState();
    expect(state.running).toBe(false);
    expect(state.queued).toBe(0);
    expect(state.lastError).toMatch(/Expo Go/);
  });

  it('rend les huit permissions unsupported plutot que de les inventer', async () => {
    const permissions = await FieldAgent.getPermissions();
    expect(Object.values(permissions).every((value) => value === 'unsupported')).toBe(true);
    expect(permissions.location).toBe('unsupported');
    expect(permissions.notificationAccess).toBe('unsupported');
  });

  it('rend un abonnement qu on peut retirer sans precaution', () => {
    const subscription = FieldAgent.addListener('position', () => undefined);
    expect(() => subscription.remove()).not.toThrow();
  });

  it('avertit une seule fois, pas a chaque appel', async () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    await FieldAgent.stop();
    await FieldAgent.stop();
    await FieldAgent.flush();
    // Le module a deja averti dans un test precedent : ce qui compte est qu il
    // n inonde pas la console, pas le compte exact a partir d ici.
    expect(warn.mock.calls.length).toBeLessThanOrEqual(1);
  });

  it('laisse toujours passer les erreurs de programmation', async () => {
    // Celles-la ne sont pas des limites de plateforme : les avaler en Expo Go
    // les ferait decouvrir en production.
    await expect(FieldAgent.triggerAlert({ title: '' })).rejects.toThrow(/title/);
    await expect(FieldAgent.setInterval(0)).rejects.toThrow(/secondes/);
    await expect(FieldAgent.setBubbleImage('')).rejects.toThrow();
  });
});
