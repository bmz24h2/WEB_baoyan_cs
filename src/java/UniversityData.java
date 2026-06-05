package com.baoyan;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UniversityData.java — 所有静态数据
 *
 * 数据来源说明：
 *   强/弱 com 数据  ← 用户提供的小红书图片（原帖 @8052244911），100% 来自用户
 *   官网 URL       ← 各校已知域名，置信度标注 HIGH/MEDIUM/LOW
 *   小红书关键词    ← 与强弱com数据一一对应，来自用户图片
 *
 * URL 置信度：
 *   HIGH   已知稳定域名，大概率有效
 *   MEDIUM 推测路径，可能因学校改版失效，建议用 ScraperService.verifyUrls() 验证
 *   LOW    仅知主站域名，子路径不确定
 *
 * com 状态（强弱com）：
 *   STRONG   强com（接收外校推免生）
 *   WEAK     弱com（基本不接收外校推免生）
 *   MIX_DS   博士弱 + 硕士强
 *   MIX_SD   博士强 + 硕士弱
 *   CAS_MIX  硕士强 + 博士弱（中科院特有表述）
 *   UNKNOWN  图中未收录，状态不明
 */
public class UniversityData {

    // ── com 状态枚举 ─────────────────────────────────────────────────────────
    public enum ComStatus {
        STRONG("strong", "强"),
        WEAK("weak", "弱"),
        MIX_DS("mix_ds", "博弱+硕强"),
        MIX_SD("mix_sd", "博强+硕弱"),
        CAS_MIX("cas_mix", "硕强+博弱"),
        UNKNOWN("unknown", "未知");

        public final String code;
        public final String label;

        ComStatus(String code, String label) {
            this.code  = code;
            this.label = label;
        }

        public boolean hasStrongComponent() {
            return this == STRONG || this == MIX_DS || this == CAS_MIX;
        }
    }

    // ── 分页配置 ─────────────────────────────────────────────────────────────
    public static class PaginationConfig {
        public final String urlPattern;  // 如 "https://cs.nju.edu.cn/58/list{page}.htm"
        public final int start;
        public final int maxPages;

        public PaginationConfig(String urlPattern, int start, int maxPages) {
            this.urlPattern = urlPattern;
            this.start      = start;
            this.maxPages   = maxPages;
        }

        /** 生成所有分页 URL */
        public List<String> generateUrls() {
            List<String> urls = new ArrayList<>();
            for (int i = start; i <= maxPages; i++) {
                urls.add(urlPattern.replace("{page}", String.valueOf(i)));
            }
            return urls;
        }
    }

    // ── 院系配置 ─────────────────────────────────────────────────────────────
    public static class DeptConfig {
        public final String name;
        public final String url;
        public final String urlConfidence;          // HIGH / MEDIUM / LOW
        public final String teacherLinkSelector;    // CSS selector for teacher links
        public final String nameSelector;           // CSS selector for teacher name
        public final boolean dynamic;               // 是否需要 Playwright 渲染
        public final String encoding;
        public final ComStatus comStatus;           // 来自用户图片
        public final PaginationConfig pagination;   // null = 无分页
        public final String note;

        private DeptConfig(Builder b) {
            this.name                = b.name;
            this.url                 = b.url;
            this.urlConfidence       = b.urlConfidence;
            this.teacherLinkSelector = b.teacherLinkSelector;
            this.nameSelector        = b.nameSelector;
            this.dynamic             = b.dynamic;
            this.encoding            = b.encoding;
            this.comStatus           = b.comStatus;
            this.pagination          = b.pagination;
            this.note                = b.note;
        }

        /** 该院系的所有列表页 URL（含分页） */
        public List<String> getAllListUrls() {
            List<String> urls = new ArrayList<>();
            urls.add(this.url);
            if (pagination != null) {
                urls.addAll(pagination.generateUrls());
            }
            return urls;
        }

        public static class Builder {
            private final String name;
            private final String url;
            private String urlConfidence  = "LOW";
            private String teacherLinkSelector = "a[href]";
            private String nameSelector   = "h3";
            private boolean dynamic       = false;
            private String encoding       = "utf-8";
            private ComStatus comStatus   = ComStatus.UNKNOWN;
            private PaginationConfig pagination = null;
            private String note           = "";

            public Builder(String name, String url) {
                this.name = name;
                this.url  = url;
            }
            public Builder confidence(String c)    { this.urlConfidence = c; return this; }
            public Builder linkSel(String s)       { this.teacherLinkSelector = s; return this; }
            public Builder nameSel(String s)       { this.nameSelector = s; return this; }
            public Builder dynamic()               { this.dynamic = true; return this; }
            public Builder encoding(String e)      { this.encoding = e; return this; }
            public Builder com(ComStatus c)        { this.comStatus = c; return this; }
            public Builder pagination(String p, int s, int max) {
                this.pagination = new PaginationConfig(p, s, max); return this;
            }
            public Builder note(String n)          { this.note = n; return this; }
            public DeptConfig build()              { return new DeptConfig(this); }
        }
    }

