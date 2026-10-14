package com.friend.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final AuthService auth;
    private final JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();
    // username -> session（同一用户多处登录取最后一个）
    private final Map<String, WebSocketSession> online = new ConcurrentHashMap<>();
    // sessionId -> username
    private final Map<String, String> sessionUser = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(AuthService auth, JdbcTemplate jdbc) {
        this.auth = auth;
        this.jdbc = jdbc;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        String token = null;
        if (query != null) {
            for (String p : query.split("&")) {
                if (p.startsWith("token=")) token = p.substring(6);
            }
        }
        AuthService.UserInfo user = token != null ? auth.resolveToken(token) : null;
        if (user == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        WebSocketSession old = online.put(user.username(), session);
        if (old != null && old.isOpen()) {
            sessionUser.remove(old.getId());
            old.close();
        }
        sessionUser.put(session.getId(), user.username());
        broadcastOnline();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String from = sessionUser.get(session.getId());
        if (from == null) return;
        JsonNode node = om.readTree(message.getPayload());
        String type = node.path("type").asText("");
        if ("msg".equals(type)) {
            String to = node.path("to").asText("");
            String contentType = node.path("contentType").asText("text");
            String content = node.path("content").asText("");
            if (to.isBlank() || content.isBlank() || content.length() > 4000) return;
            if (!contentType.matches("text|image")) contentType = "text";
            long ts = System.currentTimeMillis();
            jdbc.update("INSERT INTO messages (from_user, to_user, content_type, content, created_at) VALUES (?,?,?,?,?)",
                    from, to, contentType, content, ts);

            Map<String, Object> envelope = Map.of(
                    "type", "msg", "from", from, "to", to,
                    "contentType", contentType, "content", content, "ts", ts);
            String payload = om.writeValueAsString(envelope);
            deliver(to, payload);
            deliver(from, payload); // 回显给发送方（多端同步）
        } else if ("ping".equals(type)) {
            session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        }
    }

    private void deliver(String username, String payload) {
        WebSocketSession s = online.get(username);
        if (s != null && s.isOpen()) {
            try {
                s.sendMessage(new TextMessage(payload));
            } catch (IOException ignored) {
            }
        }
        // 不在线则只落库，上线后通过 /api/history 拉取
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String user = sessionUser.remove(session.getId());
        if (user != null) {
            WebSocketSession cur = online.get(user);
            if (cur != null && cur.getId().equals(session.getId())) {
                online.remove(user);
            }
            broadcastOnline();
        }
    }

    private void broadcastOnline() {
        List<String> users = List.copyOf(online.keySet());
        try {
            String payload = om.writeValueAsString(Map.of("type", "presence", "online", users));
            for (WebSocketSession s : online.values()) {
                if (s.isOpen()) s.sendMessage(new TextMessage(payload));
            }
        } catch (IOException ignored) {
        }
    }
}
