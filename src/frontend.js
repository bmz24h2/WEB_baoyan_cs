/**
 * frontend.js — UI 渲染层
 * 数据从后端 API /api/university-data 加载
 * 不再依赖 data.js
 */

// ── 数据（从 API 加载） ──────────────────────────────────────
let UNIS = [];
let UNIV_META = {};

// ── 梯队颜色映射 ─────────────────────────────────────────────
const TM={
  "顶尖C9":   {bg:"var(--purple-bg)",txt:"var(--purple-txt)",dot:"var(--purple-mid)"},
  "上游985":  {bg:"var(--blue-bg)",  txt:"var(--blue-txt)",  dot:"var(--blue-mid)"},
  "中游985":  {bg:"var(--green-bg)", txt:"var(--green-txt)", dot:"var(--green-mid)"},
  "中下游985":{bg:"var(--amber-bg)", txt:"var(--amber-txt)", dot:"var(--amber-mid)"},
  "末流985":  {bg:"var(--coral-bg)", txt:"var(--coral-txt)", dot:"var(--coral-mid)"},
  "中科院":   {bg:"var(--surface2)", txt:"var(--text2)",     dot:"#888"},
  "顶级211":  {bg:"var(--purple-bg)",txt:"var(--purple-txt)",dot:"var(--purple-mid)"},
  "中上211":  {bg:"var(--blue-bg)",  txt:"var(--blue-txt)",  dot:"var(--blue-mid)"},
  "中流211":  {bg:"var(--green-bg)", txt:"var(--green-txt)", dot:"var(--green-mid)"},
  "中下211":  {bg:"var(--amber-bg)", txt:"var(--amber-txt)", dot:"var(--amber-mid)"},
  "末流211":  {bg:"var(--coral-bg)", txt:"var(--coral-txt)", dot:"var(--coral-mid)"},
  "双非":     {bg:"var(--surface2)", txt:"var(--text3)",     dot:"#aaa"},
};
const ts = t => TM[t] || {bg:"var(--surface2)",txt:"var(--text2)",dot:"#888"};

function comInfo(c){
  if(c==="strong")  return{badge:"badge-strong",label:"强",preview:"dp-strong"};
  if(c==="weak")    return{badge:"badge-weak",  label:"弱",preview:"dp-weak"};
  if(c==="mix_ds")  return{badge:"badge-mix",   label:"博弱+硕强",preview:"dp-mix"};
  if(c==="mix_sd")  return{badge:"badge-mix",   label:"博强+硕弱",preview:"dp-mix"};
  if(c==="cas_mix") return{badge:"badge-mix",   label:"硕强+博弱",preview:"dp-mix"};
  return{badge:"badge-weak",label:"?",preview:"dp-weak"};
}

function guessProvince(name) {
  const rules = [
    [/北京|首都|清华|北大|北航|北理|北邮|北科|北交|北化|北林|北外|传媒|民族|财经|经贸|政法|体育/, "北京"],
    [/上海|复旦|同济|华东|东华|上财|上大|上外/, "上海"],
    [/天津/, "天津"],
    [/南京|东南|河海|南航|南理|苏州|江苏|江南|南农|中国矿业|扬州/, "江苏"],
    [/浙江|杭州|宁波/, "浙江"],[/合肥|安徽|中国科学技术大学/, "安徽"],
    [/厦门|福建|福州/, "福建"],[/南昌|江西/, "江西"],
    [/山东|青岛|济南|石油大学/, "山东"],[/郑州|河南/, "河南"],
    [/武汉|华中|湖北|地质大学/, "湖北"],[/长沙|湖南|中南/, "湖南"],
    [/广州|深圳|广东|华南|暨南|广工|广外/, "广东"],[/广西|桂林/, "广西"],
    [/成都|四川|电子科大|西南财|川大/, "四川"],[/重庆|西南大学/, "重庆"],
    [/贵州|贵阳/, "贵州"],[/云南|昆明/, "云南"],
    [/西安|陕西|西电|长安|西北/, "陕西"],[/兰州|甘肃/, "甘肃"],
    [/青海/, "青海"],[/海南|三亚/, "海南"],[/太原|山西/, "山西"],
    [/石家庄|河北|燕山|华北电力/, "河北"],
    [/沈阳|大连|辽宁|东北大学|东财/, "辽宁"],[/长春|吉林|延边/, "吉林"],
    [/哈尔滨|黑龙江|工程大学/, "黑龙江"],[/内蒙古/, "内蒙古"],
    [/新疆|乌鲁木齐|石河子/, "新疆"],[/西藏|拉萨/, "西藏"],[/宁夏|银川/, "宁夏"],
  ];
  for (const [re, prov] of rules) if (re.test(name)) return prov;
  return "";
}

function lookupUnivMeta(name) {
  const u = UNIS.find(u => u.name === name || name.includes(u.name) || u.name.includes(name));
  if (u) return {tier: u.tier, province: u.province, note: u.note || "", inUnis: true};
  const m = UNIV_META[name];
  if (m) return {...m, inUnis: false};
  for (const [key, val] of Object.entries(UNIV_META)) {
    if (name.includes(key) || key.includes(name)) return {...val, inUnis: false};
  }
  return {tier:"双非", province: guessProvince(name), note:"", inUnis: false};
}

