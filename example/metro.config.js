const path = require('path');
const { getDefaultConfig } = require('expo/metro-config');

const root = path.resolve(__dirname, '..');
const config = getDefaultConfig(__dirname);

// L'exemple consomme le paquet depuis le dossier parent : Metro doit surveiller
// la source TypeScript et resoudre react/react-native une seule fois.
config.watchFolders = [root];
config.resolver.nodeModulesPaths = [
  path.resolve(__dirname, 'node_modules'),
  path.resolve(root, 'node_modules'),
];
config.resolver.disableHierarchicalLookup = true;

module.exports = config;
