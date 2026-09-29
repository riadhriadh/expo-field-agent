# expo-field-agent

[English](README.md) · **Français** · [العربية](README.ar.md)

> **Development build obligatoire pour que quoi que ce soit se passe.**
>
> Dans Expo Go le paquet **dégrade au lieu de casser** : chaque appel rend une
> valeur neutre, `isAvailable` vaut `false`, et rien n'est suivi, affiché ni sonné.
>
> ```bash
> npx expo prebuild && npx expo run:android
> ```

Suivi GPS en arrière-plan qui survit à l'app fermée, à l'écran éteint et au
redémarrage du téléphone ; bulle flottante par-dessus les autres applications ;
alerte plein écran qui rend un composant React Native de l'application hôte,
téléphone verrouillé compris.

Android : complet. iOS : ce que la plateforme permet, et rien de plus — le
tableau des limites est plus bas, écrit noir sur blanc.

---

## Une application complète qui s'en sert

**[riadhriadh/example-expo-field-agent](https://github.com/riadhriadh/example-expo-field-agent)** — une application chauffeur
dans l'esprit d'Uber Driver : passage en service, onboarding des permissions,
bulle qui reflète l'étape, et offre de course en plein écran. Elle a été écrite à
partir de [docs/prompt-rider-app.md](docs/prompt-rider-app.md), le cahier des
charges de ce dépôt.

| La bulle par-dessus l'application | La bulle par-dessus l'écran d'accueil |
|---|---|
| <img src="https://raw.githubusercontent.com/riadhriadh/expo-field-agent/main/docs/screenshots/rider-online.png" width="240" alt="Application chauffeur en service, la bulle affiche En ligne" /> | <img src="https://raw.githubusercontent.com/riadhriadh/expo-field-agent/main/docs/screenshots/bubble-home.png" width="240" alt="La bulle flottant par-dessus l'ecran d'accueil Android" /> |

À gauche : le chauffeur est en service, le service envoie les positions, la bulle
est posée sur l'application. À droite : la même bulle **par-dessus le lanceur**,
application en arrière-plan — c'est précisément ce que JavaScript ne peut pas
faire, et la raison d'être native de ce paquet.

La carte est éteinte sur ces captures parce qu'aucune clé Google Maps n'est
configurée ; le suivi en dessous, lui, tourne — d'où les coordonnées qui
continuent de se mettre à jour.

---

## Expo Go — dégradé, jamais bloquant

La moitié native ne peut pas exister dans Expo Go : Expo Go embarque un jeu figé
de code natif dont le tien ne fait pas partie. C'est un fait de plateforme,
aucun paquet ne le change.

Ce que ce paquet en fait : **il dégrade.** L'import est sans danger, et chaque
appel rend une valeur neutre au lieu de lever, donc tu peux construire et
naviguer tes écrans dans Expo Go et garder un development build pour ce qui
demande un appareil.

```ts
import * as FieldAgent from 'expo-field-agent';

if (!FieldAgent.isAvailable) {
  // Expo Go : dis-le dans l'interface plutot que de livrer un interrupteur mort.
}
```

| Appel | Dans Expo Go |
|---|---|
| `isAvailable` | `false` |
| `getPermissions()` | toutes les clés à `'unsupported'` |
| `start()`, `stop()`, `setAuthHeader()`, `setInterval()`, `openSettings()` | se résolvent, ne font rien |
| `isRunning()` | `false` |
| `flush()` | `{ sent: 0, queued: 0 }` |
| `getState()` | `running:false`, `queued:0`, `provider:'none'`, `locationEnabled:false`, et `lastError` qui dit pourquoi |
| `getLog()` | `[]` |
| `exportLog()` | `null` |
| `getOdometer()` | `0` |
| `clearLog()`, `resetOdometer()` | se résolvent, ne font rien |
| `showBubble()` | `false` |
| `triggerAlert()`, `dismissAlert()`, `setAlertSound()`, `setStrings()`, `setBubbleImage()` | se résolvent, ne font rien |
| `getPendingAlert()` / `getPendingAlertSync()` | `null` |
| `addListener()` | un abonnement qui ne se déclenche jamais ; `.remove()` est sans risque |
| `<AlertHost>` | ne rend rien |

Un seul `console.warn` part au premier appel dégradé — une fois, pas à chaque
appel, pour rester lisible.

**Les erreurs d'argument lèvent toujours, dans Expo Go comme ailleurs.**
`triggerAlert({})` sans titre, `setInterval(0)`, un `setBubbleImage('')` vide :
ce sont des bugs dans ton code, pas des limites de plateforme. Les avaler ici les
laisserait filer en production.

**Ne confonds pas dégradation et prise en charge.** Rien n'est suivi, aucune
bulle n'est dessinée, aucune alerte ne sonne. `isAvailable` est le signal
honnête — branche ton interface dessus, et fais les vrais essais sur un
development build.

---

## En as-tu besoin ? À lire avant tout le reste

Ce module existe pour une seule situation : **quelqu'un travaille dehors, son
téléphone est dans sa poche, et ton serveur doit continuer à le voir et pouvoir
l'interrompre.** Tout le reste en découle. Si ce n'est pas ta situation, un outil
plus léger te servira mieux, et les tableaux ci-dessous le nomment.

### Sers-t'en quand

| Situation | Ce qui casse sans module natif |
|---|---|
| **Livreur / chauffeur VTC en service** — une position toutes les 15 s vers le dispatch, plus une offre de course qui doit l'atteindre écran verrouillé, téléphone en poche | Le livreur balaie l'app par habitude. JavaScript meurt avec la tâche ; les positions s'arrêtent et le dispatch croit qu'il est rentré chez lui. |
| **Technicien en tournée** — preuve de passage sur une journée entière, sous-sols et tunnels sans réseau | Les points enregistrés hors ligne vivent en mémoire et disparaissent avec le processus. La tournée revient trouée. |
| **Travailleur isolé / ronde de sécurité** — preuve de présence et rappel urgent | Un battement « je suis toujours là » qui s'arrête quand le CPU dort déclare un disparu toutes les nuits. |
| **Astreinte, ambulance, dépannage** — un appel qui doit sonner même en silencieux | Une notification normale sur un téléphone en silencieux, c'est un appel auquel personne ne répond. |
| **Flotte et logistique** — un lot rejoué après un tunnel ne doit pas créer de doublons | Retirer les points d'une file par nombre au lieu de par identifiant les duplique ou les perd dès que deux envois se chevauchent. |

### Ne t'en sers **pas** quand — voilà quoi prendre à la place

| Ce que tu veux vraiment | Prends ça |
|---|---|
| La position seulement quand l'app est ouverte à l'écran | `expo-location` seul. Pas d'échelle de permissions, pas de notification permanente, pas de service de premier plan. |
| Une position d'arrière-plan occasionnelle, au mieux, sans garantie de survivre à un redémarrage | `expo-location` + `expo-task-manager` (`startLocationUpdatesAsync`). Bien plus simple. La garantie est toute la raison d'être de ce module — si tu n'en as pas besoin, ne la paie pas. |
| Une notification push normale | `expo-notifications`. Un full-screen intent utilisé pour du contenu non urgent fait signaler ton application, et Android 14 le place derrière un accès spécial exactement pour ça. |
| Livrer dans Expo Go | Impossible. C'est un module natif, et Expo Go embarque un jeu figé de code natif dont le tien ne fait pas partie. |
| Une bulle flottante sur iOS | Ça n'existe pas et ça n'existera pas. Ni ce paquet ni un autre ne te la donnera. Lis le tableau des limites avant de la promettre à qui que ce soit. |

### Pourquoi un module natif, au fond

L'acquisition GPS n'est **pas** réécrite — elle reste celle de la plateforme
(`FusedLocationProviderClient`, `CLLocationManager`). Ce qui justifie du code
natif, c'est la liste de ce que JavaScript est structurellement incapable de
faire :

| Ce qu'il faut | Pourquoi JS ne peut pas |
|---|---|
| Survivre à l'app balayée des récents | Le moteur JS est détruit avec la tâche. Seul un service de premier plan Android déclaré `stopWithTask="false"` continue de tourner. |
| Survivre à la mort du processus et au redémarrage | `START_STICKY`, un receiver `BOOT_COMPLETED` et un watchdog `AlarmManager` sont des constructions de manifeste et de natif. Après un redémarrage, il n'y a aucun point d'entrée JS. |
| `foregroundServiceType="location"` | Android 14 plante le service quand le type manque. C'est un attribut de manifeste, résolu à la compilation. |
| Dessiner par-dessus les autres applications | Une fenêtre `TYPE_APPLICATION_OVERLAY`. React Native rend à l'intérieur de ton activité, jamais en dehors. |
| Ouvrir un écran depuis l'arrière-plan, téléphone verrouillé | Un full-screen intent, plus l'exemption de lancement en arrière-plan qu'accorde `SYSTEM_ALERT_WINDOW`. |
| Sonner alors que le téléphone est en silencieux | Le flux audio `USAGE_ALARM`, plus le focus audio pour que l'application de navigation ne couvre pas le son. |
| Une file qui survit à un kill en plein envoi | Elle doit être écrite sur disque par le processus qui fait le POST. |

**Zéro fichier natif à toucher chez toi.** Tout ce qui précède est installé par
le config plugin, depuis `app.json`.

---

## De zéro à une application qui suit — le pas à pas

Le chemin complet : dossier vide d'un côté, téléphone qui envoie sa position
écran éteint de l'autre. Chaque commande, chaque clé et chaque valeur de retour
ci-dessous sort du code de ce dépôt.

### 0. Expo Go ne fera pas tourner ça — commence par là

**Ce module ne peut pas tourner dans Expo Go.** Expo Go embarque un jeu figé de
code natif, et `expo.modules.fieldagent.*` n'en fait pas partie. Il faut un
**development build** — une application native que tu compiles toi-même. Les
étapes 4 et 5 ne sont donc pas du confort : sans elles, rien ne se passe.

Ce qui arrive quand même dans Expo Go : ça dégrade, ça ne plante pas.
`FieldAgent.isAvailable` vaut `false`, chaque appel rend une valeur neutre, et
un seul `console.warn` part au premier appel dégradé — le tableau complet est
plus haut, dans « Expo Go — dégradé, jamais bloquant ». Les erreurs d'argument,
elles, lèvent quand même : ce sont tes bugs, pas des limites de plateforme.

Branche ton interface sur `FieldAgent.isAvailable`. Un interrupteur mort est
pire qu'un bandeau « suivi indisponible ».

### 1. Créer l'application

```bash
npx create-expo-app@latest mon-app-terrain --template blank-typescript
cd mon-app-terrain
```

### 2. Installer le module

```bash
npx expo install expo-field-agent
```

La pile contre laquelle ce pas à pas est écrit — celle des `devDependencies` de
ce dépôt, donc celle contre laquelle le module est **développé et testé** :

| Paquet | Version |
|---|---|
| `expo` | `^57.0.24` |
| `react` | `19.2.3` |
| `react-native` | `0.86.3` |
| `@types/react` | `~19.2.0` |
| `typescript` | `^5.9.3` |
| `babel-preset-expo` | `^57.0.12` |
| `expo-module-scripts` | `^56.0.3` |

**Le SDK 57 n'est pas exigé.** Les `peerDependencies` du module disent
`"expo": ">=52.0.0"`, `"react": "*"`, `"react-native": "*"` : les hôtes plus
anciens, à partir du SDK 52, restent pris en charge. 57 est simplement la
version sur laquelle il est construit.

Facultatif, seulement si tu veux le lanceur du menu développeur :

```bash
npx expo install expo-dev-client
```

L'application `example/` ne le liste pas, alors même que son script de démarrage
est `expo start --dev-client` : `npx expo run:android` produit déjà à lui seul un
build de debug qui fonctionne.

### 3. Configurer `app.json`

Le bloc minimal qui marche vraiment :

```json
{
  "expo": {
    "name": "Mon App Terrain",
    "slug": "mon-app-terrain",
    "scheme": "monappterrain",
    "android": { "package": "com.exemple.monappterrain" },
    "ios": { "bundleIdentifier": "com.exemple.monappterrain" },
    "plugins": [
      [
        "expo-field-agent",
        {
          "tracking": {
            "url": "https://api.exemple.tn/api/positions"
          },
          "notification": {
            "title": "En service",
            "body": "Ta position est partagée pendant tes courses."
          },
          "ios": {
            "locationWhenInUsePermission": "Ta position sert à t'affecter les courses proches.",
            "locationAlwaysPermission": "Ta position continue à être partagée pendant tes courses, même application fermée."
          }
        }
      ]
    ]
  }
}
```

C'est vraiment tout ce qu'il faut. `["expo-field-agent"]`, sans le moindre objet
d'options, installe le plugin en entier lui aussi — **chaque clé a un défaut**,
et la liste complète est juste en dessous, dans « Installation ». La seule qui ne
peut pas en avoir est `tracking.url` : donne-la ici, ou à chaud avec
`start({ url })`. Sans l'une ni l'autre, `start()` lève.

Les défauts de `notification.*` et des deux phrases `ios.*` sont **en français**.
Si ton application parle une autre langue, écris-les ici — sinon tes
utilisateurs lisent du français.

**Ne recopie pas tout d'`example/app.json`.** C'est une configuration de
démonstration : `tracking.url` pointe sur `https://httpbin.org/post`,
`tracking.exactAlarms` y vaut `true` — donc `SCHEDULE_EXACT_ALARM` dans ton
manifeste, et une revue Play que tu n'as peut-être pas à subir —, `alert.torch`
est activé et `logLevel` est à `debug`, qui écrit une ligne par événement. Sur la
pile ci-dessus, le bloc minimal plus haut suffit.

Une valeur malformée ne fait **jamais** échouer le build : elle imprime un
`[expo-field-agent] …` sur la sortie d'erreur et le défaut s'applique. Une clé
inconnue, à la racine comme un niveau plus bas, est signalée puis ignorée
(`cle inconnue "tracking.intervalSecondes"…`). Une faute de frappe silencieuse
ressemble exactement à une fonctionnalité cassée — lis la sortie du prebuild.

### 4. `prebuild` — obligatoire, pas optionnel

```bash
npx expo prebuild --clean
```

`expo-field-agent` est un **config plugin plus du code natif**. Tout ce dont il a
besoin vit dans des fichiers de projet natifs qui n'existent pas avant le
prebuild.

Côté Android, `android/app/src/main/AndroidManifest.xml` reçoit 14
`<uses-permission>` — `INTERNET`, `ACCESS_NETWORK_STATE`,
`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`,
`SYSTEM_ALERT_WINDOW`, `USE_FULL_SCREEN_INTENT`, `ACCESS_NOTIFICATION_POLICY`,
`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `VIBRATE`, plus `SCHEDULE_EXACT_ALARM` si
et seulement si `tracking.exactAlarms: true` —, le service `TrackingService` en
`foregroundServiceType="location"` et `stopWithTask="false"`, les receivers
`BootReceiver`, `WatchdogReceiver`, `AlertActionReceiver` et
`ProvidersChangedReceiver`, l'activité `AlertActivity` en `showWhenLocked` /
`turnScreenOn` / `excludeFromRecents`, une `<meta-data>`
`expo.modules.fieldagent.CONFIG` qui porte toute la configuration résolue en un
seul blob JSON, et ton `alert.sound` copié dans
`res/raw/field_agent_alert.<ext>` avec `noCompress` ajouté à `app/build.gradle`.

Côté iOS, `ios/<Projet>/Info.plist` reçoit `UIBackgroundModes: ["location"]`, les
trois `NSLocation*UsageDescription` et un dictionnaire `EXFieldAgent` que lit le
code Swift ; le son est copié en `FieldAgentAlert.<ext>` et ajouté aux Copy
Bundle Resources.

Rien de tout ça n'est joignable depuis JavaScript. C'est toute la raison d'être
native du module.

**Refais un prebuild après chaque changement de plugin dans `app.json`.**
`expo prebuild` réutilise un dossier `android/` existant : *ne pas écrire* un
nœud n'est pas la même chose que *le retirer*. Le plugin retire activement ce
dont il n'a plus besoin (`remove()` / `removePermission()`), mais seulement si le
prebuild tourne. Désactiver `exactAlarms` ou `notificationBridge` sans refaire un
prebuild, c'est continuer à livrer exactement ce que tu viens de désactiver.

### 5. Lancer sur un vrai téléphone

```bash
npx expo run:android          # compile, installe, démarre Metro
```

```bash
npx expo run:ios
```

Sur les machines Homebrew en Ruby 3.4, CocoaPods 1.16 casse si la locale n'est
pas UTF-8 (`Unicode Normalization not appropriate for ASCII-8BIT`) — rien à voir
avec ce module non plus : `LANG=en_US.UTF-8 npx expo run:ios`.

**Un vrai téléphone, pas un émulateur**, pour tout essai qui compte. Un émulateur
Android ne produit que des positions **simulées** : avec
`tracking.rejectMock: true`, chaque point est rejeté, une seule `error` de code
`MOCK_LOCATION` part, et l'application a l'air de tourner alors que la file reste
vide et que rien n'est jamais envoyé. Le défaut est `false` — garde-le sur
émulateur. Et un émulateur n'a ni Doze réel ni tueur de tâches constructeur.

Dernier piège de cette étape : une URL en `http://` fonctionne depuis
`expo run:android` et meurt en release. C'est le gabarit de prebuild d'Expo qui
pose `usesCleartextTraffic="true"` dans le seul
`android/app/src/debug/AndroidManifest.xml` ; le module n'en déclare nulle part.
Utilise `https://`, ou écris ta propre network security config pour ta machine
de dev.

### 6. L'application minimale qui suit vraiment

Les permissions dans l'ordre imposé, `start()`, les points qui arrivent,
`stop()` :

```tsx
import { useEffect, useState } from 'react';
import { Button, Text, View } from 'react-native';
import * as FieldAgent from 'expo-field-agent';

// Les écrans de réglages : à demander plus tard, à un moment calme, jamais à l'inscription.
const ECRANS = ['overlay', 'dndAccess', 'fullScreenIntent', 'batteryUnrestricted', 'autostart'] as const;

// Ton écran d'explication, exigé par Google Play avant la demande d'arrière-plan.
// Plein texte, tes mots : « à l'écran suivant, choisis Localisation > Toujours autoriser ».
async function montrerLExplication() { /* se résout quand l'utilisateur tape « Continuer » */ }

export default function App() {
  const [point, setPoint] = useState('aucun point');
  const [enService, setEnService] = useState(false);

  useEffect(() => {
    const pos = FieldAgent.addListener('position', (p) =>
      setPoint(`${p.latitude.toFixed(5)}, ${p.longitude.toFixed(5)}`));
    const err = FieldAgent.addListener('error', (e) => console.warn(e.code, e.message));
    return () => { pos.remove(); err.remove(); };
  }, []);

  async function prendreService() {
    // 1. Premier plan seulement : la boîte de dialogue de localisation, puis celle des notifications.
    let perms = await FieldAgent.requestPermissions({ skip: ['backgroundLocation', ...ECRANS] });
    if (perms.location !== 'granted') return;   // rien d'autre ne vaut la peine d'être demandé

    // 2. Ton écran à toi, avant d'envoyer l'utilisateur dans les Réglages d'Android.
    await montrerLExplication();

    // 3. Arrière-plan seulement. Sur Android 11+ c'est la fiche de l'application qui s'ouvre,
    //    pas une boîte de dialogue : l'appel se résout quand l'utilisateur revient.
    perms = await FieldAgent.requestPermissions({ skip: ['location', 'notifications', ...ECRANS] });
    if (perms.backgroundLocation !== 'granted') {
      await FieldAgent.openSettings('backgroundLocation');   // même fiche, en raccourci
    }

    await FieldAgent.setAuthHeader('Bearer …');   // chiffré : Keystore sur Android, Keychain sur iOS
    try {
      await FieldAgent.start();                   // ou start({ url }) pour surcharger app.json
      setEnService(true);
    } catch (e) {
      console.warn('start a été refusé :', e);    // les trois cas sont juste en dessous
    }
  }

  async function quitterService() {
    await FieldAgent.stop();                      // stop() ne lève pas
    setEnService(false);
  }

  if (!FieldAgent.isAvailable) {
    return <Text>Suivi indisponible : il faut un development build.</Text>;
  }

  return (
    <View style={{ flex: 1, justifyContent: 'center', gap: 12, padding: 24 }}>
      <Text>{point}</Text>
      <Button
        title={enService ? 'Quitter le service' : 'Prendre le service'}
        onPress={enService ? quitterService : prendreService}
      />
    </View>
  );
}
```

**Pourquoi deux appels à `requestPermissions()`.** L'appel sans `skip` déroule
toute l'échelle d'un coup : localisation, notifications, arrière-plan, puis les
écrans de réglages. Or sur **Android 11+ aucune boîte de dialogue ne peut
accorder la localisation d'arrière-plan** : le module ouvre la fiche
*Informations sur l'application*, et l'utilisateur doit y taper lui-même
**Autorisations → Localisation → Toujours autoriser**. Enchaîner directement,
c'est le laisser tomber dans un écran système sans savoir pourquoi — et Google
Play exige une explication préalable. D'où la coupure en deux, et d'où
l'explication en toutes lettres à l'étape 2. Sur l'API 29 exactement, une vraie
boîte de dialogue existe encore et peut rendre `granted` directement.

L'échelle est idempotente : ce qui est déjà `granted` est sauté, donc rappeler
`requestPermissions()` après un accord partiel ne redemande que ce qui manque.
Et distingue `'undetermined'` de `'denied'` : `'denied'` n'apparaît qu'après une
première demande, donc brancher un raccourci vers les Réglages sur
`!== 'granted'` envoie un nouvel utilisateur dans les Réglages au lieu de lui
montrer la boîte de dialogue.

**`start()` lève, et il faut le montrer.** Trois cas distincts : `tracking.url`
absente, `ACCESS_FINE_LOCATION` non accordée, et **Android qui refuse un
démarrage depuis l'arrière-plan**. Le troisième est le seul qui n'est pas de ta
faute : une `error` de code `SERVICE_START` part, une notification « suivi
interrompu » s'affiche sur son propre canal, la promesse est rejetée — mais
l'intention reste persistée, donc le receiver de boot, le watchdog toutes les
~15 min et le retour au premier plan la reprennent tout seuls. Affiche-le, ne le
traite pas comme définitif.

### 7. Vérifier que le service tourne pour de bon

```bash
adb shell dumpsys activity services <ton.package> | grep isForeground   # attendu : isForeground=true
```

Le module n'écrit **rien dans logcat** : son journal part dans une base SQLite
que le service tient lui-même, justement pour survivre au kill. Lis-le depuis
l'application avec `getLog()`, ou sors-le en fichier avec `exportLog()` — la
section « Le journal natif » plus bas détaille les deux.

La section « Vérifier — un scénario, une commande » plus bas déroule les douze
scénarios, tous exécutables depuis l'application `example/`.

### Et sur iOS — ce que ce pas à pas ne te donne pas

Tout compile et s'exécute sur iOS, et les capacités absentes rendent une valeur
explicite plutôt que de lever, donc un seul chemin de code sert les deux
plateformes. Mais la promesse centrale de ce pas à pas — *un suivi qui survit à
tout* — est celle d'Android, pas celle d'iOS.

| Ce que ce pas à pas promet | Android | iOS |
|---|---|---|
| Suivi en arrière-plan | ✅ service de premier plan `type="location"` | ✅ `UIBackgroundModes: location`, `pausesLocationUpdatesAutomatically = false` |
| Survit à l'app balayée des récents | ✅ `stopWithTask=false` | ⚠️ oui, sauf *force quit* |
| Survit à la mort du processus | ✅ `START_STICKY` + watchdog ~15 min | ❌ rien ne le relance, hors changement significatif de position |
| Survit au redémarrage du téléphone | ✅ `BOOT_COMPLETED` + `QUICKBOOT_POWERON` | ❌ non |
| Notification « en service » permanente | ✅ obligatoire — c'est le prix du suivi en arrière-plan, et le seul signal honnête pour l'utilisateur | ❌ n'existe pas : rien ne le prévient, sauf l'indicateur système de localisation |
| Bulle flottante | ✅ `TYPE_APPLICATION_OVERLAY` | ❌ aucune API ; `showBubble()` rend `false` |
| Écran plein et sonnerie en silencieux | ✅ `setFullScreenIntent` + flux `USAGE_ALARM` | ⚠️ notification `.timeSensitive` ; `.critical` seulement avec l'entitlement Apple (`ios.criticalAlerts`) |
| Rejet des positions simulées | ✅ `Location.isMock`, `isMock` sur chaque point | ❌ `CLLocation` n'expose aucun drapeau : `tracking.rejectMock` ne fait rien |
| `getLog()` / `getOdometer()` | ✅ SQLite écrit par le service, survit au kill et au redémarrage | ⚠️ bouchons neutres : `[]`, `0`, `null` — ça se résout, ça ne rend rien |
| `setStrings()` | ✅ renomme les canaux, reconstruit la notification | ❌ sans objet : ni notification ni canal à renommer ; les deux phrases de permission viennent d'`Info.plist`, que le système lit dans la langue du téléphone |

L'échelle de permissions a la même forme en deux temps :
`requestWhenInUseAuthorization()`, puis — seulement après accord —
`requestAlwaysAuthorization()`, chaque attente bornée à 60 s parce qu'une boîte
de dialogue système peut être écartée sans jamais changer le statut.
`.authorizedWhenInUse` donne `location: 'granted'` et
`backgroundLocation: 'denied'`, pas `'undetermined'`. `openSettings(which)`
**ignore son argument** sur iOS : il n'y a qu'une destination, la fiche Réglages
de l'application — ne promets pas un lien direct vers un interrupteur précis. Et
`getPermissions()` rend **neuf clés, pas dix** : `exactAlarm` est absente, donc
`undefined` et non `'unsupported'` ; une boucle sur une liste figée de dix noms
affiche une case vide. Enfin `setInterval(seconds)` ne règle aucun intervalle sur
iOS — la plateforme livre au mouvement, donc c'est le **filtre de distance** qui
bouge.

En une phrase : sur Android ce module est une garantie de survie ; sur iOS c'est
de la localisation d'arrière-plan au mieux de ce que la plateforme concède.
Promets la seconde chose, jamais la première.

---

## Installation

```bash
npx expo install expo-field-agent
```

Puis dans `app.json` :

```json
["expo-field-agent", {
  "tracking": {
    "url": "https://api.exemple.tn/api/positions",
    "batchUrl": "https://api.exemple.tn/api/positions/batch",
    "intervalSeconds": 15,
    "idleIntervalSeconds": 60,
    "distanceFilterMeters": 15,
    "batchSize": 50,
    "queueSize": 1000,
    "heartbeatSeconds": 120
  },
  "notification": {
    "channelName": "Suivi en service",
    "title": "En service",
    "body": "Ta position est partagée pendant tes courses.",
    "icon": "./assets/notif.png",
    "color": "#FF6B2C"
  },
  "alert": {
    "titlePattern": "Nouvelle course",
    "sound": "./assets/alerte.wav",
    "channelName": "Nouvelles courses",
    "route": "field-agent-alert",
    "ttlSeconds": 45,
    "torch": true
  },
  "bubble": {
    "icon": "./assets/bulle.png",
    "label": "Suivi",
    "colors": { "ok": "#1DB954", "warn": "#F5A623", "bad": "#E5484D" }
  }
}]
```

### Toutes les clés sont optionnelles · Every key is optional · كل المفاتيح اختيارية

**FR** — Aucune clé n'est obligatoire et chacune a un défaut sensé.
`["expo-field-agent"]`, sans le moindre objet d'options, fonctionne : le plugin
s'installe en entier. Seule `tracking.url` ne peut pas avoir de défaut — donne-la
ici, ou à chaud avec `start({ url })`. Une valeur absente ou malformée ne casse
**jamais** le build : elle produit un avertissement lisible
(`[expo-field-agent] …`) et le défaut s'applique.

**EN** — No key is required, and every one of them has a sensible default.
`["expo-field-agent"]` with no options object at all works: the plugin installs
in full. Only `tracking.url` cannot have a default — give it here, or at runtime
with `start({ url })`. A missing or malformed value **never** fails the build: it
prints a readable warning (`[expo-field-agent] …`) and the default applies.

**AR** — لا يوجد مفتاح إجباري، ولكل مفتاح قيمة افتراضية معقولة.
`["expo-field-agent"]` بدون أي كائن خيارات يعمل: يُثبَّت الملحق بالكامل. وحده
`tracking.url` لا يمكن أن تكون له قيمة افتراضية — مرّرها هنا، أو أثناء التشغيل
عبر `start({ url })`. أي قيمة ناقصة أو غير صالحة **لا** تُفشل البناء أبدًا: يظهر
تحذير مقروء (`[expo-field-agent] …`) وتُطبَّق القيمة الافتراضية.

| Clé · Key · المفتاح | Défaut · Default · الافتراضي | Si tu l'omets · If omitted · إذا أُغفلت |
| --- | --- | --- |
| `tracking.url` | `null` | Le suivi refuse de démarrer · Tracking refuses to start · التتبّع يرفض أن يبدأ |
| `tracking.batchUrl` | `null` | Envoi un par un sur `url` · Points go one by one to `url` · تُرسَل النقاط واحدة تلو الأخرى إلى `url` |
| `tracking.intervalSeconds` | `15` | |
| `tracking.idleIntervalSeconds` | `60` | |
| `tracking.distanceFilterMeters` | `15` | |
| `tracking.batchSize` | `50` | |
| `tracking.queueSize` | `1000` | |
| `tracking.heartbeatSeconds` | `max(idleIntervalSeconds × 2, 120)` | Soit `120` avec les défauts · So `120` with the defaults · أي `120` مع القيم الافتراضية |
| `tracking.exactAlarms` | `false` | `SCHEDULE_EXACT_ALARM` reste hors du manifeste, le watchdog garde une alarme inexacte · Stays out of the manifest, the watchdog keeps an inexact alarm · يبقى خارج البيان، ويحتفظ المراقب بمنبّه غير دقيق |
| `tracking.wakeLock` | `true` | Le CPU reste debout pendant toute la session : battement et envois gardent leur cadence écran verrouillé · The CPU stays up for the whole session: heartbeat and uploads keep their cadence with the screen locked · يبقى المعالج مستيقظًا طوال الجلسة: ينتظم النبض والإرسال والشاشة مقفلة |
| `tracking.maxAccuracyMeters` | `100` | Au-delà, le point est du bruit et n'entre pas dans la file. Minimum `1` · Above it a fix is noise. Minimum `1` · فوقها النقطة ضجيج. الحدّ الأدنى `1` |
| `tracking.maxSpeedMps` | `60` | Au-delà, c'est un saut GPS et non un trajet. Minimum `1` · Above it the jump is a GPS artefact. Minimum `1` · فوقها قفزة GPS لا رحلة. الحدّ الأدنى `1` |
| `tracking.rejectMock` | `false` | Le point simulé est gardé et marqué `isMock` plutôt que rejeté · Kept and flagged rather than dropped · تُحفَظ النقطة المزيّفة وتُعلَّم بدل رفضها |
| `notification.channelName` | `"Suivi en service"` | |
| `notification.title` | `"En service"` | |
| `notification.body` | `"Ta position est partagee pendant tes courses."` | |
| `notification.icon` | `null` | L'icône de l'application · The app icon · أيقونة التطبيق |
| `notification.color` | `"#FF6B2C"` | |
| `notification.resumeTitle` | `"Suivi interrompu"` | |
| `notification.resumeBody` | `"Android a refuse de relancer le suivi. Ouvre l'application pour reprendre."` | |
| `alert.titlePattern` | `".*"` | Tout titre déclenche · Every title fires · كل عنوان يُطلق التنبيه |
| `alert.sound` | `null` | Sonnerie d'alarme du système · The system alarm ringtone · نغمة المنبّه في النظام |
| `alert.channelName` | `"Nouvelles courses"` | |
| `alert.route` | `"field-agent-alert"` | |
| `alert.ttlSeconds` | `45` | |
| `alert.torch` | `false` | |
| `alert.forceVolume` | `true` | Le flux d'alarme est poussé pour l'alerte — le seul qu'Android joue encore en silencieux |
| `alert.volumeLevel` | `1` | Poussé au maximum de l'appareil. Un plancher, jamais un plafond : qui a mis plus fort garde son niveau |
| `alert.channelVersion` | `1` | |
| `alert.notificationBridge` | `false` | Aucun écouteur de notifications déclaré — voir la section FCM pour savoir quand l'activer |
| `bubble.icon` | `null` | Une pastille à la couleur de l'état · A dot in the state colour · نقطة بلون الحالة |
| `bubble.label` | `"Suivi"` | |
| `bubble.colors.ok` | `"#1DB954"` | |
| `bubble.colors.warn` | `"#F5A623"` | |
| `bubble.colors.bad` | `"#E5484D"` | |
| `bubble.colors.urgent` | `"#E5484D"` | |
| `ios.locationWhenInUsePermission` | `"Ta position sert a t'affecter les courses proches."` | |
| `ios.locationAlwaysPermission` | `"Ta position continue a etre partagee pendant tes courses, meme application fermee."` | |
| `ios.criticalAlerts` | `false` | Demande l'entitlement Apple · Needs Apple's entitlement · يتطلّب تصريح Apple |
| `rootComponent` | `"main"` | Ce qu'enregistrent `registerRootComponent` et expo-router · What `registerRootComponent` and expo-router register · ما يسجّله `registerRootComponent` و expo-router |
| `logLevel` | `"error"` | Seules les erreurs sont écrites ; `off` n'écrit rien du tout · Errors only; `off` writes nothing · الأخطاء فقط؛ و`off` لا يكتب شيئًا |
| `logMaxDays` | `7` | Les lignes plus vieilles sont supprimées à l'écriture suivante. Minimum `1` · Older rows are dropped on the next write. Minimum `1` |

> **SDK 52 uniquement, et sans rapport avec ce module :** certaines versions
> d'`expo-modules-core` embarquent un Compose Compiler qui refuse le Kotlin
> 1.9.24 par défaut d'Expo 52 (`This version (1.5.15) of the Compose Compiler
> requires Kotlin version 1.9.25`). Le correctif est côté application :
>
> ```json
> ["expo-build-properties", { "android": { "kotlinVersion": "1.9.25" } }]
> ```
>
> L'application `example/` de ce dépôt tourne sur le SDK 57 et n'en a pas besoin.

> **iOS, machines Homebrew :** CocoaPods 1.16 sur Ruby 3.4 casse si la locale
> n'est pas UTF-8 (`Unicode Normalization not appropriate for ASCII-8BIT`).
> Rien à voir avec ce module non plus :
>
> ```bash
> LANG=en_US.UTF-8 npx expo run:ios
> ```

Le plugin s'occupe de tout le natif : permissions, service de premier plan
`type="location"`, receivers de boot et de watchdog, activité d'alerte, copie du
son dans `res/raw` avec `noCompress`, `UIBackgroundModes` et les
`NSLocation*UsageDescription` côté iOS. **Zéro fichier natif à toucher.**

---

## Intégration complète

```tsx
import * as FieldAgent from 'expo-field-agent';
import { AlertHost } from 'expo-field-agent';
import { useEffect } from 'react';

export default function App() {
  useEffect(() => {
    const sub = FieldAgent.addListener('error', (e) => console.warn(e.code, e.message));
    return () => sub.remove();
  }, []);

  async function prendreService(jeton: string) {
    await FieldAgent.requestPermissions();             // dans l'ordre imposé par Android
    await FieldAgent.setAuthHeader(`Bearer ${jeton}`); // chiffré (Keystore / Keychain)
    await FieldAgent.start();                          // idempotent
    await FieldAgent.showBubble();                     // false sur iOS, par conception
  }

  return (
    <>
      <MonApp onPrendreService={prendreService} />
      <AlertHost
        render={(alert, actions) => (
          <MonEcranDOffre alert={alert} onAccept={actions.dismiss} onRefuse={actions.dismiss} />
        )}
      />
    </>
  );
}
```

`AlertHost` se monte une fois et s'affiche par-dessus tout. Il fonctionne dans
les deux cas d'arrivée :

- **app déjà ouverte** — l'événement `alert` remonte et l'écran s'affiche ;
- **app fermée ou téléphone verrouillé** — l'activité plein écran s'ouvre seule,
  démarre le moteur React Native, et `AlertHost` lit l'alerte **de façon
  synchrone** au premier rendu (`getPendingAlertSync()`). Il n'y a pas de course
  entre le bundle qui charge et l'événement qui part : le natif retient l'alerte
  et la rend lisible, il ne se contente pas de l'émettre.

---

## API

```ts
// Permissions -------------------------------------------------------------
FieldAgent.getPermissions(): Promise<Permissions>;
FieldAgent.requestPermissions(opts?: { skip?: (keyof Permissions)[] }): Promise<Permissions>;
FieldAgent.openSettings(which: keyof Permissions): Promise<void>;

// Suivi -------------------------------------------------------------------
FieldAgent.start(options?: Partial<TrackingOptions>): Promise<void>;   // idempotent
FieldAgent.stop(): Promise<void>;
FieldAgent.isRunning(): Promise<boolean>;
FieldAgent.setAuthHeader(value: string | null): Promise<void>;
FieldAgent.setInterval(seconds: number): Promise<void>;                // à chaud
FieldAgent.flush(): Promise<{ sent: number; queued: number }>;
FieldAgent.getState(): Promise<TrackingState>;
FieldAgent.getOdometer(): Promise<number>;                             // mètres, Android seulement
FieldAgent.resetOdometer(): Promise<void>;                             // Android seulement

// Journal (Android seulement) ---------------------------------------------
FieldAgent.getLog(opts?: { limit?: number; sinceMs?: number }): Promise<LogEntry[]>;  // plus récent d'abord
FieldAgent.clearLog(): Promise<void>;
FieldAgent.exportLog(): Promise<string | null>;                        // chemin du fichier écrit

// Bulle -------------------------------------------------------------------
FieldAgent.showBubble(): Promise<boolean>;                             // false si iOS ou permission absente
FieldAgent.hideBubble(): Promise<void>;
FieldAgent.setBubbleState(s: 'ok' | 'warn' | 'bad' | 'urgent', text?: string): Promise<void>;
FieldAgent.setBubbleImage(source: string | number | null): Promise<void>;  // fichier local, null = bubble.icon

// Langue ------------------------------------------------------------------
FieldAgent.setStrings(values: FieldAgentStrings | null): Promise<void>;    // null = retour a app.json

// Alerte ------------------------------------------------------------------
FieldAgent.triggerAlert({ title, body?, data? }): Promise<void>;
FieldAgent.dismissAlert(): Promise<void>;
FieldAgent.setAlertSound(enabled: boolean): Promise<void>;             // coupe-son côté hôte
FieldAgent.getPendingAlert(): Promise<AlertPayload | null>;
FieldAgent.getPendingAlertSync(): AlertPayload | null;

// Événements --------------------------------------------------------------
FieldAgent.addListener('position' | 'sent' | 'error' | 'alert' | 'bubblePress' | 'providerChange', cb): Subscription;
```

`Permissions` porte dix clés :

| clé | ce que c'est |
|---|---|
| `location` | `ACCESS_FINE_LOCATION` |
| `backgroundLocation` | « toujours autoriser » |
| `notifications` | `POST_NOTIFICATIONS` (Android 13+) |
| `overlay` | `SYSTEM_ALERT_WINDOW` — la bulle, **et** l'exemption de lancement en arrière-plan |
| `batteryUnrestricted` | liste système d'optimisation de batterie |
| `dndAccess` | `ACCESS_NOTIFICATION_POLICY` |
| `fullScreenIntent` | **ajout** — Android 14 place `setFullScreenIntent` derrière un accès spécial. Sans lui l'alerte au verrouillage retombe silencieusement en notification classique, donc l'état est visible plutôt que supposé. |
| `notificationAccess` | **ajout** — l'écran système d'accès aux notifications, utile uniquement au `alert.notificationBridge` optionnel. `unsupported` tant que le pont n'est pas activé. |
| `autostart` | **ajout** — l'écran de démarrage automatique du constructeur. Aucune API ne le lit : `granted` une fois que l'utilisateur y est passé, `undetermined` avant, `unsupported` sur une marque sans écran connu. |
| `exactAlarm` | **ajout** — `SCHEDULE_EXACT_ALARM`, pour le `tracking.exactAlarms` optionnel. `unsupported` tant que tu n'as pas activé l'option, parce que sans elle la permission n'est même pas dans le manifeste et envoyer l'utilisateur l'accorder n'accorderait rien. Une fois activée : `granted` sous Android 12, où l'alarme est exacte sans rien demander, puis `granted` / `denied` selon `canScheduleExactAlarms()`. |

### Quand la localisation d'arrière-plan disparaît en pleine journée

`backgroundLocation` peut être révoquée alors que le service tourne déjà : un
réglage basculé à la main, une restauration de sauvegarde, la réinitialisation
automatique qu'Android applique à une application inutilisée depuis des mois, ou
un chauffeur qui choisit « Lorsque l'app est active » sur iOS. La plateforme ne
signale pas ça comme un échec : le fournisseur fusionné cesse simplement de
livrer des points dès que l'application quitte le premier plan, et iOS efface
`allowsBackgroundLocationUpdates` tout seul. Aucune exception, aucun rappel,
aucune erreur — le chauffeur disparaît de la carte, voilà tout.

Les deux plateformes émettent désormais un événement `error` de code
**`BACKGROUND_LOCATION_LOST`** dès qu'elles s'en aperçoivent, et une seule fois
tant que la permission ne revient pas. Android vérifie à chaque démarrage du
service et à chaque battement de cœur ; iOS à chaque changement d'autorisation.
Traite-le comme bruyant : la journée n'est plus enregistrée, et seul le chauffeur
peut y remédier depuis les réglages système.

Deux clés en plus du contrat d'origine, parce que sans elles l'alerte et le
suivi cassent sur Android 14+ et sur MIUI/EMUI/ColorOS **sans rien dire**.

### Alarmes exactes — `SCHEDULE_EXACT_ALARM` seulement, et seulement si tu le demandes

Android a deux permissions d'alarme exacte. Ce plugin n'en déclarera jamais
qu'une seule, et c'est une politique, pas un oubli.

`USE_EXACT_ALARM` est accordée à l'installation et ne demande rien à
l'utilisateur — raison pour laquelle Google Play la réserve aux réveils, aux
minuteurs et aux agendas. Une application de livraison qui la livre voit sa mise
à jour rejetée. Elle n'est **jamais** déclarée ici, option activée ou non : même
refus, pour la même raison, que `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, que ce
module n'a jamais demandée et ne demandera pas.

`SCHEDULE_EXACT_ALARM` est celle que l'utilisateur accorde depuis un écran de
réglages. Elle entre dans le manifeste **uniquement** quand tu mets
`tracking.exactAlarms: true`, exactement comme `alert.notificationBridge` : une
permission que l'hôte n'a pas demandée, c'est une revue de store à laquelle il
n'a pas souscrit.

```json
["expo-field-agent", { "tracking": { "exactAlarms": true } }]
```

**Ce que l'option achète vraiment.** L'alarme du watchdog est ce qui réveille le
processus toutes les ~15 minutes pour vérifier que le suivi est encore vivant et
relancer le service sinon. Depuis Android 12, démarrer un service de premier
plan depuis l'arrière-plan est interdit hors d'une courte liste d'exemptions —
et une alarme inexacte `setAndAllowWhileIdle` n'est **pas** sur cette liste,
alors qu'une alarme exacte `setExactAndAllowWhileIdle` y est. Sans l'option le
watchdog se réveille quand même et essaie quand même ; il est simplement refusé
plus souvent, et la reprise attend alors le prochain passage au premier plan.
Avec, la reprise est autorisée sur-le-champ. Toute la différence est là : pas la
précision, le droit d'agir.

Fais-la accorder comme n'importe quel autre accès spécial :

```ts
const { exactAlarm } = await FieldAgent.getPermissions();
if (exactAlarm === 'denied') await FieldAgent.openSettings('exactAlarm');
```

C'est une décision de compilation. Contrairement aux clés de cadence, elle ne
s'active pas depuis `start()` : la permission doit être dans le manifeste avant
la livraison, et rien à chaud ne peut l'y mettre.

Le chemin inexact est un repli et non un `else` : la permission peut être retirée
entre deux ticks, donc `canScheduleExactAlarms()` est relu à chaque armement et
une `SecurityException` retombe directement sur l'alarme inexacte plutôt que de
perdre le watchdog.

### Ce que rend `getState()`

```ts
type TrackingState = {
  running: boolean;
  queued: number;                            // points en attente sur disque
  lastFixAt: number | null;                  // ms Unix, lu sur le point
  lastSentAt: number | null;                 // ms Unix du dernier POST accepté
  lastError: string | null;                  // « CODE: message » — la dernière, pas un historique
  lastErrorAt: number | null;                // quand elle s'est produite
  provider: 'fused' | 'manager' | 'none';    // ce qui acquiert réellement
  locationEnabled: boolean;                  // l'interrupteur de localisation du système
};
```

Les trois dernières sont des ajouts, et chacune répond à une question qui n'avait
aucune réponse depuis l'extérieur :

- **`lastErrorAt`** — une erreur sans date ne se trie pas. « File pleine » d'il y
  a trois jours et « file pleine » d'il y a trois secondes, c'est la même chaîne,
  et une seule des deux est un incident. Un avertissement n'écrase jamais la
  paire ; seule une vraie erreur le fait.
- **`provider`** — `'fused'` c'est Google Play Services, `'manager'` le repli sur
  `LocationManager` sur un appareil qui n'en a pas (Huawei récents, ROMs
  allégées), `'none'` quand rien ne tourne. Les deux n'ont jamais été la même
  chose et rien ne disait lequel tu avais ; une cadence plus grossière a
  désormais une cause.
