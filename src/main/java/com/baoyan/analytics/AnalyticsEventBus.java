package com.baoyan.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.*;

/** SSE 实时事件总线（从 AnalyticsController 提取） */
@Service
class AnalyticsEventBus {

private static final Logger log = LoggerFactory.getLogger(AnalyticsEventBus.class);
private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
private final AtomicLong eventSeq = new AtomicLong(0);

// 记录最近 100 条事件（供新连接时回放）
private final Deque<ScrapeEvent> recentEvents = new ArrayDeque<>();
private static final int MAX_HISTORY = 100;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScrapeEvent(
    String type,          // scrape-start | scrape-progress | scrape-done | scrape-error | heartbeat
    String university,
    int    count,
    String reason,        // for error events
    Long   duration,      // for done events (ms)
    String threadId,      // virtual thread name
    long   seq
) {
    static ScrapeEvent start(String university) {
        return new ScrapeEvent("scrape-start", university, 0, null, null,
            Thread.currentThread().getName(), 0);
    }
    static ScrapeEvent progress(String university, int count) {
        return new ScrapeEvent("scrape-progress", university, count, null, null,
            Thread.currentThread().getName(), 0);
    }
    static ScrapeEvent done(String university, int count, long durationMs) {
        return new ScrapeEvent("scrape-done", university, count, null, durationMs,
            Thread.currentThread().getName(), 0);
    }
    static ScrapeEvent error(String university, String reason) {
        return new ScrapeEvent("scrape-error", university, 0, reason, null,
            Thread.currentThread().getName(), 0);
    }
}

/** ScraperService 在各阶段调用此方法推送事件 */
public void publish(ScrapeEvent event) {
    long seq = eventSeq.incrementAndGet();
    var e = new ScrapeEvent(event.type(), event.university(), event.count(),
                            event.reason(), event.duration(), event.threadId(), seq);
    synchronized (recentEvents) {
        recentEvents.addLast(e);
        if (recentEvents.size() > MAX_HISTORY) recentEvents.pollFirst();
    }

    List<SseEmitter> dead = new ArrayList<>();
    for (SseEmitter emitter : emitters) {
        try {
            emitter.send(SseEmitter.event()
                .name(e.type())
                .id(String.valueOf(seq))
                .data(e));
        } catch (Exception ex) {
            dead.add(emitter);
        }
    }
    emitters.removeAll(dead);
}

SseEmitter subscribe() {
    // 超时 30 分钟，浏览器会自动重连
    SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(e -> emitters.remove(emitter));

    // 回放最近事件
    synchronized (recentEvents) {
        for (var ev : recentEvents) {
            try {
                emitter.send(SseEmitter.event().name(ev.type()).id(String.valueOf(ev.seq())).data(ev));
            } catch (Exception ignored) {}
        }
    }
    return emitter;
}

int subscriberCount() { return emitters.size(); }
}