// ── 从 API 加载数据 ──────────────────────────────────────────
async function loadDataFromApi() {
  try {
    const r = await fetch(`${API_BASE}/university-data`, { signal: AbortSignal.timeout(5000) });
    if (!r.ok) throw new Error("HTTP " + r.status);
    const data = await r.json();
    UNIS = data.unis || [];
    UNIV_META = data.meta || {};
    // xhs 如果是字符串需要解析
    UNIS.forEach(u => {
      if (typeof u.xhs === "string") { try { u.xhs = JSON.parse(u.xhs); } catch(_) { u.xhs = []; } }
      if (!Array.isArray(u.xhs)) u.xhs = [];
    });
    console.log(`✅ 从 API 加载: ${UNIS.length} 所985 + ${Object.keys(UNIV_META).length} 所211`);
    return true;
  } catch (e) {
    console.warn("⚠ API 数据加载失败:", e.message, "— 页面需要后端运行");
    return false;
  }
}

let activeTier="all", activeProv="all";

function buildSidebar(){
  // 合并 UNIS 和 UNIV_META 的省份
  const uniProvs = UNIS.filter(u=>u.rank<900).map(u=>u.province);
  const metaProvs = Object.values(UNIV_META).map(m=>m.province).filter(Boolean);
  const provs=[...new Set([...uniProvs, ...metaProvs])].sort();
  const el=document.getElementById("prov");
  const all=document.createElement("button");
  all.className="sidebar-link active";all.dataset.prov="all";
  all.textContent="所有省份";all.onclick=()=>setProv(all);
  el.appendChild(all);
  provs.forEach(p=>{
    const b=document.createElement("button");
    b.className="sidebar-link";b.dataset.prov=p;
    b.textContent=p;b.onclick=()=>setProv(b);
    el.appendChild(b);
  });
}

function setTier(btn){
  document.querySelectorAll("[data-tier]").forEach(b=>b.classList.remove("active"));
  btn.classList.add("active");
  activeTier = btn.dataset.tier;
  document.querySelectorAll("[data-prov]").forEach(b=>b.classList.remove("active"));
  const allProvBtn = document.querySelector('[data-prov="all"]');
  if (allProvBtn) allProvBtn.classList.add("active");
  activeProv = "all";
  render();
}
function setProv(btn){
  document.querySelectorAll("[data-prov]").forEach(b=>b.classList.remove("active"));
  btn.classList.add("active");
  activeProv = btn.dataset.prov;
  render();
}

/** 构建 UNIV_META（211/双非）卡片 */
function buildMetaCard(name, meta) {
  const t = ts(meta.tier);
  const card = document.createElement("div");
  card.className = "card";
  card.onclick = () => openUnknownP(name, meta);
  card.innerHTML = `
    <div class="card-head">
      <div class="rank-badge" style="background:${t.bg};color:${t.txt}">
        ${meta.tier.includes("211") ? "211" : "非"}
      </div>
      <div>
        <div class="card-name">${name}</div>
        <div class="card-sub">${meta.province || ""}${meta.note ? " · " + meta.note : ""}</div>
      </div>
    </div>
    <div class="tier-pill" style="background:${t.bg};color:${t.txt}">${meta.tier}</div>
    <div style="font-size:11px;color:var(--text3);margin-top:2px">
      强弱com数据暂未收录，可点击查看详情
    </div>
    <div class="card-links">
      <button class="clbtn">查看详情</button>
    </div>`;
  return card;
}

