package com.baoyan.analytics;

import com.baoyan.db.DatabaseService;
import com.baoyan.model.Teacher;
import com.baoyan.model.Teacher;
import com.baoyan.model.SchoolInfo;
import com.baoyan.db.DatabaseService;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
@Service
class MatchService {

private static final Logger log = LoggerFactory.getLogger(MatchService.class);

@Autowired
private DatabaseService db;

// IDF 预计算缓存：term → log(N / df(term))
private final Map<String, Double> idfCache = new ConcurrentHashMap<>();
private volatile boolean idfReady = false;
private static final int TOP_N_TERMS = 5000;

// ── 研究方向语义扩展表 ─────────────────────────────────────────────
// 两层扩展：① 同义词/子方向（直接命中） ② 语义近邻（相关领域，找不到精确匹配时兜底）
// 例：量子计算 → 直接命中：量子算法/量子信息；近邻：密码学/算法理论/高性能计算
private static final Map<String, List<String>> DIR_EXPAND = new HashMap<>();

    /** 暴露给 AnalyticsService 使用，返回不可修改视图 */
    public static Map<String, List<String>> getDirExpand() {
        return java.util.Collections.unmodifiableMap(DIR_EXPAND);
    }
static {
    DIR_EXPAND.put("机器学习",    Arrays.asList(
        // 同义/子方向
        "机器学习","深度学习","神经网络","迁移学习","联邦学习","表示学习","集成学习","半监督","自监督","对比学习",
        // 英文 subfield/topic（OpenAlex）
        "Artificial Intelligence","Machine Learning","Deep Learning",
        // 语义近邻
        "模式识别","统计学习","贝叶斯","优化","特征工程","数据驱动"));
    DIR_EXPAND.put("计算机视觉",  Arrays.asList(
        "计算机视觉","图像识别","目标检测","图像分割","图像处理","视觉感知","三维视觉","医学图像","SLAM","点云","视频理解","深度估计","光流","图像生成","视觉跟踪",
        // 英文 subfield/topic（OpenAlex）
        "Computer Vision and Pattern Recognition","Computer Vision","Pattern Recognition","Image Processing","Object Detection",
        // 近邻
        "模式识别","机器人感知","遥感","多媒体","图形学","增强现实"));
    DIR_EXPAND.put("自然语言处理",Arrays.asList(
        "自然语言处理","NLP","文本挖掘","语言模型","机器翻译","信息抽取","情感分析","知识图谱","问答系统","对话系统","文本生成","语义理解","文本分类","命名实体",
        // 英文 subfield/topic（OpenAlex）
        "Natural Language Processing","Information Retrieval","Knowledge Graph",
        // 近邻
        "语言学","信息检索","知识工程","文本数据库","语音识别","推荐系统"));
    DIR_EXPAND.put("大语言模型",  Arrays.asList(
        "大语言模型","LLM","GPT","预训练","Transformer","BERT","语言模型","指令微调","RLHF","RAG","Agent","大模型","基础模型","涌现能力","提示工程","思维链",
        // 英文 subfield/topic（OpenAlex）
        "Large Language Model","Generative AI","Foundation Model",
        // 近邻
        "自然语言处理","知识图谱","对话系统","文本生成","信息检索","推荐系统","多模态"));
    DIR_EXPAND.put("具身智能",    Arrays.asList(
        "具身智能","Embodied AI","机器人学习","机器人操控","模仿学习","感知规划","抓取","灵巧手","人形机器人","sim2real",
        // 近邻
        "机器人","控制","自动化","运动规划","强化学习","机电一体化","智能控制","仿生","无人系统"));
    DIR_EXPAND.put("多模态",      Arrays.asList(
        "多模态","CLIP","视觉语言","图文对齐","多模态理解","多模态生成","多模态大模型","视频理解","VQA","图像描述","视觉问答",
        // 近邻
        "计算机视觉","自然语言处理","语音","跨媒体","音视频","人机交互"));
    DIR_EXPAND.put("自动驾驶",    Arrays.asList(
        "自动驾驶","无人驾驶","自动驾驶感知","激光雷达","点云","轨迹预测","路径规划","高精地图","BEV","端到端自动驾驶",
        // 近邻
        "计算机视觉","目标检测","传感器融合","控制","嵌入式","机器人","交通","智能网联"));
    DIR_EXPAND.put("网络安全",    Arrays.asList(
        "网络安全","信息安全","密码学","安全协议","漏洞挖掘","入侵检测","恶意代码","隐私保护","可信计算","区块链","隐私计算","对抗样本","模型安全","数据安全","软件安全",
        // 近邻
        "形式化验证","访问控制","安全审计","系统安全","云安全","物联网安全","工控安全"));
    DIR_EXPAND.put("强化学习",    Arrays.asList(
        "强化学习","深度强化学习","策略梯度","多智能体","逆强化学习","模仿学习","RLHF","马尔科夫决策",
        // 近邻
        "控制理论","游戏AI","机器人控制","决策","规划","优化","运筹学","博弈论"));
    DIR_EXPAND.put("图神经网络",  Arrays.asList(
        "图神经网络","GNN","图卷积","图学习","异构图","图Transformer","子图",
        // 近邻
        "知识图谱","社交网络","分子图","图数据库","生物信息","药物发现","组合优化","图算法"));
    DIR_EXPAND.put("系统/体系结构",Arrays.asList(
        "体系结构","操作系统","计算机系统","编译","存储","分布式","并行计算","异构计算","处理器","FPGA","芯片","数据库系统","云计算",
        // 英文 subfield/topic（OpenAlex）
        "Hardware and Architecture","Computer Architecture","Distributed Systems","Cloud Computing",
        // 近邻
        "内存管理","调度","文件系统","虚拟化","容器","微架构","缓存","互联网络","硬件加速"));
    DIR_EXPAND.put("算法理论",    Arrays.asList(
        "算法","理论计算机","复杂度","组合优化","图论","近似算法","随机算法","博弈论","密码协议","形式化","在线算法",
        // 英文 subfield/topic（OpenAlex）
        "Computational Theory and Mathematics","Theoretical Computer Science",
        // 近邻
        "数学","运筹学","离散数学","计算几何","最优化","数论","代数","概率论","编码理论"));
    DIR_EXPAND.put("数据库/数据挖掘",Arrays.asList(
        "数据库","数据挖掘","数据管理","知识发现","推荐系统","图数据","时序数据","流数据","NoSQL","OLAP","大数据","数据仓库",
        // 英文 subfield/topic（OpenAlex）
        "Information Systems","Database Systems","Data Mining","Recommendation Systems",
        // 近邻
        "信息检索","商业智能","数据集成","异常检测","数据质量","知识图谱","数据湖","联邦学习"));
    DIR_EXPAND.put("软件工程",    Arrays.asList(
        "软件工程","软件测试","程序分析","形式化方法","软件安全","DevOps","需求工程","代码生成","智能软件","静态分析","模糊测试",
        // 英文 subfield/topic（OpenAlex）
        "Software","Software Engineering",
        // 近邻
        "软件架构","微服务","软件可靠性","程序验证","缺陷检测","软件维护","开源","开发工具"));
    DIR_EXPAND.put("高性能计算",  Arrays.asList(
        "高性能计算","并行计算","超算","GPU","CUDA","集群","云计算","异构","加速器","AI基础设施","模型压缩","推理加速",
        // 近邻
        "分布式计算","网格计算","数值计算","科学计算","模拟","有限元","大规模计算","性能优化"));
    DIR_EXPAND.put("嵌入式/物联网",Arrays.asList(
        "嵌入式","物联网","边缘计算","传感器","实时系统","CPS","无线传感","智能硬件","端侧AI","移动计算","TinyML",
        // 英文 subfield/topic（OpenAlex）
        "Internet of Things","Edge Computing","Embedded Systems",
        // 近邻
        "单片机","RTOS","低功耗","无线通信","工业控制","智能家居","可穿戴","MEMS","信号处理"));
    DIR_EXPAND.put("人机交互",    Arrays.asList(
        // 注意：AR/VR/UI/UX 等短英文缩写由 AnalyticsService.keywordMatches 做单词边界匹配，
        // 避免 contains("ar") 误命中 research/software/hardware 等含"ar"的词
        "人机交互","HCI","用户界面","用户体验","UX","UI","虚拟现实","增强现实","AR","VR","混合现实","智能交互","界面设计","交互设计",
        // 近邻（去掉"可视化"——过宽，几乎所有方向都含此词，是误匹配源）
        "认知科学","眼动","手势识别","语音交互","触觉反馈","可穿戴交互","社交计算"));
    DIR_EXPAND.put("量子计算",    Arrays.asList(
        // 核心量子词
        "量子计算","量子通信","量子算法","量子纠错","量子密钥","量子信息","量子机器学习","量子电路","量子优势","量子模拟",
        // 语义近邻（这些方向与量子有实质交叉，末流211可能有这类老师）
        "密码学","密码协议","后量子密码","信息论","编码理论","算法理论","复杂度","随机算法",
        "高性能计算","并行计算","数值计算","优化","线性代数","数学","物理","光子","低温"));
    DIR_EXPAND.put("网络与通信",  Arrays.asList(
        "网络","通信","无线网络","5G","6G","网络协议","SDN","P2P","CDN","流量","拥塞控制","网络测量",
        // 英文 subfield/topic（OpenAlex）
        "Computer Networks and Communications","Wireless Communications","Signal Processing",
        // 近邻
        "移动通信","卫星通信","信号处理","编码","调制","天线","频谱","物联网","网络安全","边缘计算"));
    // 兼容旧键名
    DIR_EXPAND.put("信息安全",    DIR_EXPAND.get("网络安全"));
    DIR_EXPAND.put("多模态/大模型", DIR_EXPAND.get("多模态"));
}

/**
 * 核心方法：把用户输入（意向导师方向 + 意愿去向 + 科研背景）扩展为完整检索词集合。
 *
 *   dirs       = 意向导师方向标签 → 语义扩展（机器学习 → 深度学习/神经网络/…）
 *   targetDirs = 学生意愿去向文本 → 提取 CS 词 → 扩展（与老师方向匹配）
 *   background = 学生科研经历描述 → 提取技术词 → 推断方向（ResNet→计算机视觉）
 *
 * 三者取并集作为最终查询词，实现"三维匹配"。
 */
public String buildExpandedQuery(List<String> dirs, String background) {
    return buildExpandedQuery(dirs, background, "");
}

public String buildExpandedQuery(List<String> dirs, String background, String targetDirs) {
    Set<String> terms = new LinkedHashSet<>();

    // 1. 展开意向导师方向标签
    for (String dir : dirs) {
        List<String> expanded = DIR_EXPAND.getOrDefault(dir, List.of(dir));
        terms.addAll(expanded);
        terms.add(dir);
    }

    // 2. 展开意愿去向（学生想研究什么）
    if (targetDirs != null && !targetDirs.isBlank()) {
        terms.add(targetDirs.trim());
        expandTextToTerms(targetDirs, terms);
    }

    // 3. 从科研经历中识别技术词 → 推断方向
    if (background != null && !background.isBlank()) {
        terms.add(background.trim());
        expandTextToTerms(background, terms);
    }

    return String.join(" ", terms);
}

/** 从自由文本中识别 CS 方向词和技术词，扩展到检索词集 */
private void expandTextToTerms(String text, Set<String> terms) {
    // 检测是否包含方向关键词 → 扩展同类
    for (Map.Entry<String, List<String>> entry : DIR_EXPAND.entrySet()) {
        String dirKey = entry.getKey();
        boolean mentioned = entry.getValue().stream().anyMatch(text::contains);
        if (mentioned || text.contains(dirKey)) {
            terms.addAll(entry.getValue());
        }
    }
    // 技术词 → 所属方向映射
    Map<String, String> techToDir = new HashMap<>();
    techToDir.put("ResNet",      "计算机视觉"); techToDir.put("YOLO",     "计算机视觉");
    techToDir.put("Transformer", "自然语言处理"); techToDir.put("BERT",    "自然语言处理");
    techToDir.put("GPT",         "自然语言处理"); techToDir.put("LLM",     "多模态/大模型");
    techToDir.put("PyTorch",     "深度学习");    techToDir.put("TensorFlow","深度学习");
    techToDir.put("CVPR",        "计算机视觉"); techToDir.put("ICCV",    "计算机视觉");
    techToDir.put("ACL",         "自然语言处理"); techToDir.put("EMNLP",  "自然语言处理");
    techToDir.put("目标检测",    "计算机视觉"); techToDir.put("语义分割","计算机视觉");
    techToDir.put("点云",        "计算机视觉"); techToDir.put("三维",    "计算机视觉");
    techToDir.put("GAN",         "多模态/大模型"); techToDir.put("扩散模型","多模态/大模型");
    techToDir.put("知识图谱",    "自然语言处理"); techToDir.put("推荐系统","数据库/数据挖掘");
    techToDir.put("漏洞",        "信息安全");   techToDir.put("密码",    "信息安全");
    techToDir.put("强化",        "强化学习");   techToDir.put("多智能体", "强化学习");
    techToDir.put("机器人",      "强化学习");   techToDir.put("自动驾驶","计算机视觉");
    techToDir.put("大模型",      "多模态/大模型"); techToDir.put("具身",  "强化学习");
    for (Map.Entry<String, String> e : techToDir.entrySet()) {
        if (text.contains(e.getKey())) {
            terms.add(e.getValue());
            List<String> dirTerms = DIR_EXPAND.get(e.getValue());
            if (dirTerms != null) terms.addAll(dirTerms);
        }
    }
}

/** 应用启动后异步预计算 IDF，放入内存 */
@Async
public void buildIdf() {
    log.info("[TF-IDF] 开始预计算 IDF 词表...");
    long start = System.currentTimeMillis();
    try (Connection conn = db.getConnection()) {
        long totalDocs = countDocs(conn);
        if (totalDocs == 0) { log.warn("[TF-IDF] 数据库无教师记录，跳过 IDF 计算"); return; }

        // 从 researchAreas 提取所有词，统计文档频率
        Map<String, Long> df = new HashMap<>();
        String sql = "SELECT research_areas AS researchAreas FROM teachers WHERE research_areas IS NOT NULL AND research_areas != ''";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Set<String> docTerms = tokenize(rs.getString(1));
                docTerms.forEach(t -> df.merge(t, 1L, Long::sum));
            }
        }

