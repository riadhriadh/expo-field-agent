# expo-field-agent

[English](README.md) · **Français** · [العربية](README.ar.md)

> **Development build obligatoire. Ce paquet ne fonctionne pas dans Expo Go.**
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
| `notification.channelName` | `"Suivi en service"` | |
| `notification.title` | `"En service"` | |
| `notification.body` | `"Ta position est partagee pendant tes courses."` | |
| `notification.icon` | `null` | L'icône de l'application · The app icon · أيقونة التطبيق |
| `notification.color` | `"#FF6B2C"` | |
| `alert.titlePattern` | `".*"` | Tout titre déclenche · Every title fires · كل عنوان يُطلق التنبيه |
| `alert.sound` | `null` | Sonnerie d'alarme du système · The system alarm ringtone · نغمة المنبّه في النظام |
| `alert.channelName` | `"Nouvelles courses"` | |
| `alert.route` | `"field-agent-alert"` | |
| `alert.ttlSeconds` | `45` | |
| `alert.torch` | `false` | |
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

> **SDK 52 uniquement, et sans rapport avec ce module :** certaines versions
> d'`expo-modules-core` embarquent un Compose Compiler qui refuse le Kotlin
> 1.9.24 par défaut d'Expo 52 (`This version (1.5.15) of the Compose Compiler
> requires Kotlin version 1.9.25`). Le correctif est côté application :
>
> ```json
> ["expo-build-properties", { "android": { "kotlinVersion": "1.9.25" } }]
> ```
>
> L'application `example/` l'inclut pour cette raison.

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

// Bulle -------------------------------------------------------------------
FieldAgent.showBubble(): Promise<boolean>;                             // false si iOS ou permission absente
FieldAgent.hideBubble(): Promise<void>;
FieldAgent.setBubbleState(s: 'ok' | 'warn' | 'bad' | 'urgent', text?: string): Promise<void>;

// Alerte ------------------------------------------------------------------
FieldAgent.triggerAlert({ title, body?, data? }): Promise<void>;
FieldAgent.dismissAlert(): Promise<void>;
FieldAgent.setAlertSound(enabled: boolean): Promise<void>;             // coupe-son côté hôte
FieldAgent.getPendingAlert(): Promise<AlertPayload | null>;
FieldAgent.getPendingAlertSync(): AlertPayload | null;

// Événements --------------------------------------------------------------
FieldAgent.addListener('position' | 'sent' | 'error' | 'alert' | 'bubblePress', cb): Subscription;
```

`Permissions` porte neuf clés :

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

Deux clés en plus du contrat d'origine, parce que sans elles l'alerte et le
suivi cassent sur Android 14+ et sur MIUI/EMUI/ColorOS **sans rien dire**.

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

Le plugin **compile et s'exécute sur iOS dans tous les cas**. Les capacités
absentes rendent une valeur explicite (`false`, `"unsupported"`), jamais une
exception.

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
   visible (`error` de code `SERVICE_START`) et une notification « reprendre »
   est posée — il n'est pas avalé.
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
5. **Sonner en silencieux** — flux `USAGE_ALARM` ; volume d'alarme poussé au
   maximum, ancien volume **sauvé sur disque** (un process tué en pleine alerte
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
  distance contre battement de cœur. 16 cas.
- **`Queue`** — ajout, plafond, retrait par identifiants (y compris avec des
  points ajoutés « en vol »), survie au redémarrage, fichier tronqué par un
  kill. 9 cas.
- **config plugin** — le manifeste produit contient bien les permissions, le
  service `type="location"` et `stopWithTask=false`, le receiver de boot avec
  ses variantes quickboot, l'activité d'alerte `showWhenLocked` ; l'insertion
  dans `build.gradle` est idempotente ; une config malformée avertit au lieu de
  planter. 13 cas.

Le reste est manuel, assumé, et décrit dans le tableau ci-dessus.

### Ce qui a été construit, et non pas seulement écrit

| Vérification | Résultat |
|---|---|
| `expo prebuild` (Android) → manifeste, `res/raw`, `res/drawable`, `build.gradle` | ✅ |
| `expo prebuild` (iOS) → `Info.plist`, son ajouté au projet Xcode | ✅ |
| `:expo-field-agent:compileDebugKotlin` — Expo SDK 52 | ✅ zéro avertissement dans les sources du module |
| `:expo-field-agent:test` — Geo + Queue | ✅ 25/25 |
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

**Le battement de cœur dégrade en veille profonde.** Il est cadencé par un
`Handler` du fil principal, donc sur `uptimeMillis`, qui **cesse d'avancer quand
le CPU dort**. Téléphone immobile, écran éteint, Doze : le battement ne part pas
à `heartbeatSeconds`, il part au réveil suivant du service — c'est-à-dire au
plus tard à l'alarme du watchdog, soit le plancher que le système impose aux
alarmes *while-idle*, **~15 minutes**. Descendre en dessous demanderait une
alarme exacte, que Google Play refuse aux applications qui ne sont ni réveil ni
agenda. En course, le problème ne se pose pas : chaque point GPS réveille le
CPU. Mesuré sur émulateur, pas déduit.

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
