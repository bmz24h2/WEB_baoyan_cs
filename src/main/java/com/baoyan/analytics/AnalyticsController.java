package com.baoyan.analytics;

import com.baoyan.scraper.IndirectSearch;
import com.baoyan.db.DatabaseService;
import com.baoyan.model.Teacher;
import com.baoyan.model.SchoolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@EnableAsync
@RestController
@RequestMapping("/api")
public class AnalyticsController {

private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

@Autowired private AnalyticsService analyticsService;
@Autowired private AnalyticsEventBus eventBus;
@Autowired private MatchService matchService;
@Autowired private DatabaseService db;

// ── 初始化：建 FTS5 虚拟表 + 预计算 IDF ──────────────────────
@jakarta.annotation.PostConstruct
public void init() {
    initFTS5();
    matchService.buildIdf();
}

/**
 * 建立 SQLite FTS5 虚拟表 teachers_fts。
 * FTS5（Full-Text Search version 5）是 SQLite 内置的全文检索引擎（2015+），
 * content='teachers' 表示内容表，使用 porter 词干器。
 */
private void initFTS5() {
    try (Connection conn = db.getConnection(); Statement st = conn.createStatement()) {
        // 清理旧版错误列名的 FTS5 表（若存在则重建，保证列名与 teachers 表一致）
        try { st.execute("DROP TRIGGER IF EXISTS teachers_fts_insert"); } catch (Exception ignored) {}
        try { st.execute("DROP TRIGGER IF EXISTS teachers_fts_delete"); } catch (Exception ignored) {}
        try { st.execute("DROP TRIGGER IF EXISTS teachers_fts_update"); } catch (Exception ignored) {}
        try { st.execute("DROP TABLE IF EXISTS teachers_fts");           } catch (Exception ignored) {}
        // FTS5 虚拟表（content table 模式，不重复存储数据）
        st.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS teachers_fts
            USING fts5(
                name,
                research_areas,
                department,
                content='teachers',
                content_rowid='id',
                tokenize='unicode61 remove_diacritics 2'
            )
            """);

        // 检查是否需要填充（首次或重建后）
        long ftsCount = 0, teacherCount = 0;
        boolean corrupted = false;
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM teachers_fts")) {
            if (rs.next()) ftsCount = rs.getLong(1);
        } catch (SQLException corruptEx) {
            // FTS5 索引损坏（SQLITE_CORRUPT_VTAB），需要彻底删除重建
            log.warn("[FTS5] 检测到全文索引损坏，正在强制重建: {}", corruptEx.getMessage());
            corrupted = true;
        }

        if (corrupted) {
            // 损坏时：删掉底层 shadow 表彻底清理，再重建
            rebuildCorruptedFTS5(st);
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM teachers_fts")) {
                if (rs.next()) ftsCount = rs.getLong(1);
            } catch (Exception ignored) { ftsCount = 0; }
        }

        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM teachers")) {
            if (rs.next()) teacherCount = rs.getLong(1);
        }
        if (ftsCount < teacherCount) {
            // 增量填充 FTS5 索引
            st.execute("INSERT INTO teachers_fts(teachers_fts) VALUES('rebuild')");
            log.info("[FTS5] 全文索引重建完成，共 {} 条记录", teacherCount);
        }

        // 创建自动同步触发器（新增/更新/删除教师时同步 FTS5）
        st.execute("""
            CREATE TRIGGER IF NOT EXISTS teachers_fts_insert
            AFTER INSERT ON teachers BEGIN
                INSERT INTO teachers_fts(rowid, name, research_areas, department)
                VALUES (new.id, new.name, new.research_areas, new.department);
            END
            """);
        st.execute("""
            CREATE TRIGGER IF NOT EXISTS teachers_fts_delete
            AFTER DELETE ON teachers BEGIN
                INSERT INTO teachers_fts(teachers_fts, rowid, name, research_areas, department)
                VALUES ('delete', old.id, old.name, old.research_areas, old.department);
            END
            """);
        st.execute("""
            CREATE TRIGGER IF NOT EXISTS teachers_fts_update
            AFTER UPDATE ON teachers BEGIN
                INSERT INTO teachers_fts(teachers_fts, rowid, name, research_areas, department)
                VALUES ('delete', old.id, old.name, old.research_areas, old.department);
                INSERT INTO teachers_fts(rowid, name, research_areas, department)
                VALUES (new.id, new.name, new.research_areas, new.department);
            END
            """);
        log.info("[FTS5] 全文索引和触发器初始化完成");
    } catch (Exception e) {
        log.warn("[FTS5] FTS5 初始化失败（SQLite 版本可能不支持），将退化为 LIKE 搜索: {}", e.getMessage());
    }
}

/**
 * 强制清理损坏的 FTS5 表。
 * content='teachers' 外部内容表模式下，FTS5 会创建若干 shadow 表
 * （teachers_fts_data / teachers_fts_idx / teachers_fts_docsize / teachers_fts_config）。
 * 当虚拟表损坏且常规 DROP 失败时，直接删除这些底层 shadow 表再重建虚拟表。
 */
private void rebuildCorruptedFTS5(Statement st) {
    // 先尝试常规 DROP
    try { st.execute("DROP TABLE IF EXISTS teachers_fts"); } catch (Exception ignored) {}
    // 删除可能残留的 shadow 表
    for (String shadow : new String[]{
            "teachers_fts_data", "teachers_fts_idx",
            "teachers_fts_docsize", "teachers_fts_config",
            "teachers_fts_content"}) {
        try { st.execute("DROP TABLE IF EXISTS " + shadow); } catch (Exception ignored) {}
    }
    // 重新创建虚拟表
    try {
        st.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS teachers_fts
            USING fts5(
                name,
                research_areas,
                department,
                content='teachers',
                content_rowid='id',
                tokenize='unicode61 remove_diacritics 2'
            )
            """);
        log.info("[FTS5] 损坏的全文索引已清理并重建");
    } catch (Exception e) {
        log.warn("[FTS5] 重建失败: {}", e.getMessage());
    }
}

