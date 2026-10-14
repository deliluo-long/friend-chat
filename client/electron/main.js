// Electron 主进程：窗口/托盘/通知/IPC（参考商业 IM 客户端的核心能力面）
const { app, BrowserWindow, Tray, Menu, Notification, ipcMain, dialog, nativeImage } = require('electron')
const path = require('path')

const DEV_URL = process.env.FC_DEV_URL || ''
let mainWindow = null
let tray = null
let flashing = false

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1000,
    height: 680,
    minWidth: 760,
    minHeight: 520,
    frame: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })

  if (DEV_URL) {
    mainWindow.loadURL(DEV_URL)
  } else {
    mainWindow.loadFile(path.join(__dirname, '..', 'dist', 'index.html'))
  }

  mainWindow.on('close', (e) => {
    // 点关闭 = 最小化到托盘（IM 惯例）
    if (!app.isQuitting) {
      e.preventDefault()
      mainWindow.hide()
    }
  })
}

function createTray() {
  // 1x1 蓝点作为默认托盘图标（无版权素材）
  const icon = nativeImage.createFromBuffer(
    Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64'),
    { scaleFactor: 1.0 }
  )
  tray = new Tray(icon)
  const menu = Menu.buildFromTemplate([
    { label: '显示主窗口', click: () => showMainWindow() },
    { label: '退出', click: () => { app.isQuitting = true; app.quit() } }
  ])
  tray.setToolTip('FriendChat')
  tray.setContextMenu(menu)
  tray.on('click', () => showMainWindow())
}

function showMainWindow() {
  if (!mainWindow) return
  mainWindow.show()
  mainWindow.focus()
}

// ============ IPC 面（对齐商业 IM 的核心 10 通道）============

ipcMain.handle('win:minimize', () => mainWindow?.minimize())
ipcMain.handle('win:maximize', () => {
  if (!mainWindow) return
  mainWindow.isMaximized() ? mainWindow.unmaximize() : mainWindow.maximize()
  return mainWindow.isMaximized()
})
ipcMain.on('win:close', () => mainWindow?.hide())
ipcMain.handle('app:version', () => app.getVersion())

ipcMain.on('tray:flash', (_e, on) => {
  flashing = !!on
  if (tray) {
    if (flashing && !trayFlashTimer) {
      trayFlashTimer = setInterval(() => {
        tray.setImage(emptyIcon())
      }, 600)
    } else if (!flashing && trayFlashTimer) {
      clearInterval(trayFlashTimer)
      trayFlashTimer = null
      tray.setImage(baseIcon())
    }
  }
})

ipcMain.on('notify:show', (_e, { title, body }) => {
  if (Notification.isSupported()) {
    const n = new Notification({ title: title || 'FriendChat', body: body || '' })
    n.on('click', () => showMainWindow())
    n.show()
  }
})

ipcMain.handle('file:open-image', async () => {
  const r = await dialog.showOpenDialog(mainWindow, {
    properties: ['openFile'],
    filters: [{ name: '图片', extensions: ['png', 'jpg', 'jpeg', 'gif', 'webp'] }]
  })
  return r.canceled ? null : r.filePaths[0]
})

ipcMain.handle('file:read-image', async (_e, filePath) => {
  const fs = require('fs')
  try {
    return fs.readFileSync(filePath).toString('base64')
  } catch {
    return null
  }
})

// 极简 KV 存储（替代 electron-store，避免依赖）
const store = new Map()
ipcMain.handle('store:get', (_e, key) => store.get(key) ?? null)
ipcMain.handle('store:set', (_e, key, value) => { store.set(key, value) })

function baseIcon() {
  return nativeImage.createFromBuffer(
    Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64'),
    { scaleFactor: 1.0 }
  )
}
function emptyIcon() {
  return nativeImage.createEmpty()
}
let trayFlashTimer = null

app.whenReady().then(() => {
  createWindow()
  createTray()
})

app.on('window-all-closed', () => {
  // 不退出，驻留托盘
})
