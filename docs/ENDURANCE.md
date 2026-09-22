# Tests d'endurance

Un suivi GPS en arrière-plan ne se teste pas en le regardant tourner cinq
minutes avec l'écran allumé. Dans cette configuration tout marche, y compris les
implémentations qui s'arrêtent dès que le téléphone rentre dans la poche.

Ce dossier contient de quoi reproduire, en quelques secondes et à la demande,
les cinq états dans lesquels Android coupe réellement les vivres. Le critère est
le même pour tous : **aucun trou supérieur à 2 × l'intervalle configuré** entre
deux `recorded_at` consécutifs reçus par le serveur.

---

## Le harnais

`scripts/endurance/mock-server.mjs` est un serveur de réception factice, sans
aucune dépendance — Node et rien d'autre. Il accepte exactement ce que le client
envoie, ni plus ni moins :

| Ce que le client poste | Vers | Corps |
|---|---|---|
| un point seul | `tracking.url` | le payload brut : `client_id`, `lat`, `lng`, `accuracy`, `speed`, `heading`, `altitude`, `recorded_at`, `heartbeat` |
| un lot | `tracking.batchUrl` | `{"positions":[ …les mêmes payloads… ]}` |

L'en-tête `Authorization` est accepté quand `setAuthHeader()` a été appelé ; le
rapport dit combien de requêtes le portaient, **jamais sa valeur**.

```bash
node scripts/endurance/mock-server.mjs --port 8787 --max-gap 30
```

| Option | Défaut | À quoi ça sert |
|---|---|---|
| `--port N` | `8787` | port d'écoute |
| `--fail-rate N` | `0` | part des requêtes répondues en 503 (`0.2` ou `20`, au choix) |
| `--latency MS` | `0` | délai ajouté avant chaque réponse, pour simuler un réseau lent |
| `--max-gap SECONDS` | `30` | seuil du verdict, soit 2 × l'intervalle par défaut de 15 s |

Dans `app.json` de l'application de test :

```json
"tracking": {
  "url": "http://192.168.1.20:8787/positions",
  "batchUrl": "http://192.168.1.20:8787/positions/batch",
  "intervalSeconds": 15
}
```

L'IP est celle de ton poste sur le réseau du téléphone, pas `localhost` — pour
l'appareil, `localhost` c'est lui-même. (Ou `adb reverse tcp:8787 tcp:8787` et
`http://localhost:8787`.)

Vérifie que le port est libre avant de lancer une session de trente minutes :

```bash
lsof -nP -iTCP:8787 -sTCP:LISTEN
```

Sur macOS, un serveur déjà lié à `127.0.0.1:8787` ne provoque **pas** d'erreur
quand le mock se lie à `0.0.0.0:8787` — les deux cohabitent, et c'est l'autre
qui répond. Tu verrais alors un rapport à zéro point après trente minutes de
test pour rien. C'est exactement pour ça qu'un rapport vide est compté comme un
échec.

### Pourquoi le 503 enregistre quand même le point

`--fail-rate` écrit le point **avant** de répondre 503. C'est délibéré : c'est le
cas serveur le plus méchant, celui où la ligne est bien insérée mais la réponse
se perd. Le client ne peut pas le savoir, il rejoue, et le rapport montre alors
les deux choses qu'on veut prouver d'un coup — que la file sur disque rejoue
bien, et que la déduplication sur `client_id` encaisse le rejeu sans créer de
position fantôme. Un serveur de test qui jette le point cacherait la moitié du
problème.

### Le rapport

`Ctrl+C` imprime le rapport et sort en **code 1** si le plus grand trou dépasse
`--max-gap` — une CI peut donc bloquer dessus. Un `GET` sur le même port rend le
même rapport sans arrêter le serveur, ce qui est pratique au milieu d'un test
long.

```
=== rapport endurance ===
serveur en ligne     : 1806 s
requetes             : 121 (dont 121 avec Authorization)
503 injectes         : 24
points recus         : 145
points uniques       : 121 (dont 8 heartbeat)
doublons (rejeux)    : 24
payloads invalides   : 0
plage recorded_at    : 2026-09-22T08:00:03.412Z -> 2026-09-22T08:30:04.118Z
duree couverte       : 1800.7 s
plus grand trou      : 21.4 s
                       entre 2026-09-22T08:12:41.002Z et 2026-09-22T08:13:02.402Z
seuil --max-gap      : 30 s

VERDICT : OK — aucun trou au-dela de 30 s.
```

Zéro point reçu ou un seul point est un **échec**, pas un succès : un test qui ne
mesure rien ne prouve rien.

---

## Les scénarios