        // 取文档频率最高的 TOP_N_TERMS 词计算 IDF
        idfCache.clear();
        df.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(TOP_N_TERMS)
            .forEach(entry -> {
                double idf = Math.log((double)(totalDocs + 1) / (entry.getValue() + 1)) + 1.0;
                idfCache.put(entry.getKey(), idf);
            });

        idfReady = true;
        log.info("[TF-IDF] IDF 计算完成：{} 词，耗时 {}ms", idfCache.size(), System.currentTimeMillis() - start);
    } catch (Exception e) {
        log.error("[TF-IDF] IDF 计算失败", e);
    }
}

/**
 * 语义匹配主入口：
 *   1. FTS5 全文召回（毫秒级）
 *   2. TF-IDF 精确评分（Java 21 虚拟线程并行计算）
 *   3. 按梯队权重综合排序
 */
public List<MatchResult> match(String query, String tierFilter, int limit) {
    if (query == null || query.isBlank()) return List.of();

    Set<String> queryTerms = tokenize(query);
    if (queryTerms.isEmpty()) return List.of();

    // "all" 或空 = 不限梯队，直接搜全库（含211等未注册院校）
    boolean doTierFilter = tierFilter != null
        && !tierFilter.isBlank()
        && !tierFilter.equals("all");
    String effectiveTier = doTierFilter ? tierFilter : null;

    // 1. FTS5 召回（带梯队过滤）
    List<TeacherCandidate> candidates = fts5Recall(query, queryTerms,
            Math.min(limit * 3, 150), effectiveTier);

    if (candidates.isEmpty()) {
        candidates = likeRecall(queryTerms, Math.min(limit * 3, 150), effectiveTier);
    }

    if (candidates.isEmpty()) return List.of();

    // 2. TF-IDF 评分
    List<MatchResult> scored = parallelScore(candidates, queryTerms, tierFilter);

    // 3. 排序 + 截断
    return scored.stream()
        .sorted(Comparator.comparingDouble(MatchResult::score).reversed())
        .limit(limit)
        .collect(Collectors.toList());
}

