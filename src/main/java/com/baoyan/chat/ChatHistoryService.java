package com.baoyan.chat;

import com.baoyan.db.DatabaseService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AI 顾问对话历史持久化服务
 *
 * 设计：
 *   - 复用主项目的 SQLite 数据库（output/faculty.db）
 *   - 两张表：chat_sessions（会话元数据）+ chat_messages（具体消息）
 *   - 容量上限：最多保留 50 个会话，超出时按 updated_at 删除最旧的
 *   - 删除会话时手动级联删消息（避免依赖 SQLite 的 PRAGMA foreign_keys）
 *
 * 与现有 DatabaseService 完全独立：
 *   - 自带 @Value 注入数据源 URL
 *   - 自带 @PostConstruct 在启动时建表，不需要改 BaoyanApp.main()
 */
@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
    private static final int    MAX_SESSIONS = 50;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    private Connection getConnection() throws SQLException {
        String path = datasourceUrl.replace("jdbc:sqlite:", "");
        new File(path).getParentFile().mkdirs();
        return DriverManager.getConnection(datasourceUrl);
    }

    @PostConstruct
    public void init() {
        try (Connection conn = getConnection(); Statement s = conn.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    title         TEXT NOT NULL,
                    created_at    TEXT NOT NULL,
                    updated_at    TEXT NOT NULL,
                    message_count INTEGER NOT NULL DEFAULT 0
                )
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_chat_sessions_updated ON chat_sessions(updated_at DESC)");
            s.execute("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id INTEGER NOT NULL,
                    role       TEXT NOT NULL,
                    content    TEXT NOT NULL,
                    model      TEXT,
                    created_at TEXT NOT NULL
                )
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_chat_messages_session ON chat_messages(session_id, created_at)");
            log.info("✅ ChatHistoryService initialized (max sessions: {})", MAX_SESSIONS);
        } catch (SQLException e) {
            log.error("Failed to init chat history tables", e);
        }
    }

    private String now() { return LocalDateTime.now().format(ISO); }

    private String generateTitle(String firstMessage) {
        String trimmed = firstMessage.trim().replaceAll("\\s+", " ");
        return trimmed.length() > 30 ? trimmed.substring(0, 30) + "…" : trimmed;
    }

    /** 创建新会话，返回 sessionId。同时触发清理（保留最新 MAX_SESSIONS 个）。 */
    public long createSession(String firstMessage) {
        String title = generateTitle(firstMessage);
        String t = now();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO chat_sessions (title, created_at, updated_at) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, t);
            ps.setString(3, t);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    enforceLimit(conn);
                    return id;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to create session", e);
        }
        return -1;
    }

    /** 保存一条消息，并更新会话的 updated_at 和 message_count */
    public void saveMessage(long sessionId, String role, String content, String model) {
        if (sessionId <= 0) return;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO chat_messages (session_id, role, content, model, created_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setLong  (1, sessionId);
                ps.setString(2, role);
                ps.setString(3, content);
                ps.setString(4, model);
                ps.setString(5, now());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE chat_sessions SET updated_at = ?, message_count = message_count + 1 WHERE id = ?")) {
                ps.setString(1, now());
                ps.setLong  (2, sessionId);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            log.error("Failed to save message", e);
        }
    }

    /** 列出所有会话（按 updated_at 倒序），不含具体消息 */
    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                "SELECT id, title, created_at, updated_at, message_count FROM chat_sessions ORDER BY updated_at DESC")) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",           rs.getLong  ("id"));
                m.put("title",        rs.getString("title"));
                m.put("createdAt",    rs.getString("created_at"));
                m.put("updatedAt",    rs.getString("updated_at"));
                m.put("messageCount", rs.getInt   ("message_count"));
                list.add(m);
            }
        } catch (SQLException e) {
            log.error("Failed to list sessions", e);
        }
        return list;
    }

    /** 获取一个会话的元数据 + 全部消息，按时间顺序 */
    public Map<String, Object> getSession(long sessionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, title, created_at, updated_at, message_count FROM chat_sessions WHERE id = ?")) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    result.put("id",           rs.getLong  ("id"));
                    result.put("title",        rs.getString("title"));
                    result.put("createdAt",    rs.getString("created_at"));
                    result.put("updatedAt",    rs.getString("updated_at"));
                    result.put("messageCount", rs.getInt   ("message_count"));
                }
            }
            List<Map<String, Object>> messages = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT role, content, model, created_at FROM chat_messages WHERE session_id = ? ORDER BY id")) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("role",      rs.getString("role"));
                        m.put("content",   rs.getString("content"));
                        m.put("model",     rs.getString("model"));
                        m.put("createdAt", rs.getString("created_at"));
                        messages.add(m);
                    }
                }
            }
            result.put("messages", messages);
        } catch (SQLException e) {
            log.error("Failed to get session", e);
            return null;
        }
        return result;
    }

    /** 删除一个会话及其全部消息 */
    public boolean deleteSession(long sessionId) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chat_messages WHERE session_id = ?")) {
                ps.setLong(1, sessionId);
                ps.executeUpdate();
            }
            int rows;
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chat_sessions WHERE id = ?")) {
                ps.setLong(1, sessionId);
                rows = ps.executeUpdate();
            }
            conn.commit();
            return rows > 0;
        } catch (SQLException e) {
            log.error("Failed to delete session", e);
            return false;
        }
    }

    /** 清空全部会话（用户主动调用） */
    public void deleteAllSessions() {
        try (Connection conn = getConnection(); Statement s = conn.createStatement()) {
            s.execute("DELETE FROM chat_messages");
            s.execute("DELETE FROM chat_sessions");
            log.info("All chat sessions cleared");
        } catch (SQLException e) {
            log.error("Failed to clear sessions", e);
        }
    }

    /** 检查会话是否存在 */
    public boolean exists(long sessionId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM chat_sessions WHERE id = ?")) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            return false;
        }
    }

    /** 超过 MAX_SESSIONS 时，删除最旧的若干会话及其消息 */
    private void enforceLimit(Connection conn) throws SQLException {
        long count;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM chat_sessions")) {
            rs.next();
            count = rs.getLong(1);
        }
        if (count <= MAX_SESSIONS) return;

        long toDelete = count - MAX_SESSIONS;
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM chat_sessions ORDER BY updated_at ASC LIMIT ?")) {
            ps.setLong(1, toDelete);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        if (ids.isEmpty()) return;

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chat_messages WHERE session_id IN (" + placeholders + ")")) {
            for (int i = 0; i < ids.size(); i++) ps.setLong(i + 1, ids.get(i));
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chat_sessions WHERE id IN (" + placeholders + ")")) {
            for (int i = 0; i < ids.size(); i++) ps.setLong(i + 1, ids.get(i));
            ps.executeUpdate();
        }
        log.info("Auto-deleted {} oldest sessions (limit: {})", ids.size(), MAX_SESSIONS);
    }
}