- **`locationEnabled`** — l'interrupteur système. `false` explique à lui seul une
  absence de points, et c'est la première chose à regarder avant d'accuser le
  service.

**Sur iOS ces trois clés sont absentes de la charge et valent `undefined`.** Le
tracker iOS rend les cinq clés d'origine. Traite-les comme Android seulement tant
que ça n'a pas changé.

### `providerChange` — la localisation elle-même coupée

Android seulement. Un chauffeur qui déroule le volet et coupe la tuile de
localisation, ou qui passe en mode avion, disparaît de la carte avec le service
toujours vivant et toujours vert : le fournisseur fusionné cesse simplement de
livrer, sans rappel et sans exception. Le broadcast `PROVIDERS_CHANGED` est le
seul signal qui existe.

```ts
FieldAgent.addListener('providerChange', ({ enabled, gps, network }) => {
  if (!enabled) afficherBandeau('Localisation coupée — plus rien n est enregistré.');
});
```

`enabled` vaut `gps || network` ; les deux drapeaux servent au cas où un seul
fournisseur a disparu. Quand la localisation se coupe alors que le suivi est
voulu, une `error` de code **`LOCATION_OFF`** part en plus. Quand elle revient,
le service redemande les mises à jour tout seul — le fournisseur fusionné ne
reprend pas une requête tombée pendant la coupure — donc l'hôte n'a rien à faire.

