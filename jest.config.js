// expo-module-scripts ships the plugin preset as a module rather than a preset
// directory, so it is spread here instead of referenced through `preset`.
// The .cjs extension is explicit: from expo-module-scripts 56 the presets are
// CommonJS files with that suffix, and the extensionless specifier resolves to
// the old layout, which pulls a babel-preset-expo that no longer ships.
const preset = require('expo-module-scripts/jest-preset-plugin');

module.exports = {
  ...preset,
  rootDir: __dirname,
  // Both halves are tested: the config plugin, and the JS layer's behaviour
  // when the native module is absent.
  roots: ['<rootDir>/plugin/src', '<rootDir>/src'],
};
