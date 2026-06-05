package com.baoyan;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * BaoyanApp.java — Spring Boot 应用全部逻辑
 *
 * 包含以下内部类（均为 public static，Spring 自动发现）：
 *   Teacher          教师数据模型
 *   DatabaseService  SQLite 数据库操作（@Service）
 *   ScraperService   爬虫引擎 + URL 验证器（@Service）
 *   ApiController    REST 接口（@RestController /api/**）
 *
 * 启动: mvn spring-boot:run
 * 打包: mvn package && java -jar target/baoyan-cs-navigator-1.0.0.jar
 */
@SpringBootApplication
public class BaoyanApp {

    private static final Logger log = LoggerFactory.getLogger(BaoyanApp.class);

    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(BaoyanApp.class, args);
        DatabaseService db = ctx.getBean(DatabaseService.class);
        db.initSchema();
        db.initStaticData();
        log.info("✅ 985/211 CS 保研导航已启动 → http://localhost:8080");
        log.info("   当前数据库: {} 条教师记录", db.countTeachers());
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(3600);
            }
        };
    }

    // ════════════════════════════════════════════════════════════════════════
    // Teacher — 教师数据模型
    // ════════════════════════════════════════════════════════════════════════

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Teacher {

        private Long   id;
        private String name;
        private String university;
        private String department;
        private String profileUrl;
        private String title;          // 教授 / 副教授 / 讲师 / 研究员
        private String researchAreas;  // 从个人主页解析的研究方向
        private String email;
        private String googleScholar;
        private String scrapedAt;

        public Teacher() {}

        public Teacher(String name, String university, String department, String profileUrl) {
            this.name        = name;
            this.university  = university;
            this.department  = department;
            this.profileUrl  = profileUrl;
            this.scrapedAt   = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        public Long   getId()              { return id; }
        public void   setId(Long id)       { this.id = id; }
        public String getName()            { return name; }
        public void   setName(String v)    { this.name = v; }
        public String getUniversity()      { return university; }
        public void   setUniversity(String v) { this.university = v; }
        public String getDepartment()      { return department; }
        public void   setDepartment(String v) { this.department = v; }
        public String getProfileUrl()      { return profileUrl; }
        public void   setProfileUrl(String v) { this.profileUrl = v; }
        public String getTitle()           { return title; }
        public void   setTitle(String v)   { this.title = v; }
        public String getResearchAreas()   { return researchAreas; }
        public void   setResearchAreas(String v) { this.researchAreas = v; }
        public String getEmail()           { return email; }
        public void   setEmail(String v)   { this.email = v; }
        public String getGoogleScholar()   { return googleScholar; }
        public void   setGoogleScholar(String v) { this.googleScholar = v; }
        public String getScrapedAt()       { return scrapedAt; }
        public void   setScrapedAt(String v) { this.scrapedAt = v; }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class SchoolInfo {

        private Long   id;
        private String university;
        private String category;
        private String title;
        private String url;
        private String source;
        private String snippet;
        private String publishedAt;
        private String scrapedAt;

        public SchoolInfo() {}

        public SchoolInfo(String university, String category, String title, String url, String source) {
            this.university = university;
            this.category   = category;
            this.title      = title;
            this.url        = url;
            this.source     = source;
            this.scrapedAt  = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        public Long   getId()              { return id; }
        public void   setId(Long id)       { this.id = id; }
        public String getUniversity()      { return university; }
        public void   setUniversity(String v) { this.university = v; }
        public String getCategory()        { return category; }
        public void   setCategory(String v) { this.category = v; }
        public String getTitle()           { return title; }
        public void   setTitle(String v)   { this.title = v; }
        public String getUrl()             { return url; }
        public void   setUrl(String v)     { this.url = v; }
        public String getSource()          { return source; }
        public void   setSource(String v)  { this.source = v; }
        public String getSnippet()         { return snippet; }
        public void   setSnippet(String v) { this.snippet = v; }
        public String getPublishedAt()     { return publishedAt; }
        public void   setPublishedAt(String v) { this.publishedAt = v; }
        public String getScrapedAt()       { return scrapedAt; }
        public void   setScrapedAt(String v) { this.scrapedAt = v; }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DatabaseService — SQLite 数据库操作
    // ════════════════════════════════════════════════════════════════════════

    @Service
    public static class DatabaseService {

        private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

        @Value("${spring.datasource.url}")
        private String datasourceUrl;

        private Connection getConnection() throws SQLException {
            String path = datasourceUrl.replace("jdbc:sqlite:", "");
            new File(path).getParentFile().mkdirs();
            return DriverManager.getConnection(datasourceUrl);
        }

        /** 初始化数据库表（启动时调用一次） */
        public void initSchema() {
            try (Connection conn = getConnection(); Statement s = conn.createStatement()) {
                s.execute("""
                    CREATE TABLE IF NOT EXISTS teachers (
                        id             INTEGER PRIMARY KEY AUTOINCREMENT,
                        name           TEXT NOT NULL,
                        university     TEXT NOT NULL,
                        department     TEXT,
                        profile_url    TEXT UNIQUE,
                        title          TEXT,
                        research_areas TEXT,
                        email          TEXT,
                        google_scholar TEXT,
                        scraped_at     TEXT
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS url_verify (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        university  TEXT,
                        department  TEXT,
                        url         TEXT,
                        confidence  TEXT,
                        ok          INTEGER,
                        status_code INTEGER,
                        redirect    TEXT,
                        alternative TEXT,
                        error       TEXT,
                        verified_at TEXT DEFAULT (datetime('now'))
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS scrape_log (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        url        TEXT,
                        status     TEXT,
                        message    TEXT,
                        created_at TEXT DEFAULT (datetime('now'))
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS school_info (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        university   TEXT NOT NULL,
                        category     TEXT,
                        title        TEXT NOT NULL,
                        url          TEXT UNIQUE,
                        source       TEXT,
                        snippet      TEXT,
                        published_at TEXT,
                        scraped_at   TEXT
                    )""");
                // ★ 院校静态数据表（原 data.js 迁入）
                s.execute("""
                    CREATE TABLE IF NOT EXISTS universities (
                        name     TEXT PRIMARY KEY,
                        rank     INTEGER DEFAULT 999,
                        province TEXT,
                        tier     TEXT NOT NULL,
                        cs_url   TEXT DEFAULT '',
                        adm_url  TEXT DEFAULT '',
                        note     TEXT DEFAULT '',
                        xhs      TEXT DEFAULT '[]'
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS departments (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        univ_name  TEXT NOT NULL,
                        dept_name  TEXT NOT NULL,
                        com_status TEXT DEFAULT 'unknown',
                        UNIQUE(univ_name, dept_name),
                        FOREIGN KEY (univ_name) REFERENCES universities(name)
                    )""");
                log.info("DB schema 初始化完成: {}", datasourceUrl);
                // ★ 防止同一个老师从不同URL被存两次
                try { s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_teacher_name_univ ON teachers(name, university)"); }
                catch (SQLException ignored) { /* 旧库已有数据时忽略 */ }
            } catch (SQLException e) {
                log.error("DB 初始化失败: {}", e.getMessage());
            }
        }

        /** ★ 首次启动自动导入 init_data.sql */
        public void initStaticData() {
            try (Connection conn = getConnection();
                 var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM universities")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    log.info("universities 表已有 {} 条，跳过导入", rs.getInt(1));
                    return;
                }
            } catch (SQLException e) { log.warn("检查 universities 表失败: {}", e.getMessage()); return; }

            // 尝试从项目根目录或 classpath 读取 init_data.sql
            String[] paths = {"init_data.sql", "src/init_data.sql", "data/init_data.sql"};
            java.io.InputStream is = null;
            for (String p : paths) {
                java.io.File f = new java.io.File(p);
                if (f.exists()) { try { is = new java.io.FileInputStream(f); log.info("从 {} 导入静态数据…", p); break; } catch (Exception ignored) {} }
            }
            if (is == null) { is = getClass().getClassLoader().getResourceAsStream("init_data.sql"); }
            if (is == null) { log.warn("未找到 init_data.sql，跳过静态数据导入"); return; }

            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                 Connection conn = getConnection(); Statement s = conn.createStatement()) {
                conn.setAutoCommit(false);
                StringBuilder sb = new StringBuilder();
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("--")) continue;
                    sb.append(line);
                    if (line.endsWith(";")) {
                        String sql = sb.toString();
                        // 跳过 CREATE TABLE（已在 initSchema 中建好）
                        if (!sql.toUpperCase().startsWith("CREATE ")) {
                            s.executeUpdate(sql);
                            count++;
                        }
                        sb.setLength(0);
                    }
                }
                conn.commit();
                log.info("✅ 导入完成: {} 条 SQL", count);
            } catch (Exception e) {
                log.error("导入 init_data.sql 失败: {}", e.getMessage());
            }
        }

        /** ★ 查询全部院校数据（供 /api/university-data 用） */
        public List<Map<String, Object>> getAllUniversities() {
            List<Map<String, Object>> result = new ArrayList<>();
            String sql = "SELECT name, rank, province, tier, cs_url, adm_url, note, xhs FROM universities ORDER BY rank, name";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                var rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",     rs.getString("name"));
                    m.put("rank",     rs.getInt("rank"));
                    m.put("province", rs.getString("province"));
                    m.put("tier",     rs.getString("tier"));
                    m.put("csUrl",    rs.getString("cs_url"));
                    m.put("admUrl",   rs.getString("adm_url"));
                    m.put("note",     rs.getString("note"));
                    // xhs 是 JSON 数组字符串，直接保留给前端解析
                    m.put("xhs",      rs.getString("xhs"));
                    // 查询该校的院系
                    m.put("depts",    getDepartments(conn, rs.getString("name")));
                    result.add(m);
                }
            } catch (SQLException e) { log.warn("getAllUniversities: {}", e.getMessage()); }
            return result;
        }

        private List<Map<String, String>> getDepartments(Connection conn, String univName) {
            List<Map<String, String>> depts = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT dept_name, com_status FROM departments WHERE univ_name=? ORDER BY id")) {
                ps.setString(1, univName);
                var rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, String> d = new LinkedHashMap<>();
                    d.put("n", rs.getString("dept_name"));
                    d.put("c", rs.getString("com_status"));
                    depts.add(d);
                }
            } catch (SQLException e) { log.warn("getDepartments({}): {}", univName, e.getMessage()); }
            return depts;
        }

        /** 插入或更新教师（按 profile_url 去重 + name+university 去重） */
        public boolean upsertTeacher(Teacher t) {
            // ★ 先检查同名同校是否已存在（不同URL但同一个人）
            if (existsByNameAndUniv(t.getName(), t.getUniversity())) {
                log.debug("跳过重复: {} @ {} (已有不同URL的记录)", t.getName(), t.getUniversity());
                return false;
            }
            String sql = """
                INSERT INTO teachers
                  (name,university,department,profile_url,title,research_areas,email,google_scholar,scraped_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(profile_url) DO UPDATE SET
                  title=excluded.title, research_areas=excluded.research_areas,
                  email=excluded.email, google_scholar=excluded.google_scholar,
                  scraped_at=excluded.scraped_at
                """;
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, t.getName());    ps.setString(2, t.getUniversity());
                ps.setString(3, t.getDepartment()); ps.setString(4, t.getProfileUrl());
                ps.setString(5, t.getTitle());   ps.setString(6, t.getResearchAreas());
                ps.setString(7, t.getEmail());   ps.setString(8, t.getGoogleScholar());
                ps.setString(9, t.getScrapedAt());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                log.warn("upsertTeacher failed [{}]: {}", t.getProfileUrl(), e.getMessage());
                return false;
            }
        }

        public boolean existsByUrl(String url) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM teachers WHERE profile_url=?")) {
                ps.setString(1, url);
                return ps.executeQuery().next();
            } catch (SQLException e) { return false; }
        }

        /** ★ 按姓名+学校判重（防止同一人不同URL重复入库） */
        public boolean existsByNameAndUniv(String name, String university) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM teachers WHERE name=? AND university=?")) {
                ps.setString(1, name);
                ps.setString(2, university);
                return ps.executeQuery().next();
            } catch (SQLException e) { return false; }
        }

        public boolean upsertSchoolInfo(SchoolInfo info) {
            if (info.getUrl() == null || info.getUrl().isBlank()) return false;
            String sql = """
                INSERT INTO school_info
                  (university,category,title,url,source,snippet,published_at,scraped_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(url) DO UPDATE SET
                  university=excluded.university, category=excluded.category,
                  title=excluded.title, source=excluded.source, snippet=excluded.snippet,
                  published_at=excluded.published_at, scraped_at=excluded.scraped_at
                """;
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, info.getUniversity());   ps.setString(2, info.getCategory());
                ps.setString(3, info.getTitle());        ps.setString(4, info.getUrl());
                ps.setString(5, info.getSource());       ps.setString(6, info.getSnippet());
                ps.setString(7, info.getPublishedAt());  ps.setString(8, info.getScrapedAt());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                log.warn("upsertSchoolInfo failed [{}]: {}", info.getUrl(), e.getMessage());
                return false;
            }
        }

        public List<SchoolInfo> searchSchoolInfo(String university, String category, String q) {
            StringBuilder sql = new StringBuilder("SELECT * FROM school_info WHERE 1=1");
            List<Object> p = new ArrayList<>();
            if (university != null && !university.isBlank()) { sql.append(" AND university LIKE ?"); p.add("%" + university + "%"); }
            if (category != null && !category.isBlank())     { sql.append(" AND category=?"); p.add(category); }
            if (q != null && !q.isBlank()) {
                sql.append(" AND (title LIKE ? OR snippet LIKE ? OR source LIKE ?)");
                p.add("%" + q + "%"); p.add("%" + q + "%"); p.add("%" + q + "%");
            }
            sql.append(" ORDER BY CASE category WHEN 'admission' THEN 0 WHEN 'experience' THEN 1 WHEN 'teacher' THEN 2 ELSE 3 END, scraped_at DESC LIMIT 1000");
            List<SchoolInfo> result = new ArrayList<>();
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < p.size(); i++) ps.setObject(i + 1, p.get(i));
                ResultSet rs = ps.executeQuery();
                while (rs.next()) result.add(mapSchoolInfoRow(rs));
            } catch (SQLException e) { log.error("searchSchoolInfo failed: {}", e.getMessage()); }
            return result;
        }

        public long countSchoolInfo() {
            try (Connection conn = getConnection();
                 ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM school_info")) {
                return rs.next() ? rs.getLong(1) : 0;
            } catch (SQLException e) { return 0; }
        }

        public List<Map<String, Object>> countSchoolInfoByCategory() {
            List<Map<String, Object>> out = new ArrayList<>();
            try (Connection conn = getConnection();
                 ResultSet rs = conn.createStatement().executeQuery(
                     "SELECT category, COUNT(*) cnt FROM school_info GROUP BY category ORDER BY cnt DESC")) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", rs.getString("category"));
                    row.put("count",    rs.getLong("cnt"));
                    out.add(row);
                }
            } catch (SQLException e) { log.error("countSchoolInfoByCategory: {}", e.getMessage()); }
            return out;
        }

        /** ResultSet 行 → SchoolInfo 对象（供 searchSchoolInfo 调用） */
        private SchoolInfo mapSchoolInfoRow(ResultSet rs) throws SQLException {
            SchoolInfo s = new SchoolInfo();
            s.setId(rs.getLong("id"));
            s.setUniversity(rs.getString("university"));
            s.setCategory(rs.getString("category"));
            s.setTitle(rs.getString("title"));
            s.setUrl(rs.getString("url"));
            s.setSource(rs.getString("source"));
            s.setSnippet(rs.getString("snippet"));
            s.setPublishedAt(rs.getString("published_at"));
            s.setScrapedAt(rs.getString("scraped_at"));
            return s;
        }

        /** 多条件查询，任意参数可为 null 表示不过滤 */
        public List<Teacher> search(String university, String department,
                                     String area, String title, String name) {
            StringBuilder sql = new StringBuilder("SELECT * FROM teachers WHERE 1=1");
            List<Object> p = new ArrayList<>();
            if (university != null && !university.isBlank()) { sql.append(" AND university LIKE ?"); p.add("%" + university + "%"); }
            if (department != null && !department.isBlank()) { sql.append(" AND department LIKE ?"); p.add("%" + department + "%"); }
            if (area      != null && !area.isBlank())        { sql.append(" AND research_areas LIKE ?"); p.add("%" + area + "%"); }
            if (title     != null && !title.isBlank())       { sql.append(" AND title LIKE ?"); p.add("%" + title + "%"); }
            if (name      != null && !name.isBlank())        { sql.append(" AND name LIKE ?"); p.add("%" + name + "%"); }
            sql.append(" ORDER BY university, name LIMIT 500");
            List<Teacher> result = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < p.size(); i++) ps.setObject(i + 1, p.get(i));
                ResultSet rs = ps.executeQuery();
                while (rs.next()) result.add(mapRow(rs));
            } catch (SQLException e) { log.error("search failed: {}", e.getMessage()); }
            return result;
        }

        public long countTeachers() {
            try (Connection conn = getConnection();
                 ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM teachers")) {
                return rs.next() ? rs.getLong(1) : 0;
            } catch (SQLException e) { return 0; }
        }

        public List<Map<String, Object>> countByUniversity() {
            List<Map<String, Object>> out = new ArrayList<>();
            try (Connection conn = getConnection();
                 ResultSet rs = conn.createStatement().executeQuery(
                     "SELECT university, COUNT(*) cnt FROM teachers GROUP BY university ORDER BY cnt DESC")) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("university", rs.getString("university"));
                    row.put("count",      rs.getLong("cnt"));
                    out.add(row);
                }
            } catch (SQLException e) { log.error("countByUniversity: {}", e.getMessage()); }
            return out;
        }

        public void saveVerifyResult(String univ, String dept, String url, String conf,
                                      boolean ok, int code, String redirect,
                                      String alt, String error) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT OR REPLACE INTO url_verify
                       (university,department,url,confidence,ok,status_code,redirect,alternative,error)
                     VALUES (?,?,?,?,?,?,?,?,?)""")) {
                ps.setString(1, univ);  ps.setString(2, dept);  ps.setString(3, url);
                ps.setString(4, conf);  ps.setInt(5, ok ? 1 : 0); ps.setInt(6, code);
                ps.setString(7, redirect); ps.setString(8, alt); ps.setString(9, error);
                ps.executeUpdate();
            } catch (SQLException e) { log.warn("saveVerify: {}", e.getMessage()); }
        }

        public List<Map<String, Object>> getVerifyResults(Boolean okOnly) {
            String sql = "SELECT * FROM url_verify"
                + (okOnly != null ? " WHERE ok=" + (okOnly ? "1" : "0") : "")
                + " ORDER BY university, department";
            List<Map<String, Object>> out = new ArrayList<>();
            try (Connection conn = getConnection();
                 ResultSet rs = conn.createStatement().executeQuery(sql)) {
                ResultSetMetaData m = rs.getMetaData();
                int cols = m.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) row.put(m.getColumnName(i), rs.getObject(i));
                    out.add(row);
                }
            } catch (SQLException e) { log.error("getVerify: {}", e.getMessage()); }
            return out;
        }

        public void logScrape(String url, String status, String msg) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO scrape_log(url,status,message) VALUES(?,?,?)")) {
                ps.setString(1, url); ps.setString(2, status); ps.setString(3, msg);
                ps.executeUpdate();
            } catch (SQLException e) { log.warn("logScrape: {}", e.getMessage()); }
        }

        private Teacher mapRow(ResultSet rs) throws SQLException {
            Teacher t = new Teacher();
            t.setId(rs.getLong("id"));
            t.setName(rs.getString("name"));
            t.setUniversity(rs.getString("university"));
            t.setDepartment(rs.getString("department"));
            t.setProfileUrl(rs.getString("profile_url"));
            t.setTitle(rs.getString("title"));
            t.setResearchAreas(rs.getString("research_areas"));
            t.setEmail(rs.getString("email"));
            t.setGoogleScholar(rs.getString("google_scholar"));
            t.setScrapedAt(rs.getString("scraped_at"));
            return t;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ScraperService — 已迁移到独立文件 ScraperService.java
    // Spring 通过 @Service 自动发现并注入，无需在此声明
    // ════════════════════════════════════════════════════════════════════════
    @RestController
    @RequestMapping("/api")
    public static class ApiController {

        private static final Logger log = LoggerFactory.getLogger(ApiController.class);

        @Autowired DatabaseService db;
        @Autowired ScraperService  scraper;

        /** 当前正在后台爬取的院校名称集合（用于 /scrape/progress 端点） */
        private static final Set<String> activeScrapingJobs = ConcurrentHashMap.newKeySet();

        // ── GET /api/university-data ── ★ 从数据库读取全部院校数据（前端主入口）
        @GetMapping("/university-data")
        public ResponseEntity<Map<String, Object>> getUniversityData() {
            List<Map<String, Object>> all = db.getAllUniversities();
            List<Map<String, Object>> unis = new ArrayList<>();
            Map<String, Object> meta = new LinkedHashMap<>();

            for (Map<String, Object> u : all) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> depts = (List<Map<String, String>>) u.get("depts");
                boolean hasDepts = depts != null && !depts.isEmpty();
                boolean hasComData = hasDepts; // 有院系数据 = 有 com 数据

                if (hasComData) {
                    // 985 / 有 com 数据的 211 → 放入 unis 数组
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("rank",     u.get("rank"));
                    entry.put("name",     u.get("name"));
                    entry.put("province", u.get("province"));
                    entry.put("tier",     u.get("tier"));
                    entry.put("csUrl",    u.getOrDefault("csUrl", ""));
                    entry.put("admUrl",   u.getOrDefault("admUrl", ""));
                    entry.put("depts",    depts);
                    entry.put("note",     u.getOrDefault("note", ""));
                    // xhs 是 JSON 字符串，解析成数组
                    String xhsStr = (String) u.getOrDefault("xhs", "[]");
                    try { entry.put("xhs", new com.fasterxml.jackson.databind.ObjectMapper().readValue(xhsStr, List.class)); }
                    catch (Exception e) { entry.put("xhs", List.of()); }
                    unis.add(entry);
                } else {
                    // 211 / 无 com 数据 → 放入 meta 对象
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("tier",     u.get("tier"));
                    m.put("province", u.get("province"));
                    m.put("note",     u.getOrDefault("note", ""));
                    meta.put((String) u.get("name"), m);
                }
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("unis", unis);
            resp.put("meta", meta);
            resp.put("total", all.size());
            return ResponseEntity.ok(resp);
        }

        // ── GET /api/universities ─────────────────────────────────────────────

        @GetMapping("/universities")
        public ResponseEntity<Map<String, Object>> listUniversities(
                @RequestParam(required = false) String tier,
                @RequestParam(required = false) String province,
                @RequestParam(required = false) String comFilter,
                @RequestParam(required = false) String q) {

            List<UniversityData.UniversityConfig> unis = new ArrayList<>(UniversityData.ALL);
            if (tier     != null && !tier.isBlank())     unis = unis.stream().filter(u -> u.tier.equals(tier)).collect(Collectors.toList());
            if (province != null && !province.isBlank()) unis = unis.stream().filter(u -> u.province.equals(province)).collect(Collectors.toList());
            if ("hasStrong".equals(comFilter)) unis = unis.stream().filter(UniversityData.UniversityConfig::hasStrongDept).collect(Collectors.toList());
            if ("allWeak".equals(comFilter))   unis = unis.stream().filter(UniversityData.UniversityConfig::isAllWeak).collect(Collectors.toList());
            if (q != null && !q.isBlank()) {
                String lq = q.toLowerCase();
                unis = unis.stream().filter(u -> u.name.contains(lq) || u.note.contains(lq)
                    || u.departments.stream().anyMatch(d -> d.name.contains(lq))).collect(Collectors.toList());
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("total",        unis.size());
            resp.put("tiers",        UniversityData.allTiers());
            resp.put("provinces",    UniversityData.allProvinces());
            resp.put("universities", unis.stream().map(this::toUnivMap).collect(Collectors.toList()));
            return ResponseEntity.ok(resp);
        }

        @GetMapping("/universities/{name}")
        public ResponseEntity<?> getUniversity(@PathVariable String name) {
            return UniversityData.findByName(name).map(u -> {
                Map<String, Object> m = toUnivMap(u);
                m.put("scrapedTeacherCount", db.search(u.name, null, null, null, null).size());
                return ResponseEntity.ok(m);
            }).orElse(ResponseEntity.notFound().build());
        }

        // ── GET /api/teachers ─────────────────────────────────────────────────

        @GetMapping("/teachers")
        public ResponseEntity<Map<String, Object>> searchTeachers(
                @RequestParam(required = false) String university,
                @RequestParam(required = false) String department,
                @RequestParam(required = false) String area,
                @RequestParam(required = false) String title,
                @RequestParam(required = false) String name,
                @RequestParam(defaultValue = "0")  int offset,
                @RequestParam(defaultValue = "50") int limit) {
            limit = Math.min(limit, 200);
            List<Teacher> all  = db.search(university, department, area, title, name);
            List<Teacher> page = all.stream().skip(offset).limit(limit).collect(Collectors.toList());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("total", all.size()); resp.put("offset", offset); resp.put("limit", limit);
            resp.put("teachers", page);
            if (all.isEmpty() && db.countTeachers() == 0)
                resp.put("note", "数据库暂无数据，请先调用 POST /api/scrape 触发爬虫");
            return ResponseEntity.ok(resp);
        }

        @GetMapping("/teachers/stats")
        public ResponseEntity<Map<String, Object>> teacherStats() {
            return ResponseEntity.ok(Map.of(
                "totalTeachers", db.countTeachers(),
                "byUniversity",  db.countByUniversity()));
        }

        // ── GET /api/search ───────────────────────────────────────────────────

        @GetMapping("/search")
        public ResponseEntity<Map<String, Object>> globalSearch(@RequestParam String q) {
            if (q == null || q.isBlank()) return ResponseEntity.badRequest().build();
            Set<String> seen = new LinkedHashSet<>();
            List<Teacher> teachers = new ArrayList<>();
            for (Teacher t : db.search(null, null, null, null, q)) if (seen.add(t.getProfileUrl())) teachers.add(t);
            for (Teacher t : db.search(null, null, q, null, null)) if (seen.add(t.getProfileUrl())) teachers.add(t);
            for (Teacher t : db.search(q, null, null, null, null)) if (seen.add(t.getProfileUrl())) teachers.add(t);
            List<Map<String, Object>> univs = UniversityData.ALL.stream()
                .filter(u -> u.name.contains(q) || u.note.contains(q) || u.departments.stream().anyMatch(d -> d.name.contains(q)))
                .map(this::toUnivMap).collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("query", q, "universities", univs,
                "teachers", teachers.stream().limit(30).collect(Collectors.toList()),
                "totalTeachers", teachers.size()));
        }

        // ── GET /api/xhs/{university} ─────────────────────────────────────────

        @GetMapping("/xhs/{university}")
        public ResponseEntity<?> xhsInfo(@PathVariable String university) {
            return UniversityData.findByName(university).map(u ->
                ResponseEntity.ok(Map.of(
                    "university", u.name,
                    "keywords",   u.xhsKeywords,
                    "bingUrls",   u.getBingXhsUrls(),
                    "note",       "小红书不支持直接爬取；请在APP内手动搜索关键词，或用Bing链接查看"))
            ).orElse(ResponseEntity.notFound().build());
        }

        // ── GET /api/verify ───────────────────────────────────────────────────

        @GetMapping("/verify")
        public ResponseEntity<Map<String, Object>> getVerify(
                @RequestParam(required = false) Boolean ok) {
            List<Map<String, Object>> res = db.getVerifyResults(ok);
            long okCnt = res.stream().filter(r -> Integer.valueOf(1).equals(r.get("ok"))).count();
            return ResponseEntity.ok(Map.of("total", res.size(), "ok", okCnt,
                "failed", res.size() - okCnt, "results", res));
        }

        // ── GET /api/scrape/status/{university} ───────────────────────────────
        // 检查某院校在数据库中是否有教师记录，前端用来决定是否触发按需爬取

        @GetMapping("/scrape/status/{university}")
        public ResponseEntity<Map<String, Object>> scrapeStatus(@PathVariable String university) {
            long count = db.search(university, null, null, null, null).size();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("university", university);
            resp.put("hasData",    count > 0);
            resp.put("count",      count);
            resp.put("active",     activeScrapingJobs.contains(university));
            return ResponseEntity.ok(resp);
        }

        // ── GET /api/scrape/progress/{university} ─────────────────────────────
        // 返回当前已爬取数量 + 是否仍在运行，供前端实时进度轮询使用

        @GetMapping("/scrape/progress/{university}")
        public ResponseEntity<Map<String, Object>> scrapeProgress(@PathVariable String university) {
            long count  = db.search(university, null, null, null, null).size();
            boolean active = activeScrapingJobs.contains(university);
            return ResponseEntity.ok(Map.of(
                "university", university,
                "count",      count,
                "active",     active,
                "done",       !active && count > 0));
        }

        // ── POST /api/scrape ──────────────────────────────────────────────────

        @PostMapping("/scrape")
        public ResponseEntity<Map<String, Object>> triggerScrape(
                @RequestBody(required = false) Map<String, Object> body) {
            @SuppressWarnings("unchecked")
            List<String> targets = body != null
                ? (List<String>) body.getOrDefault("universities", Collections.emptyList())
                : Collections.emptyList();
            // 可选：前端传 homepage 供未知院校爬取使用，格式 {"universities":["江南大学"],"homepage":"https://www.jiangnan.edu.cn"}
            String homepage = body != null ? (String) body.get("homepage") : null;
            final String hp = homepage;

            // 防重复：同一院校不重复触发
            if (!targets.isEmpty()) {
                targets = targets.stream()
                    .filter(t -> activeScrapingJobs.add(t))  // add 失败说明已在运行
                    .collect(Collectors.toList());
                if (targets.isEmpty()) {
                    return ResponseEntity.accepted().body(Map.of(
                        "status",  "already_running",
                        "note",    "该院校已在爬取中，请通过 GET /api/scrape/progress/{university} 查看进度"));
                }
            }
            final List<String> finalTargets = targets;

            new Thread(() -> {
                try {
                    Map<String, Object> r;
                    if (finalTargets.isEmpty()) {
                        r = scraper.scrapeAll();
                    } else if (hp != null && !hp.isBlank() && finalTargets.size() == 1) {
                        int n = scraper.scrapeUnknown(finalTargets.get(0), hp);
                        r = Map.of("scraped", n, "totalInDb", db.countTeachers());
                    } else {
                        r = scraper.scrapeByName(finalTargets);
                    }
                    log.info("爬取完成: {}", r);
                } catch (Exception e) {
                    log.error("爬取异常: {}", e.getMessage());
                } finally {
                    finalTargets.forEach(activeScrapingJobs::remove);
                }
            }).start();
            return ResponseEntity.accepted().body(Map.of(
                "status",  "started",
                "targets", finalTargets.isEmpty() ? "全部高校" : finalTargets,
                "note",    "后台异步执行，进度见 GET /api/scrape/progress/{university} 或 output/scraper.log"));
        }

        // ── POST /api/verify/run ──────────────────────────────────────────────

        @PostMapping("/verify/run")
        public ResponseEntity<Map<String, Object>> triggerVerify() {
            new Thread(() -> {
                try { scraper.verifyUrls(); } catch (Exception e) { log.error("验证异常: {}", e.getMessage()); }
            });
            return ResponseEntity.accepted().body(Map.of(
                "status", "started",
                "note",   "后台异步执行，完成后通过 GET /api/verify 查看结果"));
        }

        // ── 工具方法 ──────────────────────────────────────────────────────────

        private Map<String, Object> toUnivMap(UniversityData.UniversityConfig u) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", u.rank);           m.put("name", u.name);
            m.put("province", u.province);   m.put("tier", u.tier);
            m.put("homepage", u.homepage);   m.put("gradAdmission", u.gradAdmission);
            m.put("note", u.note);           m.put("xhsKeywords", u.xhsKeywords);
            m.put("hasStrongDept", u.hasStrongDept());
            m.put("departments", u.departments.stream().map(d -> {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("name", d.name);      dm.put("url", d.url);
                dm.put("urlConfidence", d.urlConfidence);
                dm.put("comStatus", d.comStatus.code);
                dm.put("comLabel",  d.comStatus.label);
                dm.put("dynamic",   d.dynamic);
                if (d.note != null && !d.note.isEmpty()) dm.put("note", d.note);
                return dm;
            }).collect(Collectors.toList()));
            return m;
        }
    }
}