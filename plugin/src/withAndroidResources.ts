import { ConfigPlugin, withAppBuildGradle, withDangerousMod } from 'expo/config-plugins';
import fs from 'fs';
import path from 'path';

import { ANDROID_RESOURCES, ResolvedProps, SOUND_EXTENSIONS, warn } from './props';

const BLOCK_START = '// @generated begin expo-field-agent';
const BLOCK_END = '// @generated end expo-field-agent';

/**
 * Idempotent insertion of one block into app/build.gradle.
 *
 * Hand-rolled rather than pulled from `@expo/config-plugins/build/utils/…`:
 * that path is a transitive dependency's internal, and a fifteen-line replace
 * is a smaller liability than an import that moves between SDK versions.
 */
export function insertNoCompress(contents: string, extensions: string[]): string {
  const list = extensions.map((extension) => `'${extension}'`).join(', ');
  const block = [
    `    ${BLOCK_START}`,
    '    androidResources {',
    `        noCompress ${list}`,
    '    }',
    `    ${BLOCK_END}`,
  ].join('\n');

  if (contents.includes(BLOCK_START)) {
    const existing = new RegExp(`[ \\t]*${BLOCK_START}[\\s\\S]*?${BLOCK_END}`);
    return contents.replace(existing, block);
  }

  const anchor = /^android\s*\{[^\n]*$/m;
  if (!anchor.test(contents)) {
    warn("bloc `android { }` introuvable dans app/build.gradle : ajoute noCompress 'wav' a la main.");
    return contents;
  }
  return contents.replace(anchor, (match) => `${match}\n${block}`);
}

function copy(from: string, to: string): void {
  fs.mkdirSync(path.dirname(to), { recursive: true });
  fs.copyFileSync(from, to);
}

/** Removes stale copies so switching alerte.wav -> alerte.mp3 does not leave both behind. */
function removeSiblings(dir: string, basename: string, keep: string | null): void {
  if (!fs.existsSync(dir)) return;
  for (const entry of fs.readdirSync(dir)) {
    if (path.parse(entry).name !== basename) continue;
    if (keep && entry === keep) continue;
    fs.rmSync(path.join(dir, entry), { force: true });
  }
}

export const withFieldAgentResources: ConfigPlugin<{ props: ResolvedProps }> = (config, { props }) => {
  config = withDangerousMod(config, [
    'android',
    (cfg) => {
      const projectRoot = cfg.modRequest.projectRoot;
      const resRoot = path.join(cfg.modRequest.platformProjectRoot, 'app', 'src', 'main', 'res');
      const rawDir = path.join(resRoot, 'raw');
      const drawableDir = path.join(resRoot, 'drawable');

      if (props.alert.sound) {
        const source = path.resolve(projectRoot, props.alert.sound);
        const extension = path.extname(source).toLowerCase().replace('.', '');
        const filename = `${ANDROID_RESOURCES.sound}.${extension}`;
        removeSiblings(rawDir, ANDROID_RESOURCES.sound, filename);
        copy(source, path.join(rawDir, filename));
      } else {
        removeSiblings(rawDir, ANDROID_RESOURCES.sound, null);
      }

      // Single-density copies: the notification icon is a 24dp monochrome asset
      // and the bubble icon is drawn into a 48dp target, so density buckets buy
      // nothing here. Ship a 96px source if you care about xxhdpi crispness.
      for (const [asset, name] of [
        [props.notification.icon, ANDROID_RESOURCES.notificationIcon],
        [props.bubble.icon, ANDROID_RESOURCES.bubbleIcon],
      ] as const) {
        if (asset) {
          copy(path.resolve(projectRoot, asset), path.join(drawableDir, `${name}.png`));
        } else {
          removeSiblings(drawableDir, name, null);
        }
      }

      return cfg;
    },
  ]);

  return withAppBuildGradle(config, (cfg) => {
    if (cfg.modResults.language !== 'groovy') {
      warn("app/build.gradle n'est pas en Groovy : ajoute manuellement androidResources { noCompress 'wav' }.");
      return cfg;
    }
    // Without this, openRawResourceFd() throws on the compressed asset and
    // MediaPlayer never opens the alert sound — the alert is silent forever.
    cfg.modResults.contents = insertNoCompress(cfg.modResults.contents, SOUND_EXTENSIONS);
    return cfg;
  });
};
