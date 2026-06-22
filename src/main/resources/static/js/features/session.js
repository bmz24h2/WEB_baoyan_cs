/**
 * features/session.js  —  会话行为追踪模块
 *
 * 职责：
 *  - 记录用户在当前会话（sessionStorage）中点击过的导师
 *  - 将事件数据附带到 /api/recommend 请求，触发后端 session re-ranking
 *
 * 理论依据：参考 SIGIR/KDD 2022-2024 session-based recommendation 研究范式
 *  - 短期兴趣（session）+ 长期兴趣（profile）双融合
 *  - 时间衰减加权：越近的点击权重越高（λ^i）
 *
 * 使用：
 *   SessionTracker.track(teacher)          // 点击老师时调用
 *   SessionTracker.getEvents()             // 获取当前 session 事件列表
 *   SessionTracker.clear()                 // 清空（换题/新搜索时）
 *
 * 依赖：无
 */
var SessionTracker = (function () {
  'use strict';

  var KEY     = 'baoyan_teacher_session';
  var MAX     = 20;   // 最多保留最近 20 条

  /** 记录一次导师点击 */
  function track(teacher) {
    try {
      var events = getEvents();
      events.unshift({
        teacherName:   teacher.name         || '',
        university:    teacher.university   || '',
        researchAreas: (teacher.dirs || []).join(','),
        timestamp:     Date.now(),
      });
      sessionStorage.setItem(KEY, JSON.stringify(events.slice(0, MAX)));
    } catch (_) { /* sessionStorage 不可用时静默失败 */ }
  }

  /** 读取当前 session 事件列表 */
  function getEvents() {
    try { return JSON.parse(sessionStorage.getItem(KEY) || '[]'); }
    catch (_) { return []; }
  }

  /** 清空 session 事件 */
  function clear() {
    sessionStorage.removeItem(KEY);
  }

  return { track: track, getEvents: getEvents, clear: clear };
})();
