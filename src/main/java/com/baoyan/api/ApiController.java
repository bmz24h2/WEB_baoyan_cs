package com.baoyan.api;

import com.baoyan.model.Teacher;
import com.baoyan.model.SchoolInfo;
import com.baoyan.db.DatabaseService;
import com.baoyan.scraper.ScraperService;
import com.baoyan.scraper.UniversityData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/** REST 接口层（从 BaoyanApp.ApiController 提取） */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    @Autowired DatabaseService db;
    @Autowired ScraperService  scraper;

    /** 当前正在后台爬取的院校名称集合（用于 /scrape/progress 端点） */
    // university → 爬取开始时间戳(ms)，用于前端显示「已爬 Xs」
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> activeScrapingJobs = new java.util.concurrent.ConcurrentHashMap<>();

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
            "totalTeachers",   db.countTeachers(),
            "totalSchoolInfo", db.countSchoolInfo(),
            "byCategory",      db.countSchoolInfoByCategory(),
            "byUniversity",    db.countByUniversity()));
    }

    // ── GET /api/school-info ──────────────────────────────────────────────
    @GetMapping("/school-info")
    public ResponseEntity<Map<String, Object>> getSchoolInfo(
            @RequestParam(required = false) String university,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        List<SchoolInfo> items = db.searchSchoolInfo(university, category, q);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total",    items.size());
        resp.put("items",    items);
        resp.put("category", category);
        return ResponseEntity.ok(resp);
    }

    // ── POST /api/scrape/info ─────────────────────────────────────────────
    @PostMapping("/scrape/info")
    public ResponseEntity<Map<String, Object>> triggerInfoScrape(
            @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> targets = body != null
            ? (List<String>) body.getOrDefault("universities", Collections.emptyList())
            : Collections.emptyList();
        if (targets.isEmpty()) return ResponseEntity.badRequest()
            .body(Map.of("error", "请传入 universities 数组"));
        final List<String> finalTargets = targets;
        new Thread(() -> {
            for (String name : finalTargets) {
                try { scraper.scrapeInfoOnly(name, null); }
                catch (Exception e) { log.error("scrapeInfo[{}]: {}", name, e.getMessage()); }
            }
        }).start();
        return ResponseEntity.accepted().body(Map.of(
            "status", "started",
            "targets", finalTargets,
            "note", "后台爬取实验室/通知/招生计划，进度见 scraper.log"));
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
        boolean active = activeScrapingJobs.containsKey(university);
        resp.put("active",   active);
        resp.put("elapsedSeconds", active
            ? (System.currentTimeMillis() - activeScrapingJobs.getOrDefault(university, System.currentTimeMillis())) / 1000
            : -1);
        return ResponseEntity.ok(resp);
    }

    // ── GET /api/scrape/progress/{university} ─────────────────────────────
    // 返回当前已爬取数量 + 是否仍在运行，供前端实时进度轮询使用

    @GetMapping("/scrape/progress/{university}")
    public ResponseEntity<Map<String, Object>> scrapeProgress(@PathVariable String university) {
        long count  = db.countTeachersByUniversity(university);   // ★ 精确 COUNT，不受 LIMIT 500 影响
        boolean active = activeScrapingJobs.containsKey(university);
        long elapsed = active
            ? (System.currentTimeMillis() - activeScrapingJobs.getOrDefault(university, System.currentTimeMillis())) / 1000
            : -1;
        return ResponseEntity.ok(Map.of(
            "university",    university,
            "count",         count,
            "active",        active,
            "elapsedSeconds",elapsed,
            "done",          !active));   // ★ 去掉 count>0 条件：爬完即 done，即使结果为 0
    }

    // ── GET /api/scrape/jobs ──────────────────────────────────────────────
    // 返回最近爬取的院校列表（从 teachers 表按 university 聚合）
    // 前端「最近爬取任务」面板使用

    @GetMapping("/scrape/jobs")
    public ResponseEntity<List<Map<String, Object>>> scrapeJobs(
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> jobs = new ArrayList<>();
        String sql = """
            SELECT university,
                   COUNT(*)        AS teacher_count,
                   MAX(scraped_at) AS last_scraped
            FROM teachers
            WHERE university IS NOT NULL AND university != ''
            GROUP BY university
            ORDER BY last_scraped DESC
            LIMIT ?""";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String uni = rs.getString("university");
                Map<String, Object> job = new LinkedHashMap<>();
                job.put("universityId",  uni);
                job.put("teacherCount",  rs.getInt("teacher_count"));
                job.put("startedAt",     rs.getString("last_scraped"));
                // 状态：仍在爬取中 → RUNNING，否则 → DONE
                job.put("status", activeScrapingJobs.containsKey(uni) ? "RUNNING" : "DONE");
                jobs.add(job);
            }
        } catch (SQLException e) {
            log.error("[爬取任务] 查询失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
        // 把当前正在爬取但还没有教师记录的院校也加进去（显示 RUNNING）
        for (String uni : activeScrapingJobs.keySet()) {
            boolean exists = jobs.stream().anyMatch(j -> uni.equals(j.get("universityId")));
            if (!exists) {
                Map<String, Object> job = new LinkedHashMap<>();
                job.put("universityId", uni);
                job.put("teacherCount", 0);
                job.put("startedAt",    null);
                job.put("status",       "RUNNING");
                jobs.add(0, job);  // 放最前面
            }
        }
        return ResponseEntity.ok(jobs);
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
                .filter(t -> activeScrapingJobs.putIfAbsent(t, System.currentTimeMillis()) == null)  // putIfAbsent 返回非null说明已在运行
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
                finalTargets.forEach(activeScrapingJobs::remove);  // 移除后 elapsedSeconds 返回 -1，前端知道已完成
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