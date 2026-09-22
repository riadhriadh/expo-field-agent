#!/usr/bin/env bash
# App Standby Bucket "rare" : l'application que l'utilisateur n'a pas ouverte
# depuis des jours. Android lui accorde une fenetre de travail par jour et
# repousse ses alarmes inexactes. C'est l'etat naturel d'une application de
# terrain le lundi matin apres un week-end.
set -euo pipefail

PKG="${1:-tn.exemple.fieldagent}"

command -v adb >/dev/null || { echo "adb introuvable dans le PATH."; exit 2; }
adb wait-for-device

cat <<EOF
--- standby.sh ---
Paquet : $PKG

Ce que je vais faire :
  adb shell am set-standby-bucket $PKG rare

Ce qu'il faut observer :
  - un service en premier plan deja demarre n'est PAS soumis au bucket : le
    suivi doit continuer normalement. Un trou ici veut dire que le suivi ne
    tient que par les alarmes, donc qu'il ne tient pas.
  - c'est le redemarrage apres une mort du processus qui souffre : le watchdog
    rearme via AlarmManager, et en bucket rare ces alarmes sont retardees.
    Enchaine avec kill.sh pour voir la vraie difference.

EOF

echo "+ adb shell am set-standby-bucket $PKG rare"
adb shell am set-standby-bucket "$PKG" rare

echo "bucket courant :"
adb shell am get-standby-bucket "$PKG" || true
echo "(10 = active, 20 = working set, 30 = frequent, 40 = rare, 45 = restricted)"

cat <<EOF

Laisse tourner au moins 20 minutes — un bucket rare se juge sur la duree, pas
sur deux minutes.

Pour revenir a la normale :
  adb shell am set-standby-bucket $PKG active

Pour lire le verdict : Ctrl+C sur le mock-server (rapport + code 1 si le plus
grand trou depasse --max-gap), ou, sans l'arreter :
  curl http://localhost:8787/
EOF