// ════════════════════════════════════════════════════════════════
// GET /api/analytics/events — SSE 实时事件流
// ════════════════════════════════════════════════════════════════

/**
 * Server-Sent Events 端点。
 * 浏览器连接后，爬虫触发的事件将实时推送（无需前端轮询）。
 * 前端代码：new EventSource('/api/analytics/events')
 */
@GetMapping(value = "/analytics/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamEvents() {
    SseEmitter emitter = eventBus.subscribe();
    log.info("[SSE] 新订阅，当前连接数: {}", eventBus.subscriberCount());
    // 立即发送一条心跳确认连接
    try {
        emitter.send(SseEmitter.event()
            .name("heartbeat")
            .data(Map.of("msg", "connected", "subscribers", eventBus.subscriberCount())));
    } catch (Exception ignored) {}
    return emitter;
}

// ════════════════════════════════════════════════════════════════
// GET /api/analytics/overview — KPI 摘要
// ════════════════════════════════════════════════════════════════

@GetMapping("/analytics/overview")
public ResponseEntity<Map<String, Object>> overview() {
    return ResponseEntity.ok(analyticsService.getOverview());
}

// ════════════════════════════════════════════════════════════════
// GET /api/analytics/distribution — 院校教师分布
// ════════════════════════════════════════════════════════════════

@GetMapping("/analytics/distribution")
public ResponseEntity<Map<String, Object>> distribution(
        @RequestParam(defaultValue = "20") int topN) {
    return ResponseEntity.ok(analyticsService.getDistribution(topN));
}

// ════════════════════════════════════════════════════════════════
// GET /api/analytics/tiers — 梯队教师数量
// ════════════════════════════════════════════════════════════════

@GetMapping("/analytics/tiers")
public ResponseEntity<Map<String, Object>> tiers() {
    return ResponseEntity.ok(analyticsService.getTiers());
}

