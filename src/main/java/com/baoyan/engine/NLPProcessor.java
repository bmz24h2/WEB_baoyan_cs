package com.baoyan.engine;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.*;
import java.util.regex.*;

/**
 * NLPProcessor — 中文 CS 领域自然语言处理引擎
 *
 * 核心功能：
 *  1. CS 研究方向分类树（三层层次结构）
 *  2. 关键词同义词/近义词归一化
 *  3. CCF 期刊 / 会议重要性评分（A/B/C 三档）
 *  4. TF-IDF 关键词提取
 *  5. 中文学术文本相似度计算（字符 N-gram + 词频加权）
 *  6. 教师简介研究方向自动识别
 *  7. 导师活跃度评分（近 3 年论文数估算）
 */
@Component
public class NLPProcessor {

    // ── 1. CS 研究方向分类树 ────────────────────────────────────
    //   顶层方向 → 中层方向 → 典型关键词列表
    public static final Map<String, Map<String, List<String>>> DIRECTION_TREE = new LinkedHashMap<>();
    static {
        /* ─ 人工智能与机器学习 ─ */
        Map<String, List<String>> ai = new LinkedHashMap<>();
        ai.put("机器学习", Arrays.asList(
            "机器学习","machine learning","ML","监督学习","无监督学习","半监督学习",
            "强化学习","迁移学习","元学习","联邦学习","对比学习","自监督学习",
            "few-shot","zero-shot","prompt tuning","fine-tuning","RLHF"
        ));
        ai.put("深度学习", Arrays.asList(
            "深度学习","deep learning","DL","神经网络","卷积网络","CNN","RNN","LSTM",
            "Transformer","attention","自注意力","BERT","GPT","大语言模型","LLM",
            "预训练模型","扩散模型","diffusion","GAN","生成对抗网络","VAE"
        ));
        ai.put("计算机视觉", Arrays.asList(
            "计算机视觉","computer vision","CV","图像识别","目标检测","语义分割",
            "实例分割","人脸识别","姿态估计","3D视觉","点云","NeRF","多模态",
            "视频理解","YOLO","图像生成","超分辨率","医学图像"
        ));
        ai.put("自然语言处理", Arrays.asList(
            "自然语言处理","NLP","文本分类","情感分析","命名实体识别","NER",
            "关系抽取","机器翻译","问答系统","对话系统","知识图谱","信息抽取",
            "文本生成","摘要","阅读理解","语言模型","词向量","word2vec"
        ));
        ai.put("强化学习", Arrays.asList(
            "强化学习","reinforcement learning","RL","深度强化学习","DRL",
            "Q-learning","策略梯度","Actor-Critic","MCTS","博弈","多智能体"
        ));
        DIRECTION_TREE.put("人工智能与机器学习", ai);

        /* ─ 系统与体系结构 ─ */
        Map<String, List<String>> sys = new LinkedHashMap<>();
        sys.put("体系结构", Arrays.asList(
            "体系结构","computer architecture","处理器设计","RISC-V","ARM","x86",
            "微架构","流水线","超标量","乱序执行","分支预测","缓存","存储层次",
            "AI加速器","GPU","TPU","FPGA","芯片设计","EDA","编译器后端"
        ));
        sys.put("操作系统", Arrays.asList(
            "操作系统","OS","内核","Linux","调度","内存管理","虚拟化","容器",
            "文件系统","驱动","实时操作系统","RTOS","安全操作系统","Unikernel"
        ));
        sys.put("分布式系统", Arrays.asList(
            "分布式系统","distributed systems","一致性","共识协议","Raft","Paxos",
            "分布式存储","分布式计算","云计算","Serverless","微服务","容错","副本"
        ));
        sys.put("高性能计算", Arrays.asList(
            "高性能计算","HPC","并行计算","MPI","CUDA","GPU编程","超算","集群",
            "性能优化","向量化","SIMD","内存带宽","计算图优化","算子融合"
        ));
        sys.put("存储系统", Arrays.asList(
            "存储系统","storage","文件系统","键值存储","对象存储","NVME","SSD",
            "持久内存","PMEM","数据压缩","去重","纠删码","RAID"
        ));
        DIRECTION_TREE.put("系统与体系结构", sys);

        /* ─ 安全与隐私 ─ */
        Map<String, List<String>> sec = new LinkedHashMap<>();
        sec.put("信息安全", Arrays.asList(
            "信息安全","网络安全","漏洞挖掘","模糊测试","fuzzing","程序分析",
            "静态分析","动态分析","二进制分析","逆向工程","CTF","渗透测试",
            "入侵检测","恶意软件","APT","威胁情报"
        ));
        sec.put("密码学", Arrays.asList(
            "密码学","cryptography","公钥密码","对称密码","哈希函数","数字签名",
            "零知识证明","ZKP","同态加密","安全多方计算","MPC","量子密码",
            "后量子密码","区块链","智能合约"
        ));
        sec.put("隐私保护", Arrays.asList(
            "隐私保护","privacy","差分隐私","differential privacy","联邦学习隐私",
            "数据脱敏","匿名化","访问控制","身份认证","可信执行环境","TEE","SGX"
        ));
        DIRECTION_TREE.put("安全与隐私", sec);

        /* ─ 数据与数据库 ─ */
        Map<String, List<String>> data = new LinkedHashMap<>();
        data.put("数据库系统", Arrays.asList(
            "数据库","database","关系数据库","SQL","事务","OLAP","OLTP","列存储",
            "图数据库","时序数据库","内存数据库","查询优化","索引","并发控制"
        ));
        data.put("数据挖掘", Arrays.asList(
            "数据挖掘","data mining","频繁模式","关联规则","聚类","分类",
            "异常检测","图挖掘","社交网络","推荐系统","知识发现"
        ));
        data.put("大数据", Arrays.asList(
            "大数据","big data","Hadoop","Spark","Flink","流计算","批处理",
            "数据湖","数据仓库","ETL","实时计算","数据集成"
        ));
        DIRECTION_TREE.put("数据与数据库", data);

        /* ─ 算法理论 ─ */
        Map<String, List<String>> algo = new LinkedHashMap<>();
        algo.put("算法设计", Arrays.asList(
            "算法","algorithm","复杂度","NP","近似算法","随机算法","在线算法",
            "参数化算法","计算几何","组合优化","图算法","网络流","匹配"
        ));
        algo.put("理论计算机科学", Arrays.asList(
            "理论计算机","TCS","计算复杂性","自动机","形式语言","可计算性",
            "信息论","编码理论","量子计算","量子算法","量子纠错"
        ));
        algo.put("运筹优化", Arrays.asList(
            "运筹学","优化","凸优化","整数规划","混合整数规划","元启发式",
            "遗传算法","粒子群","模拟退火","博弈论","机制设计"
        ));
        DIRECTION_TREE.put("算法理论", algo);

        /* ─ 软件工程 ─ */
        Map<String, List<String>> se = new LinkedHashMap<>();
        se.put("软件工程", Arrays.asList(
            "软件工程","software engineering","代码分析","程序合成","自动修复",
            "测试","软件测试","形式化验证","模型检测","程序证明","静态分析"
        ));
        se.put("编程语言", Arrays.asList(
            "编程语言","PL","类型系统","编译器","解释器","JIT","运行时",
            "垃圾回收","内存安全","Rust","函数式编程","并发安全"
        ));
        DIRECTION_TREE.put("软件工程", se);

        /* ─ 人机交互与多媒体 ─ */
        Map<String, List<String>> hci = new LinkedHashMap<>();
        hci.put("人机交互", Arrays.asList(
            "人机交互","HCI","用户界面","可用性","用户体验","UX","触控",
            "语音交互","AR","VR","混合现实","XR","无障碍","眼动追踪"
        ));
        hci.put("多媒体", Arrays.asList(
            "多媒体","视频编码","音频处理","图像压缩","流媒体","内容分发",
            "音视频同步","媒体计算","数字水印"
        ));
        DIRECTION_TREE.put("人机交互与多媒体", hci);
    }