iOS n'a pas de broadcast équivalent et **n'émet jamais cet événement.** Un
écouteur y est inerte, pas faux.

### Le journal natif — `getLog()`, `clearLog()`, `exportLog()`

Android seulement. Les pannes qui méritent d'être lues arrivent sur un téléphone
dans une camionnette, des heures avant que quelqu'un le branche en adb, et
logcat est un tampon circulaire que le système recycle en quelques minutes. Ce
journal est une table SQLite dans le stockage de l'application, écrite par le
**service** : il continue donc d'enregistrer à travers une mort de processus, un
redémarrage et une journée entière sans le moindre JavaScript.

```ts
const entrees = await FieldAgent.getLog({ limit: 100 });
// [{ at: 1758546185123, level: 'error', code: 'FOREGROUND', message: '…' }, …]

const chemin = await FieldAgent.exportLog();   // fichier de cache, plus ancien d'abord, ou null
await FieldAgent.clearLog();
```

- Plus récent d'abord, `limit` vaut `500` par défaut et `sinceMs` `0`. Les deux
  sont validés avant tout le reste : un `limit` non entier, un `limit` sous 1, un
  `sinceMs` négatif ou non fini **lèvent**, dans Expo Go comme ailleurs, parce que
  ce sont des bugs dans ton code et pas des limites de plateforme.
