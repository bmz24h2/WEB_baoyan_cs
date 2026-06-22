package com.baoyan.db;

import com.baoyan.model.Teacher;
import com.baoyan.model.SchoolInfo;
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

/** SQLite 数据库操作服务（从 BaoyanApp.DatabaseService 提取） */
@Service
public class DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public Connection getConnection() throws SQLException {
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
                    lab_name       TEXT,
                    recruiting     INTEGER DEFAULT 0,
                    scraped_at     TEXT,
                    cited_count    INTEGER DEFAULT 0,
                    works_count    INTEGER DEFAULT 0,
                    active_year    INTEGER DEFAULT 0,
                    counts_by_year TEXT
                )""");
            // ★ 迁移旧库：添加新列（已有则忽略）
            try { s.execute("ALTER TABLE teachers ADD COLUMN lab_name TEXT"); } catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE teachers ADD COLUMN recruiting INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE teachers ADD COLUMN cited_count INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE teachers ADD COLUMN works_count INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE teachers ADD COLUMN active_year INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE teachers ADD COLUMN counts_by_year TEXT"); } catch (SQLException ignored) {}
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
                    extra_json   TEXT,
                    published_at TEXT,
                    scraped_at   TEXT
                )""");
            try { s.execute("ALTER TABLE school_info ADD COLUMN extra_json TEXT"); } catch (SQLException ignored) {}
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

            // ★ 模拟面试题库
            s.execute("""
                CREATE TABLE IF NOT EXISTS interview_questions (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    category   TEXT NOT NULL,
                    question   TEXT NOT NULL,
                    hint       TEXT,
                    source     TEXT DEFAULT 'builtin',
                    created_at TEXT DEFAULT (datetime('now','localtime'))
                )""");

            // 内置题目（仅在题库为空时填充）
            var qCount = s.executeQuery("SELECT COUNT(*) FROM interview_questions");
            if (qCount.next() && qCount.getInt(1) == 0) {
                String[][] builtIn = {
                  {"自我介绍","请用 2 分钟做一个完整的自我介绍。","结构：院校专业→成绩→核心科研→目标→选校理由"},
                  {"自我介绍","你的最大优势和劣势分别是什么？","优势结合科研；劣势真实但附改进措施"},
                  {"自我介绍","为什么选择保研而不是出国或工作？","体现对学术的规划，有说服力"},
                  {"自我介绍","你为什么对当前研究方向感兴趣？","结合具体课程/项目经历，避免空话"},
                  {"科研经历","介绍你最重要的科研经历，说明你的具体贡献。","STAR法：情境→任务→行动→结果，量化"},
                  {"科研经历","科研过程中遇到什么技术难题，怎么解决的？","展示问题解决能力，体现独立思考"},
                  {"科研经历","你的项目/论文的核心创新点是什么？有何局限？","客观分析，展示学术成熟度"},
                  {"科研经历","如果继续这个课题，你下一步会怎么做？","体现对领域的深度思考"},
                  {"专业基础","解释什么是过拟合，有哪些防止方法？","正则化、Dropout、数据增强、早停等"},
                  {"专业基础","梯度消失和梯度爆炸的原因及解决方法？","ReLU、BatchNorm、残差连接、梯度裁剪"},
                  {"专业基础","解释 Attention 机制原理，Transformer 为何能取代 RNN？","QKV，并行计算，长距离依赖"},
                  {"专业基础","什么是 RLHF？在大语言模型训练中起什么作用？","SFT→奖励模型→PPO，对齐人类偏好"},
                  {"计算机视觉","ResNet 的核心贡献是什么？","梯度消失、退化问题，恒等映射学习"},
                  {"计算机视觉","目标检测中 two-stage 和 one-stage 的区别？","Faster RCNN vs YOLO，精度vs速度"},
                  {"计算机视觉","ViT 如何将 Transformer 应用到图像，有什么挑战？","patch embedding，需要大数据预训练"},
                  {"自然语言处理","BERT 和 GPT 的根本区别，各适合什么任务？","双向编码器 vs 单向生成器"},
                  {"自然语言处理","解释 RAG（检索增强生成）的工作原理。","向量检索+生成，减少幻觉"},
                  {"自然语言处理","大模型的 Prompt Engineering 有哪些技巧？","Zero/Few-shot, CoT, System prompt"},
                  {"系统/算法","解释 CAP 定理，如何在分布式系统中权衡？","一致性/可用性/分区容错，只能保证两个"},
                  {"系统/算法","快速排序的时间复杂度分析，最坏情况如何优化？","平均O(nlogn)，随机化pivot"},
                  {"系统/算法","深度学习推理加速有哪些方法？","量化、剪枝、知识蒸馏、TensorRT"},
                  {"英文问答","Please briefly introduce your research experience in English.","2-3分钟，语速适中，核心成果说清楚"},
                  {"英文问答","What are your plans for future research?","结合目标导师方向，体现前瞻性"},
                  {"英文问答","Why did you choose this university and research group?","具体到导师论文/方向，不说空话"},
                  {"综合素质","介绍一篇最近让你印象深刻的论文。","背景→问题→方法→实验→启发"},
                  {"综合素质","如何平衡科研压力和生活？","展示抗压能力，真实但正面"},
                  {"综合素质","导师的研究方向与你的期望不一致，怎么处理？","沟通、适应、主动性"},
                };
                try (var ps2 = conn.prepareStatement(
                    "INSERT INTO interview_questions(category,question,hint,source) VALUES(?,?,?,'builtin')")) {
                    for (String[] q : builtIn) {
                        ps2.setString(1, q[0]); ps2.setString(2, q[1]); ps2.setString(3, q[2]);
                        ps2.addBatch();
                    }
                    ps2.executeBatch();
                    log.info("内置面试题库初始化完成，共 {} 题", builtIn.length);
                }
            }
            // ★ 用户认证相关表
            s.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    username   TEXT UNIQUE NOT NULL,
                    password   TEXT NOT NULL,
                    salt       TEXT NOT NULL,
                    created_at TEXT DEFAULT (datetime('now','localtime'))
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS user_sessions (
                    token      TEXT PRIMARY KEY,
                    user_id    INTEGER NOT NULL,
                    expires_at TEXT NOT NULL,
                    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS user_profiles (
                    user_id      INTEGER PRIMARY KEY,
                    profile_json TEXT,
                    avatar_b64   TEXT,
                    updated_at   TEXT DEFAULT (datetime('now','localtime')),
                    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                )""");
            // 旧库迁移：补充 avatar_b64 列
            try { s.execute("ALTER TABLE user_profiles ADD COLUMN avatar_b64 TEXT"); }
            catch (SQLException ignored) { /* 列已存在，忽略 */ }
            s.execute("""
                CREATE TABLE IF NOT EXISTS interview_progress (
                    user_id     INTEGER NOT NULL,
                    question_id TEXT NOT NULL,
                    done_at     TEXT DEFAULT (datetime('now','localtime')),
                    PRIMARY KEY(user_id, question_id),
                    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                )""");
            // ★ 面试练习记录（答案 + AI点评，独立于AI顾问历史）
            s.execute("""
                CREATE TABLE IF NOT EXISTS interview_records (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id       INTEGER NOT NULL,
                    question_id   TEXT,
                    question_text TEXT,
                    category      TEXT NOT NULL DEFAULT '自由练习',
                    answer        TEXT,
                    feedback      TEXT,
                    duration_sec  INTEGER DEFAULT 0,
                    created_at    TEXT DEFAULT (datetime('now','localtime')),
                    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                )""");
            // 定期清理过期 session
            s.executeUpdate("""
                DELETE FROM user_sessions
                WHERE expires_at < datetime('now','localtime')""");
            log.info("DB schema 初始化完成: {}", datasourceUrl);
            // ★ 防止同一个老师从不同URL被存两次
            try { s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_teacher_name_univ ON teachers(name, university)"); }
            catch (SQLException ignored) { /* 旧库已有数据时忽略 */ }

            // ★ 自动清理垃圾数据：名字包含机构词/活动词的非人名条目
            int cleaned = s.executeUpdate("""
                DELETE FROM teachers WHERE
                  name LIKE '%校友%' OR name LIKE '%基金%' OR name LIKE '%协会%' OR
                  name LIKE '%联盟%' OR name LIKE '%委员%' OR name LIKE '%学会%' OR
                  name LIKE '%论坛%' OR name LIKE '%合作%' OR name LIKE '%招聘%' OR
                  name LIKE '%办公室%' OR name LIKE '%管理%部%' OR
                  research_areas LIKE '%月%日下午%' OR research_areas LIKE '%月%日上午%' OR
                  research_areas LIKE '%优质资源%' OR research_areas LIKE '%合作纽带%'
                """);
            if (cleaned > 0) log.info("🧹 自动清理垃圾教师记录 {} 条", cleaned);
        } catch (SQLException e) {
            log.error("DB 初始化失败: {}", e.getMessage());
        }
    }

    /** ★ 首次启动自动导入 init_data.sql */
    public void initStaticData() {
        // ★ 不再用"已有数据就跳过"：init_data.sql 全部使用 INSERT OR IGNORE
        //   每次启动都执行一遍，新增学校自动补入，已有数据不受影响
        int before = 0;
        try (Connection conn = getConnection();
             var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM universities")) {
            if (rs.next()) before = rs.getInt(1);
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
            // 统计新增了多少所学校
            int after = 0;
            try (var rs2 = conn.createStatement().executeQuery("SELECT COUNT(*) FROM universities")) {
                if (rs2.next()) after = rs2.getInt(1);
            }
            int added = after - before;
            if (added > 0) log.info("✅ universities 表新增 {} 所院校（现共 {} 所）", added, after);
            else           log.info("✅ universities 表已是最新（共 {} 所），无新增", after);
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
              (name,university,department,profile_url,title,research_areas,email,google_scholar,lab_name,recruiting,scraped_at,cited_count,works_count,active_year,counts_by_year)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(profile_url) DO UPDATE SET
              title=excluded.title, research_areas=excluded.research_areas,
              email=excluded.email, google_scholar=excluded.google_scholar,
              lab_name=COALESCE(excluded.lab_name, lab_name),
              recruiting=excluded.recruiting,
              scraped_at=excluded.scraped_at,
              cited_count=excluded.cited_count,
              works_count=excluded.works_count,
              active_year=excluded.active_year,
              counts_by_year=excluded.counts_by_year
            """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getName());       ps.setString(2, t.getUniversity());
            ps.setString(3, t.getDepartment()); ps.setString(4, t.getProfileUrl());
            ps.setString(5, t.getTitle());      ps.setString(6, t.getResearchAreas());
            ps.setString(7, t.getEmail());      ps.setString(8, t.getGoogleScholar());
            ps.setString(9, t.getLabName());
            ps.setInt(10, Boolean.TRUE.equals(t.getRecruiting()) ? 1 : 0);
            ps.setString(11, t.getScrapedAt());
            ps.setInt   (12, t.getCitedCount());
            ps.setInt   (13, t.getWorksCount());
            ps.setInt   (14, t.getActiveYear());
            ps.setString(15, t.getCountsByYear());
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
              (university,category,title,url,source,snippet,extra_json,published_at,scraped_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            ON CONFLICT(url) DO UPDATE SET
              university=excluded.university, category=excluded.category,
              title=excluded.title, source=excluded.source, snippet=excluded.snippet,
              extra_json=COALESCE(excluded.extra_json, extra_json),
              published_at=excluded.published_at, scraped_at=excluded.scraped_at
            """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, info.getUniversity());   ps.setString(2, info.getCategory());
            ps.setString(3, info.getTitle());        ps.setString(4, info.getUrl());
            ps.setString(5, info.getSource());       ps.setString(6, info.getSnippet());
            ps.setString(7, info.getExtraJson());    ps.setString(8, info.getPublishedAt());
            ps.setString(9, info.getScrapedAt());
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
        s.setExtraJson(rs.getString("extra_json"));
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

    /** 精确统计某院校的教师数（不受 search() 的 LIMIT 500 影响） */
    public long countTeachersByUniversity(String university) {
        String sql = "SELECT COUNT(*) FROM teachers WHERE university LIKE ?";
        try (Connection conn = getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + university + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) { return 0; }
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

    /** ★ NEW: 从实验室成员列表反向更新教师的 lab_name */
    public void updateTeacherLabName(String name, String university, String labName) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE teachers SET lab_name=? WHERE name=? AND university=? AND (lab_name IS NULL OR lab_name='')")) {
            ps.setString(1, labName); ps.setString(2, name); ps.setString(3, university);
            ps.executeUpdate();
        } catch (SQLException e) { log.debug("updateTeacherLabName: {}", e.getMessage()); }
    }

    public void logScrape(String url, String status, String msg) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO scrape_log(url,status,message) VALUES(?,?,?)")) {
            ps.setString(1, url); ps.setString(2, status); ps.setString(3, msg);
            ps.executeUpdate();
        } catch (SQLException e) { log.warn("logScrape: {}", e.getMessage()); }
    }

    /** 获取学校 note 字段（用于判断是否为交叉学科院校） */
    public String getUniversityNote(String univName) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT note FROM universities WHERE name = ? LIMIT 1")) {
            ps.setString(1, univName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("note") : null;
            }
        } catch (Exception e) {
            return null;
        }
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
        t.setLabName(rs.getString("lab_name"));
        t.setRecruiting(rs.getInt("recruiting") == 1);
        t.setScrapedAt(rs.getString("scraped_at"));
        return t;
    }
}