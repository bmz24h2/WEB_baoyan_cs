package com.baoyan.engine;

import com.baoyan.scraper.UniversityData.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.*;

/**
 * RecommendationEngine — 多算法导师推荐引擎
 *
 * 实现算法：
 *  1. TF-IDF 向量化 + 余弦相似度（用户描述 vs 导师简介）
 *  2. PageRank 声誉打分（基于 CCF 论文引用图模拟）
 *  3. 协同过滤（基于相似背景用户的历史选择）
 *  4. 多因素加权评分
 *     - 研究方向匹配度（NLP 识别）
 *     - GPA 竞争力门槛
 *     - 院系强弱 com 权重
 *     - 导师活跃度
 *     - 用户偏好梯队匹配
 */
@Component
public class RecommendationEngine {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private NLPProcessor nlp;

    // ─────────────────── 请求/响应 DTO ────────────────────────
    public static class RecommendRequest {
        public List<String> dirs;          // 用户研究方向
        public List<String> tiers;         // 目标梯队
        public double gpa;                 // 4.0 制 GPA
        public double rankPct;             // 专业排名百分比（越低越好）
        public String expKeywords;         // 科研经历关键词
        public int limit = 10;
    }

    public static class RecommendResult {
        public String advisorId;
        public String name;
        public String title;
        public String university;
        public String dept;
        public List<String> dirs = new ArrayList<>();
        public double score;               // 综合评分 [0,1]
        public double tfIdfScore;
        public double pageRankScore;
        public double dirMatchScore;
        public double activityScore;
        public int ccfA;
        public String reason;
        public String profileUrl;
    }

    // ─────────────────── 主入口 ──────────────────────────────
    public List<RecommendResult> recommend(RecommendRequest req) {
        // 1. 从 DB 拉取候选导师
        List<Map<String, Object>> candidates = fetchCandidates(req);
        if (candidates.isEmpty()) return Collections.emptyList();

        // 2. 构建用户查询向量
        String userText = buildUserText(req);
        Map<String, Double> userVec = tfidfVectorize(userText);

        // 3. 计算 PageRank 声誉（全库）
        Map<String, Double> pageRanks = computePageRanks(candidates);

        // 4. 对每个候选打分
        List<RecommendResult> results = candidates.stream()
            .map(row -> scoreCandidate(row, userVec, pageRanks, req))
            .filter(r -> r.score > 0.05)
            .sorted(Comparator.comparingDouble((RecommendResult r) -> r.score).reversed())
            .limit(req.limit)
            .collect(Collectors.toList());

        // 5. 协同过滤后处理（重排序 top-5）
        collaborativeRerank(results, req);

        return results;
    }

    // ─────────────────── 1. 数据拉取 ─────────────────────────
    private List<Map<String, Object>> fetchCandidates(RecommendRequest req) {
        try {
            StringBuilder sql = new StringBuilder(
                "SELECT t.id, t.name, t.title, t.university, t.department, " +
                "t.research_interests, t.profile_url, t.bio, t.publications " +
                "FROM teachers t ");

            List<Object> params = new ArrayList<>();
            if (req.tiers != null && !req.tiers.isEmpty()) {
                // 通过 universities 表过滤梯队
                sql.append("JOIN universities u ON t.university = u.name " +
                           "WHERE u.tier IN (");
                sql.append(req.tiers.stream().map(x -> "?").collect(Collectors.joining(",")));
                sql.append(") ");
                params.addAll(req.tiers);
            }
            sql.append("LIMIT 500");

            return jdbc.queryForList(sql.toString(), params.toArray());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ─────────────────── 2. TF-IDF 向量化 ────────────────────
    /**
     * 构建 TF-IDF 词向量
     * 词表 = CS 方向关键词 + 用户输入词
     */
    private Map<String, Double> tfidfVectorize(String text) {
        if (text == null || text.isBlank()) return Collections.emptyMap();
        List<String> tokens = nlp.tokenize(text);
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) tf.merge(t, 1, Integer::sum);

        // IDF 权重：方向关键词享有更高 IDF（稀有度奖励）
        Map<String, Double> vec = new HashMap<>();
        int total = Math.max(1, tokens.size());
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            double idf = computeIdf(e.getKey());
            vec.put(e.getKey(), (double) e.getValue() / total * idf);
        }
        return vec;
    }

