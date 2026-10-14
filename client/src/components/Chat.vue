<template>
  <div class="chat-wrap">
    <aside class="sidebar">
      <div class="me">
        <div class="avatar">{{ session.displayName[0].toUpperCase() }}</div>
        <div class="me-info">
          <div class="me-name">{{ session.displayName }}</div>
          <button class="logout" @click="$emit('logout')">退出</button>
        </div>
      </div>
      <div class="friend-list">
        <div v-for="f in friends" :key="f.username"
             class="friend-item" :class="{ active: f.username === activeUser, online: onlineSet.has(f.username) }"
             @click="selectUser(f.username)">
          <div class="avatar sm">{{ f.displayName[0].toUpperCase() }}</div>
          <div class="f-info">
            <div class="f-name">{{ f.displayName }}</div>
            <div class="f-last">{{ lastMsgOf(f.username) }}</div>
          </div>
          <span v-if="unread[f.username]" class="badge">{{ unread[f.username] }}</span>
        </div>
      </div>
    </aside>

    <main class="chat-main">
      <div v-if="!activeUser" class="empty">
        <p>选择一个朋友开始聊天</p>
      </div>
      <template v-else>
        <header class="chat-header">
          <span>{{ activeUser }}</span>
          <span class="online-dot" :class="{ on: onlineSet.has(activeUser) }"></span>
        </header>
        <div class="msg-list" ref="msgList">
          <div v-for="(m, i) in messages" :key="i"
               class="msg-row" :class="{ mine: m.from === session.username }">
            <div class="bubble">
              <img v-if="m.contentType === 'image'" :src="imgUrl(m.content)" class="msg-img" />
              <template v-else>{{ m.content }}</template>
            </div>
            <div class="ts">{{ fmtTime(m.ts || m.created_at) }}</div>
          </div>
        </div>
        <footer class="input-bar">
          <button class="icon-btn" title="发图片" @click="sendImage">🖼</button>
          <input v-model="draft" placeholder="输入消息，Enter 发送"
                 @keyup.enter="sendText" :disabled="!wsOk" />
          <button class="send-btn" :disabled="!draft.trim() || !wsOk" @click="sendText">发送</button>
        </footer>
        <p v-if="!wsOk" class="conn-lost">连接已断开，正在重连…</p>
      </template>
    </main>
  </div>
</template>

