#!/usr/bin/env node
/**
 * Serveur de reception factice pour les tests d'endurance.
 *
 * Zero dependance : node >= 18 et rien d'autre, parce qu'un harnais qu'il faut
 * installer est un harnais que personne ne lance.
 *
 * Il accepte exactement ce que le client envoie (voir Outbox.flush) :
 *   - point seul   -> POST tracking.url      avec le payload brut de la file
 *   - lot          -> POST tracking.batchUrl avec {"positions":[ ...payloads ]}
 *   - en-tete Authorization quand setAuthHeader() a ete appele
 *
 * Le payload d'un point (TrackingService.enqueue) :
 *   client_id, lat, lng, accuracy, speed, heading, altitude, recorded_at, heartbeat
 *
 * Le critere de reussite est le plus grand trou entre deux recorded_at
 * consecutifs : c'est la seule mesure qui dit si Android a laisse le service
 * respirer pendant toute la duree du test.
 */

import http from 'node:http';

const args = process.argv.slice(2);

if (args.includes('--help') || args.includes('-h')) {
  console.log(`Usage: node mock-server.mjs [options]

  --port N          port d'ecoute (defaut 8787)
  --fail-rate N     part des requetes repondues en 503 (0.2 ou 20 : les deux
                    s'ecrivent, 0 desactive). Le point est quand meme
                    enregistre avant le 503 : c'est le cas serveur le plus
                    mechant (la ligne est ecrite, la reponse se perd) et c'est
                    lui qui prouve a la fois que la file rejoue et que la
                    deduplication sur client_id encaisse le rejeu.
  --latency MS      delai ajoute avant chaque reponse (defaut 0)
  --max-gap SECONDS trou maximal tolere entre deux recorded_at (defaut 30,
                    soit 2 x l'intervalle par defaut de 15 s). Au-dela, le
                    processus sort en code 1 pour qu'une CI puisse bloquer.

Routes : POST sur n'importe quel chemin (le corps decide seul, point ou lot),
GET sur n'importe quel chemin pour lire le rapport sans arreter le serveur.
Ctrl+C imprime le rapport et sort.`);
  process.exit(0);
}

function opt(name, fallback) {
  for (let i = 0; i < args.length; i++) {
    if (args[i] === `--${name}`) return args[i + 1];
    if (args[i].startsWith(`--${name}=`)) return args[i].slice(name.length + 3);
  }
  return fallback;
}

function num(name, fallback) {
  const raw = opt(name, null);
  if (raw === null || raw === undefined) return fallback;
  const value = Number(raw);
  if (!Number.isFinite(value) || value < 0) {
    console.error(`--${name} attend un nombre positif, recu "${raw}"`);
    process.exit(2);
  }
  return value;
}

const PORT = num('port', 8787);
const LATENCY_MS = num('latency', 0);
const MAX_GAP_S = num('max-gap', 30);
// 20 et 0.2 designent la meme chose : personne ne veut se souvenir de l'unite.
const rawFailRate = num('fail-rate', 0);
const FAIL_RATE = rawFailRate > 1 ? rawFailRate / 100 : rawFailRate;

const MAX_BODY_BYTES = 4 * 1024 * 1024;

/** client_id -> point. La Map EST la deduplication. */
const points = new Map();
let totalReceived = 0;
let duplicates = 0;
let malformed = 0;
let requests = 0;
let requestsWithAuth = 0;
let injectedFailures = 0;
const startedAt = Date.now();

const iso = (ms) => new Date(ms).toISOString();

/**
 * Enregistre un point. Rend false quand le payload n'a pas de quoi etre
 * identifie ou date : on repond alors 400, ce qui fait tomber le point cote
 * client — un format casse doit etre bruyant, pas absorbe en silence.
 */
function store(point) {
  totalReceived++;
  const id = point?.client_id;
  const at = point?.recorded_at;
  if (typeof id !== 'string' || id.length === 0 || !Number.isFinite(at)) {
    malformed++;
    return false;
  }
  if (points.has(id)) {
    duplicates++;
    return true;
  }
  points.set(id, { recordedAt: at, receivedAt: Date.now(), heartbeat: point.heartbeat === true });
  return true;
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    request.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(new Error('corps trop gros'));
        request.destroy();
        return;
      }
      chunks.push(chunk);
    });
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    request.on('error', reject);
  });
}