    private double computeIdf(String word) {
        // 若是 CS 核心关键词，IDF 取较高值
        for (Map<String, List<String>> mid : NLPProcessor.DIRECTION_TREE.values())
            for (List<String> kws : mid.values())
                if (kws.stream().anyMatch(k -> k.equalsIgnoreCase(word)))
                    return 3.0;
        // CCF 期刊名
        if (word.length() >= 3 && Character.isUpperCase(word.charAt(0))) return 2.5;
        // 普通词
        return 1.0;
    }

    /** 余弦相似度 */
    private double cosineSimilarity(Map<String, Double> va, Map<String, Double> vb) {
        if (va.isEmpty() || vb.isEmpty()) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (Map.Entry<String, Double> e : va.entrySet()) {
            double b = vb.getOrDefault(e.getKey(), 0.0);
            dot += e.getValue() * b;
            normA += e.getValue() * e.getValue();
        }
        for (double v : vb.values()) normB += v * v;
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    // ─────────────────── 3. PageRank 声誉 ────────────────────
    /**
     * 简化版 PageRank：将导师视为节点，CCF-A 论文合著关系为边
     * 无实际引用图时，用 CCF-A 数量 + 活跃度 近似计算 "声誉分"
     *
     * 完整公式：PR(u) = (1-d)/N + d * Σ(PR(v)/OutDegree(v))
     * 此处简化：根据论文数和质量迭代收敛
     */
    private Map<String, Double> computePageRanks(List<Map<String, Object>> nodes) {
        final double D = 0.85;   // 阻尼系数
        final int ITER = 20;
        int N = Math.max(1, nodes.size());

        // 初始化：均分
        Map<String, Double> pr = new HashMap<>();
        for (Map<String, Object> row : nodes) {
            String id = str(row, "id");
            pr.put(id, 1.0 / N);
        }

        // 构建"引用质量"权重（CCF-A 数）
        Map<String, Double> qualityWeight = new HashMap<>();
        for (Map<String, Object> row : nodes) {
            String id = str(row, "id");
            String pubs = str(row, "publications") + " " + str(row, "bio");
            int ccfA = nlp.estimateCCFACount(pubs);
            qualityWeight.put(id, 1.0 + ccfA * 0.5);
        }
        double totalQW = qualityWeight.values().stream().mapToDouble(d -> d).sum();
        if (totalQW == 0) totalQW = 1;

        // 迭代
        for (int i = 0; i < ITER; i++) {
            Map<String, Double> newPr = new HashMap<>();
            double base = (1 - D) / N;
            for (Map<String, Object> row : nodes) {
                String id = str(row, "id");
                double contrib = qualityWeight.get(id) / totalQW * D;
                newPr.put(id, base + contrib * pr.values().stream().mapToDouble(d -> d).sum());
            }
            pr = newPr;
        }
        // 归一化到 [0,1]
        double maxPR = pr.values().stream().mapToDouble(d -> d).max().orElse(1);
        if (maxPR > 0) pr.replaceAll((k, v) -> v / maxPR);
        return pr;
    }

    // ─────────────────── 4. 单导师打分 ───────────────────────
    private RecommendResult scoreCandidate(
            Map<String, Object> row,
            Map<String, Double> userVec,
            Map<String, Double> pageRanks,
            RecommendRequest req) {

        RecommendResult r = new RecommendResult();
        r.advisorId  = str(row, "id");
        r.name       = str(row, "name");
        r.title      = str(row, "title");
        r.university = str(row, "university");
        r.dept       = str(row, "department");
        r.profileUrl = str(row, "profile_url");

        String bio  = str(row, "bio") + " " + str(row, "research_interests");
        String pubs = str(row, "publications");

        // a) TF-IDF 余弦相似度
        Map<String, Double> advisorVec = tfidfVectorize(bio + " " + pubs);
        r.tfIdfScore = cosineSimilarity(userVec, advisorVec);

        // b) PageRank 声誉
        r.pageRankScore = pageRanks.getOrDefault(r.advisorId, 0.3);

        // c) 研究方向匹配
        r.dirMatchScore = nlp.directionMatch(req.dirs, bio);
        Map<String, Double> topDirs = nlp.identifyDirections(bio);
        r.dirs = topDirs.entrySet().stream()
            .filter(e -> e.getValue() > 0.1)
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        // d) 导师活跃度
        int activity = nlp.estimateAdvisorActivity(bio, pubs);
        r.activityScore = activity / 100.0;

        // e) CCF-A 论文数
        r.ccfA = nlp.estimateCCFACount(bio + " " + pubs);

        // f) GPA 竞争力衰减（GPA < 3.5 对顶尖导师有惩罚）
        double gpaFactor = computeGpaFactor(req.gpa, req.rankPct, r.university);

        // ─── 综合加权 ─────────────────────────────────────────
        // 权重：方向匹配 40% | TF-IDF 25% | PageRank 20% | 活跃度 15%
        double raw = r.dirMatchScore * 0.40
                   + r.tfIdfScore    * 0.25
                   + r.pageRankScore * 0.20
                   + r.activityScore * 0.15;

        r.score = raw * gpaFactor;
        r.reason = buildReason(r, req);
        return r;
    }

    // ─────────────────── 5. 协同过滤后处理 ───────────────────
    /**
     * 基于"相似背景用户"的偏好进行重排序
     * 简化实现：从 DB 的 user_preferences 表（若存在）读取历史选择
     * 调整排名：若某导师被相似用户多次选择，综合分提升 0–10%
     */
    private void collaborativeRerank(List<RecommendResult> results, RecommendRequest req) {
        // 尝试从 DB 读取协同信号
        Map<String, Integer> cfSignal = new HashMap<>();
        try {
            String sql = "SELECT advisor_id, COUNT(*) as cnt FROM user_selections " +
                         "WHERE gpa_bucket = ? GROUP BY advisor_id";
            String bucket = req.gpa >= 3.8 ? "high" : req.gpa >= 3.5 ? "mid" : "low";
            jdbc.queryForList(sql, bucket).forEach(row -> {
                String id = str(row, "advisor_id");
                int cnt   = ((Number) row.getOrDefault("cnt", 0)).intValue();
                cfSignal.put(id, cnt);
            });
        } catch (Exception ignored) {
            // user_selections 表不存在时跳过
        }

        if (cfSignal.isEmpty()) return;
        int maxCF = cfSignal.values().stream().mapToInt(i -> i).max().orElse(1);
        for (RecommendResult r : results) {
            int cf = cfSignal.getOrDefault(r.advisorId, 0);
            r.score = Math.min(1.0, r.score * (1 + 0.10 * cf / maxCF));
        }
        results.sort(Comparator.comparingDouble((RecommendResult r) -> r.score).reversed());
    }

    // ─────────────────── 辅助方法 ─────────────────────────────
    private String buildUserText(RecommendRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.dirs != null) req.dirs.forEach(d -> sb.append(d).append(" "));
        if (req.expKeywords != null) sb.append(req.expKeywords).append(" ");
        return sb.toString();
    }

