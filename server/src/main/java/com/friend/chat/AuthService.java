package com.friend.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> tokens = new HashMap<>(); // token -> userId

    public AuthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record UserInfo(Long id, String username, String displayName) {}

    public Long register(String username, String password, String inviteCode) {
        if (username == null || username.length() < 2 || username.length() > 20
                || !username.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("用户名需 2-20 位字母数字下划线");
        }
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("密码至少 4 位");
        }
        Integer codeOk = jdbc.queryForObject(
            "SELECT used_count < max_uses FROM invite_codes WHERE code = ?",
            Integer.class, inviteCode);
        if (codeOk == null || codeOk != 1) {
            throw new IllegalArgumentException("邀请码无效或已用完");
        }
        Integer exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        String hash = org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
            password, org.springframework.security.crypto.bcrypt.BCrypt.gensalt());
        jdbc.update("INSERT INTO users (username, password_hash, display_name, created_at) VALUES (?,?,?,?)",
            username, hash, username, System.currentTimeMillis());
        jdbc.update("UPDATE invite_codes SET used_count = used_count + 1 WHERE code = ?", inviteCode);
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    public String login(String username, String password) {
        var rows = jdbc.queryForList(
            "SELECT id, password_hash FROM users WHERE username = ?", username);
        if (rows.isEmpty()) throw new IllegalArgumentException("用户不存在");
        String hash = (String) rows.get(0).get("password_hash");
        if (!org.springframework.security.crypto.bcrypt.BCrypt.checkpw(password, hash)) {
            throw new IllegalArgumentException("密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "")
            + Long.toHexString(random.nextLong());
        tokens.put(token, ((Number) rows.get(0).get("id")).longValue());
        return token;
    }

    public UserInfo resolveToken(String token) {
        Long uid = tokens.get(token);
        if (uid == null) return null;
        var rows = jdbc.queryForList(
            "SELECT username, display_name FROM users WHERE id = ?", uid);
        if (rows.isEmpty()) return null;
        return new UserInfo(uid, (String) rows.get(0).get("username"),
                (String) rows.get(0).get("display_name"));
    }
}