/**
 * FTS5 全文检索召回。
 * tierFilter=null → 不限梯队，搜全库（含211等未在 universities 表注册的院校）
 * tierFilter=非空 → 限定 universities.tier IN (...)，只搜指定梯队
 */
private List<TeacherCandidate> fts5Recall(String query, Set<String> terms,
                                           int limit, String tierFilter) {
    List<TeacherCandidate> results = new ArrayList<>();
    String ftsQuery = String.join(" OR ", terms.stream()
        .filter(t -> t.length() >= 2).collect(Collectors.toList()));
    if (ftsQuery.isBlank()) return results;

    boolean hasTier = tierFilter != null && !tierFilter.isBlank();
    String[] tierArr = hasTier ? tierFilter.split(",") : new String[0];
    String tierIn = hasTier
        ? Arrays.stream(tierArr).map(x -> "?").collect(Collectors.joining(",")) : "";

    String sql = hasTier
        ? ("SELECT t.id, t.name, t.university, t.department, t.title," +
           " t.research_areas AS researchAreas, t.profile_url AS profileUrl," +
           " t.recruiting, t.email, rank AS fts_rank" +
           " FROM teachers t JOIN teachers_fts f ON t.id = f.rowid" +
           " WHERE teachers_fts MATCH ?" +
           " AND t.university IN (SELECT name FROM universities WHERE tier IN (" + tierIn + "))" +
           " ORDER BY fts_rank LIMIT ?")
        : ("SELECT t.id, t.name, t.university, t.department, t.title," +
           " t.research_areas AS researchAreas, t.profile_url AS profileUrl," +
           " t.recruiting, t.email, rank AS fts_rank" +
           " FROM teachers t JOIN teachers_fts f ON t.id = f.rowid" +
           " WHERE teachers_fts MATCH ?" +
           " ORDER BY fts_rank LIMIT ?");

    try (Connection conn = db.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        int idx = 1;
        ps.setString(idx++, ftsQuery);
        for (String tier : tierArr) ps.setString(idx++, tier.trim());
        ps.setInt(idx, limit);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            results.add(new TeacherCandidate(
                rs.getLong("id"), rs.getString("name"), rs.getString("university"),
                rs.getString("department"), rs.getString("title"),
                rs.getString("researchAreas"), rs.getString("profileUrl"),
                rs.getBoolean("recruiting"), rs.getString("email"),
                rs.getDouble("fts_rank")));
        }
    } catch (Exception e) {
        log.debug("[FTS5] 召回失败，降级为 LIKE: {}", e.getMessage());
    }
    return results;
}

