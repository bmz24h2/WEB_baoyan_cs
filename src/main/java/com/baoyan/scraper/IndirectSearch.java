package com.baoyan.scraper;

import com.baoyan.model.Teacher;
import com.baoyan.model.SchoolInfo;
import com.baoyan.db.DatabaseService;
import com.baoyan.engine.FetchEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * IndirectSearch — 间接搜索策略集（ScraperCore 的唯一子类）
 *
 * 继承 ScraperCore，在直连 edu.cn 失败后提供多级兜底：
 *
 *   L0  OpenAlex 批量 API（优先，免费开放，cursor 翻页最多 600 人）
 *   I-1 百度无限制搜索（不加 site: 限制，覆盖知乎/论坛/快照）
 *   I-2 AMiner 学术库 API（中文学者结构化数据）
 *   I-3 Bing 英文搜索（补充英文学术页）
 *   J   DBLP XML API（CCF 论文作者）
 *   K   Semantic Scholar API（AI/ML 方向）
 *   M   百度学术（中文期刊作者摘要抽取）
 *   N   知乎 + 保研论坛（正则提取导师姓名）
 *   C   交叉学科专用定向搜索
 *
 * 这是整个爬虫体系中 Spring 唯一注册的 @Service Bean；
 * ScraperService 通过 @Autowired ScraperCore core 以多态方式使用。
 *
 * 通过继承自动拥有父类的：
 *   - engine / db（@Autowired 字段）
 *   - discoverHomepage / discoverCsColleges / discoverFacultyPages
 *   - isRedirectedToHomepage
 *   - scrapeInfoFromIndirectSources（Strategy N）
 *   - 所有静态常量（FACULTY_PATHS / KNOWN_CS / YANZHAO_IDS 等）
 */
@Service
public class IndirectSearch extends ScraperCore {

    private static final Logger log = LoggerFactory.getLogger(IndirectSearch.class);


    // ════════════════════════════════════════════════════════════════════════
    // ★ Strategy I: 间接网络搜索（edu.cn 超时 / 策略 A-H 全部失败时的曲线兜底）
    //
    //   根本思路：不再假设能直连 .edu.cn，转而搜索"提到该校导师"的任意网页：
    //     I-1  百度无限制搜索  → 发现知乎/论坛/第三方教育站/百度快照
    //     I-2  AMiner 学术库  → aminer.cn 公开 API，收录绝大多数 985/211 教授
    //     I-3  Bing 英文搜索  → 补充百度未覆盖的英文学术页面
    //
    //   和原有 Strategy D（site:host 百度搜索）的区别：
    //     Strategy D 只找学校自己的域名，爬不到 → 返回空
    //     Strategy I 不加 site: 限制，任何提到该校导师的页面都纳入候选
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 校名 → 英文名映射（AMiner / Bing 搜索用）。
     * 超过10条须用 Map.ofEntries，不能用 Map.of。
     */

    private static final Map<String,String> UNIV_EN_NAMES;
    static {
        Map<String,String> m = new LinkedHashMap<>();
        m.put("江南大学",     "Jiangnan University");
        m.put("苏州大学",     "Soochow University");
        m.put("南京理工",     "Nanjing University of Science and Technology");
        m.put("南京航空",     "Nanjing University of Aeronautics and Astronautics");
        m.put("西南交通",     "Southwest Jiaotong University");
        m.put("北京邮电",     "Beijing University of Posts and Telecommunications");
        m.put("北京交通",     "Beijing Jiaotong University");
        m.put("西安电子",     "Xidian University");
        m.put("电子科技",     "University of Electronic Science and Technology of China");
        m.put("东北大学",     "Northeastern University China");
        m.put("大连理工",     "Dalian University of Technology");
        m.put("华南理工",     "South China University of Technology");
        m.put("湖南大学",     "Hunan University");
        m.put("重庆大学",     "Chongqing University");
        m.put("兰州大学",     "Lanzhou University");
        m.put("吉林大学",     "Jilin University");
        m.put("华东师范",     "East China Normal University");
        m.put("山东大学",     "Shandong University");
        m.put("中南大学",     "Central South University");
        m.put("厦门大学",     "Xiamen University");
        m.put("西北工业",     "Northwestern Polytechnical University");
        m.put("天津大学",     "Tianjin University");
        m.put("南开大学",     "Nankai University");
        m.put("四川大学",     "Sichuan University");
        m.put("北京理工",     "Beijing Institute of Technology");
        m.put("北京航空航天", "Beihang University");
        m.put("清华大学",     "Tsinghua University");
        m.put("北京大学",     "Peking University");
        m.put("浙江大学",     "Zhejiang University");
        m.put("上海交通",     "Shanghai Jiao Tong University");
        m.put("复旦大学",     "Fudan University");
        m.put("南京大学",     "Nanjing University");
        m.put("中国科学技术", "University of Science and Technology of China");
        m.put("哈尔滨工业",   "Harbin Institute of Technology");
        m.put("武汉大学",     "Wuhan University");
        m.put("西安交通",     "Xi'an Jiaotong University");
        m.put("同济大学",     "Tongji University");
        m.put("东南大学",     "Southeast University");
        m.put("中山大学",     "Sun Yat-sen University");
        m.put("华中科技",     "Huazhong University of Science and Technology");
        // ★ 末流 / 弱势 211 补充（AMiner / DBLP / Bing 策略依赖英文名）
        m.put("延边大学",     "Yanbian University");
        m.put("海南大学",     "Hainan University");
        m.put("宁夏大学",     "Ningxia University");
        m.put("新疆大学",     "Xinjiang University");
        m.put("西藏大学",     "Tibet University");
        m.put("青海大学",     "Qinghai University");
        m.put("东北林业",     "Northeast Forestry University");
        m.put("西南民族",     "Southwest Minzu University");
        m.put("广西大学",     "Guangxi University");
        m.put("贵州大学",     "Guizhou University");
        m.put("云南大学",     "Yunnan University");
        m.put("石河子大学",   "Shihezi University");
        m.put("中国传媒",     "Communication University of China");
        m.put("北京化工",     "Beijing University of Chemical Technology");
        m.put("华北电力",     "North China Electric Power University");
        m.put("中国矿业",     "China University of Mining and Technology");
        m.put("中国石油",     "China University of Petroleum");
        m.put("中国地质",     "China University of Geosciences");
        m.put("河海大学",     "Hohai University");
        m.put("南京农业",     "Nanjing Agricultural University");
        m.put("东北农业",     "Northeast Agricultural University");
        m.put("华中农业",     "Huazhong Agricultural University");
        m.put("合肥工业",     "Hefei University of Technology");
        m.put("燕山大学",     "Yanshan University");
        m.put("福州大学",     "Fuzhou University");
        m.put("南京师范",     "Nanjing Normal University");
        UNIV_EN_NAMES = Collections.unmodifiableMap(m);
    }

    // ── 顶层调度 ──────────────────────────────────────────────────────────────

    public int scrapeFromIndirectSources(String univName, String failedBase) {
        int total = 0;

        // ★ L0: OpenAlex 批量（最优先）
        //   优点：量大（可达 500+），返回完整引用/论文数
        //   缺点：只有英文名（publishName），无中文名
        int oa = scrapeViaOpenAlexBatch(univName);
        total += oa;
        log.info("  [L0] OpenAlex 批量结果: {} 位（英文名，后续 AMiner 补中文名）", oa);

        // ★ I-2: AMiner 始终运行（不受 OpenAlex 数量影响）
        //   AMiner 专收中国学者，有 name_zh 中文名字段，与 OpenAlex 互补
        //   upsert 逻辑：若该校已有同英文名的老师则更新中文名，否则新增
        int aminer = scrapeViaAminer(univName);
        total += aminer;
        log.info("  [I-2] AMiner 结果: {} 位（含中文名）", aminer);

        // 以下策略在 OpenAlex 已找到足够多时跳过（搜索引擎速度慢且有反爬）
        if (oa < 25) {
            // I-1: 百度无限制搜索
            int baidu = scrapeViaBaiduUnrestricted(univName, failedBase);
            total += baidu;
            log.info("  [I-1] 百度间接搜索结果: {} 位", baidu);

            // I-3: Bing 英文搜索
            if (total < 20) {
                int bing = scrapeViaBingSearch(univName);
                total += bing;
                log.info("  [I-3] Bing 间接搜索结果: {} 位", bing);
            }
        }

        // J: DBLP — 和 OpenAlex 互补（覆盖国内 CCF 论文作者，有中文名）
        if (total < 40) {
            int dblp = scrapeViaDblp(univName);
            total += dblp;
            log.info("  [J] DBLP 结果: {} 位", dblp);
        }

        // K: Semantic Scholar — AI/ML 方向补充
        if (total < 40) {
            int ss = scrapeViaSemanticScholar(univName);
            total += ss;
            log.info("  [K] Semantic Scholar 结果: {} 位", ss);
        }

        // M: 百度学术 — 中文学者覆盖补充
        if (total < 20) {
            int m = scrapeViaBaiduXueshu(univName);
            total += m;
            log.info("  [M] 百度学术结果: {} 位", m);
        }

        // N: 知乎/论坛 — 最终兜底
        if (total < 10) {
            int z = scrapeViaZhihuAndForums(univName);
            total += z;
            log.info("  [N-zhihu] 知乎/论坛结果: {} 位", z);
        }

        log.info("【{}】间接搜索合计: {} 位 (L0-OA/I-2AMiner中文名/I-1百度/I-3Bing/J-DBLP/K-S2/M-学术/N-知乎)", univName, total);
        return total;
    }

