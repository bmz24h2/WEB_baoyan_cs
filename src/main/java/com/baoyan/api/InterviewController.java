package com.baoyan.api;

import com.baoyan.db.DatabaseService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.zip.*;

/**
 * 模拟面试 API（题库 + 练习记录）
 *
 * 题库：
 *   GET    /api/interview/questions         → 获取题目列表
 *   POST   /api/interview/questions         → 添加单题
 *   PUT    /api/interview/questions/{id}    → 编辑题目
 *   DELETE /api/interview/questions/{id}    → 删除题目
 *   POST   /api/interview/questions/import  → 批量导入（CSV/TXT/MD/JSON/DOCX/PDF）
 *   GET    /api/interview/categories        → 获取所有分类
 *
 * 练习记录（需登录）：
 *   POST   /api/interview/records           → 保存一条练习记录
 *   GET    /api/interview/records           → 查询所有记录
 *   GET    /api/interview/records/{id}      → 查询单条记录
 *   DELETE /api/interview/records/{id}      → 删除单条记录
 *   DELETE /api/interview/records           → 清空所有记录
 */
@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);

    @Autowired
    private DatabaseService db;

    @Autowired
    private AuthController auth;

    // ── 查询题目 ──────────────────────────────────────────────────────
    @GetMapping("/questions")
    public ResponseEntity<Map<String, Object>> listQuestions(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "500") int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = category != null && !category.isBlank()
            ? "SELECT * FROM interview_questions WHERE category=? ORDER BY id LIMIT ?"
            : "SELECT * FROM interview_questions ORDER BY category, id LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (category != null && !category.isBlank()) {
                ps.setString(1, category); ps.setInt(2, limit);
            } else {
                ps.setInt(1, limit);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",       rs.getInt("id"));
                m.put("category", rs.getString("category"));
                m.put("question", rs.getString("question"));
                m.put("hint",     rs.getString("hint"));
                m.put("source",   rs.getString("source"));
                m.put("createdAt",rs.getString("created_at"));
                result.add(m);
            }
        } catch (SQLException e) {
            log.error("[题库] 查询失败: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("questions", result, "count", result.size()));
    }

    // ── 获取所有分类 ──────────────────────────────────────────────────
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> listCategories() {
        List<String> cats = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT DISTINCT category FROM interview_questions ORDER BY category")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) cats.add(rs.getString(1));
        } catch (SQLException e) { log.error("[题库] 分类查询失败: {}", e.getMessage()); }
        return ResponseEntity.ok(Map.of("categories", cats));
    }

    // ── 添加单题 ──────────────────────────────────────────────────────
    @PostMapping("/questions")
    public ResponseEntity<Map<String, Object>> addQuestion(@RequestBody Map<String, String> body) {
        String category = body.getOrDefault("category", "自定义").trim();
        String question = body.getOrDefault("question", "").trim();
        String hint     = body.getOrDefault("hint",     "").trim();
        String source   = body.getOrDefault("source",   "manual");  // manual / ai_chat / import

        if (question.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "题目内容不能为空"));

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO interview_questions(category,question,hint,source) VALUES(?,?,?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category);
            ps.setString(2, question);
            ps.setString(3, hint.isEmpty() ? null : hint);
            ps.setString(4, source);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int newId = keys.next() ? keys.getInt(1) : -1;
            log.info("[题库] 新增题目 id={} category={} source={}", newId, category, source);
            return ResponseEntity.ok(Map.of("id", newId, "message", "添加成功"));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 编辑题目 ──────────────────────────────────────────────────────
    @PutMapping("/questions/{id}")
    public ResponseEntity<Map<String, Object>> updateQuestion(
            @PathVariable int id,
            @RequestBody Map<String, String> body) {
        String category = body.get("category");
        String question = body.get("question");
        String hint     = body.get("hint");
        if (question != null && question.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "题目内容不能为空"));
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE interview_questions SET category=COALESCE(?,category),"
                 + "question=COALESCE(?,question), hint=COALESCE(?,hint) WHERE id=?")) {
            ps.setString(1, category); ps.setString(2, question);
            ps.setString(3, hint);     ps.setInt(4, id);
            int rows = ps.executeUpdate();
            return rows > 0
                ? ResponseEntity.ok(Map.of("message", "更新成功"))
                : ResponseEntity.notFound().build();
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 删除题目 ──────────────────────────────────────────────────────
    @DeleteMapping("/questions/{id}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable int id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM interview_questions WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            return ResponseEntity.ok().build();
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── 批量删除题目 ──────────────────────────────────────────────────
    // body: { "ids": [1, 2, 3] }
    @DeleteMapping("/questions/batch")
    public ResponseEntity<Map<String, Object>> deleteQuestionsBatch(
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        if (ids == null || ids.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "ids 不能为空"));

        String placeholders = String.join(",",
            java.util.Collections.nCopies(ids.size(), "?"));
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM interview_questions WHERE id IN (" + placeholders + ")")) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            int deleted = ps.executeUpdate();
            log.info("[题库] 批量删除 {} 题", deleted);
            return ResponseEntity.ok(Map.of("deleted", deleted, "message", "已删除 " + deleted + " 题"));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 批量导入（CSV / TXT / JSON）────────────────────────────────────
    /**
     * 支持格式：
     *
     *  CSV（逗号分隔，第一行可选表头）：
     *    category,question,hint
     *    自我介绍,请做自我介绍,结构：院校→科研→目标
     *
     *  TXT（每行一题，支持前缀分类）：
     *    [自我介绍] 请做自我介绍
     *    请做自我介绍           ← 无前缀默认"自定义"
     *
     *  JSON 数组：
     *    [{"category":"自我介绍","question":"...","hint":"..."}]
     */
    @PostMapping("/questions/import")
    public ResponseEntity<Map<String, Object>> importQuestions(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "skip") String mode) {

        if (file.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));

        String filename = Objects.requireNonNull(file.getOriginalFilename(), "").toLowerCase();
        String content;
        try {
            byte[] bytes = file.getBytes();
            if (filename.endsWith(".docx")) {
                content = extractDocxText(bytes);
            } else if (filename.endsWith(".pdf")) {
                content = extractPdfText(bytes);
            } else {
                content = new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件读取失败：" + e.getMessage()));
        }
        if (content.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "未能从文件中提取到有效文字"));

        List<String[]> rows = new ArrayList<>(); // [category, question, hint]
        try {
            if (filename.endsWith(".json")) {
                rows = parseJson(content);
            } else if (filename.endsWith(".csv") || looksLikeCsv(content)) {
                // CSV 文件，或 DOCX/PDF/TXT 提取后内容像 CSV（含逗号分隔三列）
                rows = parseCsv(content);
            } else {
                // TXT / MD / DOCX / PDF 按 TXT 规则解析
                rows = parseTxt(content);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "解析失败：" + e.getMessage()));
        }

        if (rows.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "未解析到有效题目"));

        int imported = 0, updated = 0, skipped = 0;
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);

            // replace_all：先删除所有非内置题目
            if ("replace_all".equals(mode)) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM interview_questions WHERE source != 'builtin'")) {
                    ps.executeUpdate();
                }
            }

            for (String[] row : rows) {
                String cat = row[0].isBlank() ? "自定义" : row[0];
                String q   = row[1];
                String hint = row.length > 2 ? row[2] : null;

                if ("overwrite".equals(mode)) {
                    // 检查是否已存在（按 question 内容匹配）
                    boolean exists = false;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM interview_questions WHERE question=?")) {
                        ps.setString(1, q);
                        exists = ps.executeQuery().next();
                    }
                    if (exists) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE interview_questions SET category=?,hint=? WHERE question=?")) {
                            ps.setString(1, cat);
                            ps.setString(2, hint);
                            ps.setString(3, q);
                            ps.executeUpdate();
                        }
                        updated++;
                    } else {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO interview_questions(category,question,hint,source) VALUES(?,?,?,'import')")) {
                            ps.setString(1, cat); ps.setString(2, q); ps.setString(3, hint);
                            ps.executeUpdate();
                        }
                        imported++;
                    }
                } else {
                    // skip（默认）或 replace_all（删完后直接插）
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR IGNORE INTO interview_questions(category,question,hint,source) VALUES(?,?,?,'import')")) {
                        ps.setString(1, cat); ps.setString(2, q); ps.setString(3, hint);
                        int n = ps.executeUpdate();
                        if (n > 0) imported++; else skipped++;
                    }
                }
            }
            conn.commit();
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
        log.info("[题库] 导入完成 mode={} 新增={} 更新={} 跳过={} 文件={}", mode, imported, updated, skipped, filename);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("imported", imported);
        result.put("updated",  updated);
        result.put("skipped",  skipped);
        result.put("message",  "导入完成：新增 " + imported + " 题，更新 " + updated + " 题，跳过 " + skipped + " 题");
        return ResponseEntity.ok(result);
    }

    // ── 解析工具 ──────────────────────────────────────────────────────


    // ── DOCX 文字提取 ────────────────────────────────────────────────
    private static String extractDocxText(byte[] bytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (java.util.zip.ZipInputStream zin =
                new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                    xml = xml.replaceAll("<w:p[^>]*>", "\n")
                             .replaceAll("<[^>]+>", " ")
                             .replaceAll("[ \t]+", " ")
                             .replaceAll("\n +", "\n")
                             .replaceAll("\n{3,}", "\n\n");
                    xml = xml.replace("&amp;", "&").replace("&lt;", "<")
                             .replace("&gt;", ">").replace("&quot;", "\"");
                    sb.append(xml);
                    break;
                }
            }
        }
        return sb.toString().trim();
    }

    // ── PDF 文字提取（文字型 PDF）────────────────────────────────────
    private static String extractPdfText(byte[] bytes) {
        try {
            String raw = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            StringBuilder sb = new StringBuilder();
            java.util.regex.Pattern btPat = java.util.regex.Pattern.compile(
                "BT\\s+(.*?)\\s+ET", java.util.regex.Pattern.DOTALL);
            java.util.regex.Pattern tjPat = java.util.regex.Pattern.compile(
                "\\(([^()]*)\\)\\s*Tj");
            java.util.regex.Matcher btM = btPat.matcher(raw);
            while (btM.find()) {
                java.util.regex.Matcher tjM = tjPat.matcher(btM.group(1));
                while (tjM.find()) sb.append(tjM.group(1)).append(" ");
                sb.append("\n");
            }
            String result = sb.toString().replaceAll("\\s+", " ").trim();
            if (result.length() < 30) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\(([\\x20-\\x7E]{4,200})\\)").matcher(raw);
                StringBuilder sb2 = new StringBuilder();
                while (m.find()) sb2.append(m.group(1)).append(" ");
                result = sb2.toString().trim();
            }
            return result;
        } catch (Exception e) { return ""; }
    }

    /**
     * 判断内容是否像 CSV 格式：取前5个非空行，超过半数含有逗号分隔3段则认为是 CSV
     */
    private static boolean looksLikeCsv(String content) {
        String[] lines = content.split("[\r\n]+");
        int checked = 0, csvLike = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            checked++;
            String[] parts = line.split(",", 3);
            if (parts.length >= 2 && !parts[1].isBlank()) csvLike++;
            if (checked >= 5) break;
        }
        return checked > 0 && csvLike * 2 >= checked;
    }

    private List<String[]> parseCsv(String content) {
        List<String[]> rows = new ArrayList<>();
        for (String line : content.split("[\r\n]+")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase();
            // 跳过表头行和说明行（任意位置均跳过）
            if (lower.startsWith("category") || lower.startsWith("分类") || lower.startsWith("类别"))
                continue;
            // 跳过说明/注释类文字（含"说明"、"注"、"#"开头）
            if (lower.startsWith("说明") || lower.startsWith("#") || lower.startsWith("//") || lower.startsWith("注：") || lower.startsWith("（注"))
                continue;
            String[] parts = line.split(",", 3);
            if (parts.length < 2) continue;
            String cat = parts[0].trim();
            String q   = parts[1].trim();
            // 跳过题目列是纯占位符（question/hint 等关键词）
            if (q.isBlank() || q.equalsIgnoreCase("question") || q.equalsIgnoreCase("hint"))
                continue;
            // 跳过分类列是纯占位符
            if (cat.equalsIgnoreCase("category") || cat.equalsIgnoreCase("question"))
                continue;
            rows.add(new String[]{
                cat.isBlank() ? "自定义" : cat,
                q,
                parts.length > 2 ? parts[2].trim() : ""
            });
        }
        return rows;
    }

    private List<String[]> parseTxt(String content) {
        List<String[]> rows = new ArrayList<>();
        String currentCat = "自定义";
        for (String line : content.split("[\r\n]+")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            // [分类] 题目文字  or  # 分类名
            if (line.startsWith("[") && line.contains("]")) {
                int end = line.indexOf(']');
                currentCat = line.substring(1, end).trim();
                String rest = line.substring(end + 1).trim();
                if (!rest.isBlank()) rows.add(new String[]{currentCat, rest, ""});
            } else if (line.startsWith("#") && line.length() < 20) {
                currentCat = line.substring(1).trim();
            } else {
                rows.add(new String[]{currentCat, line, ""});
            }
        }
        return rows;
    }

    private List<String[]> parseJson(String json) {
        // 简单正则解析（不引入 Jackson 依赖）
        List<String[]> rows = new ArrayList<>();
        java.util.regex.Pattern objPat = java.util.regex.Pattern.compile(
            "\\{([^{}]*)\\}", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher objM = objPat.matcher(json);
        while (objM.find()) {
            String obj = objM.group(1);
            String cat  = extractJsonString(obj, "category");
            String q    = extractJsonString(obj, "question");
            String hint = extractJsonString(obj, "hint");
            if (q != null && !q.isBlank())
                rows.add(new String[]{cat != null ? cat : "自定义", q, hint != null ? hint : ""});
        }
        return rows;
    }

    private static String extractJsonString(String obj, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .matcher(obj);
        return m.find() ? m.group(1).replace("\\\"","\"").replace("\\n","\n") : null;
    }
    // ════════════════════════════════════════════════════════════════
    // 练习记录接口（需登录）
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/records")
    public ResponseEntity<?> saveRecord(HttpServletRequest req,
                                        @RequestBody Map<String, Object> body) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();
        String questionId   = strVal(body, "questionId");
        String questionText = strVal(body, "questionText");
        String category     = strVal(body, "category");
        String answer       = strVal(body, "answer");
        String feedback     = strVal(body, "feedback");
        int    duration     = intVal(body, "duration");
        if (answer.isBlank() && feedback.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "答案和点评不能都为空"));
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO interview_records" +
                 " (user_id,question_id,question_text,category,answer,feedback,duration_sec,created_at)" +
                 " VALUES (?,?,?,?,?,?,?,datetime('now','localtime'))",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt   (1, user.id());
            ps.setString(2, questionId.isBlank()   ? null : questionId);
            ps.setString(3, questionText.isBlank() ? null : questionText);
            ps.setString(4, category.isBlank()     ? "自由练习" : category);
            ps.setString(5, answer.isBlank()       ? null : answer);
            ps.setString(6, feedback.isBlank()     ? null : feedback);
            ps.setInt   (7, duration);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            long id = keys.next() ? keys.getLong(1) : -1;
            log.info("[面试记录] 用户 {} 保存 id={} 分类={}", user.username(), id, category);
            return ResponseEntity.ok(Map.of("id", id, "message", "已保存"));
        } catch (SQLException e) {
            log.error("[面试记录] 保存失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/records")
    public ResponseEntity<?> listRecords(HttpServletRequest req,
                                         @RequestParam(defaultValue = "100") int limit) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id,question_id,question_text,category,answer,feedback,duration_sec,created_at" +
                 " FROM interview_records WHERE user_id=? ORDER BY created_at DESC LIMIT ?")) {
            ps.setInt(1, user.id()); ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",           rs.getLong  ("id"));
                m.put("questionId",   rs.getString("question_id"));
                m.put("questionText", rs.getString("question_text"));
                m.put("category",     rs.getString("category"));
                m.put("answer",       rs.getString("answer"));
                m.put("feedback",     rs.getString("feedback"));
                m.put("duration",     rs.getInt   ("duration_sec"));
                m.put("createdAt",    rs.getString("created_at"));
                rows.add(m);
            }
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("records", rows, "count", rows.size()));
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<?> getRecord(HttpServletRequest req, @PathVariable long id) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id,question_id,question_text,category,answer,feedback,duration_sec,created_at" +
                 " FROM interview_records WHERE id=? AND user_id=?")) {
            ps.setLong(1, id); ps.setInt(2, user.id());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return ResponseEntity.notFound().build();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",           rs.getLong  ("id"));
            m.put("questionId",   rs.getString("question_id"));
            m.put("questionText", rs.getString("question_text"));
            m.put("category",     rs.getString("category"));
            m.put("answer",       rs.getString("answer"));
            m.put("feedback",     rs.getString("feedback"));
            m.put("duration",     rs.getInt   ("duration_sec"));
            m.put("createdAt",    rs.getString("created_at"));
            return ResponseEntity.ok(m);
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/records/{id}")
    public ResponseEntity<?> deleteRecord(HttpServletRequest req, @PathVariable long id) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM interview_records WHERE id=? AND user_id=?")) {
            ps.setLong(1, id); ps.setInt(2, user.id());
            int n = ps.executeUpdate();
            return n > 0 ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/records")
    public ResponseEntity<?> clearRecords(HttpServletRequest req) {
        var user = auth.resolveUser(req);
        if (user == null) return ResponseEntity.status(401).build();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM interview_records WHERE user_id=?")) {
            ps.setInt(1, user.id());
            int n = ps.executeUpdate();
            return ResponseEntity.ok(Map.of("deleted", n, "message", "已清空"));
        } catch (SQLException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private static String strVal(Map<String, Object> m, String key) {
        Object v = m.get(key); return v == null ? "" : v.toString().trim();
    }
    private static int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key); if (v == null) return 0;
        try { return ((Number) v).intValue(); } catch (Exception e) { return 0; }
    }

}