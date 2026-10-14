<template>
  <div class="app-root" :class="{ 'is-frameless': isElectron }">
    <div v-if="isElectron" class="title-bar">
      <span class="title-text">FriendChat</span>
      <div class="title-actions">
        <button class="tb-btn" @click="win.minimize()">—</button>
        <button class="tb-btn" @click="win.maximize()">□</button>
        <button class="tb-btn tb-close" @click="win.close()">✕</button>
      </div>
    </div>
    <Login v-if="!session" @logged-in="onLoggedIn" :api-base="apiBase" />
    <Chat v-else :session="session" :api-base="apiBase" @logout="onLogout" />
  </div>
</template>

<script>
import Login from './components/Login.vue'
import Chat from './components/Chat.vue'

export default {
  components: { Login, Chat },
  data() {
    return {
      isElectron: typeof window.fc !== 'undefined',
      win: typeof window.fc !== 'undefined' ? window.fc.win : null,
      apiBase: localStorage.getItem('fc_server') || 'http://localhost:8082',
      session: null
    }
  },
  created() {
    // 恢复本地会话
    try {
      const saved = localStorage.getItem('fc_session')
      if (saved) this.session = JSON.parse(saved)
    } catch { /* ignore */ }
  },
  methods: {
    onLoggedIn(session) {
      this.session = session
      localStorage.setItem('fc_session', JSON.stringify(session))
    },
    onLogout() {
      this.session = null
      localStorage.removeItem('fc_session')
    }
  }
}
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; }
body { font-family: "Segoe UI", "Microsoft YaHei", sans-serif; background: #f5f6fa; }

.title-bar {
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #4263eb;
  color: #fff;
  -webkit-app-region: drag;
  user-select: none;
}
.title-text { font-size: 13px; padding-left: 12px; }
.title-actions { display: flex; -webkit-app-region: no-drag; }
.tb-btn {
  width: 44px; height: 34px;
  border: none; background: transparent; color: #fff;
  font-size: 13px; cursor: pointer;
}
.tb-btn:hover { background: rgba(255,255,255,.15); }
.tb-close:hover { background: #e03131; }

.app-root { height: 100%; display: flex; flex-direction: column; }
.app-root.is-frameless > :not(.title-bar) { flex: 1; min-height: 0; }
.app-root:not(.is-frameless) > .title-bar { display: none; }
</style>