function render(){
  const q=document.getElementById("sb").value.trim().toLowerCase();
  const cf=document.getElementById("cf").value;
  const grid=document.getElementById("grid");
  grid.innerHTML="";

  const is211Tier = activeTier.includes("211");

  // ── 1. 从 UNIS 中筛选 ──
  const filtered=UNIS.filter(u=>{
    if(activeTier!=="all"&&u.tier!==activeTier) return false;
    if(activeProv!=="all"&&u.province!==activeProv) return false;
    if(cf==="hasStrong"&&!u.depts.some(d=>["strong","mix_ds","cas_mix"].includes(d.c))) return false;
    if(cf==="allWeak"&&u.depts.some(d=>["strong","mix_ds","cas_mix"].includes(d.c))) return false;
    if(q){
      const hay=[u.name,u.province,...u.depts.map(d=>d.n),u.note].join(" ").toLowerCase();
      if(!hay.includes(q)) return false;
    }
    return true;
  });

  // ── 2. 从 UNIV_META 中筛选（211 梯队浏览 / 搜索 / 省份） ──
  const unisNames = new Set(UNIS.map(u=>u.name));
  const metaFiltered = Object.entries(UNIV_META).filter(([name, meta]) => {
    if (unisNames.has(name)) return false; // 已在 UNIS 中显示，跳过
    if (activeTier !== "all" && meta.tier !== activeTier) return false;
    if (activeProv !== "all" && meta.province !== activeProv) return false;
    // 211 没有 com 数据，强弱 com 筛选时不显示
    if (cf === "hasStrong" || cf === "allWeak") return false;
    if (q) {
      const hay = [name, meta.province || "", meta.tier, meta.note || ""].join(" ").toLowerCase();
      if (!hay.includes(q)) return false;
    }
    // 仅在以下情况显示 UNIV_META：选了 211 梯队 / 有搜索词 / 选了省份
    return is211Tier || q || activeProv !== "all";
  });

  const totalCount = filtered.length + metaFiltered.length;

  if (!totalCount) {
    const rawQ = document.getElementById("sb").value.trim();
    const isNameSearch = rawQ.length >= 2 && activeTier==="all" && activeProv==="all";

    if (isNameSearch) {
      // 尝试在 UNIV_META 里匹配 211 / 双非
      const metaMatches = Object.entries(UNIV_META).filter(([name]) =>
        name.includes(rawQ) || rawQ.includes(name)
      );

      if (metaMatches.length > 0) {
        metaMatches.forEach(([name, meta]) => {
          grid.appendChild(buildMetaCard(name, meta));
        });
      } else {
        // 完全未知学校
        const meta = {tier:"双非", province: guessProvince(rawQ), note:""};
        const t = ts("双非");
        const card = document.createElement("div");
        card.className = "card";
        card.onclick = () => openUnknownP(rawQ, meta);
        card.innerHTML = `
          <div class="card-head">
            <div class="rank-badge" style="background:${t.bg};color:${t.txt}">?</div>
            <div>
              <div class="card-name">${rawQ}</div>
              <div class="card-sub">${meta.province ? meta.province + " · " : ""}双非 / 未收录</div>
            </div>
          </div>
          <div class="tier-pill" style="background:${t.bg};color:${t.txt}">双非·未知</div>
          <div style="font-size:11px;color:var(--text3);margin-top:2px">
            不在985/211预设列表中，可手动触发爬取教师数据
          </div>
          <div class="card-links">
            <button class="clbtn" onclick="event.stopPropagation();openUnknownP('${rawQ}',${JSON.stringify(meta)})">
              查看详情 / 爬取教师
            </button>
          </div>`;
        grid.appendChild(card);
      }
    } else {
      grid.innerHTML = `<div class="empty">没有符合条件的院校</div>`;
      return;
    }
  }  // end of !totalCount

  // ── 渲染 UNIS 卡片 ──
  filtered.forEach(u=>{
    const t=ts(u.tier);
    const card=document.createElement("div");
    card.className="card";card.onclick=()=>openP(u);

    const prev=u.depts.slice(0,5).map(d=>{
      const ci=comInfo(d.c);
      const short=d.n.replace(/科学与技术|与工程学院|科学与工程|信息与电气工程学院|科学技术/g,"").slice(0,11);
      return `<span class="dp-tag ${ci.preview}">${short}</span>`;
    }).join("");
    const more=u.depts.length>5?`<span class="dp-tag dp-more">+${u.depts.length-5}</span>`:"";

    card.innerHTML=`
      <div class="card-head">
        <div class="rank-badge" style="background:${t.bg};color:${t.txt}">${u.rank<900?"#"+u.rank:"★"}</div>
        <div>
          <div class="card-name">${u.name}</div>
          <div class="card-sub">${u.province}${u.depts.length?` · ${u.depts.length}个院系`:""}</div>
        </div>
      </div>
      <div class="tier-pill" style="background:${t.bg};color:${t.txt}">${u.tier}</div>
      ${u.depts.length
        ?`<div class="dept-preview">${prev}${more}</div>`
        :`<div style="font-size:11px;color:var(--text3)">图中未收录，请查阅官网</div>`}
      <div class="card-links">
        <a class="clbtn" href="${u.csUrl}" target="_blank" onclick="event.stopPropagation()">↗ 院系</a>
        <a class="clbtn" href="${u.admUrl}" target="_blank" onclick="event.stopPropagation()">↗ 研招</a>
      </div>
    `;
    grid.appendChild(card);
  });

  // ── 渲染 UNIV_META 卡片（211 / 搜索匹配） ──
  metaFiltered.forEach(([name, meta]) => {
    grid.appendChild(buildMetaCard(name, meta));
  });

  // ── 教师数据搜索：当有搜索词且 API 可用时，追加来自 DB 的教师结果 ──
  if (q && q.length >= 2 && apiAvailable) {
    const teacherSection = document.createElement("div");
    teacherSection.id = "teacher-search-results";
    teacherSection.style.cssText = "grid-column:1/-1;margin-top:6px;";
    teacherSection.innerHTML = `<div style="font-size:11px;color:var(--text3);padding:6px 0">
      正在搜索教师数据库…</div>`;
    grid.appendChild(teacherSection);

    fetch(`${API_BASE}/search?q=${encodeURIComponent(q)}`, { signal: AbortSignal.timeout(4000) })
      .then(r => r.ok ? r.json() : null)
      .then(data => {
        const sec = document.getElementById("teacher-search-results");
        if (!sec) return;
        const teachers = data?.teachers || [];
        if (!teachers.length) { sec.remove(); return; }
        const rows = teachers.slice(0, 20).map(t => `
          <div style="display:flex;align-items:center;gap:8px;padding:5px 0;
                      border-bottom:0.5px solid var(--border);font-size:13px;">
            <span style="font-weight:500;min-width:70px">${t.name||""}</span>
            <span style="color:var(--text3);font-size:11px;min-width:60px">${t.university||""}</span>
            <span style="color:var(--text3);font-size:11px;min-width:50px">${t.title||""}</span>
            <span style="color:var(--text2);font-size:11px;flex:1">${(t.researchAreas||"").slice(0,50)}</span>
            ${t.profileUrl?`<a href="${t.profileUrl}" target="_blank"
              style="font-size:11px;color:var(--blue-txt);white-space:nowrap">↗ 主页</a>`:""}
          </div>`).join("");
        sec.innerHTML = `
          <div style="font-size:11px;font-weight:600;letter-spacing:.08em;color:var(--text3);
                      text-transform:uppercase;padding:8px 0 4px;margin-top:4px;
                      border-top:0.5px solid var(--border)">
            教师数据库匹配（${data.totalTeachers} 条）
          </div>
          ${rows}
          ${teachers.length>20?`<div style="font-size:11px;color:var(--text3);padding-top:4px">
            还有 ${teachers.length-20} 条，
            <a href="${API_BASE}/search?q=${encodeURIComponent(q)}" target="_blank"
               style="color:var(--blue-txt)">查看全部 →</a></div>`:""}`;
      })
      .catch(() => {
        const sec = document.getElementById("teacher-search-results");
        if (sec) sec.remove();
      });
  }
}

