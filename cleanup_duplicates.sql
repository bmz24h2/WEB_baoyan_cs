-- cleanup_duplicates.sql
-- Run: Get-Content cleanup_duplicates.sql -Encoding UTF8 | sqlite3 output/faculty.db

-- 1. Remove duplicates, keep the row with the lowest id
DELETE FROM teachers WHERE id NOT IN (
    SELECT MIN(id) FROM teachers GROUP BY name, university
);

-- 2. Remove dirty data where university name was scraped as teacher name
DELETE FROM teachers WHERE name = university;
DELETE FROM teachers WHERE name LIKE '%大学' OR name LIKE '%学院'
    OR name LIKE '%研究所' OR name LIKE '%研究院' OR name LIKE '%实验室';

-- 3. Remove UI/navigation artifacts scraped as teacher names
--    （页面上的"返回上一级""更多""首页"等导航文字被爬虫当作姓名抓取）
DELETE FROM teachers WHERE name IN (
    '返回上一级', '返回', '更多', '更多信息', '首页', '主页',
    '上一页', '下一页', '目录', '导航', '联系我们', '网站地图',
    '登录', '注册', '搜索', '查看详情', '点击查看', '详细信息',
    '个人主页', '个人简介', '查看更多', '返回列表'
);
DELETE FROM teachers WHERE
    name LIKE '返回%'          -- "返回上一级" "返回首页" 等
    OR name LIKE '%首页%'
    OR name LIKE '%导航%'
    OR name LIKE '%登录%'
    OR name LIKE '%版权%'
    OR name LIKE 'http%'       -- 误抓的 URL
    OR name LIKE 'www.%'
    OR name LIKE '<%'          -- HTML 标签
    OR length(trim(name)) < 2  -- 单字（不是有效姓名）
    OR length(trim(name)) > 12 -- 超过12字几乎不可能是中文姓名
                               -- （含英文名如"Zhang Wei"约10字以内也安全）
    OR name GLOB '*[0-9]*'     -- 含数字（如 "teacher01"）
    ;

-- 3b. ★ 新增：高校主页常见栏目名 / 功能入口（4字以内也会被误抓）
DELETE FROM teachers WHERE name IN (
    -- 高校导航栏目
    '招生就业','国际交流','教育教学','学校概览','群团组织',
    '学术刊物','附件下载','信息公开','党建引领','基金校友',
    '校历','书记信箱','院长信箱','图书馆','档案馆','校史馆',
    '科学研究','合作交流','新闻中心','通知公告','党委书记',
    '继续教育','后勤保障','在线服务','就业指导','创新创业',
    '学生工作','研究生院','本科生院','工会主席','纪检监察',
    '对外合作','国内合作','招标采购','信息化建设','资产管理',
    '邮箱','网站','主站','首页链接','返回主站',
    -- 英文导航
    'Home','About','Faculty','Research','Contact','News','Events',
    'People','Team','Staff','Members','Students','Alumni'
);
-- 含关键字的栏目名（用 LIKE 匹配更宽泛的变体）
DELETE FROM teachers WHERE
    name LIKE '%信箱'           -- "书记信箱""院长信箱"
    OR name LIKE '%公开%'       -- "信息公开""政务公开"
    OR name LIKE '%下载'        -- "附件下载""资料下载"
    OR name LIKE '%中心%'       -- "服务中心""数据中心"（4字以内）
    OR name LIKE '招生%'        -- "招生就业""招生办公室"
    OR name LIKE '就业%'        -- "就业指导"
    OR name LIKE '后勤%'        -- "后勤保障"
    OR (length(name) = 4 AND (
        name LIKE '%交流%'
        OR name LIKE '%教学%'
        OR name LIKE '%合作%'
        OR name LIKE '%管理%'
        OR name LIKE '%服务%'
        OR name LIKE '%建设%'
        OR name LIKE '%工作%'
        OR name LIKE '%保障%'
    ))
    ;

-- 4. 清除 research_areas 中混入大段简介的脏数据
--    （若某行的 research_areas 超过80字，说明把 bio 误填进去了，清空以免影响匹配）
UPDATE teachers SET research_areas = NULL
WHERE research_areas IS NOT NULL AND length(research_areas) > 80
  AND research_areas NOT LIKE '%,%'   -- 逗号分隔的关键词列表不受此限
  AND research_areas NOT LIKE '%、%'; -- 顿号分隔的也豁免

-- 5. Add unique index to prevent future duplicates
CREATE UNIQUE INDEX IF NOT EXISTS idx_teacher_name_univ ON teachers(name, university);

-- 6. Verify
SELECT 'Total after cleanup: ' || COUNT(*) FROM teachers;
SELECT 'Remaining duplicates: ' || COUNT(*) FROM (
    SELECT name, university FROM teachers GROUP BY name, university HAVING COUNT(*) > 1
);
SELECT 'Nav artifacts remaining: ' || COUNT(*) FROM teachers WHERE name LIKE '返回%';