/**
 * LIKE 模糊查询降级召回。
 * tierFilter=null → 不限梯队
 */
private List<TeacherCandidate> likeRecall(Set<String> terms, int limit, String tierFilter) {
    List<TeacherCandidate> results = new ArrayList<>();
    List<String> termList = terms.stream().filter(t -> t.length() >= 2)
                                          .collect(Collectors.toList());
    if (termList.isEmpty()) return results;

    boolean hasTier = tierFilter != null && !tierFilter.isBlank();
    String[] tierArr = hasTier ? tierFilter.split(",") : new String[0];
    String likeClause = termList.stream()
        .map(t -> "research_areas LIKE ?").collect(Collectors.joining(" OR "));
    String tierClause = hasTier
        ? " AND university IN (SELECT name FROM universities WHERE tier IN ("
          + Arrays.stream(tierArr).map(x -> "?").collect(Collectors.joining(",")) + "))"
        : "";

    String sql = "SELECT id, name, university, department, title,"
               + " research_areas AS researchAreas, profile_url AS profileUrl, recruiting, email"
               + " FROM teachers WHERE (" + likeClause + ")" + tierClause + " LIMIT ?";
    try (Connection conn = db.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        int idx = 1;
        for (String t : termList) ps.setString(idx++, "%" + t + "%");
        for (String tier : tierArr) ps.setString(idx++, tier.trim());
        ps.setInt(idx, limit);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            results.add(new TeacherCandidate(
                rs.getLong("id"), rs.getString("name"), rs.getString("university"),
                rs.getString("department"), rs.getString("title"),
                rs.getString("researchAreas"), rs.getString("profileUrl"),
                rs.getBoolean("recruiting"), rs.getString("email"), 0));
        }
    } catch (Exception e) {
        log.error("[LIKE] 降级召回失败", e);
    }
    return results;
}

