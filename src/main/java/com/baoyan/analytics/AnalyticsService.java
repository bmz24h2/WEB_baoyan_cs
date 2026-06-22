package com.baoyan.analytics;

import com.baoyan.db.DatabaseService;
import com.baoyan.analytics.MatchService;
import com.baoyan.model.Teacher;
import com.baoyan.model.SchoolInfo;
import com.baoyan.db.DatabaseService;
import java.sql.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.sql.*;
@Service
class AnalyticsService {

private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

@Autowired
private DatabaseService db;

@Autowired
private MatchService matchService;

@Cacheable("analytics-overview")
public Map<String, Object> getOverview() {
    Map<String, Object> r = new LinkedHashMap<>();
    try (Connection conn = db.getConnection()) {
        r.put("totalTeachers",       queryLong(conn, "SELECT COUNT(*) FROM teachers"));
        r.put("universitiesWithData",queryLong(conn, "SELECT COUNT(DISTINCT university) FROM teachers"));
        r.put("total985",            queryLong(conn, "SELECT COUNT(*) FROM universities WHERE tier NOT LIKE '%211%'"));
        r.put("unis985WithData",     queryLong(conn, "SELECT COUNT(DISTINCT t.university) FROM teachers t JOIN universities u ON t.university = u.name"));
        r.put("totalScrapeRuns",     queryLong(conn, "SELECT COUNT(*) FROM scrape_log"));
        r.put("uniqueAreas",         queryLong(conn, "SELECT COUNT(DISTINCT research_areas) FROM teachers WHERE research_areas IS NOT NULL"));
    } catch (Exception e) {
        log.error("[Analytics] getOverview 失败", e);
    }
    return r;
}

@Cacheable("analytics-distribution")
public Map<String, Object> getDistribution(int topN) {
    List<Map<String, Object>> items = new ArrayList<>();
    String sql = """
        SELECT t.university, COUNT(*) AS cnt, u.tier
        FROM teachers t
        LEFT JOIN universities u ON t.university = u.name
        GROUP BY t.university
        ORDER BY cnt DESC
        LIMIT ?
        """;
    try (Connection conn = db.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, topN);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name",  rs.getString("university"));
            item.put("count", rs.getLong("cnt"));
            item.put("tier",  rs.getString("tier"));
            items.add(item);
        }
    } catch (Exception e) {
        log.error("[Analytics] getDistribution 失败", e);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("items", items);
    return result;
}

