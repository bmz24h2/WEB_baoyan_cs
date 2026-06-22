package com.baoyan.scraper;

import com.baoyan.model.Teacher;
import com.baoyan.model.SchoolInfo;
import com.baoyan.db.DatabaseService;
import com.baoyan.engine.FetchEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
 * ScraperCore — 爬虫算法基类（abstract）
 *
 * 职责：
 *   - 静态常量（FACULTY_PATHS / LAB_PATHS / KNOWN_CS 等）
 *   - Strategy A-H：edu.cn 直连 URL 发现
 *     discoverHomepage / discoverCsColleges / discoverFacultyPages
 *     robots.txt 挖掘 / AJAX 端点推断 / 深度 Sitemap
 *   - 首页跳转检测 isRedirectedToHomepage
 *   - Strategy N：招生通知间接爬取（研招网 / 搜狗微信 / 百度文库 …）
 *
 * 子类：
 *   IndirectSearch extends ScraperCore（@Service，Spring 唯一实例）
 *   加入 Strategy I/J/K/L/M/N 间接教师搜索策略
 *
 * 设计说明：
 *   本类声明为 abstract，不注册 Spring Bean；
 *   由具体子类 IndirectSearch 携带 @Service 注解对外暴露。
 *   ScraperService 通过 @Autowired ScraperCore core 获得子类实例（多态）。
 */
public abstract class ScraperCore {

    protected static final Logger log = LoggerFactory.getLogger(ScraperCore.class);

    @Autowired protected FetchEngine               engine;
    @Autowired protected DatabaseService db;




    public static final Pattern CS_KW = Pattern.compile(
        "计算机|软件|人工智能|网络|网安|信息安全|数据科学|电子信息|自动化|智能科学|通信|信息科学" +
        "|控制|机器学习|大数据|数字媒体|AI|CS|ICT|SE|EE");

    public static final Pattern FACULTY_KW = Pattern.compile(
        "师资队伍|教师队伍|教职工|全体教师|所有教师|教师名单|师资力量|师资介绍|教师介绍" +
        "|Faculty|People|Staff|Team|Members|Researchers|教师风采|教授风采");

    // ★ robots.txt / sitemap 路径发现用：比 FACULTY_KW 更宽松
    public static final Pattern FACULTY_PATH_KW = Pattern.compile(
        "szdw|jsdw|jsml|jzgry|rcdw|szry|teacher|faculty|staff|people|member|person|rencai");

    public static final List<String> COLLEGE_DIR_PATHS = Arrays.asList(
        "/xxjg/xydh1.htm","/xxjg/xydh.htm","/colleges/","/xyjs/index.htm",
        "/jgsz/xysz.htm","/jgsz/index.htm","/jgsz/xyxx.htm",
        "/szjg/xyjs.htm","/szjg/index.htm",
        "/info/1/list.htm","/about/colleges.htm","/structure.htm",
        "/about/index.htm","/gaikuang/index.htm");

    public static final List<String> FACULTY_PATHS = Arrays.asList(
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
        "/jzgry/","/rencai/","/szrygk/",
        "/jszy/qbjs.htm","/jszy/bjs.htm","/jszy/fjs.htm",
        "/info/1013/list.htm","/info/1014/list.htm","/info/1015/list.htm",
        "/info/1016/list.htm","/info/1017/list.htm","/info/1018/list.htm",
        "/rcdw/index.htm","/rcdw/qbjs.htm",
        "/xkyjy/szdw.htm","/xkyj/szdw.htm");

    // ★ NEW: 实验室/课题组常见路径
    public static final List<String> LAB_PATHS = Arrays.asList(
        "/kycg/sysdjs.htm", "/kycg/index.htm", "/yjjg/index.htm", "/yjjg/list.htm",
        "/labs/", "/research/", "/research/groups/", "/research-groups/",
        "/kxyj/index.htm", "/keyanjigou/", "/yjzx/index.htm",
        "/lab/", "/labs.html", "/kybm/", "/kyjg/", "/yjjgml/",
        "/info/1060/list.htm", "/info/1061/list.htm", "/info/1062/list.htm",
        "/info/1070/list.htm", "/info/1071/list.htm", "/info/1072/list.htm",
        "/jxky/kyjg.htm", "/jxky/yjjg.htm", "/syslists/", "/platbase/",
        "/platform/", "/dept/labs.htm", "/xkyjy/yjjg.htm",
        "/yjjg/yjjgjs.htm", "/yjjg/yjzx.htm", "/yjjg/gjhzjg.htm",
        "/szrygk/yjjg.htm", "/kxyj/yjjg.htm", "/kxyj/yjzx.htm");

    // ★ NEW: 招生通知/预推免公告常见路径
    public static final List<String> NOTICE_PATHS = Arrays.asList(
        "/bszn/", "/yjszs/", "/zsxx/", "/tzgg/", "/tzgg/index.htm",
        "/ggtz/", "/graduate/", "/yjszs/index.htm",
        "/info/3/list.htm", "/info/4/list.htm", "/info/5/list.htm",
        "/zsb/", "/yjsb/", "/baoyan/", "/tuimian/",
        "/tzgg/yjs.htm", "/tzgg/zsxx.htm", "/yjss/tzgg.htm",
        "/zsb/tzgg.htm", "/yjszs/tzgg.htm", "/yjszs/zsxx.htm",
        "/ggxx/", "/tztg/", "/xyzx/", "/xytz/",
        "/notice/", "/news/grad/", "/grad/news/",
        "/portal/siteapp78/index.html", "/content/channel/2/", "/content/channel/3/");