    // ── 2. 同义词/近义词归一化表 ───────────────────────────────
    // value 都归一化到 key（规范化形式）
    private static final Map<String, String> SYNONYM_MAP = new HashMap<>();
    static {
        putSyn("机器学习", "ML","machine learning","统计学习","ML算法");
        putSyn("深度学习", "DL","deep learning","神经网络学习","深度神经网络");
        putSyn("计算机视觉", "CV","computer vision","图像处理","视觉识别","视觉计算");
        putSyn("自然语言处理", "NLP","natural language processing","语言处理","文本处理","文字处理");
        putSyn("强化学习", "RL","reinforcement learning","深度强化学习","DRL");
        putSyn("知识图谱", "KG","knowledge graph","知识库","知识表示","KG推理");
        putSyn("信息安全", "网络安全","cyber security","安全","系统安全");
        putSyn("密码学", "cryptography","crypto","密码","加密","公钥密码学");
        putSyn("分布式系统", "distributed","分布式","分布式计算","分布式存储");
        putSyn("体系结构", "计算机体系结构","computer architecture","处理器","微体系结构");
        putSyn("高性能计算", "HPC","并行计算","GPU计算","超算");
        putSyn("数据库", "database","DB","数据管理","关系数据库","DBMS");
        putSyn("数据挖掘", "data mining","DM","知识发现","模式挖掘");
        putSyn("算法", "algorithm","algorithms","算法理论","计算理论");
        putSyn("软件工程", "SE","software engineering","软件开发","代码质量");
        putSyn("量子计算", "quantum computing","量子","量子算法","量子信息");
        putSyn("推荐系统", "recommendation","协同过滤","个性化推荐","信息过滤");
        putSyn("图神经网络", "GNN","graph neural network","图学习","图卷积","GCN");
        putSyn("大语言模型", "LLM","large language model","预训练模型","GPT","BERT");
        putSyn("联邦学习", "federated learning","FL","隐私机器学习");
    }
    private static void putSyn(String canonical, String... synonyms) {
        for (String s : synonyms) SYNONYM_MAP.put(s.toLowerCase(), canonical);
    }

