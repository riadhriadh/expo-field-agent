// expo-module-scripts ships the plugin preset as a module rather than a preset
// directory, so it is spread here instead of referenced through `preset`.
const preset = require('expo-module-scripts/jest-preset-plugin');

module.exports = {
  ...preset,
  rootDir: __dirname,
  roots: ['<rootDir>/plugin/src'],
};