@Cacheable("analytics-tiers")
public Map<String, Object> getTiers() {
    List<Map<String, Object>> tiers = new ArrayList<>();
    String sql = """
        SELECT u.tier, COUNT(*) AS cnt
        FROM teachers t
        JOIN universities u ON t.university = u.name
        GROUP BY u.tier
        ORDER BY cnt DESC
        """;
    try (Connection conn = db.getConnection();
         Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery(sql)) {
        while (rs.next()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name",  rs.getString("tier"));
            item.put("count", rs.getLong("cnt"));
            tiers.add(item);
        }
    } catch (Exception e) {
        log.error("[Analytics] getTiers 失败", e);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("tiers", tiers);
    return result;
}

// ── 研究方向热度（复用 MatchService.DIR_EXPAND 语义扩展表）─────────────
    @Cacheable("analytics-areas")
    public List<Map<String, Object>> getResearchAreas(int limit) {
        // 直接用 MatchService 的 DIR_EXPAND 关键词表，每位教师对每个方向最多计 1 次
        Map<String, List<String>> dirExpand = matchService.getDirExpand();
        int[] counts = new int[dirExpand.size()];
        List<String> dirNames = new ArrayList<>(dirExpand.keySet());

        String sql = "SELECT research_areas FROM teachers WHERE research_areas IS NOT NULL AND research_areas != ''";
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String raw = rs.getString(1);
                if (raw == null || raw.isBlank()) continue;
                String lower = raw.toLowerCase();
                for (int i = 0; i < dirNames.size(); i++) {
                    List<String> keywords = dirExpand.get(dirNames.get(i));
                    for (String kw : keywords) {
                        if (lower.contains(kw.toLowerCase())) { counts[i]++; break; }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Analytics] getResearchAreas 失败", e);
        }

        List<Map<String, Object>> areas = new ArrayList<>();
        for (int i = 0; i < dirNames.size(); i++) {
            if (counts[i] == 0) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("keyword", dirNames.get(i));
            item.put("count",   counts[i]);
            areas.add(item);
        }
        areas.sort((a, b) -> ((Integer) b.get("count")) - ((Integer) a.get("count")));
        if (areas.size() > limit) areas = new ArrayList<>(areas.subList(0, limit));
        return areas;
    }

    // ── 各方向近10年活跃教师趋势（基于 counts_by_year 字段）────────────────
    // 返回格式：{years:[2015..2025], series:[{name:"机器学习", data:[1,2,3...]}, ...]}
    public Map<String, Object> getAreaTrend(int minYear, int maxYear) {
        Map<String, List<String>> dirExpand = matchService.getDirExpand();
        List<String> dirNames = new ArrayList<>(dirExpand.keySet());
        int yearCount = maxYear - minYear + 1;

        // dirName → [year_index → count]
        Map<String, int[]> trendData = new LinkedHashMap<>();
        for (String d : dirNames) trendData.put(d, new int[yearCount]);

        String sql = "SELECT research_areas, counts_by_year FROM teachers " +
                     "WHERE counts_by_year IS NOT NULL AND counts_by_year != '[]'";
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String areas  = rs.getString("research_areas");
                String cbyRaw = rs.getString("counts_by_year");
                if (areas == null || cbyRaw == null) continue;
                String areasLower = areas.toLowerCase();

                // 解析 counts_by_year JSON: [{y:2020,w:3,c:10},...]
                java.util.regex.Matcher ym = java.util.regex.Pattern
                    .compile("\"y\"\\s*:\\s*([0-9]{4}).*?\"w\"\\s*:\\s*([0-9]+)")
                    .matcher(cbyRaw);
                Set<Integer> activeYears = new HashSet<>();
                while (ym.find()) {
                    int yr = Integer.parseInt(ym.group(1));
                    int wc = Integer.parseInt(ym.group(2));
                    if (wc > 0 && yr >= minYear && yr <= maxYear) activeYears.add(yr);
                }
                if (activeYears.isEmpty()) continue;

                // 对每个方向，如果该教师研究方向匹配，则在其活跃年份上 +1
                for (int i = 0; i < dirNames.size(); i++) {
                    String dir = dirNames.get(i);
                    List<String> keywords = dirExpand.get(dir);
                    boolean matched = keywords.stream()
                        .anyMatch(kw -> areasLower.contains(kw.toLowerCase()));
                    if (!matched) continue;
                    int[] arr = trendData.get(dir);
                    for (int yr : activeYears) arr[yr - minYear]++;
                }
            }
        } catch (Exception e) {
            log.error("[Analytics] getAreaTrend 失败", e);
        }

        // 组装结果，只保留有数据的方向（最大值 >= 2）
        List<Integer> years = new ArrayList<>();
        for (int y = minYear; y <= maxYear; y++) years.add(y);

        List<Map<String, Object>> series = new ArrayList<>();
        for (String dir : dirNames) {
            int[] arr = trendData.get(dir);
            int maxVal = 0; for (int v : arr) maxVal = Math.max(maxVal, v);
            if (maxVal < 2) continue;
            List<Integer> data = new ArrayList<>();
            for (int v : arr) data.add(v);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", dir);
            s.put("data", data);
            series.add(s);
        }
        series.sort((a, b) -> {
            List<Integer> da = (List<Integer>) a.get("data");
            List<Integer> db2 = (List<Integer>) b.get("data");
            int sa = da.stream().mapToInt(Integer::intValue).sum();
            int sb = db2.stream().mapToInt(Integer::intValue).sum();
            return sb - sa;
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("years",  years);
        result.put("series", series.size() > 10 ? series.subList(0, 10) : series);
        result.put("note",   "基于 OpenAlex 论文年份统计，需重爬后数据才完整");
        return result;
    }

    @CacheEvict(value = {"analytics-overview", "analytics-distribution", "analytics-tiers", "analytics-areas"}, allEntries = true)
public void evictAllCaches() {
    log.info("[Cache] 分析缓存已清除");
}

private long queryLong(Connection conn, String sql) throws SQLException {
    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
        return rs.next() ? rs.getLong(1) : 0;
    }
}
}