#!/usr/bin/env bash
# Le constructeur (ou l'utilisateur) interdit l'execution en arriere-plan.
# Xiaomi, Huawei, Oppo et consorts posent cette restriction d'eux-memes sur les
# applications qu'ils jugent gourmandes. C'est la panne la plus frequente en
# production et la moins visible en developpement.
set -euo pipefail

PKG="${1:-tn.exemple.fieldagent}"

command -v adb >/dev/null || { echo "adb introuvable dans le PATH."; exit 2; }
adb wait-for-device

cat <<EOF
--- appops.sh ---
Paquet : $PKG

Ce que je vais faire :
  adb shell cmd appops set $PKG RUN_ANY_IN_BACKGROUND ignore

Ce qu'il faut observer :
  - le suivi s'arrete ou devient tres irregulier : c'est ATTENDU, Android a
    retire le droit, aucun code ne le contourne
  - ce qui compte, c'est que la panne soit VISIBLE : une erreur dans getLog(),
    un lastError rempli, la notification de reprise. Un arret silencieux est
    le vrai bug.
  - au retablissement du droit, le suivi doit repartir seul si desiredRunning
    est toujours vrai, et la file doit rejouer ce qu'elle a garde

EOF

echo "+ adb shell cmd appops set $PKG RUN_ANY_IN_BACKGROUND ignore"
adb shell cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND ignore

echo "etat courant :"
adb shell cmd appops get "$PKG" RUN_ANY_IN_BACKGROUND || true

cat <<EOF

Laisse tourner 10 minutes, puis retablis et laisse tourner 10 minutes de plus :
  adb shell cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow

Pour lire le verdict : Ctrl+C sur le mock-server (rapport + code 1 si le plus
grand trou depasse --max-gap), ou, sans l'arreter :
  curl http://localhost:8787/

Note : ici le trou pendant la restriction est normal. Ce qu'on verifie, c'est
que les points repartent APRES le \`allow\`, sans toucher a l'application.
EOF
