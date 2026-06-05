const UNIS=[
  {rank:1,name:"清华大学",province:"北京",tier:"顶尖C9",
   csUrl:"https://www.cs.tsinghua.edu.cn",admUrl:"https://yz.tsinghua.edu.cn",
   depts:[
     {n:"计算机科学与技术系",c:"strong"},
     {n:"软件学院",c:"strong"},
     {n:"交叉信息研究院(IIIS)",c:"weak"},
     {n:"深圳国际研究生院",c:"weak"},
     {n:"智能网络研究中心",c:"weak"},
     {n:"自动化系",c:"mix_ds"},
     {n:"电机系",c:"weak"},
     {n:"生物医学工程系",c:"weak"},
   ],
   note:"计算机系和软件学院是强com；IIIS（YAO班）弱com；自动化系博士弱硕士强",
   xhs:["清华计算机保研","THU CS夏令营","清华IIIS保研"]},

  {rank:2,name:"北京大学",province:"北京",tier:"顶尖C9",
   csUrl:"https://cs.pku.edu.cn",admUrl:"https://admission.pku.edu.cn",
   depts:[
     {n:"计算机学院",c:"weak"},
     {n:"软件与微电子学院",c:"strong"},
     {n:"智能学院",c:"weak"},
     {n:"电子学院",c:"strong"},
     {n:"深圳研究生院",c:"weak"},
     {n:"前沿交叉学科研究院",c:"strong"},
     {n:"未来技术学院",c:"weak"},
     {n:"医学技术学院",c:"weak"},
     {n:"工学院",c:"weak"},
   ],
   note:"计算机学院弱com；软件与微电子、电子学院、前沿交叉研究院强com",
   xhs:["北大计算机保研","PKU夏令营","北大信科保研"]},

  {rank:3,name:"浙江大学",province:"浙江",tier:"顶尖C9",
   csUrl:"https://www.cs.zju.edu.cn",admUrl:"https://grs.zju.edu.cn",
   depts:[
     {n:"计算机科学与技术学院",c:"mix_ds"},
     {n:"软件学院",c:"weak"},
     {n:"控制科学与工程学院",c:"weak"},
     {n:"电气工程学院",c:"strong"},
     {n:"工程师学院",c:"strong"},
     {n:"生物医学工程与仪器科学学院",c:"strong"},
   ],
   note:"计算机学院博士弱+硕士强；软件学院弱com；电气、工程师、生医工强com",
   xhs:["浙大计算机保研","ZJU夏令营"]},

  {rank:4,name:"上海交通大学",province:"上海",tier:"顶尖C9",
   csUrl:"https://www.cs.sjtu.edu.cn",admUrl:"https://www.gs.sjtu.edu.cn",
   depts:[
     {n:"电子信息与电气工程学院",c:"weak"},
     {n:"博渊未来技术学院",c:"weak"},
     {n:"高级金融学院-计算机方向",c:"strong"},
     {n:"智慧能源创新学院",c:"strong"},
   ],
   note:"电院（CS主系）弱com；高金计算机方向、智慧能源学院强com",
   xhs:["交大计算机保研","SJTU夏令营","交大ACM班"]},

  {rank:5,name:"复旦大学",province:"上海",tier:"顶尖C9",
   csUrl:"https://cs.fudan.edu.cn",admUrl:"https://gs.fudan.edu.cn",
   depts:[
     {n:"计算机科学技术学院",c:"strong"},
     {n:"信息科学与工程学院",c:"weak"},
     {n:"工程与应用技术研究院",c:"strong"},
     {n:"航空航天学院",c:"weak"},
     {n:"数字医学中心",c:"strong"},
     {n:"软件学院",c:"strong"},
   ],
   note:"计算机、软件、工程研究院、数字医学中心均强com",
   xhs:["复旦计算机保研","Fudan夏令营"]},

  {rank:6,name:"南京大学",province:"江苏",tier:"顶尖C9",
   csUrl:"https://cs.nju.edu.cn",admUrl:"https://grawww.nju.edu.cn",
   depts:[
     {n:"计算机学院",c:"strong"},
     {n:"软件学院",c:"strong"},
     {n:"人工智能学院",c:"strong"},
     {n:"电子学院",c:"strong"},
   ],
   note:"四个院系全部强com，顶尖985里最开放之一",
   xhs:["南大计算机保研","NJU夏令营"]},

  {rank:7,name:"中国科学技术大学",province:"安徽",tier:"顶尖C9",
   csUrl:"https://cs.ustc.edu.cn",admUrl:"https://gradschool.ustc.edu.cn",
   depts:[
     {n:"计算机科学与技术学院",c:"weak"},
     {n:"信息与科学工程学院",c:"strong"},
     {n:"六系（信息学部）",c:"strong"},
     {n:"大数据学院",c:"strong"},
     {n:"先进技术研究院",c:"weak"},
   ],
   note:"计算机学院弱com；信科院、六系、大数据学院强com",
   xhs:["科大计算机保研","USTC夏令营","科大直博"]},

  {rank:7,name:"国防科技大学",province:"湖南",tier:"顶尖C9",
   csUrl:"https://www.nudt.edu.cn",admUrl:"https://www.nudt.edu.cn",
   depts:[{n:"计算机学院",c:"weak"}],
   note:"军校，主要招军籍生，对外名额极少，强弱com图中未详细收录",
   xhs:["国防科大保研"]},

  {rank:8,name:"华中科技大学",province:"湖北",tier:"上游985",
   csUrl:"http://cs.hust.edu.cn",admUrl:"https://gs.hust.edu.cn",
   depts:[
     {n:"计算机学院",c:"weak"},
     {n:"网络空间安全学院",c:"weak"},
     {n:"人工智能学院",c:"weak"},
     {n:"电子与电气工程学院",c:"weak"},
     {n:"光学与电子信息学院",c:"strong"},
     {n:"武汉光电国家研究中心",c:"weak"},
     {n:"机械科学与工程学院",c:"weak"},
   ],
   note:"计算机学院弱com；光学与电子信息学院强com",
   xhs:["华科计算机保研","HUST夏令营"]},

  {rank:9,name:"武汉大学",province:"湖北",tier:"上游985",
   csUrl:"https://cs.whu.edu.cn",admUrl:"https://gs.whu.edu.cn",
   depts:[
     {n:"计算机学院",c:"strong"},
     {n:"信息学院",c:"strong"},
     {n:"国家网络安全学院",c:"strong"},
     {n:"测绘遥感信息工程国家重点实验室",c:"weak"},
     {n:"电气与自动化学院",c:"strong"},
   ],
   note:"计算机、信息、网安、电气四院系强com；测绘遥感实验室弱com",
   xhs:["武大计算机保研","WHU网安夏令营"]},

  {rank:10,name:"西安交通大学",province:"陕西",tier:"上游985",
   csUrl:"http://www.cs.xjtu.edu.cn",admUrl:"https://gs.xjtu.edu.cn",
   depts:[
     {n:"计算机学院",c:"weak"},
     {n:"网络空间安全学院",c:"weak"},
     {n:"人工智能与机器人所",c:"strong"},
     {n:"电子与信息学部",c:"weak"},
     {n:"电气工程学院",c:"weak"},
     {n:"微电子学院",c:"weak"},
     {n:"未来技术学院",c:"strong"},
     {n:"钱学森学院",c:"weak"},
   ],
   note:"计算机学院弱com；AI与机器人所、未来技术学院强com",
   xhs:["西交大计算机保研","XJTU夏令营"]},

  {rank:11,name:"中山大学",province:"广东",tier:"上游985",
   csUrl:"https://cse.sysu.edu.cn",admUrl:"https://graduate.sysu.edu.cn",
   depts:[
     {n:"数据科学与计算机学院",c:"strong"},
     {n:"航空航天学院",c:"strong"},
   ],
   note:"图中两个院系均强com",
   xhs:["中大计算机保研","SYSU夏令营"]},

  {rank:12,name:"北京航空航天大学",province:"北京",tier:"上游985",
   csUrl:"https://scse.buaa.edu.cn",admUrl:"https://ev.buaa.edu.cn",
   depts:[
     {n:"软件学院",c:"strong"},
     {n:"人工智能学院",c:"strong"},
   ],
   note:"软件学院和AI学院均强com（图中仅收录这两个）",
   xhs:["北航计算机保研","BUAA夏令营"]},

  {rank:13,name:"东南大学",province:"江苏",tier:"上游985",
   csUrl:"https://cse.seu.edu.cn",admUrl:"https://gs.seu.edu.cn",
   depts:[
     {n:"计算机科学与工程学院",c:"weak"},
     {n:"信息科学与工程学院",c:"strong"},
     {n:"软件学院",c:"weak"},
     {n:"人工智能学院",c:"strong"},
     {n:"网络空间安全学院",c:"strong"},
     {n:"电子科学与工程学院",c:"strong"},
     {n:"集成电路学院",c:"strong"},
     {n:"电气工程学院",c:"strong"},
     {n:"自动化学院",c:"strong"},
   ],
   note:"计算机学院、软件学院弱com；其余七个院系均强com，整体开放",
   xhs:["东南大学计算机保研","SEU夏令营"]},

  {rank:14,name:"北京理工大学",province:"北京",tier:"上游985",
   csUrl:"https://cs.bit.edu.cn",admUrl:"https://gs.bit.edu.cn",
   depts:[{n:"计算机学院",c:"weak"}],
   note:"图中仅收录计算机学院，弱com",
   xhs:["北理工计算机保研","BIT夏令营"]},

  {rank:15,name:"四川大学",province:"四川",tier:"上游985",
   csUrl:"https://cs.scu.edu.cn",admUrl:"https://gs.scu.edu.cn",
   depts:[
     {n:"计算机学院",c:"strong"},
     {n:"网络空间安全学院",c:"strong"},
     {n:"电子信息学院",c:"strong"},
     {n:"电气工程学院",c:"strong"},
   ],
   note:"图中四个院系全部强com",
   xhs:["川大计算机保研","SCU夏令营"]},

  {rank:16,name:"哈尔滨工业大学",province:"黑龙江",tier:"上游985",
   csUrl:"https://cs.hit.edu.cn",admUrl:"https://gs.hit.edu.cn",
   depts:[
     {n:"计算机学部",c:"strong"},
     {n:"电气工程及自动化学院",c:"strong"},
   ],
   note:"两个院系均强com；深圳校区单独招生可单独查询",
   xhs:["哈工大计算机保研","HIT夏令营","哈工大深圳保研"]},

  {rank:17,name:"同济大学",province:"上海",tier:"中游985",
   csUrl:"https://cs.tongji.edu.cn",admUrl:"https://yjsc.tongji.edu.cn",
   depts:[
     {n:"电子与信息工程学院计算机系",c:"strong"},
     {n:"软件学院",c:"strong"},
   ],
   note:"两个院系均强com",
   xhs:["同济计算机保研","Tongji夏令营"]},

  {rank:18,name:"中国人民大学",province:"北京",tier:"中游985",
   csUrl:"https://info.ruc.edu.cn",admUrl:"https://pgs.ruc.edu.cn",
   depts:[],
   note:"图中未收录，请查阅官网",
   xhs:["人大信息保研","RUC夏令营"]},

  {rank:19,name:"北京师范大学",province:"北京",tier:"中游985",
   csUrl:"https://ai.bnu.edu.cn",admUrl:"https://yjsy.bnu.edu.cn",
   depts:[],
   note:"图中未收录，请查阅官网",
   xhs:["北师大AI保研"]},

  {rank:20,name:"天津大学",province:"天津",tier:"中游985",
   csUrl:"https://cic.tju.edu.cn",admUrl:"https://gs.tju.edu.cn",
   depts:[
     {n:"智能与计算学部",c:"strong"},
     {n:"电气工程学院",c:"weak"},
   ],
   note:"智能与计算学部强com；电气工程学院弱com",
   xhs:["天大计算机保研","TJU夏令营"]},

  {rank:21,name:"南开大学",province:"天津",tier:"中游985",
   csUrl:"https://cc.nankai.edu.cn",admUrl:"https://graduate.nankai.edu.cn",
   depts:[
     {n:"计算机学院",c:"weak"},
     {n:"软件学院",c:"weak"},
   ],
   note:"计算机学院和软件学院均弱com",
   xhs:["南开计算机保研","NKU夏令营"]},

  {rank:22,name:"山东大学",province:"山东",tier:"中游985",
   csUrl:"https://www.cs.sdu.edu.cn",admUrl:"https://www.grad.sdu.edu.cn",
   depts:[
     {n:"计算机科学与技术学院",c:"strong"},
     {n:"软件学院",c:"strong"},
     {n:"网络空间安全学院",c:"strong"},
     {n:"电气工程学院",c:"strong"},
   ],
   note:"图中四个院系全部强com",
   xhs:["山大计算机保研","SDU夏令营"]},

  {rank:23,name:"西北工业大学",province:"陕西",tier:"中游985",
   csUrl:"https://jsj.nwpu.edu.cn",admUrl:"https://gs.nwpu.edu.cn",
   depts:[
     {n:"计算机学部",c:"weak"},
     {n:"网络空间安全学院",c:"weak"},
     {n:"软件学院",c:"weak"},
     {n:"电子信息学部",c:"strong"},
   ],
   note:"计算机、网安、软件均弱com；电子信息学部强com",
   xhs:["西工大计算机保研","NPU夏令营"]},

  {rank:24,name:"厦门大学",province:"福建",tier:"中游985",
   csUrl:"https://cs.xmu.edu.cn",admUrl:"https://gs.xmu.edu.cn",
   depts:[
     {n:"计算机学院",c:"strong"},
     {n:"信息学院",c:"weak"},
   ],
   note:"计算机学院强com；信息学院弱com",
   xhs:["厦大计算机保研","XMU夏令营"]},

  {rank:25,name:"中南大学",province:"湖南",tier:"中游985",
   csUrl:"https://cse.csu.edu.cn",admUrl:"https://graduate.csu.edu.cn",
   depts:[
     {n:"计算机学院",c:"strong"},
     {n:"软件学院",c:"strong"},
   ],
   note:"两个院系均强com",
   xhs:["中南大学计算机保研","CSU夏令营"]},

  {rank:26,name:"吉林大学",province:"吉林",tier:"中游985",
   csUrl:"https://ccccu.jlu.edu.cn",admUrl:"https://yjsc.jlu.edu.cn",
   depts:[],note:"图中未收录，请查阅官网",xhs:["吉大计算机保研"]},

  {rank:27,name:"中国农业大学",province:"北京",tier:"中游985",
   csUrl:"https://ciee.cau.edu.cn",admUrl:"https://graduate.cau.edu.cn",
   depts:[],note:"图中未收录，请查阅官网",xhs:["农大计算机保研"]},

  {rank:28,name:"大连理工大学",province:"辽宁",tier:"中游985",
   csUrl:"https://cs.dlut.edu.cn",admUrl:"https://gs.dlut.edu.cn",
   depts:[{n:"电子信息与电气工程学部",c:"weak"}],
   note:"图中仅收录电子信息学部，弱com",
   xhs:["大工计算机保研","DUT夏令营"]},

  {rank:29,name:"华东师范大学",province:"上海",tier:"中游985",
   csUrl:"https://cs.ecnu.edu.cn",admUrl:"https://yjsy.ecnu.edu.cn",
   depts:[
     {n:"计算机学院",c:"strong"},
     {n:"数据科学学院",c:"strong"},
   ],
   note:"两个院系均强com",
   xhs:["华师大计算机保研","ECNU夏令营"]},

  {rank:30,name:"华南理工大学",province:"广东",tier:"中下游985",
   csUrl:"https://www2.scut.edu.cn/cs",admUrl:"https://www2.scut.edu.cn/graduate",
   depts:[{n:"计算机科学与工程学院",c:"strong"}],
   note:"计算机学院强com（图中仅收录此院系）",
   xhs:["华工计算机保研","SCUT夏令营"]},

  {rank:31,name:"电子科技大学",province:"四川",tier:"中下游985",
   csUrl:"https://www.scse.uestc.edu.cn",admUrl:"https://www.uestc.edu.cn/graduate",
   depts:[
     {n:"计算机科学与工程学院",c:"weak"},
     {n:"软件学院",c:"weak"},
     {n:"信息与通信工程学院",c:"weak"},
     {n:"电子工程学院",c:"weak"},
     {n:"集成电路工程学院",c:"weak"},
     {n:"机械与电气工程学院",c:"weak"},
     {n:"深圳研究院",c:"weak"},
   ],
   note:"图中所有院系均弱com",
   xhs:["成电计算机保研","UESTC夏令营"]},

  {rank:32,name:"湖南大学",province:"湖南",tier:"中下游985",
   csUrl:"https://csee.hnu.edu.cn",admUrl:"https://graduate.hnu.edu.cn",
   depts:[
     {n:"控制科学与工程学院",c:"weak"},
     {n:"信息科学与工程学院",c:"strong"},
     {n:"电气信息工程学院",c:"strong"},
   ],
   note:"信息学院和电气信息学院强com；控制学院弱com",
   xhs:["湖南大学计算机保研"]},

  {rank:33,name:"重庆大学",province:"重庆",tier:"中下游985",
   csUrl:"https://cs.cqu.edu.cn",admUrl:"https://graduate.cqu.edu.cn",
   depts:[
     {n:"计算机科学与技术学院",c:"weak"},
     {n:"电气工程学院",c:"weak"},
   ],
   note:"图中两个院系均弱com",
   xhs:["重大计算机保研"]},

  {rank:38,name:"兰州大学",province:"甘肃",tier:"末流985",
   csUrl:"https://xxxy.lzu.edu.cn",admUrl:"https://yjs.lzu.edu.cn",
   depts:[],note:"图中未收录，请查阅官网",xhs:["兰大计算机保研"]},

  {rank:39,name:"东北大学",province:"辽宁",tier:"末流985",
   csUrl:"https://cse.neu.edu.cn",admUrl:"https://gs.neu.edu.cn",
   depts:[],note:"图中未收录，请查阅官网",xhs:["东北大学计算机保研"]},

  {rank:57,name:"中国海洋大学",province:"山东",tier:"末流985",
   csUrl:"https://it.ouc.edu.cn",admUrl:"https://yjsy.ouc.edu.cn",
   depts:[],note:"图中未收录，请查阅官网",xhs:["海大计算机保研"]},

  {rank:73,name:"西北农林科技大学",province:"陕西",tier:"末流985",
   csUrl:"https://cie.nwafu.edu.cn",admUrl:"https://yjsc.nwafu.edu.cn",
   depts:[],note:"图中未收录，请查阅官网",xhs:["西农计算机保研"]},

  {rank:103,name:"中央民族大学",province:"北京",tier:"末流985",
   csUrl:"https://ie.muc.edu.cn",admUrl:"https://yjs.muc.edu.cn",
   depts:[],note:"图中未收录，请查阅官网",xhs:["民大计算机保研"]},

  /* 图中有的院外机构 */
  {rank:900,name:"中国科学院",province:"北京",tier:"中科院",
   csUrl:"http://www.ict.ac.cn",admUrl:"https://www.ucas.ac.cn",
   depts:[
     {n:"计算技术研究所",c:"weak"},
     {n:"自动化所",c:"cas_mix"},
     {n:"信工所",c:"weak"},
     {n:"软件所",c:"strong"},
   ],
   note:"各所独立招生；自动化所硕士强+博士弱；软件所强com；计算所弱com",
   xhs:["中科院计算所保研","中科院软件所保研","中科院直博"]},

  {rank:901,name:"北京邮电大学",province:"北京",tier:"中游985",
   csUrl:"https://cs.bupt.edu.cn",admUrl:"https://yjszs.bupt.edu.cn",
   depts:[
     {n:"计算机学院",c:"weak"},
     {n:"人工智能学院",c:"weak"},
     {n:"信息与通信工程学院",c:"weak"},
     {n:"现代邮政学院",c:"weak"},
   ],
   note:"图中所有院系均弱com",
   xhs:["北邮计算机保研","BUPT夏令营"]},

  {rank:902,name:"西安电子科技大学",province:"陕西",tier:"中游985",
   csUrl:"https://scs.xidian.edu.cn",admUrl:"https://gr.xidian.edu.cn",
   depts:[{n:"网络与信息安全学院",c:"weak"}],
   note:"图中仅收录网安学院，弱com",
   xhs:["西电计算机保研"]},

  {rank:903,name:"北京交通大学",province:"北京",tier:"中游985",
   csUrl:"https://scit.bjtu.edu.cn",admUrl:"https://gs.bjtu.edu.cn",
   depts:[{n:"计算机与信息技术学院",c:"weak"}],
   note:"图中仅收录计信学院，弱com",
   xhs:["北交大计算机保研"]},
];

