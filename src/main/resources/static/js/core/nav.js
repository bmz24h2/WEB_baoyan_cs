/**
 * core/nav.js  —  导航栏统一生成 + 初始化
 *
 * 职责：
 *  1. 向页面中的 <nav class="topnav"> 注入完整导航 HTML
 *  2. 根据当前 URL 自动高亮对应链接
 *  3. 加载 Lucide 图标（本地 → CDN → 降级）
 *
 * 页面只需保留空容器：
 *   <nav class="topnav"></nav>
 *
 * 今后新增/删除/改名导航项，只改这里的 LINKS 数组。
 */
(function () {
  'use strict';

  // ── 导航链接定义（顺序即显示顺序）────────────────────────────────
  // profile.html 入口已移到右上角头像，不在此列表
  var LINKS = [
    { href: 'home.html',          label: '主页',    icon: 'home'          },
    { href: 'index.html',         label: '院校导航', icon: 'landmark'      },
    { href: 'tools.html',         label: '工具箱',  icon: 'wrench'        },
    { href: 'advisor-match.html', label: '导师匹配', icon: 'users'         },
    { href: 'analytics.html',     label: '数据分析', icon: 'bar-chart-2'   },
    { href: 'chat.html',          label: 'AI 顾问',  icon: 'message-circle'},
    { href: 'interview.html',     label: '模拟面试', icon: 'mic'           },
    { href: 'statement.html',     label: '个人陈述', icon: 'file-text'     },
    { href: 'documents.html',     label: '文档生成', icon: 'file-plus'     },
  ];

  // ── 生成并注入 nav HTML ───────────────────────────────────────────
  function buildNav() {
    var nav = document.querySelector('nav.topnav');
    if (!nav) return;

    var page   = location.pathname.split('/').pop() || 'home.html';
    var inner  = [
      '<a class="topnav-logo" href="/html/home.html">',
      '  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"',
      '       width="16" height="16" style="flex-shrink:0">',
      '    <path d="M22 10v6M2 10l10-5 10 5-10 5z"/>',
      '    <path d="M6 12v5c3 3 9 3 12 0v-5"/>',
      '  </svg>',
      '  保研导航',
      '</a>',
      '<span class="topnav-sep">·</span>',
    ];

    LINKS.forEach(function (item) {
      var active = item.href === page ? ' active' : '';
      inner.push(
        '<a class="topnav-link' + active + '" href="/html/' + item.href + '">'
        + '<i data-lucide="' + item.icon + '"></i>'
        + item.label
        + '</a>'
      );
    });

    inner.push('<span class="topnav-space"></span>');
    nav.innerHTML = inner.join('\n');
  }

  // ── 加载 Lucide：本地 → CDN → 跳过 ──────────────────────────────
  function loadLucide() {
    function init() {
      if (typeof lucide !== 'undefined') lucide.createIcons();
    }
    var local = document.createElement('script');
    local.src = '/js/lucide.min.js';
    local.onload = init;
    local.onerror = function () {
      var cdn = document.createElement('script');
      cdn.src = 'https://unpkg.com/lucide@0.513.0/dist/umd/lucide.min.js';
      cdn.onload = init;
      document.head.appendChild(cdn);
    };
    document.head.appendChild(local);
  }

  // ── 入口 ─────────────────────────────────────────────────────────
  function init() {
    buildNav();
    loadLucide();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();