package com.baoyan.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * 简历解析 → 自动填充个人信息
 *
 * POST /api/profile/extract-from-resume
 *   file: 上传的简历文件（.docx / .pdf / .txt）
 *   → 提取文字 → 调 AI → 返回结构化 JSON
 *
 * 支持格式：
 *   .docx — ZIP 解析 word/document.xml（最可靠）
 *   .txt / .md — 直接读文本
 *   .pdf — 基础文字提取（复杂排版可能不完整，建议用 docx）
 *
 * 模型优先级：阿里云（20秒超时）→ 超时/失败自动切智谱兜底
 */
@RestController
@RequestMapping("/api/profile")
public class ResumeExtractController {

    private static final Logger log = LoggerFactory.getLogger(ResumeExtractController.class);

    @Value("${aliyun.api.key:${qwen.api.key:}}")
    private String aliyunKey;

    @Value("${zhipu.api.key:}")
    private String zhipuKey;

    private static final String ALIYUN_ENDPOINT =
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String ZHIPU_ENDPOINT  =
        "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    // ── 主入口 ────────────────────────────────────────────────────────
    @PostMapping("/extract-from-resume")
    public ResponseEntity<Map<String, Object>> extract(
            @RequestParam("file") MultipartFile file) {

        String filename = Objects.requireNonNull(file.getOriginalFilename(), "").toLowerCase();
        log.info("[简历解析] 收到文件: {} ({} bytes)", filename, file.getSize());

        // Step 1: 提取文本
        String text;
        try {
            byte[] bytes = file.getBytes();
            if (filename.endsWith(".docx")) {
                text = extractDocxText(bytes);
            } else if (filename.endsWith(".pdf")) {
                text = extractPdfText(bytes);
            } else {
                text = new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("[简历解析] 文件读取失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", "文件读取失败：" + e.getMessage()));
        }

        if (text.isBlank() || text.length() < 20) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "未能从文件中提取到有效文字，请尝试上传 .docx 或 .txt 格式"));
        }

        // 限制长度（避免超出 token 限制）
        if (text.length() > 4000) text = text.substring(0, 4000) + "\n…（已截断）";
        log.info("[简历解析] 提取文字 {} 字", text.length());

        // Step 2: 调 AI 结构化提取（阿里云 → 智谱 fallback）
        String aiPrompt = buildExtractionPrompt(text);
        String aiResponse;
        try {
            aiResponse = callLlmWithFallback(aiPrompt);
        } catch (Exception e) {
            log.error("[简历解析] AI 调用失败: {}", e.getMessage());
            return ResponseEntity.status(503)
                .body(Map.of("error", "AI 服务暂不可用：" + e.getMessage(),
                             "rawText", text));
        }

        // Step 3: 解析 JSON 响应
        log.info("[简历解析] AI原始返回(前2000字):\n{}", aiResponse.substring(0, Math.min(2000, aiResponse.length())));
        Map<String, Object> extracted = parseJsonResponse(aiResponse);
        extracted.put("_rawText", text.length() > 300 ? text.substring(0, 300) + "…" : text);
        extracted.put("_filename", filename);

        // ★ Step 4: 正则兜底修正关键字段（AI 可能截断邮箱等）
        // 邮箱：直接从原文正则提取，比 AI 更可靠
        java.util.regex.Matcher emailM = java.util.regex.Pattern
            .compile("[a-zA-Z0-9._%+\\-]{1,64}@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")
            .matcher(text);
        if (emailM.find()) {
            String regexEmail = emailM.group();
            String aiEmail = String.valueOf(extracted.getOrDefault("email", ""));
            if (!regexEmail.equals(aiEmail)) {
                log.info("[简历解析] 邮箱正则修正: AI提取={} → 正则={}", aiEmail, regexEmail);
                extracted.put("email", regexEmail);
            }
        }

        // ★ Step 5: 正则兜底修正 eduStart/eduEnd
        // 从原文找第一个 YYYY.MM 格式（入学年月通常最早出现）
        java.util.regex.Matcher eduM = java.util.regex.Pattern
            .compile("(20\\d{2})[.年/](0?[1-9]|1[0-2])[月]?")
            .matcher(text);
        if (eduM.find()) {
            String foundYear  = eduM.group(1);
            String foundMonth = String.format("%02d", Integer.parseInt(eduM.group(2)));
            String foundStart = foundYear + "." + foundMonth;
            String aiStart = String.valueOf(extracted.getOrDefault("eduStart", ""));
            if (aiStart.isBlank()) {
                log.info("[简历解析] eduStart 正则兜底: {}", foundStart);
                extracted.put("eduStart", foundStart);
            }
            // eduEnd 兜底：AI 未提取且原文含"至今"/"在读"时，入学年+4
            String aiEnd = String.valueOf(extracted.getOrDefault("eduEnd", ""));
            if (aiEnd.isBlank()) {
                boolean onGoing = text.contains("至今") || text.contains("在读") || text.contains("present");
                if (onGoing) {
                    String inferredEnd = (Integer.parseInt(foundYear) + 4) + ".06";
                    log.info("[简历解析] eduEnd 推算（至今+4年）: {}", inferredEnd);
                    extracted.put("eduEnd", inferredEnd);
                }
            }
        }

        log.info("[简历解析] 提取完成: name={}, school={}, email={}", extracted.get("name"), extracted.get("school"), extracted.get("email"));
        return ResponseEntity.ok(extracted);
    }

    /** 一键清除服务器端个人数据（自定义模板）*/
    @DeleteMapping("/clear-personal-data")
    public ResponseEntity<Map<String,Object>> clearPersonalData() {
        int count = 0;
        try {
            java.nio.file.Path customDir = java.nio.file.Paths.get("output","custom-templates");
            if (java.nio.file.Files.exists(customDir)) {
                try (var stream = java.nio.file.Files.list(customDir)) {
                    for (java.nio.file.Path p : stream.toList()) {
                        java.nio.file.Files.deleteIfExists(p);
                        count++;
                    }
                }
            }
            log.info("[清除个人数据] 已删除自定义模板 {} 个", count);
        } catch (Exception e) {
            log.warn("[清除个人数据] 失败: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage(), "deletedTemplates", count));
        }
        return ResponseEntity.ok(Map.of("deletedTemplates", count, "message", "清除成功"));
    }

    private String extractDocxText(byte[] docxBytes) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zin = new ZipInputStream(new java.io.ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                    // 段落标签换行，其余标签直接删除（不加空格，避免分割连续文字如邮箱）
                    String text = xml.replaceAll("<w:p[^>]*>", "\n")
                                     .replaceAll("<[^>]+>", "")
                                     .replaceAll("[ \t]+", " ")
                                     .replaceAll("\n[ ]+", "\n")
                                     .replaceAll("\n{3,}", "\n\n");
                    text = text.replace("&amp;","&").replace("&lt;","<")
                               .replace("&gt;",">").replace("&quot;","\"")
                               .replace("&#x2019;","'").replace("&#x2018;","'");
                    sb.append(text);
                    break;
                }
            }
        }
        return sb.toString().trim();
    }

    private String extractPdfText(byte[] pdfBytes) {
        try {
            String raw = new String(pdfBytes, StandardCharsets.ISO_8859_1);
            StringBuilder sb = new StringBuilder();

            Pattern btPat = Pattern.compile("BT\\s+(.*?)\\s+ET", Pattern.DOTALL);
            Pattern tjPat = Pattern.compile("\\(((?:[^()\\\\]|\\\\.)*)\\)\\s*Tj");
            Pattern taPat = Pattern.compile("\\[((?:[^\\[\\]]*)*)\\]\\s*TJ");
            Pattern strPat= Pattern.compile("\\(((?:[^()\\\\]|\\\\.)*)\\)");

            Matcher btM = btPat.matcher(raw);
            while (btM.find()) {
                String block = btM.group(1);
                Matcher tjM = tjPat.matcher(block);
                while (tjM.find()) {
                    sb.append(decodePdfString(tjM.group(1))).append(" ");
                }
                Matcher taM = taPat.matcher(block);
                while (taM.find()) {
                    Matcher sm = strPat.matcher(taM.group(1));
                    while (sm.find()) sb.append(decodePdfString(sm.group(1)));
                    sb.append(" ");
                }
                sb.append("\n");
            }

            String result = sb.toString().replaceAll("\\s+", " ").trim();
            if (result.length() < 50) {
                Pattern anyStr = Pattern.compile("\\(([\\x20-\\x7E\\u4e00-\\u9fff]{4,100})\\)");
                Matcher m = anyStr.matcher(raw);
                StringBuilder sb2 = new StringBuilder();
                while (m.find()) sb2.append(m.group(1)).append(" ");
                result = sb2.toString();
            }
            return result.trim();
        } catch (Exception e) {
            log.warn("[PDF提取] 失败: {}", e.getMessage());
            return "";
        }
    }

    private String decodePdfString(String s) {
        return s.replace("\\n","\n").replace("\\r","").replace("\\t"," ")
                .replace("\\\\","\\").replace("\\(","(").replace("\\)",")")
                .replaceAll("\\\\[0-7]{3}", "");
    }

    // ── AI 提示词 ─────────────────────────────────────────────────────

    private String buildExtractionPrompt(String resumeText) {
        // 注意：不能用 .formatted() —— 提示词含中文全角字符会被误判为格式符抛异常
        String template =
            "请从以下简历中提取结构化信息，以纯 JSON 格式返回，不要加 ```json 包装，不要任何说明文字。\n\n" +
            "字段说明：\n" +
            "- name: 姓名（中文）\n" +
            "- school: 本科院校全名（如\"华中科技大学\"）\n" +
            "- major: 专业名称（如\"计算机科学与技术\"）\n" +
            "- grade: 年级（如\"大三\"、\"大四\"）\n" +
            "- gpa: GPA 或绩点数值字符串（如\"3.8\"或\"3.8/5.0\"）。若简历写的是加权平均分/均分/百分制成绩（如90分），原样填入；若写的是绩点（如3.8/4.0、绩点3.85），提取数字部分；找不到则填\"\"\n" +
            "- rankPct: 专业排名，原样提取完整字符串（如写\"第2/139\"原样填\"2/139\"，如写\"前10%\"填\"10\"，如写\"2/139\"必须完整提取不能截断为\"2/13\"）\n" +
            "- email: 邮箱地址（必须完整提取，包含@前后所有字符，格式为 xxx@yyy.zzz；若无法确认完整邮箱则填\"\"，宁可为空也不要填不完整的）\n" +
            "- experience: 【最重要字段必须完整】科研/项目/论文/竞赛经历，逐条原文列出，\n" +
            "  论文含完整题目+作者+期刊+投稿状态；项目含时间+全名+级别+角色+描述；\n" +
            "  竞赛含时间+赛事+奖项+角色。一条都不能省略，不得压缩内容\n" +
            "- awards: 【只填有明确奖项/荣誉等级的内容】，包括：奖学金（含年份）、竞赛获奖（含等级如国家级/省级）、三好学生、优秀学生等学校认定的荣誉称号。\n" +
            "  注意：【不包含】志愿服务经历本身（如「担任马拉松志愿者」是经历不是奖项）；\n" +
            "  但如果获得了「优秀志愿者」这个称号，则仅把称号名称放 awards，具体经历放 practice。\n" +
            "  示例 awards：校级二等奖学金（2023-2024）、院级三好学生（2024）、国家级三等奖\n" +
            "- practice: 【只填实践/志愿/学生工作的经历描述】，如：担任XX志愿者并获优秀称号、参加XX社会实践团、担任学生干部等。\n" +
            "  经历描述（时间+做了什么+结果）放这里；奖项名称本身（如「优秀志愿者称号」）放 awards。\n" +
            "  示例 practice：担任2025年无锡马拉松志愿者，获优秀志愿者称号；加入绿动联盟社会实践团，获院级优秀团队。若无则填\"\"\n" +
            "- eduStart: 入学年月，格式 YYYY.MM（如\"2021.09\"）。从教育经历中提取，若写成\"2021年9月入学\"则转为\"2021.09\"。找不到则填\"\"\n" +
            "- eduEnd: 毕业/预计毕业年月，格式 YYYY.MM（如\"2025.06\"）。若原文写\"至今\"、\"在读\"，则取 eduStart 的年份+4，月份固定06，如\"2021.09至今\"则填\"2025.06\"。找不到则填\"\"\n" +
            "- targetDirs: 数组，推断出的研究兴趣方向（从项目/论文/课程推断，如[\"机器学习\",\"计算机视觉\"]）\n\n" +
            "若某字段无法从简历中获取，该字段填 \"\" 或 []。\n\n" +
            "只返回 JSON，示例格式：\n" +
            "{\"name\":\"张三\",\"school\":\"华中科技大学\",\"major\":\"计算机科学与技术\",\"grade\":\"大三\",\"gpa\":\"3.8\",\"rankPct\":\"5\",\"email\":\"zs@hust.edu.cn\",\"eduStart\":\"2021.09\",\"eduEnd\":\"2025.06\",\"experience\":\"参与图像识别项目，复现ResNet，投稿CVPR workshop\",\"awards\":\"国家奖学金、计算机设计大赛省级一等奖\",\"targetDirs\":[\"计算机视觉\",\"机器学习\"]}\n\n" +
            "简历内容：\n";
        // 若简历较长，把「科研经历」部分提前，确保AI在token限制内优先看到
        String processedText = resumeText;
        int sciIdx = resumeText.indexOf("科研经历");
        if (sciIdx > 0 && resumeText.length() > 3500) {
            String sciPart = resumeText.substring(sciIdx);
            String basicPart = resumeText.substring(0, sciIdx);
            processedText = "===科研经历（此段最重要，必须完整提取到experience字段）===\n"
                + sciPart + "\n\n===基本信息===\n" + basicPart;
        }
        return template + processedText + "\n";
    }

    // ── LLM 调用：阿里云按优先级逐个尝试，最终切智谱兜底 ────────────────

    /** 阿里云模型列表，按优先级排列（与 ModelRegistry 保持同步） */
    private static final String[] ALIYUN_MODELS = {
        "qwen3.6-flash",
        "kimi-k2.6",
        "qwen3.6-27b",
        "deepseek-v4-pro",
        "qwen3.7-max",
        "qwen3.7-max-2026-05-20",
        "qwen3.7-plus-2026-05-26",
    };

    /**
     * 带 fallback 的 LLM 调用：
     *   1. 阿里云：按 ALIYUN_MODELS 顺序逐个尝试（每个 60 秒超时）
     *   2. 全部失败 → 切智谱 glm-4-flash（90 秒超时，永久免费）
     *   3. 两者均失败 → 抛异常
     */
    private String callLlmWithFallback(String prompt) throws Exception {
        if (!aliyunKey.isBlank()) {
            for (String model : ALIYUN_MODELS) {
                try {
                    log.info("[简历解析] 尝试阿里云模型: {}", model);
                    return callLlm(ALIYUN_ENDPOINT, aliyunKey, model, prompt, 60);
                } catch (Exception e) {
                    log.warn("[简历解析] 模型 {} 失败（{}），尝试下一个", model, e.getMessage());
                }
            }
            log.warn("[简历解析] 所有阿里云模型均失败，切换智谱兜底");
        }
        if (!zhipuKey.isBlank()) {
            log.info("[简历解析] 使用智谱 glm-4-flash 兜底");
            return callLlm(ZHIPU_ENDPOINT, zhipuKey, "glm-4-flash", prompt, 90);
        }
        throw new IllegalStateException("未配置 AI API Key（aliyun.api.key 或 zhipu.api.key）");
    }

    private String callLlm(String endpoint, String key, String model,
                            String prompt, int timeoutSeconds) throws Exception {
        String body = "{\"model\":\"" + model + "\",\"max_tokens\":6000,\"stream\":false," +
                      "\"messages\":[{\"role\":\"user\",\"content\":" + jsonString(prompt) + "}]}";

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

        HttpResponse<String> resp = client.send(
            java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type",  "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " +
                resp.body().substring(0, Math.min(200, resp.body().length())));
        }

        String respBody = resp.body();
        Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(respBody);
        if (m.find()) {
            return m.group(1)
                .replace("\\n", "\n").replace("\\\"", "\"")
                .replace("\\\\", "\\").replace("\\/", "/");
        }
        int ci = respBody.indexOf("\"content\"");
        if (ci >= 0) {
            int sq = respBody.indexOf('"', ci + 10);
            int eq = respBody.lastIndexOf('"', respBody.indexOf("\"role\"", ci) - 1);
            if (sq >= 0 && eq > sq) return respBody.substring(sq + 1, eq);
        }
        throw new RuntimeException("无法解析响应: " +
            respBody.substring(0, Math.min(300, respBody.length())));
    }

    /** 将字符串转义为 JSON 字符串值（包含引号） */
    private static String jsonString(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"")
                       .replace("\n","\\n").replace("\r","\\r")
                       .replace("\t","\\t") + "\"";
    }

    // ── 解析 AI 返回的 JSON ───────────────────────────────────────────

    private Map<String, Object> parseJsonResponse(String raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        String json = raw.replaceAll("(?s)^```json\\s*", "")
                         .replaceAll("(?s)```\\s*$", "")
                         .trim();

        int start = json.indexOf('{'), end = json.lastIndexOf('}');
        if (start < 0) {
            result.put("error", "AI 返回格式异常: " + raw.substring(0, Math.min(200, raw.length())));
            return result;
        }
        // 末尾 } 可能因 max_tokens 截断而缺失，end<0 时退而用整段
        json = (end > start) ? json.substring(start, end + 1) : json.substring(start);

        String[] keys = {"name","school","major","grade","gpa","rankPct","email","eduStart","eduEnd","experience","awards","practice"};

        // ── 容错提取：按 "key": 的位置把每个字段的值切片出来 ──
        // 对 experience / awards 这类长文本字段尤其重要：小模型生成时
        // 经常出现字面换行或未转义的引号、书名号《》，严格 JSON 正则会截断。
        // 这里改为「从本字段冒号后，一直读到下一个已知 key 出现之前」。
        for (String k : keys) {
            String val = extractField(json, k, keys);
            result.put(k, val != null ? val : "");
        }

        // experience/awards 可能是数组，extractField 已经处理，但再做一次兜底确认
        // （确保返回 String 而不是 null）
        if (result.get("experience") == null || result.get("experience").equals("")) {
            String expArr = extractArrayField(json, "experience");
            if (expArr != null) result.put("experience", expArr);
        }
        if (result.get("awards") == null || result.get("awards").equals("")) {
            String awdArr = extractArrayField(json, "awards");
            if (awdArr != null) result.put("awards", awdArr);
        }
        if (result.get("practice") == null || result.get("practice").equals("")) {
            String prcArr = extractArrayField(json, "practice");
            if (prcArr != null) result.put("practice", prcArr);
        }

        // targetDirs 数组
        Pattern arrP = Pattern.compile("\"targetDirs\"\\s*:\\s*\\[([^\\]]*)]", Pattern.DOTALL);
        Matcher arrM = arrP.matcher(json);
        if (arrM.find()) {
            List<String> dirs = new ArrayList<>();
            Pattern strP = Pattern.compile("\"([^\"]+)\"");
            Matcher sm = strP.matcher(arrM.group(1));
            while (sm.find()) dirs.add(sm.group(1).trim());
            result.put("targetDirs", dirs);
        } else {
            result.put("targetDirs", new ArrayList<>());
        }

        // 邮箱格式校验：不符合 xxx@yyy.zzz 格式则清空，避免 AI 提取不完整
        Object emailVal = result.get("email");
        if (emailVal instanceof String emailStr && !emailStr.isBlank()) {
            if (!emailStr.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                log.warn("[简历解析] 邮箱格式异常，已清空: {}", emailStr);
                result.put("email", "");
            }
        }

        return result;
    }

    /**
     * 提取数组字段（如 experience/awards 被 AI 输出为 [...] 格式时），
     * 把数组里的每个字符串元素拼成多行文本返回。
     */
    private static String extractArrayField(String json, String key) {
        Pattern ap = Pattern.compile(
            "\"" + key + "\"\\s*:\\s*\\[([^\\[]*?)\\]",
            Pattern.DOTALL);
        Matcher am = ap.matcher(json);
        if (!am.find()) return null;
        String inner = am.group(1);
        // 提取数组里每个 "..." 字符串
        List<String> items = new ArrayList<>();
        Pattern sp = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL);
        Matcher sm = sp.matcher(inner);
        while (sm.find()) {
            String item = sm.group(1)
                .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
            if (!item.isBlank()) items.add(item.trim());
        }
        return items.isEmpty() ? null : String.join("\n", items);
    }

    /**
     * 容错提取单个字段值。
     * 先检查是否为数组格式（experience/awards AI 常返回数组），
     * 再用「严格正则」和「切片法」两个结果取更长的那个。
     */
    private static String extractField(String json, String key, String[] allKeys) {
        // ★ 优先尝试数组格式（AI 有时把 experience/awards 输出为 [...]）
        String asArray = extractArrayField(json, key);
        if (asArray != null && !asArray.isBlank()) return asArray;

        // 结果 A：严格正则（值转义规范时最干净）
        String strict = null;
        Matcher m = Pattern
            .compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL)
            .matcher(json);
        if (m.find()) strict = unescape(m.group(1)).trim();

        // 结果 B：切片法（定位 key 后读到下一个已知 key 之前）
        String sliced = null;
        Matcher km = Pattern.compile("\"" + key + "\"\\s*:\\s*\"", Pattern.DOTALL).matcher(json);
        if (km.find()) {
            int valStart = km.end();
            int valEnd = json.length();
            for (String other : allKeys) {
                if (other.equals(key)) continue;
                Matcher om = Pattern.compile("\"" + other + "\"\\s*:").matcher(json);
                if (om.find(valStart) && om.start() < valEnd) valEnd = om.start();
            }
            Matcher tm = Pattern.compile("\"targetDirs\"\\s*:").matcher(json);
            if (tm.find(valStart) && tm.start() < valEnd) valEnd = tm.start();

            String chunk = json.substring(valStart, valEnd);
            chunk = chunk.replaceAll("[,}\\s]*$", "");   // 去末尾 , } 空白
            chunk = chunk.replaceAll("\"$", "");          // 去末尾引号
            sliced = unescape(chunk).trim();
        }

        // 两者都没有 → null
        if (strict == null && sliced == null) return null;
        if (strict == null) return sliced;
        if (sliced == null) return strict;
        // 取更长的：裸引号截断时 strict 会明显偏短
        return (sliced.length() > strict.length()) ? sliced : strict;
    }

    /** 还原 JSON 转义序列为可读文本 */
    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\r", "")
                .replace("\\t", "    ").replace("\\\"", "\"")
                .replace("\\/", "/").replace("\\\\", "\\");
    }
}