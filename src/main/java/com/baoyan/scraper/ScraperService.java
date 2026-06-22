package com.baoyan.scraper;

import com.baoyan.model.Teacher;
import com.baoyan.db.DatabaseService;
import com.baoyan.engine.FetchEngine;
import com.baoyan.model.SchoolInfo;
import com.baoyan.scraper.IndirectSearch;
import org.jsoup.nodes.Document;
import com.baoyan.model.Teacher;
import com.baoyan.db.DatabaseService;
import com.baoyan.engine.FetchEngine;
import com.baoyan.model.SchoolInfo;
import com.baoyan.scraper.IndirectSearch;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ScraperService — 高层调度层（调用函数文件）
 *
 * 职责：
 *   - 提供对外 API（scrapeAll / scrapeByName / scrapeUnknown / scrapeInfoOnly / verifyUrls）
 *   - 编排爬取流程：并行任务 / 教师 + 信息双线程 / 失败时降级到间接搜索
 *   - 具体算法委托给 ScraperCore（URL发现 / Strategy I / Strategy N）
 *
 * 依赖关系（调用链）：
 *   ScraperService  →  ScraperCore  →  FetchEngine
 *                  ↘               ↘  DatabaseService
 */
@Service
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

    @Autowired FetchEngine               engine;
    @Autowired DatabaseService db;
    @Autowired IndirectSearch core;

    @Value("${scraper.concurrency:4}")  private int concurrency;
    @Value("${verifier.concurrency:8}") private int verConcurrency;
    @Value("${verifier.timeout:12000}") private int verTimeout;

    // ════════════════════════════════════════════════════════════════════════
    // 公开 API
    // ════════════════════════════════════════════════════════════════════════

    public Map<String,Object> scrapeAll() { return scrapePreset(UniversityData.ALL); }

    public Map<String,Object> scrapeByName(List<String> names) {
        List<UniversityData.UniversityConfig> matched = UniversityData.ALL.stream()
            .filter(u -> names.stream().anyMatch(n -> u.name.contains(n) || n.contains(u.name)))
            .collect(Collectors.toList());
        int total = 0;
        if (!matched.isEmpty()) {
            log.info("预设命中 {} 所: {}", matched.size(),
                matched.stream().map(u -> u.name).collect(Collectors.joining(",")));
            total += (int) scrapePreset(matched).getOrDefault("scraped", 0);
        }

        // ★ 收集所有需要 scrapeUnknown 的学校
        List<String> unknowns = names.stream()
            .filter(n -> matched.stream().noneMatch(u -> u.name.contains(n) || n.contains(u.name)))
            .collect(Collectors.toList());

        if (!unknowns.isEmpty()) {
            // ★ 并发：最多同时跑 concurrency 所（和爬虫线程池保持一致），
            //   各自独立搜索，不再串行等待
            ExecutorService exec = Executors.newFixedThreadPool(
                Math.min(unknowns.size(), concurrency));
            List<java.util.concurrent.Future<Integer>> futures = unknowns.stream()
                .map(n -> exec.submit(() -> {
                    log.info("「{}」不在预设，启动多阶段探测…", n);
                    int s = scrapeUnknown(n, null);
                    if (s == 0) log.warn("「{}」无结果，请检查日志", n);
                    return s;
                }))
                .collect(Collectors.toList());
            exec.shutdown();
            for (java.util.concurrent.Future<Integer> f : futures) {
                try { total += f.get(); } catch (Exception e) {
                    log.warn("scrapeUnknown 异常: {}", e.getMessage());
                }
            }
        }

        return Map.of("scraped", total, "totalInDb", db.countTeachers());
    }


    public int scrapeUnknown(String univName, String homepage) {
        List<String> knownUrls = ScraperCore.KNOWN_CS.entrySet().stream()
            .filter(e -> univName.contains(e.getKey()) || e.getKey().contains(univName))
            .flatMap(e -> e.getValue().stream()).collect(Collectors.toList());

        // ★ 核心判断：
        //   - 有预设 URL（KNOWN_CS 命中）或调用方明确传入了 homepage → 值得直连
        //   - 既无预设也无 homepage → edu.cn 十有八九反爬，直接走间接搜索，不碰 edu.cn
        boolean hasDirectUrl = !knownUrls.isEmpty() || (homepage != null && !homepage.isBlank());

        if (!hasDirectUrl) {
            log.info("【{}】无预设 URL，跳过 edu.cn 直连，直接启动间接搜索…", univName);
            // ★ 招生信息也走间接搜索（研招网/搜狗微信/百度文库），不调 scrapeInfoOnly
            //   scrapeInfoOnly 内部会调 discoverHomepage → 仍然访问 edu.cn，与本策略矛盾
            java.util.concurrent.CompletableFuture<Integer> infoF =
                java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> core.scrapeInfoFromIndirectSources(univName, null));

            String univNote    = db.getUniversityNote(univName);
            List<String> cross = IndirectSearch.getCrossDomains(univNote);
            int teachers = cross.isEmpty()
                ? core.scrapeFromIndirectSources(univName, null)
                : core.scrapeFromCrossDisciplinary(univName, cross);
            if (teachers == 0 && !cross.isEmpty())
                teachers = core.scrapeFromIndirectSources(univName, null);

            // ★ 最多等 3 分钟；超时后放弃等待（info 继续在后台跑，不阻塞下一所学校）
            int info = 0;
            try {
                info = infoF.get(3, java.util.concurrent.TimeUnit.MINUTES);
            } catch (java.util.concurrent.TimeoutException e) {
                log.warn("【{}】scrapeInfoOnly 超时（3min），跳过等待，继续下一所", univName);
                infoF.cancel(false);  // 不强制中断，让它继续后台写库
            } catch (Exception e) {
                log.warn("【{}】scrapeInfoOnly 异常: {}", univName, e.getMessage());
            }
            log.info("🏁 【{}】完成: {} 位教师, {} 条信息（纯间接模式）", univName, teachers, info);
            return teachers;
        }

        // ── 以下仅对有预设 URL 或调用方传入 homepage 的院校执行 ──────────────
        String base = (homepage != null && !homepage.isBlank())
            ? homepage.replaceAll("/+$", "") : core.discoverHomepage(univName);
        if (base == null) { log.warn("无法确定「{}」主页", univName); return 0; }
        log.info("【{}】主页: {}", univName, base);

        final String finalBase     = base;
        final List<String> finalKU = knownUrls;

        java.util.concurrent.CompletableFuture<Integer> teacherFuture =
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                int n = 0;
                for (String ku : finalKU)
                    for (String fp : core.discoverFacultyPages(ku))
                        n += engine.scrapeTeachersFromPage(fp, univName, "");
                if (n == 0) {
                    List<ScraperCore.CollegeEntry> colleges = core.discoverCsColleges(finalBase, univName);
                    for (ScraperCore.CollegeEntry col : colleges)
                        for (String fp : core.discoverFacultyPages(col.url))
                            n += engine.scrapeTeachersFromPage(fp, univName, col.name);
                }
                // 快速失败：连续 3 次 null 即放弃剩余路径
                if (n == 0) {
                    int consecutive = 0;
                    for (String path : ScraperCore.FACULTY_PATHS) {
                        int got = engine.scrapeTeachersFromPage(finalBase + path, univName, "");
                        if (got == 0) {
                            if (++consecutive >= 3) {
                                log.warn("【{}】连续 {} 次 edu.cn 失败，提前放弃直连", univName, consecutive);
                                break;
                            }
                        } else { n += got; consecutive = 0; if (n >= 5) break; }
                    }
                }
                log.info("【{}】直连教师: {} 位", univName, n);
                return n;
            });

        java.util.concurrent.CompletableFuture<Integer> infoFuture =
            java.util.concurrent.CompletableFuture.supplyAsync(() -> scrapeInfoOnly(univName, finalBase));

        int teachers = teacherFuture.join();
        int info     = infoFuture.join();

        // 直连失败时降级间接搜索
        if (teachers == 0) {
            String univNote    = db.getUniversityNote(univName);
            List<String> cross = IndirectSearch.getCrossDomains(univNote);
            if (!cross.isEmpty()) {
                log.warn("【{}】交叉学科，定向交叉策略…", univName);
                teachers = core.scrapeFromCrossDisciplinary(univName, cross);
                if (teachers == 0) teachers = core.scrapeFromIndirectSources(univName, base);
            } else {
                log.warn("【{}】直连全失败，切换间接搜索…", univName);
                teachers = core.scrapeFromIndirectSources(univName, base);
            }
        }

        log.info("🏁 【{}】全量完成: {} 位教师, {} 条信息", univName, teachers, info);
        return teachers;
    }

    /**
     * ★ NEW: 仅爬取非教师数据（实验室 + 通知 + 招生计划）。
     * 可单独由 POST /api/scrape/info 触发，也可作为 scrapeUnknown 的子任务并行调用。
     *
     * @return 写入 school_info 的条数
     */
    public int scrapeInfoOnly(String univName, String base) {
        if (base == null) base = core.discoverHomepage(univName);
        if (base == null) { log.warn("【{}】无法确定主页，跳过信息爬取", univName); return 0; }
        int total = 0;
        total += scrapeLabPages(univName, base);
        total += scrapeNoticePages(univName, base);
        total += scrapePlanPages(univName, base);

        // ★ Strategy N: edu.cn 直连无结果 → 间接多源搜索
        if (total == 0) {
            log.warn("【{}】直连 edu.cn 通知/计划全部失败，切换 Strategy N…", univName);
            total += core.scrapeInfoFromIndirectSources(univName, base);
        }

        log.info("【{}】信息爬取完成: {} 条 (lab/notice/plan)", univName, total);
        return total;
    }

    // ── 实验室/课题组爬取 ────────────────────────────────────────────────────

    private int scrapeLabPages(String univName, String base) {
        int count = 0;
        List<String> labUrls = core.discoverLabPages(base);
        log.info("【{}】发现 {} 个实验室/课题组页面", univName, labUrls.size());
        for (String url : labUrls) {
            Document doc = engine.fetchRobust(url);
            if (doc == null || core.isRedirectedToHomepage(doc, url)) continue;
            String type = engine.detectPageType(doc, url);
            if (!"lab".equals(type) && !"unknown".equals(type)) continue;
            java.util.Map<String,Object> lab = engine.parseLabPage(doc, url);
            String labName = (String) lab.getOrDefault("name", "");
            if (labName.isBlank()) continue;

            SchoolInfo info = new SchoolInfo(
                univName, "lab", labName, url, base);
            info.setSnippet((String) lab.getOrDefault("snippet",""));
            // extra JSON: pi + researchAreas + members + recruiting
            info.setPublishedAt("");
            try {
                String extra = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(java.util.Map.of(
                        "pi",          lab.getOrDefault("pi",""),
                        "research",    lab.getOrDefault("researchAreas",""),
                        "members",     lab.getOrDefault("members", java.util.List.of()),
                        "recruiting",  lab.getOrDefault("recruiting", false)));
                info.setExtraJson(extra);
            } catch (Exception ignored) {}
            if (db.upsertSchoolInfo(info)) {
                count++;
                log.info("  ✅ lab: {}", labName);
            }

            // ★ 若实验室页面列出了成员，顺手把成员 labName 更新到 teachers 表
            @SuppressWarnings("unchecked")
            java.util.List<String> members = (java.util.List<String>) lab.getOrDefault("members", java.util.List.of());
            for (String member : members)
                db.updateTeacherLabName(member, univName, labName);
        }
        return count;
    }


    private int scrapeNoticePages(String univName, String base) {
        int count = 0;
        List<String> noticeUrls = core.discoverNoticeListPages(base);
        log.info("【{}】发现 {} 个通知列表页", univName, noticeUrls.size());
        for (String listUrl : noticeUrls) {
            Document listDoc = engine.fetchRobust(listUrl);
            if (listDoc == null || core.isRedirectedToHomepage(listDoc, listUrl)) continue;
            // 从列表页找有保研/预推免关键词的具体公告链接
            for (Element a : listDoc.select("a[href]")) {
                String linkText = a.text();
                if (!FetchEngine.NOTICE_KW.matcher(linkText).find()
                 && !FetchEngine.PLAN_KW.matcher(linkText).find()) continue;
                String href = a.absUrl("href");
                if (href.isEmpty() || engine.skipHref(href)) continue;
                Document noticeDoc = engine.fetchRobust(href);
                if (noticeDoc == null) continue;
                java.util.Map<String,Object> notice = engine.parseNoticePage(noticeDoc, href);
                String noticeTitle = (String) notice.getOrDefault("title", linkText);
                String category    = (String) notice.getOrDefault("category", "notice");
                SchoolInfo info = new SchoolInfo(
                    univName, category, noticeTitle, href, listUrl);
                info.setSnippet((String) notice.getOrDefault("snippet",""));
                info.setPublishedAt((String) notice.getOrDefault("publishedAt",""));
                try {
                    java.util.Map<String,Object> extra = new java.util.LinkedHashMap<>();
                    if (notice.containsKey("deadline"))     extra.put("deadline",  notice.get("deadline"));
                    if (notice.containsKey("quota"))        extra.put("quota",     notice.get("quota"));
                    if (notice.containsKey("gpaReq"))       extra.put("gpaReq",    notice.get("gpaReq"));
                    if (notice.containsKey("rankReq"))      extra.put("rankReq",   notice.get("rankReq"));
                    if (notice.containsKey("contactEmail")) extra.put("email",     notice.get("contactEmail"));
                    info.setExtraJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(extra));
                } catch (Exception ignored) {}
                if (db.upsertSchoolInfo(info)) {
                    count++;
                    log.info("  ✅ {}: {}", category, noticeTitle.substring(0, Math.min(40, noticeTitle.length())));
                }
            }
        }
        return count;
    }

    private int scrapePlanPages(String univName, String base) {
        int count = 0;
        for (String path : ScraperCore.PLAN_PATHS) {
            Document doc = engine.fetchRobust(base + path);
            if (doc == null || core.isRedirectedToHomepage(doc, base + path)) continue;
            String text = doc.text();
            if (!FetchEngine.PLAN_KW.matcher(text.substring(0, Math.min(600, text.length()))).find()) continue;
            // 整页作为一个 plan 条目（含导师列表）
            java.util.Map<String,Object> notice = engine.parseNoticePage(doc, base + path);
            notice.put("category", "plan");
            SchoolInfo info = new SchoolInfo(
                univName, "plan", doc.title(), base + path, base);
            info.setSnippet((String) notice.getOrDefault("snippet",""));
            if (db.upsertSchoolInfo(info)) {
                count++;
                log.info("  ✅ plan: {}", doc.title());
            }
            // 跟进计划页上的子链接（每个导师独立页面）
            for (Element a : doc.select("a[href]")) {
                String href = a.absUrl("href");
                if (href.isEmpty() || engine.skipHref(href)) continue;
                if (!FetchEngine.baseDomain3(href).equals(FetchEngine.baseDomain3(base))) continue;
                String linkText = a.text();
                if (FetchEngine.CN_NAME_PAT.matcher(linkText.trim()).matches()) continue; // 跳过纯姓名链接
                Document subDoc = engine.fetchRobust(href);
                if (subDoc == null) continue;
                if ("plan".equals(engine.detectPageType(subDoc, href))) {
                    java.util.Map<String,Object> sub = engine.parseNoticePage(subDoc, href);
                    SchoolInfo subInfo = new SchoolInfo(
                        univName, "plan", subDoc.title(), href, base + path);
                    subInfo.setSnippet((String) sub.getOrDefault("snippet",""));
                    if (db.upsertSchoolInfo(subInfo)) count++;
                }
            }
            if (count > 0) break; // 找到一个有效计划页即停
        }
        return count;
    }


    private Map<String,Object> scrapePreset(List<UniversityData.UniversityConfig> unis) {
        ExecutorService exec=Executors.newFixedThreadPool(concurrency);
        long t0=System.currentTimeMillis();
        List<Future<Integer>> futures=unis.stream()
            .map(u->exec.submit(()->scrapePresetUniversity(u))).collect(Collectors.toList());
        int total=futures.stream().mapToInt(f->{try{return f.get();}catch(Exception e){return 0;}}).sum();
        exec.shutdown();
        long elapsed=System.currentTimeMillis()-t0;
        log.info("🏁 批量: {} 位 {}s",total,elapsed/1000);
        return Map.of("scraped",total,"elapsedMs",elapsed,"totalInDb",db.countTeachers());
    }

    private int scrapePresetUniversity(UniversityData.UniversityConfig u) {
        int count=0;
        log.info("🎓 {}",u.name);
        for (UniversityData.DeptConfig dept : u.departments) {
            for (String listUrl : dept.getAllListUrls()) {
                Document listDoc=engine.fetchRobust(listUrl);
                if (listDoc==null) { log.error("  ❌ {}",listUrl); db.logScrape(listUrl,"FAILED","null"); continue; }
                if (core.isRedirectedToHomepage(listDoc, listUrl)) { log.warn("  ⚠️ 首页重定向跳过: {}",listUrl); continue; }
                listDoc.setBaseUri(listUrl);
                List<Map<String,String>> entries=engine.parseTeacherLinksCompat(
                    listDoc,listUrl,dept.teacherLinkSelector,dept.nameSelector);
                log.info("  {} → {} 位",dept.name,entries.size());
                for (Map<String,String> entry : entries) {
                    String url=entry.get("profileUrl");
                    if (db.existsByUrl(url)) continue;
                    Document pd=engine.fetchRobust(url);
                    if (pd==null) continue;
                    Map<String,String> info=engine.parseProfileAdvanced(pd);
                    Teacher t=new Teacher(entry.get("name"),u.name,dept.name,url);
                    t.setTitle(info.getOrDefault("title",""));
                    t.setResearchAreas(info.getOrDefault("researchAreas",""));
                    t.setEmail(info.getOrDefault("email",""));
                    t.setGoogleScholar(info.getOrDefault("googleScholar",""));
                    t.setLabName(info.getOrDefault("labName",""));
                    t.setRecruiting("true".equals(info.getOrDefault("recruiting","false")));
                    if (db.upsertTeacher(t)) {
                        count++;
                        String ra=t.getResearchAreas();
                        log.info("  ✅ {} | {} | {}",t.getName(),t.getTitle(),
                            ra!=null?ra.substring(0,Math.min(50,ra.length())):"");
                    }
                }
            }
        }
        log.info("🏁 {} 完成: {} 位",u.name,count);
        return count;
    }

    // ════════════════════════════════════════════════════════════════════════
    // URL 验证器
    // ════════════════════════════════════════════════════════════════════════


    public Map<String,Object> verifyUrls() {
        log.info("🔍 开始验证URL…");
        ExecutorService exec=Executors.newFixedThreadPool(verConcurrency);
        List<Future<Map<String,Object>>> futures=new ArrayList<>();
        for (UniversityData.UniversityConfig u : UniversityData.ALL)
            for (UniversityData.DeptConfig d : u.departments)
                futures.add(exec.submit(()->checkOneUrl(u.name,d)));
        int total=0,ok=0,failed=0;
        List<Map<String,Object>> failedList=new ArrayList<>();
        for (Future<Map<String,Object>> f : futures) {
            try {
                Map<String,Object> r=f.get((long)verTimeout*2,TimeUnit.MILLISECONDS);
                total++;
                boolean isOk=Boolean.TRUE.equals(r.get("ok"));
                if (isOk) ok++; else { failed++; failedList.add(r); }
                db.saveVerifyResult((String)r.get("university"),(String)r.get("department"),
                    (String)r.get("url"),(String)r.get("confidence"),
                    isOk,(int)r.getOrDefault("statusCode",0),
                    (String)r.get("redirect"),(String)r.get("alternative"),(String)r.get("error"));
            } catch (Exception e) { log.warn("验证异常: {}",e.getMessage()); }
        }
        exec.shutdown();
        log.info("✅ 验证完成: {}/{} OK, {} 失败",ok,total,failed);
        return Map.of("total",total,"ok",ok,"failed",failed,"failedDetails",failedList);
    }

    private Map<String,Object> checkOneUrl(String univName, UniversityData.DeptConfig dept) {
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("university",univName); r.put("department",dept.name);
        r.put("url",dept.url);       r.put("confidence",dept.urlConfidence);
        r.put("ok",false);           r.put("statusCode",0);
        r.put("redirect",null);      r.put("alternative",null); r.put("error",null);
        try {
            HttpClient c=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(verTimeout))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> resp=c.send(
                HttpRequest.newBuilder().uri(URI.create(dept.url))
                    .timeout(Duration.ofMillis(verTimeout))
                    .header("User-Agent",engine.ua()).header("Accept-Language","zh-CN,zh;q=0.9").GET().build(),
                HttpResponse.BodyHandlers.ofString());
            int code=resp.statusCode();
            r.put("statusCode",code); r.put("ok",code==200);
            if (!resp.uri().toString().equals(dept.url)) r.put("redirect",resp.uri().toString());
            if (code==200) { r.put("chineseLinks",engine.countCnLinks(resp.body()));
                log.info("  ✅ [{}] {}/{}",dept.urlConfidence,univName,dept.name); }
            else log.warn("  ❌ HTTP {} {}/{}",code,univName,dept.name);
        } catch (Exception e) {
            r.put("error",e.getClass().getSimpleName()+": "+e.getMessage());
            log.warn("  ⚠️ {}/{}: {}",univName,dept.name,e.getMessage());
        }
        return r;
    }

}