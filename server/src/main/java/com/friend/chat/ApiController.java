package com.friend.chat;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final AuthService auth;
    private final JdbcTemplate jdbc;

    public ApiController(AuthService auth, JdbcTemplate jdbc) {
        this.auth = auth;
        this.jdbc = jdbc;
    }

    private AuthService.UserInfo requireUser(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("未登录");
        AuthService.UserInfo u = auth.resolveToken(token);
        if (u == null) throw new IllegalArgumentException("token 失效");
        return u;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        Long uid = auth.register(body.get("username"), body.get("password"), body.get("inviteCode"));
        return Map.of("ok", true, "userId", uid);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String token = auth.login(body.get("username"), body.get("password"));
        var u = auth.resolveToken(token);
        return Map.of("ok", true, "token", token,
                "user", Map.of("username", u.username(), "displayName", u.displayName()));
    }

    @GetMapping("/friends")
    public List<Map<String, Object>> friends(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : null;
        var me = requireUser(token);
        // 内部软件简化：全员互为好友
        var rows = jdbc.queryForList(
            "SELECT username, display_name FROM users WHERE username != ? ORDER BY username", me.username());
        return rows.stream().map(r -> Map.<String, Object>of(
                "username", r.get("username"), "displayName", r.get("display_name"))).toList();
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam String withUser,
            @RequestParam(required = false, defaultValue = "0") long before,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : null;
        var me = requireUser(token);
        if (before <= 0) before = System.currentTimeMillis() + 60_000;
        int lim = Math.min(Math.max(limit, 1), 100);
        var rows = jdbc.queryForList("""
            SELECT from_user, to_user, content_type, content, created_at FROM messages
            WHERE (from_user = ? AND to_user = ?) OR (from_user = ? AND to_user = ?)
              AND created_at < ?
            ORDER BY created_at DESC LIMIT ?
            """, me.username(), withUser, withUser, me.username(), before, lim);
        Collections.reverse(rows);
        return rows;
    }

    @PostMapping("/image")
    public Map<String, Object> uploadImage(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : null;
        requireUser(token);
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|webp)$")) {
            throw new IllegalArgumentException("仅支持 png/jpg/gif/webp");
        }
        if (file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("图片不能超过 5MB");
        String ext = name.substring(name.lastIndexOf('.'));
        String saved = UUID.randomUUID().toString().replace("-", "") + ext;
        file.transferTo(java.nio.file.Path.of("./uploads", saved));
        return Map.of("ok", true, "url", "/uploads/" + saved);
    }
}
