package com.friend.chat;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DbConfig {

    @Bean
    CommandLineRunner initDb(JdbcTemplate jdbc) {
        return args -> {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )""");
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    from_user TEXT NOT NULL,
                    to_user TEXT NOT NULL,
                    content_type TEXT NOT NULL DEFAULT 'text',
                    content TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )""");
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS invite_codes (
                    code TEXT PRIMARY KEY,
                    max_uses INTEGER NOT NULL,
                    used_count INTEGER NOT NULL DEFAULT 0
                )""");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_msg_pair ON messages(from_user, to_user, created_at)");

            // 初始邀请码（可在 SQLite 中自行修改）
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM invite_codes WHERE code = 'FRIEND-2026'", Integer.class);
            if (cnt != null && cnt == 0) {
                jdbc.update("INSERT INTO invite_codes (code, max_uses) VALUES ('FRIEND-2026', 3)");
            }

            Files.createDirectories(Path.of("./uploads"));
        };
    }
}