function _openPBase(u){
  const t=ts(u.tier);
  const rows=u.depts.map(d=>{
    const ci=comInfo(d.c);
    return `<tr><td>${d.n}</td><td><span class="${ci.badge}">${ci.label}</span></td></tr>`;
  }).join("")||`<tr><td colspan="2" style="color:var(--text3);font-size:12px;padding:8px 0">图中未收录该校</td></tr>`;

  const xhsHtml=u.xhs.map(k=>`<span class="xkw">${k}</span>`).join("");
  const bingLinks=u.xhs.slice(0,2).map(k=>
    `<a href="https://www.bing.com/search?q=${encodeURIComponent("site:xiaohongshu.com "+k)}&setlang=zh-CN" target="_blank" style="font-size:11px;color:var(--blue-txt);display:block;margin-top:4px">→ Bing搜「${k}」</a>`
  ).join("");

  document.getElementById("pc").innerHTML=`
    <div style="padding-right:28px;margin-bottom:10px">
      <div style="font-size:19px;font-weight:700">${u.name}</div>
      <div style="font-size:11px;color:var(--text3);margin-top:2px">${u.province}${u.rank<900?" · 排名 #"+u.rank:""}</div>
    </div>
    <span class="tier-pill" style="background:${t.bg};color:${t.txt};font-size:12px;padding:3px 10px;border-radius:10px">${u.tier}</span>

    <div class="sec">
      <div class="sec-lbl">各院系强弱 com（来源：小红书 @8052244911）</div>
      <table class="dept-tbl"><tbody>${rows}</tbody></table>
    </div>

    ${u.note?`<div class="sec"><div class="sec-lbl">备注</div><div class="note-box">${u.note}</div></div>`:""}

    <div class="sec">
      <div class="sec-lbl">小红书搜索关键词</div>
      <div>${xhsHtml}</div>
      <div style="margin-top:5px">${bingLinks}</div>
    </div>

    <div class="btn-row">
      <a class="pbtn pri" href="${u.csUrl}" target="_blank">↗ 院系主页</a>
      <a class="pbtn" href="${u.admUrl}" target="_blank">↗ 研招网</a>
    </div>

    <div style="margin-top:14px;font-size:11px;color:var(--text3);line-height:1.6">
      ⚠ 强弱com每年可能变化，以当年官方通知为准。<br>
      ⚠ 官网链接为已知域名，若失效请搜索院校名称。
    </div>
  `;
  document.getElementById("ov").classList.add("open");
}

function closeP(e){
  if(!e||e.target===document.getElementById("ov"))
    document.getElementById("ov").classList.remove("open");
}
document.addEventListener("keydown",e=>{if(e.key==="Escape")closeP();});

// ★ 异步启动：先从 API 加载数据，再渲染
(async function init() {
  await checkApi();
  const loaded = await loadDataFromApi();
  if (!loaded) {
    document.getElementById("grid").innerHTML = `
      <div class="empty">
        <div class="scrape-status error" style="justify-content:center;display:inline-flex;gap:8px">
          ⚠ 后端未连接，无法加载院校数据
        </div>
        <div style="margin-top:8px;font-size:12px;color:var(--text3)">
          请在项目目录下运行 <code style="background:var(--surface2);padding:1px 5px;border-radius:3px">mvn spring-boot:run</code> 启动后端
        </div>
      </div>`;
    return;
  }
  buildSidebar();
  render();
})();

