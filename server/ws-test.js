// WebSocket 双端联调测试：alice 和 bob 互发消息
const WebSocket = require('ws');

const TOKEN_A = process.argv[2];
const TOKEN_B = process.argv[3];
const URL = 'ws://localhost:8082/ws?token=';

function connect(token, name) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(URL + token);
    const received = [];
    ws.on('open', () => resolve({ ws, received }));
    ws.on('message', (data) => {
      const msg = JSON.parse(data.toString());
      received.push(msg);
      console.log(`[${name}] <-`, JSON.stringify(msg));
    });
    ws.on('error', reject);
    setTimeout(() => reject(new Error('connect timeout')), 8000);
  });
}

(async () => {
  const a = await connect(TOKEN_A, 'alice');
  const b = await connect(TOKEN_B, 'bob');
  console.log('--- 双端已连接，等待 presence 广播 ---');
  await new Promise(r => setTimeout(r, 1000));

  console.log('--- alice -> bob: 你好 ---');
  a.ws.send(JSON.stringify({ type: 'msg', to: 'bob', contentType: 'text', content: '你好，我是 alice' }));
  await new Promise(r => setTimeout(r, 800));

  console.log('--- bob -> alice: 收到！ ---');
  b.ws.send(JSON.stringify({ type: 'msg', to: 'alice', contentType: 'text', content: '收到！我是 bob' }));
  await new Promise(r => setTimeout(r, 800));

  console.log('--- 超长内容拦截测试（5000 字符）---');
  a.ws.send(JSON.stringify({ type: 'msg', to: 'bob', contentType: 'text', content: 'x'.repeat(5000) }));
  await new Promise(r => setTimeout(r, 500));

  console.log('--- 非法 contentType 归一化测试 ---');
  a.ws.send(JSON.stringify({ type: 'msg', to: 'bob', contentType: 'evil', content: 'test' }));
  await new Promise(r => setTimeout(r, 500));

  a.ws.close(); b.ws.close();
  await new Promise(r => setTimeout(r, 500));
  console.log('--- 测试完成 ---');
  process.exit(0);
})().catch(e => { console.error('FAIL:', e.message); process.exit(1); });
