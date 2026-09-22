#!/usr/bin/env bash
# Doze force : l'appareil pose sur une table, ecran eteint, toute la nuit.
# C'est le scenario qui tue les suivis naifs — les alarmes inexactes sont
# regroupees et les sockets coupes tant que le Doze tient.
set -euo pipefail

PKG="${1:-tn.exemple.fieldagent}"

command -v adb >/dev/null || { echo "adb introuvable dans le PATH."; exit 2; }
adb wait-for-device

cat <<EOF
--- doze.sh ---
Paquet : $PKG

Ce que je vais faire :
  1. debrancher la batterie (virtuellement) — le Doze refuse de demarrer en charge
  2. activer puis forcer le mode Doze

Ce qu'il faut observer :
  - le suivi continue, plus lentement : Android regroupe les reveils, donc un
    point toutes les ~2 intervalles au lieu d'un par intervalle est NORMAL
  - ce qui n'est pas normal, c'est un trou de plusieurs minutes : cela veut
    dire que le watchdog n'a pas rearme son alarme allowWhileIdle

EOF

echo "+ adb shell dumpsys battery unplug"
adb shell dumpsys battery unplug
echo "+ adb shell dumpsys deviceidle enable"
adb shell dumpsys deviceidle enable || true
echo "+ adb shell dumpsys deviceidle force-idle"
adb shell dumpsys deviceidle force-idle

echo
echo "Doze force. Eteins aussi l'ecran et laisse tourner au moins 15 minutes."
echo "Etat courant :"
adb shell dumpsys deviceidle get deep || true

cat <<EOF

Pour revenir a la normale :
  adb shell dumpsys deviceidle unforce
  adb shell dumpsys battery reset

Pour lire le verdict : Ctrl+C sur le mock-server (il imprime le rapport et sort
en code 1 si le plus grand trou depasse --max-gap), ou, sans l'arreter :
  curl http://localhost:8787/
EOF
