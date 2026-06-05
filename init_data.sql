-- init_data.sql — 院校静态数据（由 data.js 生成）
-- 运行: sqlite3 output/baoyan.db < init_data.sql
-- 或由 BaoyanApp 首次启动时自动导入

-- ════════════════════════════════════════════════════════════
-- 建表
-- ════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS universities (
    name     TEXT PRIMARY KEY,
    rank     INTEGER DEFAULT 999,
    province TEXT,
    tier     TEXT NOT NULL,
    cs_url   TEXT DEFAULT '',
    adm_url  TEXT DEFAULT '',
    note     TEXT DEFAULT '',
    xhs      TEXT DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS departments (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    univ_name  TEXT NOT NULL,
    dept_name  TEXT NOT NULL,
    com_status TEXT DEFAULT 'unknown',
    UNIQUE(univ_name, dept_name),
    FOREIGN KEY (univ_name) REFERENCES universities(name)
);

-- ════════════════════════════════════════════════════════════
-- 985 院校（含 com 数据）
-- ════════════════════════════════════════════════════════════

INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('清华大学',1,'北京','顶尖C9','https://www.cs.tsinghua.edu.cn','https://yz.tsinghua.edu.cn','计算机系和软件学院是强com；IIIS（YAO班）弱com；自动化系博士弱硕士强','["清华计算机保研","THU CS夏令营","清华IIIS保研"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('清华大学','计算机科学与技术系','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('清华大学','软件学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('清华大学','交叉信息研究院(IIIS)','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('清华大学','深圳国际研究生院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('清华大学','智能网络研究中心','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('清华大学','自动化系','mix_ds');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('清华大学','电机系','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('清华大学','生物医学工程系','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('北京大学',2,'北京','顶尖C9','https://cs.pku.edu.cn','https://admission.pku.edu.cn','计算机学院弱com；软件与微电子、电子学院、前沿交叉研究院强com','["北大计算机保研","PKU夏令营","北大信科保研"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京大学','计算机学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京大学','软件与微电子学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京大学','智能学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京大学','电子学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京大学','深圳研究生院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京大学','前沿交叉学科研究院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京大学','未来技术学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京大学','医学技术学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京大学','工学院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('浙江大学',3,'浙江','顶尖C9','https://www.cs.zju.edu.cn','https://grs.zju.edu.cn','计算机学院博士弱+硕士强；软件学院弱com；电气、工程师、生医工强com','["浙大计算机保研","ZJU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('浙江大学','计算机科学与技术学院','mix_ds');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('浙江大学','软件学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('浙江大学','控制科学与工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('浙江大学','电气工程学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('浙江大学','工程师学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('浙江大学','生物医学工程与仪器科学学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('上海交通大学',4,'上海','顶尖C9','https://www.cs.sjtu.edu.cn','https://www.gs.sjtu.edu.cn','电院（CS主系）弱com；高金计算机方向、智慧能源学院强com','["交大计算机保研","SJTU夏令营","交大ACM班"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('上海交通大学','电子信息与电气工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('上海交通大学','博渊未来技术学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('上海交通大学','高级金融学院-计算机方向','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('上海交通大学','智慧能源创新学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('复旦大学',5,'上海','顶尖C9','https://cs.fudan.edu.cn','https://gs.fudan.edu.cn','计算机、软件、工程研究院、数字医学中心均强com','["复旦计算机保研","Fudan夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('复旦大学','计算机科学技术学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('复旦大学','信息科学与工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('复旦大学','工程与应用技术研究院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('复旦大学','航空航天学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('复旦大学','数字医学中心','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('复旦大学','软件学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('南京大学',6,'江苏','顶尖C9','https://cs.nju.edu.cn','https://grawww.nju.edu.cn','四个院系全部强com，顶尖985里最开放之一','["南大计算机保研","NJU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('南京大学','计算机学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('南京大学','软件学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('南京大学','人工智能学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('南京大学','电子学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('中国科学技术大学',7,'安徽','顶尖C9','https://cs.ustc.edu.cn','https://gradschool.ustc.edu.cn','计算机学院弱com；信科院、六系、大数据学院强com','["科大计算机保研","USTC夏令营","科大直博"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中国科学技术大学','计算机科学与技术学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中国科学技术大学','信息与科学工程学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中国科学技术大学','六系（信息学部）','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中国科学技术大学','大数据学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中国科学技术大学','先进技术研究院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('国防科技大学',7,'湖南','顶尖C9','https://www.nudt.edu.cn','https://www.nudt.edu.cn','军校，主要招军籍生，对外名额极少，强弱com图中未详细收录','["国防科大保研"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('国防科技大学','计算机学院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('华中科技大学',8,'湖北','上游985','http://cs.hust.edu.cn','https://gs.hust.edu.cn','计算机学院弱com；光学与电子信息学院强com','["华科计算机保研","HUST夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('华中科技大学','计算机学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('华中科技大学','网络空间安全学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('华中科技大学','人工智能学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('华中科技大学','电子与电气工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('华中科技大学','光学与电子信息学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('华中科技大学','武汉光电国家研究中心','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('华中科技大学','机械科学与工程学院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('武汉大学',9,'湖北','上游985','https://cs.whu.edu.cn','https://gs.whu.edu.cn','计算机、信息、网安、电气四院系强com；测绘遥感实验室弱com','["武大计算机保研","WHU网安夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('武汉大学','计算机学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('武汉大学','信息学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('武汉大学','国家网络安全学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('武汉大学','测绘遥感信息工程国家重点实验室','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('武汉大学','电气与自动化学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('西安交通大学',10,'陕西','上游985','http://www.cs.xjtu.edu.cn','https://gs.xjtu.edu.cn','计算机学院弱com；AI与机器人所、未来技术学院强com','["西交大计算机保研","XJTU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西安交通大学','计算机学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西安交通大学','网络空间安全学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西安交通大学','人工智能与机器人所','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西安交通大学','电子与信息学部','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西安交通大学','电气工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西安交通大学','微电子学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西安交通大学','未来技术学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西安交通大学','钱学森学院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('中山大学',11,'广东','上游985','https://cse.sysu.edu.cn','https://graduate.sysu.edu.cn','图中两个院系均强com','["中大计算机保研","SYSU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中山大学','数据科学与计算机学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中山大学','航空航天学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('北京航空航天大学',12,'北京','上游985','https://scse.buaa.edu.cn','https://ev.buaa.edu.cn','软件学院和AI学院均强com（图中仅收录这两个）','["北航计算机保研","BUAA夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京航空航天大学','软件学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京航空航天大学','人工智能学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('东南大学',13,'江苏','上游985','https://cse.seu.edu.cn','https://gs.seu.edu.cn','计算机学院、软件学院弱com；其余七个院系均强com，整体开放','["东南大学计算机保研","SEU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('东南大学','计算机科学与工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('东南大学','信息科学与工程学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('东南大学','软件学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('东南大学','人工智能学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('东南大学','网络空间安全学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('东南大学','电子科学与工程学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('东南大学','集成电路学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('东南大学','电气工程学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('东南大学','自动化学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('北京理工大学',14,'北京','上游985','https://cs.bit.edu.cn','https://gs.bit.edu.cn','图中仅收录计算机学院，弱com','["北理工计算机保研","BIT夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京理工大学','计算机学院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('四川大学',15,'四川','上游985','https://cs.scu.edu.cn','https://gs.scu.edu.cn','图中四个院系全部强com','["川大计算机保研","SCU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('四川大学','计算机学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('四川大学','网络空间安全学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('四川大学','电子信息学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('四川大学','电气工程学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('哈尔滨工业大学',16,'黑龙江','上游985','https://cs.hit.edu.cn','https://gs.hit.edu.cn','两个院系均强com；深圳校区单独招生可单独查询','["哈工大计算机保研","HIT夏令营","哈工大深圳保研"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('哈尔滨工业大学','计算机学部','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('哈尔滨工业大学','电气工程及自动化学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('同济大学',17,'上海','中游985','https://cs.tongji.edu.cn','https://yjsc.tongji.edu.cn','两个院系均强com','["同济计算机保研","Tongji夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('同济大学','电子与信息工程学院计算机系','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('同济大学','软件学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('中国人民大学',18,'北京','中游985','https://info.ruc.edu.cn','https://pgs.ruc.edu.cn','图中未收录，请查阅官网','["人大信息保研","RUC夏令营"]');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('北京师范大学',19,'北京','中游985','https://ai.bnu.edu.cn','https://yjsy.bnu.edu.cn','图中未收录，请查阅官网','["北师大AI保研"]');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('天津大学',20,'天津','中游985','https://cic.tju.edu.cn','https://gs.tju.edu.cn','智能与计算学部强com；电气工程学院弱com','["天大计算机保研","TJU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('天津大学','智能与计算学部','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('天津大学','电气工程学院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('南开大学',21,'天津','中游985','https://cc.nankai.edu.cn','https://graduate.nankai.edu.cn','计算机学院和软件学院均弱com','["南开计算机保研","NKU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('南开大学','计算机学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('南开大学','软件学院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('山东大学',22,'山东','中游985','https://www.cs.sdu.edu.cn','https://www.grad.sdu.edu.cn','图中四个院系全部强com','["山大计算机保研","SDU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('山东大学','计算机科学与技术学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('山东大学','软件学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('山东大学','网络空间安全学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('山东大学','电气工程学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('西北工业大学',23,'陕西','中游985','https://jsj.nwpu.edu.cn','https://gs.nwpu.edu.cn','计算机、网安、软件均弱com；电子信息学部强com','["西工大计算机保研","NPU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西北工业大学','计算机学部','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西北工业大学','网络空间安全学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西北工业大学','软件学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西北工业大学','电子信息学部','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('厦门大学',24,'福建','中游985','https://cs.xmu.edu.cn','https://gs.xmu.edu.cn','计算机学院强com；信息学院弱com','["厦大计算机保研","XMU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('厦门大学','计算机学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('厦门大学','信息学院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('中南大学',25,'湖南','中游985','https://cse.csu.edu.cn','https://graduate.csu.edu.cn','两个院系均强com','["中南大学计算机保研","CSU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中南大学','计算机学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中南大学','软件学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('吉林大学',26,'吉林','中游985','https://ccccu.jlu.edu.cn','https://yjsc.jlu.edu.cn','图中未收录，请查阅官网','["吉大计算机保研"]');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('中国农业大学',27,'北京','中游985','https://ciee.cau.edu.cn','https://graduate.cau.edu.cn','图中未收录，请查阅官网','["农大计算机保研"]');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('大连理工大学',28,'辽宁','中游985','https://cs.dlut.edu.cn','https://gs.dlut.edu.cn','图中仅收录电子信息学部，弱com','["大工计算机保研","DUT夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('大连理工大学','电子信息与电气工程学部','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('华东师范大学',29,'上海','中游985','https://cs.ecnu.edu.cn','https://yjsy.ecnu.edu.cn','两个院系均强com','["华师大计算机保研","ECNU夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('华东师范大学','计算机学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('华东师范大学','数据科学学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('华南理工大学',30,'广东','中下游985','https://www2.scut.edu.cn/cs','https://www2.scut.edu.cn/graduate','计算机学院强com（图中仅收录此院系）','["华工计算机保研","SCUT夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('华南理工大学','计算机科学与工程学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('电子科技大学',31,'四川','中下游985','https://www.scse.uestc.edu.cn','https://www.uestc.edu.cn/graduate','图中所有院系均弱com','["成电计算机保研","UESTC夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('电子科技大学','计算机科学与工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('电子科技大学','软件学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('电子科技大学','信息与通信工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('电子科技大学','电子工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('电子科技大学','集成电路工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('电子科技大学','机械与电气工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('电子科技大学','深圳研究院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('湖南大学',32,'湖南','中下游985','https://csee.hnu.edu.cn','https://graduate.hnu.edu.cn','信息学院和电气信息学院强com；控制学院弱com','["湖南大学计算机保研"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('湖南大学','控制科学与工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('湖南大学','信息科学与工程学院','strong');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('湖南大学','电气信息工程学院','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('重庆大学',33,'重庆','中下游985','https://cs.cqu.edu.cn','https://graduate.cqu.edu.cn','图中两个院系均弱com','["重大计算机保研"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('重庆大学','计算机科学与技术学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('重庆大学','电气工程学院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('兰州大学',38,'甘肃','末流985','https://xxxy.lzu.edu.cn','https://yjs.lzu.edu.cn','图中未收录，请查阅官网','["兰大计算机保研"]');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('东北大学',39,'辽宁','末流985','https://cse.neu.edu.cn','https://gs.neu.edu.cn','图中未收录，请查阅官网','["东北大学计算机保研"]');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('中国海洋大学',57,'山东','末流985','https://it.ouc.edu.cn','https://yjsy.ouc.edu.cn','图中未收录，请查阅官网','["海大计算机保研"]');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('西北农林科技大学',73,'陕西','末流985','https://cie.nwafu.edu.cn','https://yjsc.nwafu.edu.cn','图中未收录，请查阅官网','["西农计算机保研"]');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('中央民族大学',103,'北京','末流985','https://ie.muc.edu.cn','https://yjs.muc.edu.cn','图中未收录，请查阅官网','["民大计算机保研"]');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('中国科学院',900,'北京','中科院','http://www.ict.ac.cn','https://www.ucas.ac.cn','各所独立招生；自动化所硕士强+博士弱；软件所强com；计算所弱com','["中科院计算所保研","中科院软件所保研","中科院直博"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中国科学院','计算技术研究所','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中国科学院','自动化所','cas_mix');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中国科学院','信工所','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('中国科学院','软件所','strong');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('北京邮电大学',901,'北京','顶级211','https://cs.bupt.edu.cn','https://yjszs.bupt.edu.cn','图中所有院系均弱com','["北邮计算机保研","BUPT夏令营"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京邮电大学','计算机学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京邮电大学','人工智能学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京邮电大学','信息与通信工程学院','weak');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京邮电大学','现代邮政学院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('西安电子科技大学',902,'陕西','顶级211','https://scs.xidian.edu.cn','https://gr.xidian.edu.cn','图中仅收录网安学院，弱com','["西电计算机保研"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('西安电子科技大学','网络与信息安全学院','weak');
INSERT OR IGNORE INTO universities(name,rank,province,tier,cs_url,adm_url,note,xhs) VALUES('北京交通大学',903,'北京','中上211','https://scit.bjtu.edu.cn','https://gs.bjtu.edu.cn','图中仅收录计信学院，弱com','["北交大计算机保研"]');
INSERT OR IGNORE INTO departments(univ_name,dept_name,com_status) VALUES('北京交通大学','计算机与信息技术学院','weak');

-- ════════════════════════════════════════════════════════════
-- 211 / 双非院校（无 com 数据）
-- ════════════════════════════════════════════════════════════

INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('中国传媒大学',999,'北京','顶级211','传媒/新闻顶级211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('中国政法大学',999,'北京','顶级211','法学顶级211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('中央财经大学',999,'北京','顶级211','财经顶级211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('北京外国语大学',999,'北京','顶级211','外语顶级211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('上海财经大学',999,'上海','顶级211','财经顶级211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('对外经济贸易大学',999,'北京','顶级211','经贸顶级211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('上海外国语大学',999,'上海','顶级211','外语顶级211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('北京科技大学',999,'北京','中上211','冶金/材料强校');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('北京化工大学',999,'北京','中上211','化工强校');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('苏州大学',999,'江苏','中上211','江苏强综合211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('南京师范大学',999,'江苏','中上211','师范类强校');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('南京理工大学',999,'江苏','中上211','工科均衡，CS/软件保研良好');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('南京航空航天大学',999,'江苏','中上211','航空/CS/自动化实力强');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('西南财经大学',999,'四川','中上211','财经强校');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('华东理工大学',999,'上海','中上211','化工+CS，沪上211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('上海大学',999,'上海','中上211','综合性沪上211，CS近年发展快');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('西南大学',999,'重庆','中上211','综合211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('武汉理工大学',999,'湖北','中上211','工科211，CS/材料');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('华中师范大学',999,'湖北','中上211','师范类强校');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('中南财经政法大学',999,'湖北','中上211','财经政法强校');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('东北师范大学',999,'吉林','中上211','师范类211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('西南交通大学',999,'四川','中上211','交通/CS/信号处理');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('空军军医大学',999,'陕西','中上211','军医大学');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('暨南大学',999,'广东','中上211','华南综合211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('华北电力大学',999,'北京','中上211','电力/能源强校');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('中国石油大学',999,'山东','中上211','华东/北京均有校区');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('河海大学',999,'江苏','中上211','水利+CS，南京211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('郑州大学',999,'河南','中上211','河南唯一211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('海军军医大学',999,'上海','中上211','军医大学');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('哈尔滨工程大学',999,'黑龙江','中上211','工科强211，CS/自动化优势');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('中国矿业大学',999,'江苏','中流211','矿业+CS，徐州校区');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('北京工业大学',999,'北京','中流211','北京市属211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('西北大学',999,'陕西','中流211','西安综合211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('湖南师范大学',999,'湖南','中流211','师范类211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('北京林业大学',999,'北京','中流211','林业特色211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('北京体育大学',999,'北京','中流211','体育顶级211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('江南大学',999,'江苏','中流211','轻工/食品强校');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('中国地质大学',999,'湖北','中流211','武汉/北京均有校区');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('南京农业大学',999,'江苏','中流211','农业211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('陕西师范大学',999,'陕西','中流211','师范类211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('福州大学',999,'福建','中流211','福建省属211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('合肥工业大学',999,'安徽','中流211','CS/自动化保研热门');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('华中农业大学',999,'湖北','中流211','农业211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('华南师范大学',999,'广东','中流211','师范类211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('河北工业大学',999,'河北','中下211','河北/天津工科211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('大连海事大学',999,'辽宁','中下211','航海/交通211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('长安大学',999,'陕西','中下211','公路交通强校');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('安徽大学',999,'安徽','中下211','安徽省属211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('太原理工大学',999,'山西','中下211','山西唯一211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('中国药科大学',999,'江苏','中下211','药学特色211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('云南大学',999,'云南','中下211','云南省属211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('东华大学',999,'上海','中下211','纺织+CS，沪上211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('四川农业大学',999,'四川','中下211','农业211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('天津医科大学',999,'天津','中下211','医科211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('南昌大学',999,'江西','中下211','江西省属211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('北京中医药大学',999,'北京','中下211','中医药顶级211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('东北农业大学',999,'黑龙江','末流211','农业211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('延边大学',999,'吉林','末流211','边疆211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('西藏大学',999,'西藏','末流211','西藏唯一211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('青海大学',999,'青海','末流211','青海唯一211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('宁夏大学',999,'宁夏','末流211','宁夏唯一211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('东北林业大学',999,'黑龙江','末流211','林业211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('海南大学',999,'海南','末流211','海南省属211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('新疆大学',999,'新疆','末流211','新疆省属211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('广西大学',999,'广西','末流211','广西唯一211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('贵州大学',999,'贵州','末流211','贵州唯一211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('内蒙古大学',999,'内蒙古','末流211','内蒙古唯一211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('石河子大学',999,'新疆','末流211','兵团211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('辽宁大学',999,'辽宁','中流211','综合211');
INSERT OR IGNORE INTO universities(name,rank,province,tier,note) VALUES('东北财经大学',999,'辽宁','中流211','财经强校（非官方211，常被归入此档）');
