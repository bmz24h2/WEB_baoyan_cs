package com.baoyan.api;

import com.baoyan.db.DatabaseService;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 用户认证接口
 *
 * POST /api/auth/register  — 注册（username, password）
 * POST /api/auth/login     — 登录（username, password）→ 设置 HttpOnly Cookie
 * POST /api/auth/logout    — 登出（清除 Cookie + DB session）
 * GET  /api/auth/whoami    — 当前登录用户信息（401 = 未登录）
 *
 * Session：BAOYAN_SESSION Cookie（HttpOnly，30 天有效）
 * 密码：SHA-256(salt + password)，salt 为随机 UUID
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String COOKIE_NAME = "BAOYAN_SESSION";
    private static final int    SESSION_DAYS = 30;
    private static final DateTimeFormatter DF =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private DatabaseService db;

    // ── 注册 ─────────────────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();

        if (username.isBlank() || username.length() < 2)
            return err("用户名至少 2 个字符");
        if (password.isBlank() || password.length() < 6)
            return err("密码至少 6 个字符");
        if (!username.matches("[\\w\\u4e00-\\u9fff\\-_.]+"))
            return err("用户名只能包含字母、数字、中文、_、-、.");

        String salt     = UUID.randomUUID().toString();
        String pwdHash  = hashPassword(password, salt);

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO users(username,password,salt) VALUES(?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, pwdHash);
            ps.setString(3, salt);
            ps.executeUpdate();
            log.info("[Auth] 新用户注册: {}", username);
            return ResponseEntity.status(201).body(Map.of("message", "注册成功"));
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) return err("用户名已被占用");
            log.error("[Auth] 注册失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 登录 ─────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id,password,salt FROM users WHERE username=?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return err("用户名或密码错误");

            int    userId   = rs.getInt("id");
            String storedPw = rs.getString("password");
            String salt     = rs.getString("salt");

            if (!hashPassword(password, salt).equals(storedPw))
                return err("用户名或密码错误");

            // 创建 session
            String token   = UUID.randomUUID().toString();
            String expires = LocalDateTime.now()
                                 .plusDays(SESSION_DAYS)
                                 .format(DF);
            try (PreparedStatement ps2 = conn.prepareStatement(
                     "INSERT INTO user_sessions(token,user_id,expires_at) VALUES(?,?,?)")) {
                ps2.setString(1, token);
                ps2.setInt(2, userId);
                ps2.setString(3, expires);
                ps2.executeUpdate();
            }

            // 设置 HttpOnly Cookie
            Cookie cookie = new Cookie(COOKIE_NAME, token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(SESSION_DAYS * 24 * 3600);
            response.addCookie(cookie);

            log.info("[Auth] 用户登录: {} (id={})", username, userId);
            return ResponseEntity.ok(Map.of(
                "userId",   userId,
                "username", username,
                "message",  "登录成功"));
        } catch (SQLException e) {
            log.error("[Auth] 登录失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 登出 ─────────────────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String token = extractToken(request);
        if (token != null) {
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM user_sessions WHERE token=?")) {
                ps.setString(1, token);
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        }
        // 清除 Cookie
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }

    // ── 当前用户信息 ──────────────────────────────────────────────────
    @GetMapping("/whoami")
    public ResponseEntity<Map<String, Object>> whoami(HttpServletRequest request) {
        UserInfo user = resolveUser(request);
        if (user == null) return ResponseEntity.status(401).build();
        // 读取头像
        String avatar = null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT avatar_b64 FROM user_profiles WHERE user_id=?")) {
            ps.setInt(1, user.id());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) avatar = rs.getString("avatar_b64");
        } catch (SQLException ignored) {}
        Map<String,Object> resp = new java.util.LinkedHashMap<>();
        resp.put("userId",   user.id());
        resp.put("username", user.username());
        resp.put("avatar",   avatar);
        return ResponseEntity.ok(resp);
    }

    // ── 修改密码 ──────────────────────────────────────────────────────
    @PutMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        UserInfo user = resolveUser(request);
        if (user == null) return ResponseEntity.status(401).build();

        String current  = body.getOrDefault("currentPassword", "");
        String newPwd   = body.getOrDefault("newPassword",     "").trim();
        if (newPwd.length() < 6) return err("新密码至少 6 个字符");

        try (Connection conn = db.getConnection()) {
            // 验证当前密码
            PreparedStatement ps = conn.prepareStatement(
                "SELECT password,salt FROM users WHERE id=?");
            ps.setInt(1, user.id());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return err("用户不存在");
            if (!hashPassword(current, rs.getString("salt")).equals(rs.getString("password")))
                return err("当前密码错误");

            // 更新密码
            String newSalt = UUID.randomUUID().toString();
            String newHash = hashPassword(newPwd, newSalt);
            PreparedStatement ps2 = conn.prepareStatement(
                "UPDATE users SET password=?, salt=? WHERE id=?");
            ps2.setString(1, newHash); ps2.setString(2, newSalt); ps2.setInt(3, user.id());
            ps2.executeUpdate();

            // 使其他设备的 session 失效（保留当前 session）
            String curToken = extractToken(request);
            PreparedStatement ps3 = conn.prepareStatement(
                "DELETE FROM user_sessions WHERE user_id=? AND token!=?");
            ps3.setInt(1, user.id()); ps3.setString(2, curToken != null ? curToken : "");
            ps3.executeUpdate();

            log.info("[Auth] 用户 {} 修改了密码", user.username());
            return ResponseEntity.ok(Map.of("message", "密码修改成功"));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** 从 request 中解析当前登录用户，未登录返回 null */
    public UserInfo resolveUser(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT u.id, u.username FROM user_sessions s
                 JOIN users u ON u.id = s.user_id
                 WHERE s.token = ?
                   AND s.expires_at > datetime('now','localtime')""")) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            return new UserInfo(rs.getInt("id"), rs.getString("username"));
        } catch (SQLException e) { return null; }
    }

    public record UserInfo(int id, String username) {}

    /** 从 Cookie 中提取 session token */
    static String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies)
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        return null;
    }

    // ── 内部工具 ──────────────────────────────────────────────────────
    private static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((salt + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }

    private static ResponseEntity<Map<String, Object>> err(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }
}