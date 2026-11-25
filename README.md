# AgentCareer

AgentCareer is a Spring Boot backend for an AI interview and career coaching workflow. The repository keeps the runtime governance code that makes the system observable and resilient, including trace persistence, prompt runtime metrics, fallback handling, and stage-level orchestration telemetry.

Frontend lives in [`frontend/`](frontend/README.md): a zero-build static SPA (native ES modules, no npm dependencies) that talks to the backend over same-origin `/api/**`.

---

## 本地运行（VSCode 终端）

下面命令都在 VSCode 的**集成终端**里执行。Windows 默认是 PowerShell，macOS/Linux 是 bash，两种写法都给了。

> 需要**两个终端**：一个跑后端（占用 8080），一个跑前端（占用 5173）。

### 0. 前置要求

| 依赖 | 要求 | 说明 |
|---|---|---|
| JDK | **21** | `pom.xml` 要求 Java 21。先执行 `java -version` 确认，不能是 8/17 |
| Node.js | 18+ | 前端零依赖，只需要 `node`，不需要 `npm install` |
| MySQL | 8.x | 库名默认 `career_mind`，连接信息见 `application.yml` |
| Redis | 7.x | **可选**。没起也能跑：限流、短期缓存、幂等会降级为进程内实现 |
| RabbitMQ | 3.x | **可选**。没起也能跑：面试结束后的闪卡/画像合并/图谱索引不会生成（状态停在 `PENDING`） |
| PostgreSQL + pgvector | 可选 | 向量检索用。没有这个库时见下方「常见报错」里的开关 |
| 模型 API Key | 必须 | 没有 key 后端**启动就会失败**（`AiConfig` 校验），填法见下一节 |

### 0.1 模型 Key 填在哪（两套模型客户端，key 不一定相同）

| 用途 | 谁在用 | 配置项（`application.yml`） | 对应环境变量 |
|---|---|---|---|
| 面试官本体：出题、追问、调用工具 | `agentscope` harness（[InterviewAgentConfig.java](src/main/java/com/ai/interview/config/InterviewAgentConfig.java)） | `agentscope.model.api-key` / `base-url` / `name` | `AGENTSCOPE_MODEL_API_KEY` / `AGENTSCOPE_MODEL_BASE_URL` / `AGENTSCOPE_MODEL_NAME` |
| 简历解析、评分、知识提炼 | `langchain4j`（[AiConfig.java](src/main/java/com/config/AiConfig.java)） | `langchain4j.open-ai.chat-model.api-key` / `base-url` / `model-name` | `DASHSCOPE_API_KEY`（没填时复用 `AGENTSCOPE_MODEL_API_KEY`）/ `AI_CHAT_BASE_URL` / `AI_CHAT_MODEL` |

> ⚠️ **最常见的一次踩坑**：`key` 和 `base-url` 必须是**同一家厂商**。
> 例如把智谱的 key（形如 `32位hex.16位`）配到 `https://dashscope.aliyuncs.com/...` 上，
> 表现就是简历解析报 `模型没有返回可用的简历画像`，后端日志里能看到 `Incorrect API key provided`。

**各家服务怎么填（两套一起换）**

| 厂商 | key 形态 | base-url | model |
|---|---|---|---|
| 阿里云百炼 DashScope | `sk-…` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
| DeepSeek | `sk-…` | `https://api.deepseek.com/v1` | `deepseek-chat` |
| 智谱 GLM | `32位hex.16位` | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-flash` / `glm-4-plus` |
| 月之暗面 Kimi | `sk-…` | `https://api.moonshot.cn/v1` | `moonshot-v1-8k` |
| 硅基流动 | `sk-…` | `https://api.siliconflow.cn/v1` | `Qwen/Qwen2.5-72B-Instruct` |

**推荐做法：用仓库根目录的 `.env`**

```powershell
Copy-Item .env.example .env      # 只需一次
notepad .env                     # 填 key + base-url + 模型名
```

