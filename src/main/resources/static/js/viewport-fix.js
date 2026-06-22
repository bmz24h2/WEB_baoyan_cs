/* viewport-fix.js
   解决 Windows 显示缩放（125%/150%/175%）导致页面内容区变窄的问题。
   原理：检测 devicePixelRatio，若 >1 则把 viewport width 设为物理像素宽度，
   让浏览器以 1:1 物理像素渲染，消除系统缩放对布局宽度的影响。
   在每个页面 <head> 最前面引入即可。
*/
(function () {
  var dpr = window.devicePixelRatio || 1;
  if (dpr <= 1) return;                         // 无缩放，不处理
  var physicalW = Math.round(screen.width * dpr); // 物理像素宽度
  var meta = document.querySelector('meta[name="viewport"]');
  if (!meta) {
    meta = document.createElement('meta');
    meta.name = 'viewport';
    document.head.insertBefore(meta, document.head.firstChild);
  }
  // 把 viewport 设为物理宽度，让 CSS 1px = 1 物理像素
  meta.content = 'width=' + physicalW + ', initial-scale=' + (1 / dpr).toFixed(6);
})();
