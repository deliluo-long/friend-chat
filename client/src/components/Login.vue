<template>
  <div class="login-wrap">
    <div class="login-card">
      <h1>FriendChat</h1>
      <p class="sub">朋友圈子 · 私有部署</p>

      <div class="tabs">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <input v-model="form.username" placeholder="用户名（字母数字下划线）" @keyup.enter="submit" />
      <input v-model="form.password" type="password" placeholder="密码" @keyup.enter="submit" />
      <input v-if="mode === 'register'" v-model="form.inviteCode" placeholder="邀请码" @keyup.enter="submit" />

      <input v-model="serverInput" class="server" placeholder="服务器地址" @change="saveServer" />

      <button class="primary" :disabled="busy" @click="submit">
        {{ busy ? '请稍候…' : (mode === 'login' ? '进入' : '注册并进入') }}
      </button>

      <p v-if="error" class="error">{{ error }}</p>
    </div>
  </div>
</template>

<script>
export default {
  props: { apiBase: String },
  emits: ['logged-in'],
  data() {
    return {
      mode: 'login',
      form: { username: '', password: '', inviteCode: '' },
      serverInput: this.apiBase,
      busy: false,
      error: ''
    }
  },
  methods: {
    saveServer() {
      const v = this.serverInput.trim().replace(/\/+$/, '')
      this.$emit('update:apiBase', v)
      localStorage.setItem('fc_server', v)
    },
    async submit() {
      this.error = ''
      const { username, password, inviteCode } = this.form
      if (!username || !password) { this.error = '请填写用户名和密码'; return }
      this.busy = true
      try {
        let url, body
        if (this.mode === 'login') {
          url = `${this.serverInput.trim().replace(/\/+$/, '')}/api/login`
          body = { username, password }
        } else {
          if (!inviteCode) { this.error = '请填写邀请码'; this.busy = false; return }
          url = `${this.serverInput.trim().replace(/\/+$/, '')}/api/register`
          body = { username, password, inviteCode }
          await fetch(url, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
          })
          url = `${this.serverInput.trim().replace(/\/+$/, '')}/api/login`
        }
        const res = await fetch(url, {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password })
        })
        const data = await res.json()
        if (!res.ok || !data.ok) {
          this.error = data.error || '登录失败'
          return
        }
        this.$emit('logged-in', {
          token: data.token,
          username: data.user.username,
          displayName: data.user.displayName,
          apiBase: this.serverInput.trim().replace(/\/+$/, '')
        })
      } catch (e) {
        this.error = '无法连接服务器：' + e.message
      } finally {
        this.busy = false
      }
    }
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #4263eb 0%, #7048e8 100%);
}
.login-card {
  width: 360px;
  background: #fff;
  border-radius: 12px;
  padding: 32px 28px;
  box-shadow: 0 12px 40px rgba(0,0,0,.18);
}
h1 { font-size: 22px; color: #212529; text-align: center; }
.sub { text-align: center; color: #868e96; font-size: 12px; margin: 6px 0 18px; }
.tabs { display: flex; margin-bottom: 14px; border-bottom: 1px solid #e9ecef; }
.tabs button {
  flex: 1; padding: 8px; border: none; background: none;
  font-size: 14px; color: #868e96; cursor: pointer;
}
.tabs button.active { color: #4263eb; border-bottom: 2px solid #4263eb; }
input {
  width: 100%; padding: 10px 12px; margin-bottom: 10px;
  border: 1px solid #dee2e6; border-radius: 8px; font-size: 14px;
}
input:focus { outline: none; border-color: #4263eb; }
input.server { font-size: 12px; color: #495057; background: #f8f9fa; }
button.primary {
  width: 100%; padding: 11px; margin-top: 4px;
  background: #4263eb; color: #fff; border: none;
  border-radius: 8px; font-size: 15px; cursor: pointer;
}
button.primary:hover { background: #3b5bdb; }
button.primary:disabled { background: #adb5bd; cursor: default; }
.error { color: #e03131; font-size: 13px; margin-top: 10px; text-align: center; }
</style>