<script>
export default {
  props: { session: Object },
  emits: ['logout'],
  data() {
    return {
      friends: [],
      onlineSet: new Set(),
      activeUser: null,
      history: {},       // username -> [msg]
      unread: {},
      draft: '',
      ws: null,
      wsOk: false,
      reconnectTimer: null,
      heartbeat: null
    }
  },
  computed: {
    messages() { return this.history[this.activeUser] || [] }
  },
  async created() {
    await this.loadFriends()
    this.connectWs()
  },
  beforeUnmount() {
    this.ws?.close()
    clearInterval(this.heartbeat)
    clearTimeout(this.reconnectTimer)
  },
  methods: {
    async loadFriends() {
      const res = await fetch(`${this.session.apiBase}/api/friends`, {
        headers: { Authorization: `Bearer ${this.session.token}` }
      })
      if (res.ok) this.friends = await res.json()
    },
    connectWs() {
      const url = this.session.apiBase.replace(/^http/, 'ws') + '/ws?token=' + this.session.token
      this.ws = new WebSocket(url)
      this.ws.onopen = () => {
        this.wsOk = true
        this.heartbeat = setInterval(() => {
          if (this.ws?.readyState === 1) this.ws.send('{"type":"ping"}')
        }, 25000)
      }
      this.ws.onmessage = (ev) => {
        const m = JSON.parse(ev.data)
        if (m.type === 'presence') {
          this.onlineSet = new Set(m.online)
        } else if (m.type === 'msg') {
          const peer = m.from === this.session.username ? m.to : m.from
          if (!this.history[peer]) this.history[peer] = []
          // 去重（回显与历史拉取可能重叠）
          if (!this.history[peer].some(x => x.ts === m.ts)) {
            this.history[peer].push(m)
          }
          if (m.from !== this.session.username && peer !== this.activeUser) {
            this.unread[peer] = (this.unread[peer] || 0) + 1
            this.notifyUser(m)
          }
          this.scrollBottom()
        }
      }
      this.ws.onclose = () => {
        this.wsOk = false
        clearInterval(this.heartbeat)
        this.reconnectTimer = setTimeout(() => this.connectWs(), 3000)
      }
    },
    notifyUser(m) {
      if (window.fc) {
        window.fc.notify.show(m.from, String(m.content).slice(0, 60))
        window.fc.tray.flash(true)
      }
    },
    async selectUser(u) {
      this.activeUser = u
      this.unread[u] = 0
      if (!this.history[u]) {
        const res = await fetch(
          `${this.session.apiBase}/api/history?withUser=${encodeURIComponent(u)}&limit=50`,
          { headers: { Authorization: `Bearer ${this.session.token}` } })
        if (res.ok) {
          this.history[u] = await res.json()
        }
      }
      if (window.fc) window.fc.tray.flash(false)
      this.scrollBottom()
    },
    lastMsgOf(u) {
      const h = this.history[u]
      if (!h || !h.length) return ''
      const last = h[h.length - 1]
      return last.contentType === 'image' ? '[图片]' : String(last.content).slice(0, 16)
    },
    sendText() {
      const content = this.draft.trim()
      if (!content || !this.wsOk) return
      this.ws.send(JSON.stringify({ type: 'msg', to: this.activeUser, contentType: 'text', content }))
      this.draft = ''
    },
    async sendImage() {
      if (!window.fc) {
        // 浏览器开发模式：原生 file input
        const inp = document.createElement('input')
        inp.type = 'file'
        inp.accept = 'image/*'
        inp.onchange = () => {
          if (inp.files[0]) this.uploadImage(inp.files[0])
        }
        inp.click()
        return
      }
      // Electron：通过主进程对话框选图，再由主进程读取为 base64
      const filePath = await window.fc.file.openImage()
      if (!filePath) return
      const b64 = await window.fc.file.readImage(filePath)
      if (!b64) return
      // base64 -> Blob 上传（文件名从路径取）
      const byteStr = atob(b64)
      const arr = new Uint8Array(byteStr.length)
      for (let i = 0; i < byteStr.length; i++) arr[i] = byteStr.charCodeAt(i)
      const name = filePath.split(/[\\/]/).pop() || 'image.png'
      this.uploadImage(new Blob([arr]), name)
    },
    async uploadImage(file, name) {
      const fd = new FormData()
      fd.append('file', file, name || (file.name || 'image.png'))
      const res = await fetch(`${this.session.apiBase}/api/image`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${this.session.token}` },
        body: fd
      })
      const data = await res.json()
      if (data.ok) {
        this.ws.send(JSON.stringify({ type: 'msg', to: this.activeUser, contentType: 'image', content: data.url }))
      }
    },
    imgUrl(u) {
      if (u.startsWith('http')) return u
      return this.session.apiBase + u
    },
    fmtTime(ts) {
      if (!ts) return ''
      const d = new Date(ts)
      return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    },
    scrollBottom() {
      this.$nextTick(() => {
        const el = this.$refs.msgList
        if (el) el.scrollTop = el.scrollHeight
      })
    }
  }
}
</script>

<style scoped>
.chat-wrap { display: flex; height: 100%; }
.sidebar {
  width: 220px; background: #fff;
  border-right: 1px solid #e9ecef;
  display: flex; flex-direction: column;
}
.me { display: flex; align-items: center; gap: 10px; padding: 14px; border-bottom: 1px solid #e9ecef; }
.me-info { flex: 1; }
.me-name { font-size: 14px; font-weight: 600; color: #212529; }
.logout {
  border: none; background: none; color: #adb5bd;
  font-size: 12px; cursor: pointer; padding: 0;
}
.logout:hover { color: #e03131; }
.friend-list { flex: 1; overflow-y: auto; }
.friend-item {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px; cursor: pointer; position: relative;
}
.friend-item:hover { background: #f1f3f5; }
.friend-item.active { background: #e7f0ff; }
.avatar {
  width: 40px; height: 40px; border-radius: 50%;
  background: #4263eb; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-weight: 600; font-size: 16px; flex-shrink: 0;
}
.avatar.sm { width: 36px; height: 36px; font-size: 14px; }
.friend-item.online .avatar { background: #2f9e44; }
.f-info { flex: 1; min-width: 0; }
.f-name { font-size: 14px; color: #343a40; }
.f-last { font-size: 12px; color: #adb5bd; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.badge {
  background: #e03131; color: #fff; font-size: 11px;
  min-width: 18px; height: 18px; border-radius: 9px;
  display: flex; align-items: center; justify-content: center;
  padding: 0 5px;
}
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.empty { flex: 1; display: flex; align-items: center; justify-content: center; color: #adb5bd; }
.chat-header {
  padding: 12px 18px; background: #fff; border-bottom: 1px solid #e9ecef;
  font-weight: 600; color: #343a40; display: flex; align-items: center; gap: 8px;
}
.online-dot { width: 8px; height: 8px; border-radius: 50%; background: #ced4da; }
.online-dot.on { background: #2f9e44; }
.msg-list { flex: 1; overflow-y: auto; padding: 16px 18px; }
.msg-row { display: flex; flex-direction: column; margin-bottom: 10px; align-items: flex-start; }
.msg-row.mine { align-items: flex-end; }
.bubble {
  max-width: 70%; padding: 9px 13px; border-radius: 12px;
  background: #fff; font-size: 14px; color: #212529;
  word-break: break-word; box-shadow: 0 1px 2px rgba(0,0,0,.06);
}
.msg-row.mine .bubble { background: #4263eb; color: #fff; }
.msg-img { max-width: 240px; max-height: 240px; border-radius: 8px; display: block; }
.ts { font-size: 11px; color: #adb5bd; margin-top: 3px; }
.input-bar {
  display: flex; gap: 8px; padding: 12px 16px;
  background: #fff; border-top: 1px solid #e9ecef;
}
.icon-btn { border: none; background: none; font-size: 20px; cursor: pointer; }
.input-bar input {
  flex: 1; padding: 10px 14px; border: 1px solid #dee2e6;
  border-radius: 8px; font-size: 14px;
}
.input-bar input:focus { outline: none; border-color: #4263eb; }
.send-btn {
  padding: 0 22px; background: #4263eb; color: #fff;
  border: none; border-radius: 8px; cursor: pointer; font-size: 14px;
}
.send-btn:disabled { background: #adb5bd; }
.conn-lost { text-align: center; color: #e8590c; font-size: 12px; padding: 4px; background: #fff4e6; }
</style>