```ini
# A. 面试官（示例：智谱 GLM）
AGENTSCOPE_MODEL_API_KEY=<你的 key>
AGENTSCOPE_MODEL_BASE_URL=https://open.bigmodel.cn/api/paas/v4
AGENTSCOPE_MODEL_NAME=glm-4-flash

# B. 简历解析/评分（同一家的话就填同一个 key）
DASHSCOPE_API_KEY=<你的 key>
AI_CHAT_BASE_URL=https://open.bigmodel.cn/api/paas/v4
AI_CHAT_MODEL=glm-4-flash
```

要点：

- `.env` 已在 [.gitignore](.gitignore) 里，不会提交
- 靠 `application.yml` 的 `spring.config.import: optional:file:./.env[.properties]` 加载，所以 `.env` 必须放在**启动时的工作目录**（在仓库根跑 `mvnw` 就是仓库根）
- `DASHSCOPE_API_KEY` 留空时会自动复用 `AGENTSCOPE_MODEL_API_KEY`，所以只想用一个 key 时只填 A 组即可
- 不想用 `.env` 就直接设同名**环境变量**，或者启动时加 `-D` 参数（见第 2 节），优先级：命令行 `-D` > 环境变量 > `.env`
- **embedding 是可选的**：DeepSeek 没有 embedding 接口，此时向量召回降级（`MemoryService` 会打印 `写入向量库失败，已保留 MySQL 记忆记录`），面试与复盘照常；智谱/百炼都提供 embedding，可用 `AI_EMBEDDING_BASE_URL` / `AI_EMBEDDING_MODEL` 指过去

### 1. 初始化数据库

迁移脚本是纯 SQL，项目没有接 Flyway，需要手工执行一次（**已执行过的跳过**，重复执行会报 `Duplicate column`）。

PowerShell：

```powershell
$mysql = "mysql"   # 或在 PATH 里找不到时写全路径，例如 D:\Mysql\mysql-8.0.34-winx64\bin\mysql.exe
Get-Content -Raw src\main\resources\db\migration\V20260525_01__add_interview_transcript_and_resume_text.sql |
  & $mysql --host=127.0.0.1 --user=root --password=12345 --database=career_mind --default-character-set=utf8mb4

Get-Content -Raw src\main\resources\db\migration\V20260526_01__add_interview_post_process_status.sql |
  & $mysql --host=127.0.0.1 --user=root --password=12345 --database=career_mind

Get-Content -Raw src\main\resources\db\migration\V20261123_01__add_resume_library.sql |
  & $mysql --host=127.0.0.1 --user=root --password=12345 --database=career_mind --default-character-set=utf8mb4
```

bash：

```bash
mysql -h127.0.0.1 -uroot -p12345 career_mind < src/main/resources/db/migration/V20260525_01__add_interview_transcript_and_resume_text.sql
mysql -h127.0.0.1 -uroot -p12345 career_mind < src/main/resources/db/migration/V20260526_01__add_interview_post_process_status.sql
mysql -h127.0.0.1 -uroot -p12345 career_mind < src/main/resources/db/migration/V20261123_01__add_resume_library.sql
```

`V20261123_01` 是**幂等**的（用 `information_schema` 判断后再 `ALTER`），可以放心重复执行；它建 `resume` 表并给 `interview_session` / `interview_message` 补列。

### 2. 启动后端（终端 A）

如果已经按 0.1 节填好 `.env`，这里就一行（在**仓库根目录**执行）：

```powershell
.\mvnw.cmd spring-boot:run
```

```bash
./mvnw spring-boot:run
```

不想用 `.env` 时，改成临时环境变量：

```powershell
$env:DASHSCOPE_API_KEY          = "你的 dashscope key"
$env:AGENTSCOPE_MODEL_API_KEY   = "你的 deepseek key"   # 不跑面试可以省略
$env:PGVECTOR_INITIALIZE_SCHEMA = "false"               # 没有 career_vector 库时必须加

.\mvnw.cmd spring-boot:run
```

