package com.baoyan.engine;

import com.baoyan.model.Teacher;
import com.baoyan.model.SchoolInfo;
import com.baoyan.db.DatabaseService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.*;
import java.net.http.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * FetchEngine — HTTP抓取 + HTML解析 + 教师提取 引擎（增强版）
 *
 * 策略总览：
 *   策略1  含职称关键词的链接上下文
 *   策略2  URL特征匹配（含 /info/\d+/\d+ JW系统路径）
 *   策略3  表格行（tr含职称文字+链接）
 *   策略4  纯姓名链接（2-5汉字锚文字）
 *   策略5  定制CSS选择器
 *   策略6  DOM重复模式检测（MDR算法启发）— NEW
 *   策略7  Script标签JSON挖掘（Vue/React内嵌数据）— NEW
 *
 * Profile解析增强：
 *   - DL/DT/DD 结构化提取（国内最常见布局）
 *   - TABLE th/td 配对提取
 *   - JSON-LD + meta description
 *   - 段落兜底
 *   - 质量门控放宽（姓名有效即保存，字段空只发警告）
 *
 * Fetch增强：
 *   - Wayback Machine 兜底（服务器不稳时）
 *   - 编码自适应（GBK/GB18030）
 *   - 更宽分页URL模板（pageNo/curPage/_N.htm）
 */
@Service
public class FetchEngine {

    private static final Logger log = LoggerFactory.getLogger(FetchEngine.class);

    @Autowired DatabaseService db;

    @Value("${scraper.request-delay-min:600}")  long delayMin;
    @Value("${scraper.request-delay-max:1500}") long delayMax;
    @Value("${scraper.max-retries:2}")          int  maxRetries;
    @Value("${scraper.timeout:10000}")          int  timeout;
    @Value("${scraper.profile-workers:4}")      int  profileWorkers;
    @Value("${scraper.wayback-fallback:true}")  boolean waybackFallback;

    // ════════════════════════════════════════════════════════════════════════
    // 常量
    // ════════════════════════════════════════════════════════════════════════

