// Headless player for the RadarLink matrix: joins, prints chat, leaves after STAY seconds.
// usage: node bot.js <host> <port> <version> [staySeconds]
// mineflayer covers 1.8.8 and newer; 1.7.x uses minecraft-protocol directly (login and keep-alive only), with the
// FML handshake when the server is Forge based (Crucible and other 1.7.10 Bukkit hybrids).
'use strict';
const [host, port, version, stayArg] = process.argv.slice(2);
const stay = Number(stayArg || 45);
const username = process.env.BOT_NAME || 'RadarBot';
const deadline = setTimeout(() => { console.log('BOT timeout'); process.exit(2); }, (stay + 120) * 1000);
const done = (r) => { console.log('BOT end ' + r); clearTimeout(deadline); process.exit(0); };

if (version.startsWith('1.7')) {
  const mc = require('minecraft-protocol');
  mc.ping({ host, port: Number(port), version: '1.7' }, (err, status) => {
    if (err) { console.log('BOT ping error ' + err.message); process.exit(2); }
    const mods = status && status.modinfo && status.modinfo.modList;
    join17(mc, mods);
  });
}

function join17(mc, mods) {
  const client = mc.createClient({ host, port: Number(port), version: '1.7', username, auth: 'offline' });
  if (mods && mods.length) {
    console.log('BOT Forge server, presenting ' + mods.map((m) => m.modid).join(', '));
    fml17Handshake(client, mods);
  }
  client.on('login', () => {
    console.log('BOT spawned on 1.7.10');
    setTimeout(() => { console.log('BOT leaving'); client.end('quitting'); }, stay * 1000);
  });
  client.on('chat', (p) => console.log('CHAT ' + p.message));
  client.on('kick_disconnect', (p) => console.log('BOT kicked ' + p.reason));
  client.on('error', (e) => console.log('BOT error ' + e.message));
  client.on('end', done);
}

if (!version.startsWith('1.7')) {
  const mineflayer = require('mineflayer');
  const bot = mineflayer.createBot({ host, port: Number(port), version, username, auth: 'offline', checkTimeoutInterval: 120000,
    // No movement packets: ViaBackwards (newest servers) kicks for translated moves, and the bot only needs to be online.
    physicsEnabled: false });
  bot.once('spawn', () => {
    console.log('BOT spawned on ' + bot.version);
    setTimeout(() => { console.log('BOT leaving'); bot.quit(); }, stay * 1000);
  });
  bot.on('message', (m) => console.log('CHAT ' + m.toString()));
  bot.on('kicked', (r) => console.log('BOT kicked ' + JSON.stringify(r)));
  bot.on('error', (e) => console.log('BOT error ' + e.message));
  bot.on('end', done);
}

// The FML|HS client handshake of Forge 1.7.10 (one registry packet), which minecraft-protocol-forge does not cover.
function fml17Handshake(client, mods) {
  const varint = (n) => { const b = []; do { let x = n & 0x7f; n >>>= 7; if (n) x |= 0x80; b.push(x); } while (n); return Buffer.from(b); };
  const str = (t) => { const d = Buffer.from(t, 'utf8'); return Buffer.concat([varint(d.length), d]); };
  const send = (data) => client.write('custom_payload', { channel: 'FML|HS', data });
  const ack = (phase) => send(Buffer.from([0xff, phase]));
  client.tagHost = '\0FML\0';
  client.on('custom_payload', (packet) => {
    if (packet.channel !== 'FML|HS') return;
    const kind = packet.data[0];
    if (kind === 0x00) { // ServerHello: register channels, ClientHello, our mod list
      client.write('custom_payload', { channel: 'REGISTER', data: Buffer.from(['FML|HS', 'FML', 'FML|MP', 'FORGE'].join('\0')) });
      send(Buffer.from([0x01, packet.data[1]]));
      send(Buffer.concat([Buffer.from([0x02]), varint(mods.length)].concat(mods.map((m) => Buffer.concat([str(m.modid), str(m.version)])))));
    } else if (kind === 0x02) { // server ModList
      ack(2);
    } else if (kind === 0x03) { // registry (ModIdData)
      ack(3);
    } else if (kind === 0xff) { // server HandshakeAck: WAITINGCACK (2), COMPLETE (3)
      if (packet.data[1] === 2) ack(4);
      else if (packet.data[1] === 3) ack(5);
    }
  });
}
