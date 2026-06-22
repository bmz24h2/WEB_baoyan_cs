package com.baoyan.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 保研顾问对话接口
 *
 * 模型切换逻辑：
 *   自动模式：按 ModelRegistry.LLM 列表顺序尝试，额度耗尽自动换下一个
 *   手动模式：前端传入 selectedModel，直接使用该模型（不走 fallback）
 *
 * 服务商 API Key 配置（application.properties）：
 *   aliyun.api.key=sk-xxx   ← 阿里云百炼（原 qwen.api.key 仍兼容）
 *   zhipu.api.key=xxx       ← 智谱AI，永久免费（从 open.bigmodel.cn 获取）
 */
@RestController
@CrossOrigin(origins = "*")
public class ChatController {

    // 兼容旧配置名 qwen.api.key
    @Value("${aliyun.api.key:${qwen.api.key:}}")
    private String aliyunKey;

    @Value("${zhipu.api.key:}")
    private String zhipuKey;

    @Autowired
    private ChatHistoryService history;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    // ── 主对话接口 ─────────────────────────────────────────────────────────
    @PostMapping("/api/chat")
    public SseEmitter chat(@RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);
        if (aliyunKey.isBlank() && zhipuKey.isBlank()) {
            asyncClose(emitter, "error",
                "⚠️ 未配置任何 API Key。请在 application.properties 中添加：\n" +
                "aliyun.api.key=sk-xxx  （阿里云百炼）\n" +
                "zhipu.api.key=xxx      （智谱AI，永久免费）");
            return emitter;
        }
        pool.submit(() -> doStream(emitter, req));
        return emitter;
    }

    // ── 前端配置查询（用于禁用未配置的服务商）──────────────────────────────
    @GetMapping("/api/chat/config")
    public Map<String, Boolean> config() {
        return Map.of(
            "aliyun", !aliyunKey.isBlank(),
            "zhipu",  !zhipuKey.isBlank()
        );
    }

    // ── 会话管理 REST 端点 ────────────────────────────────────────────────

    /** 列出全部会话（不含消息体，用于侧边栏） */
    @GetMapping("/api/chat/sessions")
    public List<Map<String, Object>> listSessions() {
        return history.listSessions();
    }

    /** 获取单个会话的元数据 + 全部消息（用于加载历史对话） */
    @GetMapping("/api/chat/sessions/{id}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable long id) {
        var session = history.getSession(id);
        return session == null
            ? ResponseEntity.notFound().build()
            : ResponseEntity.ok(session);
    }

    /** 删除单个会话 */
    @DeleteMapping("/api/chat/sessions/{id}")
    public Map<String, Boolean> deleteSession(@PathVariable long id) {
        return Map.of("deleted", history.deleteSession(id));
    }

    /** 清空全部会话 */
    @DeleteMapping("/api/chat/sessions")
    public Map<String, String> clearAllSessions() {
        history.deleteAllSessions();
        return Map.of("status", "ok");
    }

    // ── 核心：带 fallback 的流式调用 ───────────────────────────────────────
    private void doStream(SseEmitter emitter, ChatRequest req) {
        List<ModelRegistry.ModelEntry> candidates;

        if (req.getSelectedModel() != null && !req.getSelectedModel().isBlank()) {
            // 手动选择模式：只尝试这一个模型
            var found = ModelRegistry.findById(req.getSelectedModel());
            if (found.isEmpty()) {
                asyncClose(emitter, "error", "未找到模型：" + req.getSelectedModel());
                return;
            }
            candidates = List.of(found.get());
        } else {
            // 自动模式：筛选出已配置 Key 的模型按顺序尝试
            candidates = ModelRegistry.LLM.stream()
                    .filter(m -> !getKey(m.provider()).isBlank())
                    .toList();
            if (candidates.isEmpty()) {
                asyncClose(emitter, "error", "没有可用的模型，请检查 API Key 配置。");
                return;
            }
        }

        List<Map<String, String>> messages = buildMessages(req);

        for (int i = 0; i < candidates.size(); i++) {
            var model = candidates.get(i);
            String key = getKey(model.provider());
            try {
                var resp = callApi(model.provider().endpoint, key, model.id(), messages);

                if (resp.statusCode() == 200) {
                    // ── 成功调通：处理会话（新建 or 续用）──────────────────
                    Long sessionId = req.getSessionId();
                    boolean isNewSession = false;
                    if (sessionId == null || !history.exists(sessionId)) {
                        sessionId = history.createSession(req.getMessage());
                        isNewSession = true;
                    }

                    // 推送 session_info 事件（前端用来同步侧边栏 + 记录当前会话 id）
                    String si = toJson("id",    String.valueOf(sessionId),
                                       "isNew", String.valueOf(isNewSession));
                    emitter.send(SseEmitter.event().name("session_info").data(si));

                    // 若用了备用模型，推送 fallback 事件
                    if (i > 0) {
                        String fb = toJson("from",     candidates.get(0).id(),
                                          "to",       model.id(),
                                          "type",     "大语言模型",
                                          "provider", model.provider().label);
                        emitter.send(SseEmitter.event().name("fallback").data(fb));
                    }
                    // 推送当前模型信息
                    String info = toJson("model",    model.id(),
                                        "type",     "大语言模型",
                                        "provider", model.provider().label,
                                        "forever",  String.valueOf(model.forever()));
                    emitter.send(SseEmitter.event().name("model_info").data(info));

                    // 流式输出，同时累积完整回复
                    String assistantContent = streamTokens(emitter, resp.body());

                    // 持久化：用户消息 + 助手回复
                    history.saveMessage(sessionId, "user",      req.getMessage(), null);
                    history.saveMessage(sessionId, "assistant", assistantContent, model.id());
                    return;
                }

                String errBody = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);

                // 额度耗尽且是自动模式 → 换下一个
                if (ModelRegistry.isQuotaExhausted(resp.statusCode(), errBody)
                        && req.getSelectedModel() == null) {
                    continue;
                }

                // 手动模式或其他错误 → 直接报错
                asyncClose(emitter, "error",
                    model.provider().label + " API 错误 " + resp.statusCode() +
                    "。\n若是额度问题，请切换自动模式或换其他模型。");
                return;

            } catch (Exception e) {
                asyncClose(emitter, "error", "请求异常：" + e.getMessage());
                return;
            }
        }

        asyncClose(emitter, "error",
            "❌ 所有可用模型均已耗尽或不可用。\n" +
            "阿里云：请登录控制台充值或等待额度恢复。\n" +
            "智谱AI：请在 application.properties 添加 zhipu.api.key 以启用永久免费额度。");
    }

    // ── 发起 API 请求 ──────────────────────────────────────────────────────
    private HttpResponse<InputStream> callApi(
            String endpoint, String key, String modelId,
            List<Map<String, String>> messages) throws Exception {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        body.put("messages", messages);
        body.put("stream", true);
        body.put("max_tokens", 1500);
        body.put("temperature", 0.75);

        return http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(90))
                .build(),
            HttpResponse.BodyHandlers.ofInputStream()
        );
    }

    // ── 逐 token 流式输出，并累积完整内容用于持久化 ──────────────────────
    private String streamTokens(SseEmitter emitter, InputStream body) throws Exception {
        StringBuilder full = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) {
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    break;
                }
                try {
                    @SuppressWarnings("unchecked")
                    var chunk   = (Map<String, Object>) mapper.readValue(data, Map.class);
                    @SuppressWarnings("unchecked")
                    var choices = (List<Map<String, Object>>) chunk.get("choices");
                    if (choices == null || choices.isEmpty()) continue;
                    @SuppressWarnings("unchecked")
                    var delta   = (Map<String, Object>) choices.get(0).get("delta");
                    if (delta == null) continue;
                    var content = delta.get("content");
                    if (content instanceof String s && !s.isEmpty()) {
                        full.append(s);
                        emitter.send(SseEmitter.event().name("token").data(s));
                    }
                } catch (Exception ignored) {}
            }
        }
        emitter.complete();
        return full.toString();
    }

    // ── 构建 messages ──────────────────────────────────────────────────────
    private List<Map<String, String>> buildMessages(ChatRequest req) {
        var msgs = new ArrayList<Map<String, String>>();
        var sys  = new StringBuilder("""
            你是一个专业的中国 CS 保研咨询助手，深度熟悉 985/211 高校计算机专业保研全流程。

            核心知识：
            1. 时间节点：夏令营（5-8月）→ 预推免（9月下旬）→ 推免系统（10月22日）
            2. 院校梯队：T1 清北 / T2 华五交 / T3 强985 / T4 普通985+强211
            3. 材料：个人陈述、简历、推荐信、论文/竞赛加分
            4. 面试：数据结构/算法/OS/计网/数学 + 科研展示
            5. 导师联系：邮件写法、套词时机、研究契合度展示
            6. 强/弱 com：强 committee 多导师集体决定，弱 com 需先获导师意向
            """);
        if (req.getContext() != null && !req.getContext().isBlank())
            sys.append("\n【当前关注】\n").append(req.getContext().trim()).append("\n");
        sys.append("\n用中文回答，专业友好，适当分点加粗。不确定信息请明确说明。");

        msgs.add(Map.of("role", "system", "content", sys.toString()));
        if (req.getHistory() != null && !req.getHistory().isEmpty()) {
            int start = Math.max(0, req.getHistory().size() - 16);
            msgs.addAll(req.getHistory().subList(start, req.getHistory().size()));
        }
        msgs.add(Map.of("role", "user", "content", req.getMessage().trim()));
        return msgs;
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────
    private String getKey(ModelRegistry.Provider p) {
        return switch (p) {
            case ALIYUN -> aliyunKey;
            case ZHIPU  -> zhipuKey;
        };
    }

    /** 简单拼 JSON（k-v 对，值均为字符串） */
    private String toJson(String... kvs) {
        var sb = new StringBuilder("{");
        for (int i = 0; i < kvs.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(kvs[i]).append("\":\"").append(kvs[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    private void asyncClose(SseEmitter emitter, String event, String data) {
        pool.submit(() -> {
            try { emitter.send(SseEmitter.event().name(event).data(data)); emitter.complete(); }
            catch (Exception e) { emitter.completeWithError(e); }
        });
    }

    // ── DTO ───────────────────────────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatRequest {
        private String message;
        private String context;
        private String selectedModel;             // null = 自动 fallback
        private Long   sessionId;                 // null = 新建会话；非空 = 续聊
        private List<Map<String, String>> history;

        public String getMessage()                { return message; }
        public void   setMessage(String m)        { this.message = m; }
        public String getContext()                { return context; }
        public void   setContext(String c)        { this.context = c; }
        public String getSelectedModel()          { return selectedModel; }
        public void   setSelectedModel(String s)  { this.selectedModel = s; }
        public Long   getSessionId()              { return sessionId; }
        public void   setSessionId(Long id)       { this.sessionId = id; }
        public List<Map<String, String>> getHistory() { return history; }
        public void setHistory(List<Map<String, String>> h) { this.history = h; }
    }
}