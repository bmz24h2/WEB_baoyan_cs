/**
 * features/favorites.js  —  收藏导师模块
 */
var Favorites = (function () {
  'use strict';

  var KEY = 'baoyan_fav_teachers';

  function getAll() {
    try { return JSON.parse(localStorage.getItem(KEY) || '[]'); }
    catch (_) { return []; }
  }

  function saveAll(list) {
    localStorage.setItem(KEY, JSON.stringify(list));
  }

  function isFaved(name, university) {
    return getAll().some(function (f) {
      return f.name === name && f.university === university;
    });
  }

  /** 切换收藏状态，更新按钮外观 */
  function toggle(teacher, btn) {
    var list = getAll();
    var idx  = list.findIndex(function (f) {
      return f.name === teacher.name && f.university === teacher.university;
    });
    if (idx >= 0) {
      list.splice(idx, 1);
      if (btn) btn.textContent = '☆';
    } else {
      // ★ 兼容后端不同字段名：dirs / researchAreas / research_areas
      var dirs = teacher.dirs
        || (typeof teacher.researchAreas === 'string'
              ? teacher.researchAreas.split(/[,，、]+/).map(function(s){ return s.trim(); }).filter(Boolean)
              : teacher.researchAreas)
        || (typeof teacher.research_areas === 'string'
              ? teacher.research_areas.split(/[,，、]+/).map(function(s){ return s.trim(); }).filter(Boolean)
              : teacher.research_areas)
        || [];
      list.unshift(Object.assign({}, teacher, {
        dirs: dirs,
        savedAt: Date.now()
      }));
      if (btn) btn.textContent = '⭐';
    }
    saveAll(list);
  }

  /** 删除单条收藏 */
  function remove(name, university) {
    saveAll(getAll().filter(function (f) {
      return !(f.name === name && f.university === university);
    }));
  }

  /** HTML 转义 */
  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g,'&amp;').replace(/</g,'&lt;')
      .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  }

  /**
   * 渲染收藏列表到指定容器
   * @param {string} containerId  容器元素的 id
   */
  function render(containerId) {
    var el = document.getElementById(containerId);
    if (!el) return;
    var list = getAll();

    if (!list.length) {
      el.innerHTML = '<div style="color:var(--text3);font-size:13px;padding:20px 4px">'
        + '还没有收藏的导师。在匹配结果里点 ☆ 收藏。</div>';
      return;
    }

    el.innerHTML = list.map(function (f, i) {
      // 研究方向标签
      var dirs = (f.dirs || []).slice(0, 6);   // 最多显示6个
      var dirTags = dirs.map(function (d) {
        return '<span style="background:var(--purple-bg,#f0eaff);color:var(--purple-txt,#5a00d8);'
          + 'border-radius:10px;padding:2px 9px;font-size:11px">' + esc(d) + '</span>';
      }).join('');

      // 评分环（如果有 score）
      var scoreHtml = '';
      if (f.score != null) {
        var pct = Math.round((+f.score || 0) * 100);
        scoreHtml = '<div style="display:flex;align-items:center;justify-content:center;'
          + 'flex-direction:column;width:40px;height:40px;border-radius:50%;'
          + 'border:2.5px solid var(--green-mid,#4caf50);flex-shrink:0;margin-right:12px">'
          + '<span style="font-size:13px;font-weight:700;color:var(--green-txt,#2e7d32)">' + pct + '</span>'
          + '<span style="font-size:9px;color:var(--text3)">分</span>'
          + '</div>';
      }

      // 主页链接
      var link = f.profileUrl
        ? '<a href="' + esc(f.profileUrl) + '" target="_blank" '
          + 'style="font-size:11px;color:var(--blue-txt,#3a7bd5);text-decoration:none;'
          + 'background:var(--surface2);border-radius:4px;padding:2px 8px;border:.5px solid var(--border2)">主页</a> '
        : '';

      // 收藏时间
      var savedStr = '';
      if (f.savedAt) {
        try {
          var d = new Date(+f.savedAt);
          var mon = d.getMonth() + 1;   // 0-11 → 1-12
          var day = d.getDate();
          // 合法性检查：月份 1-12，日期 1-31
          if (mon >= 1 && mon <= 12 && day >= 1 && day <= 31) {
            savedStr = '<span style="font-size:10px;color:var(--text3);margin-left:6px">'
              + mon + '月' + day + '日收藏</span>';
          }
        } catch(_) {}
      }

      return '<div class="fav-card" style="background:var(--surface);border:.5px solid var(--border);'
        + 'border-radius:10px;padding:14px 16px;margin-bottom:10px">'
        + '<div style="display:flex;align-items:flex-start">'
          + scoreHtml
          + '<div style="flex:1;min-width:0">'
            // 姓名 + 职称
            + '<div style="font-size:14px;font-weight:700;margin-bottom:2px">'
              + esc(f.name || '—')
              + '<span style="font-size:12px;font-weight:400;color:var(--text3);margin-left:6px">'
              + esc(f.title || '') + '</span>'
              + savedStr
            + '</div>'
            // 学校
            + '<div style="font-size:12px;color:var(--text2);margin-bottom:6px">'
              + esc(f.university || '') + '</div>'
            // 研究方向标签
            + (dirTags
                ? '<div style="display:flex;gap:4px;flex-wrap:wrap;margin-bottom:8px">' + dirTags + '</div>'
                : '<div style="font-size:11px;color:var(--text3);margin-bottom:8px">暂无方向信息</div>')
            // 操作栏
            + '<div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">'
              + link
              + '<button data-fav-remove-idx="' + i + '" '
                + 'style="background:none;border:.5px solid var(--border2);border-radius:4px;'
                + 'cursor:pointer;font-size:11px;padding:2px 8px;color:var(--text3)">'
                + '取消收藏</button>'
            + '</div>'
          + '</div>'
        + '</div>'
        + '</div>';
    }).join('');

    // 用 addEventListener 绑定取消收藏按钮（避免 onclick 字符串里传名字导致引号冲突）
    el.querySelectorAll('[data-fav-remove-idx]').forEach(function(btn) {
      btn.addEventListener('click', function() {
        var idx = +btn.dataset.favRemoveIdx;
        var item = getAll()[idx];
        if (item) {
          remove(item.name, item.university);
          render(containerId);
        }
      });
    });
  }

  return {
    isFaved: isFaved,
    toggle:  toggle,
    remove:  remove,
    render:  render,
    getAll:  getAll,
  };
})();