    // ── 高校配置 ─────────────────────────────────────────────────────────────
    public static class UniversityConfig {
        public final int rank;
        public final String name;
        public final String province;
        public final String tier;
        public final String homepage;
        public final String gradAdmission;
        public final List<DeptConfig> departments;
        public final String note;
        public final List<String> xhsKeywords;

        public UniversityConfig(int rank, String name, String province, String tier,
                                String homepage, String gradAdmission,
                                List<DeptConfig> departments,
                                String note, List<String> xhsKeywords) {
            this.rank          = rank;
            this.name          = name;
            this.province      = province;
            this.tier          = tier;
            this.homepage      = homepage;
            this.gradAdmission = gradAdmission;
            this.departments   = Collections.unmodifiableList(new ArrayList<>(departments));
            this.note          = note != null ? note : "";
            this.xhsKeywords   = Collections.unmodifiableList(new ArrayList<>(xhsKeywords));
        }

        public boolean hasStrongDept() {
            return departments.stream().anyMatch(d -> d.comStatus.hasStrongComponent());
        }

        public boolean isAllWeak() {
            return !departments.isEmpty() &&
                   departments.stream().noneMatch(d -> d.comStatus.hasStrongComponent());
        }

        /** 生成 Bing site:xiaohongshu.com 搜索链接 */
        public List<String> getBingXhsUrls() {
            return xhsKeywords.stream()
                .map(kw -> {
                    String encoded = URLEncoder.encode("site:xiaohongshu.com " + kw,
                                                       StandardCharsets.UTF_8);
                    return "https://www.bing.com/search?q=" + encoded + "&setlang=zh-CN";
                })
                .collect(Collectors.toList());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 高校数据列表（按排名排序）
    // ════════════════════════════════════════════════════════════════════════
    public static final List<UniversityConfig> ALL = new ArrayList<>();
    public static final Map<String, UniversityConfig> BY_NAME = new LinkedHashMap<>();

    static {

        // ── 顶尖 C9 ──────────────────────────────────────────────────────────

        add(new UniversityConfig(1, "清华大学", "北京", "顶尖C9",
            "https://www.tsinghua.edu.cn", "https://yz.tsinghua.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机科学与技术系",
                    "https://www.cs.tsinghua.edu.cn/csen/Faculty/All_Faculty.htm")
                    .confidence("MEDIUM").linkSel("td a, .faculty-item a").nameSel("td:first-child, h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("软件学院",
                    "https://www.thss.tsinghua.edu.cn/faculty.htm")
                    .confidence("MEDIUM").linkSel(".teacher-list a").nameSel("h4, .name")
                    .dynamic().com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("交叉信息研究院(IIIS)",
                    "https://iiis.tsinghua.edu.cn/en/faculty/")
                    .confidence("HIGH").linkSel(".faculty-member a").nameSel(".name")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("深圳国际研究生院",
                    "https://www.sz.tsinghua.edu.cn/info/1003/1472.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("智能网络研究中心",
                    "https://www.insc.tsinghua.edu.cn/info/1049/1052.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("自动化系",
                    "https://www.au.tsinghua.edu.cn/info/1078/1765.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.MIX_DS).note("博士弱硕士强").build(),
                new DeptConfig.Builder("电机系",
                    "https://www.eea.tsinghua.edu.cn/yjszs/jsmd.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("生物医学工程系",
                    "https://www.bme.tsinghua.edu.cn/info/1053/1292.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "计算机系和软件学院强com；IIIS（YAO班）弱com且极难；自动化系博士弱硕士强",
            Arrays.asList("清华计算机保研", "THU CS夏令营", "清华IIIS保研")
        ));

        add(new UniversityConfig(2, "北京大学", "北京", "顶尖C9",
            "https://www.pku.edu.cn", "https://admission.pku.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "https://cs.pku.edu.cn/Faculty1/All1.htm")
                    .confidence("MEDIUM").linkSel("a[href*='teacher'], .faculty a").nameSel("h3, .name")
                    .dynamic().com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("软件与微电子学院",
                    "https://www.ss.pku.edu.cn/index.php/teachersmain/szdwqb")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("智能学院",
                    "https://www.iai.pku.edu.cn/szdw/zrjs/index.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("电子学院",
                    "https://eecs.pku.edu.cn/Faculty/AllFaculty.htm")
                    .confidence("MEDIUM").linkSel(".teacher a").nameSel(".name, h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("深圳研究生院",
                    "https://www.pkusz.edu.cn/faculty/")
                    .confidence("LOW").linkSel("a[href*='faculty']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("前沿交叉学科研究院",
                    "https://www.aais.pku.edu.cn/szdw/index.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("未来技术学院",
                    "https://www.coe.pku.edu.cn/teaching/team/index.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("医学技术学院",
                    "https://smt.bjmu.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("工学院",
                    "https://www.coe.pku.edu.cn/teaching/team/")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "计算机学院弱com；软件与微电子、电子学院、前沿交叉研究院强com；两个院系分开报考",
            Arrays.asList("北大计算机保研", "PKU夏令营", "北大信科保研")
        ));

        add(new UniversityConfig(3, "浙江大学", "浙江", "顶尖C9",
            "https://www.zju.edu.cn", "https://grs.zju.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机科学与技术学院",
                    "https://www.cs.zju.edu.cn/csen/24551/list.htm")
                    .confidence("MEDIUM").linkSel(".teacher-item a, li a[href*='teacher']").nameSel(".teacher-name, h3")
                    .pagination("https://www.cs.zju.edu.cn/csen/24551/list{page}.htm", 2, 5)
                    .com(ComStatus.MIX_DS).note("博士弱+硕士强").build(),
                new DeptConfig.Builder("软件学院",
                    "https://www.cst.zju.edu.cn/cstzjuen/27494/list.htm")
                    .confidence("MEDIUM").linkSel(".faculty a").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("控制科学与工程学院",
                    "https://www.cse.zju.edu.cn/cseen/26228/list.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("电气工程学院",
                    "https://www.ee.zju.edu.cn/eeen/26003/list.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("工程师学院",
                    "https://www.cpe.zju.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("生物医学工程与仪器科学学院",
                    "https://www.bme.zju.edu.cn/bmeen/26528/list.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "计算机学院博士弱+硕士强；软件学院弱com；电气、工程师、生医工强com",
            Arrays.asList("浙大计算机保研", "ZJU夏令营")
        ));

        add(new UniversityConfig(4, "上海交通大学", "上海", "顶尖C9",
            "https://www.sjtu.edu.cn", "https://www.gs.sjtu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("电子信息与电气工程学院",
                    "https://www.cs.sjtu.edu.cn/en/Faculty.aspx")
                    .confidence("HIGH").linkSel(".faculty-list a, table a").nameSel("td:nth-child(1), .name")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("博渊未来技术学院",
                    "https://byftc.sjtu.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("高级金融学院-计算机方向",
                    "https://saif.sjtu.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='faculty']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("智慧能源创新学院",
                    "https://ai.sjtu.edu.cn/about/faculty")
                    .confidence("HIGH").linkSel(".people-item a").nameSel(".people-name")
                    .dynamic().com(ComStatus.STRONG).build()
            ),
            "电院（CS主系）弱com；高金计算机方向、智慧能源学院强com",
            Arrays.asList("交大计算机保研", "SJTU夏令营", "交大ACM班")
        ));

        add(new UniversityConfig(5, "复旦大学", "上海", "顶尖C9",
            "https://www.fudan.edu.cn", "https://gs.fudan.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机科学技术学院",
                    "https://cs.fudan.edu.cn/57/list.htm")
                    .confidence("MEDIUM").linkSel(".teacher a, .ullist a").nameSel("h3, .name")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("信息科学与工程学院",
                    "https://sist.fudan.edu.cn/szdw/qbjs.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("工程与应用技术研究院",
                    "https://www.engineering.fudan.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("航空航天学院",
                    "https://asa.fudan.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("数字医学中心",
                    "https://cfdm.fudan.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("软件学院",
                    "https://sds.fudan.edu.cn/faculty1/list.htm")
                    .confidence("MEDIUM").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "计算机、软件、工程研究院、数字医学中心均强com；信息学院、航空航天学院弱com",
            Arrays.asList("复旦计算机保研", "Fudan夏令营")
        ));

