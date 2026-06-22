package com.baoyan.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 语音合成代理 — 调用阿里云百炼 CosyVoice API
 *
 * POST /api/speech/synthesize
 *   body: { text, voice?, speed?, format? }
 *   return: audio/mpeg 或 audio/wav 字节流
 *
 * 同一个 aliyun.api.key，无需额外注册。
 * 若未配置 Key → 返回 503，前端自动降级到浏览器 TTS。
 *
 * 可用音色（voice 字段）：
 *   longxiaochun  小淳（女，温柔，默认）
 *   longwan       婉子（女，活泼）
 *   longcheng     程成（男，沉稳）
 *   longfei       龙飞（男，磁性）
 *   loongstella   Stella（女，英文/中文混合）
 */
@RestController
@RequestMapping("/api/speech")
public class SpeechController {

    private static final Logger log = LoggerFactory.getLogger(SpeechController.class);

    private static final String TTS_ENDPOINT =
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2audio/synthesis";

    @Value("${aliyun.api.key:${qwen.api.key:}}")
    private String aliyunKey;

    /** 可用音色列表（供前端展示） */
    @GetMapping("/voices")
    public ResponseEntity<Map<String, Object>> listVoices() {
        return ResponseEntity.ok(Map.of("voices", List.of(
            Map.of("id","longxiaochun","name","小淳（女·温柔）",  "gender","female"),
            Map.of("id","longwan",     "name","婉子（女·活泼）",  "gender","female"),
            Map.of("id","longcheng",   "name","程成（男·沉稳）",  "gender","male"),
            Map.of("id","longfei",     "name","龙飞（男·磁性）",  "gender","male"),
            Map.of("id","longxiaoxia", "name","小夏（女·温柔）",  "gender","female"),
            Map.of("id","longxiaobai", "name","小白（女·播报）",  "gender","female")
        ), "configured", !aliyunKey.isBlank()));
    }

    /** 合成语音 → 返回 mp3/wav 字节流 */
    @PostMapping("/synthesize")
    public ResponseEntity<byte[]> synthesize(@RequestBody Map<String, Object> req) {
        if (aliyunKey.isBlank()) {
            log.warn("[TTS] 未配置 aliyun.api.key，无法使用 CosyVoice");
            return ResponseEntity.status(503).build();  // 让前端降级到浏览器 TTS
        }

        String text   = (String) req.getOrDefault("text",   "");
        String voice  = (String) req.getOrDefault("voice",  "longxiaochun");
        // speed 可能是 null（JSON null）或 NaN（序列化后变 null）
        Object speedObj = req.getOrDefault("speed", 1.1);
        double speed;
        try { speed = speedObj == null ? 1.1 : ((Number) speedObj).doubleValue();
              if (Double.isNaN(speed) || speed < 0.5 || speed > 2.0) speed = 1.1;
        } catch (Exception ignored) { speed = 1.1; }
        String format = (String) req.getOrDefault("format", "mp3");

        if (text.isBlank()) return ResponseEntity.badRequest().build();

        // 限制长度，防止超额（TTS 通常按字数计费）
        if (text.length() > 2000) text = text.substring(0, 2000) + "…";

        // 构造请求 JSON（CosyVoice v1 格式）
        String body = """
            {
              "model": "cosyvoice-v1",
              "input": { "text": "%s" },
              "parameters": {
                "voice":  "%s",
                "format": "%s",
                "volume": 50,
                "rate":   %.1f,
                "pitch":  1.0,
                "sample_rate": 22050
              }
            }
            """.formatted(
                text.replace("\"", "\\\"").replace("\n", " "),
                voice, format, speed);

        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15)).build();

            HttpResponse<byte[]> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(TTS_ENDPOINT))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization",  "Bearer " + aliyunKey)
                    .header("Content-Type",   "application/json")
                    .header("Accept",         "audio/" + format)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray());

            if (resp.statusCode() == 200) {
                byte[] audio = resp.body();
                // 检查是否直接返回了音频（Content-Type 是 audio/*）
                String ct = resp.headers().firstValue("content-type").orElse("");
                if (ct.startsWith("audio/")) {
                    return ResponseEntity.ok()
                        .contentType("mp3".equals(format)
                            ? MediaType.parseMediaType("audio/mpeg")
                            : MediaType.parseMediaType("audio/wav"))
                        .body(audio);
                }
                // 可能是 JSON 包装的 base64 音频
                String json = new String(audio, StandardCharsets.UTF_8);
                if (json.contains("\"audio\"")) {
                    // 提取 base64 音频
                    int start = json.indexOf("\"audio\"") + 9;
                    int quote1 = json.indexOf('"', start);
                    int quote2 = json.indexOf('"', quote1 + 1);
                    if (quote1 >= 0 && quote2 > quote1) {
                        String b64 = json.substring(quote1 + 1, quote2);
                        byte[] decoded = java.util.Base64.getDecoder().decode(b64);
                        return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(
                                "mp3".equals(format) ? "audio/mpeg" : "audio/wav"))
                            .body(decoded);
                    }
                }
                log.warn("[TTS] 响应格式未识别 ct={} body前100={}", ct,
                    json.length() > 100 ? json.substring(0, 100) : json);
                return ResponseEntity.status(502).build();
            }

            log.warn("[TTS] CosyVoice 返回 {}: {}",
                resp.statusCode(),
                new String(resp.body(), StandardCharsets.UTF_8).substring(0,
                    Math.min(200, resp.body().length)));
            return ResponseEntity.status(resp.statusCode()).build();

        } catch (Exception e) {
            log.error("[TTS] 合成失败: {}", e.getMessage());
            return ResponseEntity.status(503).build();
        }
    }
}