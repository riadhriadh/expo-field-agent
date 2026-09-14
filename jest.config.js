// expo-module-scripts ships the plugin preset as a module rather than a preset
// directory, so it is spread here instead of referenced through `preset`.
const preset = require('expo-module-scripts/jest-preset-plugin');

module.exports = {
  ...preset,
  rootDir: __dirname,
  // Both halves are tested: the config plugin, and the JS layer's behaviour
  // when the native module is absent.
  roots: ['<rootDir>/plugin/src', '<rootDir>/src'],
};