    // jsoup 1.16+ 删除了 validateTLSCertificates()，改用自定义 SSLSocketFactory
    private static final SSLSocketFactory TRUST_ALL_SSL;
    static {
        SSLSocketFactory sf;
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }}, new SecureRandom());
            sf = sc.getSocketFactory();
        } catch (Exception e) {
            sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
        TRUST_ALL_SSL = sf;
    }

    static final Random RNG = new Random();

    static final List<String> UAS = Arrays.asList(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.5; rv:125.0) Gecko/20100101 Firefox/125.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 OPR/110.0.0.0",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:124.0) Gecko/20100101 Firefox/124.0",
        "Mozilla/5.0 (compatible; Baiduspider/2.0; +http://www.baidu.com/search/spider.html)"
    );

    // 职称（严格 + 宽松）
    static final Pattern TITLE_PAT = Pattern.compile(
        "(正教授|特聘教授|长聘教授|讲席教授|冠名教授|教授|副教授|助理教授|讲师|助理讲师" +
        "|研究员|副研究员|助理研究员|高级工程师|正高级工程师|工程师" +
        "|博士生导师|硕士生导师|博导|硕导)");

    private static final Pattern TITLE_PAT_LOOSE = Pattern.compile(
        "(Professor|Associate Professor|Assistant Professor|Lecturer|Researcher" +
        "|Research Fellow|Postdoc|特聘研究员|青年研究员|青年教授|四青人才|青年长江|青年千人)");

    private static final Pattern EMAIL_PAT = Pattern.compile(
        "[a-zA-Z0-9._%+\\-]{1,60}@[a-zA-Z0-9.\\-]{1,40}\\.[a-zA-Z]{2,10}");

    // 研究方向（多模式）
    static final List<Pattern> RESEARCH_PATS = Arrays.asList(
        Pattern.compile("研究方向[：:：]\\s*(.{8,300}?)(?=[。；;\\n\\r]|$)"),
        Pattern.compile("研究领域[：:：]\\s*(.{8,300}?)(?=[。；;\\n\\r]|$)"),
        Pattern.compile("主要方向[：:：]\\s*(.{8,300}?)(?=[。；;\\n\\r]|$)"),
        Pattern.compile("主要研究[方向领域方面][：:：]?\\s*(.{8,300}?)(?=[。；;\\n\\r]|$)"),
        Pattern.compile("研究兴趣[：:：]\\s*(.{8,300}?)(?=[。；;\\n\\r]|$)"),
        Pattern.compile("研究内容[：:：]\\s*(.{8,300}?)(?=[。；;\\n\\r]|$)"),
        Pattern.compile("(?i)Research\\s+Interests?\\s*[：:：]?\\s*(.{8,400}?)(?=[.;\\n\\r]|$)"),
        Pattern.compile("(?i)Research\\s+Areas?\\s*[：:：]?\\s*(.{8,400}?)(?=[.;\\n\\r]|$)"),
        Pattern.compile("(?i)Specializations?\\s*[：:：]?\\s*(.{8,400}?)(?=[.;\\n\\r]|$)"),
        Pattern.compile("个人简介[：:：]?\\s*(.{15,400}?)(?=[。\\n]|$)")
    );

    // 结构化标签匹配（DL/TABLE提取用）
    private static final Pattern LABEL_RESEARCH = Pattern.compile(
        "研究方向|研究领域|研究兴趣|主要方向|主要研究|Research\\s*(?:Area|Interest)");
    private static final Pattern LABEL_TITLE = Pattern.compile(
        "^(?:职称|职务|岗位|职位|技术职称|专业职务)$");
    private static final Pattern LABEL_EMAIL = Pattern.compile(
        "(?i)^(?:邮箱|邮件|电子邮件|email|e-mail|mail)$");
    private static final Pattern LABEL_DEPT = Pattern.compile(
        "^(?:学院|部门|系所|院系|单位)$");

    // ★ 师资列表页面识别（供 detectPageType 计分使用）
    public static final Pattern FACULTY_KW = Pattern.compile(
        "师资队伍|师资力量|师资简介|师资介绍|教师队伍|教职工|专任教师|全体教师" +
        "|教师简介|教师列表|教师信息|导师简介|导师队伍|教师风采" +
        "|(?i)faculty|teaching\\s+staff|academic\\s+staff" +
        "|(?i)our\\s+(?:faculty|team|people|staff)|professors\\s+and\\s+staff");

    // ★ NEW — 实验室/课题组识别
    public static final Pattern LAB_KW = Pattern.compile(
        "课题组|研究组|实验室|研究中心|联合实验室|Laboratory|Lab\\b|Research\\s+Group|Research\\s+Lab");
    private static final Pattern LABEL_LAB = Pattern.compile(
        "^(?:课题组|研究组|研究团队|所在团队|所属(?:实验室|课题组)|实验室|团队名称)$");
    static final Pattern RECRUIT_KW = Pattern.compile(
        "正在招收|本年度招[收生]|招收推免|接收保研|欢迎[联系报考优]|招募学生|诚招|" +
        "招收硕[士博]|open\\s+position|looking\\s+for\\s+student|joining\\s+(?:our\\s+)?group|" +
        "welcome.*student|欢迎.*联系", Pattern.CASE_INSENSITIVE);

    // ★ NEW — 通知/公告检测
    public static final Pattern NOTICE_KW = Pattern.compile(
        "预推免|夏令营|推免通知|保研通知|招生通知|招生简章|接收推荐免试|研究生招生公告" +
        "|学术夏令营|报名须知|招生公告|导师招生计划|接收调剂|拟接收|推免报名");
    public static final Pattern PLAN_KW = Pattern.compile(
        "导师招生计划|意向导师|招收名额|拟招收|开放课题|接受简历|招生计划(?:表|汇总)");

    // ★ NEW — 截止日期提取（含全角月/日）
    static final Pattern DEADLINE_PAT = Pattern.compile(
        "(202[3-9]|20[3-4]\\d)[年/\\-](1[0-2]|0?[1-9])[月/\\-](3[01]|[12]\\d|0?[1-9])日?" +
        "|(202[3-9]|20[3-4]\\d)\\.(1[0-2]|0?[1-9])\\.(3[01]|[12]\\d|0?[1-9])");

    // ★ NEW — 招生名额 / GPA / 排名要求
    static final Pattern QUOTA_PAT = Pattern.compile(
        "(?:拟招收|计划招收|招收|接收|接受)[^，。\\n]{0,15}?([0-9０-９]+)[\\s]*[人名]" +
        "|([0-9]+)[\\s]*(?:个)?(?:招生)?名额");
    static final Pattern GPA_PAT  = Pattern.compile(
        "(?i)GPA[\\s]*[≥>≧=]{1,2}[\\s]*([0-9]+\\.?[0-9]*)");
    static final Pattern RANK_PAT = Pattern.compile(
        "排名?前[\\s]*([0-9]+)[%％]|成绩[^，。\\n]{0,10}前([0-9]+)[%％]?");

    // 姓名清洗正则
    private static final Pattern NAME_STRIP = Pattern.compile(
        "(正教授|特聘教授|长聘教授|教授|副教授|讲师|研究员|副研究员|博导|硕导|Dr\\.|Prof\\.)" +
        "|[（(][^）)]{0,20}[）)]|[A-Za-z0-9\\s/｜|，,。、·]");

    // 纯中文姓名验证（2-5汉字）
    public static final Pattern CN_NAME_PAT = Pattern.compile("^[\\u4e00-\\u9fff]{2,5}$");

    // ★ 机构名称模式（排除学校/学院/研究所等被当成人名）
    static final Pattern INSTITUTION_NAME_PAT = Pattern.compile(
        ".*(大学|学院|学部|学系|中心|研究所|研究院|实验室|书院|附属|集团|公司|医院|委员会)$");

    // ★ 关键修复1：新增 /info/\d+/\d+（金智JW系统）+ /article/\d+/（泛模式）
    static final Pattern PROFILE_URL_PAT = Pattern.compile(
        "(?i)/teacher[s]?/|/staff/|/person/|/faculty/|/people/|/jsdw/" +
        "|/ryjj/|/zyjs/|/jsxq/|/academic/|/member/|/profile/" +
        "|/cv/|/intro/|/homepage/|/personalPage|/rencai/" +
        "|[?&](id|uid|tid|pid|personId|teacherid|staffid|jsid|userId)=\\d+" +
        "|/detail/[a-zA-Z0-9]|/show/[a-zA-Z0-9]|/view/[a-zA-Z0-9]" +
        "|/info/\\d+/\\d+\\.htm"         // ★ 金智教务系统（江南大学等）
        + "|/article/\\d+/\\d+\\.htm"    // ★ 新闻/人物文章型
        + "|/teacher/\\d+"               // ★ 数字ID型
        + "|/jzg/\\d+");                 // ★ 教职工ID型

    // 导航词黑名单（防止把导航链接当教师姓名）
    // ★ 同时覆盖机构词、活动词、复合词，防止"基金校友""校企合作"等被误识别为人名
    static final Set<String> NAME_BLACKLIST = new HashSet<>(Arrays.asList(
        // 职称/身份词（不应独立作为姓名）
        "教授","副教授","讲师","研究员","博士","硕士","学生","教工","助教","导师","辅导员",
        // 机构/组织词
        "学院","中心","研究所","师资","副院长","院长","书记","党委","工会","委员","主任",
        "校友","基金","校企","科技","联盟","协会","学会","社团","委员会","理事会",
        "集团","公司","实验室","研究院","研究中心","工作室","教研室","研究部",
        // 活动/事件词
        "通知","公告","动态","活动","新闻","讲座","报告","会议","论坛","研讨","峰会",
        "招生","就业","合作","项目","基地","实验","期刊","荣誉","奖项","颁奖",
        // 导航/功能词
        "简介","介绍","联系","更多","查看","点击","详情","返回","课程","首页",
        "中文","版权","访问","下载","申请","报名","登录","注册","关于","搜索",
        "图书","资源","网站","工程","期刊","人才",
        // 复合黑名单（整体匹配）
        "师资队伍","通知公告","人才培养","科学研究","党的建设","学生天地",
        "学院新闻","讲座报告","标志成果","平台基地","科研团队","社会服务",
        "基本概况","理论学习","工作动态","组织机构","新闻动态","网站首页",
        "院长信箱","内设机构","学院领导","学院简介","师资招聘","兼聘导师",
        "信息公开","规章制度","对外交流","学科建设","毕业就业","关工委员",
        "本科生教育","研究生教育","研究生招生","本科招生","教师招聘",
        "优质资源","合作纽带","学科专项","校地合作","校企合作","产教融合"
    ));

    // ★ 非人名词根：含以下任一词根的字符串直接排除（substr 匹配，覆盖更多变体）
    static final Set<String> NAME_BADROOTS = new HashSet<>(Arrays.asList(
        "校友","基金","协会","联盟","委员","学会","论坛","研讨","招聘",
        "通知","公告","活动","合作","项目","奖励","颁奖","评优","表彰",
        "招生","就业","党委","工会","纪委","学生会","学联","组委"
    ));

    /** 统一姓名合法性检查：黑名单 + 词根过滤 */
    public static boolean isBadName(String n) {
        if (n == null || n.isBlank()) return true;
        if (NAME_BLACKLIST.contains(n)) return true;
        for (String root : NAME_BADROOTS) { if (n.contains(root)) return true; }
        return false;
    }

    // ════════════════════════════════════════════════════════════════════════
    // HTTP 层：限速 + Cookie + 编码自适应 + 多级降级
    // ════════════════════════════════════════════════════════════════════════

    private final ConcurrentHashMap<String, Long>              lastReqTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String,String>> cookieJar  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock>     hostLocks   = new ConcurrentHashMap<>();

    public String ua() { return UAS.get(RNG.nextInt(UAS.size())); }

    void rateLimit(String host) {
        ReentrantLock lock = hostLocks.computeIfAbsent(host, h -> new ReentrantLock(true));
        lock.lock();
        try {
            long now    = System.currentTimeMillis();
            long last   = lastReqTime.getOrDefault(host, 0L);
            long wait   = delayMin + (long)(RNG.nextDouble() * (delayMax - delayMin));
            long remain = wait - (now - last);
            if (remain > 0) {
                try { Thread.sleep(remain); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            lastReqTime.put(host, System.currentTimeMillis());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 核心抓取：多级降级
     *   HTTPS → HTTP（SSL错误时）
     *   UTF-8 → GBK → GB18030（编码自适应）
     *   403 → 换UA + Baidu Referer 重试
     *   429/503 → 指数退避
     *   ★ NEW: 所有重试失败后 → Wayback Machine 兜底
     */
    public Document fetchRobust(String url) {
        if (url == null || url.isBlank()) return null;
        String host;
        try { host = new URI(url).getHost(); } catch (Exception e) { host = "unknown"; }
        rateLimit(host);

        String[] candidates = url.startsWith("https://")
            ? new String[]{url, url.replace("https://", "http://")}
            : new String[]{url};

        for (String tryUrl : candidates) {
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    var conn = Jsoup.connect(tryUrl)
                        .userAgent(ua())
                        .header("Accept",          "text/html,application/xhtml+xml,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                        .header("Accept-Encoding", "gzip, deflate")
                        .header("Connection",      "keep-alive")
                        .header("Upgrade-Insecure-Requests", "1")
                        .sslSocketFactory(TRUST_ALL_SSL)
                        .timeout(timeout)
                        .followRedirects(true)
                        .ignoreContentType(false)
                        .maxBodySize(6 * 1024 * 1024);

                    Map<String,String> cookies = cookieJar.get(host);
                    if (cookies != null) conn.cookies(cookies);

                    var resp = conn.execute();
                    if (!resp.cookies().isEmpty())
                        cookieJar.merge(host, new HashMap<>(resp.cookies()), (a, b) -> { a.putAll(b); return a; });

                    int code = resp.statusCode();

                    if (code == 403) {
                        log.warn("  403 {} attempt={}, 换UA+Referer重试", tryUrl, attempt);
                        conn.userAgent(UAS.get((attempt + 3) % UAS.size()))
                            .referrer("https://www.baidu.com/s?wd=" + URLEncoder.encode(host, "UTF-8"));
                        resp  = conn.execute();
                        code  = resp.statusCode();
                    }
                    if (code == 429 || code == 503) {
                        long w = (long) Math.pow(2.5, attempt + 1) * 1000;
                        log.warn("  {} {} 退避{}ms", code, tryUrl, w);
                        Thread.sleep(w); continue;
                    }
                    if (code >= 400) { log.debug("  HTTP {} {}", code, tryUrl); break; }

                    byte[] raw = resp.bodyAsBytes();
                    Document doc = resp.parse();

                    // 编码检测：中文字符数过少时尝试 GBK / GB18030
                    long cnChars = doc.text().chars().filter(c -> c >= 0x4e00 && c <= 0x9fff).count();
                    if (cnChars < 5 && doc.text().length() > 300) {
                        for (String enc : new String[]{"GBK", "GB18030"}) {
                            try {
                                Document d2 = Jsoup.parse(new ByteArrayInputStream(raw), enc, tryUrl);
                                long cn2 = d2.text().chars().filter(c -> c >= 0x4e00 && c <= 0x9fff).count();
                                if (cn2 > cnChars) { doc = d2; log.debug("  编码降级→{} {}", enc, tryUrl); break; }
                            } catch (Exception ignored) {}
                        }
                    }

                    String t = doc.title();
                    if (t.contains("403") || t.contains("禁止") || t.contains("访问被拒绝"))
                        { log.warn("  页面拒绝: {}", tryUrl); break; }

                    return doc;

                } catch (java.io.IOException e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    if (msg.contains("SSL") || msg.contains("PKIX") || msg.contains("certificate"))
                        { log.debug("  SSL错误切HTTP: {}", tryUrl); break; }
                    if (attempt < maxRetries - 1) {
                        long w = (long) Math.pow(2, attempt + 1) * 1200;
                        log.debug("  IO({})→{}ms后重试 {}", msg, w, tryUrl);
                        try { Thread.sleep(w); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return null;
                } catch (Exception e) {
                    log.debug("  fetchRobust异常 {}: {}", tryUrl, e.getMessage()); break;
                }
            }
        }

        // ★ Wayback 兜底：跳过 edu.cn（连不上时 Wayback 也超慢，且抓到的是旧垃圾页，
        //   反而让 consecutiveFail 无法正常计数，导致快速失败机制失效）
        boolean isEduCn = url != null && url.contains(".edu.cn");
        if (waybackFallback && !isEduCn) {
            Document wb = fetchFromWayback(url);
            if (wb != null) return wb;
        }

        return null;
    }

    /**
     * ★ NEW: Wayback Machine 兜底
     * 通过 archive.org Availability API 获取最近快照。
     * 适用场景：服务器临时不稳定 / 旧页面被删除。
     */
    private Document fetchFromWayback(String url) {
        try {
            String apiUrl = "https://archive.org/wayback/available?url=" + URLEncoder.encode(url, "UTF-8");
            HttpClient hc = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> r = hc.send(
                HttpRequest.newBuilder().uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return null;
            Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"(https://web\\.archive\\.org[^\"]+)\"")
                .matcher(r.body());
            if (!m.find()) return null;
            String archiveUrl = m.group(1);
            log.info("  ★ Wayback Machine 兜底: {}", archiveUrl);
            return Jsoup.connect(archiveUrl).userAgent(ua()).timeout(timeout * 2)
                .sslSocketFactory(TRUST_ALL_SSL).get();
        } catch (Exception e) {
            log.debug("  Wayback fallback 失败 {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** HEAD 检测（子域名探测用） */
    public boolean headReachable(String url, HttpClient hc) {
        try {
            int code = hc.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", ua()).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
            return code < 400 || code == 403;
        } catch (Exception e) { return false; }
    }

    /** GET并计中文链接数（师资页路径探测用） */
    public boolean getHasCnLinks(String url, HttpClient hc, int min) {
        try {
            HttpResponse<String> r = hc.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", ua())
                    .header("Accept-Language","zh-CN,zh;q=0.9").GET().build(),
                HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 && countCnLinks(r.body()) >= min;
        } catch (Exception e) { return false; }
    }

    public long countCnLinks(String html) {
        return Jsoup.parse(html).select("a[href]").stream()
            .mapToLong(a -> a.text().chars().filter(c -> c >= 0x4e00 && c <= 0x9fff).count())
            .filter(n -> n >= 2).count();
    }

    public String resolveUrl(String base, String href) {
        try { return new URI(base).resolve(href).toASCIIString(); } catch (Exception e) { return href; }
    }

    public boolean skipHref(String href) {
        return href == null || href.isEmpty()
            || href.startsWith("#") || href.startsWith("javascript:")
            || href.startsWith("mailto:") || href.startsWith("tel:")
            || href.endsWith(".pdf") || href.endsWith(".doc")
            || href.endsWith(".docx") || href.endsWith(".xls")
            || href.endsWith(".xlsx") || href.endsWith(".zip")
            || href.endsWith(".rar") || href.endsWith(".png")
            || href.endsWith(".jpg") || href.endsWith(".gif");
    }

    // ════════════════════════════════════════════════════════════════════════
    // 解析层：profile / 姓名 / JSON
    // ════════════════════════════════════════════════════════════════════════

    /** 基础 profile 解析 */
    public Map<String,String> parseProfile(Document doc) {
        Map<String,String> info = new LinkedHashMap<>();
        String text = doc.text();

        Matcher m = TITLE_PAT.matcher(text);
        if (m.find()) info.put("title", m.group(1));
        else { Matcher ml = TITLE_PAT_LOOSE.matcher(text); if (ml.find()) info.put("title", ml.group(1)); }

        Matcher em = EMAIL_PAT.matcher(text);
        if (em.find()) info.put("email", em.group(0));

        for (Pattern p : RESEARCH_PATS) {
            Matcher rm = p.matcher(text);
            if (rm.find()) {
                String a = rm.group(1).strip().replaceAll("[\\n\\r]+", "；");
                info.put("researchAreas", a.length() > 400 ? a.substring(0,400) : a); break;
            }
        }

        Element gs = doc.selectFirst("a[href*='scholar.google']");
        if (gs != null) info.put("googleScholar", gs.absUrl("href"));
        Element orc = doc.selectFirst("a[href*='orcid.org']");
        if (orc != null) info.put("orcid", orc.absUrl("href"));
        return info;
    }

    /** 增强版 profile 解析：JSON-LD + meta + ★DL/TABLE结构 + 段落兜底 */
    public Map<String,String> parseProfileAdvanced(Document doc) {
        Map<String,String> info = parseProfile(doc);
        String text = doc.text();

        // JSON-LD
        for (Element sc : doc.select("script[type='application/ld+json']"))
            tryExtractFromJsonLd(sc.html(), info);

        // ★ NEW: DL/DT/DD 结构化提取（国内高校最常见，如 jiangnan.edu.cn）
        parseStructuredLabels(doc, info);

        // meta description
        Element metaDesc = doc.selectFirst("meta[name='description']");
        if (metaDesc != null) {
            String desc = metaDesc.attr("content");
            if (info.getOrDefault("researchAreas","").isEmpty() && desc.length() > 20) {
                for (Pattern p : RESEARCH_PATS) {
                    Matcher rm = p.matcher(desc);
                    if (rm.find()) {
                        String area = rm.group(1).strip();
                        info.put("researchAreas", area.length()>400 ? area.substring(0,400) : area); break;
                    }
                }
                if (info.getOrDefault("researchAreas","").isEmpty()) {
                    String cleaned = desc.replaceAll("^基本信息[^，。]{0,30}[，。]?","").trim();
                    if (cleaned.length() > 20)
                        info.put("researchAreas", cleaned.length()>400 ? cleaned.substring(0,400) : cleaned);
                }
            }
            if (info.getOrDefault("title","").isEmpty()) {
                Matcher tm = TITLE_PAT.matcher(desc);
                if (tm.find()) info.put("title", tm.group(1));
            }
        }

        // 补充非常规职称
        if (info.getOrDefault("title","").isEmpty()) {
            Matcher m2 = Pattern.compile("(特聘研究员|青年研究员|青年教授|引进人才|青年长江|青年千人|四青人才)").matcher(text);
            if (m2.find()) info.put("title", m2.group(1));
        }

        // 段落简介兜底（研究方向仍空时）
        if (info.getOrDefault("researchAreas","").isEmpty()) {
            for (Element p : doc.select("p,.intro,.profile,.jianjie,.summary,.content,.con")) {
                String pt = p.text().trim();
                if (pt.length() > 50 && pt.chars().filter(c -> c >= 0x4e00 && c <= 0x9fff).count() > 20) {
                    info.put("researchAreas", pt.length()>400 ? pt.substring(0,400) : pt); break;
                }
            }
        }

        // ★ NEW: 课题组/实验室名称（applyLabelValue 未捕到时用文本正则兜底）
        if (info.getOrDefault("labName","").isEmpty()) {
            Matcher lm = Pattern.compile(
                "(?:所在|所属|隶属)?(?:课题组|实验室|研究组|研究团队|team)[：:是为\\s]*([^\\n，,。；;（(]{4,60})")
                .matcher(text);
            if (lm.find()) {
                String lab = lm.group(1).trim();
                if (!INSTITUTION_NAME_PAT.matcher(lab).matches())
                    info.put("labName", lab.length() > 100 ? lab.substring(0,100) : lab);
            }
        }

        // ★ NEW: Google Scholar 链接（原 parseProfile 只捕了 scholar.google，这里补 SemanticScholar + DBLP）
        if (info.getOrDefault("googleScholar","").isEmpty()) {
            Element ss = doc.selectFirst("a[href*='semanticscholar.org']");
            if (ss != null) info.put("semanticScholar", ss.absUrl("href"));
            Element db2 = doc.selectFirst("a[href*='dblp.org']");
            if (db2 != null) info.put("dblp", db2.absUrl("href"));
        }
        Element orc = doc.selectFirst("a[href*='orcid.org']");
        if (orc != null) info.put("orcid", orc.absUrl("href"));

        // ★ NEW: 招生意愿检测（全文扫描 RECRUIT_KW）
        info.put("recruiting", RECRUIT_KW.matcher(text).find() ? "true" : "false");

        return info;
    }

    /**
     * ★ NEW: 结构化标签提取（DL/DT/DD + TABLE th/td + 自定义li标签）
     *
     * 覆盖场景：
     *   <dl><dt>研究方向</dt><dd>机器学习</dd></dl>
     *   <table><tr><th>职称</th><td>教授</td></tr></table>
     *   <li><span class="label">研究方向：</span><span>...</span></li>
     *   <p><strong>研究方向：</strong>机器学习</p>
     */
    private void parseStructuredLabels(Document doc, Map<String,String> info) {
        // DL/DT/DD
        for (Element dl : doc.select("dl")) {
            Elements dts = dl.select("dt");
            Elements dds = dl.select("dd");
            for (int i = 0; i < Math.min(dts.size(), dds.size()); i++) {
                applyLabelValue(dts.get(i).text().trim(), dds.get(i).text().trim(), info);
            }
        }

        // TABLE th/td 配对（每行取第一个th和第一个td）
        for (Element table : doc.select("table")) {
            for (Element tr : table.select("tr")) {
                // 尝试 th+td
                Element th = tr.selectFirst("th");
                Element td = tr.selectFirst("td");
                if (th != null && td != null) {
                    applyLabelValue(th.text().trim(), td.text().trim(), info);
                }
                // 尝试 td+td（第一列为标签，第二列为值）
                Elements tds = tr.select("td");
                if (tds.size() >= 2) {
                    applyLabelValue(tds.get(0).text().trim(), tds.get(1).text().trim(), info);
                }
            }
        }

        // ★ 常见行内标签格式：<li><span class="label">研究方向：</span>值</li>
        for (Element el : doc.select("li, p, div")) {
            // 只看"短前缀"型（整体文本<300字符），防止把整个段落当标签
            String full = el.text().trim();
            if (full.length() > 300) continue;
            for (Pattern lp : RESEARCH_PATS) {
                Matcher rm = lp.matcher(full);
                if (rm.find() && info.getOrDefault("researchAreas","").isEmpty()) {
                    String val = rm.group(1).strip();
                    if (val.length() >= 4)
                        info.put("researchAreas", val.length()>400 ? val.substring(0,400) : val);
                }
            }
        }

        // ★ <strong>/<b>/<span> 内的标签 + 后续文本节点
        for (Element strong : doc.select("strong, b, span.label, span.key")) {
            String label = strong.text().trim().replaceAll("[：:：\\s]+$","");
            String value = "";
            // 取后续文本节点
            org.jsoup.nodes.Node next = strong.nextSibling();
            while (next != null && value.isEmpty()) {
                if (next instanceof org.jsoup.nodes.TextNode) {
                    value = ((org.jsoup.nodes.TextNode) next).text().trim();
                } else if (next instanceof Element) {
                    value = ((Element) next).text().trim();
                }
                next = next.nextSibling();
            }
            if (!value.isEmpty()) applyLabelValue(label, value, info);
        }
    }

    /** 将标签-值对填充到 info（已有字段不覆盖） */
    private void applyLabelValue(String label, String value, Map<String,String> info) {
        if (label.isEmpty() || value.isEmpty()) return;
        label = label.replaceAll("[：:：\\s]+$", "");
        if (LABEL_RESEARCH.matcher(label).find() && info.getOrDefault("researchAreas","").isEmpty()) {
            info.put("researchAreas", value.length() > 400 ? value.substring(0,400) : value);
        } else if (LABEL_TITLE.matcher(label).find() && info.getOrDefault("title","").isEmpty()) {
            Matcher tm = TITLE_PAT.matcher(value);
            if (tm.find()) info.put("title", tm.group(1));
        } else if (LABEL_EMAIL.matcher(label).find() && info.getOrDefault("email","").isEmpty()) {
            Matcher em = EMAIL_PAT.matcher(value);
            if (em.find()) info.put("email", em.group());
        }
        // ★ NEW: 课题组/实验室归属
        else if (LABEL_LAB.matcher(label).find() && info.getOrDefault("labName","").isEmpty()) {
            if (!INSTITUTION_NAME_PAT.matcher(value).matches()) // 排除学院级机构名
                info.put("labName", value.length() > 100 ? value.substring(0,100) : value);
        }
    }

    private void tryExtractFromJsonLd(String json, Map<String,String> info) {
        Matcher jt = Pattern.compile("\"jobTitle\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (jt.find() && info.getOrDefault("title","").isEmpty()) info.put("title", jt.group(1));
        Matcher em = Pattern.compile("\"email\"\\s*:\\s*\"([^\"@]+@[^\"]+)\"").matcher(json);
        if (em.find() && info.getOrDefault("email","").isEmpty()) info.put("email", em.group(1));
        Matcher desc = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]{20,})\"").matcher(json);
        if (desc.find() && info.getOrDefault("researchAreas","").isEmpty()) info.put("researchAreas", desc.group(1));
    }

    /** JSON 列表响应中提取教师（REST API用） */
    List<Map<String,String>> parseJsonTeacherList(String json, String baseUrl) {
        List<Map<String,String>> result = new ArrayList<>();
        Pattern nameP  = Pattern.compile("\"(?:name|xm|jzmc|jsxm|teacherName)\"\\s*:\\s*\"([\\u4e00-\\u9fff]{2,5})\"");
        Pattern urlP   = Pattern.compile("\"(?:url|link|href|detailUrl|profileUrl|perUrl|homepage)\"\\s*:\\s*\"([^\"]+)\"");
        Pattern titleP = Pattern.compile("\"(?:title|zc|zwmc|jzlb|职称)\"\\s*:\\s*\"([^\"]{2,20})\"");
        Matcher block  = Pattern.compile("\\{[^{}]{20,600}\\}").matcher(json);
        while (block.find()) {
            String b = block.group();
            Matcher nm = nameP.matcher(b);
            if (!nm.find()) continue;
            String name = nm.group(1);
            if (isBadName(name)) continue;
            Map<String,String> e = new LinkedHashMap<>();
            e.put("name", name);
            Matcher um = urlP.matcher(b);
            if (um.find()) { String h = um.group(1); e.put("profileUrl", h.startsWith("http") ? h : resolveUrl(baseUrl, h)); }
            else e.put("profileUrl","");
            Matcher tm = titleP.matcher(b);
            if (tm.find()) e.put("title", tm.group(1));
            result.add(e);
        }
        return result;
    }

    /**
     * 姓名解析：hint优先，再从页面 title、h1/h2、meta keywords 提取
     * 支持"吴小俊-人工智能与计算机学院"格式（江南大学）
     */
    String resolveNameFromProfile(String hint, Document doc) {
        if (hint != null && CN_NAME_PAT.matcher(hint).matches() && !isBadName(hint))
            return hint;

        // title 中的"姓名-学院"格式
        for (String part : doc.title().trim().split("[-_|—/]")) {
            part = part.trim();
            if (CN_NAME_PAT.matcher(part).matches() && !isBadName(part)) return part;
        }

        // h1/h2/特定 class
        for (String sel : new String[]{"h1","h2","h3",".teacher-name",".name",".xm",
                                       ".person-name",".title-name",".staff-name",".jsxm",
                                       ".teacher_name",".personname",".author"}) {
            Element el = doc.selectFirst(sel);
            if (el == null) continue;
            String t = el.text().trim().split("[\\s　—\\-–|／/,，。：:]")[0].trim();
            if (CN_NAME_PAT.matcher(t).matches() && !isBadName(t)) return t;
        }

        // meta keywords（如"学院名,教授,吴小俊"）
        Element mkw = doc.selectFirst("meta[name='keywords']");
        if (mkw != null) {
            for (String kw : mkw.attr("content").split("[,，]")) {
                kw = kw.trim();
                if (CN_NAME_PAT.matcher(kw).matches() && !isBadName(kw)) return kw;
            }
        }

        // ★ NEW: 尝试首个 <img alt="姓名"> 或 <img title="姓名">（人物照片alt文字）
        for (Element img : doc.select("img[alt], img[title]")) {
            String alt = img.hasAttr("alt") ? img.attr("alt").trim() : img.attr("title").trim();
            if (CN_NAME_PAT.matcher(alt).matches() && !isBadName(alt)) return alt;
        }

        return hint != null ? hint : "";
    }

    /** 从锚文本清洗姓名 */
    String cleanName(String raw) {
        if (raw == null) return "";
        String n = NAME_STRIP.matcher(raw).replaceAll("").trim();
        return (n.length() >= 2 && CN_NAME_PAT.matcher(n).matches() && !isBadName(n)) ? n : "";
    }

    // ════════════════════════════════════════════════════════════════════════
    // 提取层：7策略 + BFS分页 + REST API
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 从一个师资列表页提取教师并写入数据库。
     *
     * 策略优先级（由高到低）：
     *   策略1  含职称关键词链接上下文
     *   策略2  URL特征匹配（含JW系统路径）
     *   策略3  表格行（tr+TITLE_PAT）
     *   策略4  纯姓名链接（2-5汉字）
     *   策略5  定制CSS选择器
     *   策略6  ★ DOM重复模式检测（MDR算法启发）
     *   策略7  ★ Script标签JSON挖掘
     *
     * 质量门控（放宽）：
     *   只要姓名合法即保存；title/researchAreas/email 缺失不丢弃，只记警告。
     */
    public int scrapeTeachersFromPage(String listUrl, String univName, String deptName) {
        if (listUrl.startsWith("API|")) return scrapeFromApiUrl(listUrl.substring(4), univName, deptName);

        Set<String>              seenUrls = new LinkedHashSet<>();
        List<Map<String,String>> entries  = new ArrayList<>();
        Set<String>              visited  = new LinkedHashSet<>();
        Queue<String>            queue    = new LinkedList<>();
        queue.add(listUrl);

        while (!queue.isEmpty() && visited.size() < 20) {
            String pageUrl = queue.poll();
            if (!visited.add(pageUrl)) continue;

            Document doc = fetchRobust(pageUrl);
            if (doc == null) { log.warn("  无法抓取: {}", pageUrl); continue; }
            doc.setBaseUri(pageUrl);
            int before = entries.size();

            // ── 策略1: 含职称关键词的链接上下文 ─────────────────────────────
            Pattern kw = Pattern.compile("(教授|副教授|讲师|研究员|博士|导师)");
            for (Element a : doc.select("a[href]")) {
                String href = a.absUrl("href");
                if (href.isEmpty()) href = resolveUrl(pageUrl, a.attr("href"));
                if (skipHref(href)) continue;
                String ctx = (a.parent() != null ? a.parent().text() : "") + a.text();
                if (!kw.matcher(ctx).find()) continue;
                if (!seenUrls.add(href)) continue;
                Map<String,String> m = new LinkedHashMap<>();
                m.put("name", cleanName(a.text())); m.put("profileUrl", href);
                entries.add(m);
            }

            // ── 策略2: URL 特征匹配（含JW系统/info/\d+/\d+ 路径） ─────────
            for (Element a : doc.select("a[href]")) {
                String href = a.absUrl("href");
                if (href.isEmpty()) href = resolveUrl(pageUrl, a.attr("href"));
                if (skipHref(href)) continue;
                if (PROFILE_URL_PAT.matcher(href).find() && seenUrls.add(href)) {
                    Map<String,String> m = new LinkedHashMap<>();
                    m.put("name", cleanName(a.text())); m.put("profileUrl", href);
                    entries.add(m);
                }
            }

            // ── 策略3: 表格行（tr含职称文字+链接） ──────────────────────────
            for (Element tr : doc.select("tr")) {
                Element a = tr.selectFirst("a[href]");
                if (a == null) continue;
                String href = a.absUrl("href");
                if (href.isEmpty()) href = resolveUrl(pageUrl, a.attr("href"));
                if (skipHref(href) || !TITLE_PAT.matcher(tr.text()).find()) continue;
                if (!seenUrls.add(href)) continue;
                String name = cleanName(a.text());
                if (name.isEmpty()) continue;
                Map<String,String> m = new LinkedHashMap<>();
                m.put("name", name); m.put("profileUrl", href);
                entries.add(m);
            }

            // ── 策略4: 纯姓名链接（2-5汉字 anchor text） ────────────────────
            for (Element a : doc.select("a[href]")) {
                String text = a.text().trim();
                String href = a.absUrl("href");
                if (href.isEmpty()) href = resolveUrl(pageUrl, a.attr("href"));
                if (skipHref(href)) continue;
                if (!CN_NAME_PAT.matcher(text).matches() || isBadName(text)) continue;

                boolean shortName  = text.length() <= 3;
                boolean hasNumId   = href.matches(".*\\d{3,}.*");
                boolean isListPage = href.contains("list.jsp") || href.contains("wbtreeid")
                    || href.contains("treeid=") || href.endsWith("list.htm")
                    || href.endsWith("index.htm") || href.endsWith("index.html");
                boolean profileUrl = PROFILE_URL_PAT.matcher(href).find();

                if ((shortName || (hasNumId && !isListPage) || profileUrl) && seenUrls.add(href)) {
                    Map<String,String> m = new LinkedHashMap<>();
                    m.put("name", text); m.put("profileUrl", href);
                    entries.add(m);
                }
            }

            // ── 策略5: 定制 CSS 选择器（常见高校模板） ──────────────────────
            for (String sel : new String[]{
                    "li.teacher a","div.teacher-item a","div.staff-item a",
                    ".jsxx a",".js-list a",".person-list a",".faculty-list a",
                    "div[class*=teacher] a","li[class*=teacher] a",
                    "td.jsxm a",".name a",".staff a",
                    ".teacher-card a",".profile-item a",".person-card a",
                    "article.teacher a","section[class*=faculty] a"}) {
                for (Element a : doc.select(sel)) {
                    String href = a.absUrl("href");
                    if (href.isEmpty()) href = resolveUrl(pageUrl, a.attr("href"));
                    if (skipHref(href) || !seenUrls.add(href)) continue;
                    String name = cleanName(a.text());
                    if (name.isEmpty()) {
                        Element parent = a.closest("div,li,tr,article");
                        if (parent != null) name = cleanName(parent.text());
                    }
                    Map<String,String> m = new LinkedHashMap<>();
                    m.put("name", name); m.put("profileUrl", href);
                    entries.add(m);
                }
            }

            // ── 策略6: DOM重复模式检测（MDR算法启发） ─────────────────────
            // 思路：找页面中重复出现（≥3次）的元素模板，其中包含中文姓名链接，
            // 提取该模板内的姓名+链接+行内职称（无需进入profile页）
            if (entries.size() - before < 3) {
                List<Map<String,String>> domEntries = extractByDomPattern(doc, pageUrl);
                for (Map<String,String> e : domEntries) {
                    String href = e.getOrDefault("profileUrl","");
                    if (!href.isEmpty() && seenUrls.add(href)) entries.add(e);
                    else if (href.isEmpty()) {
                        // 无URL时用姓名作key（行内全信息场景）
                        String key = "inline|" + e.get("name");
                        if (seenUrls.add(key)) entries.add(e);
                    }
                }
            }

            // ── 策略7: Script标签JSON挖掘（Vue/React/微前端） ───────────────
            // 扫描页面所有内联 <script> 块，寻找含姓名的JSON数组
            if (entries.size() - before < 3) {
                List<Map<String,String>> scriptEntries = extractFromScripts(doc, pageUrl);
                for (Map<String,String> e : scriptEntries) {
                    String href = e.getOrDefault("profileUrl","");
                    String key  = href.isEmpty() ? "script|"+e.get("name") : href;
                    if (seenUrls.add(key)) entries.add(e);
                }
            }

            log.info("    {} → 本页新增{} 累计{}", pageUrl, entries.size()-before, entries.size());

            // ── BFS 分页发现（★扩展URL模板） ────────────────────────────────
            if (visited.size() < 15) {
                for (Element a : doc.select("a[href]")) {
                    String t    = a.text().trim();
                    String href = a.absUrl("href");
                    if (href.isEmpty() || visited.contains(href)) continue;

                    boolean isNum  = t.matches("\\d{1,3}") && Integer.parseInt(t) <= 25;
                    boolean isNext = t.contains("下一页") || t.equalsIgnoreCase("next") || t.equals(">")
                                  || t.contains("下页") || t.equals("»");
                    // ★ 扩展分页URL模式：pageNo, curPage, page_index, _N.htm
                    boolean urlPag = href.matches(".*(page[Nn](?:um?)?=|[?&]p=|curPage=|cur_page=|pageIndex=|page_index=)\\d+.*")
                        || href.matches(".*[_\\-]\\d+\\.htm.*")
                        || href.matches(".*/\\d+\\.htm");

                    if (isNum || isNext || urlPag) queue.add(href);
                }
                // ★ NEW: 尝试生成 pageNo=2..5 变体（很多高校只有数字链接）
                if (visited.size() == 1 && entries.size() > 3) {
                    for (int pn = 2; pn <= 5; pn++) {
                        String paged = appendOrReplacePageParam(pageUrl, pn);
                        if (!visited.contains(paged)) queue.add(paged);
                    }
                }
            }
        }

        if (entries.isEmpty()) { log.info("    {} 无候选教师", listUrl); return 0; }

        // 域名过滤（允许同 edu.cn 基础域的所有子域名）
        String lbd = baseDomain3(listUrl);

        // ── 并行抓取 profile ───────────────────────────────────────────────
        final int workers = Math.max(1, Math.min(profileWorkers, entries.size()));
        ExecutorService pool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "profile-fetch");
            t.setDaemon(true); return t;
        });
        AtomicInteger count = new AtomicInteger(0);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>(entries.size());

        for (Map<String,String> entry : entries) {
            final String url      = entry.get("profileUrl");
            final String nameHint = entry.get("name");
            final String inlineTit = entry.getOrDefault("inlineTitle","");  // ★ 行内职称

            // 行内全信息条目（无URL）：直接保存，无需fetch profile
            if (url == null || url.isBlank() || url.startsWith("inline|") || url.startsWith("script|")) {
                if (nameHint != null && CN_NAME_PAT.matcher(nameHint).matches()
                        && !isBadName(nameHint)
                        && !INSTITUTION_NAME_PAT.matcher(nameHint).matches()
                        && !nameHint.equals(univName)) {
                    Teacher t = new Teacher(nameHint, univName, deptName,
                        "nourl:" + univName + ":" + nameHint);
                    t.setTitle(inlineTit);
                    t.setResearchAreas(entry.getOrDefault("researchAreas",""));
                    if (db.upsertTeacher(t)) { count.incrementAndGet(); log.info("    ✅ {} [行内]", nameHint); }
                }
                continue;
            }

            try {
                String eh = new URI(url).getHost();
                if (!lbd.isEmpty() && eh != null && !eh.endsWith(lbd) && !lbd.endsWith(eh)) continue;
            } catch (Exception ignored) {}
            if (db.existsByUrl(url)) continue;

            futures.add(pool.submit(() -> {
                try {
                    Document pd = fetchRobust(url);
                    Map<String,String> info = pd != null ? parseProfileAdvanced(pd) : new LinkedHashMap<>();
                    String name = pd != null ? resolveNameFromProfile(nameHint, pd) : (nameHint != null ? nameHint : "");

                    // ★ 关键修复2：质量门控放宽
                    // 只要姓名合法即保存；字段空只记警告，不丢弃。
                    // 原来的门控（title&&research&&email 全空就丢弃）会把江南大学等解析失败的老师全砍掉。
                    if (name.isEmpty() || name.length() < 2 || !CN_NAME_PAT.matcher(name).matches()
                            || isBadName(name)
                            || INSTITUTION_NAME_PAT.matcher(name).matches()
                            || name.equals(univName)) {
                        log.debug("    ⚠️ 无效姓名跳过: hint={} url={}", nameHint, url);
                        return;
                    }

                    Teacher t = new Teacher(name, univName, deptName, url);
                    // 行内职称优先；profile解析次之
                    String titleVal = !inlineTit.isEmpty() ? inlineTit : info.getOrDefault("title","");
                    t.setTitle(titleVal);
                    t.setResearchAreas(info.getOrDefault("researchAreas",""));
                    t.setEmail(info.getOrDefault("email",""));
                    t.setGoogleScholar(info.getOrDefault("googleScholar",""));

                    if (db.upsertTeacher(t)) {
                        count.incrementAndGet();
                        String ra = t.getResearchAreas();
                        boolean hasFields = !t.getTitle().isEmpty() || !t.getResearchAreas().isEmpty() || !t.getEmail().isEmpty();
                        if (!hasFields) log.warn("    ✅ {} [仅姓名，字段空] {}", name, url);
                        else log.info("    ✅ {} | {} | {}", name, t.getTitle(),
                            ra != null && !ra.isEmpty() ? ra.substring(0, Math.min(50, ra.length())) : "");
                    }
                } catch (Exception ex) {
                    log.debug("    profile worker 异常 {}: {}", url, ex.getMessage());
                }
            }));
        }

        pool.shutdown();
        try {
            if (!pool.awaitTermination(4, TimeUnit.MINUTES)) {
                log.warn("    {} profile 池超时，强制关闭", listUrl);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }

        int c = count.get();
        log.info("    ✔ {} 入库 {} 位", listUrl, c);
        return c;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ NEW 策略6: DOM重复模式检测（MDR算法启发）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * MDR（Mining Data Records）算法简化实现。
     *
     * 原理：列表页通常由重复的DOM子树（卡片）组成。
     * 算法：
     *   1. 对所有 li/div/article/tr 元素生成"结构指纹"（tag+首个class）
     *   2. 找出指纹出现≥3次的元素集合
     *   3. 检验该集合中是否有中文姓名链接（排除纯导航列表）
     *   4. 从每个匹配元素提取：姓名链接 + 行内职称（如有）
     *
     * 优势：对不含 /teacher/ 路径、不含职称上下文的现代高校网页有效。
     */
    private List<Map<String,String>> extractByDomPattern(Document doc, String pageUrl) {
        // 建立 指纹 → 元素列表 映射
        Map<String, List<Element>> fpMap = new LinkedHashMap<>();
        for (Element el : doc.select("li, div, article, section, tr")) {
            if (el.select("a[href]").isEmpty()) continue;  // 没有链接的跳过
            String cls = el.className().trim();
            String fp  = el.tagName() + (cls.isEmpty() ? "" : "." + cls.split("\\s+")[0]);
            fpMap.computeIfAbsent(fp, k -> new ArrayList<>()).add(el);
        }

        // 按出现次数降序排列，优先处理最常见的模板
        List<Map.Entry<String, List<Element>>> sorted = new ArrayList<>(fpMap.entrySet());
        sorted.sort((a, b) -> b.getValue().size() - a.getValue().size());

        for (Map.Entry<String, List<Element>> e : sorted) {
            List<Element> elems = e.getValue();
            if (elems.size() < 3 || elems.size() > 200) continue;  // 太少或太多都不像卡片

            // 统计含中文姓名链接的元素数
            long nameCount = elems.stream()
                .filter(el -> el.select("a[href]").stream()
                    .anyMatch(a -> {
                        String t = a.text().trim();
                        return CN_NAME_PAT.matcher(t).matches() && !isBadName(t);
                    }))
                .count();

            // 至少 50% 的元素含姓名（排除纯导航列表）
            if (nameCount < Math.max(3, elems.size() * 0.5)) continue;

            List<Map<String,String>> result = new ArrayList<>();
            for (Element el : elems) {
                String name = "";
                String href = "";
                String inlineTitle = "";
                String inlineResearch = "";

                // 找姓名链接
                for (Element a : el.select("a[href]")) {
                    String t = a.text().trim();
                    if (CN_NAME_PAT.matcher(t).matches() && !isBadName(t)) {
                        name = t;
                        href = a.absUrl("href");
                        if (href.isEmpty()) href = resolveUrl(pageUrl, a.attr("href"));
                        break;
                    }
                }
                if (name.isEmpty()) continue;

                // 行内职称（元素文本中提取，无需进 profile 页）
                String elText = el.text();
                Matcher tm = TITLE_PAT.matcher(elText);
                if (tm.find()) inlineTitle = tm.group(1);

                // 行内研究方向（短文本直接提取）
                for (Pattern rp : RESEARCH_PATS) {
                    Matcher rm = rp.matcher(elText);
                    if (rm.find()) { inlineResearch = rm.group(1).strip(); break; }
                }

                Map<String,String> entry = new LinkedHashMap<>();
                entry.put("name", name);
                entry.put("profileUrl", skipHref(href) ? "" : href);
                if (!inlineTitle.isEmpty())    entry.put("inlineTitle", inlineTitle);
                if (!inlineResearch.isEmpty()) entry.put("researchAreas", inlineResearch);
                result.add(entry);
            }

            if (result.size() >= 3) {
                log.info("    策略6(DOM模式) 指纹「{}」发现 {} 位", e.getKey(), result.size());
                return result;
            }
        }
        return Collections.emptyList();
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ NEW 策略7: Script标签JSON挖掘
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 从页面内联 <script> 块中挖掘教师数组数据。
     *
     * 覆盖场景：
     *   var teachers = [{name:"方伟", url:"/info/..."}]     // 传统ES5
     *   window.__INIT_DATA__ = {"list":[{xm:"方伟",...}]}   // Vue SSR
     *   var data = {records:[{teacherName:"方伟",...}]}     // 自定义
     */
    private List<Map<String,String>> extractFromScripts(Document doc, String pageUrl) {
        List<Map<String,String>> result = new ArrayList<>();
        Pattern namePat  = Pattern.compile(
            "\"(?:name|xm|姓名|teacherName|jsxm|jzmc|jsName)\"\\s*:\\s*\"([\\u4e00-\\u9fff]{2,5})\"");
        Pattern urlPat   = Pattern.compile(
            "\"(?:url|href|link|detailUrl|profileUrl|perUrl|homepage|homepageUrl|jsUrl)\"\\s*:\\s*\"([^\"]+)\"");
        Pattern titlePat = Pattern.compile(
            "\"(?:title|zc|职称|zwmc|jzlb|titleName)\"\\s*:\\s*\"([^\"]{2,20})\"");
        Pattern resPat   = Pattern.compile(
            "\"(?:researchArea|research|研究方向|yjfx|area)\"\\s*:\\s*\"([^\"]{4,200})\"");
        Pattern blockPat = Pattern.compile("\\{[^{}]{20,800}\\}");

        for (Element script : doc.select("script:not([src])")) {
            String code = script.html();
            // 快速过滤：必须含中文或明确字段名
            if (!code.contains("name") && !code.contains("xm") && !code.contains("\\u59d3")) continue;
            if (code.length() > 500_000) continue;  // 超大bundle跳过

            Matcher blockMatcher = blockPat.matcher(code);
            int found = 0;
            while (blockMatcher.find()) {
                String block = blockMatcher.group();
                Matcher nm = namePat.matcher(block);
                if (!nm.find()) continue;
                String name = nm.group(1);
                if (isBadName(name)) continue;

                Map<String,String> entry = new LinkedHashMap<>();
                entry.put("name", name);

                Matcher um = urlPat.matcher(block);
                if (um.find()) {
                    String u = um.group(1);
                    entry.put("profileUrl", u.startsWith("http") ? u : resolveUrl(pageUrl, u));
                } else {
                    entry.put("profileUrl","");
                }
                Matcher tm = titlePat.matcher(block);
                if (tm.find()) entry.put("inlineTitle", tm.group(1));
                Matcher rm = resPat.matcher(block);
                if (rm.find()) entry.put("researchAreas", rm.group(1));

                result.add(entry);
                found++;
            }
            if (found > 0) {
                log.info("    策略7(Script JSON) 发现 {} 位", found);
                break;  // 找到一个有效的script块即停止
            }
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════════════
    // 分页 URL 工具
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ★ 生成分页 URL 变体
     * 支持：?pageNo=N, ?page=N, ?curPage=N, ?pageIndex=N, _N.htm, /N.htm
     */
    String appendOrReplacePageParam(String url, int page) {
        // 已有分页参数：替换数字
        String[] params = {"pageNo","page","pageNum","curPage","page_index","pageIndex","p"};
        for (String param : params) {
            if (url.contains(param+"=")) {
                return url.replaceAll("("+param+"=)\\d+", "$1"+page);
            }
        }
        // 末段 _N.htm 或 /N.htm
        if (url.matches(".*[/_]\\d+\\.htm")) {
            return url.replaceAll("([/_])\\d+(\\.htm)$", "$1"+page+"$2");
        }
        // 无分页参数：追加 ?pageNo=N
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "pageNo=" + page;
    }

    // ════════════════════════════════════════════════════════════════════════
    // REST API 教师抓取
    // ════════════════════════════════════════════════════════════════════════

    /** 从 REST API 端点提取教师 */
    public int scrapeFromApiUrl(String apiUrl, String univName, String deptName) {
        try {
            HttpClient hc = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> r = hc.send(
                HttpRequest.newBuilder().uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", ua())
                    .header("Accept","application/json,*/*").GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return 0;

            List<Map<String,String>> entries = parseJsonTeacherList(r.body(), apiUrl);
            log.info("    API {} → {} 条", apiUrl, entries.size());
            int count = 0;
            for (Map<String,String> e : entries) {
                String name = e.getOrDefault("name","");
                String url  = e.getOrDefault("profileUrl","");
                if (name.isEmpty() || url.isEmpty() || db.existsByUrl(url)) continue;
                Document pd = fetchRobust(url);
                Map<String,String> info = pd != null ? parseProfileAdvanced(pd) : new LinkedHashMap<>();
                Teacher t = new Teacher(name, univName, deptName, url);
                t.setTitle(e.getOrDefault("title", info.getOrDefault("title","")));
                t.setResearchAreas(info.getOrDefault("researchAreas",""));
                t.setEmail(info.getOrDefault("email",""));
                if (db.upsertTeacher(t)) count++;
            }
            return count;
        } catch (Exception ex) { log.warn("API 失败 {}: {}", apiUrl, ex.getMessage()); return 0; }
    }

    /** 兼容 DeptConfig 选择器的 parseTeacherLinks */
    public List<Map<String,String>> parseTeacherLinksCompat(
            Document doc, String baseUrl, String linkSel, String nameSel) {
        Elements links;
        try { links = doc.select(linkSel); } catch (Exception e) { links = new Elements(); }
        if (links.isEmpty()) {
            Pattern kw = Pattern.compile("(教授|副教授|讲师|研究员|博士)");
            links = new Elements();
            for (Element a : doc.select("a[href]")) {
                String ctx = (a.parent()!=null?a.parent().text():"") + a.text();
                if (kw.matcher(ctx).find()) links.add(a);
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String,String>> out = new ArrayList<>();
        for (Element a : links) {
            String href = a.attr("href");
            if (skipHref(href)) continue;
            String abs = a.absUrl("href");
            if (abs.isEmpty()) abs = resolveUrl(baseUrl, href);
            if (!seen.add(abs)) continue;
            String name;
            try { Element el = a.selectFirst(nameSel); name = el!=null ? el.text() : a.text(); }
            catch (Exception ex) { name = a.text(); }
            name = cleanName(name);
            if (name.length() < 2) continue;
            Map<String,String> entry = new LinkedHashMap<>();
            entry.put("name",name); entry.put("profileUrl",abs);
            out.add(entry);
        }
        return out;
    }

    /** 取 URL 的基础域名后3段（用于同域过滤） */
    public static String baseDomain3(String url) {
        try {
            String[] parts = new URI(url).getHost().split("\\.");
            return parts.length >= 3
                ? parts[parts.length-3]+"."+parts[parts.length-2]+"."+parts[parts.length-1]
                : new URI(url).getHost();
        } catch (Exception e) { return ""; }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ NEW: 实验室/课题组页面解析
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 解析实验室/课题组主页，提取：名称、PI、研究方向、成员列表、是否在招。
     *
     * @return Map keys: name, pi, researchAreas, members(List), recruiting, url, snippet
     */
    @SuppressWarnings("unchecked")
    public Map<String,Object> parseLabPage(Document doc, String baseUrl) {
        Map<String,Object> lab = new LinkedHashMap<>();
        lab.put("url", baseUrl);
        String text = doc.text();

        // 实验室名称：title > h1/h2 > 特定class
        String name = doc.title().replaceAll("[-_|—/].*","").trim();
        for (String sel : new String[]{"h1","h2",".lab-name",".group-name",".lab-title",".group-title"}) {
            Element el = doc.selectFirst(sel);
            if (el != null && !el.text().isBlank() && el.text().length() < 60) {
                name = el.text().trim(); break;
            }
        }
        lab.put("name", name);

        // PI / 负责人
        Pattern piPat = Pattern.compile(
            "(?:PI|负责人|组长|导师|Principal\\s+Investigator)[：:\\s]*([\\u4e00-\\u9fff]{2,5}|[A-Z][a-z]+ [A-Z][a-z]+)");
        Matcher piM = piPat.matcher(text);
        if (piM.find()) lab.put("pi", piM.group(1).trim());

        // 研究方向（复用现有 RESEARCH_PATS）
        for (Pattern p : RESEARCH_PATS) {
            Matcher m = p.matcher(text);
            if (m.find()) { lab.put("researchAreas", m.group(1).trim()); break; }
        }

        // 成员列表：收集页面中所有合法中文姓名链接
        List<String> members = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element a : doc.select("a[href]")) {
            String mName = cleanName(a.text());
            if (!mName.isEmpty() && seen.add(mName)) members.add(mName);
        }
        // 也从纯文本中捕获（"成员：张三、李四"格式）
        Matcher memPat = Pattern.compile(
            "(?:成员|团队成员|实验室成员|组员)[：:：]\\s*([\\u4e00-\\u9fff、，,\\s]{4,200})").matcher(text);
        if (memPat.find()) {
            for (String part : memPat.group(1).split("[、，,\\s]+")) {
                part = part.trim();
                if (CN_NAME_PAT.matcher(part).matches() && !isBadName(part)
                        && seen.add(part)) members.add(part);
            }
        }
        lab.put("members", members);

        // 招生意愿
        lab.put("recruiting", RECRUIT_KW.matcher(text).find());

        // 摘要（前 200 字）
        String snippet = text.replaceAll("\\s+"," ").trim();
        lab.put("snippet", snippet.length() > 200 ? snippet.substring(0,200) + "…" : snippet);

        return lab;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ NEW: 招生通知/预推免公告解析
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 解析预推免/夏令营/招生简章页面。
     *
     * @return Map keys: title, url, category(notice/plan/camp),
     *                   deadline, quota, gpaReq, rankReq, snippet, publishedAt
     */
    public Map<String,Object> parseNoticePage(Document doc, String url) {
        Map<String,Object> notice = new LinkedHashMap<>();
        notice.put("url",   url);
        notice.put("title", doc.title().trim());
        String text = doc.text();

        // 细分类型
        String cat = "notice";
        if (PLAN_KW.matcher(text.substring(0, Math.min(600, text.length()))).find()) cat = "plan";
        else if (text.contains("夏令营") || text.contains("summer")) cat = "camp";
        notice.put("category", cat);

        // ── 截止日期（取文中最后出现的日期，通常最靠近截止）
        List<String> dates = extractDeadlines(text);
        if (!dates.isEmpty()) notice.put("deadline", dates.get(dates.size()-1));

        // ── 招生名额
        String quota = extractQuota(text);
        if (!quota.isEmpty()) notice.put("quota", quota);

        // ── GPA 要求
        Matcher gm = GPA_PAT.matcher(text);
        if (gm.find()) notice.put("gpaReq", gm.group(1));

        // ── 排名要求
        Matcher rm = RANK_PAT.matcher(text);
        if (rm.find()) notice.put("rankReq", (rm.group(1) != null ? rm.group(1) : rm.group(2)) + "%");

        // ── 联系邮箱
        Matcher em = EMAIL_PAT.matcher(text);
        if (em.find()) notice.put("contactEmail", em.group());

        // ── 发布时间
        Element dateEl = doc.selectFirst("time,[class*='date'],[class*='time'],[class*='pub']");
        if (dateEl != null) notice.put("publishedAt", dateEl.text().trim());

        // ── 摘要（前 300 字）
        String s = text.replaceAll("\\s+"," ").trim();
        notice.put("snippet", s.length() > 300 ? s.substring(0,300) + "…" : s);

        return notice;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ NEW: 页面类型检测（Teacher-list / Lab / Notice / Plan / Unknown）
    //        基于关键词TF-IDF打分，无需训练数据，纯规则
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 快速判断一个页面属于哪种类型。
     *
     * @return "teacher_list" | "lab" | "notice" | "plan" | "unknown"
     */
    public String detectPageType(Document doc, String url) {
        if (doc == null) return "unknown";
        String title   = doc.title().toLowerCase();
        String excerpt = doc.text().substring(0, Math.min(800, doc.text().length())).toLowerCase();
        String combined = title + " " + excerpt;

        int teacherScore = 0, labScore = 0, noticeScore = 0, planScore = 0;

        // Teacher signals
        if (FACULTY_KW.matcher(combined).find()) teacherScore += 4;
        long cnNameLinks = doc.select("a[href]").stream()
            .filter(a -> CN_NAME_PAT.matcher(cleanName(a.text())).matches()).count();
        teacherScore += (int) Math.min(cnNameLinks / 2, 6);
        if (url.matches(".*(?:szdw|jsdw|faculty|teacher|staff|people|rencai).*")) teacherScore += 3;

        // Lab signals
        if (LAB_KW.matcher(title).find())   labScore += 5;
        if (LAB_KW.matcher(excerpt).find()) labScore += 2;
        if (combined.contains("课题组成员") || combined.contains("lab member"))  labScore += 3;
        if (combined.contains("pi ") || combined.contains("负责人"))             labScore += 2;
        if (url.matches(".*(?:lab|labs|yjjg|yjzx|research|group|centre).*"))     labScore += 2;

        // Notice signals
        if (NOTICE_KW.matcher(combined).find()) noticeScore += 5;
        if (DEADLINE_PAT.matcher(combined).find())  noticeScore += 3;
        if (QUOTA_PAT.matcher(combined).find())     noticeScore += 2;
        if (combined.contains("报名") || combined.contains("申请截止")) noticeScore += 1;
        if (url.matches(".*(?:notice|tzgg|bszn|zsxx|notify|announce).*")) noticeScore += 2;

        // Plan signals
        if (PLAN_KW.matcher(combined).find()) planScore += 5;
        if (combined.contains("导师") && combined.contains("名额")) planScore += 3;
        if (url.matches(".*(?:plan|dszs|dsmc|recruit|mentor).*")) planScore += 2;

        int max = Math.max(teacherScore, Math.max(labScore, Math.max(noticeScore, planScore)));
        if (max == 0)           return "unknown";
        if (max == planScore)   return "plan";
        if (max == noticeScore) return "notice";
        if (max == labScore)    return "lab";
        return "teacher_list";
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ NEW: 辅助提取方法
    // ════════════════════════════════════════════════════════════════════════

    /** 从文本中提取所有日期字符串（截止日期用） */
    public List<String> extractDeadlines(String text) {
        List<String> dates = new ArrayList<>();
        Matcher m = DEADLINE_PAT.matcher(text);
        while (m.find()) dates.add(m.group().trim());
        return dates;
    }

    /** 从文本中提取招生名额数字 */
    public String extractQuota(String text) {
        Matcher m = QUOTA_PAT.matcher(text);
        if (!m.find()) return "";
        return (m.group(1) != null ? m.group(1) : m.group(2)).trim();
    }

    /** 统计页面中 Chinese name 链接数量（页面评分用） */
    public long countCnNameLinks(Document doc) {
        return doc.select("a[href]").stream()
            .filter(a -> CN_NAME_PAT.matcher(cleanName(a.text())).matches()).count();
    }
}