/* Enter 键在搜索框无结果时触发爬取 */
document.getElementById("sb").addEventListener("keydown", e => {
  if (e.key !== "Enter") return;
  const q = e.target.value.trim();
  if (!q) return;
  const hasMatch = UNIS.some(u => u.name.includes(q) || q.includes(u.name.slice(0, 2)));
  if (!hasMatch) {
    const hp = (document.getElementById("inp-homepage")?.value || "").trim();
    triggerSearchScrape(q, hp);
  }
});

/** 从搜索框直接触发对某院校名的爬取，并在 grid 里显示进度 */
async function triggerSearchScrape(univName, homepage) {
  const grid = document.getElementById("grid");

  if (!apiAvailable) {
    grid.innerHTML = `
      <div class="empty">
        <div class="scrape-status error" style="justify-content:center;display:inline-flex;gap:8px">
          ⚠ 后端未连接，无法爬取
        </div>
        <div style="margin-top:8px;font-size:12px;color:var(--text3)">
          请在项目目录下运行 <code style="background:var(--surface2);padding:1px 5px;border-radius:3px">mvn spring-boot:run</code> 启动后端
        </div>
      </div>`;
    return;
  }

  if (scrapingInProgress.has(univName)) {
    grid.innerHTML = `
      <div class="empty">
        <div class="scrape-status running" style="justify-content:center;display:inline-flex;gap:8px">
          <span class="spin">⟳</span> 正在爬取「${univName}」，请稍候…
        </div>
      </div>`;
    return;
  }

  scrapingInProgress.add(univName);
  const hpNote = homepage ? `<div style="margin-top:4px;font-size:11px;color:var(--text3)">目标: ${homepage}</div>` : "";
  grid.innerHTML = `
    <div class="empty">
      <div class="scrape-status running" style="justify-content:center;display:inline-flex;gap:8px">
        <span class="spin">⟳</span> 已触发爬取「${univName}」，后台进行中…
      </div>
      ${hpNote}
      <div style="margin-top:8px;font-size:12px;color:var(--text3)" id="scrape-progress-${cssEscape(univName)}">
        正在后台爬取，将每 12 秒检查一次结果（最多 3 分钟）…
      </div>
    </div>`;

  const body = { universities: [univName] };
  if (homepage) body.homepage = homepage;

  try {
    await fetch(`${API_BASE}/scrape`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (_) {}

  // 轮询：每 8s 检查一次，最多 23 次（~3 分钟）
  const ok = await pollScrapeResult(univName, (count, attempt, max, done, active) => {
    const prog = document.getElementById(`scrape-progress-${cssEscape(univName)}`);
    if (!prog) return;  // 用户已跳转到其它视图，静默终止
    if (done) {
      prog.textContent = `✅ 已发现 ${count} 位教师，正在加载…`;
    } else if (!active && attempt >= 3) {
      prog.textContent = `⚠ 后台任务已结束但未爬到数据，请查看日志`;
    } else {
      const elapsed = (attempt * 8);
      prog.textContent = active
        ? `⟳ 爬取中…已 ${elapsed}s，暂发现 ${count} 位，继续等待…`
        : `⟳ 已等待 ${elapsed}s（第 ${attempt}/${max} 次检查），暂无数据…`;
    }
  });

  // 只有在用户还在看这个爬取进度时才更新 grid（避免覆盖用户当前视图）
  const progressEl = document.getElementById(`scrape-progress-${cssEscape(univName)}`);
  if (!progressEl) {
    scrapingInProgress.delete(univName);
    return;  // 用户已导航到其他视图，放弃更新
  }

  scrapingInProgress.delete(univName);
  delete teacherCache[univName];

  // 渲染最终结果
  try {
    const r = await fetch(`${API_BASE}/teachers?university=${encodeURIComponent(univName)}&limit=5`);
    if (r.ok) {
      const data = await r.json();
      const count = data.total || 0;
      if (count > 0) {
        grid.innerHTML = `
          <div class="empty">
            <div class="scrape-status done" style="justify-content:center;display:inline-flex;gap:8px">
              ✅ 已爬取到「${univName}」${count} 位教师
            </div>
            <div style="margin-top:8px;font-size:12px;color:var(--text3)">
              可通过后端 API 查看：
              <a href="${API_BASE}/teachers?university=${encodeURIComponent(univName)}"
                 target="_blank" style="color:var(--blue-txt)">↗ 查看全部</a>
            </div>
          </div>`;
        // 更新 API badge 计数
        const badge = document.getElementById("api-badge");
        if (badge) {
          const s = await fetch(`${API_BASE}/teachers/stats`);
          if (s.ok) { const sd = await s.json(); badge.innerHTML = `✅ API 已连接 · 教师数据 ${sd.totalTeachers} 条`; }
        }
      } else {
        // 3 分钟后仍未爬到数据，显示重试按钮
        grid.innerHTML = `
          <div class="empty">
            <div class="scrape-status error" style="justify-content:center;display:inline-flex;gap:8px">
              ⚠ 爬取超时（3 分钟）仍未获取到「${univName}」数据
            </div>
            <div style="margin-top:10px;font-size:12px;color:var(--text2)">
              后台可能仍在运行，或官网结构无法自动解析。<br>
              请查阅 <code style="background:var(--surface2);padding:1px 4px;border-radius:3px">output/scraper.log</code>，
              或尝试提供官网地址后重试：
            </div>
            <div style="display:flex;gap:6px;margin-top:10px;align-items:center;flex-wrap:wrap;">
              <input id="inp-retry-homepage" type="text"
                placeholder="可选：输入学院首页 https://ai.xxx.edu.cn"
                style="flex:1;min-width:220px;padding:6px 10px;border:0.5px solid var(--border2);
                       border-radius:var(--r-sm);background:var(--surface);color:var(--text);font-size:12px;outline:none;">
              <button id="btn-retry-scrape" style="
                padding:7px 16px;border-radius:var(--r-sm);white-space:nowrap;
                border:0.5px solid var(--border2);background:var(--purple-bg);
                color:var(--purple-txt);font-size:13px;cursor:pointer;">
                ↺ 重试
              </button>
            </div>
          </div>`;
        document.getElementById("btn-retry-scrape").onclick = () => {
          const hp2 = (document.getElementById("inp-retry-homepage")?.value || "").trim();
          triggerSearchScrape(univName, hp2 || null);
        };
      }
    }
  } catch (_) {
    grid.innerHTML = `<div class="empty"><div class="scrape-status error">爬取结果查询失败，请检查后端连接</div></div>`;
  }
}

/** CSS 安全的 ID 片段（中文 → encode） */
function cssEscape(s) { return encodeURIComponent(s).replace(/%/g, "_"); }

/* ══════════════════════════════════════════════════════════
   Java 后端集成
   当 Spring Boot 运行在 localhost:8080 时，自动从 API 拉取
   动态教师数据。如果院校在数据库中无记录，则自动触发爬取。
   如果后端未启动，页面仍可完全离线使用。
══════════════════════════════════════════════════════════ */
const API_BASE = "http://localhost:8080/api";
let apiAvailable = false;
let teacherCache = {};
let statsCache   = null;
// 记录已触发过按需爬取的院校，避免重复触发
const scrapingInProgress = new Set();

/**
 * 轮询爬取结果。每 interval ms 检查一次教师数量，最多 maxAttempts 次。
 * 用 GET /api/scrape/progress/{university} 获取实时进度（已爬取数 + 是否仍在运行）。
 * count > 0 时立即成功并 resolve true；超时且 count=0 返回 false。
 *
 * 默认 8s × 23 次 ≈ 3 分钟；8s 粒度让用户更快看到"已发现 N 位"反馈。
 */
async function pollScrapeResult(univName, onUpdate,
                                { interval = 8000, maxAttempts = 23 } = {}) {
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    await new Promise(r => setTimeout(r, interval));
    delete teacherCache[univName];
    let count = 0, active = false;
    try {
      const r = await fetch(
        `${API_BASE}/scrape/progress/${encodeURIComponent(univName)}`,
        { signal: AbortSignal.timeout(5000) });
      if (r.ok) { const d = await r.json(); count = d.count || 0; active = !!d.active; }
      else {
        // fallback: progress endpoint not available (old backend) — use teacher count
        const r2 = await fetch(
          `${API_BASE}/teachers?university=${encodeURIComponent(univName)}&limit=1`,
          { signal: AbortSignal.timeout(5000) });
        if (r2.ok) { const d2 = await r2.json(); count = d2.total || 0; active = true; }
      }
    } catch (_) {}
    if (count > 0) { onUpdate(count, attempt, maxAttempts, true, active); return true; }
    onUpdate(count, attempt, maxAttempts, false, active);
    // Early-exit: if backend says done AND still 0 → no hope (not yet triggered or instant fail)
    if (!active && attempt >= 3) return false;
  }
  return false;
}

async function checkApi() {
  try {
    const r = await fetch(`${API_BASE}/teachers/stats`, { signal: AbortSignal.timeout(2000) });
    if (r.ok) {
      statsCache = await r.json();
      apiAvailable = true;
      showApiBadge(statsCache.totalTeachers);
    }
  } catch (_) {
    /* 后端未启动，静默忽略 */
  }
}

function showApiBadge(count) {
  const badge = document.createElement("div");
  badge.id = "api-badge";
  badge.style.cssText = `
    position:fixed;bottom:16px;right:16px;
    background:var(--green-bg);color:var(--green-txt);
    border:0.5px solid var(--green-mid);
    border-radius:var(--r-sm);padding:6px 12px;font-size:12px;
    z-index:200;cursor:pointer;
  `;
  badge.innerHTML = `✅ API 已连接 · 教师数据 ${count} 条`;
  badge.onclick = () => window.open(`${API_BASE}/universities`, "_blank");
  document.body.appendChild(badge);
}

async function loadTeachersForUniversity(univName) {
  if (!apiAvailable) return [];
  if (teacherCache[univName]) return teacherCache[univName];
  try {
    const r = await fetch(`${API_BASE}/teachers?university=${encodeURIComponent(univName)}&limit=100`);
    if (r.ok) {
      const data = await r.json();
      teacherCache[univName] = data.teachers || [];
      return teacherCache[univName];
    }
  } catch (_) {}
  return [];
}

/**
 * 检查某院校在数据库中是否有教师记录，若无则自动触发爬取。
 * 返回：{ hasData: bool, scrapeTriggered: bool }
 */
async function checkAndTriggerScrapeIfNeeded(univName) {
  if (!apiAvailable) return { hasData: false, scrapeTriggered: false };
  try {
    const r = await fetch(`${API_BASE}/scrape/status/${encodeURIComponent(univName)}`,
      { signal: AbortSignal.timeout(3000) });
    if (!r.ok) return { hasData: false, scrapeTriggered: false };
    const status = await r.json();
    if (status.hasData) return { hasData: true, scrapeTriggered: false };

    // 数据库中没有该院校数据，触发按需爬取
    if (!scrapingInProgress.has(univName)) {
      scrapingInProgress.add(univName);
      fetch(`${API_BASE}/scrape`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ universities: [univName] }),
      }).catch(() => {});
      return { hasData: false, scrapeTriggered: true };
    }
    return { hasData: false, scrapeTriggered: false };
  } catch (_) {
    return { hasData: false, scrapeTriggered: false };
  }
}