const TM={
  "顶尖C9":   {bg:"var(--purple-bg)",txt:"var(--purple-txt)",dot:"var(--purple-mid)"},
  "上游985":  {bg:"var(--blue-bg)",  txt:"var(--blue-txt)",  dot:"var(--blue-mid)"},
  "中游985":  {bg:"var(--green-bg)", txt:"var(--green-txt)", dot:"var(--green-mid)"},
  "中下游985":{bg:"var(--amber-bg)", txt:"var(--amber-txt)", dot:"var(--amber-mid)"},
  "末流985":  {bg:"var(--coral-bg)", txt:"var(--coral-txt)", dot:"var(--coral-mid)"},
  "中科院":   {bg:"var(--surface2)", txt:"var(--text2)",     dot:"#888"},
};
const ts=t=>TM[t]||TM["末流985"];

function comInfo(c){
  if(c==="strong")  return{badge:"badge-strong",label:"强",preview:"dp-strong"};
  if(c==="weak")    return{badge:"badge-weak",  label:"弱",preview:"dp-weak"};
  if(c==="mix_ds")  return{badge:"badge-mix",   label:"博弱+硕强",preview:"dp-mix"};
  if(c==="mix_sd")  return{badge:"badge-mix",   label:"博强+硕弱",preview:"dp-mix"};
  if(c==="cas_mix") return{badge:"badge-mix",   label:"硕强+博弱",preview:"dp-mix"};
  return{badge:"badge-weak",label:"?",preview:"dp-weak"};
}