    // ── I-1: 百度无限制搜索 ──────────────────────────────────────────────────

    /**
     * 构造不含 site: 限制的搜索词，在百度上搜索任意提到该校导师的页面。
     *
     * 目标来源举例：
     *   - 知乎"XX大学XX学院导师汇总"
     *   - 小木虫/一亩三分地 论坛帖子
     *   - 第三方教育聚合站（shuoshiyan.com / yanzhao.hao.com 等）
     *   - 百度快照（webcache）—— 用于读取 edu.cn 缓存版本
     *
     * 注意：百度对爬虫有反爬，若频繁触发验证码，适当增大 delayMin/Max。
     */
    private int scrapeViaBaiduUnrestricted(String univName, String failedBase) {
        int count = 0;
        // ★ 扩充查询词：前 4 条通用 → 后 4 条定向第三方站（知乎/硕士研/小木虫）
        // 知乎和聚合站页面没有反爬，命中率远高于 edu.cn
        List<String> queries = Arrays.asList(
            "\"" + univName + "\" 计算机 导师 研究方向 简介",
            "\"" + univName + "\" 人工智能 教授 副教授 招生",
            "\"" + univName + "\" 软件工程 师资 教师简介",
            "\"" + univName + "\" CS 导师 保研 推免",
            // ★ 定向知乎（学生讨论导师信息，结构化、无反爬）
            "site:zhihu.com \"" + univName + "\" 导师 计算机 研究方向",
            "site:zhihu.com \"" + univName + "\" 保研 人工智能 推荐导师",
            // ★ 定向硕士研/一亩三分地（保研论坛，含大量导师点评）
            "site:shuoshiyan.com \"" + univName + "\" 计算机 导师",
            "site:1point3acres.com OR site:muzi.net \"" + univName + "\" 导师 CS",
            // ★ 百度学术（xueshu.baidu.com，中文学者页面）
            "site:xueshu.baidu.com \"" + univName + "\" 计算机 教授"
        );
        Set<String> visited = new LinkedHashSet<>();

        for (String q : queries) {
            if (count >= 30) break;
            // 爬前两页（百度 pn=0 第一页，pn=10 第二页）
            for (int pn = 0; pn <= 10; pn += 10) {
                if (count >= 30) break;
                try {
                    String searchUrl = "https://www.baidu.com/s?wd="
                        + URLEncoder.encode(q, "UTF-8") + "&pn=" + pn;
                    Document bd = Jsoup.connect(searchUrl)
                        .userAgent(engine.ua())
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .referrer("https://www.baidu.com/")
                        .timeout(12000).get();

                    // 百度 PC 版结果：.result h3 a  /  .c-container .t a
                    for (Element a : bd.select(".result h3 a, .c-container .t a")) {
                        String href = a.absUrl("href");
                        if (href.isEmpty() || href.contains("baidu.com")) continue;
                        if (!visited.add(href)) continue;

                        // 快速预过滤：摘要文字含职称/导师关键词才值得跟进
                        Element resultBox = a.closest(".result, .c-container");
                        String snippet = resultBox != null ? resultBox.text() : a.text();
                        boolean likelyFaculty =
                            snippet.contains("导师")  || snippet.contains("教授") ||
                            snippet.contains("副教授") || snippet.contains("研究方向") ||
                            snippet.contains("师资")  ||
                            FetchEngine.FACULTY_KW.matcher(snippet).find();
                        if (!likelyFaculty) continue;

                        // edu.cn 直链 → 先尝试百度快照，避免再次超时
                        String fetchUrl = href;
                        if (href.contains(".edu.cn")) {
                            fetchUrl = "https://webcache.baidu.com/search?q=cache:"
                                     + URLEncoder.encode(href, "UTF-8") + "&hl=zh-CN";
                        }

                        Document page = engine.fetchRobust(fetchUrl);
                        // webcache 也失败 → 让 fetchRobust 内部 Wayback 兜底
                        if (page == null && !fetchUrl.equals(href)) {
                            page = engine.fetchRobust(href);
                        }
                        if (page == null) continue;

                        // 判断页面类型，只处理"可能含教师名单"的页面
                        String pageType = engine.detectPageType(page, href);
                        if ("notice".equals(pageType) || "plan".equals(pageType)) continue;

                        int n = engine.scrapeTeachersFromPage(fetchUrl, univName, "百度间接");
                        if (n > 0) {
                            count += n;
                            log.info("    I-1 命中 {} 位 ← {}", n, href);
                        }
                        if (count >= 30) break;
                    }
                } catch (Exception e) {
                    log.debug("  I-1 百度搜索异常 [{}]: {}", q, e.getMessage());
                }
            }
        }
        return count;
    }

    // ── I-2: AMiner 学术库 API ──────────────────────────────────────────────

