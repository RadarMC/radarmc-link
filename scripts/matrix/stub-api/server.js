// Stub of the RadarMC server API for the RadarLink matrix. No dependencies.
// Records every request as one JSON line in /out/requests.jsonl and offers one pending vote
// (nick from VOTE_NICK, streak from VOTE_STREAK) until RadarLink confirms it.
'use strict';
const http = require('http');
const fs = require('fs');

const KEY = process.env.API_KEY;
const NICK = process.env.VOTE_NICK || 'RadarBot';
const STREAK = Number(process.env.VOTE_STREAK || 3);
const VOTE_ID = 'matrix-vote-1';
const LOG = process.env.LOG_FILE || '/out/requests.jsonl';
let confirmed = false;

function reply(res, status, body) {
  const text = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(text) });
  res.end(text);
}

http.createServer((req, res) => {
  let raw = '';
  req.on('data', (c) => { raw += c; });
  req.on('end', () => {
    const url = new URL(req.url, 'http://stub');
    let body = null;
    try { body = raw ? JSON.parse(raw) : null; } catch (e) { body = { unparsed: raw }; }
    const entry = { t: new Date().toISOString(), method: req.method, path: url.pathname, query: url.search,
      ua: req.headers['user-agent'] || '', keyOk: req.headers['x-api-key'] === KEY, body };
    fs.appendFileSync(LOG, JSON.stringify(entry) + '\n');
    console.log(req.method, url.pathname, entry.ua);
    if (!entry.keyOk) return reply(res, 401, { error: 'Invalid or revoked API key', code: 'INVALID_API_KEY' });
    const p = url.pathname;
    if (req.method === 'POST' && p.endsWith('/heartbeat')) return reply(res, 200, { ok: true });
    if (req.method === 'POST' && p.endsWith('/joins')) {
      const n = body && Array.isArray(body.joins) ? body.joins.length : 0;
      return reply(res, 200, { accepted: n, ignored: 0 });
    }
    if (req.method === 'GET' && p.endsWith('/votes/pending')) {
      const votes = confirmed ? [] : [{ vote_id: VOTE_ID, minecraft_username: NICK, created_at: new Date().toISOString(), streak: STREAK }];
      return reply(res, 200, { votes, next_cursor: '' });
    }
    if (req.method === 'POST' && p.endsWith('/votes/' + VOTE_ID + '/confirm')) {
      confirmed = true;
      return reply(res, 200, { ok: true });
    }
    return reply(res, 404, { error: 'not found' });
  });
}).listen(8080, () => console.log('stub API on :8080'));
