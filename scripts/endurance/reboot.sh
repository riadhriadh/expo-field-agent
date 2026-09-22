#!/usr/bin/env bash
# Redemarrage du telephone. Le seul scenario qui prouve que BOOT_COMPLETED est
# bien cable et que desiredRunning est bien persiste : apres un reboot, plus
# rien n'est en memoire, tout vient du disque.
set -euo pipefail

PKG="${1:-tn.exemple.fieldagent}"

command -v adb >/dev/null || { echo "adb introuvable dans le PATH."; exit 2; }
adb wait-for-device

cat <<EOF
--- reboot.sh ---
Paquet : $PKG

Ce que je vais faire :
  1. adb reboot
  2. attendre le boot complet
  3. verifier que le service de suivi est reparti tout seul

Ce qu'il faut observer :
  - le service redemarre sans que personne n'ouvre l'application
  - les points enregistres avant le reboot arrivent au serveur : la file est
    sur disque, elle doit survivre a la coupure
  - un trou de la duree du reboot (30 a 60 s) est normal ; un trou qui ne se
    referme jamais veut dire que BOOT_COMPLETED n'a pas relance le service

EOF

echo "+ adb reboot"
adb reboot
sleep 5
adb wait-for-device

echo "attente du boot complet..."
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  sleep 2
done
echo "boot termine."

# Le service peut mettre quelques secondes a etre relance apres BOOT_COMPLETED.
echo "attente du service (60 s max)..."
FOUND=""
for _ in $(seq 1 30); do
  if adb shell dumpsys activity services "$PKG" 2>/dev/null | grep -q "TrackingService"; then
    FOUND="oui"
    break
  fi
  sleep 2
done

if [ -n "$FOUND" ]; then
  echo "OK : TrackingService est en vie apres le redemarrage."
  adb shell dumpsys activity services "$PKG" | grep -i "TrackingService" | head -5 || true
else
  cat <<EOF
ECHEC : aucun TrackingService dans dumpsys 60 s apres le boot.

A verifier dans cet ordre :
  - l'application a-t-elle ete lancee au moins une fois depuis son installation ?
    Android ignore BOOT_COMPLETED pour un paquet jamais lance.
  - l'application a-t-elle ete arretee via "Forcer l'arret" avant le reboot ?
    Dans ce cas rien ne repart, et c'est definitif (voir docs/ENDURANCE.md).
  - desiredRunning etait-il vrai avant le reboot ?
EOF
fi

cat <<EOF

Laisse tourner encore 10 minutes avant de conclure.

Pour lire le verdict : Ctrl+C sur le mock-server (rapport + code 1 si le plus
grand trou depasse --max-gap), ou, sans l'arreter :
  curl http://localhost:8787/
EOF
