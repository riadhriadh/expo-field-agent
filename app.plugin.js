// Point d'entree du config plugin. Expo resout ce fichier avant `main`, donc il
// doit exister a la racine du paquet publie.
module.exports = require('./plugin/build');
