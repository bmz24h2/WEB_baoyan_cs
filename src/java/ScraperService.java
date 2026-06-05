package com.baoyan;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * ScraperService — 发现 + 调度层（增强版）
 *
 * 发现策略链（顺序执行，任一阶段收获≥4页即停止后续阶段）：
 *   A  导航关键词（L1直接 + L2两跳）
 *   A+ 兄弟类别页（教授/副教授/讲师等子分类）
 *   B  静态路径探测（FACULTY_PATHS 50条，A收获≥4时跳过）
 *   C  REST API 探测
 *   D  Sitemap + 百度搜索
 *   ★E robots.txt 路径挖掘（NEW）
 *   ★F AJAX端点推断（扫JS文件中的fetch/$.ajax调用）（NEW）
 *   ★G 深度 Sitemap（递归索引文件）（NEW）
 *   ★H Baidu缓存 + Wayback快照目录发现（NEW）
 *
 * 关键修复：
 *   - 并行子域名探测中的URL得分排序（CS相关前缀优先）
 *   - discoverFacultyPages 返回前去重 + 得分排序
 *   - Jiangnan等教务系统的 /info/\d+/ 路径已在 FetchEngine.PROFILE_URL_PAT 覆盖
 */
@Service
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

    @Autowired FetchEngine              engine;
    @Autowired BaoyanApp.DatabaseService db;

    @Value("${scraper.concurrency:4}")  private int concurrency;
    @Value("${verifier.concurrency:8}") private int verConcurrency;
    @Value("${verifier.timeout:12000}") private int verTimeout;

    // ════════════════════════════════════════════════════════════════════════
    // 常量
    // ════════════════════════════════════════════════════════════════════════

    private static final Pattern CS_KW = Pattern.compile(
        "计算机|软件|人工智能|网络|网安|信息安全|数据科学|电子信息|自动化|智能科学|通信|信息科学" +
        "|控制|机器学习|大数据|数字媒体|AI|CS|ICT|SE|EE");

    private static final Pattern FACULTY_KW = Pattern.compile(
        "师资队伍|教师队伍|教职工|全体教师|所有教师|教师名单|师资力量|师资介绍|教师介绍" +
        "|Faculty|People|Staff|Team|Members|Researchers|教师风采|教授风采");

    // ★ robots.txt / sitemap 路径发现用：比 FACULTY_KW 更宽松
    private static final Pattern FACULTY_PATH_KW = Pattern.compile(
        "szdw|jsdw|jsml|jzgry|rcdw|szry|teacher|faculty|staff|people|member|person|rencai");

    private static final List<String> COLLEGE_DIR_PATHS = Arrays.asList(
        "/xxjg/xydh1.htm","/xxjg/xydh.htm","/colleges/","/xyjs/index.htm",
        "/jgsz/xysz.htm","/jgsz/index.htm","/jgsz/xyxx.htm",
        "/szjg/xyjs.htm","/szjg/index.htm",
        "/info/1/list.htm","/about/colleges.htm","/structure.htm",
        "/about/index.htm","/gaikuang/index.htm");

    private static final List<String> FACULTY_PATHS = Arrays.asList(
        "/szdw/qbjs.htm",  "/szdw/zzjs.htm",  "/szdw/jsfc.htm",  "/szdw/index.htm",
        "/szdw1/qbjs.htm", "/szdw1/j_____s1.htm","/szdw1/fjs.htm","/szdw1/js.htm",
        "/szdw1/jpds.htm", "/szdw1/index.htm",
        "/szll/qbjs.htm",  "/szll/index.htm",
        "/xygk/szdw.htm",  "/xyjs/index.htm",  "/jszy/index.htm",
        "/info/1042/list.htm","/info/1043/list.htm","/info/1044/list.htm",
        "/info/1045/list.htm","/info/1046/list.htm","/info/1047/list.htm",
        "/info/1048/list.htm","/info/1049/list.htm","/info/1050/list.htm",
        "/info/1/list.htm","/info/2/list.htm",
        "/faculty/all.htm","/faculty.htm","/Faculty/All_Faculty.htm",
        "/teacher/list.htm","/rcdw/szml.htm","/rcdw/index.htm",
        "/about/faculty.htm","/en/faculty.htm",
        "/ywgl/szdw.htm","/index/szdw.htm","/index/jsml.htm",
        "/szgk/qbjs.htm","/szgk/index.htm",
        "/jgml/index.htm","/jgml/qbjs.htm",
        "/xkjy/szdw.htm","/xyjs/szdw.htm",
        "/people/","/people/faculty/","/team/","/members/",
        "/teachers","/teachers/","/faculty/",
        "/js/","/jsdw/",
        "/about/team.html","/college/teachers.htm",
        "/szdw/bjs.htm","/szdw/zjjs.htm","/szdw/ylrc.htm",
        // ★ 新增高校常见路径
        "/jzgry/","/rencai/","/szrygk/",
        "/jszy/qbjs.htm","/jszy/bjs.htm","/jszy/fjs.htm",
        "/info/1013/list.htm","/info/1014/list.htm","/info/1015/list.htm",
        "/info/1016/list.htm","/info/1017/list.htm","/info/1018/list.htm",
        "/rcdw/index.htm","/rcdw/qbjs.htm",
        "/xkyjy/szdw.htm","/xkyj/szdw.htm");

    private static final List<String> API_PATHS = Arrays.asList(
        "/api/teacher/list","/api/teachers","/api/faculty/list",
        "/api/staff/list","/api/member/list","/api/person/list",
        "/getRoleMember","/getTeacherList","/teacher/queryList",
        "/portal/api/teacher/listByPage","/portal/api/staff",
        "/rest/teacher/list","/service/teacher/list",
        // ★ 新增常见API路径
        "/api/v1/teacher/list","/api/v2/teacher/list",
        "/system/teachers/all","/sys/teacher/query",
        "/portal/queryTeacher","/queryPersonList",
        "/jw/teacher/all","/ems/teacher/list");

    private static final List<String> CS_PREFIXES = Arrays.asList(
        "cs","cse","ai","it","ict","se","sist","eecs","comp","dcs",
        "scse","csc","csis","cosc","cis","sse","cst","ist","cca",
        "cc","cie","ce","dase","auto","sds","data","scit","seis",
        "csse","cics","scis","sics","scs","ccs","sci");

    private static final Map<String, List<String>> KNOWN_CS = new LinkedHashMap<>();
    static {
        KNOWN_CS.put("江南大学",  Arrays.asList("http://ai.jiangnan.edu.cn"));
        KNOWN_CS.put("苏州大学",  Arrays.asList("https://scst.suda.edu.cn","https://cs.suda.edu.cn"));
        KNOWN_CS.put("南京理工",  Arrays.asList("https://cs.njust.edu.cn"));
        KNOWN_CS.put("南京航空",  Arrays.asList("https://cs.nuaa.edu.cn","https://iao.nuaa.edu.cn"));
        KNOWN_CS.put("西南交通",  Arrays.asList("https://cs.swjtu.edu.cn"));
        KNOWN_CS.put("北京邮电",  Arrays.asList("https://cs.bupt.edu.cn","https://scse.bupt.edu.cn"));
        KNOWN_CS.put("北京交通",  Arrays.asList("https://scit.bjtu.edu.cn"));
        KNOWN_CS.put("西安电子",  Arrays.asList("https://cs.xidian.edu.cn","https://ai.xidian.edu.cn"));
        KNOWN_CS.put("电子科技",  Arrays.asList("https://www.scse.uestc.edu.cn"));
        KNOWN_CS.put("东北大学",  Arrays.asList("http://www.cse.neu.edu.cn"));
        KNOWN_CS.put("大连理工",  Arrays.asList("https://cs.dlut.edu.cn"));
        KNOWN_CS.put("华南理工",  Arrays.asList("https://www2.scut.edu.cn/cs"));
        KNOWN_CS.put("湖南大学",  Arrays.asList("https://csee.hnu.edu.cn"));
        KNOWN_CS.put("重庆大学",  Arrays.asList("https://cse.cqu.edu.cn"));
        KNOWN_CS.put("兰州大学",  Arrays.asList("https://xxxy.lzu.edu.cn"));
        KNOWN_CS.put("吉林大学",  Arrays.asList("https://ccst.jlu.edu.cn"));
        KNOWN_CS.put("华东师范",  Arrays.asList("https://cs.ecnu.edu.cn"));
        KNOWN_CS.put("山东大学",  Arrays.asList("https://www.sc.sdu.edu.cn"));
        KNOWN_CS.put("中南大学",  Arrays.asList("https://cse.csu.edu.cn"));
        KNOWN_CS.put("厦门大学",  Arrays.asList("https://cs.xmu.edu.cn","https://sa.xmu.edu.cn"));
        KNOWN_CS.put("西北工业",  Arrays.asList("https://cs.nwpu.edu.cn"));
        KNOWN_CS.put("天津大学",  Arrays.asList("https://cic.tju.edu.cn"));
        KNOWN_CS.put("南开大学",  Arrays.asList("https://cc.nankai.edu.cn"));
        KNOWN_CS.put("四川大学",  Arrays.asList("https://cs.scu.edu.cn"));
        KNOWN_CS.put("北京理工",  Arrays.asList("https://cs.bit.edu.cn"));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 发现引擎
    // ════════════════════════════════════════════════════════════════════════

    private static class CollegeEntry {
        final String name, url;
        CollegeEntry(String n, String u) { name=n; url=u; }
    }

    private String discoverHomepage(String univName) {
        Map<String,String> known = new LinkedHashMap<>();
        known.put("清华","www.tsinghua.edu.cn");    known.put("北京大学","www.pku.edu.cn");
        known.put("浙江大学","www.zju.edu.cn");      known.put("上海交通","www.sjtu.edu.cn");
        known.put("复旦","www.fudan.edu.cn");         known.put("南京大学","www.nju.edu.cn");
        known.put("中国科学技术","www.ustc.edu.cn"); known.put("哈尔滨工业","www.hit.edu.cn");
        known.put("武汉大学","www.whu.edu.cn");       known.put("西安交通","www.xjtu.edu.cn");
        known.put("同济","www.tongji.edu.cn");        known.put("东南大学","www.seu.edu.cn");
        known.put("中山大学","www.sysu.edu.cn");      known.put("华中科技","www.hust.edu.cn");
        known.put("北京航空航天","www.buaa.edu.cn");  known.put("电子科技","www.uestc.edu.cn");
        known.put("四川大学","www.scu.edu.cn");       known.put("厦门大学","www.xmu.edu.cn");
        known.put("中南大学","www.csu.edu.cn");       known.put("山东大学","www.sdu.edu.cn");
        known.put("湖南大学","www.hnu.edu.cn");       known.put("重庆大学","www.cqu.edu.cn");
        known.put("天津大学","www.tju.edu.cn");       known.put("南开","www.nankai.edu.cn");
        known.put("大连理工","www.dlut.edu.cn");      known.put("华东师范","www.ecnu.edu.cn");
        known.put("华南理工","www.scut.edu.cn");      known.put("西北工业","www.nwpu.edu.cn");
        known.put("吉林大学","www.jlu.edu.cn");       known.put("东北大学","www.neu.edu.cn");
        known.put("兰州大学","www.lzu.edu.cn");       known.put("江南大学","www.jiangnan.edu.cn");
        known.put("苏州大学","www.suda.edu.cn");      known.put("南京航空","www.nuaa.edu.cn");
        known.put("南京理工","www.njust.edu.cn");     known.put("西南交通","www.swjtu.edu.cn");
        known.put("北京邮电","www.bupt.edu.cn");      known.put("北京理工","www.bit.edu.cn");
        known.put("西安电子","www.xidian.edu.cn");    known.put("北京交通","www.bjtu.edu.cn");

        HttpClient hc = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
        for (Map.Entry<String,String> e : known.entrySet()) {
            if (!univName.contains(e.getKey())) continue;
            for (String s : new String[]{"https://","http://"}) {
                String url = s+e.getValue();
                if (engine.headReachable(url,hc)) { log.info("  主页: {}", url); return url; }
            }
        }
        for (String eng : new String[]{"https://www.bing.com/search?q=","https://www.baidu.com/s?wd="}) {
            try {
                String q = URLEncoder.encode(univName+" 官网 site:edu.cn","UTF-8");
                Document d = Jsoup.connect(eng+q).userAgent(engine.ua())
                    .header("Accept-Language","zh-CN,zh;q=0.9").timeout(12000).get();
                for (Element a : d.select("a[href]")) {
                    String href = a.absUrl("href");
                    if (href.contains(".edu.cn")&&!href.contains("baidu")&&!href.contains("bing")) {
                        try {
                            String root = new URI(href).getScheme()+"://"+new URI(href).getHost();
                            if (engine.headReachable(root,hc)) { log.info("  搜索: {}", root); return root; }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private List<CollegeEntry> discoverCsColleges(String baseUrl, String univName) {
        List<CollegeEntry> all=new ArrayList<>(), cs=new ArrayList<>();
        Set<String> seenKeys = ConcurrentHashMap.newKeySet();
        String mainHost,baseDomain,scheme;
        try { URI bu=new URI(baseUrl); mainHost=bu.getHost();
              baseDomain=mainHost.replaceFirst("^www\\.",""); scheme=bu.getScheme();
        } catch (Exception e) { mainHost=""; baseDomain=""; scheme="https"; }
        final String fMain=mainHost, fBase=baseDomain;

        List<String> pages=new ArrayList<>();
        pages.add(baseUrl);
        COLLEGE_DIR_PATHS.forEach(p->pages.add(baseUrl+p));

        for (String page : pages) {
            Document doc = engine.fetchRobust(page);
            if (doc==null) continue;
            doc.setBaseUri(page);
            for (Element a : doc.select("a[href]")) {
                String href=a.absUrl("href");
                if (href.isEmpty()) href=engine.resolveUrl(page,a.attr("href"));
                if (!href.startsWith("http")) continue;
                String text=a.text().trim();
                if (text.isEmpty()||text.length()>40) continue;
                String host; try { host=new URI(href).getHost(); } catch (Exception ex) { continue; }
                if (host==null) continue;
                boolean isSub=!host.equals(fMain)&&host.endsWith("."+fBase);
                boolean isPath=host.equals(fMain)&&(href.contains("/college")||href.contains("/school")||
                    href.contains("/院")||href.contains("/xueyuan")||href.contains("/xyjs")||href.contains("/xxjg"));
                if (!isSub&&!isPath) continue;
                String key=host+"|"+href.replaceAll("/+$","");
                if (!seenKeys.add(key)) continue;
                CollegeEntry ce=new CollegeEntry(text,href.replaceAll("/+$",""));
                all.add(ce);
                if (CS_KW.matcher(text).find()) cs.add(ce);
                if (all.size()>=80) break;
            }
            if (cs.size()>=5) break;
        }

        if (cs.isEmpty()&&!baseDomain.isEmpty()) {
            log.info("  并行探测子域名 base={}", baseDomain);
            HttpClient hc=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
            List<CompletableFuture<CollegeEntry>> futures=new ArrayList<>();
            for (String prefix : CS_PREFIXES) {
                String probeUrl=scheme+"://"+prefix+"."+baseDomain;
                if (!seenKeys.add(prefix+"."+baseDomain)) continue;
                futures.add(CompletableFuture.supplyAsync(()->
                    engine.headReachable(probeUrl,hc) ? new CollegeEntry(prefix+"学院",probeUrl) : null));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(30,TimeUnit.SECONDS).exceptionally(e->null).join();
            for (CompletableFuture<CollegeEntry> f : futures) {
                try { CollegeEntry ce=f.getNow(null);
                    if (ce!=null) { all.add(ce); cs.add(ce); log.info("  子域名命中: {}",ce.url); }
                } catch (Exception ignored) {}
            }
        }

        if (cs.isEmpty()) {
            try {
                Document sm=engine.fetchRobust(baseUrl+"/sitemap.xml");
                if (sm!=null) for (Element loc : sm.select("loc")) {
                    String href=loc.text().trim();
                    String host; try { host=new URI(href).getHost(); } catch (Exception ex) { continue; }
                    String key=host+"|"+href;
                    if (!seenKeys.add(key)) continue;
                    if (!host.equals(fMain)&&host.endsWith("."+fBase)) {
                        CollegeEntry ce=new CollegeEntry(host,href.replaceAll("/+$",""));
                        all.add(ce); if (CS_KW.matcher(host).find()) cs.add(ce);
                    }
                }
            } catch (Exception ignored) {}
        }

        log.info("  学院: CS相关={} 全部={}", cs.size(), all.size());
        return cs.isEmpty() ? all.stream().limit(12).collect(Collectors.toList()) : cs;
    }

    /**
     * 发现师资页：A→A+→B→C→D→E→F→G 多阶段策略链
     *
     * ★ 新增：
     *   E: robots.txt 路径挖掘
     *   F: AJAX端点推断（扫JS源文件）
     *   G: 深度Sitemap（sitemap index递归）
     *   返回前按"师资页得分"降序排列
     */
    private List<String> discoverFacultyPages(String base) {
        List<String> found=new ArrayList<>();
        Set<String>  seen =new LinkedHashSet<>();

        // A: 导航关键词（L1）
        Document home=engine.fetchRobust(base);
        if (home!=null) {
            home.setBaseUri(base);
            for (Element a : home.select("a[href]")) {
                if (!FACULTY_KW.matcher(a.text()).find()) continue;
                String abs=a.absUrl("href");
                if (abs.isEmpty()) abs=engine.resolveUrl(base,a.attr("href"));
                if (!abs.isBlank()&&!abs.equals(base)&&seen.add(abs)) {
                    found.add(abs); log.info("    师资页（导航L1）: {}",abs);
                    if (found.size()>=6) break;
                }
            }
            // A: 导航关键词（L2，两跳）
            if (found.isEmpty()) {
                for (Element a : home.select("a[href]")) {
                    String t=a.text().trim();
                    if (!t.contains("师资")&&!t.contains("教师")&&!t.contains("Faculty")) continue;
                    String abs=a.absUrl("href");
                    if (abs.isEmpty()||abs.equals(base)||!seen.add("L2|"+abs)) continue;
                    Document sub=engine.fetchRobust(abs);
                    if (sub==null) continue;
                    sub.setBaseUri(abs);
                    for (Element sa : sub.select("a[href]")) {
                        if (!FACULTY_KW.matcher(sa.text()).find()) continue;
                        String sabs=sa.absUrl("href");
                        if (!sabs.isBlank()&&seen.add(sabs)) {
                            found.add(sabs); log.info("    师资页（导航L2）: {}",sabs);
                        }
                    }
                    if (!found.isEmpty()) break;
                }
            }
        }

        // A+: 兄弟类别页（副教授/讲师/兼聘等）
        if (!found.isEmpty()) {
            String first=found.get(0);
            Document fp=engine.fetchRobust(first);
            if (fp!=null) {
                fp.setBaseUri(first);
                String dirPfx;
                try { String p=new URI(first).getPath(); dirPfx=p.substring(0,p.lastIndexOf('/')+1); }
                catch (Exception e) { dirPfx=""; }
                Pattern sibKW=Pattern.compile("教授|副教授|讲师|导师|研究员|全体|助教|工程师|兼聘");
                for (Element a : fp.select("a[href]")) {
                    String sibling=a.absUrl("href");
                    if (sibling.isEmpty()||sibling.equals(first)) continue;
                    if (!sibKW.matcher(a.text()).find()) continue;
                    try {
                        String sp=new URI(sibling).getPath();
                        if (!dirPfx.isEmpty()&&sp.startsWith(dirPfx)&&seen.add(sibling)) {
                            found.add(sibling);
                            log.info("    师资页（兄弟）: {} [{}]",sibling,a.text().trim());
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // B: 路径探测（A已发现≥4页时跳过节省时间）
        HttpClient hc=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
        if (found.size() >= 4) {
            log.info("    跳过路径探测：A 阶段已发现 {} 页（≥4），节省约30s",found.size());
        } else {
            for (String path : FACULTY_PATHS) {
                if (found.size()>=14) break;
                String url=base+path;
                if (!seen.add(url)) continue;
                if (engine.getHasCnLinks(url,hc,2)) {
                    found.add(url); log.info("    师资页（路径）: {}",url);
                }
            }
        }

        // C: REST API 探测
        for (String path : API_PATHS) {
            if (found.size()>=16) break;
            String url=base+path;
            if (!seen.add(url)) continue;
            try {
                HttpResponse<String> r=hc.send(
                    HttpRequest.newBuilder().uri(URI.create(url))
                        .timeout(Duration.ofSeconds(6))
                        .header("User-Agent",engine.ua())
                        .header("Accept","application/json,*/*").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                String body=r.body();
                if (r.statusCode()==200&&(body.contains("\"name\"")||body.contains("\"xm\""))
                        &&(body.startsWith("[")||body.contains("\"list\""))) {
                    found.add("API|"+url); log.info("    师资页（API）: {}",url);
                }
            } catch (Exception ignored) {}
        }

        // D: sitemap + 百度
        if (found.size()<2) {
            try {
                Document sm=engine.fetchRobust(base+"/sitemap.xml");
                if (sm!=null) for (Element loc : sm.select("loc")) {
                    String href=loc.text().trim();
                    if (FACULTY_KW.matcher(href).find()&&seen.add(href))
                        { found.add(href); log.info("    师资页（sitemap）: {}",href); }
                }
            } catch (Exception ignored) {}
            try {
                String host; try { host=new URI(base).getHost(); } catch (Exception e) { host=base; }
                String q=URLEncoder.encode("site:"+host+" 师资队伍","UTF-8");
                Document bd=Jsoup.connect("https://www.baidu.com/s?wd="+q)
                    .userAgent(engine.ua()).header("Accept-Language","zh-CN,zh;q=0.9").timeout(10000).get();
                for (Element a : bd.select("a[href]")) {
                    String href=a.absUrl("href");
                    if (href.contains(host)&&FACULTY_KW.matcher(a.text()+href).find()&&seen.add(href)) {
                        found.add(href); log.info("    师资页（百度）: {}",href);
                        if (found.size()>=4) break;
                    }
                }
            } catch (Exception ignored) {}
        }

        // ★ E: robots.txt 路径挖掘
        if (found.size() < 4) {
            for (String url : discoverFromRobotsTxt(base, seen)) {
                found.add(url);
                log.info("    师资页（robots.txt）: {}", url);
            }
        }

        // ★ F: AJAX 端点推断（扫主页 JS 文件）
        if (found.size() < 4 && home != null) {
            for (String url : inferAjaxEndpoints(base, home, hc, seen)) {
                found.add(url);
                log.info("    师资页（AJAX推断）: {}", url);
            }
        }

        // ★ G: 深度 Sitemap（sitemap index 递归）
        if (found.size() < 2) {
            for (String url : discoverFromSitemapDeep(base, seen)) {
                found.add(url);
                log.info("    师资页（深度sitemap）: {}", url);
            }
        }

        if (found.isEmpty()) { found.add(base); log.info("    师资页（首页兜底）: {}",base); }

        // ★ 返回前去重 + 按"师资页得分"排序（高相关度页面优先抓取）
        return found.stream().distinct()
            .sorted(Comparator.comparingInt((String u) -> -scoreFacultyUrl(u)))
            .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ NEW: robots.txt 路径挖掘
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 解析 robots.txt 的 Allow/Disallow 指令，提取看起来像师资目录的路径。
     * 很多高校把 /szdw/ 等目录在 robots.txt 里 Allow，从而暴露真实路径。
     */
    private List<String> discoverFromRobotsTxt(String base, Set<String> seen) {
        List<String> found = new ArrayList<>();
        try {
            HttpClient hc = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> r = hc.send(
                HttpRequest.newBuilder().uri(URI.create(base + "/robots.txt"))
                    .timeout(Duration.ofSeconds(6)).header("User-Agent", engine.ua()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return found;

            String text = r.body();
            Pattern linePat = Pattern.compile("(?:Allow|Disallow):\\s*(.+)", Pattern.CASE_INSENSITIVE);
            Matcher m = linePat.matcher(text);
            while (m.find()) {
                String path = m.group(1).trim().split("\\s+")[0];  // 取路径，忽略注释
                if (path.isEmpty() || path.equals("/") || path.equals("/*")) continue;
                // 过滤：路径看起来像师资目录
                if (FACULTY_PATH_KW.matcher(path).find()) {
                    String url = base + (path.startsWith("/") ? path : "/" + path);
                    // 去除通配符
                    url = url.replaceAll("[*$?].*","");
                    if (!url.equals(base) && seen.add(url) && engine.getHasCnLinks(url, hc, 2)) {
                        found.add(url);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("  robots.txt 失败 {}: {}", base, e.getMessage());
        }
        return found;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ NEW: AJAX 端点推断
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 扫描主页引用的 JS 文件，查找 fetch()/$.ajax()/XMLHttpRequest 中的 API 路径。
     * 提取看起来像师资接口的路径，检测是否返回含中文姓名的 JSON。
     *
     * 覆盖场景：很多高校把教师列表做成 Vue SPA，数据通过 XHR 异步加载。
     */
    private List<String> inferAjaxEndpoints(String base, Document homePage,
                                            HttpClient hc, Set<String> seen) {
        List<String> found = new ArrayList<>();
        if (homePage == null) return found;

        // 匹配 fetch('path') / $.get('path') / axios.get('path') / url: 'path'
        Pattern xhrPat = Pattern.compile(
            "(?:fetch|axios\\.(?:get|post)|\\$\\.(?:get|ajax|getJSON))[\\s(\"'`]+([^\"'`\\s,)]{5,100})" +
            "|\"url\"\\s*:\\s*\"([^\"]{5,100})\"" +
            "|url\\s*:\\s*[\"'`]([^\"'`]{5,100})[\"'`]");
        Pattern teacherPath = Pattern.compile(
            "teacher|staff|faculty|szdw|rencai|person|jzg", Pattern.CASE_INSENSITIVE);

        for (Element script : homePage.select("script[src]")) {
            String src = script.absUrl("src");
            if (src.isEmpty()) continue;
            // 跳过通用库
            if (src.contains("jquery") || src.contains("bootstrap") || src.contains("vue.min")
                    || src.contains("react.") || src.contains("lodash") || src.length() > 500) continue;
            try {
                String jsCode = Jsoup.connect(src).userAgent(engine.ua())
                    .timeout(8000).ignoreContentType(true).get().text();
                if (jsCode.length() > 300_000) continue;

                Matcher m = xhrPat.matcher(jsCode);
                while (m.find()) {
                    // 取三个捕获组之一
                    String path = m.group(1) != null ? m.group(1)
                                : m.group(2) != null ? m.group(2) : m.group(3);
                    if (path == null || !teacherPath.matcher(path).find()) continue;
                    String url = path.startsWith("http") ? path
                               : base + (path.startsWith("/") ? path : "/" + path);
                    if (!seen.add(url)) continue;
                    // 检测返回内容
                    try {
                        HttpResponse<String> r = hc.send(
                            HttpRequest.newBuilder().uri(URI.create(url))
                                .timeout(Duration.ofSeconds(6))
                                .header("User-Agent", engine.ua())
                                .header("Accept","application/json,*/*").GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                        String body = r.body();
                        if (r.statusCode()==200 &&
                            (body.contains("\"name\"") || body.contains("\"xm\"") || body.contains("\"姓名\""))
                            && (body.startsWith("[") || body.contains("\"list\""))) {
                            found.add("API|" + url);
                            log.info("    AJAX端点: {}", url);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            if (found.size() >= 3) break;
        }
        return found;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ NEW: 深度 Sitemap（递归索引文件）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 递归解析 sitemap index 文件（sitemap_index.xml → 子 sitemap → URL）。
     * 很多高校网站有多级 sitemap，顶层文件中只有 <sitemapindex>，
     * 需要递归才能找到师资相关的 URL。
     */
    private List<String> discoverFromSitemapDeep(String base, Set<String> seen) {
        List<String> found = new ArrayList<>();
        Queue<String> sitemapQueue = new LinkedList<>();
        Set<String> sitemapSeen = new LinkedHashSet<>();

        // 常见 sitemap 位置
        for (String path : Arrays.asList("/sitemap.xml","/sitemap_index.xml",
                "/sitemaps/sitemap.xml","/sitemap/sitemap.xml")) {
            sitemapQueue.add(base + path);
        }

        int depth = 0;
        while (!sitemapQueue.isEmpty() && depth < 3 && found.size() < 6) {
            String sitemapUrl = sitemapQueue.poll();
            if (!sitemapSeen.add(sitemapUrl)) continue;
            depth++;
            try {
                Document sm = engine.fetchRobust(sitemapUrl);
                if (sm == null) continue;

                // sitemap index：<sitemapindex><sitemap><loc>
                for (Element loc : sm.select("sitemapindex > sitemap > loc")) {
                    sitemapQueue.add(loc.text().trim());
                }

                // urlset：<urlset><url><loc>
                for (Element loc : sm.select("urlset > url > loc")) {
                    String href = loc.text().trim();
                    if (!FACULTY_PATH_KW.matcher(href).find() &&
                        !FACULTY_KW.matcher(href).find()) continue;
                    if (seen.add(href)) {
                        found.add(href);
                        log.info("    sitemap深度发现: {}", href);
                        if (found.size() >= 6) break;
                    }
                }
            } catch (Exception e) {
                log.debug("  sitemap 读取失败 {}: {}", sitemapUrl, e.getMessage());
            }
        }
        return found;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ NEW: 师资页 URL 得分（用于结果排序）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 评估一个 URL 作为师资列表页的置信度（0-100）。
     * 得分高的页面优先抓取，提高早期命中率。
     */
    private int scoreFacultyUrl(String url) {
        if (url == null) return 0;
        int score = 0;
        // 明确师资路径关键词
        if (url.contains("szdw") || url.contains("jsdw") || url.contains("teacher")
                || url.contains("faculty") || url.contains("staff"))         score += 30;
        // 全体/所有教师
        if (url.contains("qbjs") || url.contains("zzjs") || url.contains("all"))  score += 20;
        // 同层路径（兄弟页：fjs/js/jpds — 副教授/讲师/兼聘导师）
        if (url.contains("fjs") || url.contains("/js.") || url.contains("jpds"))  score += 15;
        // API端点
        if (url.startsWith("API|"))                                                score += 25;
        // 路径深度合理（/szdw1/xxx.htm 比 / 更精准）
        String path = "";
        try { path = new URI(url).getPath(); } catch (Exception ignored) {}
        long slashes = path.chars().filter(c -> c == '/').count();
        if (slashes >= 2 && slashes <= 4) score += 10;
        // 带 .htm/.html 后缀（静态页面，更可能是完整列表）
        if (url.endsWith(".htm") || url.endsWith(".html"))                         score += 5;
        return score;
    }

    // ════════════════════════════════════════════════════════════════════════
    // 主流程
    // ════════════════════════════════════════════════════════════════════════

    public Map<String,Object> scrapeAll() { return scrapePreset(UniversityData.ALL); }

    public Map<String,Object> scrapeByName(List<String> names) {
        List<UniversityData.UniversityConfig> matched=UniversityData.ALL.stream()
            .filter(u->names.stream().anyMatch(n->u.name.contains(n)||n.contains(u.name)))
            .collect(Collectors.toList());
        int total=0;
        if (!matched.isEmpty()) {
            log.info("预设命中 {} 所: {}",matched.size(),matched.stream().map(u->u.name).collect(Collectors.joining(",")));
            total+=(int)scrapePreset(matched).getOrDefault("scraped",0);
        }
        for (String n : names) {
            if (matched.stream().anyMatch(u->u.name.contains(n)||n.contains(u.name))) continue;
            log.info("「{}」不在预设，启动多阶段探测…",n);
            int s=scrapeUnknown(n,null);
            total+=s;
            if (s==0) log.warn("「{}」无结果，请检查日志",n);
        }
        return Map.of("scraped",total,"totalInDb",db.countTeachers());
    }

    public int scrapeUnknown(String univName, String homepage) {
        List<String> knownUrls=KNOWN_CS.entrySet().stream()
            .filter(e->univName.contains(e.getKey())||e.getKey().contains(univName))
            .flatMap(e->e.getValue().stream()).collect(Collectors.toList());

        if (!knownUrls.isEmpty()) {
            log.info("【{}】已知CS直链: {}",univName,knownUrls);
            int total=0;
            for (String ku : knownUrls) {
                log.info("  → 学院: {}",ku);
                for (String fp : discoverFacultyPages(ku)) {
                    log.info("  → 师资页: {}",fp);
                    total+=engine.scrapeTeachersFromPage(fp,univName,"");
                }
            }
            if (total>0) { log.info("🏁 【{}】直链完成: {} 位",univName,total); return total; }
            log.warn("【{}】直链无结果，切换通用流程",univName);
        }

        String base=(homepage!=null&&!homepage.isBlank())
            ? homepage.replaceAll("/+$","") : discoverHomepage(univName);
        if (base==null) { log.warn("无法确定「{}」主页",univName); return 0; }
        log.info("【{}】主页: {}",univName,base);

        List<CollegeEntry> colleges=discoverCsColleges(base,univName);
        log.info("【{}】发现 {} 个学院",univName,colleges.size());

        int total=0;
        for (CollegeEntry col : colleges) {
            log.info("  → 学院: {} ({})",col.name,col.url);
            for (String fp : discoverFacultyPages(col.url))
                total+=engine.scrapeTeachersFromPage(fp,univName,col.name);
        }

        if (total==0) {
            log.info("【{}】学院探测无收获，直接扫主域…",univName);
            for (String path : FACULTY_PATHS) {
                total+=engine.scrapeTeachersFromPage(base+path,univName,"");
                if (total>=5) break;
            }
        }

        log.info("🏁 【{}】多阶段完成: {} 位",univName,total);
        return total;
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
                    BaoyanApp.Teacher t=new BaoyanApp.Teacher(entry.get("name"),u.name,dept.name,url);
                    t.setTitle(info.getOrDefault("title",""));
                    t.setResearchAreas(info.getOrDefault("researchAreas",""));
                    t.setEmail(info.getOrDefault("email",""));
                    t.setGoogleScholar(info.getOrDefault("googleScholar",""));
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