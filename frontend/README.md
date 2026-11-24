# AgentCareer Console

零构建静态前端。原生 ES Module + 手写设计系统，**没有任何 npm 依赖**。

## 运行

```bash
cd frontend
node serve.mjs          # http://localhost:5173
```

`serve.mjs` 会把 `/api/**` 转发到 `http://127.0.0.1:8080`（可用 `API_TARGET` 覆盖）。
前端只发同源相对路径请求，页面里没有任何"后端地址"或连接状态的概念。

## 和后端的关系

前端只认同源接口，部署形态由运维决定：

| 前端能力 | 接口 |
|---|---|
| 开始/回答/结束面试（SSE） | `POST /api/interview/start`、`/{id}/answer`、`/{id}/finish` |
| 概览指标 | `GET /api/dashboard?userId=` |
| 知识图谱 | `GET /api/graph?userId=` |
| 记忆检索 | `GET /api/memory/search?userId=&keyword=` |
| 画像 | `GET /api/profile?userId=` |

## 部署到生产

任选一种，两种都不需要前端配置地址：

```bash
# 方案 A：交给 Spring Boot 托管（同源）
cp -r frontend/* src/main/resources/static/app/

# 方案 B：Nginx 托管静态文件，并反代 /api（见下）
```

Nginx 参考：

```nginx
server {
  listen 80;
  root /var/www/agentcareer;
  location / { try_files $uri $uri/ /index.html; }
  location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_buffering off;          # SSE 必须关
    proxy_read_timeout 300s;
  }
}
```

## 目录

```
frontend/
├── index.html            应用外壳
├── serve.mjs             零依赖静态服务器（本地预览 + /api 反代）
├── assets/styles.css     设计系统（tokens / 组件 / 图表 / 响应式 / 深色）
└── src/
    ├── main.js           外壳、路由、主题
    ├── api.js            REST + SSE 客户端
    ├── store.js          用户偏好与运行时状态
    ├── mock.js           样本数据（仅"复习训练"的计划兜底还在用，见下方待办）
    ├── ui.js             图标、格式化、提示、图表
    └── views/
        ├── overview.js   概览
        ├── resume.js     简历库（上传 / 粘贴 / 解析 / 设默认）
        ├── interview.js  模拟面试（选简历 → SSE 流式对话）
        ├── report.js     面试复盘报告 + Markdown 导出
        ├── review.js     复习训练（间隔重复）
        ├── knowledge.js  知识图谱（力导向布局，自绘 SVG）+ 知识问答
        ├── memory.js     记忆与画像 + 片段检索
        └── settings.js   外观与面试默认值
```

## 关键链路

```
简历库上传 PDF/DOCX/TXT
  → 后端抽文本 + 模型解析成结构化画像（同步进记忆与画像）
  → 创建面试时选一份简历
  → 面试官每轮都能看到「本场面试设定」（面试类型 / 目标岗位 / 主题 / 简历 / 项目摘要）
  → 每轮回答落评分 → 结束面试生成复盘
  → 面试报告页读取 GET /api/interview/{id}（对话 / 评分 / 闪卡 / 下一步）
```

## 设计原则

- **克制**：一个强调色、两种阴影、发丝线分隔。不用渐变堆叠，不用大面积彩色。
- **信息优先**：数字用等宽数字（`tabular-nums`），表格右对齐，标题层级只靠字重和字号。
- **零依赖**：没有构建步骤，也就没有依赖升级和安全通告。
- **对用户只谈产品**：页面上不出现后端地址、连接状态、接口路径这类实现细节。
- **没数据就空状态**：接口失败或没有数据时展示空状态 + 重试，不拿样本数据冒充真实记录。

## 待办

`mock.js` 里的 `mockReview.plan` 仍是「复习训练」页在学习计划缺失时的兜底，
应改成空状态；其余视图已全部切到真实接口。