    /**
     * AMiner（aminer.cn）是目前最全面的中国学者数据库：
     *   - 收录了大量 985/211 大学教授的姓名、机构、职称、研究兴趣
     *   - 提供公开的 /api/search/person 接口，返回 JSON，无需登录
     *   - 数据来源于论文作者信息，质量优于手工爬取的高校网页
     *
     * 接口文档（非官方）：
     *   GET https://api.aminer.cn/api/search/person
     *       ?query=<关键词>&t=person&offset=0&size=20&src=search
     *   响应：{ "result": [...], "total": N }
     *   每个 person 对象含：id, name, name_zh, org, position, tags, email 等
     */
    private int scrapeViaAminer(String univName) {
        int count = 0;
        HttpClient hc = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

        for (String q : buildAminerQueries(univName)) {
            if (count >= 40) break;
            try {
                String apiUrl = "https://api.aminer.cn/api/search/person"
                    + "?query=" + URLEncoder.encode(q, "UTF-8")
                    + "&t=person&offset=0&size=20&src=search";
                HttpResponse<String> resp = hc.send(
                    HttpRequest.newBuilder().uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(12))
                        .header("User-Agent",  engine.ua())
                        .header("Referer",     "https://www.aminer.cn/")
                        .header("Accept",      "application/json, */*")
                        .header("Origin",      "https://www.aminer.cn").GET().build(),
                    HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() != 200) {
                    log.debug("  I-2 AMiner HTTP {} for [{}]", resp.statusCode(), q);
                    continue;
                }
                String body = resp.body();
                if (!body.contains("\"name\"") && !body.contains("\"result\"")) continue;

                int n = parseAminerPersons(body, univName);
                count += n;
                log.debug("  I-2 AMiner [{}] → {} 位", q, n);
            } catch (Exception e) {
                log.debug("  I-2 AMiner 异常 [{}]: {}", q, e.getMessage());
            }
        }
        return count;
    }

    /**
     * 轻量 regex 解析 AMiner /api/search/person 的 JSON 响应。
     *
     * 避免引入 Jackson/Gson 解析依赖（项目已有 Jackson，但结构复杂），
     * 改用正则切割条目 + 字段提取，足以覆盖 AMiner 的扁平化 person 对象。
     *
     * AMiner person 对象样例（精简）：
     * {
     *   "id": "53f4305adabfae4b3400d5ea",
     *   "name": "Wei Wang",
     *   "name_zh": "王伟",
     *   "org": "江南大学人工智能与计算机学院",
     *   "position": "教授",
     *   "tags": ["machine learning","deep learning"],
     *   "email": "wangwei@jiangnan.edu.cn"
     * }
     */
    private int parseAminerPersons(String json, String univName) {
        int count = 0;

        // 每个 person 对象以 "id" 字段起头，按此切分
        String[] entries = json.split("\\{\"id\"\\s*:");

        // 预编译提取模式
        Pattern idPat       = Pattern.compile("^\\s*\"([a-f0-9]{18,30})\"");
        Pattern namePat     = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]{2,30})\"");
        Pattern nameZhPat   = Pattern.compile("\"name_zh\"\\s*:\\s*\"([^\"]{2,8})\"");
        Pattern orgPat      = Pattern.compile("\"(?:org|aff)\"\\s*:\\s*\"([^\"]{3,80})\"");
        Pattern posPat      = Pattern.compile("\"pos(?:ition)?\"\\s*:\\s*\"([^\"]{2,40})\"");
        Pattern tagPat      = Pattern.compile("\"(?:tags|interests)\"\\s*:\\s*\\[([^\\]]{0,400})\\]");
        Pattern emailPat    = Pattern.compile("\"email\"\\s*:\\s*\"([^\"@]{1,40}@[^\"]{3,40})\"");

        for (String entry : entries) {
            if (entry.length() < 20) continue;

            // 提取 AMiner person ID → 构造规范 profile URL
            Matcher idM = idPat.matcher(entry);
            String profileUrl = "https://www.aminer.cn/profile/"
                + (idM.find() ? idM.group(1) : "unknown_" + (count + 1));

            // 优先取中文姓名
            String name = null;
            Matcher znm = nameZhPat.matcher(entry);
            if (znm.find()) name = znm.group(1).trim();
            if (name == null || name.isEmpty()) {
                Matcher nm = namePat.matcher(entry);
                if (nm.find()) name = nm.group(1).trim();
            }
            if (name == null || name.isEmpty()) continue;

            // 姓名格式校验：中文 2-5 字 或 西文 姓/名 格式
            boolean validCn = name.matches("[\\u4e00-\\u9fff]{2,5}");
            boolean validEn = name.matches("[A-Z][a-z]+([ \\-][A-Z][a-z]+){1,3}");
            if (!validCn && !validEn) continue;

            // 机构校验：必须属于目标学校，防止同名其他学校污染
            Matcher om = orgPat.matcher(entry);
            String org = om.find() ? om.group(1).trim() : "";
            if (!org.isEmpty() && !isOrgMatchUniv(org, univName)) continue;

            // 职称
            String title = "教授";    // AMiner 默认只收录正高及以上
            Matcher pm = posPat.matcher(entry);
            if (pm.find()) title = pm.group(1).trim();

            // 研究方向
            String interests = "";
            Matcher tm = tagPat.matcher(entry);
            if (tm.find()) {
                interests = tm.group(1)
                    .replaceAll("\"", "").replaceAll(",\\s*", "；").trim();
                if (interests.length() > 300) interests = interests.substring(0, 300);
            }

            // 邮箱
            String email = "";
            Matcher em = emailPat.matcher(entry);
            if (em.find()) email = em.group(1).trim();

            // 院系：从 org 里剥离校名后剩余部分（"江南大学人工智能与计算机学院" → "人工智能与计算机学院"）
            String dept = org.isEmpty() ? "CS（AMiner）" : stripUnivPrefix(org, univName);

            Teacher t = new Teacher(name, univName, dept, profileUrl);
            t.setTitle(title);
            t.setEmail(email);
            t.setResearchAreas(interests);
            if (db.upsertTeacher(t)) count++;
        }
        return count;
    }

    // ── I-3: Bing 英文搜索 ──────────────────────────────────────────────────

    /**
     * 用英文查询搜索 Bing，主要命中：
     *   - Google Scholar 个人主页（有英文姓名 + 机构标签）
     *   - ResearchGate 个人页面
     *   - 学校英文版官网（部分高校有单独的 en.xxx.edu.cn）
     *   - 会议/期刊论文作者简介
     *
     * 英文搜索对中文名解析效果有限，用于 I-1/I-2 都不足时的最后补充。
     */
    private int scrapeViaBingSearch(String univName) {
        int count = 0;
        String engName = UNIV_EN_NAMES.getOrDefault(univName, univName);
        List<String> queries = Arrays.asList(
            "\"" + engName + "\" computer science faculty professors",
            "\"" + engName + "\" AI machine learning researchers"
        );

        for (String q : queries) {
            if (count >= 15) break;
            try {
                Document bing = Jsoup.connect(
                        "https://www.bing.com/search?q=" + URLEncoder.encode(q, "UTF-8"))
                    .userAgent(engine.ua())
                    .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8")
                    .timeout(10000).get();

                for (Element a : bing.select("#b_results h2 a")) {
                    String href = a.absUrl("href");
                    if (href.isEmpty() || href.contains("bing.com")
                            || href.contains("microsoft.com")) continue;

                    Document page = engine.fetchRobust(href);
                    if (page == null) continue;

                    int n = engine.scrapeTeachersFromPage(href, univName, "Bing间接");
                    if (n > 0) {
                        count += n;
                        log.info("    I-3 Bing 命中 {} 位 ← {}", n, href);
                    }
                    if (count >= 15) break;
                }
            } catch (Exception e) {
                log.debug("  I-3 Bing 搜索异常: {}", e.getMessage());
            }
        }
        return count;
    }

    // ── Strategy I 辅助方法 ──────────────────────────────────────────────────

    /**
     * 构建 AMiner 搜索词：中文 + 英文 × CS相关院系，共 4-6 条，
     * 通过多次搜索覆盖不同院系名称（计算机 / 人工智能 / 软件等）。
     */
    private List<String> buildAminerQueries(String univName) {
        List<String> qs = new ArrayList<>();
        // 宽泛搜索（不加方向词），适合末流211这类师资少的学校
        qs.add(univName);
        // CS 相关方向词覆盖
        qs.add(univName + " 计算机");
        qs.add(univName + " 人工智能");
        qs.add(univName + " 软件工程");
        qs.add(univName + " 信息工程");
        qs.add(univName + " 数字媒体");
        qs.add(univName + " 网络安全");
        String en = UNIV_EN_NAMES.get(univName);
        if (en != null) {
            qs.add(en);
            qs.add(en + " computer science");
            qs.add(en + " artificial intelligence");
            qs.add(en + " software engineering");
        }
        return qs;
    }

    /**
     * 判断 AMiner 的机构字段（org/aff）是否属于目标学校。
     * AMiner 里机构名形式多样："江南大学"/"Jiangnan University"/
     * "江南大学人工智能与计算机学院"/"School of AI, Jiangnan Univ." 等。
     */
    private boolean isOrgMatchUniv(String org, String univName) {
        if (org == null || univName == null) return false;
        // 中文前缀匹配：取校名前2字（"江南"、"哈工"等）
        String shortCn = univName.length() >= 2 ? univName.substring(0, 2) : univName;
        if (org.contains(shortCn)) return true;
        // 英文名前缀匹配（首单词，如 "Jiangnan"）
        String enName = UNIV_EN_NAMES.getOrDefault(univName, "");
        if (!enName.isEmpty()) {
            String enFirst = enName.split("\\s")[0]; // "Jiangnan"
            if (org.toLowerCase().contains(enFirst.toLowerCase())) return true;
        }
        return false;
    }

    /**
     * 从 AMiner org 字段里去掉学校名前缀，得到更精准的院系名。
     * 例："江南大学人工智能与计算机学院" → "人工智能与计算机学院"
     */
    private String stripUnivPrefix(String org, String univName) {
        // 先尝试去掉完整校名
        if (org.startsWith(univName)) return org.substring(univName.length()).trim();
        // 再尝试去掉前2-4字的短名
        for (int len = Math.min(univName.length(), 4); len >= 2; len--) {
            String prefix = univName.substring(0, len);
            if (org.startsWith(prefix)) return org.substring(len).trim();
        }
        return org;
    }

    // ════════════════════════════════════════════════════════════════════════

    // ════════════════════════════════════════════════════════════════════════
    // Strategy J: DBLP API — 专为 CS 设计的开放学术数据库
    //   - 无需 API Key，公开 JSON 接口
    //   - 收录所有在 CCF/ACM/IEEE 发表过论文的学者
    //   - 接口：https://dblp.org/search/author/api?q=<query>&format=json&h=100
    // ════════════════════════════════════════════════════════════════════════

    public int scrapeViaDblp(String univName) {
        int count = 0;
        String engName = UNIV_EN_NAMES.getOrDefault(univName, univName);
        List<String> queries = Arrays.asList(engName,
            engName.replaceAll("University of |University$", "").trim());

        HttpClient hc = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
        Set<String> seen = new HashSet<>();

        for (String q : queries) {
            if (count >= 60 || q.length() < 3) continue;
            try {
                String apiUrl = "https://dblp.org/search/author/api?q="
                    + URLEncoder.encode(q, "UTF-8") + "&format=json&h=100&f=0";
                HttpResponse<String> resp = hc.send(
                    HttpRequest.newBuilder().uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", engine.ua())
                        .header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) continue;
                String body = resp.body();

                // 正则匹配 DBLP person 对象各字段
                // DBLP JSON: {"result":{"hits":{"hit":[{"@pid":"...","info":{"author":"...","url":"...","notes":{"note":[{"@type":"affiliation","text":"..."}]}}}]}}}
                Pattern authorPat = Pattern.compile("\"author\"\\s*:\\s*\"([^\"]{2,40})\"");
                Pattern urlPat    = Pattern.compile("\"url\"\\s*:\\s*\"(https://dblp\\.org/pid/[^\"]+)\"");
                Pattern notePat   = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]{5,80})\"");

                // 按 @pid 字段分割每个作者条目
                String[] entries = body.split("\"@pid\"");
                for (int i = 1; i < entries.length && count < 60; i++) {
                    String e = entries[i];

                    Matcher am = authorPat.matcher(e);
                    if (!am.find()) continue;
                    String rawName = am.group(1).trim();
                    if (!seen.add(rawName.toLowerCase())) continue;

                    // 校验机构归属（notes 里的 text 字段）
                    Matcher nm = notePat.matcher(e);
                    boolean orgMatch = false;
                    String dept = "CS\uff08DBLP\uff09";
                    while (nm.find()) {
                        String note = nm.group(1);
                        if (isOrgMatchUniv(note, univName) || isOrgMatchUniv(note, engName)) {
                            orgMatch = true;
                            if (note.length() <= 40) dept = note;
                        }
                    }
                    if (!orgMatch && !e.contains(engName) && !e.contains(univName)) continue;

                    // 去掉 DBLP 消歧后缀，如 "Wei Wang 0001"
                    String name = rawName.replaceAll(" [0-9]{4}$", "").trim();

                    // profile URL
                    Matcher um = urlPat.matcher(e);
                    String profileUrl = um.find() ? um.group(1)
                        : "https://dblp.org/search?q=" + URLEncoder.encode(name + " " + engName, "UTF-8");

                    Teacher t = new Teacher(name, univName, dept, profileUrl);
                    t.setTitle("\u6559\u6388");        // DBLP 不含职称，默认教授
                    t.setResearchAreas("CS\uff08DBLP\uff09");
                    if (db.upsertTeacher(t)) count++;
                }
                Thread.sleep(800);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); break;
            } catch (Exception ex) {
                log.debug("  [J] DBLP \u5f02\u5e38 [{}]: {}", q, ex.getMessage());
            }
        }
        log.debug("  [J] DBLP({}) \u2192 {}", univName, count);
        return count;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Strategy K: Semantic Scholar API — AI/ML 方向最全的学术数据库
    //   - 完全免费，无需 Key（1 req/s），含 hIndex、引用数
    //   - 接口：https://api.semanticscholar.org/graph/v1/author/search
    // ════════════════════════════════════════════════════════════════════════

    public int scrapeViaSemanticScholar(String univName) {
        int count = 0;
        String engName = UNIV_EN_NAMES.getOrDefault(univName, univName);
        List<String> queries = Arrays.asList(
            engName + " computer science",
            engName + " artificial intelligence",
            engName + " machine learning",
            engName + " software engineering");

        HttpClient hc = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
        Set<String> seenIds = new HashSet<>();

        for (String q : queries) {
            if (count >= 50) break;
            try {
                String apiUrl = "https://api.semanticscholar.org/graph/v1/author/search"
                    + "?query=" + URLEncoder.encode(q, "UTF-8")
                    + "&fields=name,affiliations,hIndex,citationCount,externalIds&limit=20";
                HttpResponse<String> resp = hc.send(
                    HttpRequest.newBuilder().uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", engine.ua())
                        .header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 429) { Thread.sleep(3000); continue; }
                if (resp.statusCode() != 200) continue;
                String body = resp.body();

                // 按 authorId 字段拆分每个作者对象
                Pattern idPat   = Pattern.compile("\"authorId\"\\s*:\\s*\"([0-9]+)\"");
                Pattern namePat = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]{2,40})\"");
                Pattern hPat    = Pattern.compile("\"hIndex\"\\s*:\\s*([0-9]+)");

                String[] authors = body.split("\"authorId\"");
                for (int i = 1; i < authors.length && count < 50; i++) {
                    String a = "\"authorId\"" + authors[i];

                    Matcher idm = idPat.matcher(a);
                    if (!idm.find()) continue;
                    String authorId = idm.group(1);
                    if (!seenIds.add(authorId)) continue;

                    Matcher nm = namePat.matcher(a);
                    if (!nm.find()) continue;
                    String name = nm.group(1).trim();
                    if (name.length() < 2 || name.equals("name")) continue;

                    // 校验 affiliations 是否包含目标学校
                    int affIdx = a.indexOf("\"affiliations\"");
                    if (affIdx < 0) continue;
                    String affSec = a.substring(affIdx, Math.min(affIdx + 400, a.length()));
                    if (!isOrgMatchUniv(affSec, univName) && !isOrgMatchUniv(affSec, engName)) continue;

                    // 院系：取第一个 affiliation name
                    Matcher affm = namePat.matcher(affSec);
                    String dept = affm.find() ? affm.group(1) : "CS\uff08S2\uff09";
                    if (dept.length() > 40) dept = "CS\uff08S2\uff09";

                    Matcher hm = hPat.matcher(a);
                    int hIndex = hm.find() ? Integer.parseInt(hm.group(1)) : 0;
                    String title = hIndex >= 20 ? "\u6559\u6388" : hIndex >= 10 ? "\u526f\u6559\u6388" : "\u8bb2\u5e08";

                    String profileUrl = "https://www.semanticscholar.org/author/" + authorId;
                    Teacher t = new Teacher(name, univName, dept, profileUrl);
                    t.setTitle(title);
                    t.setResearchAreas("h-index=" + hIndex);
                    if (db.upsertTeacher(t)) count++;
                }
                Thread.sleep(1100); // 免费 1 req/s
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); break;
            } catch (Exception ex) {
                log.debug("  [K] S2 \u5f02\u5e38 [{}]: {}", q, ex.getMessage());
            }
        }
        log.debug("  [K] SemanticScholar({}) \u2192 {}", univName, count);
        return count;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ════════════════════════════════════════════════════════════════════════
    // ★ Strategy L0: OpenAlex 批量查询（最优先）
    //
    //   改进点（vs 原 L 策略）：
    //     1. 提升到第一位执行，结果充足时跳过 Baidu/AMiner/Bing
    //     2. 去掉 CS concept 过滤，改为全量拉取后客户端过滤（末流211 CS 标签不全）
    //     3. cursor 翻页，每页 200 条，最多 3 页（上限 600 人）
    //     4. 保留旧 scrapeViaOpenAlex 作为降级兜底
    // ════════════════════════════════════════════════════════════════════════

    /** CS 及相关方向的 OpenAlex Concept ID（客户端过滤用） */
    private static final Set<String> OA_CS_CONCEPTS = new HashSet<>(Arrays.asList(
        "C41008148",   // Computer Science
        "C154945302",  // Artificial Intelligence
        "C119857082",  // Machine Learning
        "C31972630",   // Information retrieval
        "C108827166",  // Internet
        "C2522767166", // Data Science
        "C1171819",    // Deep Learning
        "C33923547",   // Computer Vision
        "C2776903658", // Natural Language Processing
        "C11413529",   // Computer Network
        "C62520636",   // Robotics
        "C126322002",  // Bioinformatics
        "C121332964",  // Physics (量子计算方向)
        "C144133560",  // Operations research（算法优化）
        "C127413603"   // Computer Security
    ));

    public int scrapeViaOpenAlexBatch(String univName) {
        int count = 0;
        String engName = UNIV_EN_NAMES.getOrDefault(univName, univName);
        HttpClient hc = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
        try {
            // Step 1: 获取机构 ID（和旧版相同）
            String instUrl = "https://api.openalex.org/institutions?search="
                + URLEncoder.encode(engName, "UTF-8")
                + "&filter=country_code:CN&per-page=1&mailto=baoyan@edu.cn";
            HttpResponse<String> instResp = hc.send(
                HttpRequest.newBuilder().uri(URI.create(instUrl))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", engine.ua()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            // 429 限流：等 3s 后重试一次
            if (instResp.statusCode() == 429) {
                log.warn("  [L0] OpenAlex 429 限流，等待 3s 后重试…");
                Thread.sleep(3000);
                instResp = hc.send(
                    HttpRequest.newBuilder().uri(URI.create(instUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", engine.ua()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            }
            if (instResp.statusCode() != 200) return 0;

            Pattern instPat = Pattern.compile("\"id\"\\s*:\\s*\"(https://openalex\\.org/I[0-9]+)\"");
            Matcher instM = instPat.matcher(instResp.body());
            if (!instM.find()) {
                log.debug("  [L0] OpenAlex 找不到机构: {}", engName);
                return scrapeViaOpenAlex(univName); // 降级到旧版
            }
            String instId = instM.group(1);
            log.info("  [L0] OpenAlex 机构: {} → {}", engName, instId);

            // Step 2: cursor 翻页批量拉取，翻完所有页为止（由 next_cursor 是否存在决定）
            String cursor = "*";
            int page = 0;
            while (true) {
                page++;
                Thread.sleep(500); // OpenAlex polite pool 要求，加到 500ms 更稳
                String authUrl = "https://api.openalex.org/authors"
                    + "?filter=last_known_institutions.id:" + URLEncoder.encode(instId, "UTF-8")
                    + "&sort=cited_by_count:desc&per-page=200"
                    + "&cursor=" + URLEncoder.encode(cursor, "UTF-8")
                    + "&mailto=baoyan@edu.cn";

                HttpResponse<String> authResp = hc.send(
                    HttpRequest.newBuilder().uri(URI.create(authUrl))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", engine.ua()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (authResp.statusCode() == 429) {
                    log.warn("  [L0] OpenAlex authors 429，等待 5s 后重试…");
                    Thread.sleep(5000);
                    authResp = hc.send(
                        HttpRequest.newBuilder().uri(URI.create(authUrl))
                            .timeout(Duration.ofSeconds(20))
                            .header("User-Agent", engine.ua()).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                }
                if (authResp.statusCode() != 200) break;
                String body = authResp.body();

                int pageCount = parseOpenAlexBatchPage(body, univName);
                count += pageCount;
                log.info("  [L0] OpenAlex 第{}页: {} 位，累计 {} 位", page, pageCount, count);

                // 取下一页 cursor；没有或本页为空则结束
                Pattern ncPat = Pattern.compile("\"next_cursor\"\\s*:\\s*\"([^\"]+)\"");
                Matcher ncm = ncPat.matcher(body);
                if (!ncm.find() || pageCount == 0) break;
                cursor = ncm.group(1);
            } // end while
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.debug("  [L0] OpenAlex batch 异常 [{}]: {}", univName, ex.getMessage());
        }
        log.info("  [L0] OpenAlex batch({}) → {} 位", univName, count);
        return count;
    }

    /**
     * 解析一页 OpenAlex authors 响应，过滤 CS 相关 concept 后写库。
     * 对没有任何 CS concept 标签的作者也保留（末流211 标签不全，宁可多收）。
     */
    // OpenAlex subfield/field 规范英文名 → 中文翻译
    // OpenAlex 的 topics 层级：topic → subfield → field → domain
    // subfield 是全球固定的约250个规范方向名，CS field 下约20个，用它分类最准确
    private static final Map<String, String> OA_TOPIC_CN = new HashMap<>();
    static {
        // ── CS field 下的核心 subfield（OpenAlex 规范名，精确匹配）──
        OA_TOPIC_CN.put("Artificial Intelligence",                "人工智能 机器学习 深度学习");
        OA_TOPIC_CN.put("Computer Vision and Pattern Recognition","计算机视觉 图像识别 模式识别 目标检测");
        OA_TOPIC_CN.put("Computer Networks and Communications",   "网络与通信 计算机网络 无线通信");
        OA_TOPIC_CN.put("Computer Science Applications",          "计算机应用");
        OA_TOPIC_CN.put("Computational Theory and Mathematics",   "算法理论 计算理论 复杂度");
        OA_TOPIC_CN.put("Computer Graphics and Computer-Aided Design", "计算机图形学 可视化");
        OA_TOPIC_CN.put("Human-Computer Interaction",             "人机交互 HCI 用户界面 虚拟现实");
        OA_TOPIC_CN.put("Information Systems",                     "信息系统 数据库 信息管理");
        OA_TOPIC_CN.put("Information Systems and Management",      "信息系统 信息管理");
        OA_TOPIC_CN.put("Hardware and Architecture",              "体系结构 处理器 硬件");
        OA_TOPIC_CN.put("Software",                               "软件工程 程序分析");
        OA_TOPIC_CN.put("Signal Processing",                      "信号处理 通信");
        OA_TOPIC_CN.put("Control and Systems Engineering",        "控制 系统工程 自动化");
        OA_TOPIC_CN.put("Computational Mechanics",                "计算力学 高性能计算");
        OA_TOPIC_CN.put("Theoretical Computer Science",           "理论计算机 算法理论");

        // ── CS field 本身及邻近 field ──
        OA_TOPIC_CN.put("Computer Science",                       "计算机科学");
        OA_TOPIC_CN.put("Artificial Intelligence and Machine Learning", "人工智能 机器学习");

        // ── 常见细分 topic 名（OpenAlex topic 层，补充匹配近年新方向）──
        OA_TOPIC_CN.put("Machine Learning",                 "机器学习 深度学习");
        OA_TOPIC_CN.put("Deep Learning",                    "深度学习 机器学习 神经网络");
        OA_TOPIC_CN.put("Computer Vision",                  "计算机视觉 图像识别");
        OA_TOPIC_CN.put("Natural Language Processing",      "自然语言处理 NLP");
        OA_TOPIC_CN.put("Large Language Models",            "大语言模型 LLM 大模型");
        OA_TOPIC_CN.put("Large Language Model",             "大语言模型 LLM 大模型");
        OA_TOPIC_CN.put("Generative AI",                    "生成式AI 大模型 大语言模型");
        OA_TOPIC_CN.put("Foundation Models",                "基础模型 大模型 大语言模型");
        OA_TOPIC_CN.put("Transformer",                      "大语言模型 Transformer");
        OA_TOPIC_CN.put("Reinforcement Learning",           "强化学习");
        OA_TOPIC_CN.put("Graph Neural Networks",            "图神经网络 GNN");
        OA_TOPIC_CN.put("Graph Neural Network",             "图神经网络 GNN");
        OA_TOPIC_CN.put("Multimodal Learning",              "多模态 多模态大模型");
        OA_TOPIC_CN.put("Multimodal Large Language Models", "多模态 多模态大模型 大语言模型");
        OA_TOPIC_CN.put("Human-Computer Interaction Design","人机交互 HCI 用户界面");
        OA_TOPIC_CN.put("Virtual Reality",                  "虚拟现实 VR 人机交互");
        OA_TOPIC_CN.put("Augmented Reality",                "增强现实 AR 人机交互");
        OA_TOPIC_CN.put("Computer Security",                "信息安全 网络安全");
        OA_TOPIC_CN.put("Cybersecurity",                    "网络安全 信息安全");
        OA_TOPIC_CN.put("Cryptography",                     "密码学 信息安全 网络安全");
        OA_TOPIC_CN.put("Network Security",                 "网络安全 密码学");
        OA_TOPIC_CN.put("Blockchain",                       "区块链 网络安全");
        OA_TOPIC_CN.put("Software Engineering",             "软件工程 程序分析");
        OA_TOPIC_CN.put("Data Mining",                      "数据挖掘 数据库");
        OA_TOPIC_CN.put("Database Systems",                 "数据库 数据挖掘");
        OA_TOPIC_CN.put("Advanced Database Systems and Queries", "数据库 数据挖掘");
        OA_TOPIC_CN.put("Distributed Systems",              "分布式 系统 高性能计算");
        OA_TOPIC_CN.put("Cloud Computing",                  "云计算 分布式 高性能计算");
        OA_TOPIC_CN.put("Edge Computing",                   "边缘计算 嵌入式 物联网");
        OA_TOPIC_CN.put("Computer Architecture",            "体系结构 高性能计算");
        OA_TOPIC_CN.put("High Performance Computing",       "高性能计算 并行计算");
        OA_TOPIC_CN.put("Parallel Computing",               "并行计算 高性能计算");
        OA_TOPIC_CN.put("Algorithms",                       "算法理论 算法");
        OA_TOPIC_CN.put("Quantum Computing",                "量子计算 量子算法");
        OA_TOPIC_CN.put("Quantum Information",              "量子信息 量子计算");
        OA_TOPIC_CN.put("Embedded Systems",                 "嵌入式 物联网");
        OA_TOPIC_CN.put("Internet of Things",               "物联网 嵌入式");
        OA_TOPIC_CN.put("IoT and Edge/Fog Computing",       "物联网 边缘计算 嵌入式");
        OA_TOPIC_CN.put("Robotics",                         "机器人 具身智能 强化学习");
        OA_TOPIC_CN.put("Embodied AI",                      "具身智能 机器人");
        OA_TOPIC_CN.put("Autonomous Driving",               "自动驾驶 计算机视觉");
        OA_TOPIC_CN.put("Autonomous Vehicles",              "自动驾驶 计算机视觉");
        OA_TOPIC_CN.put("Image Processing",                 "图像处理 计算机视觉");
        OA_TOPIC_CN.put("Object Detection",                 "目标检测 计算机视觉");
        OA_TOPIC_CN.put("Signal Processing",                "信号处理 通信");
        OA_TOPIC_CN.put("Wireless Communications",          "无线通信 网络与通信");
        OA_TOPIC_CN.put("Advanced MIMO Systems Optimization","无线通信 网络与通信");
        OA_TOPIC_CN.put("Federated Learning",               "联邦学习 机器学习");
        OA_TOPIC_CN.put("Transfer Learning",                "迁移学习 机器学习");
        OA_TOPIC_CN.put("Domain Adaptation and Few-Shot Learning", "迁移学习 机器学习");
        OA_TOPIC_CN.put("Knowledge Graph",                  "知识图谱 自然语言处理");
        OA_TOPIC_CN.put("Recommendation Systems",           "推荐系统 数据挖掘");
        OA_TOPIC_CN.put("Bioinformatics",                   "生物信息 算法");
        OA_TOPIC_CN.put("Computational Drug Discovery Methods", "生物信息 算法");
        OA_TOPIC_CN.put("Medical Image Analysis",           "医学图像 计算机视觉");
        OA_TOPIC_CN.put("Computational Intelligence",       "计算智能 机器学习");
        OA_TOPIC_CN.put("Pattern Recognition",              "模式识别 计算机视觉");
        OA_TOPIC_CN.put("Information Retrieval",            "信息检索 自然语言处理");
        OA_TOPIC_CN.put("Advanced Bandit Algorithms Research", "强化学习 机器学习");
        OA_TOPIC_CN.put("Advanced Data Compression Techniques", "数据压缩 算法理论");
    }
    /** 把 OpenAlex 英文 topic 名转为「英文 中文」混合描述，便于中英双语检索 */
    private static String translateOATopic(String en) {
        String cn = OA_TOPIC_CN.get(en);
        return cn != null ? en + " " + cn : en;
    }

    private int parseOpenAlexBatchPage(String body, String univName) {
        int count = 0;
        // 按 author ID 切分条目
        String[] segments = body.split("\"id\"\\s*:\\s*\"https://openalex\\.org/A");
        Pattern namePat    = Pattern.compile("\"display_name\"\\s*:\\s*\"([^\"]{2,50})\"");
        Pattern citedPat   = Pattern.compile("\"cited_by_count\"\\s*:\\s*([0-9]+)");
        Pattern worksPat   = Pattern.compile("\"works_count\"\\s*:\\s*([0-9]+)");
        Pattern orcidPat   = Pattern.compile("\"orcid\"\\s*:\\s*\"(https://orcid\\.org/[^\"]+)\"");

        // ★ counts_by_year 解析
        Pattern cbyPat  = Pattern.compile("\"counts_by_year\"\\s*:\\s*\\[([^\\]]*)]");
        Pattern yearPat = Pattern.compile("\"year\"\\s*:\\s*([0-9]{4})");
        Pattern wkYPat  = Pattern.compile("\"works_count\"\\s*:\\s*([0-9]+)");
        Pattern ctYPat  = Pattern.compile("\"cited_by_count\"\\s*:\\s*([0-9]+)");

        // ★ topics 整块（含 field/subfield/domain 嵌套结构）
        Pattern topicsBlockPat = Pattern.compile("\"topics\"\\s*:\\s*\\[(.*?)]\\s*,\\s*\"(?:affiliations|counts_by_year|x_concepts|summary_stats|works_api_url)", Pattern.DOTALL);

        for (int i = 1; i < segments.length; i++) {
            String seg = segments[i];

            Matcher nm = namePat.matcher(seg);
            if (!nm.find()) continue;
            String name = nm.group(1).trim();
            if (name.length() < 2) continue;

            int cited = 0, works = 0;
            Matcher cm = citedPat.matcher(seg); if (cm.find()) cited = Integer.parseInt(cm.group(1));
            Matcher wm = worksPat.matcher(seg); if (wm.find()) works = Integer.parseInt(wm.group(1));
            if (works < 2) continue;

            // ★★ 核心改写：从 topics 提取结构化的 field + subfield ★★
            // OpenAlex topic 结构: {display_name, subfield:{display_name}, field:{display_name}, domain:{display_name}}
            // 用 field 判断是否 CS，用 subfield 作为研究方向（subfield 是规范名）
            Set<String> fields    = new LinkedHashSet<>();
            Set<String> subfields = new LinkedHashSet<>();
            Set<String> topicNames= new LinkedHashSet<>();

            Matcher tbM = topicsBlockPat.matcher(seg);
            String topicsBlock = tbM.find() ? tbM.group(1) : "";
            if (topicsBlock.isEmpty()) {
                // 兜底：取 topics 到下一个大字段之间
                int ti = seg.indexOf("\"topics\"");
                if (ti >= 0) topicsBlock = seg.substring(ti, Math.min(ti + 4000, seg.length()));
            }

            // 按每个 topic 对象切分（每个 topic 以 "id":"https://openalex.org/T 开头）
            String[] topicObjs = topicsBlock.split("\"id\"\\s*:\\s*\"https://openalex\\.org/T");
            Pattern fieldPat    = Pattern.compile("\"field\"\\s*:\\s*\\{[^}]*?\"display_name\"\\s*:\\s*\"([^\"]+)\"");
            Pattern subfieldPat = Pattern.compile("\"subfield\"\\s*:\\s*\\{[^}]*?\"display_name\"\\s*:\\s*\"([^\"]+)\"");
            Pattern topicNamePat= Pattern.compile("^[^{]*?\"display_name\"\\s*:\\s*\"([^\"]+)\"");

            for (int j = 1; j < topicObjs.length; j++) {
                String tObj = topicObjs[j];
                Matcher fm = fieldPat.matcher(tObj);
                if (fm.find()) fields.add(fm.group(1).trim());
                Matcher sfm = subfieldPat.matcher(tObj);
                if (sfm.find()) subfields.add(sfm.group(1).trim());
                Matcher tnm = topicNamePat.matcher(tObj);
                if (tnm.find()) topicNames.add(tnm.group(1).trim());
            }

            // ★ CS 过滤：field 必须含 "Computer Science"（金标准，OpenAlex 26个固定field之一）
            //   放宽：也接受邻近 field（人机交互/控制可能归在别处）
            boolean isCs = fields.stream().anyMatch(f ->
                   f.equals("Computer Science")
                || f.contains("Computer")
                || f.equals("Artificial Intelligence"));
            if (!isCs) continue;  // 非CS field 直接跳过（彻底解决医学/交通教授混入）

            // ★ 研究方向 = subfield（规范名）优先，附加最相关的 topic 名
            //   存储格式："中文翻译 英文subfield"，让中英文检索都能匹配
            List<String> interests = new ArrayList<>();
            for (String sf : subfields) {
                if (interests.size() >= 4) break;
                interests.add(translateOATopic(sf));
            }
            // 补充1个最具体的 topic 名（近年新方向，如 Large Language Models）
            for (String tn : topicNames) {
                if (interests.size() >= 5) break;
                String translated = translateOATopic(tn);
                if (!interests.contains(translated)) interests.add(translated);
            }

            // ★ counts_by_year
            int activeYear = 0;
            StringBuilder cbyJson = new StringBuilder("[");
            Matcher cbyM = cbyPat.matcher(seg);
            if (cbyM.find()) {
                String[] entries = cbyM.group(1).split("\\},?\\s*\\{");
                for (String entry : entries) {
                    Matcher ym = yearPat.matcher(entry);
                    Matcher wym = wkYPat.matcher(entry);
                    Matcher cym = ctYPat.matcher(entry);
                    if (!ym.find()) continue;
                    int yr = Integer.parseInt(ym.group(1));
                    int wc = wym.find() ? Integer.parseInt(wym.group(1)) : 0;
                    int cc = cym.find() ? Integer.parseInt(cym.group(1)) : 0;
                    if (yr < 2015 || yr > 2030) continue;
                    if (cbyJson.length() > 1) cbyJson.append(",");
                    cbyJson.append("{\"y\":").append(yr)
                           .append(",\"w\":").append(wc)
                           .append(",\"c\":").append(cc).append("}");
                    if (wc > 0 && yr > activeYear) activeYear = yr;
                }
            }
            cbyJson.append("]");

            // ORCID 优先作为 profile URL
            String profileUrl = "https://openalex.org/A" + seg.substring(0,
                Math.min(seg.indexOf('"'), 20)).replaceAll("[^0-9]","");
            Matcher om = orcidPat.matcher(seg);
            if (om.find()) profileUrl = om.group(1);

            String title = cited >= 5000 ? "教授"
                : cited >= 800 ? "副教授" : "讲师/研究员";

            Teacher t = new Teacher(name, univName, "CS（OpenAlex）", profileUrl);
            t.setTitle(title);
            t.setResearchAreas(interests.isEmpty() ? null : String.join("；", interests));
            t.setCitedCount(cited);
            t.setWorksCount(works);
            t.setActiveYear(activeYear);
            t.setCountsByYear(cbyJson.length() > 2 ? cbyJson.toString() : null);
            if (db.upsertTeacher(t)) count++;
        }
        return count;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Strategy L: OpenAlex API — 原版（单页50条，作为 L0 失败时的降级兜底）
    //   - 完全免费，先查机构 ROR ID，再按 CS concept 精准过滤
    //   - 接口：https://api.openalex.org/
    // ════════════════════════════════════════════════════════════════════════

    public int scrapeViaOpenAlex(String univName) {
        int count = 0;
        String engName = UNIV_EN_NAMES.getOrDefault(univName, univName);
        HttpClient hc = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
        try {
            // Step 1: 获取学校 OpenAlex 机构 ID
            String instUrl = "https://api.openalex.org/institutions?search="
                + URLEncoder.encode(engName, "UTF-8")
                + "&filter=country_code:CN&per-page=1&mailto=baoyan@edu.cn";
            HttpResponse<String> instResp = hc.send(
                HttpRequest.newBuilder().uri(URI.create(instUrl))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", engine.ua()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (instResp.statusCode() != 200) return 0;

            // 提取 OpenAlex institution ID（形如 https://openalex.org/I123456）
            Pattern instPat = Pattern.compile("\"id\"\\s*:\\s*\"(https://openalex\\.org/I[0-9]+)\"");
            Matcher instM = instPat.matcher(instResp.body());
            if (!instM.find()) {
                log.debug("  [L] OpenAlex \u627e\u4e0d\u5230\u673a\u6784 ID: {}", engName);
                return 0;
            }
            String instId = instM.group(1);
            log.debug("  [L] OpenAlex \u673a\u6784 ID: {} \u2192 {}", engName, instId);

            // Step 2: 按机构 + CS concept（C41008148）查研究者，按引用数降序
            String authUrl = "https://api.openalex.org/authors"
                + "?filter=last_known_institutions.id:" + URLEncoder.encode(instId, "UTF-8")
                + ",concepts.id:C41008148"
                + "&sort=cited_by_count:desc&per-page=50&mailto=baoyan@edu.cn";

            Thread.sleep(200);
            HttpResponse<String> authResp = hc.send(
                HttpRequest.newBuilder().uri(URI.create(authUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", engine.ua()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (authResp.statusCode() != 200) return 0;
            String body = authResp.body();

            // ★ 直接复用主力解析逻辑（field/subfield 结构化 + counts_by_year），保证数据一致干净
            count += parseOpenAlexBatchPage(body, univName);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.debug("  [L] OpenAlex \u5f02\u5e38 [{}]: {}", univName, ex.getMessage());
        }
        log.debug("  [L] OpenAlex({}) \u2192 {}", univName, count);
        return count;
    }


    // ════════════════════════════════════════════════════════════════════════
    // 交叉学科院校专用搜索策略
    //
    // 背景：传媒大学/政法大学/财经大学等高校虽以非CS见长，但普遍设有：
    //   传媒类  → 数字媒体技术、计算机、人工智能传播
    //   法学类  → 信息管理、网络安全、电子政务、法律科技
    //   财经类  → 金融科技、信息管理、大数据、量化金融
    //   外语类  → 计算语言学、自然语言处理、机器翻译
    //   体育类  → 体育大数据、智能运动、运动信息
    //   农业类  → 智慧农业、农业信息化、生物信息学
    //   医科类  → 医学信息学、健康大数据、生物信息学
    // ════════════════════════════════════════════════════════════════════════

    /** note 关键词 → 该类院校可能有的 CS 交叉方向搜索词 */
    public static final Map<String, List<String>> CROSS_DOMAIN_TERMS;
    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("传媒", Arrays.asList(
            "数字媒体技术", "计算机", "人工智能",
            "数据科学", "广播电视工程", "信息管理"));
        m.put("新闻", Arrays.asList(
            "数字媒体", "计算机", "人工智能传播",
            "信息技术", "数据分析"));
        m.put("法学", Arrays.asList(
            "信息管理", "网络安全", "电子政务",
            "法律科技", "司法信息", "数字法治"));
        m.put("政法", Arrays.asList(
            "信息管理", "网络安全", "电子政务",
            "数据治理", "计算机应用"));
        m.put("财经", Arrays.asList(
            "金融科技", "信息管理", "大数据",
            "金融信息", "电子商务", "量化金融"));
        m.put("经贸", Arrays.asList(
            "金融科技", "信息管理", "大数据",
            "电子商务", "数字经济", "计算机"));
        m.put("外语", Arrays.asList(
            "计算语言学", "自然语言处理",
            "信息管理", "人机交互", "机器翻译"));
        m.put("体育", Arrays.asList(
            "体育大数据", "运动信息", "智能体育",
            "计算机", "信息管理"));
        m.put("农业", Arrays.asList(
            "智慧农业", "农业信息化", "生物信息学",
            "计算机", "人工智能", "大数据"));
        m.put("医科", Arrays.asList(
            "医学信息学", "健康大数据", "生物信息",
            "计算机", "人工智能医疗"));
        m.put("中医", Arrays.asList(
            "中医信息化", "健康大数据", "生物信息",
            "计算机", "人工智能"));
        m.put("药学", Arrays.asList(
            "生物信息学", "健康大数据", "计算机",
            "人工智能药物", "化学信息学"));
        m.put("矿业", Arrays.asList(
            "计算机", "信息管理", "大数据",
            "人工智能", "智能矿山"));
        m.put("航海", Arrays.asList(
            "计算机", "信息管理", "大数据",
            "人工智能", "船舶信息"));
        CROSS_DOMAIN_TERMS = Collections.unmodifiableMap(m);
    }

    /**
     * 根据学校 note 字段判断是否为交叉学科院校，并返回对应的 CS 交叉搜索词。
     * 返回空列表 = 纯 CS / 工科院校，用标准爬虫策略即可。
     */
    public static List<String> getCrossDomains(String note) {
        if (note == null || note.isBlank()) return List.of();
        for (Map.Entry<String, List<String>> e : CROSS_DOMAIN_TERMS.entrySet()) {
            if (note.contains(e.getKey())) return e.getValue();
        }
        return List.of();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 交叉学科专用爬取：针对特定领域关键词做定向百度搜索
    // 调用时机：ScraperService.scrapeUnknown 在直连 edu.cn 全部失败后，
    //           先判断是否为交叉院校，是则调用本方法（而非通用 scrapeFromIndirectSources）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 交叉学科院校的专用间接搜索。
     *
     * 策略比通用策略更细：
     *   C-1  定向百度搜索（每个交叉方向词分别构造查询）
     *   C-2  AMiner API（相对通用，但用交叉领域英文词过滤）
     *   C-3  Semantic Scholar（AI/ML 交叉方向补充）
     *   C-4  学校研究生招生网关键词搜索（保研/导师/研究方向）
     *
     * @param univName   学校名称
     * @param domains    交叉方向关键词列表（如 ["数字媒体技术","信息管理"]）
     */
    public int scrapeFromCrossDisciplinary(String univName, List<String> domains) {
        int count = 0;
        log.info("  [C] 交叉学科层级爬取: {} 方向={}", univName, domains);

        // C-1: 按每个交叉方向分别做定向百度搜索
        for (String domain : domains) {
            if (count >= 40) break;
            List<String> queries = Arrays.asList(
                String.format("\"%s\" %s 导师 研究方向 简介", univName, domain),
                String.format("\"%s\" %s 教授 副教授 招生", univName, domain),
                String.format("\"%s\" %s 保研 推免 导师组", univName, domain)
            );
            count += scrapeViaBaiduWithQueries(univName, queries, 10);
        }
        log.info("  [C-1] 定向百度搜索: {} 位", count);

        // C-2: AMiner（覆盖中国学者，对交叉学科也有收录）
        if (count < 20) {
            count += scrapeViaAminer(univName);
            log.info("  [C-2] AMiner: {} 位背景", count);
        }

        // C-3: Semantic Scholar（补充发表过顶会论文的交叉方向学者）
        if (count < 20) {
            count += scrapeViaSemanticScholar(univName);
            log.info("  [C-3] S2: {} 位背景", count);
        }

        // C-4: 研招网/百度文库中的该校导师列表
        if (count < 10) {
            List<String> fallbackQueries = Arrays.asList(
                String.format("\"%s\" 硕士导师 计算机 研究方向", univName),
                String.format("\"%s\" 导师信息 信息技术 软件", univName),
                String.format("\"%s\" 导师目录 数据科学 信息安全", univName)
            );
            count += scrapeViaBaiduWithQueries(univName, fallbackQueries, 15);
            log.info("  [C-4] 研招网补充: {} 位背景", count);
        }

        log.info("  [C] 交叉学科爬取完成: {} 位", count);
        return count;
    }

    // ── ★ Strategy M: 百度学术搜索 ──────────────────────────────────────────

    /**
     * 百度学术（xueshu.baidu.com）对中国学者的收录远比 DBLP/S2 全面，
     * 尤其是发表中文期刊、国内会议的末流 211 教师，英文学术库几乎查不到，
     * 但百度学术有结构化的学者页面（含姓名、机构、研究方向）。
     *
     * 接口：xueshu.baidu.com/s?wd=<query>&tn=SE_baiduxueshu_c1gjeupa
     */
    private int scrapeViaBaiduXueshu(String univName) {
        int count = 0;
        // 百度学术搜索词：覆盖几个常见 CS 子方向
        List<String> queries = Arrays.asList(
            univName + " 计算机 教授",
            univName + " 人工智能 副教授",
            univName + " 软件工程 讲师",
            univName + " 信息安全 研究员"
        );
        // 提取 "作者：XXX；机构：YYY" 或 "作者：XXX (YYY)" 形式
        Pattern authorPat  = Pattern.compile("作者[：:]\\s*([\\u4e00-\\u9fff]{2,4}(?:[,，、；;][\\u4e00-\\u9fff]{2,4}){0,9})");
        Pattern titlePat   = Pattern.compile("(教授|副教授|讲师|研究员|助理教授)");
        Pattern areaPat    = Pattern.compile("研究(?:方向|领域|兴趣)[：:]\\s*([^。\\.\\n]{4,60})");
        Set<String> seenNames = new LinkedHashSet<>();

        for (String q : queries) {
            if (count >= 20) break;
            try {
                String searchUrl = "https://xueshu.baidu.com/s?wd="
                    + URLEncoder.encode(q, "UTF-8")
                    + "&tn=SE_baiduxueshu_c1gjeupa&sc_from=baiduyuan&medium=1";
                Document bd = Jsoup.connect(searchUrl)
                    .userAgent(engine.ua())
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .referrer("https://xueshu.baidu.com/")
                    .timeout(12000).get();

                // 从每条搜索结果摘要里提取姓名 + 职称 + 研究方向
                for (Element item : bd.select(".sc_res_item, .result, [class*=result]")) {
                    String itemText = item.text();
                    if (!itemText.contains(univName.substring(0, 2))) continue;

                    // 提取作者姓名列表
                    Matcher am = authorPat.matcher(itemText);
                    if (!am.find()) continue;
                    String[] names = am.group(1).split("[,，、；;]");

                    // 提取职称（取第一个匹配）
                    Matcher tm = titlePat.matcher(itemText);
                    String title = tm.find() ? tm.group(1) : "教师";

                    // 提取研究方向
                    Matcher rm = areaPat.matcher(itemText);
                    String area = rm.find() ? rm.group(1).trim() : "";

                    for (String rawName : names) {
                        String name = rawName.trim();
                        if (name.length() < 2 || name.length() > 4) continue;
                        if (!name.matches("[\\u4e00-\\u9fff]{2,4}")) continue;
                        if (!seenNames.add(name + "@" + univName)) continue;

                        // profileUrl 用 AMiner 个人页面（比搜索URL有意义）
                        String profileUrl = "https://www.aminer.cn/search?q="
                            + URLEncoder.encode(name + " " + univName, "UTF-8");
                        Teacher t = new Teacher(name, univName, "CS（百度学术）", profileUrl);
                        t.setTitle(title);
                        t.setResearchAreas(area);
                        if (db.upsertTeacher(t)) {
                            count++;
                            log.info("    M 百度学术命中: {} {} {}", name, title, area.isEmpty() ? "" : "← " + area);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("  M 百度学术异常 [{}]: {}", q, e.getMessage());
            }
        }
        return count;
    }

    // ── ★ Strategy N-zhihu: 知乎 + 保研论坛 ─────────────────────────────────

    /**
     * 知乎（zhihu.com）和保研论坛（小木虫/硕士研/一亩三分地）上有大量学生撰写的
     * 导师介绍帖、保研经验贴，其中常常包含完整的导师姓名列表和研究方向，
     * 且这些网站对爬虫几乎没有限制。
     *
     * 命中类型示例：
     *   - 知乎："延边大学计算机学院有哪些值得推荐的导师？"
     *   - 小木虫："延边大学计算机系2025年导师汇总"
     *   - 硕士研："延边大学预推免信息汇总"
     */
    private int scrapeViaZhihuAndForums(String univName) {
        int count = 0;
        List<String> queries = Arrays.asList(
            "site:zhihu.com \"" + univName + "\" 导师 计算机 研究方向",
            "site:zhihu.com \"" + univName + "\" 保研 人工智能",
            "site:shuoshiyan.com \"" + univName + "\" 计算机 导师",
            "\"" + univName + "\" 导师名单 计算机 人工智能 site:wenku.baidu.com OR site:docin.com"
        );
        // 从散文中提取姓名：匹配"张三教授""李四副教授""王五研究员"等模式
        Pattern inlinePat  = Pattern.compile(
            "([\\u4e00-\\u9fff]{2,4})\\s*(教授|副教授|讲师|研究员|助理教授|博导|硕导)");
        // 也匹配"导师：XXX，XXX"列表
        Pattern listPat    = Pattern.compile(
            "(?:导师|教师|师资)[：:]\\s*([\\u4e00-\\u9fff]{2,4}(?:[,，、；;][\\u4e00-\\u9fff]{2,4}){0,15})");
        // 研究方向提取
        Pattern areaPat    = Pattern.compile(
            "研究(?:方向|领域|兴趣)[：:]\\s*([^。\\.\\n]{4,60})");
        Set<String> seenNames = new LinkedHashSet<>();
        Set<String> visited   = new LinkedHashSet<>();

        for (String q : queries) {
            if (count >= 15) break;
            try {
                String searchUrl = "https://www.baidu.com/s?wd="
                    + URLEncoder.encode(q, "UTF-8") + "&rn=10";
                Document bd = Jsoup.connect(searchUrl)
                    .userAgent(engine.ua())
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .referrer("https://www.baidu.com/")
                    .timeout(12000).get();

                for (Element a : bd.select(".result h3 a, .c-container .t a")) {
                    String href = a.absUrl("href");
                    if (href.isEmpty() || href.contains("baidu.com")) continue;
                    if (!visited.add(href)) continue;
                    boolean targetSite = href.contains("zhihu.com") || href.contains("shuoshiyan.com")
                        || href.contains("muzi.net") || href.contains("1point3acres.com")
                        || href.contains("wenku.baidu.com") || href.contains("docin.com");
                    if (!targetSite) continue;

                    Document page = engine.fetchRobust(href);
                    if (page == null) continue;
                    String fullText = page.text();
                    if (!fullText.contains(univName.substring(0, 2))) continue;

                    // ① 导师列表模式："导师：张三，李四，王五"
                    Matcher lm = listPat.matcher(fullText);
                    while (lm.find()) {
                        for (String rawName : lm.group(1).split("[,，、；;]")) {
                            String name = rawName.trim();
                            if (name.length() < 2 || name.length() > 4) continue;
                            if (!name.matches("[\\u4e00-\\u9fff]{2,4}")) continue;
                            if (!seenNames.add(name + "@" + univName)) continue;
                            Teacher t = new Teacher(
                                name, univName, "CS（论坛）", href + "#" + name);
                            t.setTitle("教师");
                            if (db.upsertTeacher(t)) { count++; log.info("    N-list 命中: {}", name); }
                        }
                    }

                    // ② 行内职称模式："张三教授研究机器学习"
                    Matcher im = inlinePat.matcher(fullText);
                    while (im.find()) {
                        String name  = im.group(1).trim();
                        String title = im.group(2).trim();
                        if (!seenNames.add(name + "@" + univName)) continue;
                        // 在匹配位置附近尝试提取研究方向（前后 100 字符窗口）
                        int start = Math.max(0, im.start() - 50);
                        int end   = Math.min(fullText.length(), im.end() + 100);
                        String ctx = fullText.substring(start, end);
                        Matcher rm = areaPat.matcher(ctx);
                        String area = rm.find() ? rm.group(1).trim() : "";
                        Teacher t = new Teacher(
                            name, univName, "CS（论坛）", href + "#" + name);
                        t.setTitle(title);
                        t.setResearchAreas(area);
                        if (db.upsertTeacher(t)) { count++; log.info("    N-inline 命中: {} {} {}", name, title, area); }
                    }
                }
            } catch (Exception e) {
                log.debug("  N-zhihu 异常 [{}]: {}", q, e.getMessage());
            }
        }
        return count;
    }

    /**
     * 辅助方法：给定查询词列表，在百度上搜索并提取教师信息。
     * 和 scrapeViaBaiduUnrestricted 逻辑相同，但接受外部传入的 queries，
     * 方便各种策略复用。
     */
    private int scrapeViaBaiduWithQueries(String univName, List<String> queries, int limit) {
        int count = 0;
        Set<String> visited = new LinkedHashSet<>();
        for (String q : queries) {
            if (count >= limit) break;
            for (int pn = 0; pn <= 10; pn += 10) {
                if (count >= limit) break;
                try {
                    String searchUrl = "https://www.baidu.com/s?wd="
                        + URLEncoder.encode(q, "UTF-8") + "&pn=" + pn;
                    Document bd = Jsoup.connect(searchUrl)
                        .userAgent(engine.ua())
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .referrer("https://www.baidu.com/")
                        .timeout(12000).get();

                    for (Element a : bd.select(".result h3 a, .c-container .t a")) {
                        String href = a.absUrl("href");
                        if (href.isEmpty() || href.contains("baidu.com")) continue;
                        if (!visited.add(href)) continue;

                        Element box = a.closest(".result, .c-container");
                        String snippet = box != null ? box.text() : a.text();
                        boolean likely = snippet.contains("导师") || snippet.contains("教授")
                            || snippet.contains("副教授") || snippet.contains("研究方向")
                            || snippet.contains("师资") || FetchEngine.FACULTY_KW.matcher(snippet).find();
                        if (!likely) continue;

                        String fetchUrl = href;
                        if (href.contains(".edu.cn")) {
                            try {
                                fetchUrl = "https://webcache.baidu.com/search?q=cache:"
                                    + URLEncoder.encode(href, "UTF-8") + "&hl=zh-CN";
                            } catch (Exception ignored) {}
                        }
                        Document page = engine.fetchRobust(fetchUrl);
                        if (page == null && !fetchUrl.equals(href)) page = engine.fetchRobust(href);
                        if (page == null) continue;

                        String pt = engine.detectPageType(page, href);
                        if ("notice".equals(pt) || "plan".equals(pt)) continue;

                        int n = engine.scrapeTeachersFromPage(fetchUrl, univName, "交叉导师");
                        if (n > 0) {
                            count += n;
                            log.debug("    C 命中 {} 位 ← {}", n, href);
                        }
                    }
                } catch (Exception e) {
                    log.debug("    C 百度搜索异常 [{}]: {}", q, e.getMessage());
                }
            }
        }
        return count;
    }

}