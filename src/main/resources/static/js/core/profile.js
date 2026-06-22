/**
 * core/profile.js  —  个人信息管理模块
 *
 * 职责：
 *  - 统一定义 PROFILE_KEY，避免各页面硬编码
 *  - 提供 getProfile / saveProfile / clearProfile
 *  - 提供 loadProfileIntoForm：自动将 profile 填入页面表单
 *  - 提供 exportProfile / importProfile：跨设备迁移
 *
 * 使用示例：
 *   const p = Profile.get();          // 读取
 *   Profile.save({ name: '张三' });   // 合并保存
 *   Profile.loadIntoForm({ ... });    // 填入表单字段
 *
 * 依赖：无（纯 localStorage）
 */
var Profile = (function () {
  'use strict';

  var KEY = 'baoyan_profile';

  /** 读取完整 profile 对象（不存在则返回 {}） */
  function get() {
    try { return JSON.parse(localStorage.getItem(KEY) || '{}'); }
    catch (_) { return {}; }
  }

  /** 保存 profile（合并而非覆盖，传入字段优先） */
  function save(data) {
    var current = get();
    var merged = Object.assign({}, current, data);
    localStorage.setItem(KEY, JSON.stringify(merged));
    return merged;
  }

  /** 完整替换 profile */
  function set(data) {
    localStorage.setItem(KEY, JSON.stringify(data || {}));
  }

  /** 清除 profile */
  function clear() {
    localStorage.removeItem(KEY);
  }

  /**
   * 将 profile 填入页面表单元素
   * @param {Object} fieldMap  { profileKey: 'element-id' } 的映射
   *
   * 示例：
   *   Profile.loadIntoForm({
   *     name:     'p-name',
   *     school:   'p-school',
   *     gpa:      'p-gpa',
   *   });
   */
  function loadIntoForm(fieldMap) {
    var data = get();
    Object.keys(fieldMap).forEach(function (key) {
      var val = data[key];
      if (val === undefined || val === null || val === '') return;
      var el = document.getElementById(fieldMap[key]);
      if (!el) return;
      if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT') {
        el.value = val;
      }
    });
  }

  /**
   * 激活芯片组（chip group）中与 profile.targetDirs 匹配的芯片
   * @param {string} chipGroupId  芯片容器元素的 id
   * @param {string[]} values     要选中的值列表
   */
  function selectChips(chipGroupId, values) {
    if (!values || !values.length) return;
    var container = document.getElementById(chipGroupId);
    if (!container) return;
    container.querySelectorAll('.chip').forEach(function (chip) {
      if (values.indexOf(chip.dataset.v) !== -1) chip.classList.add('sel');
    });
  }

  /** 导出为 JSON 文件下载 */
  function exportToFile() {
    var raw = localStorage.getItem(KEY);
    if (!raw) { alert('没有已保存的个人信息'); return; }
    var blob = new Blob([raw], { type: 'application/json' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = '保研个人信息_' + new Date().toISOString().slice(0, 10) + '.json';
    a.click();
    URL.revokeObjectURL(a.href);
  }

  /** 从 JSON 文件导入 */
  function importFromFile(file, callback) {
    if (!file) return;
    var reader = new FileReader();
    reader.onload = function (e) {
      try {
        var data = JSON.parse(e.target.result);
        if (!data.name && !data.school) throw new Error('格式不符');
        set(data);
        if (callback) callback(null, data);
      } catch (err) {
        if (callback) callback(err);
      }
    };
    reader.readAsText(file);
  }

  return {
    KEY: KEY,
    get: get,
    save: save,
    set: set,
    clear: clear,
    loadIntoForm: loadIntoForm,
    selectChips: selectChips,
    exportToFile: exportToFile,
    importFromFile: importFromFile,
  };
})();