        add(new UniversityConfig(6, "南京大学", "江苏", "顶尖C9",
            "https://www.nju.edu.cn", "https://grawww.nju.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "https://cs.nju.edu.cn/58/list.htm")
                    .confidence("MEDIUM").linkSel(".teacher-list a").nameSel("h3")
                    .pagination("https://cs.nju.edu.cn/58/list{page}.htm", 2, 4)
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("软件学院",
                    "https://software.nju.edu.cn/szygk/szyjj/index.html")
                    .confidence("MEDIUM").linkSel(".teacher a").nameSel(".teacher-name")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("人工智能学院",
                    "https://ai.nju.edu.cn/szdw/zrjs/index.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("电子学院",
                    "https://ese.nju.edu.cn/szdw/qbjs.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "四个院系全部强com，顶尖985里接收外校最开放之一",
            Arrays.asList("南大计算机保研", "NJU夏令营")
        ));

        add(new UniversityConfig(7, "中国科学技术大学", "安徽", "顶尖C9",
            "https://www.ustc.edu.cn", "https://gradschool.ustc.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机科学与技术学院",
                    "https://cs.ustc.edu.cn/2021/0901/c1085a502734/page.htm")
                    .confidence("LOW").linkSel("a[href*='teacher'], .faculty-list a").nameSel("h3, .name")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("信息与科学工程学院",
                    "https://sist.ustc.edu.cn/2021/0412/c2756a481494/page.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h4, strong")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("六系（信息学部）",
                    "https://eeis.ustc.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("大数据学院",
                    "https://ds.ustc.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("先进技术研究院",
                    "https://iat.ustc.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "计算机学院弱com；信科院、六系、大数据学院强com",
            Arrays.asList("科大计算机保研", "USTC夏令营", "科大直博")
        ));

        add(new UniversityConfig(7, "国防科技大学", "湖南", "顶尖C9",
            "https://www.nudt.edu.cn", "https://www.nudt.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "https://www.nudt.edu.cn/xkjj/jsjkxy/index.htm")
                    .confidence("LOW").linkSel("a[href*='info'], ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).note("军校，部分页面不对外开放").build()
            ),
            "军校，主要招军籍生，对外名额极少，强弱com图中未详细收录",
            Arrays.asList("国防科大保研")
        ));

        // ── 上游 985 ────────────────────────────────────────────────────────

        add(new UniversityConfig(8, "华中科技大学", "湖北", "上游985",
            "https://www.hust.edu.cn", "https://gs.hust.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "http://faculty.hust.edu.cn/cs/")
                    .confidence("LOW").linkSel(".teacher a, .person-list a").nameSel("h3, .name")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("网络空间安全学院",
                    "http://cse.hust.edu.cn/info/1025/2007.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("人工智能学院",
                    "http://aia.hust.edu.cn/Faculty/All.htm")
                    .confidence("LOW").linkSel("a[href*='szdw']").nameSel("td, .name")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("电子与电气工程学院",
                    "http://eee.hust.edu.cn/info/1091/3006.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("光学与电子信息学院",
                    "http://oei.hust.edu.cn/info/1038/4085.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("武汉光电国家研究中心",
                    "http://wnlo.hust.edu.cn/info/1015/3072.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("机械科学与工程学院",
                    "http://mse.hust.edu.cn/info/1022/6083.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "计算机学院弱com；光学与电子信息学院强com；华为在武汉联培机会多",
            Arrays.asList("华科计算机保研", "HUST夏令营")
        ));

        add(new UniversityConfig(9, "武汉大学", "湖北", "上游985",
            "https://www.whu.edu.cn", "https://gs.whu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "https://cs.whu.edu.cn/index/szdw/jsjkxyjsxy.htm")
                    .confidence("MEDIUM").linkSel(".teacher a, ul li a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("信息学院",
                    "https://ism.whu.edu.cn/info/1055/2282.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("国家网络安全学院",
                    "https://cse.whu.edu.cn/index/szdw1.htm")
                    .confidence("MEDIUM").linkSel(".faculty a").nameSel("h4")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("测绘遥感信息工程国家重点实验室",
                    "http://www.lmars.whu.edu.cn/index.php?g=&m=article&a=index&id=157")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("电气与自动化学院",
                    "https://eas.whu.edu.cn/info/1022/2099.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "计算机、信息、网安、电气四院系强com；测绘遥感实验室弱com",
            Arrays.asList("武大计算机保研", "WHU网安夏令营")
        ));

        add(new UniversityConfig(10, "西安交通大学", "陕西", "上游985",
            "https://www.xjtu.edu.cn", "https://gs.xjtu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "http://www.cs.xjtu.edu.cn/szdw/qbjs.htm")
                    .confidence("MEDIUM").linkSel("a[href*='szdw'], table a").nameSel("td:nth-child(2), h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("网络空间安全学院",
                    "http://sccs.xjtu.edu.cn/info/1008/2476.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("人工智能与机器人所",
                    "http://www.aiar.xjtu.edu.cn/szdw/zzjs.htm")
                    .confidence("MEDIUM").linkSel("a[href*='info']").nameSel("td, h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("电子与信息学部",
                    "http://sie.xjtu.edu.cn/info/1014/3208.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("电气工程学院",
                    "http://ee.xjtu.edu.cn/info/1014/5028.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("微电子学院",
                    "http://semi.xjtu.edu.cn/info/1082/2534.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("未来技术学院",
                    "http://ftc.xjtu.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("钱学森学院",
                    "http://qxs.xjtu.edu.cn/info/1006/1163.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "计算机学院弱com；AI与机器人所、未来技术学院强com；C9成员",
            Arrays.asList("西交大计算机保研", "XJTU夏令营")
        ));

        add(new UniversityConfig(11, "中山大学", "广东", "上游985",
            "https://www.sysu.edu.cn", "https://graduate.sysu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("数据科学与计算机学院",
                    "https://cse.sysu.edu.cn/content/3689")
                    .confidence("MEDIUM").linkSel(".teacher-item a, li a[href*='content']").nameSel(".name, h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("航空航天学院",
                    "https://ssa.sysu.edu.cn/teacher")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "图中两个院系均强com",
            Arrays.asList("中大计算机保研", "SYSU夏令营")
        ));

        add(new UniversityConfig(12, "北京航空航天大学", "北京", "上游985",
            "https://www.buaa.edu.cn", "https://ev.buaa.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("软件学院",
                    "https://www.ssa.buaa.edu.cn/szll/xzjs/index.htm")
                    .confidence("MEDIUM").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("人工智能学院",
                    "https://scse.buaa.edu.cn/info/1094/2454.htm")
                    .confidence("MEDIUM").linkSel("a[href*='info/1094'], .teacher a").nameSel("h3, td")
                    .com(ComStatus.STRONG).build()
            ),
            "软件学院和AI学院均强com（图中仅收录这两个）",
            Arrays.asList("北航计算机保研", "BUAA夏令营")
        ));

        add(new UniversityConfig(13, "东南大学", "江苏", "上游985",
            "https://www.seu.edu.cn", "https://gs.seu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机科学与工程学院",
                    "https://cse.seu.edu.cn/24611/list.htm")
                    .confidence("MEDIUM").linkSel(".teacher a, ul li a").nameSel("h3, .name")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("信息科学与工程学院",
                    "https://radio.seu.edu.cn/2019/0118/c19971a270013/page.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("软件学院",
                    "https://software.seu.edu.cn/3032/list.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("人工智能学院",
                    "https://ai.seu.edu.cn/szdw/qb/index.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("网络空间安全学院",
                    "https://cyber.seu.edu.cn/szdw1/qbjs.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("电子科学与工程学院",
                    "https://ese.seu.edu.cn/szdw1/qbsz.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("集成电路学院",
                    "https://ic.seu.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("电气工程学院",
                    "https://electrical.seu.edu.cn/szdw1/qbjs.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("自动化学院",
                    "https://automation.seu.edu.cn/szdw1/qbjs.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "计算机学院、软件学院弱com；其余七个院系均强com，整体开放",
            Arrays.asList("东南大学计算机保研", "SEU夏令营")
        ));

        add(new UniversityConfig(14, "北京理工大学", "北京", "上游985",
            "https://www.bit.edu.cn", "https://gs.bit.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "https://cs.bit.edu.cn/szdw/jsml/js/index.htm")
                    .confidence("MEDIUM").linkSel("ul li a[href*='info']").nameSel("h3, td")
                    .com(ComStatus.WEAK).build()
            ),
            "图中仅收录计算机学院，弱com",
            Arrays.asList("北理工计算机保研", "BIT夏令营")
        ));

