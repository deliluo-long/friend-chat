const WebSocket = require('ws');
const TOKEN_A = process.argv[2];
const ws = new WebSocket('ws://localhost:8082/ws?token=' + TOKEN_A);
ws.on('open', () => {
  console.log('OPEN');
  ws.send(JSON.stringify({ type: 'msg', to: 'bob', contentType: 'text', content: '探针消息' }));
});
ws.on('message', (d) => { console.log('MSG:', d.toString()); });
ws.on('close', (c, r) => console.log('CLOSE', c, r.toString()));
ws.on('error', (e) => console.log('ERROR', e.message));
ws.on('unexpected-response', (req, res) => console.log('UNEXPECTED', res.statusCode));
setTimeout(() => { console.log('TIMEOUT-EXIT'); process.exit(0); }, 6000);
