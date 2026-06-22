/**
 * core/auth.js  —  客户端认证模块
 *
 * 职责：
 *  - 检查登录状态，未登录自动跳转到 login.html
 *  - 在导航栏右侧显示用户名 + 退出按钮
 *  - 提供 Auth.getUser() 供其他模块读取当前用户
 *
 * 使用：在需要登录保护的页面 </head> 前引入即可，无需额外调用。
 *   <script src="/js/core/auth.js"></script>
 *
 * 登录页（login.html）不引入此文件，避免循环跳转。
 */
var Auth = (function () {
  'use strict';

  var _user  = null;
  var API    = 'http://localhost:8080/api';

  /** 检查登录状态，未登录跳转到 login.html */
  async function check() {
    try {
      var r = await fetch(API + '/auth/whoami', { credentials: 'same-origin' });
      if (r.status === 401) {
        // 未登录：跳转，保存当前页面 URL 以便登录后返回
        var returnUrl = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.href = '/html/login.html?next=' + returnUrl;
        return null;
      }
      if (!r.ok) {
        // 服务器错误不强制跳转（可能后端未启动，允许离线浏览）
        console.warn('[Auth] whoami 返回', r.status);
        return null;
      }
      _user = await r.json();
      _renderUserBadge(_user.username, _user.avatar);
      return _user;
    } catch (e) {
      // 后端未启动时不跳转，允许离线浏览公开页面
      console.warn('[Auth] 后端未连接，跳过登录检查');
      return null;
    }
  }

  /** 当前用户信息（check() 之后可用） */
  function getUser() { return _user; }

  /** 登出 */
  async function logout() {
    try { await fetch(API + '/auth/logout', { method: 'POST', credentials: 'same-origin' }); }
    catch (_) {}
    window.location.href = '/html/login.html';
  }

  /** 在导航栏右侧注入用户名 + 退出按钮（含头像） */
  function _renderUserBadge(username, avatar) {
    var nav = document.querySelector('.topnav');
    if (!nav) return;
    if (document.getElementById('auth-badge')) return;

    var avatarHtml = avatar && avatar.startsWith('data:image/')
      ? '<img src="' + avatar + '" style="width:22px;height:22px;border-radius:50%;'
        + 'object-fit:cover;flex-shrink:0" alt="">'
      : '<span style="width:22px;height:22px;border-radius:50%;background:var(--purple-bg);'
        + 'color:var(--purple-mid);font-size:11px;font-weight:700;'
        + 'display:inline-flex;align-items:center;justify-content:center;flex-shrink:0">'
        + (username.charAt(0).toUpperCase())
        + '</span>';

    var badge = document.createElement('div');
    badge.id  = 'auth-badge';
    badge.style.cssText = 'display:flex;align-items:center;gap:6px;'
      + 'margin-left:auto;flex-shrink:0;padding-right:4px';

    // 头像+名字：点击跳转个人信息页
    var profileLink = document.createElement('a');
    profileLink.href = '/html/profile.html';
    profileLink.style.cssText = 'display:flex;align-items:center;gap:6px;'
      + 'text-decoration:none;color:var(--text2);font-size:11px;'
      + 'padding:3px 8px;border-radius:6px;cursor:pointer;transition:background .12s';
    profileLink.title = '个人信息';
    profileLink.innerHTML = avatarHtml
      + '<span>' + username + '</span>';
    profileLink.addEventListener('mouseenter', function() {
      this.style.background = 'var(--surface2)';
    });
    profileLink.addEventListener('mouseleave', function() {
      this.style.background = '';
    });

    // 设置图标（齿轮）：点击跳转 settings.html
    var settingsBtn = document.createElement('a');
    settingsBtn.href = '/html/settings.html';
    settingsBtn.title = '系统设置';
    settingsBtn.style.cssText = 'display:flex;align-items:center;justify-content:center;'
      + 'width:26px;height:26px;border-radius:6px;color:var(--text3);'
      + 'text-decoration:none;transition:background .12s;flex-shrink:0';
    settingsBtn.innerHTML = '⚙ 设置';
    settingsBtn.style.cssText = 'display:flex;align-items:center;gap:4px;'
      + 'text-decoration:none;color:var(--text2);font-size:12px;'
      + 'padding:3px 8px;border-radius:6px;cursor:pointer;transition:background .12s;';
    settingsBtn.addEventListener('mouseenter', function() {
      this.style.background = 'var(--surface2)';
      this.style.color = 'var(--text)';
    });
    settingsBtn.addEventListener('mouseleave', function() {
      this.style.background = '';
      this.style.color = '';
    });

    var logoutBtn = document.createElement('button');
    logoutBtn.textContent = '退出';
    logoutBtn.onclick = function() { Auth.logout(); };
    logoutBtn.style.cssText = 'background:none;border:1px solid var(--border2);'
      + 'border-radius:6px;padding:3px 10px;font-size:11px;color:var(--text3);cursor:pointer';
    logoutBtn.title = '退出登录';

    badge.appendChild(profileLink);
    badge.appendChild(settingsBtn);
    badge.appendChild(logoutBtn);

    var space = nav.querySelector('.topnav-space');
    if (space) nav.removeChild(space);
    nav.appendChild(badge);
  }

  // 页面加载后自动检查
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', check);
  } else {
    check();
  }

  return { check: check, getUser: getUser, logout: logout };
})();