        add(new UniversityConfig(15, "四川大学", "四川", "上游985",
            "https://www.scu.edu.cn", "https://gs.scu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "https://cs.scu.edu.cn/info/1007/1342.htm")
                    .confidence("MEDIUM").linkSel("a[href*='info/1'], ul li a").nameSel("h3, td")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("网络空间安全学院",
                    "https://scs.scu.edu.cn/szdw/zrjs.htm")
                    .confidence("MEDIUM").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("电子信息学院",
                    "https://eie.scu.edu.cn/info/1007/1042.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("电气工程学院",
                    "https://ee.scu.edu.cn/info/1007/1128.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "图中四个院系全部强com",
            Arrays.asList("川大计算机保研", "SCU夏令营")
        ));

        add(new UniversityConfig(16, "哈尔滨工业大学", "黑龙江", "上游985",
            "https://www.hit.edu.cn", "https://gs.hit.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学部",
                    "https://cs.hit.edu.cn/szdw/qbjs.htm")
                    .confidence("MEDIUM").linkSel("ul li a, table a").nameSel("h3, td")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("电气工程及自动化学院",
                    "https://auto.hit.edu.cn/szdw/qbjs.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "两个院系均强com；深圳校区单独招生，可单独查询",
            Arrays.asList("哈工大计算机保研", "HIT夏令营", "哈工大深圳保研")
        ));

        // ── 中游 985 ────────────────────────────────────────────────────────

        add(new UniversityConfig(17, "同济大学", "上海", "中游985",
            "https://www.tongji.edu.cn", "https://yjsc.tongji.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("电子与信息工程学院计算机系",
                    "https://cs.tongji.edu.cn/info/1054/2283.htm")
                    .confidence("LOW").linkSel("a[href*='info/105'], ul li a").nameSel("h3, td")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("软件学院",
                    "https://sse.tongji.edu.cn/szdw/qbjs.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "两个院系均强com",
            Arrays.asList("同济计算机保研", "Tongji夏令营")
        ));

        add(new UniversityConfig(18, "中国人民大学", "北京", "中游985",
            "https://www.ruc.edu.cn", "https://pgs.ruc.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("信息学院",
                    "https://info.ruc.edu.cn/faculty/list.htm")
                    .confidence("MEDIUM").linkSel("a.teacher, ul li a[href*='faculty']").nameSel("h3, .name")
                    .com(ComStatus.UNKNOWN).build()
            ),
            "图中未收录，请查阅官网",
            Arrays.asList("人大信息保研", "RUC夏令营")
        ));