Chaque script prend le nom du paquet en premier argument
(défaut `tn.exemple.fieldagent`), dit ce qu'il va faire avant de le faire, et
finit par rappeler comment lire le verdict.

| Scénario | Ce que ça simule | Commande | Ce qui doit se passer | Critère | Un échec veut dire |
|---|---|---|---|---|---|
| **Doze** | Le téléphone posé sur une table toute la nuit, écran éteint | `scripts/endurance/doze.sh [pkg]` | Le suivi continue, plus lentement : Android regroupe les réveils | Aucun trou > 2 × intervalle | Le watchdog ne réarme pas son alarme `allowWhileIdle`. En production : la position se fige pendant la pause déjeuner et personne ne le voit. |
| **Mort du processus** | Android récupère de la mémoire | `scripts/endurance/kill.sh [pkg]` | Le processus revient en quelques secondes, la file sur disque est rejouée | Aucun trou > 2 × intervalle ; les doublons sont attendus | Le service ne repart pas seul. En production : le suivi meurt au premier pic mémoire, c'est-à-dire tous les jours. |
| **Arrière-plan interdit** | Restriction constructeur (Xiaomi, Huawei, Oppo…) ou utilisateur | `scripts/endurance/appops.sh [pkg]` | Le suivi s'arrête — c'est normal — **et ça se voit** : `lastError`, entrée dans `getLog()`, notification de reprise | Les points repartent seuls après le `allow`, sans rouvrir l'application | La panne est silencieuse. C'est le vrai bug : un chauffeur invisible qui se croit suivi. |
| **Redémarrage** | Le téléphone reboote | `scripts/endurance/reboot.sh [pkg]` | Le service repart sans que personne n'ouvre l'application ; les points d'avant le reboot arrivent | Un trou de la durée du reboot (30–60 s) est normal ; il doit se refermer | `BOOT_COMPLETED` n'est pas câblé, ou `desiredRunning` n'est pas persisté. En production : la tournée du matin commence sans suivi. |
| **Bucket rare** | L'application pas ouverte depuis des jours | `scripts/endurance/standby.sh [pkg]` | Un service en premier plan déjà démarré n'est pas soumis au bucket : rien ne doit bouger | Aucun trou > 2 × intervalle | Le suivi ne tenait que par les alarmes, donc il ne tient pas. Enchaîne avec `kill.sh` pour voir le redémarrage souffrir. |

Compte au minimum 15 minutes par scénario, 20 pour le bucket rare. Un test
d'endurance de deux minutes n'est pas un test d'endurance.

### Ordre conseillé

`standby.sh` → `doze.sh` → `kill.sh` → `reboot.sh` → `appops.sh`, du moins
agressif au plus agressif, en laissant le serveur tourner d'un bout à l'autre :
le rapport final couvre alors toute la session et le plus grand trou est celui
de la pire minute de la journée, ce qui est exactement la question posée.

---

## Le cas dont on ne revient pas : « Forcer l'arrêt »

`kill.sh` utilise `am kill` et **pas** `am force-stop`. Ce ne sont pas deux
intensités du même geste, ce sont deux choses différentes.

| | `am kill` | `am force-stop` |
|---|---|---|
| Ce que c'est | Android tue le processus comme sous pression mémoire | L'équivalent exact de « Forcer l'arrêt » dans les paramètres |
| Alarmes | conservées | **annulées** |
| Receivers (`BOOT_COMPLETED`…) | actifs | **désarmés** |
| Ce qui redémarre | le service, tout seul | rien |

Après un `force-stop`, le paquet passe dans le *stopped state*. Tant que
l'utilisateur ne rouvre pas l'application lui-même, aucune alarme ne se
déclenche, aucun broadcast n'est délivré — `BOOT_COMPLETED` compris, donc même
un redémarrage du téléphone ne rattrape pas le coup. **Aucune API ne permet d'en
sortir**, et c'est voulu par Android : l'utilisateur qui force l'arrêt d'une
application demande explicitement qu'elle se taise.

Ce paquet ne prétend pas contourner ça, et aucun paquet ne le peut. Ce qu'on
peut faire, et ce qui est fait, c'est rendre l'état visible côté serveur : plus
de points, plus de heartbeat, et un trou qui ne se referme pas. À l'exploitant
d'appeler. C'est la seule réponse honnête, et prétendre l'inverse serait un
mensonge qui coûterait une tournée entière à quelqu'un.

Si tu veux quand même le vérifier de tes yeux :

```bash
adb shell am force-stop tn.exemple.fieldagent
# attends 10 minutes : rien n'arrive, et c'est le comportement correct
adb shell monkey -p tn.exemple.fieldagent 1   # rouvre l'app : tout repart
```