- `exportLog()` écrit tout le journal dans `cacheDir/field-agent/log-export.txt`,
  une ligne par entrée, plus ancien d'abord — l'ordre dans lequel on lit un
  incident — et rend le chemin absolu, ou `null` si l'écriture a échoué.
  Horodatage en UTC et locale fixe, pour qu'un téléphone en arabe ne livre pas
  des chiffres arabes orientaux et qu'un téléphone à Tunis n'estampille pas +01 à
  côté d'une chronologie serveur en UTC.
- `logLevel` est le plancher : `error` par défaut, `off` n'écrit rien du tout. Un
  journal qui enregistre chaque point est un journal que personne ne lit et une
  base qui grossit toute seule.
- La rétention est double : les lignes plus vieilles que `logMaxDays` sont
  supprimées à l'écriture suivante, et la table est ramenée à un plafond dur de
  **10 000 lignes**, non configurable. Un disque plein ou une base corrompue ne
  fait jamais tomber le service ; l'écriture est simplement perdue.
- **Aucune position n'y entre**, ni aucun en-tête d'authentification. L'export
  quitte l'appareil dès que quelqu'un appuie sur le bouton, et une position est
  une donnée personnelle. Ce qui y entre, ce sont des codes et des raisons :
  `FOREGROUND`, `LOCATION_OFF`, `NO_FIX`, `STALE`, `OFFLINE`, `PROVIDER`.

