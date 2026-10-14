# friend-chat — 朋友内部聊天（Electron + Spring Boot）

> 参考商业 IM 客户端交互设计（REA 静态分析证据驱动），自研的私有部署小圈子聊天软件。
> 服务端与客户端均为自研代码，无任何第三方版权素材。

## 架构

```
Electron 客户端 (Vue3 + electron 主进程)          Spring Boot 服务端
├─ 登录（账号+邀请码注册）                        ├─ REST: 注册/登录/好友/历史/图片
├─ 会话列表 + 消息流 + 输入框                     ├─ WebSocket: 在线转发 + 离线入库
├─ 本地 SQLite 聊天记录缓存                       └─ SQLite 持久化（用户/消息）
├─ 托盘 + 系统通知 + 未读角标
└─ preload contextBridge 安全桥接
```

## 快速开始

### 1. 启动服务端

```bash
cd server
mvn spring-boot:run          # 默认 8082 端口
```

首次启动自动建表并写入初始邀请码：`FRIEND-2026`（可注册 3 个账号，够小圈子用；改邀请码见下）。

### 2. 启动客户端（开发模式）

```bash
cd client
npm install                  # 使用 npmmirror: npm install --registry=https://registry.npmmirror.com
npm run dev                  # Vite 渲染进程开发服务器 (5173)
npm run electron:dev         # 另开终端启动 Electron 壳（加载 5173）
```

### 3. 生产构建

```bash
npm run build                # 打出 Windows 安装包 (client/dist/)
```

## 服务端 API 一览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/register | 注册 `{username, password, inviteCode}` |
| POST | /api/login | 登录，返回 token |
| GET | /api/friends | 好友列表（内部软件：全员互为好友） |
| GET | /api/history?withUser=&before=&limit= | 拉取历史消息 |
| POST | /api/image | 上传图片（multipart） |
| WS | /ws?token= | WebSocket：发消息/收消息/在线状态 |

## 客户端 IPC 面（对齐商业 IM 的精简版）

参考 yidongbangong 的 72 个 IPC 通道，取核心 10 个：

`win:minimize` `win:maximize` `win:close` `tray:flash` `notify:show`
`file:open-image` `app:version` `store:get` `store:set` `sql:exec`

## 安全说明

- 所有通信走 localhost / 自建服务器，WebSocket 带 token 鉴权
- preload 用 contextBridge 最小暴露面（不暴露 require/node）
- 密码 BCrypt 哈希存储
- 图片上传做类型与大小校验