    // ★ NEW: 导师招生计划常见路径
    public static final List<String> PLAN_PATHS = Arrays.asList(
        "/zsb/dszs/", "/yjszs/dszs.htm", "/yjszs/dsmc.htm",
        "/bszn/dsmc.htm", "/graduate/supervisors.htm",
        "/info/1080/list.htm", "/info/1081/list.htm", "/info/1082/list.htm",
        "/recruit/master.htm", "/recruit/phd.htm",
        "/zsxx/yjsds.htm", "/szdw/dsszs.htm", "/yjszs/dszsb.htm",
        "/zsb/yjsds/", "/yjssb/dszs.htm");

    public static final List<String> API_PATHS = Arrays.asList(
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

    public static final List<String> CS_PREFIXES = Arrays.asList(
        "cs","cse","ai","it","ict","se","sist","eecs","comp","dcs",
        "scse","csc","csis","cosc","cis","sse","cst","ist","cca",
        "cc","cie","ce","dase","auto","sds","data","scit","seis",
        "csse","cics","scis","sics","scs","ccs","sci");

    public static final Map<String, List<String>> KNOWN_CS = new LinkedHashMap<>();
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

    public static class CollegeEntry {
        final String name, url;
        CollegeEntry(String n, String u) { name=n; url=u; }
    }
    public String discoverHomepage(String univName) {
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

    public List<CollegeEntry> discoverCsColleges(String baseUrl, String univName) {
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
    public List<String> discoverFacultyPages(String base) {
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
    public int scoreFacultyUrl(String url) {
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
    public List<String> discoverLabPages(String base) {
        List<String> result = new ArrayList<>();
        for (String path : LAB_PATHS) {
            String url = base + path;
            Document doc = engine.fetchRobust(url);
            if (doc == null) continue;
            String combined = doc.title() + " " + doc.text().substring(0, Math.min(300, doc.text().length()));
            if (!FetchEngine.LAB_KW.matcher(combined).find()) continue;
            result.add(url);
            // 跟进页面内的实验室子链接
            for (Element a : doc.select("a[href]")) {
                String href = a.absUrl("href");
                if (href.isEmpty() || engine.skipHref(href)) continue;
                String linkText = a.text();
                if (FetchEngine.LAB_KW.matcher(linkText + " " + href).find()
                        && FetchEngine.baseDomain3(href).equals(FetchEngine.baseDomain3(base))
                        && !result.contains(href)) {
                    result.add(href);
                }
            }
            if (result.size() >= 20) break;
        }
        return result.stream().distinct().limit(30).collect(Collectors.toList());
    }

    public List<String> discoverNoticeListPages(String base) {
        List<String> result = new ArrayList<>();
        for (String path : NOTICE_PATHS) {
            String url = base + path;
            Document doc = engine.fetchRobust(url);
            if (doc == null) continue;
            String combined = doc.title() + " " + doc.text().substring(0, Math.min(400, doc.text().length()));
            // 只要包含任意一个通知关键词就收录
            boolean relevant = FetchEngine.NOTICE_KW.matcher(combined).find()
                || FetchEngine.PLAN_KW.matcher(combined).find()
                || combined.contains("招生") || combined.contains("保研") || combined.contains("推免");
            if (relevant) result.add(url);
            if (result.size() >= 5) break;
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ 首页跳转检测（反爬重定向识别）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 检测 edu.cn 服务器是否把请求重定向到首页（常见反爬手段）。
     *
     * 触发条件（满足任意一条即判为首页重定向）：
     *   1. 最终 URL 路径为 "/" 或空，但请求路径深度 ≥ 2
     *   2. 页面标题仅含校名/学院名（≤12字），无方向性词（师资/通知等）
     *   3. 页面中文姓名链接 < 2 条，且标题极短
     *
     * @param doc          已抓取的 Jsoup Document
     * @param requestedUrl 原始请求 URL（重定向前）
     * @return true = 被重定向到首页，应跳过该 URL
     */
    public boolean isRedirectedToHomepage(Document doc, String requestedUrl) {
        if (doc == null) return false;

        // ① URL 路径深度检测（请求 /szdw/qbjs.htm → 最终 /，属于重定向）
        String finalLoc = doc.location();
        if (finalLoc != null && !finalLoc.isBlank()) {
            try {
                String reqPath   = new URI(requestedUrl).getPath();
                String finalPath = new URI(finalLoc).getPath();
                // 请求路径有 2 段以上，但最终落在根或一级路径
                long reqDepth   = reqPath.chars().filter(c -> c == '/').count();
                long finalDepth = finalPath.chars().filter(c -> c == '/').count();
                if (reqDepth >= 2 && finalDepth <= 1
                        && (finalPath.equals("/") || finalPath.isEmpty() || finalPath.equals("/index.htm")
                            || finalPath.equals("/index.html"))) {
                    log.warn("  ⚠️ 首页重定向: {} → {}", requestedUrl, finalLoc);
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // ② 标题检测：极短且无方向性词 = 首页标题
        String title = doc.title().trim();
        boolean titleIsShort = title.length() <= 12;
        boolean titleHasSection = title.contains("师资") || title.contains("通知")
            || title.contains("招生") || title.contains("实验") || title.contains("导师")
            || title.contains("推免") || title.contains("课题") || title.contains("研究")
            || title.contains("faculty") || title.contains("notice") || title.contains("lab");
        if (titleIsShort && !titleHasSection) {
            // 进一步确认：页面中文姓名链接很少
            long cnLinks = doc.select("a[href]").stream()
                .filter(a -> FetchEngine.CN_NAME_PAT.matcher(a.text().trim()).matches()).count();
            if (cnLinks < 2) {
                log.warn("  ⚠️ 疑似首页（标题过短且无姓名链接）: {} title='{}'", requestedUrl, title);
                return true;
            }
        }

        return false;
    }

    private static final Map<String,String> YANZHAO_IDS;
    static {
        Map<String,String> m = new LinkedHashMap<>();
        // 985 高校
        m.put("清华大学",     "10003"); m.put("北京大学",     "10001");
        m.put("浙江大学",     "10335"); m.put("上海交通大学", "10248");
        m.put("复旦大学",     "10246"); m.put("南京大学",     "10284");
        m.put("中国科学技术大学","10358"); m.put("哈尔滨工业大学","10213");
        m.put("武汉大学",     "10486"); m.put("西安交通大学", "10698");
        m.put("同济大学",     "10247"); m.put("东南大学",     "10286");
        m.put("中山大学",     "10558"); m.put("华中科技大学", "10487");
        m.put("北京航空航天大学","10006"); m.put("电子科技大学","10614");
        m.put("四川大学",     "10610"); m.put("厦门大学",     "10384");
        m.put("中南大学",     "10533"); m.put("山东大学",     "10422");
        m.put("湖南大学",     "10532"); m.put("重庆大学",     "10611");
        m.put("天津大学",     "10056"); m.put("南开大学",     "10055");
        m.put("大连理工大学", "10141"); m.put("华东师范大学", "10269");
        m.put("华南理工大学", "10561"); m.put("西北工业大学", "10699");
        m.put("吉林大学",     "10183"); m.put("东北大学",     "10145");
        m.put("兰州大学",     "10730"); m.put("北京理工大学", "10007");
        m.put("北京航空",     "10006"); m.put("北京理工",     "10007");
        // 211 / 特色高校
        m.put("江南大学",     "10295"); m.put("苏州大学",     "10285");
        m.put("南京理工大学", "10288"); m.put("南京航空航天大学","10287");
        m.put("西南交通大学", "10613"); m.put("北京邮电大学", "10013");
        m.put("北京交通大学", "10004"); m.put("西安电子科技大学","10701");
        m.put("南京师范大学", "10319"); m.put("华北电力大学", "10079");
        m.put("北京化工大学", "10010"); m.put("上海大学",     "10280");
        m.put("深圳大学",     "10590"); m.put("南方科技大学", "4611010");
        m.put("哈尔滨工业",   "10213"); m.put("东南大学",     "10286");
        m.put("西安电子",     "10701"); m.put("电子科技",     "10614");
        // ★ 末流 / 弱势 211 补充
        m.put("延边大学",     "10184"); m.put("海南大学",     "10589");
        m.put("宁夏大学",     "10749"); m.put("新疆大学",     "10755");
        m.put("西藏大学",     "10694"); m.put("青海大学",     "10743");
        m.put("东北林业大学", "10225"); m.put("广西大学",     "10593");
        m.put("贵州大学",     "10663"); m.put("云南大学",     "10673");
        m.put("石河子大学",   "10759"); m.put("中国传媒大学", "10033");
        m.put("华北电力大学", "10079"); m.put("中国矿业大学", "10290");
        m.put("河海大学",     "10294"); m.put("合肥工业大学", "10360");
        m.put("燕山大学",     "10216"); m.put("福州大学",     "10386");
        YANZHAO_IDS = Collections.unmodifiableMap(m);
    }

    // ── Strategy N 顶层调度 ──────────────────────────────────────────────────

    public int scrapeInfoFromIndirectSources(String univName, String base) {
        int total = 0;

        // N-1: 研招网（最权威，结构化，命中率最高）
        int n1 = scrapeFromYanzhaoWang(univName);
        total += n1;
        log.info("  [N-1] 研招网: {} 条", n1);

        // N-2: 搜狗微信文章（高校公众号主渠道，搜狗对微信文章索引最全）
        int n2 = scrapeFromSogouWeixin(univName);
        total += n2;
        log.info("  [N-2] 搜狗微信: {} 条", n2);

        // N-3: 百度无限制通知搜索
        if (total < 3) {
            int n3 = scrapeNoticesViaBaiduBroad(univName);
            total += n3;
            log.info("  [N-3] 百度宽搜: {} 条", n3);
        }

        // N-4: 百度文库 / 道客巴巴（招生计划 PDF 集中地）
        if (total < 3) {
            int n4 = scrapeFromDocPlatforms(univName);
            total += n4;
            log.info("  [N-4] 文库平台: {} 条", n4);
        }

        // N-5: Wayback Machine CDX API（爬取 edu.cn 归档招生页）
        if (total < 2) {
            int n5 = scrapeFromWaybackTimeline(univName, base);
            total += n5;
            log.info("  [N-5] Wayback 归档: {} 条", n5);
        }

        // N-6: 内容聚合平台（搜狐/知乎/百家号/头条） — 大量高校招生简章在此转载
        int n6 = scrapeFromContentPlatforms(univName);
        total += n6;
        log.info("  [N-6] 内容平台: {} 条", n6);

        return total;
    }

    // ── N-1: 研招网 ──────────────────────────────────────────────────────────

    /**
     * 研招网（yz.chsi.com.cn）是教育部官方研究生招生服务系统，
     * 收录所有高校的招生专业目录、导师简介和招生计划。
     *
     * 爬取流程：
     *   1. 用 YANZHAO_IDS 直接构造学校主页 URL
     *   2. 若无预设 ID，在研招网内搜索校名
     *   3. 解析导师列表 / 招生简章 / 计划页面
     */
    public int scrapeFromYanzhaoWang(String univName) {
        int count = 0;
        HttpClient hc = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

        // 确定研招网学校 ID
        String schoolId = resolveYanzhaoId(univName, hc);
        if (schoolId == null) {
            log.debug("  N-1 未找到「{}」的研招网 ID", univName);
            return 0;
        }

        // 三类研招网页面：学校信息页 / 招生专业目录 / 招生简章
        List<String> yanzhaoUrls = Arrays.asList(
            "https://yz.chsi.com.cn/sch/schinfo/!view.dhtml?id=" + schoolId,
            "https://yz.chsi.com.cn/zsml/pages/queryBySchool.jsp?schId=" + schoolId,
            "https://yz.chsi.com.cn/apply/ccs/!search.dhtml?schId=" + schoolId
        );

        for (String url : yanzhaoUrls) {
            Document doc = engine.fetchRobust(url);
            if (doc == null) continue;
            count += parseYanzhaoPage(doc, url, univName);
        }

        // 搜索该校当年招生简章（含导师计划）
        try {
            String searchUrl = "https://yz.chsi.com.cn/sch/schinfo/!search.dhtml?sch="
                + URLEncoder.encode(univName.substring(0, Math.min(4, univName.length())), "UTF-8");
            Document searchDoc = engine.fetchRobust(searchUrl);
            if (searchDoc != null) {
                for (Element a : searchDoc.select("a[href*='schId'], a[href*='schinfo']")) {
                    String href = a.absUrl("href");
                    if (href.isEmpty() || !href.contains("chsi.com.cn")) continue;
                    Document page = engine.fetchRobust(href);
                    if (page != null) count += parseYanzhaoPage(page, href, univName);
                    if (count >= 5) break;
                }
            }
        } catch (Exception e) {
            log.debug("  N-1 研招网搜索异常: {}", e.getMessage());
        }

        log.debug("  N-1 研招网 [{}] ID={}: {} 条", univName, schoolId, count);
        return count;
    }

    /** 解析研招网页面，提取导师/招生计划数据并写入 school_info */
    private int parseYanzhaoPage(Document doc, String url, String univName) {
        int count = 0;
        String text = doc.text();
        String title = doc.title();

        // 判断是否与招生/导师/计划相关
        if (!text.contains("导师") && !text.contains("招生") && !text.contains("专业")
                && !FetchEngine.NOTICE_KW.matcher(text).find()) return 0;

        // 提取导师条目（研招网导师页有表格结构：姓名 / 研究方向 / 联系方式）
        for (Element tr : doc.select("tr")) {
            Elements tds = tr.select("td");
            if (tds.size() < 2) continue;
            String cell0 = tds.get(0).text().trim();
            // 首列是 2-5 字中文姓名 → 当作导师条目
            if (!FetchEngine.CN_NAME_PAT.matcher(cell0).matches()) continue;
            String cell1 = tds.size() > 1 ? tds.get(1).text().trim() : "";
            String cell2 = tds.size() > 2 ? tds.get(2).text().trim() : "";

            SchoolInfo info = new SchoolInfo(
                univName, "plan", "导师招生：" + cell0, url, "研招网");
            info.setSnippet(cell0 + (cell1.isEmpty() ? "" : " | " + cell1)
                          + (cell2.isEmpty() ? "" : " | " + cell2));
            if (db.upsertSchoolInfo(info)) count++;
        }

        // 整页作为一个 notice/plan 条目
        if (count == 0 && (FetchEngine.NOTICE_KW.matcher(text).find()
                        || FetchEngine.PLAN_KW.matcher(text).find())) {
            java.util.Map<String,Object> notice = engine.parseNoticePage(doc, url);
            String cat = (String) notice.getOrDefault("category", "notice");
            SchoolInfo info = new SchoolInfo(
                univName, cat, title.isEmpty() ? "研招网招生信息" : title, url, "研招网");
            info.setSnippet((String) notice.getOrDefault("snippet", ""));
            if (db.upsertSchoolInfo(info)) count++;
        }
        return count;
    }

    /** 从 YANZHAO_IDS 或动态搜索获取研招网学校 ID */
    private String resolveYanzhaoId(String univName, HttpClient hc) {
        // 先查静态映射（精确 + 前缀匹配）
        for (Map.Entry<String,String> e : YANZHAO_IDS.entrySet()) {
            if (univName.contains(e.getKey()) || e.getKey().contains(
                    univName.length() > 2 ? univName.substring(0, 2) : univName))
                return e.getValue();
        }
        // 动态：调研招网搜索接口
        try {
            String q = URLEncoder.encode(
                univName.length() >= 4 ? univName.substring(0, 4) : univName, "UTF-8");
            HttpResponse<String> r = hc.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("https://yz.chsi.com.cn/sch/schinfo/!search.dhtml?sch=" + q))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", engine.ua()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                // 页面里有 schId= 参数
                Matcher m = Pattern.compile("schId=(\\d+)").matcher(r.body());
                if (m.find()) return m.group(1);
            }
        } catch (Exception e) {
            log.debug("  N-1 研招网动态 ID 查询失败: {}", e.getMessage());
        }
        return null;
    }

    // ── N-2: 搜狗微信文章搜索 ──────────────────────────────────────────────

    /**
     * 搜狗（sogou.com）对微信公众号文章索引最全面，是高校招生信息的最重要间接来源。
     *
     * 高校通知发布路径：
     *   大学官微 → 学院公众号 → 研究生院公众号 → 搜狗微信索引
     *
     * 搜索接口：
     *   https://weixin.sogou.com/weixin?type=2&query=<关键词>
     *   type=2 表示搜文章（type=1 搜公众号）
     *
     * 典型命中：
     *   - "XX大学人工智能学院2025年接收推荐免试研究生通知"
     *   - "XX大学研究生院关于2025年导师招生计划公示的通知"
     */
    private int scrapeFromSogouWeixin(String univName) {
        int count = 0;
        // 使用动态年份，避免漏掉当年和下一年的内容
        int yr = java.time.Year.now().getValue();
        String years = yr + " OR " + (yr + 1);
        List<String> queries = Arrays.asList(
            univName + " 接收推荐免试研究生",
            univName + " 硕士研究生招生简章 " + yr,
            univName + " 导师招生计划 " + years,
            univName + " 预推免 夏令营 通知",
            univName + " 研究生招生 " + yr,
            univName + " 人工智能 计算机 招生简章"
        );
        Set<String> visited = new LinkedHashSet<>();

        for (String q : queries) {
            if (count >= 10) break;
            try {
                String searchUrl = "https://weixin.sogou.com/weixin?type=2&query="
                    + URLEncoder.encode(q, "UTF-8") + "&ie=utf8";
                Document sg = Jsoup.connect(searchUrl)
                    .userAgent(engine.ua())
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", "https://www.sogou.com/")
                    .timeout(12000).get();

                // 搜狗微信结果结构：.txt-box h3 a 是标题链接
                for (Element a : sg.select(".txt-box h3 a, .news-box h3 a, .wx-rb h3 a")) {
                    String href = a.absUrl("href");
                    if (href.isEmpty() || !visited.add(href)) continue;

                    // 标题相关性过滤：必须含完整校名（或常用缩写），防止「青海民族大学」混入「青海大学」
                    String linkTitle = a.text();
                    // 构造校名匹配：完整名 + 常用2-4字简称（去掉"大学"后缀）
                    String shortName = univName.endsWith("大学")
                        ? univName.substring(0, univName.length() - 2)  // 「青海大学」→「青海」不够区分，所以下面加逻辑
                        : univName;
                    // 校名命中：完整校名 OR (前缀>=3字 且 能区分同省高校)
                    int prefixLen = Math.min(univName.length(), 4); // 最多取4字前缀
                    // 对于「XX大学」型名称，去掉「大学」后如果前缀<=2字（如「青海」），
                    // 必须精确匹配完整校名，避免省名歧义
                    boolean nameHit;
                    if (univName.endsWith("大学") && univName.length() - 2 <= 2) {
                        // 短省名大学（如「青海大学」「海南大学」），必须完整匹配
                        nameHit = linkTitle.contains(univName);
                    } else {
                        // 其他情况：完整校名 OR 4字以内的明确前缀
                        nameHit = linkTitle.contains(univName)
                            || linkTitle.contains(univName.substring(0, Math.min(prefixLen, univName.length())));
                    }
                    boolean relevant = nameHit
                        && (FetchEngine.NOTICE_KW.matcher(linkTitle).find()
                        || FetchEngine.PLAN_KW.matcher(linkTitle).find()
                        || linkTitle.contains("招生") || linkTitle.contains("导师")
                        || linkTitle.contains("推免") || linkTitle.contains("预推免")
                        || linkTitle.contains("保研") || linkTitle.contains("夏令营"));
                    if (!relevant) continue;

                    // 搜狗微信链接会重定向到 mp.weixin.qq.com
                    Document article = engine.fetchRobust(href);
                    if (article == null) continue;

                    java.util.Map<String,Object> notice = engine.parseNoticePage(article, href);
                    String cat = (String) notice.getOrDefault("category", "notice");
                    String noticeTitleStr = linkTitle.isEmpty()
                        ? (String) notice.getOrDefault("title", "微信招生通知") : linkTitle;

                    SchoolInfo info = new SchoolInfo(
                        univName, cat, noticeTitleStr, href, "搜狗微信");
                    info.setSnippet((String) notice.getOrDefault("snippet", ""));
                    if (notice.containsKey("deadline") || notice.containsKey("quota")) {
                        try {
                            java.util.Map<String,Object> extra = new java.util.LinkedHashMap<>();
                            if (notice.containsKey("deadline")) extra.put("deadline", notice.get("deadline"));
                            if (notice.containsKey("quota"))    extra.put("quota",    notice.get("quota"));
                            if (notice.containsKey("gpaReq"))   extra.put("gpaReq",   notice.get("gpaReq"));
                            info.setExtraJson(new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(extra));
                        } catch (Exception ignored) {}
                    }
                    if (db.upsertSchoolInfo(info)) {
                        count++;
                        log.info("    N-2 微信命中: {}", noticeTitleStr.substring(0, Math.min(40, noticeTitleStr.length())));
                    }
                }
            } catch (Exception e) {
                log.debug("  N-2 搜狗微信异常 [{}]: {}", q, e.getMessage());
            }
        }
        return count;
    }

    // ── N-3: 百度无限制通知搜索 ──────────────────────────────────────────────

    /**
     * 用更宽泛的查询词在百度搜索招生/通知内容，不加 site: 限制，
     * 覆盖：知乎专栏 / 小木虫 / 一亩三分地 / 各类教育信息站 / edu.cn 百度快照。
     */
    private int scrapeNoticesViaBaiduBroad(String univName) {
        int count = 0;
        int yr = java.time.Year.now().getValue();
        String years = yr + " OR " + (yr + 1);
        // ★ 招生简章是最常见的转载形式，必须作为主查询词
        List<String> queries = Arrays.asList(
            "\"" + univName + "\" 硕士研究生招生简章 " + yr,
            "\"" + univName + "\" 招生简章 " + years,
            "\"" + univName + "\" 预推免 通知 " + years,
            "\"" + univName + "\" 导师招生计划 人工智能 OR 计算机",
            "\"" + univName + "\" 接收推免 保研 报名 截止",
            "\"" + univName + "\" 夏令营 招募 简历"
        );
        Set<String> visited = new LinkedHashSet<>();

        for (String q : queries) {
            if (count >= 12) break;
            try {
                String searchUrl = "https://www.baidu.com/s?wd="
                    + URLEncoder.encode(q, "UTF-8") + "&rn=20";
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
                    // ★ 扩大相关性判断：加入招生简章、硕士研究生、研招
                    boolean relevant = FetchEngine.NOTICE_KW.matcher(snippet).find()
                        || FetchEngine.PLAN_KW.matcher(snippet).find()
                        || snippet.contains("招生简章") || snippet.contains("硕士研究生招生")
                        || snippet.contains("招生") || snippet.contains("推免")
                        || snippet.contains("导师") || snippet.contains("夏令营")
                        || snippet.contains("研招") || snippet.contains("保研");
                    if (!relevant) continue;

                    String fetchUrl = href.contains(".edu.cn")
                        ? "https://webcache.baidu.com/search?q=cache:"
                            + URLEncoder.encode(href, "UTF-8") + "&hl=zh-CN"
                        : href;
                    Document page = engine.fetchRobust(fetchUrl);
                    if (page == null && !fetchUrl.equals(href)) page = engine.fetchRobust(href);
                    if (page == null) continue;

                    java.util.Map<String,Object> notice = engine.parseNoticePage(page, href);
                    String cat = (String) notice.getOrDefault("category", "notice");
                    String noticeTitle = a.text().trim();
                    if (noticeTitle.isEmpty()) noticeTitle = page.title();

                    SchoolInfo info = new SchoolInfo(
                        univName, cat, noticeTitle, href, "百度间接");
                    info.setSnippet((String) notice.getOrDefault("snippet", ""));
                    if (db.upsertSchoolInfo(info)) {
                        count++;
                        log.info("    N-3 百度命中: {}", noticeTitle.substring(0, Math.min(40, noticeTitle.length())));
                    }
                }
            } catch (Exception e) {
                log.debug("  N-3 百度宽搜异常 [{}]: {}", q, e.getMessage());
            }
        }
        return count;
    }

    // ── N-4: 百度文库 / 道客巴巴 ──────────────────────────────────────────

    /**
     * 很多高校把招生计划表（Excel/PDF）上传到百度文库或道客巴巴，
     * 这类平台有全文搜索且无需登录即可查看摘要。
     *
     * 典型文档标题：
     *   "XX大学20XX年硕士导师招生计划汇总表"
     *   "XX大学研究生院接收推免生拟录取名单"
     */
    private int scrapeFromDocPlatforms(String univName) {
        int count = 0;
        String shortName = univName.length() >= 3 ? univName.substring(0, 3) : univName;
        List<String> queries = Arrays.asList(
            shortName + " 导师招生计划",
            shortName + " 预推免 研究生",
            shortName + " 接收推免 名单"
        );
        Set<String> visited = new LinkedHashSet<>();

        // 百度文库
        for (String q : queries) {
            if (count >= 4) break;
            try {
                String url = "https://wenku.baidu.com/search?word="
                    + URLEncoder.encode(q, "UTF-8") + "&ie=utf-8&lm=-1&od=0&fr=top_home";
                Document doc = engine.fetchRobust(url);
                if (doc == null) continue;

                // 文库结果：.search-result-item 或 .doc-item，标题链接
                for (Element a : doc.select(".search-result-item a[href*='/view/'], .doc-item a[href*='/view/'], a.title[href*='/view/']")) {
                    String href = a.absUrl("href");
                    if (href.isEmpty() || !visited.add(href)) continue;
                    String linkTitle = a.text().trim();
                    if (linkTitle.isEmpty()) continue;

                    // 文库页摘要已够用，不跟进完整文档
                    SchoolInfo info = new SchoolInfo(
                        univName, "plan", "[文库] " + linkTitle, href, "百度文库");
                    Element descEl = a.closest("li,div") != null
                        ? a.closest("li,div").selectFirst(".doc-desc,.description,.abstract") : null;
                    info.setSnippet(descEl != null ? descEl.text() : linkTitle);
                    if (db.upsertSchoolInfo(info)) {
                        count++;
                        log.info("    N-4 文库命中: {}", linkTitle.substring(0, Math.min(40, linkTitle.length())));
                    }
                    if (count >= 4) break;
                }
            } catch (Exception e) {
                log.debug("  N-4 百度文库异常 [{}]: {}", q, e.getMessage());
            }
        }

        // 道客巴巴（doc88.com）补充
        if (count < 2) {
            for (String q : queries.subList(0, 2)) {
                try {
                    String url = "https://www.doc88.com/search/?q="
                        + URLEncoder.encode(q, "UTF-8");
                    Document doc = engine.fetchRobust(url);
                    if (doc == null) continue;
                    for (Element a : doc.select(".doclist-item a.doc-title, .result-list a[href*='/p-']")) {
                        String href = a.absUrl("href");
                        if (href.isEmpty() || !visited.add(href)) continue;
                        String linkTitle = a.text().trim();
                        if (linkTitle.isEmpty()) continue;
                        SchoolInfo info = new SchoolInfo(
                            univName, "plan", "[道客] " + linkTitle, href, "道客巴巴");
                        info.setSnippet(linkTitle);
                        if (db.upsertSchoolInfo(info)) count++;
                        if (count >= 4) break;
                    }
                } catch (Exception e) {
                    log.debug("  N-4 道客巴巴异常: {}", e.getMessage());
                }
                if (count >= 4) break;
            }
        }
        return count;
    }

    // ── N-5: Wayback Machine CDX API ─────────────────────────────────────

    /**
     * Wayback Machine 的 CDX（Capture DeduX）API 返回某 URL 模式的所有快照列表，
     * 比直接搜索 web.archive.org 高效 10 倍，且完全免费无需登录。
     *
     * 用途：即使 edu.cn 当前无法访问，Wayback 里可能有 2-3 年的招生公告历史快照。
     *
     * CDX API 格式：
     *   https://web.archive.org/cdx/search/cdx
     *     ?url=ai.jiangnan.edu.cn/tzgg/*
     *     &output=json&limit=20&fl=timestamp,original
     *     &filter=statuscode:200&collapse=urlkey
     */
    private int scrapeFromWaybackTimeline(String univName, String base) {
        int count = 0;
        if (base == null || base.isBlank()) return 0;

        String host;
        try { host = new URI(base).getHost(); } catch (Exception e) { return 0; }

        // 在已知 edu.cn 域名下搜索通知/招生路径的快照
        List<String> urlPatterns = Arrays.asList(
            host + "/tzgg/*",
            host + "/zsxx/*",
            host + "/yjszs/*",
            host + "/bszn/*",
            host + "/notice/*",
            host + "/*推免*",
            host + "/*招生*",
            host + "/*预推免*"
        );

        HttpClient hc = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

        Set<String> seenUrls = new LinkedHashSet<>();
        for (String pattern : urlPatterns) {
            if (count >= 6) break;
            try {
                String cdxUrl = "https://web.archive.org/cdx/search/cdx"
                    + "?url=" + URLEncoder.encode(pattern, "UTF-8")
                    + "&output=json&limit=10&fl=timestamp,original"
                    + "&filter=statuscode:200&collapse=urlkey"
                    + "&from=20230101";  // 只要 2023 年以来的快照

                HttpResponse<String> resp = hc.send(
                    HttpRequest.newBuilder().uri(URI.create(cdxUrl))
                        .timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) continue;

                String body = resp.body();
                if (!body.startsWith("[[")) continue;

                // 解析 JSON 数组：[["timestamp","url"], ...]
                Pattern rowPat = Pattern.compile("\\[\"(\\d{14})\",\"([^\"]+)\"\\]");
                Matcher m = rowPat.matcher(body);
                while (m.find() && count < 6) {
                    String ts   = m.group(1);
                    String orig = m.group(2);
                    if (!seenUrls.add(orig)) continue;

                    // 构造 Wayback 快照 URL
                    String archiveUrl = "https://web.archive.org/web/" + ts + "/" + orig;
                    Document doc = engine.fetchRobust(archiveUrl);
                    if (doc == null) continue;

                    String pageType = engine.detectPageType(doc, orig);
                    if ("teacher_list".equals(pageType)) continue;  // 跳过教师页

                    java.util.Map<String,Object> notice = engine.parseNoticePage(doc, orig);
                    String cat = (String) notice.getOrDefault("category", "notice");
                    String title = doc.title();
                    if (title.isBlank()) title = "归档页：" + orig;

                    SchoolInfo info = new SchoolInfo(
                        univName, cat, title, orig, "Wayback/" + ts.substring(0, 8));
                    info.setSnippet((String) notice.getOrDefault("snippet", ""));
                    if (db.upsertSchoolInfo(info)) {
                        count++;
                        log.info("    N-5 Wayback 命中: {} ({})", title.substring(0, Math.min(40, title.length())), ts.substring(0, 8));
                    }
                }
            } catch (Exception e) {
                log.debug("  N-5 Wayback CDX 异常 [{}]: {}", pattern, e.getMessage());
            }
        }
        return count;
    }

    // ── N-6: 内容聚合平台（搜狐 / 知乎 / 百家号 / 今日头条）─────────────────

    /**
     * 搜狐/知乎/百家号/今日头条 是中国高校招生简章的最主要转载平台。
     *
     * 典型内容：
     *   "2026年XX大学硕士研究生招生简章解读"（搜狐）
     *   "XX大学2026考研报考须知，含招生目录、报名时间"（百家号）
     *   "XX大学计算机学院2026年接收推荐免试研究生公告"（知乎）
     *
     * 策略：用 Baidu site: 限定搜索，精准打到各平台高置信度文章。
     * 比无限制搜索更快命中，因为这些平台的文章 Baidu 必然收录。
     */
    private int scrapeFromContentPlatforms(String univName) {
        int count = 0;
        int yr = java.time.Year.now().getValue();
        String shortName = univName.length() >= 3 ? univName.substring(0, 3) : univName;

        // 平台 site: + 查询词组合
        // 每个平台用 2 个查询词，共约 10 次搜索，每次最多取前 5 条
        String[][] platformQueries = {
            // {site, query}
            {"sohu.com",               shortName + " 招生简章 " + yr},
            {"sohu.com",               shortName + " 研究生招生 " + yr},
            {"zhihu.com",              shortName + " 招生简章"},
            {"zhihu.com",              shortName + " 预推免 导师"},
            {"baijiahao.baidu.com",    shortName + " 招生简章 " + yr},
            {"baijiahao.baidu.com",    shortName + " 研究生招生"},
            {"mp.weixin.qq.com",       univName  + " 招生简章"},
            {"mp.weixin.qq.com",       univName  + " 接收推荐免试"},
            {"toutiao.com",            shortName + " 招生简章 " + yr},
            {"sina.com.cn",            shortName + " 招生简章 " + yr},
        };

        Set<String> visited = new LinkedHashSet<>();
        HttpClient hc = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

        for (String[] pq : platformQueries) {
            if (count >= 10) break;
            String site  = pq[0];
            String query = pq[1];
            try {
                String searchUrl = "https://www.baidu.com/s?wd="
                    + URLEncoder.encode("site:" + site + " " + query, "UTF-8")
                    + "&rn=5";
                Document bd = Jsoup.connect(searchUrl)
                    .userAgent(engine.ua())
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .referrer("https://www.baidu.com/")
                    .timeout(10000).get();

                for (Element a : bd.select(".result h3 a, .c-container .t a")) {
                    String href = a.absUrl("href");
                    if (href.isEmpty() || href.contains("baidu.com")) continue;
                    if (!visited.add(href)) continue;

                    String linkTitle = a.text().trim();
                    // ★ 修复：精确校名匹配，防止同省其他高校（如「青海民族大学」）混入「青海大学」
                    // 对短省名大学（前缀<=2字），必须完整匹配校名
                    boolean nameRelevantN6;
                    if (univName.endsWith("大学") && univName.length() - 2 <= 2) {
                        nameRelevantN6 = linkTitle.contains(univName);
                    } else {
                        nameRelevantN6 = linkTitle.contains(univName)
                            || linkTitle.contains(univName.substring(0, Math.min(4, univName.length())));
                    }
                    boolean titleRelevant = nameRelevantN6
                        || linkTitle.contains("招生") || linkTitle.contains("推免")
                        || linkTitle.contains("研究生") || linkTitle.contains("导师");
                    if (!titleRelevant) continue;

                    // 直接抓取页面，不走 webcache（搜狐/知乎等无需）
                    Document page = engine.fetchRobust(href);
                    if (page == null) continue;

                    java.util.Map<String,Object> notice = engine.parseNoticePage(page, href);
                    String cat = (String) notice.getOrDefault("category", "notice");
                    // 优先用链接标题（更短更准），否则用页面标题
                    String title = linkTitle.isEmpty() ? page.title() : linkTitle;

                    SchoolInfo info = new SchoolInfo(
                        univName, cat, title, href, site + "（N-6）");
                    info.setSnippet((String) notice.getOrDefault("snippet", ""));
                    if (notice.containsKey("deadline") || notice.containsKey("quota")) {
                        try {
                            java.util.Map<String,Object> extra = new java.util.LinkedHashMap<>();
                            if (notice.containsKey("deadline")) extra.put("deadline", notice.get("deadline"));
                            if (notice.containsKey("quota"))    extra.put("quota",    notice.get("quota"));
                            info.setExtraJson(new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(extra));
                        } catch (Exception ignored) {}
                    }
                    if (db.upsertSchoolInfo(info)) {
                        count++;
                        log.info("    N-6 [{}] 命中: {}", site, title.substring(0, Math.min(45, title.length())));
                    }
                    if (count >= 10) break;
                }
            } catch (Exception e) {
                log.debug("  N-6 平台搜索异常 [{} {}]: {}", site, query, e.getMessage());
            }
        }
        return count;
    }
}