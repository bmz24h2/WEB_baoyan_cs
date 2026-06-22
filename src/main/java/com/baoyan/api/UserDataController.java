package com.baoyan.api;

import com.baoyan.db.DatabaseService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.*;

/**
 * 用户个人数据接口（需登录）
 *
 * 个人信息（Profile）：
 *   GET  /api/user/profile          — 读取
 *   PUT  /api/user/profile          — 保存（整体替换）
 *
 * 面试练习进度：
 *   GET  /api/user/progress         — 读取已完成题目 ID 列表
 *   POST /api/user/progress         — {questionId} 标记完成
 *   DELETE /api/user/progress/{id}  — 取消标记
 *   DELETE /api/user/progress       — 清空所有进度
 */
@RestController
@RequestMapping("/api/user")
public class UserDataController {

    private static final Logger log = LoggerFactory.getLogger(UserDataController.class);

    @Autowired private DatabaseService db;
    @Autowired private AuthController  auth;

    // ── 个人信息 ──────────────────────────────────────────────────────

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(HttpServletRequest req) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT profile_json FROM user_profiles WHERE user_id=?")) {
            ps.setInt(1, user.id());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return ResponseEntity.ok(Map.of("profile", (Object) null));
            return ResponseEntity.ok(Map.of("profile", rs.getString("profile_json")));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> saveProfile(
            HttpServletRequest req,
            @RequestBody Map<String, Object> body) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();

        // body 直接作为 profile JSON 序列化存储
        String json;
        try {
            // 简单 Map → JSON（避免引入 Jackson 显式依赖）
            json = mapToJson(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "JSON 序列化失败"));
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT INTO user_profiles(user_id, profile_json, updated_at)
                 VALUES(?, ?, datetime('now','localtime'))
                 ON CONFLICT(user_id) DO UPDATE
                   SET profile_json=excluded.profile_json,
                       updated_at=excluded.updated_at""")) {
            ps.setInt(1, user.id());
            ps.setString(2, json);
            ps.executeUpdate();
            return ResponseEntity.ok(Map.of("message", "已保存"));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 面试练习进度 ──────────────────────────────────────────────────

    @GetMapping("/progress")
    public ResponseEntity<?> getProgress(HttpServletRequest req) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();

        List<String> done = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT question_id FROM interview_progress WHERE user_id=?")) {
            ps.setInt(1, user.id());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) done.add(rs.getString("question_id"));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("doneIds", done));
    }

    @PostMapping("/progress")
    public ResponseEntity<?> markDone(
            HttpServletRequest req,
            @RequestBody Map<String, String> body) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();

        String qid = body.getOrDefault("questionId", "").trim();
        if (qid.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "questionId 不能为空"));

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT OR IGNORE INTO interview_progress(user_id, question_id)
                 VALUES(?, ?)""")) {
            ps.setInt(1, user.id());
            ps.setString(2, qid);
            ps.executeUpdate();
            return ResponseEntity.ok(Map.of("message", "已标记完成"));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/progress/{questionId}")
    public ResponseEntity<?> unmarkDone(
            HttpServletRequest req,
            @PathVariable String questionId) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM interview_progress WHERE user_id=? AND question_id=?")) {
            ps.setInt(1, user.id());
            ps.setString(2, questionId);
            ps.executeUpdate();
            return ResponseEntity.ok().build();
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/progress")
    public ResponseEntity<?> clearProgress(HttpServletRequest req) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM interview_progress WHERE user_id=?")) {
            ps.setInt(1, user.id());
            ps.executeUpdate();
            return ResponseEntity.ok(Map.of("message", "进度已清空"));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 头像 ──────────────────────────────────────────────────────────

    /** 读取头像（返回 base64 data URL 或 null） */
    @GetMapping("/avatar")
    public ResponseEntity<?> getAvatar(HttpServletRequest req) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT avatar_b64 FROM user_profiles WHERE user_id=?")) {
            ps.setInt(1, user.id());
            ResultSet rs = ps.executeQuery();
            String avatar = rs.next() ? rs.getString("avatar_b64") : null;
            return ResponseEntity.ok(Map.of("avatar", avatar != null ? avatar : ""));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** 保存头像（body: {avatar: "data:image/...;base64,..."}, 限 200KB） */
    @PutMapping("/avatar")
    public ResponseEntity<?> saveAvatar(HttpServletRequest req,
                                        @RequestBody Map<String,String> body) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();

        String b64 = body.getOrDefault("avatar", "").trim();
        if (b64.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "头像数据为空"));
        if (b64.length() > 2_800_000) // ~2MB base64
            return ResponseEntity.badRequest().body(Map.of("error", "头像文件过大（限 200KB）"));
        if (!b64.startsWith("data:image/"))
            return ResponseEntity.badRequest().body(Map.of("error", "格式不合法"));

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT INTO user_profiles(user_id, avatar_b64, updated_at)
                 VALUES(?, ?, datetime('now','localtime'))
                 ON CONFLICT(user_id) DO UPDATE
                   SET avatar_b64=excluded.avatar_b64,
                       updated_at=excluded.updated_at""")) {
            ps.setInt(1, user.id());
            ps.setString(2, b64);
            ps.executeUpdate();
            return ResponseEntity.ok(Map.of("message", "头像已保存"));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Map → JSON 简单序列化（只处理 String/Number/Boolean/null 值）──
    @SuppressWarnings("unchecked")
    private static String mapToJson(Object obj) {
        if (obj == null)                 return "null";
        if (obj instanceof String s)     return "\"" + s.replace("\\","\\\\")
                                                          .replace("\"","\\\"")
                                                          .replace("\n","\\n")
                                                          .replace("\r","\\r") + "\"";
        if (obj instanceof Number
         || obj instanceof Boolean)      return obj.toString();
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(mapToJson(list.get(i)));
            }
            return sb.append(']').toString();
        }
        if (obj instanceof Map<?,?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?,?> e : map.entrySet()) {
                if (!first) sb.append(',');
                sb.append('"').append(e.getKey()).append('"')
                  .append(':').append(mapToJson(e.getValue()));
                first = false;
            }
            return sb.append('}').toString();
        }
        return "\"" + obj + "\"";
    }
}