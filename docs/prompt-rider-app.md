# Prompt — application Rider (type Uber Driver) bâtie sur `expo-field-agent`

> Copie tout ce qui suit dans ta demande. C'est le cahier des charges, pas une
> suggestion : chaque section est vérifiable.

---

## MISSION

Construis une application React Native Expo pour **chauffeur / livreur en
service** (l'équivalent de Uber Driver côté conducteur), qui utilise
`expo-field-agent` pour tout ce qui touche au suivi en arrière-plan, à la bulle
flottante et à l'alerte plein écran.

Le scénario complet, du début à la fin :

1. Le chauffeur se connecte, passe l'onboarding des permissions.
2. Il appuie sur **« Passer en ligne »**. Son téléphone part dans sa poche.
3. Le serveur reçoit sa position en continu, application fermée, écran éteint.
4. Une course tombe. **Le téléphone est verrouillé.** L'écran s'allume, sonne
   sur le flux d'alarme même en silencieux, et affiche l'offre avec un compte à
   rebours.
5. Il accepte. Il navigue vers le client dans Waze. Le suivi continue pendant ce
   temps.
6. Il arrive, démarre la course, la termine. À chaque étape, la bulle flottante
   par-dessus Waze affiche l'étape et sa couleur.
7. Il passe hors ligne. Tout s'arrête proprement.

L'application doit survivre à : l'app balayée des récents, le processus tué, le
téléphone redémarré, un tunnel de dix minutes, un token expiré.

---

## CE QUI EXISTE DÉJÀ — NE LE RÉÉCRIS PAS

`expo-field-agent` est publié sur npm et fait déjà, en natif :

- le service de premier plan `type="location"` qui survit à l'app balayée, au
  processus tué et au redémarrage ;
- la file d'attente bornée **sur disque**, avec `clientId` par point et retrait
  par identifiant ;
- l'envoi HTTP, l'en-tête d'authentification chiffré (Keystore / Keychain), le
  regroupement, le filtre de distance, la cadence actif/repos, le battement de
  cœur ;
- la bulle flottante déplaçable qui ouvre l'application au tap ;
- l'alerte plein écran qui s'ouvre **téléphone verrouillé, application fermée**,
  sonne sur le flux d'alarme et rend un composant React de l'application hôte ;
- l'échelle de permissions Android dans l'ordre imposé, y compris
  `fullScreenIntent` (Android 14) et les écrans d'autostart des constructeurs.

**Interdiction formelle de réimplémenter l'un de ces points.** Pas de
`expo-location` en parallèle, pas de `expo-task-manager` pour du suivi, pas de
file d'attente en AsyncStorage, pas de `setInterval` JS qui POST des positions.
Si tu ressens le besoin d'écrire l'un de ces morceaux, tu as raté un appel du
plugin — relis la cartographie plus bas.

---

## CONTRAINTES NON NÉGOCIABLES

- **Development build obligatoire.** La première ligne du README de
  l'application doit être `npx expo prebuild && npx expo run:android`. Rien ne
  fonctionne dans Expo Go.
- Expo SDK 52+, TypeScript **strict**, `expo-router`.
- **Zéro configuration native à la main.** Tout passe par `app.json`.
- Dépendances autorisées, et rien d'autre : `expo-field-agent`,
  `expo-notifications`, `expo-task-manager` (uniquement pour la réception push
  app tuée), `expo-secure-store` (le refresh token), `react-native-maps`,
  `expo-linking`. **Justifie toute autre dépendance ou ne l'ajoute pas.** Pas de
  Redux, pas de saga, pas de bibliothèque de formulaires pour trois champs.
- État global : un `useReducer` + contexte, ou Zustand. Un seul, pas deux.
- **Honnêteté plateforme.** iOS n'a pas de bulle flottante et ne survit pas au
  redémarrage. L'interface ne doit rien promettre de faux : masque ce qui
  n'existe pas au lieu de l'afficher grisé sans explication.
- Les commentaires disent **pourquoi**, jamais **quoi**.

---

## LA MACHINE À ÉTATS — le cœur de l'application

Un seul état de service, une seule source de vérité. Tout le reste en découle.

```
        ┌──────────┐  passer en ligne   ┌──────────┐
        │  OFFLINE │ ─────────────────► │   IDLE   │ ◄────────────┐
        └──────────┘ ◄───────────────── └──────────┘              │
             ▲        passer hors ligne      │                    │
             │                               │ offre reçue        │
             │                               ▼                    │
             │                         ┌──────────┐  refus /      │
             │                         │  OFFERED │  expiration   │
             │                         └──────────┘ ──────────────┘
             │                               │ acceptée
             │                               ▼
             │                         ┌────────────┐
             │                         │ TO_PICKUP  │
             │                         └────────────┘
             │                               │ « je suis arrivé »
             │                               ▼
             │                         ┌────────────┐
             │                         │  ARRIVED   │
             │                         └────────────┘
             │                               │ « démarrer la course »
             │                               ▼
             │                         ┌────────────┐  terminer
             └───────────────────────  │  IN_TRIP   │ ──────────► IDLE
                 (jamais directement)  └────────────┘
```

**Correspondance obligatoire état → plugin :**

| État | `setInterval` | `setBubbleState` | Notification de service |
|---|---|---|---|
| `OFFLINE` | — (`stop()`) | `hideBubble()` | aucune |
| `IDLE` | `20` | `('ok', 'En ligne')` | « En ligne — en attente de course » |
| `OFFERED` | `20` | `('urgent', 'Nouvelle course')` | inchangée |
| `TO_PICKUP` | `5` | `('warn', 'Vers le client')` | « Course #1234 — en route » |
| `ARRIVED` | `10` | `('warn', 'Sur place')` | « Course #1234 — sur place » |
| `IN_TRIP` | `5` | `('ok', 'Course en cours')` | « Course #1234 — en cours » |
| file d'attente non vide | inchangé | `('warn', 'Hors réseau')` | inchangée |
| erreur de permission perdue | inchangé | `('bad', 'Localisation coupée')` | inchangée |

Passer de `OFFLINE` à `IDLE` **ne doit jamais** être possible sans que
`getPermissions()` rende `location`, `backgroundLocation` et `notifications` à
`granted`.

---

## LES ÉCRANS

1. **Connexion** — téléphone + mot de passe (ou OTP). Le refresh token va dans
   `expo-secure-store`, jamais dans AsyncStorage. Après succès :
   `setAuthHeader('Bearer <access>')`.

2. **Onboarding permissions** — une ligne par permission, dans l'ordre rendu par
   le plugin, avec pour chacune : son état, **ce que le chauffeur perd si elle
   manque**, et un bouton qui appelle `requestPermissions()` ou
   `openSettings(nom)`. Cet écran se relit à chaque retour au premier plan
   (`AppState`), parce que le chauffeur revient des réglages système.
   - `overlay` refusé → « pas de bulle, et l'offre risque de ne pas s'ouvrir
     seule ».
   - `fullScreenIntent` refusé → « l'offre arrivera en notification ordinaire :
     tu devras déverrouiller pour la voir ».
   - `autostart` sur Xiaomi / Oppo / Vivo / Huawei → **bloquant**. Explique que
     sans lui le service ne revient pas après un nettoyage système.
   - `batteryUnrestricted` → `openSettings('batteryUnrestricted')` uniquement.
   - `notificationAccess` → n'apparaît que si tu as mis `alert.notificationBridge: true`
     (uniquement quand tu ne contrôles pas l'émetteur du push). Sinon la clé rend
     `unsupported` et la ligne ne doit pas s'afficher.

3. **Accueil / carte** — la carte, le point du chauffeur alimenté par
   l'événement `position`, un gros interrupteur **En ligne / Hors ligne**, les
   gains du jour, et une bannière d'état qui n'apparaît que si elle a quelque
   chose à dire (file non vide, GPS perdu, token expiré).

4. **Offre de course (plein écran)** — rendu par `<AlertHost>`. Départ, arrivée,
   distance, prix estimé, **compte à rebours calculé depuis `alert.receivedAt`**,
   deux boutons : Accepter / Refuser. Cet écran doit s'afficher correctement
   quand il est le tout premier écran de l'application (téléphone verrouillé,
   app tuée).

5. **Course en cours** — l'étape, le client, un bouton « Naviguer » qui ouvre
   Waze ou Google Maps par `Linking`, et le bouton d'étape suivante
   (arrivé → démarrer → terminer).

6. **Historique et gains** — liste des courses, total du jour et de la semaine.

7. **Diagnostic** (accessible depuis les réglages, indispensable au support) —
   `getState()` en direct : `running`, `queued`, `lastFixAt`, `lastSentAt`,
   `lastError`, plus les huit permissions, plus un bouton `flush()` et un bouton
   « tester une alerte » qui appelle `triggerAlert()`.

8. **Réglages** — son des offres (`setAlertSound`), cadence en attente, et la
   bulle (uniquement si `showBubble()` a rendu `true` au moins une fois).

---

## LE CONTRAT SERVEUR

À implémenter côté serveur ou à simuler ; l'application doit s'y tenir.

```
POST /api/auth/login          → { access, refresh, driver }
POST /api/auth/refresh        → { access }

POST /api/positions           ← un point (le plugin l'envoie tout seul)
POST /api/positions/batch     ← { positions: [...] }
GET  /api/jobs/active         → la course en cours, ou null
POST /api/jobs/:id/accept     → 200 { job } | 409 { reason: 'taken' | 'expired' }
POST /api/jobs/:id/decline    → 204
POST /api/jobs/:id/step       ← { step: 'arrived' | 'started' | 'completed' }
POST /api/shifts/start        → { shiftId }
POST /api/shifts/end          → { summary }
GET  /api/earnings/today      → { total, trips }
```

**Le point envoyé par le plugin** porte `clientId` : mets un index unique
dessus. Un lot rejoué après un tunnel ne doit créer aucun doublon.

**La réponse de `POST /api/positions` peut porter une offre** — c'est le chemin
d'alerte qui ne demande **aucun push** et fonctionne application fermée, parce
que c'est le service qui a fait la requête :

```json
{ "ok": true, "alert": { "title": "Nouvelle course", "body": "3,2 km — 12 DT",
  "data": { "jobId": "1234", "pickup": {...}, "dropoff": {...}, "ttl": 30 } } }
```

Utilise-le comme chemin **principal**. Le push est le chemin de secours, pas
l'inverse : un push peut être retardé de plusieurs minutes par le réseau, la
réponse d'un POST que le service vient de faire, non.

---

## COMMENT L'OFFRE ARRIVE — les trois chemins, tous branchés

1. **Réponse serveur sur une position** (ci-dessus). Rien à écrire côté
   application : le natif déclenche l'alerte tout seul.
2. **Push, application ouverte ou en arrière-plan** —
   `Notifications.addNotificationReceivedListener` → `triggerAlert({ title,
   body, data, tag, channelId })`.
3. **Push, application tuée** — le même appel depuis une tâche
   `expo-task-manager` enregistrée par `Notifications.registerTaskAsync`, qui
   tourne en headless JS sur Android sans lancer l'application.

**Le message FCM doit être data-only, et ce n'est pas un détail de confort.**
Quand un message porte un bloc `notification` et que l'application n'est pas au
premier plan, le SDK Firebase pose l'écriteau dans la barre système **lui-même**
et n'appelle jamais ton code : pas de tâche de fond, pas de `triggerAlert()`,
pas d'écran plein. Écrire ton propre `FirebaseMessagingService` n'y change rien,
le SDK court-circuite avant. Le correctif est chez l'émetteur :

```json
{
  "message": {
    "token": "<jeton de l'appareil>",
    "android": { "priority": "HIGH" },
    "data": {
      "title": "Nouvelle course",
      "body": "3,2 km - 12 DT",
      "jobId": "1234",
      "ttl": "30"
    }
  }
}
```

Aucun `notification` nulle part, et toutes les valeurs de `data` en chaînes de
caractères — contrainte de FCM. Si tu passes par le service push d'Expo
(`exp.host`) au lieu de FCM directement, c'est déjà le cas et tu n'as rien à
faire.

Règle le filtre pour que rien d'autre ne déclenche l'écran plein :

```json
"alert": { "titlePattern": "^(nouvelle-course|Nouvelle course)$" }
```

`triggerAlert` rend la main sans rien faire quand rien ne correspond, donc
branche-le sur **toutes** tes notifications sans trier toi-même.

---

## CARTOGRAPHIE — chaque fonction du plugin doit être utilisée

C'est une exigence, pas un inventaire. Une fonction sans emploi est un bug de
conception de l'application.

| Fonction du plugin | Où exactement |
|---|---|
| `getPermissions()` | Onboarding au montage, et à chaque `AppState` → `active` |
| `requestPermissions({ skip })` | Bouton « Tout autoriser ». `skip: ['dndAccess']` si le chauffeur a déjà refusé une fois |
| `openSettings(nom)` | Chaque ligne refusée de l'onboarding ; obligatoire pour `autostart` et `batteryUnrestricted`, et pour `notificationAccess` si le pont est activé |
| `start(options?)` | Bouton « Passer en ligne ». Passe `{ intervalSeconds: 20 }` et, en recette, `{ url }` pour viser le serveur de test |
| `stop()` | « Passer hors ligne ». Confirmation si une course est en cours |
| `isRunning()` | Au démarrage de l'application, pour reconstruire l'état après un kill |
| `setAuthHeader(v)` | Après le login **et après chaque refresh de token**, toujours avant `start()` |
| `setInterval(s)` | À chaque transition d'état, selon le tableau de la machine à états |
| `flush()` | Au retour au premier plan, et sur le bouton « Réessayer » de la bannière hors réseau |
| `getState()` | Bannière d'état + écran Diagnostic, en polling de 5 s **uniquement quand l'écran est visible** |
| `showBubble()` | Juste après `start()`. Si le retour est `false`, masque toute l'interface de bulle |
| `hideBubble()` | Dans `stop()` |
| `setBubbleState(s, t)` | À chaque transition d'état, selon le tableau |
| `triggerAlert(...)` | Listener push (app ouverte) + tâche de fond (app tuée) + bouton de test du Diagnostic |
| `dismissAlert()` | À l'acceptation, au refus **et** à l'expiration de l'offre |
| `setAlertSound(b)` | Réglage « Son des offres » |
| `getPendingAlert()` | Au retour au premier plan, pour rattraper une offre reçue pendant que l'app était en arrière-plan |
| `getPendingAlertSync()` | Au premier rendu du routeur, pour ouvrir directement l'offre quand l'app démarre à cause d'une alerte |
| `addListener('position')` | Point du chauffeur sur la carte, vitesse, précision |
| `addListener('sent')` | Bannière « synchronisé » qui disparaît toute seule |
| `addListener('error')` | Journal du Diagnostic + bannière selon le `code` (`OFFLINE` discret, `PERMISSION` bloquant) |
| `addListener('alert')` | Route vers l'écran d'offre quand l'application est déjà ouverte |
| `addListener('bubblePress')` | Analytics uniquement — **pas** de navigation (voir piège 7) |
| `<AlertHost render={...} />` | Monté une seule fois, à la racine, au-dessus du routeur |
| Config `tracking.*` | `app.json` : URL, cadences, filtre, taille de file, battement |
| Config `notification.*` | Le texte et l'icône de la notification permanente de service |
| Config `alert.*` | Motif, son embarqué, TTL, torche, nom de canal |
| Config `bubble.*` | Icône, libellé, les quatre couleurs d'état |
| Config `ios.*` | Les deux phrases de permission de localisation, en français |
| Config `rootComponent` | À ne changer que si tu n'enregistres pas ta racine sous `main` |

---

## LES PIÈGES — traite-les comme des exigences

1. **Le compte à rebours part de `alert.receivedAt`, jamais de `Date.now()` au
   montage.** L'activité plein écran doit démarrer le moteur React Native :
   entre l'instant où le natif accepte l'alerte et ton premier rendu, il s'écoule
   deux à quatre secondes sur un téléphone d'entrée de gamme. Calcule
   `restant = ttl * 1000 - (Date.now() - alert.receivedAt)` et, s'il est négatif,
   **n'affiche pas l'offre** — appelle `dismissAlert()` et reviens à `IDLE`.

2. **Ne fais jamais confiance au seul événement `alert`.** Application fermée :
   l'écran s'ouvre, le bundle charge, et l'événement est parti avant que ton
   listener existe. `getPendingAlertSync()` au tout premier rendu — c'est
   exactement ce que fait `<AlertHost>`, fais pareil si tu routes toi-même.

3. **Accepter est une course, pas une certitude.** Le serveur répond 409 quand
   la course est partie ailleurs. L'écran d'offre a trois issues : acceptée,
   perdue, expirée. **Appelle `dismissAlert()` dans les trois cas** — sinon la
   sonnerie tourne jusqu'au TTL et le chauffeur croit que l'application a planté.

4. **`setAuthHeader` avant `start`, et de nouveau à chaque refresh.** Le service
   tourne sans JavaScript ; il utilise l'en-tête chiffré tel qu'il est au moment
   du POST. Un token périmé produit des 401 : le plugin **garde** ces points
   (401/403 ne sont jamais abandonnés), donc rien n'est perdu — mais si tu ne
   rappelles pas `setAuthHeader` après le refresh, la file monte jusqu'au
   plafond, et là elle commence à perdre.

5. **`queued > 0` ne veut pas dire « chauffeur déconnecté ».** C'est un tunnel,
   un parking, un opérateur. Bannière discrète, pas de déconnexion, pas de sortie
   de service. La vraie perte de service, c'est `running === false` ou un
   `lastFixAt` vieux de plus de dix minutes.

6. **Ne re-POSTE jamais une position depuis JavaScript.** L'événement `position`
   est un événement d'affichage. Le point est déjà en file et partira. Un envoi
   parallèle depuis JS crée des doublons que ton index sur `clientId` refusera —
   ou pire, qu'il acceptera si tu génères un identifiant différent.

7. **`bubblePress` peut arriver avant que ton routeur soit monté.** Ne navigue
   pas *depuis* l'événement. Au montage, reconstruis l'état — `isRunning()`,
   `getState()`, `getPendingAlert()`, `GET /api/jobs/active` — et affiche la
   bonne page. L'événement sert à mesurer l'usage de la bulle, rien de plus.

8. **iOS n'a pas de bulle.** La bonne condition n'est pas `Platform.OS`, c'est
   **la valeur rendue par `showBubble()`** : sur Android sans la permission
   `overlay`, elle rend `false` aussi. Un interrupteur qui ne fait rien est pire
   qu'un interrupteur absent.

9. **`fullScreenIntent` refusé = l'offre dégrade en notification ordinaire, en
   silence.** Android 14 met `setFullScreenIntent` derrière un accès spécial.
   L'onboarding doit lire cet état et dire ce qui sera perdu, pas afficher une
   pastille rouge sans phrase.

10. **L'autostart n'est pas une case à cocher, c'est un passage obligé.** Sur
    MIUI, ColorOS, FunTouch et EMUI, sans lui le service ne revient pas après un
    nettoyage système. Aucune API ne peut le lire ni l'accorder : le plugin
    marque `granted` une fois que l'utilisateur y est allé. Bloque le passage en
    ligne sur ces marques tant que ce n'est pas fait, et explique pourquoi.

11. **Ne demande jamais `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.** Le plugin ne
    la déclare pas exprès : Google Play retire les applications qui l'utilisent
    sans y avoir droit. `openSettings('batteryUnrestricted')` ouvre la liste
    système. Deux touches de plus, zéro risque de retrait.

12. **Naviguer dans Waze met ton application en arrière-plan — ne l'arrête
    pas.** Aucun `stop()` dans un handler `AppState`. C'est exactement le moment
    où le serveur a le plus besoin des positions.

13. **Ne journalise jamais une position.** Ni `console.log`, ni breadcrumb
    Sentry, ni analytics. Le plugin ne le fait nulle part ; ne casse pas ça
    depuis JavaScript. C'est une donnée personnelle.

14. **Un `stop()` pendant une course doit demander confirmation** et prévenir le
    serveur (`POST /api/shifts/end`). Un chauffeur qui passe hors ligne avec un
    client dans la voiture, c'est un incident, pas un état.

---

## CRITÈRES D'ACCEPTATION

Chaque ligne doit être démontrable sur un téléphone réel, commande à l'appui.

| # | Scénario | Vérification | Attendu |
|---|---|---|---|
| 1 | En ligne, app balayée des récents | `adb shell dumpsys activity services <package> \| grep isForeground` | `isForeground=true`, les positions continuent d'arriver au serveur |
| 2 | Processus tué en course | `adb shell am crash <package>` puis rouvrir l'app | l'application revient **sur l'écran de course en cours**, pas sur l'accueil |
| 3 | Téléphone redémarré en service | `adb reboot`, ne pas ouvrir l'app, attendre 2 min | le serveur reçoit à nouveau des positions |
| 4 | Offre, téléphone verrouillé, app tuée | `adb shell am force-stop <package>`, verrouiller, puis alerte via la réponse serveur | écran allumé, offre affichée, son sur le flux d'alarme, compte à rebours **cohérent avec le temps réellement écoulé** |
| 5 | Offre en silencieux, volume d'alarme à 1 | `adb shell media volume --stream 4 --set 1` puis offre | ça sonne, et le volume est **restauré** après |
| 6 | Offre expirée pendant que l'écran s'ouvre | forcer un TTL de 3 s | l'offre ne s'affiche pas, `dismissAlert()` est appelé, retour à `IDLE` |
| 7 | Deux appareils, la même offre | accepter sur les deux | l'un a 200, l'autre 409 et un message clair, la sonnerie s'arrête sur les deux |
| 8 | Tunnel de 10 min en course | `adb shell cmd connectivity airplane-mode enable` … `disable` | `queued` monte puis retombe à 0, **aucun doublon** côté serveur, aucune déconnexion du chauffeur |
| 9 | Token expiré pendant le service | expirer l'access token côté serveur | refresh automatique, `setAuthHeader` rappelé, la file se vide, rien n'est perdu |
| 10 | Bulle pendant la navigation | ouvrir Waze, regarder la bulle | elle affiche l'étape et la bonne couleur ; un tap ramène l'app **sur la course**, pas sur l'accueil |
| 11 | iOS | lancer sur iPhone | aucune interface de bulle affichée, l'onboarding dit ce qui manque, le suivi et l'offre fonctionnent |
| 12 | Diagnostic | ouvrir l'écran | `running`, `queued`, `lastFixAt`, `lastSentAt`, `lastError` et les huit permissions sont tous affichés et à jour |

---

## CE QU'IL NE FAUT PAS FAIRE

- Réimplémenter le suivi, la file, l'envoi ou les permissions : c'est le plugin.
- Ajouter `expo-location` « juste pour la position sur la carte » — l'événement
  `position` la donne déjà, et deux clients GPS actifs doublent la consommation.
- Mettre le token dans AsyncStorage en clair.
- Afficher une interface de bulle sur iOS.
- Démarrer un compte à rebours d'offre au montage du composant.
- Empiler une bibliothèque d'état, une de navigation et une de formulaires pour
  sept écrans.
- Promettre dans l'interface une capacité que le tableau des limites du plugin
  dit absente.
- Livrer sans l'écran Diagnostic : sans lui, le premier ticket support est
  ininstruisible.