        add(new UniversityConfig(19, "北京师范大学", "北京", "中游985",
            "https://www.bnu.edu.cn", "https://yjsy.bnu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("人工智能学院",
                    "https://ai.bnu.edu.cn/xygk/szdw/index.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.UNKNOWN).build()
            ),
            "图中未收录，请查阅官网",
            Arrays.asList("北师大AI保研")
        ));

        add(new UniversityConfig(20, "天津大学", "天津", "中游985",
            "https://www.tju.edu.cn", "https://gs.tju.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("智能与计算学部",
                    "https://cic.tju.edu.cn/faculty/teacherlist_js.html")
                    .confidence("MEDIUM").linkSel("a[href*='teacherOld'], ul li a").nameSel("td:first-child, .name")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("电气工程学院",
                    "https://see.tju.edu.cn/info/1008/4688.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "智能与计算学部强com；电气工程学院弱com",
            Arrays.asList("天大计算机保研", "TJU夏令营")
        ));

        add(new UniversityConfig(21, "南开大学", "天津", "中游985",
            "https://www.nankai.edu.cn", "https://graduate.nankai.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "https://cc.nankai.edu.cn/szdw/qbjs.htm")
                    .confidence("MEDIUM").linkSel("a[href*='info'], ul li a").nameSel("h3, td")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("软件学院",
                    "https://cs.nankai.edu.cn/szdw/index.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "计算机学院和软件学院均弱com",
            Arrays.asList("南开计算机保研", "NKU夏令营")
        ));

        add(new UniversityConfig(22, "山东大学", "山东", "中游985",
            "https://www.sdu.edu.cn", "https://www.grad.sdu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机科学与技术学院",
                    "https://www.cs.sdu.edu.cn/info/1012/1127.htm")
                    .confidence("MEDIUM").linkSel("a[href*='info/10'], ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("软件学院",
                    "https://www.software.sdu.edu.cn/info/1008/3297.htm")
                    .confidence("MEDIUM").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("网络空间安全学院",
                    "https://ncs.sdu.edu.cn/info/1005/1005.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("电气工程学院",
                    "https://ee.sdu.edu.cn/info/1008/4244.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "图中四个院系全部强com",
            Arrays.asList("山大计算机保研", "SDU夏令营")
        ));

        add(new UniversityConfig(23, "西北工业大学", "陕西", "中游985",
            "https://www.nwpu.edu.cn", "https://gs.nwpu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学部",
                    "https://jsj.nwpu.edu.cn/info/1272/6094.htm")
                    .confidence("LOW").linkSel("a[href*='info/12'], ul li a").nameSel("h3, td")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("网络空间安全学院",
                    "https://coe.nwpu.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("软件学院",
                    "https://www.nwpu.edu.cn/info/1141/9175.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("电子信息学部",
                    "https://eie.nwpu.edu.cn/info/1018/7089.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "计算机、网安、软件均弱com；电子信息学部强com",
            Arrays.asList("西工大计算机保研", "NPU夏令营")
        ));

        add(new UniversityConfig(24, "厦门大学", "福建", "中游985",
            "https://www.xmu.edu.cn", "https://gs.xmu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "https://cs.xmu.edu.cn/info/1080/4610.htm")
                    .confidence("LOW").linkSel("a[href*='info/1'], ul li a").nameSel("h3, td")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("信息学院",
                    "https://information.xmu.edu.cn/info/1026/4406.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "计算机学院强com；信息学院弱com",
            Arrays.asList("厦大计算机保研", "XMU夏令营")
        ));

        add(new UniversityConfig(25, "中南大学", "湖南", "中游985",
            "https://www.csu.edu.cn", "https://graduate.csu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "https://cse.csu.edu.cn/info/1178/5706.htm")
                    .confidence("LOW").linkSel("a[href*='info'], ul li a").nameSel("h3, td")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("软件学院",
                    "https://software.csu.edu.cn/info/1078/2718.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "两个院系均强com",
            Arrays.asList("中南大学计算机保研", "CSU夏令营")
        ));

        add(new UniversityConfig(26, "吉林大学", "吉林", "中游985",
            "https://www.jlu.edu.cn", "https://yjsc.jlu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机科学与技术学院",
                    "https://ccccu.jlu.edu.cn/szdw/qbjs.htm")
                    .confidence("MEDIUM").linkSel("a[href*='info'], ul li a").nameSel("h3")
                    .com(ComStatus.UNKNOWN).build()
            ),
            "图中未收录，请查阅官网",
            Arrays.asList("吉大计算机保研")
        ));

        add(new UniversityConfig(27, "中国农业大学", "北京", "中游985",
            "https://www.cau.edu.cn", "https://graduate.cau.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("信息与电气工程学院",
                    "https://ciee.cau.edu.cn/info/1025/4263.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.UNKNOWN).build()
            ),
            "图中未收录，请查阅官网",
            Arrays.asList("农大计算机保研")
        ));

        add(new UniversityConfig(28, "大连理工大学", "辽宁", "中游985",
            "https://www.dlut.edu.cn", "https://gs.dlut.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("电子信息与电气工程学部",
                    "https://seee.dlut.edu.cn/info/1007/5236.htm")
                    .confidence("LOW").linkSel("a[href*='info'], ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "图中仅收录电子信息学部，弱com",
            Arrays.asList("大工计算机保研", "DUT夏令营")
        ));

        add(new UniversityConfig(29, "华东师范大学", "上海", "中游985",
            "https://www.ecnu.edu.cn", "https://yjsy.ecnu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "https://cs.ecnu.edu.cn/info/1008/6001.htm")
                    .confidence("LOW").linkSel("a[href*='info'], ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("数据科学学院",
                    "https://dase.ecnu.edu.cn/dase_en/faculty_all/list.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "两个院系均强com",
            Arrays.asList("华师大计算机保研", "ECNU夏令营")
        ));

        // ── 中下游 985 ──────────────────────────────────────────────────────

        add(new UniversityConfig(30, "华南理工大学", "广东", "中下游985",
            "https://www.scut.edu.cn", "https://www2.scut.edu.cn/graduate",
            Arrays.asList(
                new DeptConfig.Builder("计算机科学与工程学院",
                    "https://www2.scut.edu.cn/cs/22787/list.htm")
                    .confidence("MEDIUM").linkSel("a[href*='info'], ul li a").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "计算机学院强com（图中仅收录此院系）",
            Arrays.asList("华工计算机保研", "SCUT夏令营")
        ));

        add(new UniversityConfig(31, "电子科技大学", "四川", "中下游985",
            "https://www.uestc.edu.cn", "https://www.uestc.edu.cn/graduate",
            Arrays.asList(
                new DeptConfig.Builder("计算机科学与工程学院",
                    "https://www.scse.uestc.edu.cn/info/1012/5517.htm")
                    .confidence("LOW").linkSel("a[href*='info'], ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("软件学院",
                    "https://www.se.uestc.edu.cn/info/1008/2547.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("信息与通信工程学院",
                    "https://www.uestc.edu.cn/info/1141/9175.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("电子工程学院",
                    "https://www.ee.uestc.edu.cn/info/1093/3007.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("集成电路工程学院",
                    "https://www.uestc.edu.cn/info/1141/9998.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("机械与电气工程学院",
                    "https://www.mece.uestc.edu.cn/info/1023/3047.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("深圳研究院",
                    "https://www.uestcsz.edu.cn/")
                    .confidence("LOW").linkSel("a[href*='teacher']").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "图中所有院系均弱com",
            Arrays.asList("成电计算机保研", "UESTC夏令营")
        ));

        add(new UniversityConfig(32, "湖南大学", "湖南", "中下游985",
            "https://www.hnu.edu.cn", "https://graduate.hnu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("控制科学与工程学院",
                    "https://csee.hnu.edu.cn/info/1013/1000.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("信息科学与工程学院",
                    "https://csee.hnu.edu.cn/info/1013/1001.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.STRONG).build(),
                new DeptConfig.Builder("电气信息工程学院",
                    "https://eie.hnu.edu.cn/info/1025/3124.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "信息学院和电气信息学院强com；控制学院弱com",
            Arrays.asList("湖南大学计算机保研")
        ));

        add(new UniversityConfig(33, "重庆大学", "重庆", "中下游985",
            "https://www.cqu.edu.cn", "https://graduate.cqu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机科学与技术学院",
                    "https://cs.cqu.edu.cn/info/1003/4543.htm")
                    .confidence("LOW").linkSel("a[href*='info'], ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("电气工程学院",
                    "https://ee.cqu.edu.cn/info/1025/5263.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "图中两个院系均弱com",
            Arrays.asList("重大计算机保研")
        ));

        // ── 末流 985 ────────────────────────────────────────────────────────

        add(new UniversityConfig(38, "兰州大学", "甘肃", "末流985",
            "https://www.lzu.edu.cn", "https://yjs.lzu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("信息科学与工程学院",
                    "https://xxxy.lzu.edu.cn/szdw/jzg/index.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.UNKNOWN).build()
            ),
            "图中未收录，请查阅官网",
            Arrays.asList("兰大计算机保研")
        ));

        add(new UniversityConfig(39, "东北大学", "辽宁", "末流985",
            "https://www.neu.edu.cn", "https://gs.neu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机科学与工程学院",
                    "https://cse.neu.edu.cn/csen/3311/list.htm")
                    .confidence("LOW").linkSel("a[href*='info'], ul li a").nameSel("h3")
                    .com(ComStatus.UNKNOWN).build()
            ),
            "图中未收录，请查阅官网",
            Arrays.asList("东北大学计算机保研")
        ));

        add(new UniversityConfig(57, "中国海洋大学", "山东", "末流985",
            "https://www.ouc.edu.cn", "https://yjsy.ouc.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("信息科学与工程学院",
                    "https://it.ouc.edu.cn/szpy/qbsz.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.UNKNOWN).build()
            ),
            "图中未收录，请查阅官网",
            Arrays.asList("海大计算机保研")
        ));

        add(new UniversityConfig(73, "西北农林科技大学", "陕西", "末流985",
            "https://www.nwafu.edu.cn", "https://yjsc.nwafu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("信息工程学院",
                    "https://cie.nwafu.edu.cn/xsgk/szdw/index.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.UNKNOWN).build()
            ),
            "图中未收录，请查阅官网",
            Arrays.asList("西农计算机保研")
        ));

        add(new UniversityConfig(103, "中央民族大学", "北京", "末流985",
            "https://www.muc.edu.cn", "https://yjs.muc.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("信息工程学院",
                    "https://ie.muc.edu.cn/szdw/qzjs.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.UNKNOWN).build()
            ),
            "图中未收录，请查阅官网",
            Arrays.asList("民大计算机保研")
        ));

        // ── 图中收录的院外机构 ────────────────────────────────────────────────

        add(new UniversityConfig(900, "中国科学院", "北京", "中科院",
            "http://www.ict.ac.cn", "https://www.ucas.ac.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算技术研究所",
                    "http://www.ict.ac.cn/rcdw/yjy/")
                    .confidence("LOW").linkSel("a[href*='rcdw']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("自动化所",
                    "http://www.ia.cas.cn/rcdw/yjy/")
                    .confidence("LOW").linkSel("a[href*='rcdw']").nameSel("h3")
                    .com(ComStatus.CAS_MIX).note("硕士强+博士弱").build(),
                new DeptConfig.Builder("信工所",
                    "http://www.iie.cas.cn/rcdw/yjy/")
                    .confidence("LOW").linkSel("a[href*='rcdw']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("软件所",
                    "http://www.iscas.ac.cn/rcdw/yjy/")
                    .confidence("LOW").linkSel("a[href*='rcdw']").nameSel("h3")
                    .com(ComStatus.STRONG).build()
            ),
            "各所独立招生；自动化所硕士强+博士弱；软件所强com；计算所弱com",
            Arrays.asList("中科院计算所保研", "中科院软件所保研", "中科院直博")
        ));

        add(new UniversityConfig(901, "北京邮电大学", "北京", "中游985",
            "https://cs.bupt.edu.cn", "https://yjszs.bupt.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机学院",
                    "https://cs.bupt.edu.cn/info/1058/4201.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("人工智能学院",
                    "https://ai.bupt.edu.cn/szdw/qbjs.htm")
                    .confidence("LOW").linkSel("ul li a").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("信息与通信工程学院",
                    "https://sice.bupt.edu.cn/info/1024/2869.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build(),
                new DeptConfig.Builder("现代邮政学院",
                    "https://smp.bupt.edu.cn/info/1014/3249.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "图中所有院系均弱com",
            Arrays.asList("北邮计算机保研", "BUPT夏令营")
        ));

        add(new UniversityConfig(902, "西安电子科技大学", "陕西", "中游985",
            "https://www.xidian.edu.cn", "https://gr.xidian.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("网络与信息安全学院",
                    "https://scs.xidian.edu.cn/info/1054/5694.htm")
                    .confidence("LOW").linkSel("a[href*='info']").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "图中仅收录网安学院，弱com",
            Arrays.asList("西电计算机保研")
        ));

        add(new UniversityConfig(903, "北京交通大学", "北京", "中游985",
            "https://www.bjtu.edu.cn", "https://gs.bjtu.edu.cn",
            Arrays.asList(
                new DeptConfig.Builder("计算机与信息技术学院",
                    "https://scit.bjtu.edu.cn/cms/item/list.html?pcid=33")
                    .confidence("LOW").linkSel("a[href*='item']").nameSel("h3")
                    .com(ComStatus.WEAK).build()
            ),
            "图中仅收录计信学院，弱com",
            Arrays.asList("北交大计算机保研")
        ));
    }

    private static void add(UniversityConfig u) {
        ALL.add(u);
        BY_NAME.put(u.name, u);
    }

    // ── 查询方法 ─────────────────────────────────────────────────────────────

    public static Optional<UniversityConfig> findByName(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        return ALL.stream()
            .filter(u -> u.name.contains(query) || query.contains(u.name))
            .findFirst();
    }

    public static List<UniversityConfig> filterByTier(String tier) {
        return ALL.stream()
            .filter(u -> u.tier.equals(tier))
            .collect(Collectors.toList());
    }

    public static List<UniversityConfig> filterByProvince(String province) {
        return ALL.stream()
            .filter(u -> u.province.equals(province))
            .collect(Collectors.toList());
    }

    public static List<UniversityConfig> withStrongDepts() {
        return ALL.stream()
            .filter(UniversityConfig::hasStrongDept)
            .collect(Collectors.toList());
    }

    public static List<String> allProvinces() {
        return ALL.stream()
            .map(u -> u.province)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    public static List<String> allTiers() {
        return Arrays.asList("顶尖C9", "上游985", "中游985", "中下游985", "末流985", "中科院");
    }
}