// ════════════════════════════════════════════════════════════════
// GET /api/analytics/research-areas — 研究方向热词
// ════════════════════════════════════════════════════════════════

@GetMapping("/analytics/research-areas")
public ResponseEntity<List<Map<String, Object>>> researchAreas(
        @RequestParam(defaultValue = "30") int limit) {
    return ResponseEntity.ok(analyticsService.getResearchAreas(limit));
}

// ── GET /api/analytics/area-trend ─────────────────────────────────
// 从 OpenAlex 实时查询中国高校各CS方向近10年论文数趋势
// 结果缓存1小时，避免频繁调用 OpenAlex
@GetMapping("/analytics/area-trend")
public ResponseEntity<Map<String, Object>> areaTrend(
        @RequestParam(defaultValue = "2015") int minYear,
        @RequestParam(defaultValue = "2024") int maxYear) {

    // 先尝试本地数据库（重爬后才有）
    Map<String, Object> local = analyticsService.getAreaTrend(minYear, maxYear);
    java.util.List<?> localSeries = (java.util.List<?>) local.get("series");
    if (localSeries != null && !localSeries.isEmpty()) {
        return ResponseEntity.ok(local);
    }

    // 本地无数据 → 调 OpenAlex 聚合 API
    return ResponseEntity.ok(fetchAreaTrendFromOpenAlex(minYear, maxYear));
}

/** OpenAlex Concept ID 映射（CS核心方向，使用 level≥2 的精确 concept）*/
private static final java.util.List<String[]> OA_DIR_CONCEPTS = java.util.List.of(
    new String[]{"机器学习/深度学习", "C119857082"},   // Machine Learning (level=1)
    new String[]{"深度学习",          "C108583219"},   // Deep Learning (level=2)
    new String[]{"计算机视觉",        "C31972630"},    // Computer Vision (level=2)
    new String[]{"自然语言处理",      "C204321447"},   // NLP (level=2)
    new String[]{"网络安全",          "C9920699"},     // Cybersecurity (level=2)
    new String[]{"分布式计算",        "C161191863"},   // Distributed Computing (level=2)
    new String[]{"数据挖掘",          "C97137631"},    // Data Mining (level=2)
    new String[]{"算法",              "C1033101"},     // Algorithm (level=2, not top-level CS)
    new String[]{"人机交互",          "C138885662"},   // HCI (level=2)
    new String[]{"强化学习",          "C50522688"}     // Reinforcement Learning (level=2)
);

// 文件缓存路径（写到 output/ 目录，重启不丢）
private static final java.nio.file.Path TREND_CACHE_FILE =
    java.nio.file.Paths.get("output", "area_trend_cache.json");
// 内存一级缓存（避免频繁读文件）
private volatile Map<String, Object> trendCache = null;
private volatile long trendCacheTime = 0;
// ── DELETE /api/analytics/area-trend ──────────────────────────────
// 手动清除趋势缓存，下次访问时重新从 OpenAlex 获取
@DeleteMapping("/analytics/area-trend")
public ResponseEntity<Map<String, Object>> clearAreaTrendCache() {
    trendCache = null;
    try {
        java.nio.file.Files.deleteIfExists(TREND_CACHE_FILE);
        log.info("[OpenAlex趋势] 缓存已手动清除");
    } catch (Exception e) {
        log.warn("[OpenAlex趋势] 缓存文件删除失败: {}", e.getMessage());
    }
    return ResponseEntity.ok(Map.of("message", "趋势缓存已清除，下次访问将重新从 OpenAlex 获取"));
}

