#!/usr/bin/env bash
# Le systeme tue le processus pour recuperer de la memoire.
#
# `am kill` et PAS `am force-stop` : ce sont deux choses differentes.
#   am kill       -> Android tue le processus comme il le ferait sous pression
#                    memoire. Le service est recree, les alarmes survivent.
#                    C'est le cas que ce paquet doit encaisser.
#   am force-stop -> l'equivalent de "Forcer l'arret" dans les parametres. Le
#                    paquet passe en stopped state : alarmes annulees, receivers
#                    desarmes, rien ne repart avant que l'utilisateur rouvre
#                    l'application. Aucune API ne permet d'en revenir. C'est
#                    documente comme tel dans docs/ENDURANCE.md et ce n'est pas
#                    un bug a corriger, c'est une decision d'Android.
set -euo pipefail

PKG="${1:-tn.exemple.fieldagent}"

command -v adb >/dev/null || { echo "adb introuvable dans le PATH."; exit 2; }
adb wait-for-device

cat <<EOF
--- kill.sh ---
Paquet : $PKG

Ce que je vais faire :
  adb shell am kill $PKG   (PAS force-stop, voir l'en-tete du script)

Ce qu'il faut observer :
  - le processus disparait puis revient dans les secondes qui suivent
  - la file sur disque est rejouee : les points enregistres avant la mort
    arrivent au serveur, donc des doublons sont possibles et attendus —
    le rapport les compte, la deduplication sur client_id les absorbe
  - un trou superieur a 2 intervalles veut dire que le service n'est pas
    reparti tout seul

EOF

BEFORE=$(adb shell pidof "$PKG" | tr -d '\r')
echo "pid avant : ${BEFORE:-aucun}"

echo "+ adb shell am kill $PKG"
adb shell am kill "$PKG"
sleep 3

AFTER=$(adb shell pidof "$PKG" | tr -d '\r')
echo "pid apres : ${AFTER:-aucun}"

if [ -n "$BEFORE" ] && [ "$BEFORE" = "$AFTER" ]; then
  cat <<EOF

Le pid n'a pas bouge. C'est attendu : \`am kill\` ne tue qu'un processus qu'Android
juge sacrifiable, et un service en premier plan ne l'est pas. Pour forcer la
mort, mets d'abord l'application en arriere-plan (bouton accueil) et relance ce
script, ou utilise un appareil rooté :
  adb shell su -c "kill -9 $BEFORE"
EOF
fi

cat <<EOF

Laisse tourner encore au moins 10 minutes avant de conclure.

Pour lire le verdict : Ctrl+C sur le mock-server (rapport + code 1 si le plus
grand trou depasse --max-gap), ou, sans l'arreter :
  curl http://localhost:8787/
EOF
