package com.baoyan.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/**
 * 文档生成接口
 *
 * 原理：docx 本质是 ZIP 压缩包，核心内容在 word/document.xml。
 * 无需 Apache POI，直接用 Java 标准 ZipInputStream/ZipOutputStream 操作。
 *
 * 端点：
 *   GET  /api/documents/templates              → 列出所有可用模板
 *   POST /api/documents/generate               → 填充模板，返回 docx 下载
 *   POST /api/documents/generate/pdf           → 填充后转 PDF（需 LibreOffice）
 *   POST /api/documents/templates/upload       → 上传自定义模板
 *   GET  /api/documents/templates/custom       → 列出用户上传的自定义模板
 *   DELETE /api/documents/templates/custom/{n} → 删除自定义模板
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    @Value("${aliyun.api.key:${qwen.api.key:}}")
    private String aliyunApiKey;

    @Value("${zhipu.api.key:}")
    private String zhipuApiKey;

    /** 自定义模板存储目录（运行时产生） */
    private static final Path CUSTOM_DIR = Paths.get("output", "custom-templates");

    // ── 内置模板注册表 ────────────────────────────────────────────────
    record TemplateInfo(String key, String label, String category,
                        String filename, String description,
                        List<String> extraFields) {}

    private static final List<TemplateInfo> BUILT_IN = List.of(
        new TemplateInfo("intro",       "自我介绍",   "文书",
            "intro.docx",
            "1/3/5分钟自我介绍模板，含中英文版本",
            List.of("target_school")),
        new TemplateInfo("statement",   "个人陈述",   "文书",
            "statement.docx",
            "1500字个人陈述，含学术背景/项目经历/研究规划",
            List.of("target_school","target_dir")),
        new TemplateInfo("email",       "套磁邮件",   "文书",
            "email.docx",
            "联系导师邮件模板，含研究兴趣和个人简介",
            List.of("prof_name","prof_school","target_school","target_dir")),
        new TemplateInfo("recommendation","专家推荐信","文书",
            "专家推荐信.docx",
            "导师推荐信模板，含科研成果和综合评价",
            List.of("recommender_name","recommender_title","recommender_school","target_school")),
        new TemplateInfo("resume-a",    "简历模板 A", "简历",
            "resume-a.docx",
            "简洁双栏式简历，适合经历丰富的同学",
            List.of("target_position")),
        new TemplateInfo("resume-b",    "简历模板 B", "简历",
            "resume-b.docx",
            "传统单栏式简历，内容详细",
            List.of("target_position")),
        new TemplateInfo("resume-c",    "简历模板 C", "简历",
            "resume-c.docx",
            "现代风格简历，含技能区块",
            List.of("target_position"))
    );

    // ── 每个模板的占位符替换规则 ──────────────────────────────────────
    // rule[0] = 模板段落合并后的精确原文（由Python从真实docx提取，非手写）
    // rule[1] = 替换目标（含 {var} 占位符）
    private static final Map<String, List<String[]>> PLACEHOLDER_RULES = new HashMap<>();
    static {
        PLACEHOLDER_RULES.put("email", List.of(
            new String[]{"我叫XXX，是xxx大学xxx专业xxx级本科生。我已经已经入围xx大学软件学院2021年优秀大学生夏令营，并且在xxx大学官网上大致了解了您的研究工作后，我对您xxx方向有着浓厚的研究兴趣。于是冒昧地与您联系，希望能得到您的指导。", "我叫{name}，是{school}{major}{grade}级本科生。在{prof_school}官网上了解了您在{target_dir}方向的研究工作后，我对贵组方向产生了浓厚兴趣，冒昧联系，希望能得到您的指导。"},
            new String[]{"学分绩点为xxx/5.0，综合测评排名3/228", "学分绩点为{gpa}/5.0，综合测评排名约前{rank_pct}%"},
            new String[]{"加入xxxx研究室学习", "加入本校{exp_short}相关课题组学习"},
            new String[]{"包括xxx、xxx、xxx等", "{exp_brief}"},
            new String[]{"获得了xxxx比赛国家级x等奖", "获得了{awards}"},
            new String[]{"学生：xxxx", "学生：{name}"},
            new String[]{"xxx年7月12日", "2026年6月21日"},
            new String[]{"尊敬的XXX老师：", "尊敬的{prof_name}老师："}
        ));
        PLACEHOLDER_RULES.put("statement", List.of(
            new String[]{"我是XXX，来自拥有“xxx”、“xxx“、“双一流学科建设高校”等美誉的XXXXX，就读于计算机科学与技术专业，下面我将从学术背景、项目经历、组织经历、研究生阶段学习计划四个方面来介绍我自己。", "我是{name}，来自{school}，就读于{major}专业，下面我将从学术背景、项目经历、组织经历、研究生阶段学习计划四个方面来介绍我自己。"},
            new String[]{"学分绩点为4.5/5.0，综合测评专业排名3/228", "学分绩点为{gpa}/5.0，综合测评专业排名前{rank_pct}%"},
            new String[]{"在校期间获得xxx奖学金", "在校期间获得{school}奖学金"},
            new String[]{"研究生阶段，我将在XXX方向从以下三个方面做出努力：", "研究生阶段，我将在{target_dir}方向从以下三个方面做出努力："},
            // 项目标题占位符（AI填充后走 extraRules 整段替换，这里提供静态兜底）
            new String[]{"XXXXXX ｜2020年5月 - 2020年6月 ", "{exp_title_1}"},
            new String[]{"XXXX｜2019年04月 - 至今", "{exp_title_2}"},
            new String[]{"XXXX｜2021年1月- 2021年2月", "{exp_title_3}"},
            new String[]{"XXXX-副主席", "{org_role_1}"},
            new String[]{"XXXX-主席", "{org_role_2}"}
        ));
        PLACEHOLDER_RULES.put("intro", List.of(
            new String[]{"尊敬的各位老师，上午好！我是XXX，就读于XXX大学软件工程专业，非常荣幸能够参加本次夏令营，下面我将从课程学习、科研竞赛、综合素质和未来规划四个方面介绍自己。", "尊敬的各位老师，上午好！我是{name}，就读于{school}{major}专业，非常荣幸能够参加本次夏令营，下面我将从课程学习、科研竞赛、综合素质和未来规划四个方面介绍自己。"},
            new String[]{"尊敬的各位老师，上午好！我是XXX，就读于XXX大学软件工程专业，下面我将从三个方面介绍自己：", "尊敬的各位老师，上午好！我是{name}，就读于{school}{major}专业，下面我将从三个方面介绍自己："},
            new String[]{"绩点为3.8，排名于专业前百分之三，连续两年获得学业奖学金", "绩点为{gpa}，排名于专业前{rank_pct}%，{awards}"},
            new String[]{"前五个学期绩点为3.8，绩点排名为专业前百分之三", "前五个学期绩点为{gpa}，绩点排名为专业前{rank_pct}%"},
            // 科研竞赛段（1分钟版）
            new String[]{"在科研竞赛方面，我积极参与多个科研项目，包括\"XXX\"微信小程序、\"XXX\"APP等，这些项目实践提升了我的计算机编程能力和应用能力。此外，我积极参加各类学科竞赛，曾获得\"计算机设计大赛\"省级二等奖等6个省级竞赛奖项。", "{exp_brief}"},
            // 实践段（1分钟版）
            new String[]{"在学习之余，我积极投身社会实践和志愿活动，不断提升自身团队协作能力以及综合素质。", "{practice_brief}"},
            // 目标院校（1分钟版）
            new String[]{"xxx大学是我一直向往的高校，非常希望并期待能够进入xx大学继续深造！谢谢各位老师！", "非常希望并期待能够进入{target_school}继续深造！谢谢各位老师！"},
            // 目标院校（3分钟版）
            new String[]{"xxx大学是我一直向往的高校，非常希望并期待能够进入xx大学继续深造！谢谢各位老师！", "非常希望并期待能够进入{target_school}继续深造！谢谢各位老师！"},
            new String[]{"非常希望并期待能够进入xx大学继续深造！谢谢各位老师！", "非常希望并期待能够进入{target_school}继续深造！谢谢各位老师！"},
            new String[]{"My name is XXX, and I am studying in Hebei Normal University, majoring in software engineering. Next, I will introduce myself from the following three aspects: my grades, academic prowess, and practical activities", "My name is {name}, and I am studying in {school_en}, majoring in {major}. Next, I will introduce myself from the following three aspects: my grades, academic prowess, and practical activities"},
            new String[]{"Good morning, professors! I am XXX, studying in Hebei Normal University, majoring in software engineering.", "Good morning, professors! I am {name}, studying in {school_en}, majoring in {major}."},
            new String[]{"my GPA is 3.8, ranking 9th out of 375", "my GPA is {gpa}, ranking top {rank_pct}%"},
            new String[]{"I hope and look forward to entering XX University to continue my study! That's all, thank you!", "I hope and look forward to entering {target_school_en} to continue my study! That's all, thank you!"},
            new String[]{"I hope and look forward to entering XX University to continue my study.", "I hope and look forward to entering {target_school_en} to continue my study."}
        ));
        PLACEHOLDER_RULES.put("recommendation", List.of(
            new String[]{"推荐人姓名：     XXX               推荐人职称：                  教授          ", "推荐人姓名：{recommender_name}               推荐人职称：{recommender_title}"},
            new String[]{"推荐人研究领域：  计算机应用          推荐人工作单位：       X XX大学         ", "推荐人研究领域：{recommender_dept}          推荐人工作单位：{recommender_school}"},
            new String[]{"考生姓名：       XXX               考生报考专业：     计算机科学与技术系       ", "考生姓名：{name}               考生报考专业：{major}"},
            new String[]{"您好，我是XXX大学XXX学院教授XXX，我非常荣幸推荐XXX同学参加贵院今年举办的暑期夏令营活动。", "您好，我是{recommender_school}{recommender_dept}教授{recommender_name}，我非常荣幸推荐{name}同学参加{target_school}今年举办的夏令营/直推活动。"},
            new String[]{"该同学在校成绩优异，学习态度端正，前五学期绩点一直保持专业前列", "{name}同学在校成绩优异，学习态度端正，绩点{gpa}，排名专业前{rank_pct}%"}
        ));
        PLACEHOLDER_RULES.put("resume-a", List.of(
            // 精确原文：—— 是全角破折号，空格数量以 docx 实际为准
            new String[]{"2013.09—2017.06           河北财经大学                        市场营销（本科）",
                         "{edu_start}—{edu_end}           {school}                        {major}（本科）"},
            new String[]{"应聘岗位：销售顾问        ", "应聘岗位：{target_position}        "},
            new String[]{"邮箱： XXXX@qq.com", "邮箱： {email}"}
        ));
        PLACEHOLDER_RULES.put("resume-b", List.of(
            new String[]{"求职意向：市场销售相关工作岗位", "保研意向：{target_school} · {target_dir}方向"},
            // 精确原文：末尾有空格
            new String[]{"2012.09—2016.6                  西南石油大学                  物流管理（本科） ",
                         "{edu_start}—{edu_end}                  {school}                  {major}（本科） "},
            new String[]{"电话：88888888888          邮箱：888@qq.com", "电话：          邮箱：{email}"}
        ));
        PLACEHOLDER_RULES.put("resume-c", List.of(
            new String[]{"意向岗位：XXXXX", "申请方向：{target_position}"},
            new String[]{"XXXX@XXXX.com", "{email}"},
            new String[]{"北京 北京市", "{city} {city}市"},
            // 精确原文：文库科技大学后跟  （不间断空格）而非普通空格
            new String[]{"2006.09-2010.07             文库科技大学                              软件工程/本科",
                         "{edu_start}-{edu_end}             {school}                              {major}/本科"},
            new String[]{"工作经验", "科研经历"}
        ));
    }

        // ── API ──────────────────────────────────────────────────────────

    /** 返回所有可用模板（内置 + 自定义） */
    @GetMapping("/templates")
    public ResponseEntity<Map<String,Object>> listTemplates() {
        List<Map<String,Object>> list = new ArrayList<>();
        for (TemplateInfo t : BUILT_IN) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("key",         t.key());
            m.put("label",       t.label());
            m.put("category",    t.category());
            m.put("description", t.description());
            m.put("extraFields", t.extraFields());
            m.put("custom",      false);
            list.add(m);
        }
        // 自定义模板
        try { Files.createDirectories(CUSTOM_DIR); } catch (IOException ignored) {}
        try (var stream = Files.list(CUSTOM_DIR)) {
            stream.filter(p -> p.toString().endsWith(".docx"))
                  .forEach(p -> {
                      Map<String,Object> m = new LinkedHashMap<>();
                      String fname = p.getFileName().toString();
                      m.put("key",         "custom::" + fname);
                      m.put("label",       fname.replace(".docx",""));
                      m.put("category",    "自定义");
                      m.put("description", "用户上传的自定义模板");
                      m.put("extraFields", List.of());
                      m.put("custom",      true);
                      list.add(m);
                  });
        } catch (IOException ignored) {}

        return ResponseEntity.ok(Map.of("templates", list, "count", list.size()));
    }

    /** 生成文档（返回填充后的 docx） */
    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestBody Map<String,Object> req) {
        String key = (String) req.getOrDefault("templateKey", "email");
        @SuppressWarnings("unchecked")
        Map<String,Object> rawVars2 = (Map<String,Object>) req.getOrDefault("vars", Map.of());
        Map<String,String> vars = new java.util.LinkedHashMap<>();
        rawVars2.forEach((k, v2) -> vars.put(k, v2 == null ? "" : v2.toString()));

        Map<String,String> v = new HashMap<>(vars);
        v.putIfAbsent("name",         "同学");
        v.putIfAbsent("school",       "本科院校");
        v.putIfAbsent("major",        "计算机科学与技术");
        v.putIfAbsent("grade",        "2021");
        v.putIfAbsent("gpa",          "3.8");
        v.putIfAbsent("rank_pct",     "10");
        v.putIfAbsent("target_school","目标院校");
        v.putIfAbsent("target_dir",   "人工智能");
        v.putIfAbsent("prof_name",    "老师");
        v.putIfAbsent("prof_school",  v.getOrDefault("target_school","目标院校"));
        v.putIfAbsent("email",        "student@stu.edu.cn");
        v.putIfAbsent("target_position", "保研学生 · CS方向");
        v.putIfAbsent("recommender_name",  "导师姓名");
        v.putIfAbsent("recommender_title", "教授");
        v.putIfAbsent("recommender_school", v.getOrDefault("school","本科院校"));
        v.putIfAbsent("recommender_dept",   "计算机学院");
        // exp_brief：从 experience 取前120字
        String expFull = v.getOrDefault("experience","参与相关科研项目");
        String expBriefDefault = expFull.length() > 120 ? expFull.substring(0,120)+"…" : expFull;
        v.putIfAbsent("exp_brief",    expBriefDefault);
        v.putIfAbsent("exp_short",    "AI");
        // statement 项目/组织占位符兜底（AI填充时会被 extraRules 整段覆盖）
        v.putIfAbsent("exp_title_1",  "科研/项目经历一");
        v.putIfAbsent("exp_title_2",  "科研/项目经历二");
        v.putIfAbsent("exp_title_3",  "科研/项目经历三");
        v.putIfAbsent("org_role_1",   "学生组织经历一");
        v.putIfAbsent("org_role_2",   "学生组织经历二");
        // practice_brief：从 practice 取，兜底为通用描述
        String practiceFull = v.getOrDefault("practice","");
        String practiceBriefDefault = practiceFull.isBlank() ? "积极投身社会实践和志愿活动，不断提升综合素质。"
            : (practiceFull.length() > 80 ? practiceFull.substring(0,80)+"…" : practiceFull);
        v.putIfAbsent("practice_brief", practiceBriefDefault);
        v.putIfAbsent("awards",       "相关竞赛奖项");
        v.putIfAbsent("target_school_en", "the target university");
        v.putIfAbsent("school_en",    v.getOrDefault("school","my university"));
        v.putIfAbsent("city",         "北京");
        v.putIfAbsent("edu_start",    "2021.09");
        v.putIfAbsent("edu_end",      "2025.06");

        try {
            byte[] docx = fillTemplate(key, v);
            String filename = key.replace("custom::", "") + "_filled.docx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" +
                java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8));
            return ResponseEntity.ok().headers(headers).body(docx);
        } catch (Exception e) {
            log.error("文档生成失败 [{}]: {}", key, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * AI 智能填充（同步版）：等待AI完成后直接返回docx。
     * POST /api/documents/generate/ai
     */
    @PostMapping("/generate/ai")
    public ResponseEntity<byte[]> generateWithAI(
            @RequestBody Map<String,Object> req,
            jakarta.servlet.http.HttpServletRequest httpReq) {

        String key = (String) req.getOrDefault("templateKey", "");
        @SuppressWarnings("unchecked")
        Map<String,Object> rawVars = (Map<String,Object>) req.getOrDefault("vars", Map.of());
        Map<String,String> vars = new java.util.LinkedHashMap<>();
        rawVars.forEach((k, v) -> vars.put(k, v == null ? "" : v.toString()));

        String aliyunKey = aliyunApiKey;
        String zhipuKey  = zhipuApiKey;
        if (aliyunKey.isBlank() && zhipuKey.isBlank())
            return ResponseEntity.status(503).build();

        String prompt = buildAIFillPrompt(key, vars);
        String aiJson = null;
        String[] aliyunModels = {
            "qwen3.6-flash","kimi-k2.6","qwen3.6-27b","deepseek-v4-pro",
            "qwen3.7-max","qwen3.7-plus-2026-05-26"
        };
        if (!aliyunKey.isBlank()) {
            for (String model : aliyunModels) {
                try {
                    aiJson = callAI(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                        aliyunKey, model, prompt, 90);
                    if (aiJson != null) break;
                } catch (Exception e) {
                    log.warn("[AI填充] {} 失败: {}", model, e.getMessage());
                }
            }
        }
        if (aiJson == null && !zhipuKey.isBlank()) {
            try {
                aiJson = callAI(
                    "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                    zhipuKey, "glm-4-flash", prompt, 90);
            } catch (Exception e) {
                log.warn("[AI填充] glm-4-flash 失败: {}", e.getMessage());
            }
        }
        if (aiJson == null) {
            log.error("[AI填充] 所有模型均失败");
            return ResponseEntity.status(503).build();
        }

        Map<String,String> aiVars = parseAIVars(aiJson);
        Map<String,String> merged = new java.util.HashMap<>(vars);
        aiVars.forEach((k, v) -> { if (v != null && !v.isBlank()) merged.put(k, v); });
        List<String[]> extraRules = buildExtraRules(key, aiVars);
        try {
            byte[] docx = fillTemplateWithExtra(key, merged, extraRules);
            String filename = key.replace("custom::","") + "_AI填充.docx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" +
                java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8));
            return ResponseEntity.ok().headers(headers).body(docx);
        } catch (Exception e) {
            log.error("[AI填充] 生成 docx 失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── 异步 AI 任务：后台生成，前端轮询，离开页面也不中断 ─────────────────

    private static final java.util.concurrent.ConcurrentHashMap<String, AiTask> AI_TASKS
        = new java.util.concurrent.ConcurrentHashMap<>();

    private static class AiTask {
        final String id;
        volatile String status   = "pending";
        volatile String progress = "任务已提交，等待处理…";
        volatile byte[] result;
        volatile String filename;
        volatile String error;
        final long createdAt = System.currentTimeMillis();
        AiTask(String id){ this.id = id; }
    }

    /**
     * 异步AI填充：立即返回taskId，后台线程执行，前端每3秒轮询状态。
     * POST /api/documents/generate/ai/async
     */
    @PostMapping("/generate/ai/async")
    public ResponseEntity<Map<String,String>> generateWithAIAsync(
            @RequestBody Map<String,Object> req) {

        long cutoff = System.currentTimeMillis() - 30 * 60 * 1000L;
        AI_TASKS.entrySet().removeIf(e ->
            e.getValue().createdAt < cutoff && !"running".equals(e.getValue().status));

        String taskId = java.util.UUID.randomUUID().toString().replace("-","").substring(0, 12);
        AiTask task = new AiTask(taskId);
        AI_TASKS.put(taskId, task);

        String key = (String) req.getOrDefault("templateKey", "");
        @SuppressWarnings("unchecked")
        Map<String,Object> rawVars = (Map<String,Object>) req.getOrDefault("vars", Map.of());
        Map<String,String> vars = new java.util.LinkedHashMap<>();
        rawVars.forEach((k, v) -> vars.put(k, v == null ? "" : v.toString()));
        final String aliyunKey = aliyunApiKey;
        final String zhipuKey  = zhipuApiKey;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                if (aliyunKey.isBlank() && zhipuKey.isBlank()) {
                    task.status = "error"; task.error = "未配置 AI API Key"; return;
                }
                task.status = "running"; task.progress = "正在分析个人信息，构建 prompt…";
                String prompt = buildAIFillPrompt(key, vars);
                task.progress = "正在调用 AI 模型（约 30-60 秒）…";

                String aiJson = null;
                String[] models = {"qwen3.6-flash","kimi-k2.6","qwen3.6-27b",
                                   "deepseek-v4-pro","qwen3.7-max","qwen3.7-plus-2026-05-26"};
                if (!aliyunKey.isBlank()) {
                    for (String m : models) {
                        try {
                            aiJson = callAI(
                                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                                aliyunKey, m, prompt, 90);
                            if (aiJson != null) { task.progress = "AI 已返回，正在填充模板…"; break; }
                        } catch (Exception ex) { log.warn("[AI异步] {} 失败: {}", m, ex.getMessage()); }
                    }
                }
                if (aiJson == null && !zhipuKey.isBlank()) {
                    try {
                        aiJson = callAI("https://open.bigmodel.cn/api/paas/v4/chat/completions",
                            zhipuKey, "glm-4-flash", prompt, 90);
                        if (aiJson != null) task.progress = "AI 已返回，正在填充模板…";
                    } catch (Exception ex) { log.warn("[AI异步] glm-4-flash 失败: {}", ex.getMessage()); }
                }
                if (aiJson == null) {
                    task.status = "error"; task.error = "所有 AI 模型均失败"; return;
                }
                Map<String,String> aiVars = parseAIVars(aiJson);
                Map<String,String> merged = new java.util.HashMap<>(vars);
                aiVars.forEach((k, v) -> { if (v != null && !v.isBlank()) merged.put(k, v); });
                List<String[]> extra = buildExtraRules(key, aiVars);
                task.result = fillTemplateWithExtra(key, merged, extra);
                task.filename = key.replace("custom::","") + "_AI填充.docx";
                task.status = "done"; task.progress = "生成完成，可以下载了！";
            } catch (Exception ex) {
                log.error("[AI异步] 失败: {}", ex.getMessage(), ex);
                task.status = "error"; task.error = "生成失败：" + ex.getMessage();
            }
        });
        return ResponseEntity.ok(Map.of("taskId", taskId, "status", "pending"));
    }

    /** 查询异步任务状态 GET /api/documents/task/{taskId}/status */
    @GetMapping("/task/{taskId}/status")
    public ResponseEntity<Map<String,Object>> getAiTaskStatus(@PathVariable String taskId) {
        AiTask task = AI_TASKS.get(taskId);
        if (task == null) return ResponseEntity.notFound().build();
        Map<String,Object> resp = new java.util.LinkedHashMap<>();
        resp.put("taskId", task.id); resp.put("status", task.status);
        resp.put("progress", task.progress);
        if (task.error != null) resp.put("error", task.error);
        return ResponseEntity.ok(resp);
    }

    /** 下载异步任务结果 GET /api/documents/task/{taskId}/download */
    @GetMapping("/task/{taskId}/download")
    public ResponseEntity<byte[]> downloadAiTask(@PathVariable String taskId) {
        AiTask task = AI_TASKS.get(taskId);
        if (task == null || !"done".equals(task.status) || task.result == null)
            return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        try {
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" +
                java.net.URLEncoder.encode(task.filename, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=AI填充.docx");
        }
        return ResponseEntity.ok().headers(headers).body(task.result);
    }

    /** 根据模板类型和个人信息构建 AI 填充 prompt */
    private static String buildAIFillPrompt(String key, Map<String,String> vars) {
        String name       = vars.getOrDefault("name", "");
        String school     = vars.getOrDefault("school", "");
        String major      = vars.getOrDefault("major", "");
        String gpa        = vars.getOrDefault("gpa", "");
        String rankPct    = vars.getOrDefault("rank_pct", "");
        String email      = vars.getOrDefault("email", "");
        String experience = vars.getOrDefault("experience", "");
        if (experience.isBlank()) experience = vars.getOrDefault("exp_brief", "");
        String awards     = vars.getOrDefault("awards", "");
        String targetSchool = vars.getOrDefault("target_school","");
        String targetDir  = vars.getOrDefault("target_dir","");
        String targetPos  = vars.getOrDefault("target_position","");

        boolean isResume = key.startsWith("resume-");
        boolean isIntro  = "intro".equals(key);
        boolean isEmail  = "email".equals(key);

        String templateDesc = switch (key) {
            case "resume-a","resume-b","resume-c" -> "保研简历";
            case "intro"       -> "保研自我介绍";
            case "statement"   -> "保研个人陈述";
            case "email"       -> "套磁邮件";
            case "recommendation" -> "专家推荐信";
            default            -> "保研文书";
        };

        String fieldSpec;
        if (isResume) {
            fieldSpec = "输出 JSON，字段如下（保研视角，禁止出现求职/应聘/就业/工作岗位等词）：\n"
                + "{\n"
                + "  \"skills_text\": \"编程技能：2-4条，每条约30字。Python/Java等语言+框架/工具+科研使用场景。\",\n"
                + "  \"certs_text\": \"科研成果与证书：2-3条。发表/在投论文、英语等级（CET-6分数）、竞赛获奖等。\",\n"
                + "  \"self_eval_text\": \"自我评价：120-160字。保研导向：科研热情、学术能力、团队协作、未来研究方向。\",\n"
                + "  \"exp_text\": \"科研与项目经历，每条占独立一行（用\\n换行符分隔，禁止用\\或其他符号分隔）。格式：时间段 | 项目/论文名 | 角色 | 核心贡献。不加列表符号，不写实习工作。\",\n"
                + "  \"edu_text\": \"教育背景行，格式：起止时间 学校 专业/本科\"\n"
                + "}";
        } else if (isIntro) {
            fieldSpec = "输出 JSON：\n"
                + "{\n"
                + "  \"intro_cn\": \"中文保研自我介绍：3-5分钟，约400-600字。结构：基本情况（姓名/院校/专业/GPA/排名）→科研经历（重点，逐项列出论文/项目/竞赛，必须用真实信息）→获奖荣誉→实践经历（如有）→申请意向→结语。禁止出现求职/应聘，保研视角。\",\n"
                + "  \"intro_en\": \"英文保研自我介绍：约200词。Good morning/afternoon, professors...\",\n"
                + "  \"exp_brief\": \"科研经历摘要：1-2句话，约80字，用于替换自我介绍中的科研段落占位符。必须基于真实科研信息，突出论文/项目/竞赛的具体成果。\",\n"
                + "  \"practice_brief\": \"实践经历摘要：1句话，约40字，用于替换实践段落占位符。若无实践经历则填\\\"积极投身社会实践和志愿活动，不断提升综合素质。\\\"\",\n"
                + "  \"self_eval_text\": \"书面自我评价：120-160字保研版\"\n"
                + "}";
        } else if (isEmail) {
            fieldSpec = "输出 JSON：\n"
                + "{\n"
                + "  \"email_body\": \"套磁邮件正文：约300字。自我介绍→科研背景→感兴趣方向→申请意向→联系方式。\",\n"
                + "  \"self_eval_text\": \"简短科研背景描述：3句话\"\n"
                + "}";
        } else {
            boolean isStmt = "statement".equals(key);
            boolean isRec  = "recommendation".equals(key);
            if (isStmt) {
                fieldSpec = "输出 JSON（保研个人陈述用，面向研究生导师）：\n"
                    + "{\n"
                    + "  \"exp_text\": \"科研/项目经历详述，每条一行（\\n分隔）。格式：时间 | 项目名/论文名 | 角色 | 核心贡献+成果。必须完整，不得省略。\",\n"
                    + "  \"exp_title_1\": \"第一条项目/论文的标题行，格式：项目名 ｜ 起止时间，如：MlyPredCSED研究 ｜ 2024年3月 - 2025年6月\",\n"
                    + "  \"exp_title_2\": \"第二条项目/论文标题行，格式同上，无则填(无)\",\n"
                    + "  \"exp_title_3\": \"第三条项目/论文标题行，格式同上，无则填(无)\",\n"
                    + "  \"org_role_1\": \"学生组织/社会实践经历一，格式：组织名-职务，无则填(无)\",\n"
                    + "  \"org_role_2\": \"学生组织/社会实践经历二，格式同上，无则填(无)\",\n"
                    + "  \"self_eval_text\": \"研究生阶段规划：150-200字，结合意向研究方向\\n\\n说明：不要重复科研经历，聚焦未来规划。\"\n"
                    + "}";
            } else if (isRec) {
                fieldSpec = "输出 JSON（专家推荐信用，以推荐导师第一人称写作）：\n"
                    + "{\n"
                    + "  \"rec_acquaintance\": \"认识经过段：约80字。说明推荐人与学生如何认识/合作，时间/场合/项目名称，基于学生真实科研经历。\",\n"
                    + "  \"rec_research\": \"科研/工程能力段：约200字。具体科研成果、论文、大创项目、竞赛，必须用真实信息。\",\n"
                    + "  \"rec_awards\": \"获奖与综合能力段：约100字。基于真实获奖信息。\",\n"
                    + "  \"self_eval_text\": \"综合推荐语：约80字。总结推荐，保研视角。\"\n"
                    + "}";
            } else {
                fieldSpec = "输出 JSON：\n"
                    + "{\n"
                    + "  \"self_eval_text\": \"保研个人陈述核心段落：300-500字。科研经历、学术成果、研究规划。\",\n"
                    + "  \"exp_text\": \"科研经历详述：完整描述所有科研/论文/竞赛经历\"\n"
                    + "}";
            }
        }

        return "你是一名经验丰富的保研辅导老师。请根据以下学生的真实信息，生成【"
            + templateDesc + "】所需的文字内容。\n\n"
            + "## 重要背景\n"
            + "这是保研申请材料，面向研究生招生导师：\n"
            + "- 突出科研经历、学术论文、科研项目（核心竞争力）\n"
            + "- 不强调实习/工作经历\n"
            + "- 禁止出现【求职】【应聘】【就业】【工作岗位】等词\n"
            + "- 技能侧重编程/算法/科研工具\n\n"
            + "## 学生真实信息\n"
            + "- 姓名：" + name + "\n"
            + "- 本科院校：" + school + "  专业：" + major + "\n"
            + "- GPA：" + gpa + "  排名：" + rankPct + "\n"
            + "- 邮箱：" + email + "\n"
            + "- 目标院校：" + targetSchool + "  意向研究方向：" + targetDir + "\n"
            + "- 申请方向描述：" + targetPos + "\n\n"
            + "## 科研经历与获奖（完整，请仔细阅读）\n"
            + (experience.isBlank() ? "（用户未填写，请根据专业和方向合理推断）" : experience) + "\n\n"
            + "## 获奖荣誉\n"
            + (awards.isBlank() ? "（未提供）" : awards) + "\n\n"
            + "## 输出要求\n"
            + fieldSpec + "\n\n"
            + "严格要求：\n"
            + "1. 只返回合法 JSON，不含任何 markdown 标记或额外文字\n"
            + "2. 所有内容严格基于上述真实信息，禁止编造\n"
            + "3. 保研视角，不得出现【求职】【应聘】【就业】等词\n"
            + "4. 科研经历要具体：论文写出题目方向、大创写省级等级和角色\n"
            + "5. exp_text 必须用 \\n 换行符分隔每条，禁止用 \\ 或其他符号，格式：时间 | 项目名 | 角色 | 贡献\n";
    }

    /** 调 AI 接口，返回原始响应文本（含 JSON） */
    private String callAI(String endpoint, String key, String model,
                           String prompt, int timeoutSec) throws Exception {
        String body = String.format(
            "{\"model\":\"%s\",\"max_tokens\":3000,\"stream\":false," +
            "\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}",
            model,
            prompt.replace("\\","\\\\").replace("\"","\\\"")
                  .replace("\n","\\n").replace("\r",""));

        java.net.http.HttpClient hc = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15)).build();
        java.net.http.HttpResponse<String> resp = hc.send(
            java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(endpoint))
                .timeout(java.time.Duration.ofSeconds(timeoutSec))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                    body, java.nio.charset.StandardCharsets.UTF_8))
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            log.warn("[AI填充] {} HTTP {}: {}", model, resp.statusCode(),
                resp.body().substring(0, Math.min(200, resp.body().length())));
            return null;
        }
        String rb = resp.body();
        int ci = rb.indexOf("\"content\"");
        if (ci < 0) return null;
        int sq = rb.indexOf('"', ci + 10);
        int eq = rb.lastIndexOf('"', rb.indexOf("\"role\"", ci) - 1);
        if (sq < 0 || eq <= sq) return null;
        return rb.substring(sq + 1, eq)
                 .replace("\\n","\n").replace("\\\"","\"").replace("\\\\","\\");
    }

    /** 解析 AI 返回的 JSON，映射到模板占位符 vars key */
    private static Map<String,String> parseAIVars(String raw) {
        String json = raw.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
        int s = json.indexOf('{'), e = json.lastIndexOf('}');
        if (s < 0 || e < 0) {
            log.warn("[AI填充] 未找到JSON对象, raw前200: {}", raw.substring(0, Math.min(200, raw.length())));
            return new java.util.LinkedHashMap<>();
        }
        json = json.substring(s, e + 1);
        Map<String,String> parsed = new java.util.LinkedHashMap<>();

        int i = 0;
        int len = json.length();
        while (i < len) {
            int ks = json.indexOf('"', i);
            if (ks < 0) break;
            int ke = json.indexOf('"', ks + 1);
            if (ke < 0) break;
            String key = json.substring(ks + 1, ke);
            i = ke + 1;
            while (i < len && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= len || json.charAt(i) != ':') continue;
            i++;
            while (i < len && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= len || json.charAt(i) != '"') continue;
            i++;
            StringBuilder val = new StringBuilder();
            while (i < len) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < len) {
                    char next = json.charAt(i + 1);
                    if (next == '"')       { val.append('"');  i += 2; }
                    else if (next == '\\') { val.append('\\'); i += 2; }
                    else if (next == 'n')  { val.append('\n'); i += 2; }
                    else if (next == 't')  { val.append('\t'); i += 2; }
                    else if (next == 'r')  { val.append('\r'); i += 2; }
                    else                   { val.append(c); i++; }
                } else if (c == '"') {
                    i++; break;
                } else {
                    val.append(c); i++;
                }
            }
            if (!key.isEmpty()) parsed.put(key, val.toString().trim());
        }
        log.info("[AI填充] 解析到字段: {}", parsed.keySet());
        return parsed;
    }

    @PostMapping("/generate/pdf")
    public ResponseEntity<byte[]> generatePdf(@RequestBody Map<String,Object> req) {
        try {
            ResponseEntity<byte[]> docxResp = generate(req);
            if (!docxResp.getStatusCode().is2xxSuccessful() || docxResp.getBody() == null)
                return docxResp;

            Path tmp = Files.createTempFile("baoyan_", ".docx");
            Files.write(tmp, docxResp.getBody());

            Path pdfDir = tmp.getParent();
            ProcessBuilder pb = new ProcessBuilder(
                "libreoffice", "--headless", "--convert-to", "pdf",
                "--outdir", pdfDir.toString(), tmp.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

            if (ok && p.exitValue() == 0) {
                Path pdf = pdfDir.resolve(tmp.getFileName().toString().replace(".docx",".pdf"));
                if (Files.exists(pdf)) {
                    byte[] pdfBytes = Files.readAllBytes(pdf);
                    String k = (String) req.getOrDefault("templateKey","doc");
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.set(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" +
                        java.net.URLEncoder.encode(k + "_filled.pdf", StandardCharsets.UTF_8));
                    Files.deleteIfExists(tmp);
                    Files.deleteIfExists(pdf);
                    return ResponseEntity.ok().headers(headers).body(pdfBytes);
                }
            }
            Files.deleteIfExists(tmp);
            return ResponseEntity.status(503).build();
        } catch (Exception e) {
            log.warn("PDF 转换失败（可能未安装 LibreOffice）: {}", e.getMessage());
            return ResponseEntity.status(503).build();
        }
    }

    /** 上传自定义模板 */
    @PostMapping("/templates/upload")
    public ResponseEntity<Map<String,Object>> uploadTemplate(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || !Objects.requireNonNull(file.getOriginalFilename()).endsWith(".docx"))
            return ResponseEntity.badRequest().body(Map.of("error","请上传 .docx 文件"));
        try {
            Files.createDirectories(CUSTOM_DIR);
            String safeName = file.getOriginalFilename()
                .replaceAll("[^\\w\\u4e00-\\u9fff\\-._]","_");
            Path dest = CUSTOM_DIR.resolve(safeName);
            file.transferTo(dest.toFile());
            log.info("自定义模板上传: {}", safeName);
            return ResponseEntity.ok(Map.of(
                "message","上传成功",
                "key","custom::" + safeName,
                "filename", safeName));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /** 删除自定义模板 */
    @DeleteMapping("/templates/custom/{filename}")
    public ResponseEntity<Void> deleteCustomTemplate(@PathVariable String filename) {
        try {
            Path p = CUSTOM_DIR.resolve(filename);
            if (Files.exists(p) && p.startsWith(CUSTOM_DIR)) Files.delete(p);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── 核心：填充 docx 模板 ──────────────────────────────────────────

    /** 根据 AI 生成内容和模板类型，构建额外段落替换规则 */
    private static List<String[]> buildExtraRules(String key, Map<String,String> aiVars) {
        List<String[]> rules = new java.util.ArrayList<>();
        String skillsTxt  = aiVars.getOrDefault("skills_text","");
        String certsTxt   = aiVars.getOrDefault("certs_text","");
        String evalTxt    = aiVars.getOrDefault("self_eval_text", aiVars.getOrDefault("self_eval",""));
        String expTxt     = aiVars.getOrDefault("exp_text","");
        // 清理 expTxt：AI 有时用反斜杠 \ 作条目分隔符，统一转为换行；去掉空行
        if (!expTxt.isBlank()) {
            expTxt = expTxt.replace("\\", "\n")   // \ → 换行
                           .replace("\r\n", "\n")
                           .replace("\r", "\n");
            // 去掉连续空行，trim 每行
            String[] rawLines = expTxt.split("\n");
            java.util.List<String> cleanLines = new java.util.ArrayList<>();
            for (String l : rawLines) {
                String t = l.trim();
                if (!t.isEmpty()) cleanLines.add(t);
            }
            expTxt = String.join("\n", cleanLines);
        }

        String[] skillLines = skillsTxt.isBlank() ? new String[0] : skillsTxt.split("[\r\n]+");
        String[] certLines  = certsTxt.isBlank()  ? new String[0] : certsTxt.split("[\r\n]+");

        if ("resume-c".equals(key)) {
            if (skillLines.length > 0) rules.add(new String[]{"熟练使用C#语言、asp.net、Winforms、Web Service、Ajax、xml等技术，对VB.NET有一定的了解；", skillLines[0].trim()});
            if (skillLines.length > 1) rules.add(new String[]{"熟练操作SQL Server存储过程、视图等和Access数据库；", skillLines[1].trim()});
            if (skillLines.length > 2) rules.add(new String[]{"熟练 Visual Studio 2005 、VSS等开发工具；", skillLines[2].trim()});
            if (skillLines.length > 3) rules.add(new String[]{"能熟练结合各项技术开发B/S、C/S结构多层结构的开发项目。", skillLines[3].trim()});
            if (certLines.length > 0) rules.add(new String[]{"熟悉WAN/LAN基础知识，能够进行复杂和突发性网络故障排除 ", certLines[0].trim()});
            if (certLines.length > 1) rules.add(new String[]{"熟悉PC机组装与维修，能够诊断电脑的硬件故障 ", certLines[1].trim()});
            if (certLines.length > 2) rules.add(new String[]{"驾驶证（C1）", certLines[2].trim()});
            if (!evalTxt.isBlank()) rules.add(new String[]{"我热爱自己的专业，也希望找到和自己专业对口的工作，我会\u201c爱一行干一行\u201d，但我更相信\u201c干一行爱一行\u201d，阿基米德曾说\u201c给我一个支点，我能撬起地球\u201d，我想说\u201c给我一个机会，你将收获一个希望\u201d，相信您的选择和我的努力会为我们带来双赢，真诚地希望您能够为我提供一个施展才华的平台，更希望我的到来能带来您想到的价值！", evalTxt});
            rules.add(new String[]{"\u5de5\u4f5c\u7ecf\u9a8c", "\u79d1\u7814\u7ecf\u5386"});
            rules.add(new String[]{"\u53c2\u4e0ejava\u8f6f\u4ef6\u5f00\u53d1\u3001\u6d4b\u8bd5\u7b49\u8fc7\u7a0b\uff0c\u8d1f\u8d23\u5de5\u7a0b\u4e2d\u540e\u53f0\u63a5\u53e3\u7684\u5b9e\u73b0\uff1b", ""});
            rules.add(new String[]{"解决工程中的关键问题和技术难题以及Code Review和Code Scan。\u00a0", ""});
            rules.add(new String[]{"2011.08-2013.02             \u4f18\u7c73\u4fe1\u606f\u79d1\u6280\uff08\u4e0a\u6d77\uff09\u6709\u9650\u516c\u53f8               java\u5f00\u53d1\u7a0b\u5e8f\u5458", ""});
            rules.add(new String[]{"\u8d1f\u8d23\u4fee\u6539\u540e\u53f0\u670d\u52a1\u4e2d\u4e00\u4e9b\u5c31\u653f\u7b56\u7684\u4e00\u4e9b\u8ddf\u5931\u4e1a\u76f8\u5173\u7684\u6807\u51c6\uff1b", ""});
            rules.add(new String[]{"\u8f6c\u79fb\u6a21\u5757\u4e2d\u6839\u636e\u516c\u79ef\u91d1\u4e2d\u5fc3\u4e1a\u52a1\u7684\u9700\u6c42\u5f00\u53d1\u3010\u56ed\u533a\u8f6c\u56ed\u533a\u3011\u3001\u3010\u5e73\u53f0\u8f6c\u79fb\u3011\u6a21\u5757\uff1b", ""});
            rules.add(new String[]{"\u8d1f\u8d23\u754c\u9762\u7684\u8bbe\u8ba1\u4ee5\u53ca\u540e\u53f0\u670d\u52a1\u7684\u8bbe\u8ba1\u7f16\u5199", ""});
            if (!expTxt.isBlank()) {
                rules.add(new String[]{"2014.08-2015.07             \u4f18\u7c73\u4fe1\u606f\u79d1\u6280 \uff08\u5317\u4eac\uff09\u6709\u9650\u516c\u53f8              java\u5f00\u53d1\u7a0b\u5e8f\u5458",
                    expTxt.trim()});
            }
            // 清空主修课程模板行（设计类模板与计算机专业无关）
            rules.add(new String[]{"主修课程：C语言，操作系统，数据库原理，软件工程计算机网络，计算机体系结构，数据结构，操作系统。", ""});
        } else if ("intro".equals(key)) {
            // intro：exp_brief 和 practice_brief 通过 merged map 走占位符替换，无需 extraRules
        } else if ("statement".equals(key)) {
            // statement：AI 返回的 exp_title_1/2/3 和 org_role_1/2 已进入 merged，走占位符替换
            // 同时用 exp_text 整段覆盖项目描述段（第一个项目描述段）
            String expTxtStmt = aiVars.getOrDefault("exp_text", "");
            if (!expTxtStmt.isBlank()) {
                // 覆盖第一个项目描述段（该项目针对XXXX...）
                rules.add(new String[]{"该项目针对XXXX的问题，提供XXXXX服务。我负责项目的组织策划及后端开发，采用基于Java的Spring Boot框架实现前后端互联，mysql及redis数据存储及智能云服务。针对高并发访问的问题，设计实现集群部署结合nginx实现负载均衡提高系统稳定性；针对数据安全问题，利用AOP面向切面编程方式实现系统防刷。", expTxtStmt.trim()});
                // 清空其余项目描述段（用空字符串）
                rules.add(new String[]{"项目获得XXXX一等奖（最高奖），与10余所高校进行合作，最高日活10万。相关成果转化为一项发明专利（一作，已受理）及一项软件著作权（一作，已授权）。", ""});
                rules.add(new String[]{"该项目致力于研发一款涵盖各类校园服务的xxxx系统，提高学生信息查询及事务办理的效率。", ""});
                rules.add(new String[]{"我负责后端版本研发、功能迭代工作，采用基于Java的SpringBoot框架及HttpClient进行系统间数据的交互并与前端互联，mysql及redis数据存储。针对周期性业务，设计实现多线程定时任务，完成数据定时爬取及分析、公众号提醒消息定时发送。", ""});
                rules.add(new String[]{"该项目对XXX积累的用户数据进行整理分析，提供良好的可视化界面展示用户学期数据情况。", ""});
                rules.add(new String[]{"我负责项目的组织策划及数据处理，采用基于Java语言的爬虫完成数据收集，mysql及文件完成数据存储。针对有效字段进行抽取，通过数据分析将各类数据结构化。针对用户及原始数据量大问题，采用多线程编程方式进行逻辑处理完成数据准备，并封装成API提供前端进行数据渲染完成可视化展示。", ""});
                rules.add(new String[]{"项目于2021年2月6日通过学校官微推广至全校并稳定运行，DAU破万。", ""});
            }
            // self_eval_text 用于覆盖研究生阶段规划的某个段落（通过 merged map 处理）
        } else if ("recommendation".equals(key)) {
            String recAcq  = aiVars.getOrDefault("rec_acquaintance", "");
            String recRes  = aiVars.getOrDefault("rec_research", "");
            String recAwd  = aiVars.getOrDefault("rec_awards", "");
            if (!recAcq.isBlank())
                rules.add(new String[]{"XXX同学自大一起就在我所在单位xxxxxx的智慧校园研究中心参与学校智慧校园项目研发工作，并且指导其完成大学生创新训练计划项目，接触较多，了解基本情况。", recAcq});
            if (!recRes.isBlank())
                rules.add(new String[]{"该同学具有较高的工程能力。2020年5月，XXX同学参与了大学生创新训练计划项目——学生运动打卡多端服务高可用系统开发，并作为项目第一主持人。经过一年的系统训练，最终成功开发出了较为成熟、稳定的应用，该项目现已经结题，该同学学习掌握了服务器分布式部署，SpringBoot开发框架，Mysql数据库等技术，具备较强的工程研发能力。除此之外，该同学还参与了学校xxxx、xxxx等应用的开发与维护，总使用人数超3万，对学校信息化建设产生了良好的影响。", recRes});
            if (!recAwd.isBlank())
                rules.add(new String[]{"此外，该同学积极参加各类比赛，作为核心成员获得第十二届“挑战杯”中国大学生创业计划竞赛铜奖; 获得2020年中国高校计算机大赛一等奖；获得校第二十二届“创新杯”大学生课外学术科技作品竞赛特等奖等8个校级奖项；作为第一作者取得计算机软件著作权两项，受理发明专利一项。", recAwd});
            // 清空英语/工作能力模板段
            rules.add(new String[]{"该同学具有良好的英语水平。XXX同学大一即顺利通过英语四六级，英文阅读、写作能力较强，同时该同学在课余时间经常阅读一些英文文献，我认为这为以后研究生阶段阅读文献和撰写英文论文打下了较为良好的基础。", ""});
            rules.add(new String[]{"该同学具有较强的工作能力。XXX同学担任第二十九届xxxxxxxx副主席兼青柚工作室主席，对待学生工作认真负责。XXX同学给我留下较深印象的是在项目开发和社团工作中，能够合理的分配任务，高效的完成工作。在校期间该同学获得2021年江苏省省级优秀学生干部、校优秀共青团干部等荣誉。", ""});
        } else if ("resume-a".equals(key)) {
            if (!skillsTxt.isBlank()) rules.add(new String[]{"熟练使用office办公软件", skillsTxt});
            if (!evalTxt.isBlank()) rules.add(new String[]{"具备销售、市场推广等相关实践经验，有较强的领导力、沟通能力、组织能力和团队精神", evalTxt});
            if (!expTxt.isBlank()) rules.add(new String[]{"负责公司自媒体（如微博、卫星公众）的信息发布及维护；", expTxt});
        } else if ("resume-b".equals(key)) {
            if (!skillsTxt.isBlank()) rules.add(new String[]{"熟悉Web、iOS和Android开发，精通数据库、C++及Java", skillsTxt});
            if (!evalTxt.isBlank()) rules.add(new String[]{"工作积极认真，细心负责，熟练运用办公自动化软件，善于在工作中提出问题、发现问题、解决问题，有较强的分析能力；勤奋好学，踏实肯干，动手能力强，认真负责，有很强的社会责任感；坚毅不拔，吃苦耐劳，喜欢和勇于迎接新挑战。", evalTxt});
        }
        log.info("[AI填充] 生成额外规则 {} 条 for {}", rules.size(), key);
        return rules;
    }

    /** fillTemplate 附加额外段落替换规则（用于 AI 填充覆盖模板固定文字） */
    private byte[] fillTemplateWithExtra(String key, Map<String,String> vars, List<String[]> extraRules) throws IOException {
        byte[] templateBytes = loadTemplateBytes(key);
        List<String[]> rules = new java.util.ArrayList<>(
            PLACEHOLDER_RULES.getOrDefault(key.replace("custom::",""), List.of()));
        rules.addAll(0, extraRules);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream zin  = new ZipInputStream(new ByteArrayInputStream(templateBytes));
             ZipOutputStream zout = new ZipOutputStream(out)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                byte[] data = zin.readAllBytes();
                String name = entry.getName();
                if (name.equals("word/document.xml")) {
                    String xml = new String(data, StandardCharsets.UTF_8);
                    xml = replaceInParagraphs(xml, rules, vars);
                    for (Map.Entry<String,String> ev : vars.entrySet()) {
                        String val = ev.getValue() == null ? "" : ev.getValue();
                        xml = xml.replace("{" + ev.getKey() + "}", xmlEscape(val));
                    }
                    data = xml.getBytes(StandardCharsets.UTF_8);
                }
                ZipEntry outEntry = new ZipEntry(name);
                zout.putNextEntry(outEntry);
                zout.write(data);
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private byte[] fillTemplate(String key, Map<String,String> vars) throws IOException {
        byte[] templateBytes = loadTemplateBytes(key);
        List<String[]> rules = PLACEHOLDER_RULES.getOrDefault(
            key.replace("custom::",""), List.of());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipInputStream zin  = new ZipInputStream(new ByteArrayInputStream(templateBytes));
             ZipOutputStream zout = new ZipOutputStream(out)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                byte[] data = zin.readAllBytes();
                String name = entry.getName();
                if (name.equals("word/document.xml")) {
                    String xml = new String(data, StandardCharsets.UTF_8);
                    xml = replaceInParagraphs(xml, rules, vars);
                    for (Map.Entry<String,String> e : vars.entrySet()) {
                        String val = e.getValue() == null ? "" : e.getValue();
                        xml = xml.replace("{" + e.getKey() + "}", xmlEscape(val));
                    }
                    data = xml.getBytes(StandardCharsets.UTF_8);
                }
                ZipEntry newEntry = new ZipEntry(name);
                zout.putNextEntry(newEntry);
                zout.write(data);
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /**
     * 段落级文本替换：解决 Word 把连续文字拆到多个 run 的问题。
     */
    private static String replaceInParagraphs(String xml, List<String[]> rules,
                                              Map<String,String> vars) {
        java.util.regex.Pattern pPat = java.util.regex.Pattern.compile(
            "<w:p[\\s>][^§]*?</w:p>", java.util.regex.Pattern.DOTALL);
        java.util.regex.Pattern tPat = java.util.regex.Pattern.compile(
            "<w:t\\b[^>]*>(.*?)</w:t>", java.util.regex.Pattern.DOTALL);

        java.util.regex.Matcher pm = pPat.matcher(xml);
        StringBuffer sb = new StringBuffer();

        while (pm.find()) {
            String para = pm.group();
            java.util.regex.Matcher tm = tPat.matcher(para);
            StringBuilder full = new StringBuilder();
            java.util.List<int[]> spans = new java.util.ArrayList<>();
            while (tm.find()) {
                full.append(unescapeXml(tm.group(1)));
                spans.add(new int[]{tm.start(1), tm.end(1)});
            }
            if (spans.isEmpty()) {
                pm.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(para));
                continue;
            }

            String merged  = full.toString();
            String replaced = merged;

            for (String[] rule : rules) {
                String oldText = rule[0];
                String newText = resolve(rule[1], vars);
                if (replaced.contains(oldText)) {
                    replaced = replaced.replace(oldText, newText);
                }
            }
            for (Map.Entry<String,String> e : vars.entrySet()) {
                String val = e.getValue() == null ? "" : e.getValue();
                replaced = replaced.replace("{" + e.getKey() + "}", val);
            }

            if (!replaced.equals(merged)) {
                if (replaced.contains("\n")) {
                    // ── 多行文本：每行生成一个独立 <w:p>，复用原段落格式 ──
                    String pPrXml = "";
                    java.util.regex.Matcher pPrM = java.util.regex.Pattern
                        .compile("<w:pPr>.*?</w:pPr>", java.util.regex.Pattern.DOTALL)
                        .matcher(para);
                    if (pPrM.find()) pPrXml = pPrM.group();
                    // 提取第一个 <w:r> 的 <w:rPr>（用于保持字体格式）
                    String rPrXml = "";
                    java.util.regex.Matcher rPrM = java.util.regex.Pattern
                        .compile("<w:rPr>.*?</w:rPr>", java.util.regex.Pattern.DOTALL)
                        .matcher(para);
                    if (rPrM.find()) rPrXml = rPrM.group();
                    String[] lines = replaced.split("\n", -1);
                    StringBuilder multiPara = new StringBuilder();
                    for (String line : lines) {
                        String lineText = line.trim();
                        multiPara.append("<w:p>");
                        if (!pPrXml.isEmpty()) multiPara.append(pPrXml);
                        if (!lineText.isEmpty()) {
                            multiPara.append("<w:r>");
                            if (!rPrXml.isEmpty()) multiPara.append(rPrXml);
                            multiPara.append("<w:t xml:space=\"preserve\">")
                                     .append(xmlEscape(lineText))
                                     .append("</w:t></w:r>");
                        }
                        multiPara.append("</w:p>");
                    }
                    para = multiPara.toString();
                } else {
                    StringBuilder newPara = new StringBuilder();
                    int last = 0;
                    for (int i = 0; i < spans.size(); i++) {
                        int[] sp = spans.get(i);
                        newPara.append(para, last, sp[0]);
                        if (i == 0) newPara.append(xmlEscape(replaced));
                        last = sp[1];
                    }
                    newPara.append(para.substring(last));
                    para = newPara.toString();
                }
            }
            pm.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(para));
        }
        pm.appendTail(sb);
        return sb.toString();
    }

    private static String unescapeXml(String s) {
        return s.replace("&amp;","&").replace("&lt;","<")
                .replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'");
    }

    private static String resolve(String template, Map<String,String> vars) {
        String s = template;
        for (Map.Entry<String,String> e : vars.entrySet())
            s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        return s;
    }

    private static String xmlEscape(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private byte[] loadTemplateBytes(String key) throws IOException {
        if (key.startsWith("custom::")) {
            String filename = key.substring("custom::".length());
            return Files.readAllBytes(CUSTOM_DIR.resolve(filename));
        }
        String filename = BUILT_IN.stream()
            .filter(t -> t.key().equals(key))
            .map(TemplateInfo::filename)
            .findFirst()
            .orElseThrow(() -> new FileNotFoundException("未知模板: " + key));
        InputStream is = getClass().getResourceAsStream("/templates/" + filename);
        if (is == null)
            throw new FileNotFoundException("模板文件不存在: " + filename);
        return is.readAllBytes();
    }
}