### Le compteur kilométrique

Android seulement. Des mètres accumulés côté natif depuis la dernière remise à
zéro, à travers les morts de processus et les redémarrages :

```ts
const metres = await FieldAgent.getOdometer();
await FieldAgent.resetOdometer();            // par exemple en début de service
```

Il ne compte que les pas entre des points que le filtre de qualité a **gardés**,
et il ignore tout pas plus court que la pire des deux précisions : le bruit GPS
d'un scooter garé la nuit facturerait sinon des dizaines de kilomètres au matin.
C'est donc un plancher et pas un compteur de facturation — un tunnel ou une perte
de signal, c'est de la distance qu'il ne prétend pas avoir vue.

### Qualité des points — précision, vitesse, positions simulées

Trois clés décident de ce que le filtre accepte, et ce sont des réglages plutôt
que des constantes parce qu'un scooter en ville dense et une camionnette sur
autoroute n'ont pas la même idée du saut impossible :

| clé | défaut | ce qu'elle rejette |
|---|---|---|
| `tracking.maxAccuracyMeters` | `100` | Un point que la plateforme annonce elle-même pire que ça : une estimation par antenne, pas une position |
| `tracking.maxSpeedMps` | `60` | Deux points qui impliquent plus — 60 m/s font 216 km/h, au-delà c'est un saut GPS et pas un véhicule |
| `tracking.rejectMock` | `false` | Avec `true`, tout point qu'Android signale comme venant d'un fournisseur simulé |

Deux choses arrivent quelle que soit ta configuration. Chaque point porte
désormais `isMock` dans l'événement `position` et `is_mock` dans la charge POST,
pour qu'un serveur qui facture au kilomètre tranche lui-même au lieu que la
décision soit prise sur le téléphone. Et les garde-fous de plausibilité ne
mesurent plus le temps écoulé avec l'horloge murale quand les deux points portent
l'horloge de fonctionnement de l'appareil : l'horloge murale est exactement ce
qu'un chauffeur peut avancer depuis les réglages pour s'offrir l'exemption
« tunnel » et faire passer un téléport ; l'uptime, non.

Avec `rejectMock: true`, le premier point simulé rejeté lève une `error` de code
**`MOCK_LOCATION`** — une seule fois par exécution, parce que la fraude doit se
voir mais qu'un déluge de lignes n'aide personne.

### La charge d'une alerte

```ts
type AlertPayload = {
  id: string;            // pour n'annuler que celle-ci
  title: string;
  body?: string;
  data?: Record<string, unknown>;
  receivedAt: number;    // ms Unix, côté natif
  route: string;         // `alert.route` de app.json, tel quel
};
```

`route` est simplement transporté : `AlertHost` n'en a pas besoin, mais un hôte
qui préfère naviguer (expo-router) plutôt que superposer un écran l'a sous la
main sans relire sa configuration.

### `data` et les positions

`triggerAlert({ data })` accepte un objet quelconque ; il traverse le natif en
JSON et revient parsé dans `alert.data`. `data.silent === true` coupe le son de
cette alerte-là uniquement.

Une position n'est **jamais** écrite dans les journaux, ni côté Android ni côté
iOS : c'est une donnée personnelle.

---

## La langue, et l'image de la bulle

### `setStrings()` — la langue de l'app, pas celle du téléphone

Toutes les chaînes que le plugin montre à un livreur sont remplaçables à chaud,
depuis les traductions que ton application a déjà :

```ts
await FieldAgent.setStrings({
  serviceChannelName: 'التتبّع أثناء الخدمة',
  serviceTitle: 'أثناء الخدمة',
  serviceBody: 'تتم مشاركة موقعك أثناء عملك.',
  alertChannelName: 'المهام الجديدة',
  alertChannelNameSilent: 'المهام الجديدة (صامت)',
  dismiss: 'تجاهل',
  bubbleLabel: 'تتبّع',
  bubbleAccessibility: '%s — اضغط لفتح التطبيق',
  resumeTitle: 'توقّف التتبّع',
  resumeBody: 'رفض أندرويد إعادة تشغيل التتبّع. افتح التطبيق للمتابعة.',
});
```

Chaque clé est optionnelle : celle que tu omets garde la valeur d'`app.json`, et
`setStrings(null)` les enlève toutes. Appelle-la une fois au démarrage, puis à
chaque changement de langue.

**Pourquoi à chaud plutôt que `values-ar/strings.xml`.** Une ressource par locale
suit le *téléphone*. Une application de livraison a presque toujours son propre
sélecteur de langue, et un téléphone en français ne dit rien d'un livreur qui a
choisi l'arabe dans l'app. C'est le seul mécanisme qui suit l'application.

**C'est persisté exprès.** Le service revient après un redémarrage sans qu'aucun
JavaScript ne tourne ; une langue gardée en mémoire reviendrait au défaut, et le
livreur trouverait une notification dans une langue qu'il n'a jamais choisie.

**Les canaux sont renommés sur-le-champ.** Un canal de notification fige son
importance, son son et sa vibration à la création — mais pas son nom, et le
recréer avec le même identifiant met à jour exactement ça. Sans cette passe, un
livreur qui passe à l'arabe garderait un nom de canal français dans les réglages
système jusqu'à la désinstallation. La notification de service en cours est
reconstruite dans le même appel.

**Ce que ça ne fait pas.** L'événement `error` porte un `code` stable (`OFFLINE`,
`VOLUME`, `QUEUE_FULL`…) et un `message` destiné au développeur : traduis à
partir du code, le message est pour tes journaux. Et sur iOS c'est sans effet :
pas de notification de service, pas de canal, pas de bulle, et les deux phrases
de localisation sont lues dans `Info.plist` par le système, dans la langue du
téléphone — localise-les avec `InfoPlist.strings`, aucun appel à chaud ne les
changera.

### `setBubbleImage()` — changer l'image pendant le service

