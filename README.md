# 985/211 CS 保研导航系统

## 文件结构

```
Web/
├── pom.xml                        Maven 依赖
├── application.properties         配置文件
├── init_data.sql                  院校静态数据（首次启动自动导入）
├── cleanup_duplicates.sql         清理爬虫重复数据（一次性维护脚本）
├── src/
│   ├── frontend.html              前端页面
│   ├── frontend.js                前端逻辑（从 API 加载数据）
│   ├── frontend.css               样式
│   └── java/
│       ├── BaoyanApp.java         Spring Boot 入口 + 数据库 + API
│       ├── FetchEngine.java       HTTP 抓取 + HTML 解析引擎
│       ├── ScraperService.java    爬虫调度层
│       └── UniversityData.java    985 院校配置（爬虫用）
└── output/
    ├── faculty.db                 SQLite 数据库（运行后自动生成）
    └── scraper.log                日志
```

## 数据来源

| 内容 | 来源 | 存储位置 |
|------|------|--------|
| 985 各院系强/弱 com | 小红书图片（@8052244911） | `faculty.db → universities/departments` |
| 211 分档数据 | 211 大学排名图片（顶级/中上/中流/中下/末流） | `faculty.db → universities` |
| 各校官网链接 | 各校已知域名 | `faculty.db → universities` |
| 教师姓名/职称/方向 | 爬虫实时抓取 | `faculty.db → teachers` |

## 启动步骤

```bash
# 1. 确认 Java 17+ 和 Maven 已安装
mvn --version

# 2. 启动后端（首次启动自动建表并从 init_data.sql 导入 113 所院校数据）
mvn spring-boot:run

# 3. 浏览器打开前端
# 直接用浏览器打开 src/frontend.html
# 前端从 http://localhost:8080/api/university-data 加载院校数据
```

## 清理重复爬虫数据（首次运行后执行一次）

```powershell
# Windows PowerShell
Get-Content cleanup_duplicates.sql -Encoding UTF8 | sqlite3 output/faculty.db
```

## API 接口

```
GET  /api/university-data               全部院校数据（前端主入口）
GET  /api/universities                  985 院校列表（含强弱 com）
GET  /api/universities?tier=顶尖C9      按梯队过滤
GET  /api/universities/{name}           单所学校详情
GET  /api/teachers?university=清华      教师查询
GET  /api/teachers/stats                数据库统计
GET  /api/search?q=机器学习             全局搜索
POST /api/scrape                        触发爬虫（异步）
POST /api/verify/run                    触发 URL 验证
```

## 触发爬虫

```powershell
# 爬取指定学校
curl -X POST http://localhost:8080/api/scrape `
     -H "Content-Type: application/json" `
     -d '{\"universities\": [\"清华大学\", \"北京大学\"]}'

# 查询爬取结果
curl "http://localhost:8080/api/teachers?university=清华大学"
```

## 架构说明

前端不再依赖静态 `data.js`，所有院校数据（985 强弱 com + 211 分档）全部存储在 SQLite 数据库中，通过 `/api/university-data` 接口提供给前端。修改院校数据只需操作数据库，不需要改代码。

```
init_data.sql ──→ faculty.db ──→ /api/university-data ──→ frontend.js
                      ↑
              爬虫写入 teachers 表
```