    // ── 3. CCF 期刊/会议评分 ────────────────────────────────────
    public enum CCFLevel { A, B, C, NONE }
    private static final Map<String, CCFLevel> CCF_MAP = new HashMap<>();
    static {
        // A类会议
        String[] ccfA_conf = {
            "CVPR","ICCV","ECCV","NeurIPS","NIPS","ICML","ICLR","AAAI","IJCAI",
            "ACL","EMNLP","NAACL","SIGKDD","KDD","SIGMOD","VLDB","ICDE",
            "CCS","IEEE S&P","USENIX Security","NDSS","SOSP","OSDI","EUROSYS",
            "ISCA","MICRO","ASPLOS","PLDI","OOPSLA","POPL","STOC","FOCS","SODA",
            "SIGGRAPH","CHI","MOBICOM","SIGCOMM","INFOCOM","WWW","SIGIR"
        };
        // A类期刊
        String[] ccfA_jour = {
            "TPAMI","IJCV","TIP","TOCS","TODS","VLDB Journal","TDSC","TIFS",
            "TC","TPDS","JACM","SICOMP","IEEE TNNLS","AIJ","JAIR"
        };
        for (String s : ccfA_conf) CCF_MAP.put(s.toLowerCase(), CCFLevel.A);
        for (String s : ccfA_jour) CCF_MAP.put(s.toLowerCase(), CCFLevel.A);

        // B类
        String[] ccfB = {
            "ICDM","WSDM","CIKM","COLING","EACL","ECCV","ICDCS","MIDDLEWARE",
            "RAID","NDSS","USENIX ATC","HPCA","DAC","DATE","ASE","ICSE",
            "FSE","ISSTA","TKDE","TIST","TON","TMC","TKDD","TOSEM"
        };
        for (String s : ccfB) CCF_MAP.put(s.toLowerCase(), CCFLevel.B);

        // C类
        String[] ccfC = {
            "ACCV","BMVC","ICONIP","PAKDD","RecSys","EDBT","DASFAA",
            "ACSAC","DSN","PRDC","TACO","TCAD","JSS","IST"
        };
        for (String s : ccfC) CCF_MAP.put(s.toLowerCase(), CCFLevel.C);
    }