/** Java 21 虚拟线程并行计算 TF-IDF 得分 */
private List<MatchResult> parallelScore(List<TeacherCandidate> candidates,
                                         Set<String> queryTerms, String tierFilter) {
    List<MatchResult> results = Collections.synchronizedList(new ArrayList<>());

    // Java 17 兼容线程池（原 Java 21 虚拟线程写法已替换）
    ExecutorService executor = Executors.newFixedThreadPool(Math.min(Math.max(candidates.size(), 1), 8));
    try {
        List<Future<?>> futures = candidates.stream()
            .map(c -> (Future<?>) executor.submit((Runnable) () -> {
                double score = computeTfIdf(c.researchAreas(), queryTerms);
                // 梯队权重加成
                double tierBoost = getTierBoost(c.university());
                double finalScore = score * tierBoost;
                // 招生加成
                if (c.recruiting()) finalScore *= 1.1;

                results.add(new MatchResult(
                    c.id(), c.name(), c.university(), c.department(), c.title(),
                    c.researchAreas(), c.profileUrl(), c.recruiting(), c.email(),
                    Math.min(1.0, finalScore),
                    new ArrayList<>(queryTerms)
                ));
            }))
            .collect(Collectors.toList());

        for (Future<?> f : futures) {
            try { f.get(2, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
    } finally {
        executor.shutdown();
    }

    return results;
}

private double computeTfIdf(String text, Set<String> queryTerms) {
    if (text == null || text.isBlank()) return 0;
    Set<String> docTerms = tokenize(text);
    int docLen = Math.max(docTerms.size(), 1);
    double score = 0;
    for (String qt : queryTerms) {
        if (qt.length() < 2) continue;
        // TF：词在文档中出现的次数 / 文档总词数
        long termFreq = docTerms.stream().filter(t -> t.contains(qt) || qt.contains(t)).count();
        if (termFreq == 0) continue;
        double tf = (double) termFreq / docLen;
        // IDF：从预计算缓存取，若无则用默认值
        double idf = idfCache.getOrDefault(qt, 1.0);
        score += tf * idf;
    }
    return score;
}

private double getTierBoost(String university) {
    if (university == null) return 1.0;
    String[] c9 = {"清华大学", "北京大学", "复旦大学", "上海交通大学", "浙江大学",
                    "中国科学技术大学", "南京大学", "哈尔滨工业大学", "西安交通大学"};
    for (String u : c9) if (university.contains(u)) return 1.3;
    return 1.0;
}

/** 中文分词（简化版：字符级 + 常见词组）*/
private Set<String> tokenize(String text) {
    if (text == null) return Set.of();
    Set<String> tokens = new LinkedHashSet<>();
    // 整体作为一个 token
    String clean = text.trim().toLowerCase();
    if (!clean.isBlank()) tokens.add(clean);
    // 按空格、逗号、顿号、换行等分割
    String[] parts = text.split("[\\s,，、；;。.\\n\\r]+");
    for (String p : parts) {
        String t = p.trim().toLowerCase();
        if (t.length() >= 2 && t.length() <= 30) tokens.add(t);
    }
    // 2-gram 滑动窗口（对中文效果好）
    for (String part : parts) {
        String p = part.trim();
        for (int i = 0; i < p.length() - 1; i++) {
            String bigram = p.substring(i, Math.min(i + 4, p.length())).toLowerCase();
            if (bigram.length() >= 2) tokens.add(bigram);
        }
    }
    return tokens;
}

private long countDocs(Connection conn) throws SQLException {
    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM teachers")) {
        return rs.next() ? rs.getLong(1) : 0;
    }
}

// ── 数据类 ─────────────────────────────────────────────────────
record TeacherCandidate(long id, String name, String university, String department,
                         String title, String researchAreas, String profileUrl,
                         boolean recruiting, String email, double ftsRank) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * ══════════════════════════════════════════════════════════════════════
 *  Session-Based Re-ranking  （短期兴趣 + 长期兴趣双融合）
 *
 *  理论依据：参考 2022-2024 SIGIR/KDD 的 session-based recommendation 研究范式：
 *
 *    long_score  = TF-IDF 基础分（profile.dirs + background → 长期偏好）
 *    session_vec = 当前 session 中用户点开的老师 research_areas → TF 向量
 *                  用时间衰减加权（越近的点击权重越高）
 *    hybrid_score = α × long_score + β × cosine(session_vec, teacher_vec)
 *
 *  三个参数：
 *    α = 0.70  长期兴趣权重（profile 显式填写，稳定）
 *    β = 0.30  短期兴趣权重（session 行为隐式推断，动态）
 *    λ = 0.80  时间衰减系数（每多一步前的行为乘以 λ）
 *
 *  效果：用户点了几个 CV 方向的老师 → CV 相关老师得分上升，
 *        再搜时即使没明确选"计算机视觉"也能自动获得加成。
 * ══════════════════════════════════════════════════════════════════════
 */

/** 会话事件：前端 sessionStorage 里的单条点击记录 */
public record SessionEvent(
    String teacherName,   // 老师姓名
    String university,    // 院校
    String researchAreas, // 该老师的 research_areas（逗号/分号分隔）
    long   timestamp      // 毫秒时间戳
) {}

/**
 * 双兴趣融合重排：
 *   results       — TF-IDF 初始匹配结果
 *   sessionEvents — 当前会话中用户点开过的老师列表（时间降序）
 *   returns       — 经 session 兴趣加成后的重排结果
 */
public List<MatchResult> sessionRerank(
        List<MatchResult> results,
        List<SessionEvent> sessionEvents) {

    if (sessionEvents == null || sessionEvents.isEmpty()) return results;

    // ── Step 1: 构建 session 方向词频向量（带时间衰减）────────────────
    // 越近的点击权重越高：weight_i = λ^i（i=0 是最新的）
    final double LAMBDA = 0.80;
    Map<String, Double> sessionVec = new HashMap<>();

    List<SessionEvent> sorted = sessionEvents.stream()
        .sorted(Comparator.comparingLong(SessionEvent::timestamp).reversed())
        .collect(Collectors.toList());

    for (int i = 0; i < sorted.size(); i++) {
        double weight = Math.pow(LAMBDA, i);
        String areas = sorted.get(i).researchAreas();
        if (areas == null || areas.isBlank()) continue;

        for (String term : areas.split("[,，；;\\s/]+")) {
            term = term.trim().toLowerCase();
            if (term.length() < 2) continue;
            sessionVec.merge(term, weight, Double::sum);
        }
    }

    // ── Step 2: 归一化 session 向量 ─────────────────────────────────
    double sessionNorm = Math.sqrt(sessionVec.values().stream()
        .mapToDouble(v -> v * v).sum());
    if (sessionNorm < 1e-9) return results;  // session 向量为零，不重排

    // ── Step 3: 对每位老师计算 cosine(session_vec, teacher_vec) ─────
    final double ALPHA = 0.70;  // 长期兴趣权重
    final double BETA  = 0.30;  // 短期兴趣权重

    List<MatchResult> reranked = results.stream().map(r -> {
        // 构建老师词频向量
        String areas = r.researchAreas();
        if (areas == null || areas.isBlank()) return r;

        Map<String, Double> teacherVec = new HashMap<>();
        for (String term : areas.split("[,，；;\\s/]+")) {
            term = term.trim().toLowerCase();
            if (term.length() >= 2) teacherVec.merge(term, 1.0, Double::sum);
        }

        // cosine similarity
        double dot = 0, teacherNorm = 0;
        for (Map.Entry<String, Double> e : teacherVec.entrySet()) {
            dot        += sessionVec.getOrDefault(e.getKey(), 0.0) * e.getValue();
            teacherNorm += e.getValue() * e.getValue();
        }
        teacherNorm = Math.sqrt(teacherNorm);
        double cosineSim = (teacherNorm < 1e-9) ? 0.0
            : dot / (sessionNorm * teacherNorm);

        // 双兴趣融合分数
        double hybridScore = ALPHA * r.score() + BETA * cosineSim;

        return new MatchResult(r.id(), r.name(), r.university(), r.department(),
            r.title(), r.researchAreas(), r.profileUrl(),
            r.recruiting(), r.email(), hybridScore, r.matchedTerms());
    }).sorted(Comparator.comparingDouble(MatchResult::score).reversed())
      .collect(Collectors.toList());

    log.debug("[Session] 重排 {} 位老师，session 词 {} 个，有效 session 点击 {} 次",
        results.size(), sessionVec.size(), sorted.size());

    return reranked;
}

public record MatchResult(long id, String name, String university, String department,
                          String title, String researchAreas, String profileUrl,
                          boolean recruiting, String email,
                          double score, List<String> matchedTerms) {}
}