private Map<String, Object> fetchAreaTrendFromOpenAlex(int minYear, int maxYear) {
    // 1. 内存缓存命中（永久有效，直到手动刷新）
    if (trendCache != null) {
        return trendCache;
    }
    // 2. 文件缓存命中（重启后第一次，永久有效）
    try {
        if (java.nio.file.Files.exists(TREND_CACHE_FILE)) {
            String json = java.nio.file.Files.readString(TREND_CACHE_FILE);
            @SuppressWarnings("unchecked")
            Map<String, Object> cached = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, Map.class);
            trendCache = cached;
            log.info("[OpenAlex趋势] 从文件缓存加载");
            return cached;
        }
    } catch (Exception e) {
        log.warn("[OpenAlex趋势] 文件缓存读取失败: {}", e.getMessage());
    }

    java.util.List<Integer> years = new java.util.ArrayList<>();
    for (int y = minYear; y <= maxYear; y++) years.add(y);

    java.util.List<Map<String, Object>> series = new java.util.ArrayList<>();

    java.net.http.HttpClient hc = java.net.http.HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(8))
        .build();

    for (String[] dir : OA_DIR_CONCEPTS) {
        String dirName = dir[0];
        String cid     = dir[1];
        java.util.Map<Integer, Integer> yearCounts = new java.util.TreeMap<>();
        for (int y : years) yearCounts.put(y, 0);

        try {
            // 逐年查（OpenAlex group_by 只返回非零年份，且只有最大的一条，改为逐年精确查）
            for (int yr : years) {
                String url = "https://api.openalex.org/works?filter="
                    + "concepts.id:" + cid
                    + ",institutions.country_code:CN"
                    + ",publication_year:" + yr
                    + "&per-page=1&select=id&mailto=baoyan@edu.cn";
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", "BaoyanNav/1.0")
                    .timeout(java.time.Duration.ofSeconds(6))
                    .build();
                java.net.http.HttpResponse<String> resp = hc.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    String body = resp.body();
                    java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\"count\"\\s*:\\s*([0-9]+)").matcher(body);
                    if (m.find()) yearCounts.put(yr, Integer.parseInt(m.group(1)));
                }
                Thread.sleep(200); // OpenAlex polite pool
            }
            java.util.List<Integer> data = new java.util.ArrayList<>(yearCounts.values());
            int total = data.stream().mapToInt(Integer::intValue).sum();
            if (total > 0) {
                Map<String, Object> s = new java.util.LinkedHashMap<>();
                s.put("name", dirName);
                s.put("data", data);
                series.add(s);
            }
            log.info("[OpenAlex趋势] {} 查询完成", dirName);
        } catch (Exception e) {
            log.warn("[OpenAlex趋势] {} 查询失败: {}", dirName, e.getMessage());
        }
    }

    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("years",  years);
    result.put("series", series);
    result.put("note",   "数据来自 OpenAlex，统计中国高校CS领域论文数量");

    if (!series.isEmpty()) {
        trendCache = result;
        // 写文件缓存（永久有效，手动刷新才更新）
        try {
            java.nio.file.Files.createDirectories(TREND_CACHE_FILE.getParent());
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(result);
            java.nio.file.Files.writeString(TREND_CACHE_FILE, json);
            log.info("[OpenAlex趋势] 已写入文件缓存");
        } catch (Exception e) {
            log.warn("[OpenAlex趋势] 文件缓存写入失败: {}", e.getMessage());
        }
    }
    return result;
}

// ════════════════════════════════════════════════════════════════
// GET /api/match — TF-IDF 语义导师匹配
// ════════════════════════════════════════════════════════════════

/**
 * 语义导师匹配接口。
 *
 * 处理流程（Java 21 虚拟线程）：
 *   1. 接收 query 参数（可包含多个关键词，空格分隔）
 *   2. FTS5 快速召回候选教师
 *   3. 虚拟线程并行计算 TF-IDF 得分
 *   4. 按得分 + 梯队权重综合排序
 *   5. 返回 JSON 结果（含 score、matchedTerms 等）
 *
 * 示例：GET /api/match?q=自然语言处理 大模型&tier=顶尖C9&limit=20
 */