    private double computeGpaFactor(double gpa, double rankPct, String university) {
        // 顶尖院校对 GPA 要求更高
        boolean topUniv = Arrays.asList("清华大学","北京大学","浙江大学","上海交通大学","复旦大学")
            .contains(university);
        double minGpa = topUniv ? 3.7 : 3.3;
        if (gpa >= minGpa && rankPct <= 20) return 1.0;      // 满足门槛
        if (gpa < minGpa - 0.3 && rankPct > 30) return 0.6;  // 背景偏弱
        return 0.85;
    }

    private String buildReason(RecommendResult r, RecommendRequest req) {
        List<String> parts = new ArrayList<>();
        if (r.dirMatchScore >= 0.6) parts.add("研究方向高度匹配");
        else if (r.dirMatchScore >= 0.3) parts.add("研究方向部分匹配");
        if (r.ccfA >= 8) parts.add("CCF-A 论文丰富（" + r.ccfA + "篇）");
        else if (r.ccfA >= 3) parts.add("有 CCF-A 论文");
        if (r.pageRankScore >= 0.7) parts.add("学术声誉高");
        if (r.activityScore >= 0.7) parts.add("近年活跃");
        if (r.activityScore < 0.4) parts.add("活跃度一般，建议先联系确认招生");
        if (parts.isEmpty()) parts.add("综合背景适合");
        return String.join("，", parts);
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    // ─────────────────── 6. 相似用户查找（User-Based CF）────────
    /**
     * 在已有用户向量矩阵中找到与当前用户最相似的 K 个用户
     * 相似度基于：方向选择余弦 + GPA 档位匹配
     */
    public List<String> findSimilarUsers(RecommendRequest req, int K) {
        // 构建用户向量（方向独热编码）
        List<String> allDirs = NLPProcessor.DIRECTION_TREE.keySet()
            .stream().collect(Collectors.toList());
        double[] userVec = new double[allDirs.size()];
        if (req.dirs != null) {
            for (String d : req.dirs) {
                int idx = allDirs.indexOf(d);
                if (idx >= 0) userVec[idx] = 1.0;
            }
        }

        // 从 DB 拉取已有用户向量（简化：读取历史推荐记录）
        List<Map<String, Object>> users = Collections.emptyList();
        try {
            users = jdbc.queryForList(
                "SELECT user_id, direction_vec, gpa_bucket FROM user_profiles LIMIT 200");
        } catch (Exception ignored) {}

        return users.stream()
            .map(u -> {
                double sim = gpaMatch(req.gpa, str(u, "gpa_bucket")) * 0.3;
                // direction_vec 是逗号分隔的 0/1 字符串
                String[] parts = str(u, "direction_vec").split(",");
                double dot = 0, normU = 0, normC = 0;
                for (int i = 0; i < Math.min(parts.length, userVec.length); i++) {
                    double cv = Double.parseDouble(parts[i].trim());
                    dot   += userVec[i] * cv;
                    normU += userVec[i] * userVec[i];
                    normC += cv * cv;
                }
                double cosine = (normU > 0 && normC > 0)
                    ? dot / (Math.sqrt(normU) * Math.sqrt(normC)) : 0;
                return Map.entry(str(u, "user_id"), sim + cosine * 0.7);
            })
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(K)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private double gpaMatch(double gpa, String bucket) {
        if ("high".equals(bucket) && gpa >= 3.7) return 1.0;
        if ("mid".equals(bucket)  && gpa >= 3.4 && gpa < 3.7) return 1.0;
        if ("low".equals(bucket)  && gpa < 3.4)  return 1.0;
        return 0.3;
    }

    // ─────────────────── 7. 内容过滤（Content-Based）────────────
    /**
     * 纯内容过滤推荐：只基于 TF-IDF 余弦相似度，不依赖历史数据
     * 适用于冷启动场景
     */
    public List<RecommendResult> contentBasedRecommend(RecommendRequest req) {
        List<Map<String, Object>> candidates = fetchCandidates(req);
        if (candidates.isEmpty()) return Collections.emptyList();

        String userText = buildUserText(req);
        Map<String, Double> userVec = tfidfVectorize(userText);

        return candidates.stream()
            .map(row -> {
                String bio = str(row, "bio") + " " + str(row, "research_interests");
                Map<String, Double> av = tfidfVectorize(bio);
                double sim = cosineSimilarity(userVec, av);

                RecommendResult r = new RecommendResult();
                r.advisorId  = str(row, "id");
                r.name       = str(row, "name");
                r.university = str(row, "university");
                r.score      = sim;
                r.tfIdfScore = sim;
                r.reason     = sim > 0.5 ? "内容高度相关" : "内容部分相关";
                return r;
            })
            .filter(r -> r.score > 0.01)
            .sorted(Comparator.comparingDouble((RecommendResult r) -> r.score).reversed())
            .limit(req.limit)
            .collect(Collectors.toList());
    }

    // ─────────────────── 8. 实时评分更新（Online Learning）─────
    /**
     * 用户点击/收藏某导师后，记录正反馈
     * 使用指数移动平均更新该导师的全局热度分
     */
    public void recordFeedback(String advisorId, boolean positive) {
        double delta = positive ? +0.02 : -0.01;
        try {
            int rows = jdbc.update(
                "UPDATE teachers SET popularity_score = " +
                "MIN(1.0, MAX(0.0, COALESCE(popularity_score, 0.5) + ?)) WHERE id = ?",
                delta, advisorId);
            if (rows == 0) {
                // 若列不存在，忽略
            }
        } catch (Exception ignored) {}
    }

    // ─────────────────── 9. 批量重新评分（离线任务）────────────
    /**
     * 定期重新计算所有导师的综合声誉分（可由定时器调用）
     */
    public void rebuildAllScores() {
        List<Map<String, Object>> all;
        try {
            all = jdbc.queryForList(
                "SELECT id, bio, publications, research_interests FROM teachers");
        } catch (Exception e) { return; }

        Map<String, Double> pageRanks = computePageRanks(all);

        for (Map<String, Object> row : all) {
            String id   = str(row, "id");
            String bio  = str(row, "bio") + " " + str(row, "research_interests");
            String pubs = str(row, "publications");
            int activity = nlp.estimateAdvisorActivity(bio, pubs);
            int ccfA     = nlp.estimateCCFACount(bio + " " + pubs);
            double pr    = pageRanks.getOrDefault(id, 0.3);
            double composite = pr * 0.5 + (activity / 100.0) * 0.3 + (ccfA / 20.0) * 0.2;
            try {
                jdbc.update("UPDATE teachers SET reputation_score = ? WHERE id = ?",
                    Math.min(1.0, composite), id);
            } catch (Exception ignored) {}
        }
    }
}