    /** 识别文本中 CCF 级别的期刊/会议，返回 {A:n, B:n, C:n} */
    public Map<String, Integer> countCCFPapers(String bio) {
        Map<String, Integer> cnt = new HashMap<>();
        cnt.put("A", 0); cnt.put("B", 0); cnt.put("C", 0);
        if (bio == null || bio.isBlank()) return cnt;
        String lower = bio.toLowerCase();
        for (Map.Entry<String, CCFLevel> e : CCF_MAP.entrySet()) {
            if (lower.contains(e.getKey())) {
                // 粗估：每次匹配 +1
                String k = e.getValue().name();
                if (!k.equals("NONE")) cnt.merge(k, 1, Integer::sum);
            }
        }
        return cnt;
    }

    /** 给定发表列表文本，返回归一化 CCF-A 论文估算数 */
    public int estimateCCFACount(String pubText) {
        if (pubText == null) return 0;
        return (int) CCF_MAP.entrySet().stream()
            .filter(e -> e.getValue() == CCFLevel.A)
            .filter(e -> pubText.toLowerCase().contains(e.getKey()))
            .count();
    }

    // ── 4. TF-IDF 关键词提取 ────────────────────────────────────
    /**
     * 从文本中提取前 topN 个 TF-IDF 关键词
     * 简化版：单文档内使用词频，IDF 基于预定义 CS 词表稀有度
     */
    public List<String> extractKeywords(String text, int topN) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        // 切词：中文按字/双字组合，英文按空格
        List<String> tokens = tokenize(text);
        // 词频
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) tf.merge(t.toLowerCase(), 1, Integer::sum);
        // IDF（越稀有分越高；常见词降权）
        Map<String, Double> idfBonus = new HashMap<>();
        for (String k : SYNONYM_MAP.keySet()) idfBonus.put(k, 1.5);
        // 所有方向关键词也加分
        DIRECTION_TREE.values().forEach(m -> m.values().forEach(
            kws -> kws.forEach(kw -> idfBonus.put(kw.toLowerCase(), 1.2))
        ));
        // 停用词
        Set<String> stop = new HashSet<>(Arrays.asList(
            "的","了","和","是","在","有","我","他","这","那","也","都","与","及",
            "a","an","the","in","on","of","for","to","with","and","or","is","are"
        ));

        return tf.entrySet().stream()
            .filter(e -> e.getKey().length() >= 2 && !stop.contains(e.getKey()))
            .map(e -> Map.entry(e.getKey(), e.getValue() * idfBonus.getOrDefault(e.getKey(), 1.0)))
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topN)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /** 简单分词：英文按空格/标点，中文提取2-4字 n-gram */
    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        // 英文单词
        Matcher m = Pattern.compile("[a-zA-Z][a-zA-Z0-9\\-]*").matcher(text);
        while (m.find()) tokens.add(m.group().toLowerCase());
        // 中文 2-gram
        String zh = text.replaceAll("[^\\u4e00-\\u9fff]", "");
        for (int i = 0; i < zh.length() - 1; i++)
            tokens.add(zh.substring(i, i + 2));
        // 中文 3-gram
        for (int i = 0; i < zh.length() - 2; i++)
            tokens.add(zh.substring(i, i + 3));
        return tokens;
    }

    // ── 5. 研究方向识别 ─────────────────────────────────────────
    /**
     * 给定一段教师简介/研究兴趣文本，识别所属的顶层方向（可能多个）
     * 返回：{顶层方向名 → 匹配分}，分数越高越相关
     */
    public Map<String, Double> identifyDirections(String bio) {
        if (bio == null || bio.isBlank()) return Collections.emptyMap();
        String lower = bio.toLowerCase();
        Map<String, Double> scores = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, List<String>>> top : DIRECTION_TREE.entrySet()) {
            double score = 0;
            for (Map.Entry<String, List<String>> mid : top.getValue().entrySet()) {
                for (String kw : mid.getValue()) {
                    if (lower.contains(kw.toLowerCase())) {
                        // 精确匹配加权
                        score += kw.length() >= 4 ? 2.0 : 1.0;
                    }
                }
            }
            if (score > 0) scores.put(top.getKey(), score);
        }
        // 归一化
        double total = scores.values().stream().mapToDouble(d -> d).sum();
        if (total > 0) scores.replaceAll((k, v) -> v / total);
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (a, b) -> a, LinkedHashMap::new));
    }

    // ── 6. 关键词归一化 ─────────────────────────────────────────
    public String normalize(String keyword) {
        if (keyword == null) return "";
        return SYNONYM_MAP.getOrDefault(keyword.toLowerCase().trim(), keyword.trim());
    }

    /** 批量归一化并去重 */
    public List<String> normalizeAll(List<String> keywords) {
        return keywords == null ? Collections.emptyList() :
            keywords.stream().map(this::normalize).distinct().collect(Collectors.toList());
    }

    // ── 7. 文本相似度（字符 N-gram + Jaccard）─────────────────
    /**
     * 基于字符 bigram 的 Jaccard 相似度
     * 范围 [0, 1]，1 = 完全相同
     */
    public double similarity(String a, String b) {
        if (a == null || b == null) return 0;
        Set<String> ngA = charBigrams(a.toLowerCase());
        Set<String> ngB = charBigrams(b.toLowerCase());
        if (ngA.isEmpty() && ngB.isEmpty()) return 1.0;
        if (ngA.isEmpty() || ngB.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(ngA); inter.retainAll(ngB);
        Set<String> union = new HashSet<>(ngA); union.addAll(ngB);
        return (double) inter.size() / union.size();
    }

    private Set<String> charBigrams(String s) {
        Set<String> ng = new HashSet<>();
        for (int i = 0; i < s.length() - 1; i++) ng.add(s.substring(i, i + 2));
        return ng;
    }

    // ── 8. 导师活跃度评分 ────────────────────────────────────────
    /**
     * 根据教师简介/发表列表估算活跃度分（0–100）
     * 考虑：近年论文关键词出现次数、CCF-A 论文数、最近年份
     */
    public int estimateAdvisorActivity(String bio, String pubList) {
        int score = 50; // 基础分
        if (pubList != null) {
            // 近3年（2022-2025）出现 → 活跃
            for (int yr = 2022; yr <= 2025; yr++)
                if (pubList.contains(String.valueOf(yr))) score += 8;
            // CCF-A 论文加分
            score += Math.min(30, estimateCCFACount(pubList) * 3);
        }
        if (bio != null) {
            // 基金/项目词
            String[] active = {"国家自然科学基金","NSFC","重点项目","重大研究","企业合作"};
            for (String a : active) if (bio.contains(a)) score += 5;
        }
        return Math.min(100, score);
    }

    // ── 9. 方向匹配度（用户 vs 导师）────────────────────────────
    /**
     * 计算用户方向列表与导师简介的匹配度（0–1）
     */
    public double directionMatch(List<String> userDirs, String advisorBio) {
        if (userDirs == null || userDirs.isEmpty() || advisorBio == null) return 0;
        Map<String, Double> advisorDirs = identifyDirections(advisorBio);
        if (advisorDirs.isEmpty()) return 0;

        double match = 0;
        for (String ud : userDirs) {
            String norm = normalize(ud);
            // 直接文本搜索
            if (advisorBio.toLowerCase().contains(norm.toLowerCase())) match += 0.3;
            // 方向树匹配
            for (Map.Entry<String, Map<String, List<String>>> top : DIRECTION_TREE.entrySet()) {
                for (List<String> kws : top.getValue().values()) {
                    if (kws.stream().anyMatch(k -> k.equalsIgnoreCase(norm))) {
                        match += advisorDirs.getOrDefault(top.getKey(), 0.0) * 0.7;
                        break;
                    }
                }
            }
        }
        return Math.min(1.0, match / userDirs.size());
    }
}