/** 在详情面板中插入教师列表（不自动触发爬取，改为手动按钮）*/
async function injectTeachersIntoPanel(univName) {
  if (!apiAvailable) return;

  const panelContent = document.getElementById("pc");
  const existing = document.getElementById("api-teachers-section");
  if (existing) existing.remove();

  const section = document.createElement("div");
  section.id = "api-teachers-section";
  section.className = "sec";
  section.style.marginTop = "16px";

  // 查询 DB 是否有数据
  let hasData = false, count = 0;
  try {
    const r = await fetch(`${API_BASE}/scrape/status/${encodeURIComponent(univName)}`,
      { signal: AbortSignal.timeout(3000) });
    if (r.ok) { const d = await r.json(); hasData = d.hasData; count = d.count || 0; }
  } catch (_) {}

  if (hasData) {
    // 有数据，直接展示
    const teachers = await loadTeachersForUniversity(univName);
    if (teachers.length) {
      renderTeachersInSection(section, univName, teachers);
      const btnRow = panelContent.querySelector(".btn-row");
      if (btnRow) panelContent.insertBefore(section, btnRow);
      else panelContent.appendChild(section);
    }
    return;
  }

  // 无数据：显示手动爬取按钮，不自动触发
  const isActive = scrapingInProgress.has(univName);
  section.innerHTML = `
    <div class="sec-lbl">教师数据（来自 API）</div>
    ${isActive
      ? `<div class="scrape-status running">
           <span class="spin">⟳</span>
           <span id="panel-scrape-msg">后台正在爬取「${univName}」，请稍候…</span>
         </div>`
      : `<div class="scrape-status" style="flex-direction:column;align-items:flex-start;gap:8px">
           <span style="color:var(--text2)">暂无「${univName}」教师数据</span>
           <div style="display:flex;gap:6px;flex-wrap:wrap;align-items:center">
             <input id="panel-hp-input" type="text" placeholder="可选：学院首页 URL"
               style="flex:1;min-width:180px;padding:5px 9px;font-size:12px;
                      border:0.5px solid var(--border2);border-radius:var(--r-sm);
                      background:var(--surface);color:var(--text);outline:none;">
             <button id="panel-scrape-btn" style="
               padding:6px 14px;border-radius:var(--r-sm);white-space:nowrap;
               border:0.5px solid var(--border2);background:var(--purple-bg);
               color:var(--purple-txt);font-size:12px;cursor:pointer;">
               ↓ 爬取教师数据
             </button>
           </div>
           <div style="font-size:11px;color:var(--text3)">⚠ 爬取过程在后台运行，可能需要数分钟</div>
         </div>`
    }`;

  const btnRow = panelContent.querySelector(".btn-row");
  if (btnRow) panelContent.insertBefore(section, btnRow);
  else panelContent.appendChild(section);

  if (!isActive) {
    document.getElementById("panel-scrape-btn")?.addEventListener("click", () => {
      const hp = (document.getElementById("panel-hp-input")?.value || "").trim();
      startPanelScrape(univName, hp, section);
    });
  } else {
    // 已在爬取中，继续轮询
    continuePanelPoll(univName, section);
  }
}