function report() {
  const accepted = [...points.values()].sort((a, b) => a.recordedAt - b.recordedAt);

  let largestGapMs = 0;
  let gapFrom = null;
  let gapTo = null;
  for (let i = 1; i < accepted.length; i++) {
    const gap = accepted[i].recordedAt - accepted[i - 1].recordedAt;
    if (gap > largestGapMs) {
      largestGapMs = gap;
      gapFrom = accepted[i - 1].recordedAt;
      gapTo = accepted[i].recordedAt;
    }
  }

  const spanMs = accepted.length > 1
    ? accepted[accepted.length - 1].recordedAt - accepted[0].recordedAt
    : 0;
  const heartbeats = accepted.filter((p) => p.heartbeat).length;

  const lines = [
    '',
    '=== rapport endurance ===',
    `serveur en ligne     : ${((Date.now() - startedAt) / 1000).toFixed(0)} s`,
    `requetes             : ${requests} (dont ${requestsWithAuth} avec Authorization)`,
    `503 injectes         : ${injectedFailures}`,
    `points recus         : ${totalReceived}`,
    `points uniques       : ${points.size} (dont ${heartbeats} heartbeat)`,
    `doublons (rejeux)    : ${duplicates}`,
    `payloads invalides   : ${malformed}`,
  ];

  if (accepted.length === 0) {
    lines.push('', 'VERDICT : ECHEC — aucun point recu, le test n\'a rien prouve.', '');
    return { text: lines.join('\n'), failed: true };
  }

  lines.push(
    `plage recorded_at    : ${iso(accepted[0].recordedAt)} -> ${iso(accepted[accepted.length - 1].recordedAt)}`,
    `duree couverte       : ${(spanMs / 1000).toFixed(1)} s`,
  );

  if (accepted.length === 1) {
    lines.push('', 'VERDICT : ECHEC — un seul point, aucun intervalle mesurable.', '');
    return { text: lines.join('\n'), failed: true };
  }

  const largestGapS = largestGapMs / 1000;
  const failed = largestGapS > MAX_GAP_S;

  lines.push(
    `plus grand trou      : ${largestGapS.toFixed(1)} s`,
    `                       entre ${iso(gapFrom)} et ${iso(gapTo)}`,
    `seuil --max-gap      : ${MAX_GAP_S} s`,
    '',
    failed
      ? `VERDICT : ECHEC — trou de ${largestGapS.toFixed(1)} s > ${MAX_GAP_S} s. Android a coupe le service pendant ce creux.`
      : `VERDICT : OK — aucun trou au-dela de ${MAX_GAP_S} s.`,
    '',
  );

  return { text: lines.join('\n'), failed };
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const server = http.createServer(async (request, response) => {
  if (request.method === 'GET') {
    response.writeHead(200, { 'content-type': 'text/plain; charset=utf-8' });
    response.end(report().text);
    return;
  }

  if (request.method !== 'POST') {
    response.writeHead(405).end();
    return;
  }

  requests++;
  if (request.headers.authorization) requestsWithAuth++;

  let body;
  try {
    body = await readBody(request);
  } catch {
    response.writeHead(413).end();
    return;
  }

  let parsed;
  try {
    parsed = JSON.parse(body);
  } catch {
    malformed++;
    console.log(`POST ${request.url} 400 corps illisible`);
    response.writeHead(400, { 'content-type': 'application/json' });
    response.end('{"error":"json invalide"}');
    return;
  }

  // Le lot est le seul corps qui porte "positions" ; tout le reste est un point.
  const batch = Array.isArray(parsed?.positions);
  const list = batch ? parsed.positions : [parsed];
  const allStored = list.map(store).every(Boolean);

  if (LATENCY_MS > 0) await sleep(LATENCY_MS);

  if (!allStored) {
    console.log(`POST ${request.url} 400 payload invalide (${list.length})`);
    response.writeHead(400, { 'content-type': 'application/json' });
    response.end('{"error":"client_id ou recorded_at manquant"}');
    return;
  }

  if (FAIL_RATE > 0 && Math.random() < FAIL_RATE) {
    injectedFailures++;
    console.log(`POST ${request.url} 503 ${batch ? 'lot' : 'point'}=${list.length} uniques=${points.size}`);
    response.writeHead(503, { 'content-type': 'application/json' });
    response.end('{"error":"panne simulee"}');
    return;
  }

  console.log(`POST ${request.url} 200 ${batch ? 'lot' : 'point'}=${list.length} uniques=${points.size}`);
  response.writeHead(200, { 'content-type': 'application/json' });
  response.end('{"ok":true}');
});

let finishing = false;
function finish() {
  if (finishing) return;
  finishing = true;
  const { text, failed } = report();
  console.log(text);
  server.close(() => process.exit(failed ? 1 : 0));
  // Une connexion keep-alive ne doit pas retenir le rapport en otage.
  setTimeout(() => process.exit(failed ? 1 : 0), 500).unref();
}

process.on('SIGINT', finish);
process.on('SIGTERM', finish);

server.listen(PORT, () => {
  console.log(`mock-server sur http://0.0.0.0:${PORT}`);
  console.log(`  tracking.url      -> http://<ip-du-poste>:${PORT}/positions`);
  console.log(`  tracking.batchUrl -> http://<ip-du-poste>:${PORT}/positions/batch`);
  console.log(`  fail-rate=${FAIL_RATE} latency=${LATENCY_MS}ms max-gap=${MAX_GAP_S}s`);
  console.log('  Ctrl+C pour le rapport, ou GET sur le meme port pour le lire sans arreter.');
});
