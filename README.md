# 985/211 CS 保研导航系统

## 目录

- [项目结构](#项目结构)
- [各文件职责详解](#各文件职责详解)
- [启动步骤](#启动步骤)
- [用户系统说明](#用户系统说明)
- [AI 顾问配置](#ai-顾问配置)
- [API 接口一览](#api-接口一览)
- [数据模型](#数据模型)
- [我想加功能，改哪个文件？](#我想加功能改哪个文件)

---

## 项目结构

```
Web/
├── .gitignore                             排除个人隐私数据和 API Key
├── pom.xml
├── init_data.sql                          113+ 所院校静态数据（含末流211补充）
├── src/
│   └── main/
│       ├── java/com/baoyan/
│       │   ├── BaoyanApp.java                     主入口 + CORS 配置
│       │   ├── model/
│       │   │   ├── Teacher.java                   ★ 新增 citedCount/worksCount/activeYear/countsByYear 字段
│       │   │   └── SchoolInfo.java                招生/通知信息模型
│       │   ├── db/
│       │   │   └── DatabaseService.java           ★ teachers 表新增4列；upsertTeacher 同步更新；FTS5损坏自动自愈
│       │   ├── api/
│       │   │   ├── ApiController.java             ★ 新增 GET /api/scrape/jobs 接口
│       │   │   ├── AuthController.java            用户认证：注册/登录/登出/whoami/改密码
│       │   │   ├── UserDataController.java        用户数据：个人信息云同步/面试进度/头像
│       │   │   ├── DocumentController.java        ★ 段落级占位符替换（replaceInParagraphs）；\u00a0 处理
│       │   │   ├── ResumeExtractController.java   ★ 邮箱正则兜底；XML 标签替换改为空字符串
│       │   │   └── InterviewController.java       ★ parseCsv 过滤加强；批量删除接口；导入多格式支持
│       │   ├── chat/
│       │   │   ├── ChatController.java            AI对话（SSE流式 + 多模型切换 + fallback）
│       │   │   ├── ChatHistoryService.java        对话历史持久化（SQLite，最多50会话）
│       │   │   ├── ModelRegistry.java             ★ 删除过期模型 gui-plus-2026-02-26
│       │   │   └── SpeechController.java          TTS代理：阿里云CosyVoice语音合成
│       │   ├── analytics/
│       │   │   ├── AnalyticsEventBus.java         SSE实时推送（爬取进度广播）
│       │   │   ├── MatchService.java              ★ getDirExpand() getter；人机交互去"可视化"+10方向补英文subfield词
│       │   │   ├── AnalyticsService.java          ★ keywordMatches() 根治AR子串误匹配；getResearchAreas 改用 DIR_EXPAND；getAreaTrend()
│       │   │   └── AnalyticsController.java       ★ FTS5损坏自动重建；area-trend+文件缓存
│       │   ├── scraper/
│       │   │   ├── ScraperCore.java               爬虫基类（abstract）：策略A-H + N
│       │   │   ├── IndirectSearch.java            ★ field/subfield 结构化解析；CS过滤金标准；counts_by_year；research_areas 净化
│       │   │   ├── ScraperService.java            ★ 爬虫调度（并发、队列）；预设院校直连失败自动降级间接搜索；快速失败
│       │   │   └── UniversityData.java            985/211院校配置（强弱com、预设URL）
│       │   └── engine/
│       │       ├── FetchEngine.java               HTTP抓取+HTML解析+isBadName姓名过滤
│       │       ├── NLPProcessor.java              分词、方向识别、CCF评级
│       │       └── RecommendationEngine.java      多因素加权打分
│       └── resources/
│           ├── application.properties             ⚠️ 含API Key，加入.gitignore
│           ├── application-local.properties       本地Key（已在.gitignore，推荐）
│           ├── init_data.sql                      113+ 所院校静态数据（classpath备份）
│           ├── templates/                         文档生成模板（7个.docx）
│           │   ├── intro.docx
│           │   ├── statement.docx
│           │   ├── email.docx
│           │   ├── 专家推荐信.docx
│           │   ├── resume-a.docx
│           │   ├── resume-b.docx
│           │   └── resume-c.docx
│           └── static/
│               ├── html/
│               │   ├── login.html                 ★ 头像 position:relative 修复
│               │   ├── home.html
│               │   ├── index.html
│               │   ├── tools.html
│               │   ├── advisor-match.html
│               │   ├── analytics.html             ★ 面板嵌套修复；研究热度折线图；api-status；图例居中
│               │   ├── chat.html                  ★ 模型列表同步；历史对话修复
│               │   ├── interview.html             ★ 练习记录CRUD；类别折叠；批量删除复位；导入弹窗
│               │   ├── statement.html             ★ 目标院校自动填入
│               │   ├── documents.html             ★ 恢复服务器同步；目标院校填入；套磁邮件编辑器
│               │   └── profile.html               ★ 云同步；GPA/排名 text 类型；简历解析弹窗
│               ├── css/
│               │   └── frontend.css
│               └── js/
│                   ├── lucide.min.js
│                   ├── frontend.js
│                   ├── viewport-fix.js
│                   ├── core/
│                   │   ├── nav.js
│                   │   ├── auth.js
│                   │   └── profile.js
│                   └── features/
│                       ├── voice.js
│                       ├── favorites.js
│                       └── session.js
└── output/
    ├── faculty.db                                 爬虫+用户数据库（单文件）
    ├── area_trend_cache.json                      ★ 新增：OpenAlex 趋势折线图永久文件缓存
    └── custom-templates/                          用户上传模板（.gitignore已排除）
```

> **注意**：`faculty.db` 和 `baoyan.db` 实际上是**同一个 SQLite 文件**，由 `application.properties` 中 `spring.datasource.url` 决定路径。

---

## 各文件职责详解

### 后端

#### `BaoyanApp.java`
Spring Boot 启动入口：`main()` + CORS 配置 Bean。启动时调用 `db.initSchema()` 和 `db.initStaticData()`，并打印教师总数和登录页地址。

#### `db/DatabaseService.java` ★ 更新
SQLite 操作层，所有表统一在此建立。**关键行为**：

- `initSchema()` 每次启动建表（含迁移旧库 ALTER TABLE）；自动清理垃圾数据；仅在题库为空时初始化内置面试题；清理过期用户 session
- `initStaticData()` 每次启动执行 `INSERT OR IGNORE`，新增院校自动补入
- `upsertTeacher()` 先按 `name+university` 判重，再按 `profile_url` UPSERT
- `countTeachersByUniversity(name)` 精确 COUNT，不受 `search()` 的 LIMIT 500 影响
- `getConnection()` 返回 SQLite 连接

**★ teachers 表新增4列**：`cited_count INTEGER`、`works_count INTEGER`、`active_year INTEGER`、`counts_by_year TEXT`；ALTER TABLE 自动迁移旧库；`upsertTeacher()` VALUES 从11个问号扩展到15个。

**★ FTS5 损坏自愈**：`initSchema()` 中 `DELETE FROM teachers`（清理垃圾数据）会触发 FTS5 同步触发器。若 `teachers_fts` 的 shadow 表损坏（启动报 `SQLITE_CORRUPT_VTAB` / `database disk image is malformed`），catch 块自动检测并删除 4 个 shadow 表（`teachers_fts_data` / `teachers_fts_idx` / `teachers_fts_docsize` / `teachers_fts_config`），随后由 `AnalyticsController` 启动时重建虚拟表，**无需手动删库**。

#### `model/Teacher.java` ★ 更新
新增4个字段（来自 OpenAlex，重爬后才有数据）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `citedCount` | int | 被引总次数 |
| `worksCount` | int | 论文总数 |
| `activeYear` | int | 最近活跃年份（counts_by_year 中最近有论文的年份） |
| `countsByYear` | String | 历年论文/引用 JSON：`[{"y":2020,"w":3,"c":10},...]` |

#### `api/AuthController.java`
用户认证系统：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册（username/password，SHA-256+salt存储） |
| POST | `/api/auth/login` | 登录 → 设置 HttpOnly Cookie（BAOYAN_SESSION，30天） |
| POST | `/api/auth/logout` | 登出（清除 Cookie + 删除 DB session） |
| GET | `/api/auth/whoami` | 当前登录用户信息（含头像），401 = 未登录 |
| PUT | `/api/auth/password` | 修改密码（验证当前密码，使其他设备 session 失效） |

**关键设计**：
- `resolveUser(request)` 从 Cookie 读取 token → 查 `user_sessions` → 返回 `UserInfo(id, username)`，供其他 Controller 鉴权调用
- 密码存储：`SHA-256(UUID盐 + 明文密码)`，盐和哈希分列存储
- 修改密码后保留当前 session，使其他设备 session 失效

#### `api/UserDataController.java`
用户个人数据（需登录，所有接口先调 `auth.resolveUser()` 鉴权）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/user/profile` | 读取个人信息 JSON |
| PUT | `/api/user/profile` | 保存个人信息（整体替换，存为 JSON 字符串） |
| GET | `/api/user/progress` | 读取已完成面试题 ID 列表 |
| POST | `/api/user/progress` | 标记题目完成（body: `{questionId}`） |
| DELETE | `/api/user/progress/{id}` | 取消标记单题 |
| DELETE | `/api/user/progress` | 清空所有练习进度 |
| GET | `/api/user/avatar` | 读取头像（base64 data URL） |
| PUT | `/api/user/avatar` | 保存头像（限 200KB，需 `data:image/` 前缀） |

#### `api/ApiController.java` ★ 更新
`@RestController /api/**`：院校查询、教师查询、爬虫触发、全局搜索、招生通知等主业务接口。

**★ 本次新增**：`GET /api/scrape/jobs?limit=10`：从 `teachers` 表按 `university` 分组聚合，返回最近爬取院校列表（universityId / teacherCount / startedAt / status）。修复「最近爬取任务」面板404问题。

**★ 本次更新**：
- `activeScrapingJobs` 从 `Set<String>` 改为 `ConcurrentHashMap<String, Long>`，key=学校名，value=爬取开始时间戳
- `/api/scrape/status/{university}` 和 `/api/scrape/progress/{university}` 新增返回 `elapsedSeconds` 字段

#### `api/DocumentController.java` ★ 核心修复
文档生成，**无需 Apache POI**——docx 本质是 ZIP，用 Java 标准库操作。

**★ 核心修复：段落级占位符替换**

问题根源：Word 把连续文字拆分到多个 `<w:r>/<w:t>` 标签，`xml.replace("XXX", ...)` 无法匹配，所有替换规则全部失效，下载的文档还是模板原文。

修复方案——新增 `replaceInParagraphs()` 方法：
1. 对每个 `<w:p>` 段落，把所有 `<w:t>` 文本拼接成完整字符串
2. 在完整字符串上做规则替换
3. 替换后把结果写回段落第一个 `<w:t>`，清空其余

同时在 `resume-c` 规则中加入 `\u00a0`（不间断空格）变体，解决 Word 用不间断空格对齐导致规则不匹配的问题。

**★ AI 智能填充**：

| 组件 | 说明 |
|---|---|
| `buildAIFillPrompt()` | 保研视角 prompt：明确「突出科研/论文/竞赛，禁止出现求职/应聘」，用字符串拼接规避 `%` 格式符问题 |
| `parseAIVars()` | 字符扫描法解析 AI 返回 JSON，彻底规避多层反斜杠转义地狱 |
| `buildExtraRules()` | 把 AI 内容精确映射到模板固定段落；resume-c 的「工作经验」→「科研经历」；旧条目全部清空再填新内容 |
| `fillTemplateWithExtra()` | 额外规则优先插入，再走原有 fillTemplate 流程 |
| vars 类型修复 | `Map<String,Object>` 安全逐条转 `Map<String,String>`，修复 ClassCastException → HTTP 500 |

**★ 异步 AI 任务（离开页面后后台继续生成）**：

| 端点 | 说明 |
|---|---|
| `POST /api/documents/generate/ai` | 同步版，等待AI完成直接返回 docx |
| `POST /api/documents/generate/ai/async` | ★ 异步版，立即返回 `{"taskId":"xxx"}`，后台线程跑 AI |
| `GET /api/documents/task/{id}/status` | 查询任务进度（pending/running/done/error + progress文字） |
| `GET /api/documents/task/{id}/download` | 下载完成的 docx 文件 |

前端调异步版，每3秒轮询状态，生成完成自动触发下载。`taskId` 存 localStorage，离开页面再回来自动恢复轮询。任务完成后30分钟自动清理。

其他说明：
- 内置7个模板（`BUILT_IN` 列表），自定义模板存 `output/custom-templates/`
- `PLACEHOLDER_RULES` Map 定义每个模板的占位符替换规则；resume-c 新增「工作经验→科研经历」静态规则
- PDF 输出需服务器安装 LibreOffice；未安装则返回 503

#### `api/ResumeExtractController.java` ★ 更新
简历智能解析：

- 支持 .docx（ZIP解析XML）、.pdf（基础文字提取）、.txt
- 提取文字后调 LLM（Aliyun 优先，Zhipu 备用），返回结构化 JSON
- `buildExtractionPrompt()` 定义 AI 提取指令

**★ 本次修复**：
- **max_tokens 3000→6000**：防止包含英文长论文题目的简历因 token 不足被截断
- **科研段落优先**：简历 >3500 字时把「科研经历」段落提到 prompt 最前面
- **experience 字段描述加强**：标注「最重要字段」，论文/项目/竞赛要求完整，不得省略
- **experience/awards/practice 数组解析**：AI 有时返回数组格式 `[...]`，新增 `extractArrayField()` 转多行字符串
- **practice 字段新增**：社会实践/志愿服务/学生工作，prompt 里有明确描述
- **awards/practice 字段边界明确**：「优秀志愿者称号」→ awards；「担任马拉松志愿者」→ practice，避免重复
- **邮箱正则兜底**：AI 解析完后用正则从原文提取邮箱并覆盖
- **XML 提取修复**：`extractDocxText()` 把 `<tag>` 替换为空字符串
- **入学/毕业年月提取**：新增 eduStart/eduEnd 字段（"至今/在读"取入学年+4，月份06），正则从原文兜底匹配 YYYY.MM

#### `api/InterviewController.java` ★ 更新
模拟面试题库 CRUD：

- 题目存 SQLite `interview_questions` 表
- `source` 字段：`builtin`内置 / `manual`手动 / `ai_chat`来自AI对话 / `import`文件导入
- 批量导入支持 CSV / TXT / JSON / DOCX / PDF 五种格式

**★ 本次更新**：

`parseCsv()` 过滤逻辑加强，防止 docx 里的说明文字行被当题目导入：
- 任意位置的 `category`/`分类`/`类别` 行都跳过（不只第一行）
- 以 `说明`/`#`/`//`/`注：` 开头的行跳过
- 题目列值为 `question`/`hint` 等占位符的行跳过

新增 `DELETE /api/interview/questions/batch`（body: `{ids:[...]}`）批量删除接口。

导入模式参数 `?mode=skip|overwrite|replace_all`：
- `skip`：INSERT OR IGNORE
- `overwrite`：按 question 内容匹配，存在则 UPDATE，否则 INSERT
- `replace_all`：先删所有 source!='builtin' 再插

#### `chat/ChatController.java`
SSE 流式对话主逻辑：

| 方法/类 | 职责 |
|---|---|
| `chat()` | POST /api/chat，SSE 流式对话入口 |
| `doStream()` | 自动 fallback / 手动指定模型 |
| `buildMessages()` | 组装系统提示词 + 历史 + 当前消息 ← **改系统提示词改这里** |
| `callApi()` | 向指定 endpoint 发 HTTP 请求 |
| `streamTokens()` | 解析 SSE 流，逐 token 推送前端 |

**SSE 事件类型**：

| 事件名 | 触发时机 | 数据格式 |
|---|---|---|
| `session_info` | 会话建立时 | `{"sessionId":123}` |
| `model_info` | 每次输出前 | `{"model","type","provider","forever"}` |
| `fallback` | 主模型额度耗尽 | `{"from","to","provider"}` |
| `token` | 每个流式 token | 纯文本 |
| `done` | 输出完毕 | `[DONE]` |
| `error` | 所有模型失败 | 纯文本错误说明 |

#### `chat/ModelRegistry.java` ★ 更新
模型注册表。删除过期模型 `gui-plus-2026-02-26`（2026/06/15 到期）。

**当前 LLM 优先级**：

| 优先级 | 模型ID | 服务商 | 免费到期 |
|---|---|---|---|
| 1 | `qwen3.6-flash` | 阿里云 | 2026/07/17 |
| 2 | `kimi-k2.6` | 阿里云 | 2026/07/21 |
| 3 | `qwen3.6-27b` | 阿里云 | 2026/07/23 |
| 4 | `deepseek-v4-pro` | 阿里云 | 2026/07/24 |
| 5 | `qwen3.7-max` | 阿里云 | 2026/08/20 |
| 6 | `qwen3.7-max-2026-05-20` | 阿里云 | 2026/08/20 |
| 7 | `qwen3.7-plus-2026-05-26` | 阿里云 | 2026/09/01 |
| 8 | `glm-4-flash` | 智谱AI | **永久免费** |
| 9 | `glm-4-flash-250414` | 智谱AI | **永久免费** |
| 10 | `glm-4-air` | 智谱AI | **永久免费** |

#### `chat/SpeechController.java`
阿里云 CosyVoice TTS 代理（同一个 `aliyun.api.key`）：
- `listVoices()` 返回6种音色（小淳/婉子/程成/龙飞/小夏/小白）
- `synthesize()` 接收 text/voice/speed/format 参数，返回 mp3 字节流
- 未配置 Key → 503，前端自动降级到浏览器内置 TTS

#### `analytics/MatchService.java` ★ 更新
导师匹配核心。

**★ `getDirExpand()`**：`public static Map<String, List<String>> getDirExpand()` 方法，把 `private static DIR_EXPAND`（21 个方向键 + 语义近邻词表）暴露给 `AnalyticsService` 使用，返回 unmodifiableMap。

**★ 人机交互方向去噪**：移除过宽的 `可视化`（几乎所有方向都含此词，是趋势图误匹配源），新增 `用户体验`/`UX`/`UI`/`界面设计`/`交互设计`。短英文缩写（AR/VR/UI/UX）交给 `AnalyticsService.keywordMatches` 做单词边界匹配。

**★ 10 个核心方向补英文 subfield 词**（机器学习/计算机视觉/自然语言处理/大语言模型/系统体系结构/算法理论/数据库/软件工程/嵌入式/网络与通信），如「大语言模型」加 `Large Language Model`/`Generative AI`，与 OpenAlex 返回的英文方向名双向匹配。

三个关键方法：

**`buildExpandedQuery(dirs, background)`**：
- `dirs`（意向方向标签）→ `DIR_EXPAND` 两层扩展：同义词 + 语义近邻
- `background`（科研经历文本）→ `expandTextToTerms()` 提取技术词推断方向

**`match(query, tierFilter, limit)`**：两级降级：FTS5 全文检索 → LIKE 模糊查询

**`sessionRerank(results, sessionEvents)`**（Session-based Re-ranking）：
- `hybrid_score = α×TF-IDF基础分 + β×cosine(session_vec, teacher_vec)`
- α=0.70，β=0.30，λ=0.80（时间衰减）

#### `analytics/AnalyticsService.java` ★ 核心修复
统计聚合服务（带 Spring Cache 缓存）。

**★ `keywordMatches(areas, keyword)` 新增——根治趋势图人机交互虚高**：

问题根源：原来用 `research_areas.contains("ar")` 做关键词匹配。当关键词是 `AR`/`VR` 等 2 字母缩写时，`contains` 会命中所有含 "ar" 的英文词——`Research`/`Software`/`Hardware`/`Transportation`/`Cardiovascular`/`Learning` 全部被误判为「人机交互」，导致海量非 CS 教师被计入，趋势图人机交互一条线撑爆 Y 轴（1600+）。

修复：新增 `keywordMatches()` 替换两处 `contains()`（`getAreaTrend()` 与 `getResearchAreas()`）：
- **纯 ASCII 短词（≤4 字符**，如 AR/VR/UI/UX/GPT/LLM/NLP/GNN/HCI）→ **单词边界匹配**：命中位置前后必须是非字母数字字符，`AR` 只匹配 `AR/VR` 不匹配 `Research`
- **中文关键词或长英文词（≥5 字符）** → 普通 `contains`（不会误命中）

真实数据验证：旧逻辑 10 个样本 6 个误判，新逻辑 0 个误判。

**★ `getResearchAreas()` 重构**：改用 `matchService.getDirExpand()` 的 21 方向 + 语义近邻词做关键词匹配（经 `keywordMatches` 过滤），每位教师对每个方向最多计 1 次。解决原来统计词频导致的「生物信息/医学1386异常」和「University/of/China垃圾词」问题。

**★ `getAreaTrend(minYear, maxYear)`**：从 `counts_by_year` 字段统计各方向历年活跃教师数（需重爬后才有数据），返回 `{years:[...], series:[{name, data:[...]}]}` 供折线图使用。

#### `analytics/AnalyticsController.java` ★ 重要更新
**★ FTS5 损坏自动修复**：`initFTS5()` 查询时捕获 `SQLITE_CORRUPT_VTAB`，自动调 `rebuildCorruptedFTS5()` 删除 shadow 表（teachers_fts_data/idx/docsize/config）后重建虚拟表，无需手动修复数据库。

**★ 新增 `GET /api/analytics/area-trend`**：
- 优先读本地 `getAreaTrend()` 数据（重爬后有）
- 无数据则实时调 OpenAlex API（10方向×10年共约100次请求，约20-30秒）
- 结果永久写入 `output/area_trend_cache.json`

**★ 新增 `DELETE /api/analytics/area-trend`**：手动清除缓存，前端"刷新数据"按钮调此接口。

**缓存策略**：永久有效（无TTL），只有手动清除或重启且缓存文件不存在时才重新拉取。

`OA_DIR_CONCEPTS` 列表定义10个 OpenAlex Concept ID（level≥2 精确 concept），避免使用顶层 C41008148（Computer Science）导致数据量异常偏高。

#### `scraper/IndirectSearch.java` ★ 核心重写
`parseOpenAlexBatchPage()` 改用 OpenAlex `topics` 的 field/subfield 结构化解析：

- **CS 过滤改用 `field`**：OpenAlex 2024 用 `topics`（4 层结构 `topic→subfield→field→domain`）替代旧 `x_concepts`。每个 topic 的 `field.display_name` 必须含 `Computer Science`（OpenAlex 26 个固定学科大类之一，金标准）。非 CS field 的教授（医学/交通/大气等）直接跳过，**彻底解决非 CS 教授混入**问题
- **研究方向改用 `subfield`**：`subfield.display_name` 是全球约 250 个规范方向名（CS 下约 15 个，如 `Artificial Intelligence`、`Computer Vision and Pattern Recognition`、`Human-Computer Interaction`），比细分 topic 名稳定。再附 1 个最具体 topic 名捕捉新方向（如 `Large Language Models`）
- **`OA_TOPIC_CN` 翻译表扩充**：以 subfield 规范名为主键，每条译「中文 英文」混合（如 `Artificial Intelligence` → `人工智能 机器学习 深度学习 Artificial Intelligence`），让中英文检索都能命中；新增大模型/具身智能/多模态等近年方向
- **counts_by_year 解析**：存为压缩 JSON `[{"y":2020,"w":3,"c":10}]`，只保留2015-2030年数据；从中计算 `activeYear`（最近有论文的年份）
- **research_areas 净化**：只存干净的研究方向词，不再混入 `引用:N 论文:N` 统计数字（根治「Sun Yat-sen」「论文:4」「Université」等垃圾词的根源）
- **降级方法 `scrapeViaOpenAlex`** 复用主方法解析，不再产生脏数据
- **AMiner 查询词从4个扩展到10个**
- **OpenAlex 429 限流重试**

#### `scraper/ScraperService.java` ★ 更新
爬虫调度层：`scrapeAll`（全量）/`scrapeByName`（按名匹配预设，并发）/`scrapeUnknown`（单个学校，含间接降级）。

**★ 预设院校间接降级（修复清华/北大等顶尖名校爬不到）**：

问题根源：爬虫有两个入口，原本只有一个会降级到间接搜索：
- 点击单个学校 → `scrapeUnknown` → edu.cn 失败**会**降级 OpenAlex/AMiner/知乎 ✓
- 批量 / 预设命中（清华、北大走这条）→ `scrapePresetUniversity` → **只做 edu.cn 直连，失败就返回 0，从不降级** ✗

清华、北大等在 `UniversityData.ALL` 里配了 edu.cn 的院系 URL，所以走预设分支，卡在 edu.cn 直连上。而 edu.cn 在境外 / cloudflared 隧道环境普遍连不上，导致越是顶尖名校反而越爬不到。

本次修复 `scrapePresetUniversity()`：
1. **快速失败**：连续 2 个院系列表页 `null` 就放弃直连（原来逐个超时，6 个 URL 耗约 4 分钟，前端 3 分钟就报"未爬到"警告）
2. **间接降级**：edu.cn 直连 0 位时，自动调 `scrapeFromIndirectSources`（OpenAlex 按英文校名拉全校 CS 教师 / AMiner / DBLP / 知乎），与 `scrapeUnknown` 的降级逻辑对齐

重启后点清华，日志会出现 `🔄 edu.cn 直连 0 位，启动间接搜索` 然后 `[L0] OpenAlex 机构: Tsinghua University`，几十秒内拿到上百位清华 CS 教师。

#### `scraper/` 包其余文件

| 文件 | 职责 |
|---|---|
| `ScraperCore.java` | abstract 父类：KNOWN_CS/FACULTY_PATHS 等静态常量；策略 A-H（edu.cn 直连）；Strategy N（招生通知）；★ N-2/N-6 短省名大学精确匹配 |
| `UniversityData.java` | 985/211 院校配置：强弱 com 标注、预设院系 URL、UNIV_EN_NAMES 英文名映射 |

#### `engine/FetchEngine.java`
HTTP 抓取 + HTML 解析：
- `NAME_BLACKLIST`：精确黑名单（导航词、机构词、活动词）
- `NAME_BADROOTS`：词根过滤（含"校友"/"基金"等）
- `isBadName(name)` 两层合并检查

---

### 前端

#### JS 模块结构（权责分离）

| 模块 | 文件 | 对外 API | 职责 |
|---|---|---|---|
| 导航初始化 | `core/nav.js` | 自动执行 | Lucide 图标加载（本地→CDN→降级），active 链接高亮 |
| 客户端认证 | `core/auth.js` | `Auth.check() / getUser() / logout()` | 检查登录状态，未登录跳转 login.html；导航栏右侧注入用户徽章 |
| 个人信息管理 | `core/profile.js` | `Profile.get/save/set/clear/loadIntoForm/selectChips/exportToFile/importFromFile` | localStorage CRUD，跨页面数据共享 |
| 语音录入（STT） | `features/voice.js` | `VoiceRecorder.init(opts)/start/stop/toggle/isActive` | 浏览器 SpeechRecognition 封装 |
| 语音合成（TTS） | `features/voice.js` | `VoiceSynth.speak(text,opts)/stop/toggle/isSpeaking` | CosyVoice 后端代理 → 浏览器 SpeechSynthesis 降级 |
| 收藏导师 | `features/favorites.js` | `Favorites.isFaved/toggle/remove/render/getAll` | localStorage 持久化，收藏列表渲染 |
| Session 追踪 | `features/session.js` | `SessionTracker.track/getEvents/clear` | sessionStorage 记录点击序列，配合后端重排 |
| DPI 修正 | `viewport-fix.js` | 自动执行 | 修正 Windows 125%/150% 缩放下布局变窄问题 |

#### `core/auth.js` ★ 更新

**引入规则**：所有需要登录保护的页面引入；`login.html` **不引入**（避免循环跳转）。

**★ 导航栏右侧布局**：`[头像+用户名] [⚙ 设置] [退出]`
- ⚙ 设置按钮：点击跳转 `settings.html`，悬停时变紫色，使用 Unicode `⚙` 字符（不依赖 Lucide 初始化时机）
- 设置链接已从导航栏（nav.js LINKS）移除，统一入口在头像旁

**登录检查流程**：
```
GET /api/auth/whoami
  ├─ 200 → 渲染导航栏用户徽章（头像+用户名+退出按钮）
  ├─ 401 → 跳转 /html/login.html?next=当前URL
  └─ 网络错误 → 静默警告，允许离线浏览
```

#### `VoiceRecorder.init(opts)` 参数说明

| 参数 | 说明 |
|---|---|
| `button` | 录音按钮的元素 id |
| `statusEl` | 状态文字显示的元素 id |
| `outputEl` | 文字输出的 textarea/input 的 id |
| `timerEl` | 计时器显示元素 id（可选）；30秒内绿色，1-3分钟黄色，3分钟后红色 |
| `lang` | 语言代码，默认 `zh-CN` |
| `onResult(text)` | 实时识别结果回调 |
| `onEnd(text)` | 录音结束回调 |

**Lucide 图标降级**（`core/nav.js`）：
```
1. 加载 /js/lucide.min.js（本地）
2. 失败 → 加载 unpkg CDN
3. 均失败 → CSS i[data-lucide]{display:none} 隐藏，纯文字导航
```

#### 页面子标签

| 页面 | 子标签 |
|---|---|
| `analytics.html` | 总览 ↔ 研究热度 ↔ 已爬院校 |
| `advisor-match.html` | 智能匹配 ↔ 收藏导师 |
| `interview.html` | 题库练习 ↔ 练习统计 ↔ 题库管理 |

#### HTML 页面详解

**`login.html`** ★：登录/注册双标签页。已登录时自动显示"已登录卡片"。不引入 `auth.js`。修复头像 `position:relative` 导致头像撑满视口的 Bug。

**`home.html`**：Hero + 统计条 + 功能模块网格。

**`index.html`**：院校导航。`loadDataFromApi()` 拉取院校，`render()` 筛选+渲染，`_openPBase(u)` 院校详情面板；面板内 `window.openChatWith(school,teacher)` 跳转 AI 顾问。★ 图例「博/硕不同」加 tooltip。

**`tools.html`** ★ 重大更新：申请追踪（夏令营/预推免/实验室三类型）、时间线、邮件生成、GPA 换算。各类型独立 localStorage。

**`advisor-match.html`** ★：
- `doMatch()` → POST `/api/recommend`（含 `sessionEvents`）
- 收藏系统：委托 `Favorites` 模块；★ 收藏按钮改为 `data-fav-idx` + `addEventListener`
- Session 追踪：点击"主页"链接时 `trackTeacherClick(teacher)` → 委托 `SessionTracker`
- ★ 补充 `id="api-status"` 元素和 `switchMatchTab` 函数

**`analytics.html`** ★ 重要修复：
- **面板嵌套 Bug 修复**
- **`api-status` 元素**补充定义
- **研究热度子页**：条形图改用 DIR_EXPAND 21个方向关键词匹配；新增折线图（近10年趋势）；图例移到外部
- **已爬院校子页**：接入 `/api/scrape/jobs` 接口

**`chat.html`** ★：流式输出；历史会话侧边栏；模型选择面板；`MODELS` 常量与 `ModelRegistry.java` 同步。

**`interview.html`** ★：
- 语音录音、AI 点评、自动朗读
- 练习记录：AI 点评完后自动保存，「练习统计」子标签可查看历史、折叠展开、逐条删除
- 各类别完成进度可折叠/展开
- 批量删除复位：每次重渲染后同步按钮状态
- 导入模式弹窗：skip/overwrite/replace_all

**`statement.html`** ★：4种场景×4种风格，AI 流式生成。

**★ 版本系统重构**：
- **四类型独立版本**：夏令营/直推免试/预推免/冬令营各自独立 localStorage key（`baoyan_stmt_summer_camp` 等），切换类型时版本栏和内容同步切换
- **横向滚动版本栏**：版本芯片超出宽度时横向滚动，滚动条隐藏；布局 `历史版本：[版本N]…[版本1] [清空] [字数]`
- **点击版本高亮**：`showVersion(i)` 通过 `.version-chip` class 正确切换 active 状态（修复之前内联样式无法找到元素的 bug）
- **换个版本**：直接调 `generate()` 重新生成，不受现有版本数量限制
- **加载时恢复**：页面加载时自动从 localStorage 读取对应类型的最新版本填入编辑区
- 版本上限5条，超出时自动移除最早版本

**`documents.html`** ★：
- ★ **AI 智能填充（异步模式）**：点击后立即返回 taskId，后台继续生成；前端每3秒轮询进度；生成完成自动下载；离开页面再回来自动恢复轮询（taskId 存 localStorage）
- ★ 简历模板也显示「研究方向」和「意向岗位/保研方向」字段
- ★ `loadProfile` 从 `targetDirs` 自动填入「意向岗位/保研方向」
- 套磁邮件编辑器：可编辑 textarea；三种预设风格；保存自定义模板；一键复制

**`profile.html`** ★：
- 简历上传 → AI 解析 → 弹窗预览（含获奖荣誉）→ 一键填入
- ★ 新增「获奖荣誉」字段（`p-awards`）：解析简历后自动填入，供 AI 填充简历「技能证书」栏
- ★ 新增「实践与志愿经历」字段（`p-practice`）：供个人陈述/推荐信补充
- ★ 新增「入学/毕业年月」字段（`p-edu-start`/`p-edu-end`）
- ★ **分字段保存**：每个 section 右上角有「仅保存此项」按钮，只更新该字段到 localStorage + 云端，不影响其他字段；按钮点击后显示「✅ 已保存」2秒后恢复
- 个人信息存 localStorage；登录后云同步
- 头像上传：压缩至 120×120 px
- 快捷键设置已迁移到 `settings.html`

**`settings.html`** ★ 新增：独立系统设置页。

**当前功能：自定义快捷键（键盘录制模式）**

| 操作 | 说明 |
|---|---|
| 点击录制区 | 进入录制模式，边框变紫，提示「按下快捷键…」 |
| 按键盘 | 自动捕获 Ctrl/Alt/Shift 任意组合键，标签样式显示（如 `Ctrl`+`B`） |
| 点击其他地方 | 取消录制，恢复原值 |
| 冲突检测 | 保存前自动检测重复快捷键，有冲突时拒绝保存并警告 |
| 恢复默认 | 恢复8个预设快捷键 |
| 保存 | 写入 localStorage `baoyan_hotkeys`，所有页面实时读取 |

默认快捷键（与 Markdown 编辑器一致）：Ctrl+B（加粗）/ Ctrl+I（斜体）/ Ctrl+U（下划线）/ Ctrl+K（链接）/ Ctrl+Shift+U（无序列表）/ Ctrl+Shift+O（有序列表）/ Alt+S（保存记录）/ Alt+N（新建记录）

**`profile.html` 数据流向**：

| 字段 | 自动填充到 |
|---|---|
| GPA / 专业排名 | `advisor-match.html` 表单 |
| 科研经历 | 导师匹配 + 邮件 + 面试点评背景 + 个人陈述 |
| 意向研究方向 | 导师匹配方向芯片自动选中 |
| 意向梯队 | 导师匹配梯队芯片自动选中 |
| 姓名/院校/专业/邮箱 | 邮件 + 个人陈述 + 文档生成 |
| 目标院校 | 文档生成 `f-target-school` + 个人陈述目标院校框 |
| 获奖荣誉 | AI 填充简历「技能证书」栏；个人陈述补充 |
| 实践经历 | 个人陈述、推荐信补充内容 |
| 入学/毕业年月 | 文档生成简历模板 |

---

## 启动步骤

```bash
# 1. 确认环境
java -version   # Java 17+
mvn --version   # Maven 3.6+

# 2. 启动
cd Web/
mvn spring-boot:run

# 3. 访问
http://localhost:8080/html/login.html
```

> ⚠️ 必须用 Spring Boot 地址（8080），不能用 VS Code Live Server（5500）。

**首次使用推荐顺序**：
1. 打开**登录页**，注册账号并登录
2. 打开**个人信息**，上传简历自动提取（科研经历/获奖/实践经历完整提取），或手动填写
3. 打开**设置**页，按需调整快捷键
4. 打开**院校导航**，选学校触发爬取
5. 打开**导师匹配**，选方向开始匹配
6. 用**工具箱申请追踪**记录各院校夏令营/预推免/实验室投递情况
7. 用**模拟面试**练习，**个人陈述**和**文档生成**（AI智能填充）准备材料

---

## 用户系统说明

### 认证机制

- Session 基于 HttpOnly Cookie（`BAOYAN_SESSION`），有效期 30 天
- 密码：`SHA-256(随机UUID盐 + 明文密码)`，盐和哈希分列存储，不存明文
- 修改密码后其他设备 session 自动失效，当前设备保持登录
- 启动时自动清理过期 session

### 数据库分工（单文件）

| 表分组 | 表名 | 用途 |
|---|---|---|
| 用户认证 | `users` / `user_sessions` | 账号信息、登录 Cookie |
| 用户数据 | `user_profiles` / `interview_progress` | 个人信息 JSON、头像、题目完成状态 |
| 爬虫数据 | `teachers` / `universities` / `departments` | 教师/院校/院系信息 |
| 招生信息 | `school_info` | 保研通知、经验贴 |
| 面试题库 | `interview_questions` | 共用题库（所有用户共享） |
| 面试记录 | `interview_records` | 每用户答案 + AI点评 + 用时（独立于AI顾问历史） |
| 辅助表 | `url_verify` / `scrape_log` | URL 验证结果、爬取日志 |

### 哪些数据需要登录

| 功能 | 是否需要登录 |
|---|---|
| 院校导航、导师匹配、数据分析 | 否（离线可用） |
| AI 顾问、文档生成、模拟面试（题库） | 否 |
| 面试进度同步（`/api/user/progress`） | 是 |
| 面试记录保存/查询（`/api/interview/records`） | 是 |
| 个人信息云同步（`/api/user/profile`） | 是 |
| 头像上传（`/api/user/avatar`） | 是 |

---

## AI 顾问配置

### 服务商说明

| 服务商 | 注册地址 | 费用 |
|---|---|---|
| 阿里云百炼 | https://bailian.console.aliyun.com | 新用户各模型送100万token |
| 智谱AI | https://open.bigmodel.cn | GLM-4-Flash **永久免费** |

### 配置方法

```properties
# src/main/resources/application.properties
aliyun.api.key=sk-xxxxxxxxxxxxxxxxxxxxxxxx
zhipu.api.key=xxxxxxxxxxxxxxxxxxxxxxxx
spring.datasource.url=jdbc:sqlite:output/faculty.db
```

### 自动切换逻辑

```
请求进来
  ├─ 手动选择了模型 → 直接用，失败直接报错
  └─ 自动模式 → 按 ModelRegistry.LLM 列表从上到下尝试
                  ├─ 200 成功 → 流式输出
                  ├─ 403/429+额度错误码 → 切下一个，弹 Toast
                  └─ 所有失败 → error 事件，提示充值或配智谱 Key
```

---

## API 接口一览

### 用户认证（`AuthController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册（body: username/password） |
| POST | `/api/auth/login` | 登录 → 设置 HttpOnly Cookie |
| POST | `/api/auth/logout` | 登出 |
| GET | `/api/auth/whoami` | 当前用户信息；401 = 未登录 |
| PUT | `/api/auth/password` | 修改密码 |

### 用户数据（`UserDataController`，需登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/user/profile` | 读取个人信息 JSON |
| PUT | `/api/user/profile` | 保存个人信息 |
| GET | `/api/user/progress` | 已完成面试题 ID 列表 |
| POST | `/api/user/progress` | 标记题目完成 |
| DELETE | `/api/user/progress/{id}` | 取消标记单题 |
| DELETE | `/api/user/progress` | 清空所有进度 |
| GET | `/api/user/avatar` | 读取头像 |
| PUT | `/api/user/avatar` | 保存头像（限 200KB） |

### 院校与教师（`ApiController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/university-data` | 全部院校数据 |
| GET | `/api/universities` | 院校列表 |
| GET | `/api/teachers` | 教师查询 |
| GET | `/api/teachers/stats` | 数据库统计 |
| GET | `/api/school-info` | 招生通知 |
| GET | `/api/search?q=` | 全局搜索 |
| GET | `/api/scrape/status/{university}` | 爬取状态（★ 含 elapsedSeconds） |
| GET | `/api/scrape/progress/{university}` | 爬取进度（★ 含 elapsedSeconds） |
| GET | `/api/scrape/jobs?limit=10` | ★ 最近爬取院校列表 |
| POST | `/api/scrape` | 触发爬虫 |
| POST | `/api/scrape/info` | 触发招生信息爬取 |

### 分析与匹配（`AnalyticsController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/analytics/overview` | 概览统计 |
| GET | `/api/analytics/distribution` | 强弱 com 分布 |
| GET | `/api/analytics/tiers` | 梯队分布 |
| GET | `/api/analytics/research-areas` | 研究方向热度（DIR_EXPAND关键词匹配） |
| GET | `/api/analytics/area-trend` | ★ 近10年研究方向趋势 |
| DELETE | `/api/analytics/area-trend` | ★ 手动清除趋势缓存 |
| GET | `/api/analytics/events` | SSE 实时事件流 |
| GET | `/api/match` | GET 方式匹配 |
| POST | `/api/recommend` | POST 方式匹配（含 sessionEvents） |

**`POST /api/recommend` 请求体**：
```json
{
  "dirs":          ["机器学习", "计算机视觉"],
  "tiers":         ["顶尖C9", "上游985"],
  "gpa":           3.9,
  "rankPct":       10,
  "background":    "做过ResNet改进，参与CVPR投稿",
  "sessionEvents": [{"teacherName":"张三","university":"浙大","researchAreas":"...","timestamp":1700000000000}],
  "limit":         10
}
```

### AI 顾问（`ChatController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/chat` | 流式对话，返回 SSE 流 |
| GET | `/api/chat/config` | 查询已配置服务商 |
| GET | `/api/chat/sessions` | 历史会话列表 |
| GET | `/api/chat/sessions/{id}` | 单会话详情 |
| DELETE | `/api/chat/sessions/{id}` | 删除指定会话 |
| DELETE | `/api/chat/sessions` | 清空全部会话 |

### 文档生成（`DocumentController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/documents/templates` | 内置+自定义模板列表 |
| POST | `/api/documents/generate` | 填充模板，返回 .docx |
| POST | `/api/documents/generate/ai` | ★ AI智能填充（同步版），等待完成返回 .docx |
| POST | `/api/documents/generate/ai/async` | ★ AI智能填充（异步版），立即返回 `{"taskId":"xxx"}` |
| GET | `/api/documents/task/{taskId}/status` | ★ 查询异步任务进度 |
| GET | `/api/documents/task/{taskId}/download` | ★ 下载异步任务生成的 .docx |
| POST | `/api/documents/generate/pdf` | 填充后转 PDF（需 LibreOffice） |
| POST | `/api/documents/templates/upload` | 上传自定义 .docx 模板 |
| DELETE | `/api/documents/templates/custom/{n}` | 删除自定义模板 |

### 简历解析（`ResumeExtractController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/profile/extract-from-resume` | 上传简历，返回结构化 JSON（★ experience/awards 支持数组格式） |
| DELETE | `/api/profile/clear-personal-data` | 删除 output/custom-templates/ 个人文件 |

### 面试题库（`InterviewController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/interview/questions` | 题目列表 |
| GET | `/api/interview/categories` | 所有分类列表 |
| POST | `/api/interview/questions` | 添加单题 |
| PUT | `/api/interview/questions/{id}` | 编辑题目 |
| DELETE | `/api/interview/questions/{id}` | 删除题目 |
| DELETE | `/api/interview/questions/batch` | ★ 批量删除（body: `{ids:[...]}`） |
| POST | `/api/interview/questions/import` | 批量导入（CSV/TXT/JSON/DOCX/PDF） |

### 面试练习记录（`InterviewController`，需登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/interview/records` | 保存练习记录 |
| GET | `/api/interview/records` | 查询所有记录（`?limit=100`） |
| DELETE | `/api/interview/records/{id}` | 删除单条记录 |
| DELETE | `/api/interview/records` | 清空所有记录 |

### 语音合成（`SpeechController`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/speech/voices` | 可用音色列表 |
| POST | `/api/speech/synthesize` | 文字转 mp3 |

---

## 数据模型

### `users` 表

| 列名 | 类型 | 说明 |
|---|---|---|
| `id` | INTEGER | 主键（自增） |
| `username` | TEXT | 用户名（UNIQUE） |
| `password` | TEXT | SHA-256 哈希值 |
| `salt` | TEXT | 随机 UUID 盐 |
| `created_at` | TEXT | 注册时间 |

### `user_sessions` 表

| 列名 | 类型 | 说明 |
|---|---|---|
| `token` | TEXT | Session Token（主键，UUID） |
| `user_id` | INTEGER | 关联 users.id（CASCADE DELETE） |
| `expires_at` | TEXT | 过期时间（登录时 +30天） |

### `user_profiles` 表

| 列名 | 类型 | 说明 |
|---|---|---|
| `user_id` | INTEGER | 主键，关联 users.id |
| `profile_json` | TEXT | 个人信息 JSON 字符串 |
| `avatar_b64` | TEXT | 头像 base64 data URL（120×120 px，限 200KB） |
| `updated_at` | TEXT | 最后更新时间 |

### `teachers` 表 ★ 更新

| 列名 | 类型 | 说明 |
|---|---|---|
| `id` | INTEGER | 主键 |
| `name` | TEXT | 姓名 |
| `university` | TEXT | 院校 |
| `department` | TEXT | 院系 |
| `profile_url` | TEXT UNIQUE | 主页 |
| `title` | TEXT | 职称 |
| `research_areas` | TEXT | 研究方向（重爬后为净化数据，subfield 规范名，不含统计数字） |
| `email` | TEXT | 邮箱 |
| `google_scholar` | TEXT | Scholar 链接 |
| `lab_name` | TEXT | 课题组 |
| `recruiting` | INTEGER | 是否招保研（0/1） |
| `scraped_at` | TEXT | 爬取时间 |
| `cited_count` | INTEGER | ★ 被引总次数（OpenAlex） |
| `works_count` | INTEGER | ★ 论文总数 |
| `active_year` | INTEGER | ★ 最近活跃年份 |
| `counts_by_year` | TEXT | ★ 历年论文/引用 JSON |

### `interview_questions` 表

| 列名 | 类型 | 说明 |
|---|---|---|
| `id` | INTEGER | 主键（自增） |
| `category` | TEXT | 分类 |
| `question` | TEXT | 题目内容 |
| `hint` | TEXT | 思路提示（可空） |
| `source` | TEXT | 来源：builtin/manual/ai_chat/import |
| `created_at` | TEXT | 创建时间 |

### `interview_records` 表

| 列名 | 类型 | 说明 |
|---|---|---|
| `id` | INTEGER | 主键（自增） |
| `user_id` | INTEGER | 关联 users.id（CASCADE DELETE） |
| `question_id` | TEXT | 题目 ID |
| `question_text` | TEXT | 题目完整内容（冗余存储，方便回顾） |
| `category` | TEXT | 题目分类 |
| `answer` | TEXT | 用户的回答文本 |
| `feedback` | TEXT | AI 点评全文 |
| `duration_sec` | INTEGER | 作答用时（秒） |
| `created_at` | TEXT | 练习时间 |

### `chat_sessions` / `chat_messages` 表

| 表 | 关键列 | 说明 |
|---|---|---|
| `chat_sessions` | id/title/created_at/updated_at/message_count | 会话元数据，最多保留 50 条 |
| `chat_messages` | id/session_id/role/content/model/created_at | 对话消息 |

### 申请追踪（纯前端 localStorage）

| Key | 类型 | 字段 |
|---|---|---|
| `baoyan_camps_v2` | 夏令营数组 | id/name/school/sent/sentDate/deadline/reply/priority/info/dropReason/starred/addedAt |
| `baoyan_prec_v1` | 预推免数组 | 同上 + extra（联系方式） |
| `baoyan_lab_v1` | 实验室数组 | 同上 + extra（导师姓名） |
| `baoyan_hotkeys` | 快捷键配置数组 | `[{key, action, label}, ...]` |
| `baoyan_stmt_summer_camp` | 夏令营个人陈述历史 | ★ 最多5条版本（各类型独立） |
| `baoyan_stmt_direct_rec` | 直推免试个人陈述历史 | ★ 最多5条版本 |
| `baoyan_stmt_pre_rec` | 预推免个人陈述历史 | ★ 最多5条版本 |
| `baoyan_stmt_winter_camp` | 冬令营个人陈述历史 | ★ 最多5条版本 |
| `baoyan_ai_task` | AI填充进行中的任务 | `{id, key}`，页面加载时自动恢复轮询 |

---

## 我想加功能，改哪个文件？

| 想做的事 | 改哪里 |
|---|---|
| **加新 REST 接口（院校/教师）** | `api/ApiController.java` |
| **加新统计图表** | `analytics/AnalyticsService.java` + `AnalyticsController.java` + `analytics.html` |
| **改趋势图/方向匹配的关键词逻辑** | `analytics/AnalyticsService.java` → `keywordMatches()`（短英文缩写边界匹配 + 中文/长词 contains） |
| **调整 OpenAlex CS 过滤/方向分类** | `scraper/IndirectSearch.java` → `parseOpenAlexBatchPage()`（field 过滤 + subfield 分类） |
| **扩展某方向的英文匹配词** | `analytics/MatchService.java` → `DIR_EXPAND` 对应方向加英文 subfield 名 |
| **改预设院校直连/降级策略** | `scraper/ScraperService.java` → `scrapePresetUniversity()`（快速失败阈值 + 间接降级） |
| **改匹配算法权重** | `analytics/MatchService.java` → `parallelScore()` |
| **改梯队过滤 SQL** | `analytics/MatchService.java` → `fts5Recall()` / `likeRecall()` |
| **扩展语义方向扩展词** | `analytics/MatchService.java` → `DIR_EXPAND` 静态 Map |
| **调整 Session 重排权重** | `analytics/MatchService.java` → `α=0.70 β=0.30 λ=0.80` |
| **改趋势图查询方向** | `analytics/AnalyticsController.java` → `OA_DIR_CONCEPTS` |
| **清除趋势缓存** | `DELETE /api/analytics/area-trend` 或删除 `output/area_trend_cache.json` |
| **修 FTS5 全文索引损坏** | 自动自愈（`DatabaseService.initSchema()` catch + `AnalyticsController.rebuildCorruptedFTS5()`），或手动删 `teachers_fts*` 表 |
| **扩展 OpenAlex 英中翻译** | `scraper/IndirectSearch.java` → `OA_TOPIC_CN` 静态 Map |
| **改爬虫姓名过滤** | `engine/FetchEngine.java` → `NAME_BLACKLIST`/`NAME_BADROOTS`/`isBadName()` |
| **添加新院校到爬虫** | `scraper/UniversityData.java` → `ALL` 列表；同步更新 `init_data.sql` |
| **添加院校已知教师页 URL** | `scraper/ScraperCore.java` → `KNOWN_CS` 静态 Map |
| **改爬虫短省名大学精确匹配** | `scraper/ScraperCore.java` → N-2/N-6 标题过滤逻辑 |
| **扩展 AMiner 查询词** | `scraper/IndirectSearch.java` → `buildAminerQueries()` |
| **改间接搜索策略/顺序** | `scraper/IndirectSearch.java` → `scrapeFromIndirectSources()` |
| **改推荐打分维度** | `engine/RecommendationEngine.java` |
| **给 teachers 表加字段** | `db/DatabaseService.java` → `initSchema()` + `model/Teacher.java` |
| **给用户系统表加字段** | `db/DatabaseService.java` → 用户相关建表语句 + 对应 Controller |
| **清理脏数据** | `db/DatabaseService.java` → `initSchema()` 末尾的 DELETE 语句 |
| **改 AI 系统提示词** | `chat/ChatController.java` → `buildMessages()` |
| **增减 AI 可用模型** | `chat/ModelRegistry.java` → `LLM` 列表；同步 `chat.html` 的 `MODELS` 常量 |
| **改模型切换错误码判断** | `chat/ModelRegistry.java` → `isQuotaExhausted()` |
| **接入新 AI 服务商** | `chat/ModelRegistry.java` 加 `Provider` 枚举 + `ChatController.java` 的 `getKey()` |
| **改历史会话保留上限** | `chat/ChatHistoryService.java` → `MAX_SESSIONS`（默认 50） |
| **改 TTS 音色列表** | `chat/SpeechController.java` → `listVoices()` |
| **改简历解析 AI 提示词** | `api/ResumeExtractController.java` → `buildExtractionPrompt()` |
| **改简历解析 token 上限** | `api/ResumeExtractController.java` → `max_tokens`（当前 6000） |
| **改简历解析支持格式** | `api/ResumeExtractController.java` → `extractDocxText`/`extractPdfText` |
| **加新文档生成模板** | `src/main/resources/templates/` 放 .docx + `DocumentController.java` → `BUILT_IN` |
| **改文档占位符替换规则** | `api/DocumentController.java` → `PLACEHOLDER_RULES` |
| **改 AI 填充 prompt（保研视角）** | `api/DocumentController.java` → `buildAIFillPrompt()` |
| **改 AI 填充段落覆盖规则** | `api/DocumentController.java` → `buildExtraRules()` |
| **改 CSV 导入过滤规则** | `api/InterviewController.java` → `parseCsv()` |
| **改面试题库内置题目** | `db/DatabaseService.java` → `initSchema()` 内置题目数组（仅题库为空时生效） |
| **加面试题批量导入格式** | `api/InterviewController.java` → `parseCsv`/`parseTxt`/`parseJson` |
| **改练习历史展示样式** | `html/interview.html` → `renderRecords()` 函数 |
| **改练习历史保留数量** | `html/interview.html` → `loadRecordsAndRender()` 里的 `?limit=100` |
| **改追踪类型字段/状态/颜色** | `html/tools.html` → `TRACK_TYPES` 对象 |
| **改快捷键默认配置** | `html/settings.html` → `HK_DEFAULTS` |
| **加新快捷键动作** | `html/settings.html` → `HK_ACTIONS` + `html/tools.html` → `trmKeydown()` |
| **改套磁邮件预设风格** | `documents.html` → `EMAIL_STYLES` 对象 |
| **改用户注册规则** | `api/AuthController.java` → `register()` 的校验逻辑 |
| **改 Session 有效期** | `api/AuthController.java` → `SESSION_DAYS`（默认 30） |
| **改登录页样式/布局** | `html/login.html` → `<style>` 块 |
| **改导航栏用户徽章样式** | `js/core/auth.js` → `_renderUserBadge()` |
| **改个人信息云同步逻辑** | `api/UserDataController.java` + `html/profile.html` |
| **改头像尺寸限制** | `api/UserDataController.java` → `saveAvatar()`；前端 `login.html` → `uploadAvatar()` |
| **改语音录制行为** | `js/features/voice.js` → `VoiceRecorder` |
| **改 TTS 朗读行为** | `js/features/voice.js` → `VoiceSynth.speak()` |
| **改个人信息存储字段** | `profile.html` 表单 + `js/core/profile.js` → `loadIntoForm` 字段映射 |
| **加个人信息新字段（获奖/实践）** | `html/profile.html` → 新增字段 + `getProfileData()` + `loadProfileData()` + `applyExtracted()` |
| **改导航栏图标** | 各 HTML → `<i data-lucide="icon-name">`；图标名见 lucide.dev/icons |
| **改导航栏链接/顺序** | `js/core/nav.js` → `LINKS` 数组 |
| **改全局配色** | `frontend.css` → `:root` CSS 变量 |
| **改院校卡片样式/字段** | `frontend.js` → `render()` |
| **改院校详情面板** | `frontend.js` → `_openPBase()` |
| **改爬取进度显示/时间** | `frontend.js` → `continuePanelPoll()` / `triggerSearchScrape()` |
| **添加全新页面** | 新建 `static/html/xxx.html`，引入 `core/nav.js` 和 `core/auth.js`；在 `core/nav.js` 的 `LINKS` 数组加入链接 |
| **改收藏导师渲染样式** | `js/features/favorites.js` → `render()` |
| **改 Session 追踪上限** | `js/features/session.js` → `MAX = 20` 常量 |
| **改导师匹配方向芯片** | `advisor-match.html` 芯片区 + `MatchService.DIR_EXPAND` 键名同步 |
| **改 AI 顾问快捷提问** | `chat.html` → `.chip-row` 区域 |
| **改个人陈述生成场景** | `statement.html` → `.type-btn[data-v]` 列表 + TYPE_LABELS Map |
| **改文档生成模板卡片** | `documents.html` → `ICONS` Map + `renderTemplateGrid()` |
| **改 `output/custom-templates/` 存储路径** | `api/DocumentController.java` → `CUSTOM_DIR` 常量 |
| **修复 Windows DPI 缩放布局问题** | `js/viewport-fix.js` |
| **改个人陈述版本上限** | `html/statement.html` → `generate()` 里的 `if(versions.length > 5) versions.pop()` |
| **改个人陈述版本存储 key** | `html/statement.html` → `verKey()` 函数返回值 |
| **改 AI 填充任务缓存时长（30分钟）** | `api/DocumentController.java` → `AiTask` 清理逻辑 `30 * 60 * 1000L` |
| **改 AI 填充前端轮询间隔（3秒）** | `html/documents.html` → `startAIPolling()` 里的 `setInterval(..., 3000)` |
| **改设置页默认快捷键** | `html/settings.html` → `HK_DEFAULTS` 数组 |
| **加设置页新功能区块** | `html/settings.html` → 复制「外观与语言」section 结构 |
| **改头像旁设置按钮样式** | `js/core/auth.js` → `_renderUserBadge()` 里的 settingsBtn 样式 |
| **给 profile.html 加新 section 的分字段保存** | `html/profile.html` → 新增 section 后在 `saveSection()` 的 `keyMap` 里加字段映射 |
| **改简历解析弹窗显示字段** | `html/profile.html` → `showExtractModal()` + 弹窗 HTML 的 `.ex-field` |