/** 从面板手动触发爬取 */
async function startPanelScrape(univName, homepage, section) {
  if (scrapingInProgress.has(univName)) return;
  scrapingInProgress.add(univName);

  section.innerHTML = `
    <div class="sec-lbl">教师数据（来自 API）</div>
    <div class="scrape-status running">
      <span class="spin">⟳</span>
      <span id="panel-scrape-msg">已触发爬取「${univName}」，后台运行中…</span>
    </div>`;

  const body = { universities: [univName] };
  if (homepage) body.homepage = homepage;
  try {
    await fetch(`${API_BASE}/scrape`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (_) {}

  continuePanelPoll(univName, section);
}

/** 轮询面板内进度，直到爬完或超时 */
function continuePanelPoll(univName, section) {
  pollScrapeResult(univName, async (count, attempt, max, done, active) => {
    const sec = document.getElementById("api-teachers-section");
    if (!sec) return;
    if (done) {
      scrapingInProgress.delete(univName);
      delete teacherCache[univName];
      const teachers = await loadTeachersForUniversity(univName);
      if (teachers.length > 0) renderTeachersInSection(sec, univName, teachers);
      return;
    }
    const msg = sec.querySelector("#panel-scrape-msg");
    if (msg) msg.textContent = active
      ? `爬取中（已 ${attempt * 8}s）… 暂发现 ${count} 位`
      : `已等待 ${attempt * 8}s，第 ${attempt}/${max} 次检查…`;
  }).then(found => {
    scrapingInProgress.delete(univName);
    if (!found) {
      const sec = document.getElementById("api-teachers-section");
      if (!sec) return;
      const st = sec.querySelector(".scrape-status");
      if (st) { st.className = "scrape-status error";
        st.innerHTML = `⚠ 3 分钟内未爬到「${univName}」数据，请查阅 output/scraper.log`; }
    }
  });
}

/**
 * 打开 211 / 双非 学校详情面板
 * meta = {tier, province, note}
 */
function openUnknownP(name, meta) {
  if (typeof meta === "string") { try { meta = JSON.parse(meta); } catch(_) { meta = {}; } }
  const t = ts(meta.tier || "双非");
  document.getElementById("pc").innerHTML = `
    <div style="padding-right:28px;margin-bottom:10px">
      <div style="font-size:19px;font-weight:700">${name}</div>
      <div style="font-size:11px;color:var(--text3);margin-top:2px">
        ${meta.province || ""}${meta.province ? " · " : ""}${meta.tier || "未知梯队"}
      </div>
    </div>
    <span class="tier-pill" style="background:${t.bg};color:${t.txt};font-size:12px;
          padding:3px 10px;border-radius:10px">${meta.tier || "未知"}</span>

    ${meta.note ? `<div class="sec">
      <div class="sec-lbl">备注</div>
      <div class="note-box">${meta.note}</div>
    </div>` : ""}

    <div class="sec">
      <div class="sec-lbl">强弱 com 信息</div>
      <div class="note-box" style="color:var(--text3)">
        该院校不在本项目预设的985/C9列表中，暂无强弱com数据。<br>
        可通过小红书搜索「${name} CS保研」「${name} 预推免」获取经验帖。
      </div>
    </div>

    <div class="btn-row">
      <button class="pbtn" onclick="window.open('https://www.bing.com/search?q=${encodeURIComponent("site:xiaohongshu.com "+name+"保研")}','_blank')">
        → 小红书搜保研经验
      </button>
    </div>`;
  document.getElementById("ov").classList.add("open");
  injectTeachersIntoPanel(name);
}

function renderTeachersInSection(section, univName, teachers) {
  const rows = teachers.slice(0, 30).map(t => `
    <div style="display:flex;align-items:center;gap:8px;padding:5px 0;
                border-bottom:0.5px solid var(--border);font-size:13px;">
      <span style="font-weight:500;min-width:60px">${t.name || ""}</span>
      <span style="color:var(--text3);font-size:11px;min-width:50px">${t.title || ""}</span>
      <span style="color:var(--text2);font-size:11px;flex:1">${(t.researchAreas || "").slice(0, 40)}</span>
      ${t.profileUrl ? `<a href="${t.profileUrl}" target="_blank"
          style="font-size:11px;color:var(--blue-txt);white-space:nowrap">↗ 主页</a>` : ""}
    </div>
  `).join("");

  section.innerHTML = `
    <div class="sec-lbl">已爬取教师（来自 API · ${teachers.length} 位）</div>
    <div class="scrape-status done" style="margin-bottom:8px">✅ 数据已就绪</div>
    ${rows}
    ${teachers.length > 30 ? `<div style="font-size:11px;color:var(--text3);margin-top:6px">
      还有 ${teachers.length - 30} 位，访问
      <a href="${API_BASE}/teachers?university=${encodeURIComponent(univName)}"
         target="_blank" style="color:var(--blue-txt)">API</a> 查看全部</div>` : ""}
  `;
}

/* openP — 打开院校详情面板并注入教师数据 */
function openP(u) {
  _openPBase(u);
  injectTeachersIntoPanel(u.name);
}