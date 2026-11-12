# AgentCareer Console

零构建静态前端。原生 ES Module + 手写设计系统，**没有任何 npm 依赖**。

## 运行

```bash
cd frontend
node serve.mjs          # http://localhost:5173
```

或者用任意静态服务器（`npx serve .`、Nginx、IIS 都行），也可以用 VS Code 的 Live Server。

## 连后端

1. 启动后端（默认 `http://localhost:8080`）
2. 打开控制台的「设置」页，把「后端地址」填成后端地址，**关掉「演示模式」**
3. 点「测试连接」，看到"连接成功"即可

> 演示模式默认开启：不请求后端，用内置样本数据渲染整站，方便先看效果。

## 和后端的关系

| 前端能力 | 后端接口 |
|---|---|
| 开始/回答/结束面试（SSE） | `POST /api/interview/start`、`/{id}/answer`、`/{id}/finish` |
| 概览指标 | `GET /api/dashboard?userId=` |
| 知识图谱 | `GET /api/graph?userId=` |
| 记忆检索 | `GET /api/memory/search?userId=&keyword=` |
| 画像 | `GET /api/profile?userId=` |

后端已提供 `WebCorsConfig`，允许 `http://localhost:*` 跨域访问 `/api/**`。

## 部署到生产

任选一种：

```bash
# 方案 A：交给 Spring Boot 托管（同源，无需 CORS）
cp -r frontend/* src/main/resources/static/app/

# 方案 B：Nginx 托管静态文件，并反代 /api
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
├── serve.mjs             零依赖静态服务器（仅本地预览）
├── assets/styles.css     设计系统（tokens / 组件 / 图表 / 响应式 / 深色）
└── src/
    ├── main.js           外壳、路由、主题
    ├── api.js            REST + SSE 客户端
    ├── store.js          设置与运行时状态
    ├── mock.js           演示数据
    ├── ui.js             图标、格式化、提示、图表
    └── views/
        ├── overview.js   概览
        ├── interview.js  模拟面试（SSE + 流式）
        ├── report.js     面试报告 + Markdown 导出
        ├── knowledge.js  知识图谱（力导向布局，自绘 SVG）
        ├── memory.js     记忆与画像 + 片段检索
        └── settings.js   连接、外观、默认值
```

## 设计原则

- **克制**：一个强调色、两种阴影、发丝线分隔。不用渐变堆叠，不用大面积彩色。
- **信息优先**：数字用等宽数字（`tabular-nums`），表格右对齐，标题层级只靠字重和字号。
- **零依赖**：没有构建步骤，也就没有依赖升级和安全通告。
- **可读的降级**：后端不可用时不是白屏，而是演示数据 + 明确的"演示数据"标记。
