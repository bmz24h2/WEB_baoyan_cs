package com.baoyan.chat;

import java.util.List;
import java.util.Optional;

/**
 * 模型注册表 — 定义所有可用模型及其所属服务商
 *
 * 当前支持的服务商：
 *   ALIYUN  阿里云百炼   https://bailian.console.aliyun.com  → aliyun.api.key
 *   ZHIPU   智谱AI       https://open.bigmodel.cn            → zhipu.api.key
 *
 * 自动切换逻辑：LLM 列表从上到下依次尝试，遇到额度耗尽错误跳下一个。
 * 手动选择：前端传入 selectedModel，直接用指定模型（不走 fallback）。
 */
public class ModelRegistry {

    // ── 服务商（每个有独立 endpoint 和 API Key）──────────────────────────
    public enum Provider {
        ALIYUN("阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
        ZHIPU ("智谱AI",    "https://open.bigmodel.cn/api/paas/v4/chat/completions");

        public final String label;
        public final String endpoint;
        Provider(String label, String endpoint) {
            this.label    = label;
            this.endpoint = endpoint;
        }
    }

    // ── 单个模型条目 ──────────────────────────────────────────────────────
    public record ModelEntry(
            String   id,           // API 调用时使用的 model 参数
            String   displayName,  // 前端显示名称
            Provider provider,
            String   expires,      // 免费额度到期日，null 表示永久
            boolean  forever       // 是否永久免费（智谱 GLM-4-Flash 等）
    ) {}

    // ── 大语言模型优先级列表 ──────────────────────────────────────────────
    // 阿里云在前（用户的现有额度），智谱在后（永久免费兜底）
    public static final List<ModelEntry> LLM = List.of(

        // ── 阿里云百炼（按到期日从晚到早排，尽量用够再切）────────────
        // gui-plus-2026-02-26 已于 2026/06/15 到期，已删除
        new ModelEntry("qwen3.6-flash",                "Qwen3.6-Flash",      Provider.ALIYUN, "2026/07/17", false),
        new ModelEntry("kimi-k2.6",                    "Kimi-K2.6",          Provider.ALIYUN, "2026/07/21", false),
        new ModelEntry("qwen3.6-27b",                  "Qwen3.6-27B",        Provider.ALIYUN, "2026/07/23", false),
        new ModelEntry("deepseek-v4-pro",              "DeepSeek-V4-Pro",    Provider.ALIYUN, "2026/07/24", false),
        new ModelEntry("qwen3.7-max",                  "Qwen3.7-Max",        Provider.ALIYUN, "2026/08/20", false),
        new ModelEntry("qwen3.7-max-2026-05-20",       "Qwen3.7-Max 0520",   Provider.ALIYUN, "2026/08/20", false),
        new ModelEntry("qwen3.7-plus-2026-05-26",      "Qwen3.7+",           Provider.ALIYUN, "2026/09/01", false),

        // ── 智谱AI（永久免费，阿里云全部到期后自动切来）────────────────
        new ModelEntry("glm-4-flash",                  "GLM-4-Flash",        Provider.ZHIPU,  null,         true),
        new ModelEntry("glm-4-flash-250414",           "GLM-4-Flash 250414", Provider.ZHIPU,  null,         true),
        new ModelEntry("glm-4-air",                    "GLM-4-Air",          Provider.ZHIPU,  null,         true)
    );

    // ── 工具方法 ──────────────────────────────────────────────────────────
    public static Optional<ModelEntry> findById(String id) {
        return LLM.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    /** 判断是否为免费额度耗尽错误（触发切换下一个模型） */
    public static boolean isQuotaExhausted(int status, String body) {
        if (status != 403 && status != 429) return false;
        return body.contains("AllocationQuota.FreeTierOnly")
                || body.contains("Arrearage")
                || body.contains("QuotaExceeded")
                || body.contains("InsufficientCredit")
                || body.contains("insufficient_quota")
                || body.contains("exceeded");
    }
}