`bubble.icon` dans `app.json`, c'est l'image embarquée à la compilation. Ceci la
change pendant que l'app tourne — le type de course, une photo que ton code vient
de télécharger :

```ts
await FieldAgent.setBubbleImage('file:///data/user/0/…/client.jpg');
await FieldAgent.setBubbleImage(null);   // retour a bubble.icon
```

Elle accepte une uri `file://`, un chemin absolu, une uri `content://`, ou le
résultat d'un `require('./x.png')`. **Sources locales uniquement** — le
téléchargement appartient à l'hôte, qui a son authentification, son cache et sa
politique de reprise, et la bulle doit rester une fenêtre légère. Une source
`http(s)` est refusée par un `error` de code `BUBBLE_IMAGE`, pas ignorée en
silence.

Les dimensions sont lues avant les pixels : une photo de 12 mégapixels est
sous-échantillonnée au lieu d'être décodée entière dans un emplacement de 24 dp.
Une image qui échoue retombe sur la pastille d'état, jamais sur un trou. Le
chemin est persisté, donc la bulle le retrouve quand le service repart sans JS.

**Dans un development build, un `require()` est servi par Metro en http** et donc
refusé, avec un message qui le dit. Les images embarquées vont dans
`bubble.icon`, qui est un vrai drawable dans tous les types de build.

---

## Limites par plateforme — la vérité

| Capacité | Android | iOS |
|---|---|---|
| Suivi en arrière-plan | ✅ service de premier plan `type="location"` | ✅ `UIBackgroundModes: location`, `allowsBackgroundLocationUpdates`, `pausesLocationUpdatesAutomatically = false` |
| Survit à l'app balayée | ✅ `stopWithTask=false`, `onTaskRemoved` n'arrête rien | ⚠️ oui tant que l'app n'est pas *force quit* |
| Survit à la mort du processus | ✅ `START_STICKY` + watchdog `AlarmManager` toutes les ~15 min | ⚠️ **rien ne redémarre**, sauf les changements significatifs de position (`startMonitoringSignificantLocationChanges`) |
| Survit au redémarrage du téléphone | ✅ `BOOT_COMPLETED` + `QUICKBOOT_POWERON` | ❌ non |
| Bulle flottante | ✅ `TYPE_APPLICATION_OVERLAY` | ❌ **impossible, aucune API.** `showBubble()` rend `false`. L'équivalent le plus proche est une Live Activity (Dynamic Island / écran verrouillé), non fournie ici. |
| Sonner en silencieux | ✅ flux `USAGE_ALARM`, volume poussé au max puis restauré | ⚠️ seulement avec l'entitlement **Critical Alerts**, accordé par Apple sur demande motivée (`ios.criticalAlerts: true`). Sans lui : notification normale. |
| Écran plein à la réception | ✅ `setFullScreenIntent` + `CATEGORY_CALL` + canal `IMPORTANCE_HIGH`, **et** exemption via `SYSTEM_ALERT_WINDOW` | ⚠️ pas d'équivalent. Notification `.timeSensitive` (`.critical` avec l'entitlement). CallKit donnerait un vrai plein écran mais Apple rejette l'abus : ce n'est pas un appel, donc ce n'est pas utilisé. |
| Contourner « Ne pas déranger » | ⚠️ `setBypassDnd(true)` seulement si `ACCESS_NOTIFICATION_POLICY` est déjà accordée **au moment de la création du canal** | ⚠️ `.timeSensitive` perce les modes de concentration ; le reste demande Critical Alerts |
| Écrans constructeurs (autostart) | ✅ table par marque + repli sur la fiche de l'application | ❌ sans objet |
| Watchdog sur alarme exacte | ⚠️ `SCHEDULE_EXACT_ALARM` en option seulement, jamais `USE_EXACT_ALARM` ; sans elle la reprise est refusée plus souvent | ❌ sans objet, il n'y a pas d'`AlarmManager` et rien à relancer |
| Journal embarqué (`getLog`, `clearLog`, `exportLog`) | ✅ SQLite écrit par le service, survit à la mort du processus et au redémarrage | ⚠️ **déclaré mais vide.** Les trois fonctions existent sur le module iOS et rendent `[]`, rien et `null` : ça se résout, ça n'enregistre rien. |
| Compteur kilométrique (`getOdometer`, `resetOdometer`) | ✅ accumulé sur les points acceptés, persisté | ⚠️ **déclaré mais vide** : `getOdometer()` rend toujours `0` |
| Événement `providerChange` | ✅ receiver `PROVIDERS_CHANGED`, plus une erreur `LOCATION_OFF` et une redemande automatique au retour | ❌ pas de broadcast équivalent ; iOS ne l'émet jamais |
| `getState().provider` / `.locationEnabled` / `.lastErrorAt` | ✅ | ⚠️ présentes dans la charge iOS : `provider` vaut `'manager'` ou `'none'`, `locationEnabled` est réel, `lastErrorAt` est toujours `null` |
| Rejet des positions simulées (`tracking.rejectMock`) | ✅ `Location.isMock`, et `isMock` sur chaque point | ❌ `CLLocation` n'expose aucun drapeau de ce genre ; la clé ne fait rien et aucun `is_mock` n'est envoyé |

Le plugin **compile et s'exécute sur iOS dans tous les cas**. Les capacités
absentes rendent une valeur explicite (`false`, `"unsupported"`), jamais une
exception.

**Y compris les cinq fonctions du journal natif et du compteur kilométrique.**
`getLog`, `clearLog`, `exportLog`, `getOdometer` et `resetOdometer` sont bien
déclarées sur le module iOS — sans quoi l'appel rejetterait —, mais ce sont des
bouchons : `[]`, `null`, `0`, et rien d'écrit. Ne construis pas d'écran de
diagnostic iOS dessus. Dans Expo Go, où il n'y a aucun module natif, les valeurs
neutres du tableau de dégradation s'appliquent de la même façon.

---

## Ce que le plugin gère pour toi

Chaque point ci-dessous est un bug de production, pas une bonne pratique
théorique.

1. **Service de premier plan** — `type="location"` déclaré dans le manifeste
   *et* passé à `startForeground()` (Android 14 plante sinon). `START_STICKY`.
   `onTaskRemoved` n'arrête rien : c'est exactement le moment où l'utilisateur
   balaie l'app en croyant que ça continue.
