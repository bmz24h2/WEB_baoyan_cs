# 985 CS 保研导航系统

## 文件结构

```
985_scraper/
├── pom.xml                          Maven 依赖
├── frontend.html                    前端页面（可独立打开，后端启动后自动接通）
├── src/main/
│   ├── java/com/baoyan/
│   │   ├── BaoyanApplication.java              Spring Boot 入口
│   │   ├── data/UniversityData.java            所有静态数据（高校配置 + 强弱com + XHS关键词）
│   │   ├── model/Teacher.java                  教师数据模型
│   │   ├── service/
│   │   │   ├── DatabaseService.java            SQLite 数据库操作
│   │   │   └── ScraperService.java             爬虫引擎 + URL验证器
│   │   └── controller/ApiController.java       REST API 接口
│   └── resources/application.properties       配置文件
└── output/                                     运行后自动生成
    ├── faculty.db                              SQLite 数据库
    └── scraper.log                             日志
```

## 数据来源

| 内容 | 来源 | 可信度 |
|------|------|--------|
| 各院系强/弱 com | 用户提供的小红书图片（@8052244911） | ✅ |
| 各校官网链接 | 各校已知域名 | ✅ 需 verify 确认 |
| 小红书关键词 | 与强弱com图对应 | ✅ |
| 教师姓名/职称/方向 | 爬虫实时抓取 | ✅ 运行后实时 |

## 启动步骤

```bash
# 1. 安装 Java 17+ 和 Maven
mvn --version

# 2. 编译并启动 Spring Boot 后端
mvn spring-boot:run

# 3. 用浏览器打开 frontend.html
#    → 自动检测 localhost:8080，连接成功后显示「API 已连接」绿色徽章

# 4. 触发 URL 验证（需国内网络）
curl -X POST http://localhost:8080/api/verify/run

# 5. 触发爬虫（全部高校）
curl -X POST http://localhost:8080/api/scrape \
     -H "Content-Type: application/json" \
     -d '{"universities": ["清华大学", "北京大学", "浙江大学"]}'

# 6. 查询教师
curl "http://localhost:8080/api/teachers?area=NLP&title=教授"
```

## API 接口

```
GET  /api/universities                  高校列表（含强弱com）
GET  /api/universities?tier=顶尖C9       按梯队过滤
GET  /api/universities?comFilter=hasStrong  只看含强com的学校
GET  /api/universities/{name}           单所学校详情
GET  /api/teachers?university=清华&area=NLP  教师查询
GET  /api/teachers/stats                数据库统计
GET  /api/search?q=机器学习              全局搜索
GET  /api/xhs/清华大学                   小红书关键词 + Bing链接
GET  /api/verify                        URL验证历史
POST /api/scrape                        触发爬虫（异步）
POST /api/verify/run                    触发URL验证（异步）
```

## 前后端连接说明

`frontend.html` 启动时会尝试访问 `http://localhost:8080/api/teachers/stats`：
- **后端运行中**：页面右下角显示绿色「API 已连接」徽章，点击学校卡片后侧栏自动加载实时教师数据
- **后端未运行**：页面完全正常使用（强弱com等嵌入数据不受影响）

## 动态页面说明

部分高校的教师列表是 JS 动态渲染的（`dynamic=true`），`ScraperService.fetchDynamic()` 目前用 Jsoup 兜底。
如需完整 JS 渲染，在 `ScraperService.java` 的 `fetchDynamic()` 方法中接入 Playwright for Java：

```java
try (Playwright pw = Playwright.create()) {
    Browser b = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    Page p = b.newPage();
    p.navigate(url);
    p.waitForLoadState(LoadState.NETWORKIDLE);
    String html = p.content();
    b.close();
    return Jsoup.parse(html);
}
```
