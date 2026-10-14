// preload：contextBridge 最小暴露面（不暴露 require/node）
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('fc', {
  win: {
    minimize: () => ipcRenderer.invoke('win:minimize'),
    maximize: () => ipcRenderer.invoke('win:maximize'),
    close: () => ipcRenderer.send('win:close')
  },
  app: {
    version: () => ipcRenderer.invoke('app:version')
  },
  tray: {
    flash: (on) => ipcRenderer.send('tray:flash', on)
  },
  notify: {
    show: (title, body) => ipcRenderer.send('notify:show', { title, body })
  },
  file: {
    openImage: () => ipcRenderer.invoke('file:open-image'),
    readImage: (filePath) => ipcRenderer.invoke('file:read-image', filePath)
  },
  store: {
    get: (key) => ipcRenderer.invoke('store:get', key),
    set: (key, value) => ipcRenderer.invoke('store:set', key, value)
  }
})