2. **Survie** — `BOOT_COMPLETED` **et** `QUICKBOOT_POWERON` (certaines ROMs
   n'envoient que le second) ; watchdog `setAndAllowWhileIdle` toutes les
   ~15 min ; l'état « en service » vit dans les préférences, pas en mémoire.
   Quand Android 12+ refuse un démarrage depuis l'arrière-plan, l'échec est
   visible — une `error` de code `SERVICE_START`, une **promesse `start()`
   rejetée**, et une notification « suivi interrompu » sur son propre canal, qui
   dit que le suivi est arrêté et non le contraire, et qui s'efface dès que le
   service revient. `tracking.exactAlarms` rend la reprise du watchdog bien
   moins souvent refusée ; la section sur les alarmes exactes dit ce que cette
   permission coûte.
3. **Canaux de notification versionnés** — un canal existant ne relit jamais son
   importance, son son, sa vibration ni son contournement du DND. Les
   identifiants portent la version (`fa_alert_v1`), la variante DND et la
   variante muette ; les anciens sont supprimés à la création. Bump
   `alert.channelVersion` pour forcer une recréation sur le parc installé.
4. **Son embarqué** — le config plugin copie ton fichier dans `res/raw` et
   ajoute `noCompress` à `app/build.gradle` (sans quoi `openRawResourceFd()`
   échoue et `MediaPlayer` n'ouvre rien). **Écart assumé :** le son fourni par
   l'hôte est essayé *en premier*, la chaîne système (alarme → sonnerie →
   notification) sert de repli — pas l'inverse. Tu l'as embarqué exprès, et
   c'est le seul qui ne dépende pas d'une ROM. Chaque candidat est ouvert avant
   d'être retenu.
5. **Sonner en silencieux** — flux `USAGE_ALARM`. **C'est ça, la réponse à
   « qu'elle sonne même en silencieux » : le flux d'alarme est le seul
   qu'Android continue de jouer en mode silencieux et en vibreur.** Monter le
   flux de notification ou celui de la sonnerie ne changerait rien, et c'est
   pour ça que ni l'un ni l'autre n'est touché. Jusqu'où on pousse, c'est
   `alert.volumeLevel` (0 à 1 du maximum de l'appareil, `1` par défaut), et
   `alert.forceVolume: false` supprime la montée pour un hôte qui préfère
   respecter le niveau choisi par l'utilisateur. La montée est un **plancher,
   jamais un plafond** — qui garde déjà son alarme plus fort la garde, et aucune
   valeur précédente n'est enregistrée dans ce cas. L'ancien volume est **sauvé
   sur disque** (un process tué en pleine alerte
   laisserait sinon le réveil bloqué au max) et restauré au démarrage suivant ;
   le volume est relu après écriture, et une montée refusée en silence produit
   un `error` de code `VOLUME`. Focus audio
   `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`, sinon Waze couvre l'alerte et le son
   part dans l'oreillette. Plafond de durée (`ttlSeconds`) obligatoire.
6. **Ouvrir un écran depuis l'arrière-plan** — les deux voies, pas une :
   `setFullScreenIntent` + `CATEGORY_CALL` + canal `IMPORTANCE_HIGH`, **et**
   l'exemption que donne `SYSTEM_ALERT_WINDOW`. L'activité est
   `showWhenLocked`, `turnScreenOn`, `excludeFromRecents`,
   `launchMode="singleTask"`, et gère ses encarts (Android 15, bord-à-bord).
7. **Bulle** — `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE`, déplaçable,
   collée au bord au relâchement, position persistée. **Un appui ramène
   l'application au premier plan** (et émet `bubblePress`) : le tap vient
   presque toujours d'une autre application, où JavaScript ne tourne pas et ne
   pourrait pas le faire ; c'est `SYSTEM_ALERT_WINDOW`, déjà indispensable à la
   bulle, qui rend ce lancement légal. Un `ACTION_CANCEL` — le système reprend
   le geste — n'est **pas** un appui : le compter en produisait des fantômes.
   **Elle ne peut pas s'afficher par-dessus l'écran verrouillé** — aucune
   fenêtre de superposition ne le peut. Sur écran verrouillé la surface, c'est
   la notification plein écran ; la bulle revient au déverrouillage.
8. **Constructeurs** — `Power` ouvre le bon écran sur MIUI, EMUI, ColorOS,
   FunTouch, One UI, OxygenOS, Realme, Meizu, Letv, Asus, Transsion, Nokia, avec
   plusieurs composants candidats par marque et un repli sur la fiche de
   l'application. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` n'est **pas** demandée :
   Google Play la refuse à la plupart des apps et son rejet fait retirer
   l'application. La liste système coûte deux touches de plus.
9. **Batterie** — filtre de distance, cadence différenciée actif/repos,
   groupage (`setMaxUpdateDelayMillis`) au repos, `WakeLock` partiel uniquement
   pendant un envoi, `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` immédiat au
   démarrage (sinon le premier point arrive trois minutes plus tard), et un
   battement de cœur qui contourne le filtre de distance.
10. **File** — bornée, sur disque, retrait **par identifiants** et jamais par
    nombre. Chaque point porte un `client_id` : pose un index unique dessus côté
    serveur et un lot rejoué ne crée aucun doublon. Une panne réseau
    (`OFFLINE`, `TIMEOUT`) ne vide rien et ne déconnecte personne ; seul un 4xx
    définitif (ni 401/403, ni 408/429) est abandonné, sinon un point empoisonné
    bloquerait tous les suivants.
11. **Fil principal et fuites** — le clignotement du flash bat sur le fil
    principal avec un jeton d'identité, pour qu'un coup déjà en file ne rallume
    pas la torche après l'arrêt. `MediaPlayer` est libéré même quand `prepare()`
    échoue. Écouteurs, `postDelayed` et vues de superposition sont retirés.
12. **Android 13/14/15** — `POST_NOTIFICATIONS` demandée avant de compter sur
    la moindre notification ; localisation en arrière-plan en deux temps
    (`requestPermissions` fait « pendant l'utilisation » puis renvoie vers les
    réglages système à partir d'Android 11, comme la plateforme l'impose et
    comme Google Play exige de le divulguer) ; `FOREGROUND_SERVICE_LOCATION` au
    manifeste ; bord-à-bord géré pour l'API 35.

---

## Brancher une notification FCM sur l'écran plein

Oui, c'est le cas d'usage prévu. Le filtre `alert.titlePattern` est testé contre
**le titre, le tag et l'identifiant de canal** — passe celui que ton pipeline
remplit réellement, tu n'as pas besoin de fabriquer un faux titre.

```json
"alert": { "titlePattern": "^(nouvelle-course|Nouvelle course)$" }
```

**Application ouverte ou en arrière-plan (JS vivant)** — `expo-notifications` :

```ts
import * as Notifications from 'expo-notifications';
import * as FieldAgent from 'expo-field-agent';

Notifications.addNotificationReceivedListener(({ request }) => {
  const { title, body, data } = request.content;
  FieldAgent.triggerAlert({
    title: title ?? '',
    body: body ?? undefined,
    data: data as Record<string, unknown>,
    tag: (data as any)?.tag,                    // ton tag FCM
    channelId: (request.trigger as any)?.channelId, // le canal Android
  });
});
```

`triggerAlert` rend la main sans rien faire si rien ne correspond au motif — tu
peux donc le brancher sur **toutes** tes notifications sans trier toi-même.

**Application tuée** — même appel, mais depuis une tâche d'arrière-plan, qui
s'exécute en headless JS sur Android sans que l'application soit lancée :

```ts
import * as TaskManager from 'expo-task-manager';
import * as Notifications from 'expo-notifications';
import * as FieldAgent from 'expo-field-agent';

const TACHE = 'field-agent-push';

TaskManager.defineTask(TACHE, ({ data, error }) => {
  if (error || !data) return;
  const notification = (data as any).notification?.data ?? (data as any);
  FieldAgent.triggerAlert({
    title: notification.title ?? '',
    body: notification.body,
    data: notification,
    tag: notification.tag,
    channelId: notification.channelId,
  });
});

Notifications.registerTaskAsync(TACHE);
```

### Tous les chemins d'arrivée, et ce qui se passe vraiment

| Comment l'alerte arrive | Écran plein ? |
|---|---|
| La réponse de ton serveur sur `POST /positions` | ✅ ne demande aucun push — c'est le service qui a fait la requête |
| FCM **data-only**, app ouverte ou en arrière-plan | ✅ par `addNotificationReceivedListener` → `triggerAlert()` |
| FCM **data-only**, app tuée | ✅ par une tâche headless `expo-task-manager` → `triggerAlert()` |
| FCM **avec un bloc `notification`**, app en arrière-plan ou tuée | ✅ **uniquement** avec `alert.notificationBridge: true` — sinon ❌ |
| App arrêtée de force depuis les réglages | ❌ plus rien ne l'atteint, jamais. Aucune application ne peut corriger ça |
| iOS, quel que soit le chemin | ❌ l'écran plein n'existe pas. Notification `.timeSensitive`, `.critical` avec l'entitlement Apple |

### Le message devrait être data-only — et pourquoi aucun code client ne rattrape

Quand un message FCM porte un bloc `notification` et que ton application n'est
pas au premier plan, le SDK Firebase pose cette notification dans la barre
système **lui-même** et n'appelle jamais ton code. Pas de `onMessageReceived`,
pas de tâche de fond, pas de `triggerAlert()`. Écrire ton propre
`FirebaseMessagingService` n'y change rien : le SDK court-circuite avant tout
service que tu pourrais enregistrer.

Le correctif gratuit est chez l'émetteur, et c'est une seule clé :

```json
{
  "message": {
    "token": "<jeton de l'appareil>",
    "android": { "priority": "HIGH" },
    "data": {
      "title": "Nouvelle course",
      "body": "3,2 km - 12 DT",
      "jobId": "1234"
    }
  }
}
```

Aucun `notification` nulle part — ni `message.notification`, ni
`message.android.notification`. Et toutes les valeurs de `data` doivent être des
chaînes : c'est une contrainte de FCM, pas la nôtre.

**Si tu passes par le service push d'Expo (`exp.host`) plutôt que par FCM
directement, c'est déjà réglé :** Expo envoie du data-only en interne et
`expo-notifications` affiche la notification lui-même, donc la tâche de fond
tourne. Le piège ne mord que quand tu parles à FCM directement.

### `alert.notificationBridge` — quand tu ne contrôles pas l'émetteur

Si le push vient d'un système que tu ne peux pas changer, il reste une voie : un
`NotificationListenerService`. Il voit la notification *après* qu'Android l'a
posée, et c'est le seul point d'observation qui reste une fois que le SDK
Firebase a contourné ton application.

```json
"alert": { "notificationBridge": true }
```

Ce qu'il fait : il lit **uniquement les notifications de ton propre paquet**, les
confronte à `alert.titlePattern` exactement comme n'importe quelle autre source,
déclenche l'alerte plein écran, et annule la copie de la barre système qu'il
vient de remplacer pour que l'utilisateur ne voie pas deux fois le même
événement. Sa propre notification d'alerte est exclue par identifiant, sans quoi
il se redéclencherait indéfiniment.

Ce qu'il coûte, et c'est à peser avant de l'activer :

- Il ajoute `BIND_NOTIFICATION_LISTENER_SERVICE` à ton manifeste. **Google Play
  examine toute application qui le porte** et attend que l'accès aux
  notifications soit une fonctionnalité centrale. Le plugin affiche un
  avertissement à la compilation pour que ce ne soit jamais une surprise
  découverte au moment de publier.
- L'utilisateur doit accorder l'accès à la main, dans un écran de réglages
  système — `openSettings('notificationAccess')` l'ouvre, et `getPermissions()`
  rend `notificationAccess`. La valeur est `unsupported` tant que le pont n'est
  pas activé, et toujours `unsupported` sur iOS.
- **La charge `data` du message FCM ne survit pas.** Une notification posée porte
  son titre, son texte, son tag et son canal — pas la table `data`, qui ne
  parvient à l'application que par l'intent de lancement, au tap. L'alerte arrive
  avec `data.source === 'notificationBridge'` et rien d'autre : c'est à l'hôte de
  retrouver le reste par son API (`GET /api/jobs/active` dans l'application
  rider). C'est une limite du chemin, pas de l'implémentation.

Désactivé par défaut. Corrige l'émetteur si tu peux ; sers-toi de ça quand tu ne
peux pas.

---

## D'où viennent les alertes

Le natif ne peut pas intercepter les notifications d'autrui : il expose un point
d'entrée unique, `triggerAlert()`, filtré par `alert.titlePattern`
(insensible à la casse, chaîne simple ou regex). Trois sources y arrivent :

1. **Ta réponse serveur.** Si un POST de position renvoie
   `{"alert": {"title": "...", "body": "...", "data": {...}}}`, le service
   déclenche l'alerte. C'est le seul chemin qui **n'a besoin d'aucun push** et
   fonctionne application fermée, parce que c'est le service qui a fait la
   requête.
2. **Un push, application ouverte** — branche `expo-notifications` :
   ```ts
   Notifications.addNotificationReceivedListener(({ request }) => {
     FieldAgent.triggerAlert({
       title: request.content.title ?? '',
       body: request.content.body ?? undefined,
       data: request.content.data,
     });
   });
   ```
3. **Un push, application fermée** — même appel depuis une tâche d'arrière-plan
   `expo-notifications` + `expo-task-manager`
   (`Notifications.registerTaskAsync`), qui s'exécute en headless JS sur Android
   même app tuée.

---

## Vérifier — un scénario, une commande

| # | Scénario | Commande de vérification | Attendu |
|---|---|---|---|
| 1 | Démarrage à froid, permissions accordées, `start()` | `adb shell dumpsys activity services tn.exemple.fieldagent \| grep isForeground` | `isForeground=true`, et un premier POST < 10 s (`adb logcat -s OkHttp` côté serveur, ou l'événement `sent` dans le journal de l'exemple) |
| 2 | App balayée des récents | idem après le balayage | service toujours là, événements `position` qui continuent |
| 3 | Processus tué | `adb shell am crash tn.exemple.fieldagent` puis `adb shell pidof tn.exemple.fieldagent` | nouveau PID, service revenu, `getState().queued` reprend sa descente |
| 4 | Redémarrage du téléphone | `adb reboot` puis, sans ouvrir l'app, `adb shell dumpsys activity services tn.exemple.fieldagent` | service relancé par `BootReceiver` |
| 5 | Avion 2 min puis retour | `adb shell cmd connectivity airplane-mode enable` … `disable` | `queued` monte puis retombe à 0 ; **aucun doublon** côté serveur (index unique sur `client_id`) |
| 6 | Écran verrouillé + alerte | `adb shell input keyevent 26` puis `triggerAlert` depuis l'exemple | écran réveillé, `AlertActivity` au premier plan, son |
| 7 | Silencieux + volume alarme à 1 + alerte | `adb shell media volume --stream 4 --set 1` puis alerte, et `adb shell dumpsys audio \| grep -A3 STREAM_ALARM` | volume monté au max pendant l'alerte, restauré après ; `dumpsys media.audio_flinger` montre un flux `USAGE_ALARM` actif |
| 8 | « Ne pas déranger » actif + alerte | activer DND, `getPermissions().dndAccess` | sonne si `granted` ; sinon l'état le dit clairement et le README explique quoi faire |
| 9 | Son coupé côté hôte + alerte | `setAlertSound(false)` puis alerte | aucun lecteur, **aucune** montée de volume (`dumpsys audio` inchangé), l'écran s'ouvre quand même |
| 10 | Alerte application fermée | balayer l'app des récents (ou `adb shell am kill tn.exemple.fieldagent`), puis alerte via la réponse serveur ou une tâche push. **Pas** `am force-stop` : une application arrêtée de force ne reçoit plus rien tant qu'on ne la relance pas à la main | l'écran s'ouvre et affiche le composant de l'hôte avec les bonnes données |
| 11 | Bulle : glisser, coller au bord, taper | manuel + `adb shell dumpsys window \| grep fieldagent` | événement `bubblePress` reçu ; position conservée après `am crash` |
| 12 | 8 h de suivi continu | `adb shell dumpsys meminfo tn.exemple.fieldagent` toutes les heures ; `adb shell dumpsys batterystats --charged tn.exemple.fieldagent` | `TOTAL PSS` stable ; voir « limites assumées » pour la consommation |

L'application `example/` rejoue chacun de ces scénarios depuis son écran
d'accueil. Ses trois ressources (`assets/notif.png`, `assets/bulle.png`,
`assets/alerte.wav`) sont des marqueurs générés : remplace-les par les tiennes,
le plugin ne fait que les copier.

Ces douze-là sont la passe fonctionnelle. Ce qu'ils ne mesurent pas, c'est la
**durée** — les cinq états dans lesquels Android coupe réellement les vivres, et
qui vont très bien pendant les cinq minutes où tu les regardes écran allumé. Ce
harnais-là est séparé : un serveur de réception Node sans aucune dépendance, qui
enregistre le `recorded_at` de chaque point reçu et imprime le plus grand trou,
plus un script adb par scénario (Doze, `am kill`, arrière-plan restreint par
`appops`, redémarrage, bucket rare). Le verdict tient dans un seul nombre —
**aucun trou supérieur à 2 × l'intervalle configuré** — et le serveur sort en
code non nul quand il est dépassé, donc une CI peut bloquer dessus. Tout est dans
[docs/ENDURANCE.md](docs/ENDURANCE.md) et `scripts/endurance/`, y compris
pourquoi `am force-stop` est le cas dont on ne revient pas.

---

## Tests automatiques

```bash
npm test
```

```bash
cd example && npx expo prebuild --platform android && cd android && ./gradlew :expo-field-agent:test
```

- **`Geo`** — filtre de plausibilité en JVM pur, sans Android : accuracy
  aberrante, points hors ordre, sauts impossibles, exemption tunnel, filtre de
  distance contre battement de cœur, l'horloge de fonctionnement qu'une horloge
  murale avancée ne trompe pas, le verdict « position simulée », le pas du
  compteur kilométrique et la borne de fraîcheur du battement. 35 cas.
- **`Log`** — l'échelle de sévérité, l'arithmétique de rétention et le format
  d'une ligne en UTC, sans le moindre Android (11 cas) ; puis la moitié SQLite
  sous Robolectric : rotation, plafond de 10 000 lignes, ordre de l'export, et
  une base qui refuse de s'ouvrir sans emporter le service. 9 cas.
- **`Queue`** — ajout, plafond, retrait par identifiants (y compris avec des
  points ajoutés « en vol »), survie au redémarrage, fichier tronqué par un
  kill. 9 cas.
- **`Volume` et `Images`** — le volume d'alarme comme plancher et jamais comme
  plafond, et le décodage « bornes avant pixels » de la bulle. 14 cas.
- **`LocationSource`** — le choix fused/manager à partir de la seule
  disponibilité (2 cas), puis `ManagerSource` contre un faux `LocationManager` :
  les deux fournisseurs demandés, celui qui est désactivé ignoré au lieu de
  lever, le plus frais des deux derniers points connus gardé. 4 cas.
- **`Bus`** — une erreur atteint l'écouteur *et* le disque, un avertissement
  n'efface jamais la dernière erreur, et une erreur levée avant `attach()` sort
  quand même. 6 cas.
- **`Config`** — les nouvelles clés sont bien lues dans le manifeste, un hôte
  silencieux reçoit les défauts livrés, un plafond à zéro est remonté à un, une
  valeur aberrante retombe sur « erreurs seulement », une option de `start()`
  l'emporte sur le manifeste — et `exactAlarms` n'est délibérément pas quelque
  chose qu'une option de `start()` peut activer, parce que la permission se
  décide à la compilation. 8 cas.
- **`Watchdog`** — `exactAlarm` vaut `unsupported` tant que l'hôte n'a pas
  activé l'option, une alarme refusée est écrite au lieu d'être levée, un
  changement de fournisseur porte ce qui reste actif, le retour de la
  localisation ne relance le service que si quelqu'un est en service, et le
  silence n'est daté qu'au-delà de la borne. 8 cas.
- **config plugin et surface JS** — le manifeste produit contient bien les
  permissions, le service `type="location"` et `stopWithTask=false`, le receiver
  de boot avec ses variantes quickboot, l'activité d'alerte `showWhenLocked`, le
  receiver `PROVIDERS_CHANGED`, et `SCHEDULE_EXACT_ALARM` **seulement** si l'hôte
  l'a demandée ; l'insertion dans `build.gradle` est idempotente ; chaque prop
  malformée avertit et retombe sur son défaut au lieu de planter ; et Expo Go
  rend sa valeur neutre à chaque appel pendant que les erreurs d'argument lèvent
  toujours. 55 cas.

Le reste est manuel, assumé, et décrit dans le tableau ci-dessus.

### Ce qui a été construit, et non pas seulement écrit

| Vérification | Résultat |
|---|---|
| `expo prebuild` (Android) → manifeste, `res/raw`, `res/drawable`, `build.gradle` | ✅ |
| `expo prebuild` (iOS) → `Info.plist`, son ajouté au projet Xcode | ✅ |
| `:expo-field-agent:compileDebugKotlin` — Expo SDK 52 | ✅ zéro avertissement dans les sources du module |
| `:expo-field-agent:testDebugUnitTest` — Geo, Log, Queue, Volume, Images, LocationSource, Bus, Config, Watchdog | ✅ 106/106 |
| `npm test` — config plugin, props, dégradation Expo Go | ✅ 55/55 |
| `:app:assembleDebug` — APK complet, manifeste fusionné | ✅ |
| `xcodebuild -target ExpoFieldAgent` (simulateur iOS) | ✅ |
| `npm pack` → installation dans une application Expo **SDK 57** neuve, `expo prebuild`, compilation | ✅ sans une seule modification manuelle |
| `tsc` sur le paquet, sur le config plugin et sur l'exemple | ✅ |

Ce qui n'a **pas** été vérifié ici, et qui ne peut l'être que sur un téléphone :
les douze scénarios du tableau précédent. Les commandes sont données pour ça.

---

## Choix et limites assumées

**Pourquoi un service natif plutôt qu'`expo-location` seul.** L'acquisition GPS
reste celle de la plateforme (`FusedLocationProviderClient`, `CLLocationManager`)
— elle n'est pas réécrite. Ce que JavaScript ne peut pas faire, et qui justifie
ce module : déclarer `foregroundServiceType="location"` pour Android 14, tenir
une file bornée sur disque, revenir après une mort de processus ou un
redémarrage, dessiner une superposition, ouvrir une activité plein écran depuis
l'arrière-plan, et sonner sur le flux d'alarme.

**Dépendances ajoutées.** Une seule :
`com.google.android.gms:play-services-location`, déjà présente dans tout projet
Expo qui utilise `expo-location`. `LocationManager` n'expose le groupage
(`maxUpdateDelay`) et `getCurrentLocation` qu'à partir de l'API 30/31 ; ce module
vise `minSdk 24`. Le chiffrement de l'en-tête d'authentification passe par
`AndroidKeyStore` + `javax.crypto` (plateforme) plutôt que par
`androidx.security:security-crypto`, et par le Keychain sur iOS. Le transport
utilise `HttpURLConnection` / `URLSession` — quelques kilo-octets de JSON ne
justifient pas d'épingler une version d'OkHttp dans le projet hôte.

**L'activité d'alerte monte le composant racine de l'application.** Pas un
second composant à enregistrer : `AlertActivity` démarre exactement la racine
que `registerRootComponent` (et expo-router) enregistre sous le nom `main`, donc
ton `<AlertHost>` se monte comme d'habitude et lit l'alerte au premier rendu. Si
tu enregistres ta racine sous un autre nom, dis-le au plugin :
`["expo-field-agent", { "rootComponent": "monNom" }]`.

**Deux surfaces React possibles pendant un instant.** Si l'utilisateur ouvre l'application par
son icône pendant qu'une alerte est affichée, l'activité d'alerte se ferme
d'elle-même (`ActivityLifecycleCallbacks`) pour qu'il n'y ait jamais deux arbres
React montés durablement. La fenêtre où les deux coexistent se compte en
millisecondes.

**Consommation de batterie : aucun chiffre n'est publié.** Il n'a pas été
mesuré, donc il n'est pas annoncé. Le protocole pour le mesurer sur ton parc :

```bash
adb shell dumpsys batterystats --reset
# ... 8 h de service, écran éteint, trajet réel ...
adb shell dumpsys batterystats --charged tn.exemple.fieldagent > batterie.txt
```

L'ordre de grandeur dépend entièrement de `intervalSeconds`, de la qualité du
signal et du modèle ; un chiffre mesuré sur un Pixel ne dit rien d'un Redmi.

**Le battement de cœur tient sur un wake lock, pas sur la chance.** Il est
cadencé par un `Handler` du fil principal, donc sur `uptimeMillis`, qui **cesse
d'avancer quand le CPU dort** — et un service au premier plan n'empêche pas le
CPU de dormir. Téléphone immobile, écran éteint, Doze : sans rien, le battement
ne partait pas à `heartbeatSeconds` mais au réveil suivant du service, c'est-à-dire
au plus tard à l'alarme du watchdog, soit le plancher que le système impose aux
alarmes *while-idle*, **~15 minutes**. Pour un serveur qui juge la fraîcheur,
l'agent avait disparu. `tracking.wakeLock`, actif par défaut, tient un
`PARTIAL_WAKE_LOCK` pendant toute la session : le CPU reste debout, le battement
repart à l'heure et la file se vide tout de suite, écran verrouillé compris. Le
lock n'est pas ignoré en Doze parce que l'uid porte un service au premier plan.
Le prix est la batterie ; `tracking.wakeLock: false` le rend à qui n'en veut pas,
et le comportement redevient celui décrit plus haut. `tracking.exactAlarms` reste
la porte d'à côté, pour le watchdog et lui seul : uniquement
`SCHEDULE_EXACT_ALARM`, que l'utilisateur accorde et peut reprendre, jamais
l'`USE_EXACT_ALARM` d'installation que Google Play réserve aux réveils et aux
agendas. En course, le problème ne se posait déjà pas : chaque point GPS réveille
le CPU.

**Le battement se tait plutôt que de mentir.** Renvoyer la dernière position
connue sous un horodatage frais est tout son intérêt — mais passé
`max(heartbeatSeconds × 4, 5 min)` comptés depuis le moment où ce processus a
accepté ce point, il n'envoie plus rien et écrit une ligne `STALE` dans le
journal. Un téléphone qui a perdu le GPS dans un parking souterrain il y a
quarante minutes publiait là où il était comme là où il est, et un régulateur qui
route là-dessus envoie quelqu'un dans une rue vide. Le silence est la réponse
honnête, et le contrôle de fraîcheur du serveur fait le reste.

**`flush()` sans service.** Fonctionne : la file et le transport vivent dans
`Outbox`, pas dans le service, donc un `flush()` manuel envoie même quand le
suivi est arrêté.

**Ce que le plugin ne fait pas.** Pas de Live Activity iOS, pas de CallKit, pas
de `NotificationListenerService` pour intercepter les notifications d'autres
applications, pas de re-géocodage. Rien de tout cela n'est un TODO : ce sont des
non-objectifs, listés ici pour qu'ils ne se découvrent pas en démonstration.

---

## Licence

MIT.
