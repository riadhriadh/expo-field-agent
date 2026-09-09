import { SOUND_EXTENSIONS } from '../props';
import { insertNoCompress } from '../withAndroidResources';

const GRADLE = `apply plugin: "com.android.application"

android {
    namespace 'tn.exemple.fieldagent'
    defaultConfig {
        applicationId 'tn.exemple.fieldagent'
    }
}

dependencies {
}
`;

describe('insertNoCompress', () => {
  it('adds the block inside android { }', () => {
    const result = insertNoCompress(GRADLE, SOUND_EXTENSIONS);
    expect(result).toContain('androidResources {');
    expect(result).toContain("noCompress 'wav', 'mp3', 'ogg', 'm4a', 'aac'");
    // Must land inside the android block, not after it.
    expect(result.indexOf('androidResources')).toBeLessThan(result.indexOf('dependencies'));
    expect(result.indexOf('android {')).toBeLessThan(result.indexOf('androidResources'));
  });

  it('is idempotent across repeated prebuilds', () => {
    const once = insertNoCompress(GRADLE, SOUND_EXTENSIONS);
    const twice = insertNoCompress(once, SOUND_EXTENSIONS);
    expect(twice).toBe(once);
    expect(twice.match(/androidResources/g)).toHaveLength(1);
  });

  it('rewrites the block when the extension list changes', () => {
    const once = insertNoCompress(GRADLE, ['wav']);
    const updated = insertNoCompress(once, ['wav', 'mp3']);
    expect(updated).toContain("noCompress 'wav', 'mp3'");
    expect(updated.match(/androidResources/g)).toHaveLength(1);
  });

  it('warns instead of corrupting a build.gradle it does not understand', () => {
    const spy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    const untouched = insertNoCompress('// rien ici\n', SOUND_EXTENSIONS);
    expect(untouched).toBe('// rien ici\n');
    expect(spy).toHaveBeenCalled();
    spy.mockRestore();
  });
});
