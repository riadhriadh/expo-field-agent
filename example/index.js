import { registerRootComponent } from 'expo';

import App from './App';

// registerRootComponent enregistre le composant sous le nom "main" ; c'est
// exactement ce nom que AlertActivity monte, cote natif, pour l'ecran plein.
registerRootComponent(App);