let activeTier="all", activeProv="all";

function buildSidebar(){
  const provs=[...new Set(UNIS.filter(u=>u.rank<900).map(u=>u.province))].sort();
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

function render(){
  const q=document.getElementById("sb").value.trim().toLowerCase();
  const cf=document.getElementById("cf").value;
  const grid=document.getElementById("grid");
  grid.innerHTML="";

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

  if(!filtered.length){
    const q=document.getElementById("sb").value.trim();
    const isNameSearch = q.length >= 2 && activeTier==="all" && activeProv==="all";
    grid.innerHTML=`
      <div class="empty" id="empty-msg">
        没有符合条件的院校
        ${isNameSearch
          ? `<div style="margin-top:10px;font-size:13px;color:var(--text2)">
              「${q}」不在预设列表中<br>
              <div style="display:flex;gap:6px;margin-top:8px;align-items:center;flex-wrap:wrap;">
                <input id="inp-homepage" type="text" placeholder="可选：输入官网地址 https://www.xxx.edu.cn"
                  style="flex:1;min-width:220px;padding:6px 10px;border:0.5px solid var(--border2);
                         border-radius:var(--r-sm);background:var(--surface);color:var(--text);font-size:12px;outline:none;">
                <button id="btn-scrape-search" style="
                  padding:7px 16px;border-radius:var(--r-sm);white-space:nowrap;
                  border:0.5px solid var(--border2);background:var(--purple-bg);
                  color:var(--purple-txt);font-size:13px;cursor:pointer;
                ">↓ 爬取教师数据</button>
              </div>
              ${!apiAvailable
                ? `<div style="margin-top:6px;font-size:11px;color:var(--text3)">⚠ 后端未连接，请先启动 Spring Boot</div>`
                : ""}
            </div>`
          : ""}
      </div>`;
    const btn = document.getElementById("btn-scrape-search");
    if (btn) btn.onclick = () => {
      const hp = (document.getElementById("inp-homepage")?.value || "").trim();
      triggerSearchScrape(q, hp);
    };
    return;
  }

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
}

function openP(u){
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

buildSidebar();
render();

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

/** 在详情面板中插入从 API 获取的教师列表，并处理按需爬取状态 */
async function injectTeachersIntoPanel(univName) {
  if (!apiAvailable) return;

  const panelContent = document.getElementById("pc");

  // 先检查数据库是否有数据，若无则触发爬取
  const { hasData, scrapeTriggered } = await checkAndTriggerScrapeIfNeeded(univName);

  const existing = document.getElementById("api-teachers-section");
  if (existing) existing.remove();

  const section = document.createElement("div");
  section.id = "api-teachers-section";
  section.className = "sec";
  section.style.marginTop = "16px";

  if (scrapeTriggered) {
    // 刚触发了爬取，显示正在爬取的提示，并通过轮询逐步刷新
    section.innerHTML = `
      <div class="sec-lbl">教师数据（来自 API）</div>
      <div class="scrape-status running">
        <span class="spin">⟳</span>
        <span id="panel-scrape-msg">数据库中暂无「${univName}」教师数据，已自动触发爬取，每 12 秒检查一次（最多 3 分钟）…</span>
      </div>
    `;
    const btnRow = panelContent.querySelector(".btn-row");
    if (btnRow) panelContent.insertBefore(section, btnRow);
    else panelContent.appendChild(section);

    // 轮询：每 8s 检查一次，最多 23 次（~3 分钟）
    pollScrapeResult(univName, async (count, attempt, max, done, active) => {
      const sec = document.getElementById("api-teachers-section");
      if (!sec) return;  // 用户关闭了面板
      if (done) {
        scrapingInProgress.delete(univName);
        delete teacherCache[univName];
        const teachers = await loadTeachersForUniversity(univName);
        if (teachers.length > 0) renderTeachersInSection(sec, univName, teachers);
        return;
      }
      const msg = sec.querySelector("#panel-scrape-msg");
      if (msg) {
        const elapsed = attempt * 8;
        msg.textContent = active
          ? `爬取进行中（已 ${elapsed}s）…暂发现 ${count} 位「${univName}」教师。`
          : `已等待 ${elapsed}s，第 ${attempt}/${max} 次检查，暂无数据。`;
      }
    }).then(found => {
      scrapingInProgress.delete(univName);
      if (!found) {
        const sec = document.getElementById("api-teachers-section");
        if (!sec) return;
        const status = sec.querySelector(".scrape-status");
        if (status) {
          status.className = "scrape-status error";
          status.innerHTML =
            `⚠ 3 分钟内未能爬到「${univName}」数据，可能官网结构无法自动解析，请查阅 output/scraper.log`;
        }
      }
    });
    return;
  }

  if (!hasData) {
    // API 可用但该院校确实没有数据，且未触发爬取（说明已触发过）
    section.innerHTML = `
      <div class="sec-lbl">教师数据（来自 API）</div>
      <div class="scrape-status">
        暂无教师数据，可通过后端 POST /api/scrape 手动触发爬取
      </div>
    `;
    const btnRow = panelContent.querySelector(".btn-row");
    if (btnRow) panelContent.insertBefore(section, btnRow);
    else panelContent.appendChild(section);
    return;
  }

  const teachers = await loadTeachersForUniversity(univName);
  if (!teachers.length) return;

  renderTeachersInSection(section, univName, teachers);
  const btnRow = panelContent.querySelector(".btn-row");
  if (btnRow) panelContent.insertBefore(section, btnRow);
  else panelContent.appendChild(section);
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

/* 覆写 openP，在打开面板后异步注入教师数据 */
const _origOpenP = openP;
function openP(u) {
  _origOpenP(u);
  injectTeachersIntoPanel(u.name);
}

/* 启动时检测 API */
checkApi();