@GetMapping("/match")
public ResponseEntity<Map<String, Object>> match(
        @RequestParam String q,
        @RequestParam(defaultValue = "all") String tier,
        @RequestParam(defaultValue = "30") int limit) {

    long start = System.currentTimeMillis();
    List<MatchService.MatchResult> results = matchService.match(q, tier, limit);
    long elapsed = System.currentTimeMillis() - start;

    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("teachers",     results);
    resp.put("totalMatched", results.size());
    resp.put("queryTerms",   q.split("\\s+"));
    resp.put("elapsed",      elapsed + "ms");
    resp.put("engine",       "tfidf-fts5");

    return ResponseEntity.ok(resp);
}

// ════════════════════════════════════════════════════════════════
// POST /api/recommend — advisor-match.html 调用的导师匹配接口
// ════════════════════════════════════════════════════════════════

/**
 * 导师推荐主接口。
 *
 * 流程：
 *  1. FTS5 全文召回 + TF-IDF 评分
 *  2. 若 DB 中有结果 → 直接返回
 *  3. 若 DB 中无结果 → 查询所选梯队里哪些院校尚未爬取
 *     返回 { results:[], noData:true, scrapeTargets:[...] }
 *     前端见到 scrapeTargets 后调用 POST /api/scrape 触发爬取
 *     绝不返回任何硬编码模拟数据
 *
 * 请求体：
 *   { dirs:string[], tiers:string[], gpa:number,
 *     rankPct:number, expKeywords:string, limit?:number }
 * 响应体（有结果）：
 *   { results:[{name,title,university,dept,dirs,score,reason,profileUrl}],
 *     total:N, elapsed:"Xms", engine:"fts5+tfidf" }
 * 响应体（无结果需爬取）：
 *   { results:[], noData:true, scrapeTargets:[...], hint:"..." }
 */