```bash
export DASHSCOPE_API_KEY="你的 dashscope key"
export AGENTSCOPE_MODEL_API_KEY="你的 deepseek key"
export PGVECTOR_INITIALIZE_SCHEMA=false

./mvnw spring-boot:run
```

或者直接命令行传参（优先级最高）：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--agentscope.model.api-key=sk-xxx --DASHSCOPE_API_KEY=sk-yyy"
```

成功的标志（日志最后两行）：

```
Tomcat started on port 8080 (http) with context path '/'
Started CareerSynapseApplication in 24.4 seconds
```

健康检查（不需要 actuator，直接打业务接口）：

```powershell
curl.exe -s "http://127.0.0.1:8080/api/dashboard?userId=1"
```

返回 `{"code":0,...}` 即后端可用。

### 3. 启动前端（终端 B）

```bash
cd frontend
node serve.mjs
```

打开 <http://localhost:5173> 即可。

`serve.mjs` 做了两件事：托管静态文件 + 把 `/api/**` 反代到 `http://127.0.0.1:8080`。所以：

- **不要**用 `file://` 直接双击 `index.html`（前端只发同源请求，`file://` 下必然失败）
- 想换端口：`node serve.mjs 8081`
- 后端不在 8080：`API_TARGET=http://127.0.0.1:9090 node serve.mjs`（PowerShell：`$env:API_TARGET="http://127.0.0.1:9090"; node serve.mjs`）

### 4. 端到端走一遍

1. 「简历库」→ 上传 PDF/DOCX/TXT 或粘贴文本 → 自动解析出技能水位与项目
2. 「模拟面试」→ 选目标岗位、主题、**关联简历** → 开始面试
3. 回答几轮 → 结束面试
4. 「面试报告」看复盘（对话、评分、闪卡、下一步），「概览」「复习训练」「记忆与画像」会自动更新

### 5. 其他常用命令

```powershell
# 确认 JDK 21（本机 PATH 里可能先命中 JDK 8/17）
java -version
$env:JAVA_HOME          # mvnw 用的是这个

# 全量测试（31 个用例）
.\mvnw.cmd test

# 只跑某个测试
.\mvnw.cmd test -Dtest=ResumeTextExtractorTest

# 打包成可执行 jar，用 java -jar 启动（比 spring-boot:run 少一个 JVM，更省内存）
.\mvnw.cmd -DskipTests package
# 注意：这里必须用 JDK 21 的 java，直接用 PATH 里的 java 可能命中 JDK 8
& "$env:JAVA_HOME\bin\java.exe" -jar target\agent-career-0.0.1-SNAPSHOT.jar
```

```bash
# 用 docker 起中间件（MySQL / Redis / pgvector）
docker compose up -d mysql redis postgres
```

### 5.1 启动 RabbitMQ（可选）

只有「面试结束后的后处理」需要它：**闪卡生成、画像合并、知识图谱索引**。不起也能跑面试和看复盘，只是会话的 `post_process_status` 会一直停在 `PENDING`，并且后端日志每 5 秒刷一次 `Attempting to connect to: [localhost:5672]`。

**方式 A：Docker（推荐，先启动 Docker Desktop）**

```powershell
docker compose up -d rabbitmq
# 等价的手写命令：
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management
```

管理台：<http://localhost:15672>（默认 `guest` / `guest`）。

**方式 B：Windows 原生安装（会注册成系统服务，开机自启）**

```powershell
winget install --id RabbitMQ.RabbitMQ -e
# 安装后：
rabbitmq-service start        # 或 net start RabbitMQ
```

RabbitMQ 依赖 Erlang，`winget` 的安装包一般会一并处理；如果提示缺 Erlang，先 `winget install Erlang/Erlang`。

**方式 C：WSL2 里装（需要已有发行版）**

```bash
wsl -d Ubuntu -u root -- apt update
wsl -d Ubuntu -u root -- apt install -y rabbitmq-server
wsl -d Ubuntu -u root -- service rabbitmq-server start
```

> WSL 的网络默认与 Windows 共享 localhost，所以后端的 `localhost:5672` 能直接连上。

**方式 D：不想装，只想让日志安静**

在 `.env` 里加一行后重启后端：

```ini
RABBITMQ_LISTENER_ENABLED=false
```

这样消费者不启动，日志干净；面试、复盘报告、复习训练都正常，只有后处理不执行。以后起了 MQ，把这一行删掉（或改成 `true`）重启即可。

启动后验证：

```powershell
Test-NetConnection 127.0.0.1 -Port 5672 -InformationLevel Quiet   # 期望 True
```

### 5.2 向量检索（pgvector，可选）

记忆片段的向量召回依赖 **pgvector 的 PostgreSQL** + 一个 **embedding 接口**。两者任一缺失，`MemoryService` 每次写片段都会打一条 `写入向量库失败，已保留 MySQL 记忆记录` 的 WARN（不影响面试与复盘，但会刷日志、并且白调一次 embedding）。

本机没有 pgvector 时，直接关掉：

```ini
VECTOR_STORE_ENABLED=false
```

关掉后 `VectorStoreService` 完全跳过：不建表、不调 embedding、不写库，记忆只落 MySQL，`/api/memory/search` 走关键词检索，知识图谱/复习/复盘都不受影响。

想真正启用（三样都要齐）：

```powershell
# 1) 起一个带 pgvector 的 PG。本机 5432 已被原生 PostgreSQL 占用，这里换 5433
docker run -d --name pgvector -p 5433:5432 `
  -e POSTGRES_DB=career_vector -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=12345 `
  pgvector/pgvector:pg16
```

```ini
# 2) .env
VECTOR_STORE_ENABLED=true
PGVECTOR_PORT=5433
PGVECTOR_INITIALIZE_SCHEMA=true          # 首次启动自动建表 + CREATE EXTENSION vector
AI_EMBEDDING_BASE_URL=https://open.bigmodel.cn/api/paas/v4   # 智谱 embedding-3（DeepSeek 没有 embedding）
AI_EMBEDDING_MODEL=embedding-3
AI_EMBEDDING_DIMENSIONS=1024
```

> `AI_EMBEDDING_DIMENSIONS` 必须和模型实际维度一致，否则会报 `Embedding 维度不匹配`。

### 6. 常见报错

| 报错 | 原因 | 处理 |
|---|---|---|
| `openAiApiKey cannot be null or empty` | 没设 `DASHSCOPE_API_KEY` | 按第 2 步设置环境变量后重启 |
| `数据库 "career_vector" 不存在` | pgvector 初始化连不上 PG | 建库 `CREATE DATABASE career_vector;` + `CREATE EXTENSION vector;`，或设 `PGVECTOR_INITIALIZE_SCHEMA=false` |
| `Failed to create root directory: ...\.agentscope` | harness 要在 `~/.agentscope` 下建状态目录，家目录只读 / 容器 / 受限环境会失败 | 换一个可写目录：`.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Duser.home=D:\writable-home"`；或 `java -Duser.home=D:\writable-home -jar target\agent-career-0.0.1-SNAPSHOT.jar` |
| `Error: could not open '...\lib\jvm.cfg'` | IDE/脚本指向了下载不完整的 JDK | 换一个完整的 JDK 21（`lib\jvm.cfg` 必须存在） |
| `无效的目标发行版: 21` / `release version 21 not supported` | `JAVA_HOME` 或 IDE 的 Project SDK 不是 21 | 换 JDK 21；VSCode 见下节 |
| 前端页面能开，但接口全部 502 | 后端没起，或端口不是 8080 | 先起后端；换端口就用 `API_TARGET` |
| 面试能开始，但复习/闪卡一直是空 | RabbitMQ 没起（后处理没消费） | 起 RabbitMQ（见 5.1），或先接受这个降级 |
| 日志每 5 秒刷 `Attempting to connect to: [localhost:5672]` + `Failed to check/redeclare auto-delete queue(s)` | RabbitMQ 没起，消费者在重连（不影响接口） | 起 RabbitMQ（见 5.1）；不想装就在 `.env` 加 `RABBITMQ_LISTENER_ENABLED=false` 后重启 |
| 反复出现 `OpenAiEmbeddingModel` / `Incorrect API key provided` + `写入向量库失败` | embedding 走的是阿里云，但 key 是别家的；或 pgvector 库不存在 | 本机不打算用向量检索就设 `VECTOR_STORE_ENABLED=false`；要用见 5.2（换 pgvector + 配同厂商 embedding） |
| `Could not initialize plugin: MockMaker`（跑测试时） | JVM 不允许自附加（沙箱/受限环境） | 加 `-DargLine=-javaagent:<path-to-byte-buddy-agent.jar>` |
| 端口 8080 / 5173 被占用 | 上次进程没退 | `netstat -ano | findstr :8080` 找到 PID 后 `taskkill /PID <pid> /F` |

### 7. VSCode 里选 JDK（避免"全红"）

VSCode 的 Java 扩展是独立于终端 `java -version` 的，需要单独指定 JDK 21。推荐在 `.vscode/settings.json` 里加：

```jsonc
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.jdt.ls.java.home": "C:\\Users\\<你>\\.jdks\\ms-21.0.8",
  "java.configuration.runtimes": [
    { "name": "JavaSE-21", "path": "C:\\Users\\<你>\\.jdks\\ms-21.0.8", "default": true }
  ]
}
```

改完执行命令面板的 **Java: Clean Java Language Server Workspace**，再点 **Reload All Maven Projects**（Maven 面板）。依赖来源是 `pom.xml`，本机 Maven 仓库默认 `~/.m2/repository`，也可以用 `-Dmaven.repo.local=` 指定成别处。

---

## What stays in the repo

- Core application code, tests, and migration scripts
- Runtime observability code such as `TraceContext`, `TracePersistenceService`, `TraceAnalyticsService`, `PromptRuntimeTracker`, and orchestration metrics
- Benchmark definitions and reusable datasets that explain how retrieval, runtime trace, and multi-agent decision experiments are evaluated

## What does not stay in the repo

- Generated benchmark run logs such as `*_run_log.csv`
- One-off benchmark result summaries produced from local experiments

Benchmark tests are written so the repository remains self-contained even when generated run logs are absent. If you want to run a real experiment, place the generated output files back under `src/main/resources/data/**` locally without committing them.

## API 一览

| 能力 | 接口 |
|---|---|
| 简历库 | `GET/POST /api/resume`、`GET/PUT/DELETE /api/resume/{id}`、`POST /api/resume/upload`、`POST /api/resume/{id}/parse`、`POST /api/resume/{id}/default` |
| 面试 | `POST /api/interview/start`（SSE，可带 `resumeId`）、`POST /api/interview/{id}/answer`（SSE）、`POST /api/interview/{id}/finish` |
| 面试记录/复盘 | `GET /api/interview?userId=`、`GET /api/interview/{sessionId}?userId=` |
| 概览 | `GET /api/dashboard?userId=` |
| 画像/记忆 | `GET /api/profile?userId=`、`POST /api/profile/init`、`GET /api/memory/search`、`GET /api/memory/recent`、`GET /api/memory/summary-md` |
| 知识图谱/问答 | `GET /api/graph?userId=`、`POST /api/knowledge/ask` |
| 复习训练 | `GET /api/review/calendar`（每天练习计划）、`GET /api/review/practice?date=`（当天题目）、`POST /api/review/submit`、`GET /api/review/related?cardId=`（关联知识点）、`GET /api/review/plan`、`GET /api/review/today` |