@PostMapping("/recommend")
public ResponseEntity<Map<String, Object>> recommend(
        @RequestBody Map<String, Object> body) {

    // ── 1. 解析请求 ────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    List<String> dirs  = (List<String>) body.getOrDefault("dirs",  List.of());
    @SuppressWarnings("unchecked")
    List<String> tiers = (List<String>) body.getOrDefault("tiers", List.of());
    String background  = (String) body.getOrDefault("background",
                         body.getOrDefault("expKeywords", ""));
    String targetDirs  = (String) body.getOrDefault("targetDirs", "");
    int limit  = ((Number) body.getOrDefault("limit", 10)).intValue();

    // ── 1b. 解析 session 事件（来自前端 sessionStorage）──────────
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> rawSession =
        (List<Map<String,Object>>) body.getOrDefault("sessionEvents", List.of());
    List<MatchService.SessionEvent> sessionEvents = rawSession.stream()
        .map(e -> new MatchService.SessionEvent(
            (String) e.getOrDefault("teacherName",   ""),
            (String) e.getOrDefault("university",    ""),
            (String) e.getOrDefault("researchAreas", ""),
            ((Number) e.getOrDefault("timestamp", System.currentTimeMillis())).longValue()))
        .collect(Collectors.toList());

    // ── 2. 三维语义扩展：意向方向 + 意愿去向 + 科研经历 ────────
    String query = matchService.buildExpandedQuery(dirs, background, targetDirs);
    if (query.isBlank()) {
        return ResponseEntity.ok(Map.of(
            "results", List.of(),
            "message", "请至少选择意向方向、填写意愿去向或描述科研经历"));
    }

    // ── 3. FTS5 + TF-IDF 匹配 ─────────────────────────────────
    String tierFilter = tiers.isEmpty() ? "all" : String.join(",", tiers);
    long t0 = System.currentTimeMillis();
    List<MatchService.MatchResult> matched = matchService.match(query, tierFilter, limit);

    // ── 3b. Session-based Re-ranking（短期兴趣融合）─────────────
    // 若前端传入了本次会话的点击历史，则用 cosine 相似度加权重排
    if (!sessionEvents.isEmpty()) {
        matched = matchService.sessionRerank(matched, sessionEvents);
    }

    long elapsed = System.currentTimeMillis() - t0;

    // ── 4. 结果格式转换 → 前端 renderResults() 期望格式 ─────────
    List<Map<String, Object>> results = matched.stream().map(r -> {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name",       r.name());
        item.put("title",      r.title()      != null ? r.title()      : "");
        item.put("university", r.university() != null ? r.university() : "");
        item.put("dept",       r.department() != null ? r.department() : "");

        // research_areas → 研究方向标签列表
        // 过滤规则：
        //   ① 每段不超过 15 字（防止 bio 文本混入，如"2023年毕业于…"）
        //   ② 不含句号/叹号等终止符（整句话不是方向词）
        //   ③ 最多显示 4 个
        List<String> dirList = r.researchAreas() != null
            ? Arrays.stream(r.researchAreas().split("[,，、；;\\n\\r]+"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank()
                              && s.length() <= 15
                              && !s.contains("。") && !s.contains("！")
                              && !s.contains("？") && !s.contains("年"))
                    .limit(4)
                    .collect(Collectors.toList())
            : List.of();
        item.put("dirs", dirList);
        item.put("score",      r.score());
        item.put("profileUrl", r.profileUrl() != null ? r.profileUrl() : "");

        // matchedTerms 去除 n-gram 碎片后作为推荐理由
        // 例："软件工程"的 n-gram 会产生"件工程""工程"等子串
        // 规则：若词 A 是词 B 的真子串（且 B 更长），则丢弃 A
        List<String> cleanTerms = deduplicateSubstrings(r.matchedTerms());
        String reason = cleanTerms.isEmpty()
            ? "综合匹配"
            : "匹配方向：" + cleanTerms.stream().limit(3).collect(Collectors.joining("、"));
        item.put("reason", reason);
        return item;
    }).collect(Collectors.toList());

    // ── 5. 构建响应 ────────────────────────────────────────────
    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("results", results);
    resp.put("total",   results.size());
    resp.put("elapsed", elapsed + "ms");
    resp.put("engine",  "fts5+tfidf");

    // ── 6. 无论有无结果，都查未爬院校 ─────────────────────────
    //   有结果时：附带 partialScrapeTargets 告知还有哪些院校没爬
    //   无结果时：完整的爬取提示
    List<Map<String, Object>> unscraped = findUnscrapedUniversitiesClassified(tiers, 8);

    if (results.isEmpty()) {
        List<Map<String, Object>> pureTargets  = unscraped.stream()
            .filter(c -> "pure".equals(c.get("type"))).collect(Collectors.toList());
        List<Map<String, Object>> crossTargets = unscraped.stream()
            .filter(c -> "cross".equals(c.get("type"))).collect(Collectors.toList());
        resp.put("noData",       true);
        resp.put("pureTargets",  pureTargets);
        resp.put("crossTargets", crossTargets);
        resp.put("scrapeTargets", unscraped.stream()
            .map(c -> c.get("name")).collect(Collectors.toList()));
        resp.put("hint", unscraped.isEmpty()
            ? "所选梯队院校已全部爬取，但当前数据库中无匹配教师。请尝试更换方向、放宽梯队或减少关键词。"
            : "所选梯队中以下院校尚未爬取：" + unscraped.stream().limit(6)
                .map(c -> (String) c.get("name")).collect(Collectors.joining("、")));
    } else if (!unscraped.isEmpty()) {
        // 有结果但有未爬院校 → 附带补爬信息，让前端显示"还有X所未爬取"
        resp.put("partialScrapeTargets", unscraped.stream()
            .map(c -> c.get("name")).collect(Collectors.toList()));
        resp.put("partialHint",
            "当前结果仅来自已爬取院校，以下 " + unscraped.size() + " 所院校尚未爬取，爬完后匹配范围更广：" +
            unscraped.stream().limit(5).map(c -> (String) c.get("name")).collect(Collectors.joining("、")) +
            (unscraped.size() > 5 ? " 等" : ""));
    }
    return ResponseEntity.ok(resp);
}

/**
 * 去除 n-gram 碎片：若词 A 是词 B 的真子串（B 更长），则丢弃 A。
 * 例：["软件工程", "件工程", "工程"] → ["软件工程"]
 * 同时过滤掉长度 < 3 的无意义短片段。
 */
private List<String> deduplicateSubstrings(List<String> terms) {
    if (terms == null || terms.isEmpty()) return List.of();
    // 先去重，再按长度降序
    List<String> sorted = terms.stream()
        .distinct()
        .filter(t -> t != null && t.length() >= 3)
        .sorted(Comparator.comparingInt(String::length).reversed())
        .collect(Collectors.toList());
    List<String> kept = new ArrayList<>();
    for (String t : sorted) {
        // 若当前词是已保留的某个更长词的子串，则丢弃
        boolean isSubstr = kept.stream().anyMatch(longer -> longer.contains(t));
        if (!isSubstr) kept.add(t);
    }
    return kept;
}

/**
 * 找出所选梯队里尚未爬取的院校，并按学科类型分类返回。
 *
 * 每项 Map 包含：
 *   name  — 院校名
 *   type  — "pure"（纯CS/工科）或 "cross"（交叉学科）
 *   note  — 原始 note 字段
 *   domains — 交叉学科时的 CS 搜索关键词列表（pure 时为空）
 *   hint  — 交叉学科说明文字
 */
private List<Map<String, Object>> findUnscrapedUniversitiesClassified(
        List<String> tiers, int maxCount) {

    if (tiers.isEmpty()) return List.of(); // "不限"时不主动推荐爬取

    // 已爬取院校
    Set<String> scraped = new HashSet<>();
    try (Connection conn = db.getConnection();
         Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery(
             "SELECT DISTINCT university FROM teachers WHERE university IS NOT NULL AND university != ''")) {
        while (rs.next()) scraped.add(rs.getString(1).trim());
    } catch (Exception e) {
        log.warn("[Recommend] 查询已爬取院校失败: {}", e.getMessage());
    }

    // 按梯队查候选院校（同时取出 note 用于分类）
    String ph = tiers.stream().map(x -> "?").collect(Collectors.joining(","));
    List<String[]> rows = new ArrayList<>(); // [name, note]
    try (Connection conn = db.getConnection();
         PreparedStatement ps = conn.prepareStatement(
             "SELECT name, note FROM universities WHERE tier IN (" + ph + ") ORDER BY rank LIMIT 50")) {
        for (int i = 0; i < tiers.size(); i++) ps.setString(i + 1, tiers.get(i).trim());
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new String[]{rs.getString(1), rs.getString(2)});
        }
    } catch (Exception e) {
        log.warn("[Recommend] 查询候选院校失败: {}", e.getMessage());
    }

    List<Map<String, Object>> result = new ArrayList<>();
    for (String[] row : rows) {
        if (result.size() >= maxCount) break;
        String name = row[0], note = row[1];
        if (scraped.contains(name)) continue;

        // 用 ScraperCore.getCrossDomains 判断学科类型
        @SuppressWarnings("unchecked")
        List<String> domains = (List<String>)
            com.baoyan.scraper.IndirectSearch.getCrossDomains(note);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("note", note != null ? note : "");
        if (domains.isEmpty()) {
            item.put("type",    "pure");
            item.put("domains", List.of());
        } else {
            item.put("type",    "cross");
            item.put("domains", domains);
            item.put("hint",    "交叉学科，重点搜索：" +
                domains.stream().limit(3).collect(Collectors.joining("、")));
        }
        result.add(item);
    }
    return result;
}

// ════════════════════════════════════════════════════════════════
// 定时任务：每 5 分钟心跳推送 + 缓存清除
// ════════════════════════════════════════════════════════════════

@Scheduled(fixedRate = 300_000) // 5 分钟
public void heartbeat() {
    if (eventBus.subscriberCount() > 0) {
        eventBus.publish(new AnalyticsEventBus.ScrapeEvent(
            "heartbeat", null, 0, null, null, "scheduler", 0));
    }
    // 同时清除分析缓存，保证数据新鲜
    analyticsService.evictAllCaches();
}
}