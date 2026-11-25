/**
 * 零依赖静态服务器。仅用于本地预览前端。
 *
 * 用法：
 *   node serve.mjs            # http://localhost:5173
 *   node serve.mjs 8081       # 指定端口
 *   API_TARGET=http://127.0.0.1:8080 node serve.mjs
 *
 * 前端只发同源请求（/api/**）。本地联调时由这里把 /api 转发给后端，
 * 所以前端本身不需要、也不提供"后端地址"配置。
 * 生产环境两种部署方式都不需要前端配置地址：
 *   1. 复制到 src/main/resources/static/ 由 Spring Boot 同源托管；
 *   2. 交给 Nginx，location /api 反代到后端。
 */
import { createServer, request as httpRequest } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { extname, join, normalize, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(fileURLToPath(new URL(".", import.meta.url)));
const port = Number(process.argv[2] || process.env.PORT || 5173);
const apiTarget = new URL(process.env.API_TARGET || "http://127.0.0.1:8080");

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".ico": "image/x-icon",
  ".woff2": "font/woff2",
  ".map": "application/json; charset=utf-8",
};

/** 把 /api/** 原样转发到后端（含 SSE：直接管道透传，不缓冲）。 */
function proxy(req, res) {
  const upstream = httpRequest(
    {
      protocol: apiTarget.protocol,
      hostname: apiTarget.hostname,
      port: apiTarget.port || 80,
      path: req.url,
      method: req.method,
      headers: { ...req.headers, host: apiTarget.host },
    },
    (upstreamRes) => {
      res.writeHead(upstreamRes.statusCode || 502, upstreamRes.headers);
      upstreamRes.pipe(res);
    },
  );

  upstream.on("error", (error) => {
    if (res.headersSent) {
      res.end();
      return;
    }
    res.writeHead(502, { "content-type": "application/json; charset=utf-8" });
    res.end(
      JSON.stringify({
        code: 502,
        message: `后端未启动或不可达（${apiTarget.origin}）：${error.message}`,
        data: null,
      }),
    );
  });

  req.pipe(upstream);
}

async function resolveFile(urlPath) {
  const clean = decodeURIComponent(urlPath.split("?")[0]);
  const candidate = normalize(join(root, clean));
  if (!candidate.startsWith(root)) {
    return null; // 目录穿越防护
  }
  try {
    const info = await stat(candidate);
    if (info.isDirectory()) {
      return join(candidate, "index.html");
    }
    return candidate;
  } catch {
    // SPA 回退：未知路径一律返回 index.html
    return join(root, "index.html");
  }
}

const server = createServer(async (req, res) => {
  if ((req.url || "").startsWith("/api/")) {
    proxy(req, res);
    return;
  }

  const file = await resolveFile(req.url || "/");
  if (!file) {
    res.writeHead(403, { "content-type": "text/plain; charset=utf-8" });
    res.end("403");
    return;
  }
  try {
    const body = await readFile(file);
    res.writeHead(200, {
      "content-type": MIME[extname(file)] || "application/octet-stream",
      "cache-control": "no-cache",
    });
    res.end(body);
  } catch {
    res.writeHead(404, { "content-type": "text/plain; charset=utf-8" });
    res.end("404");
  }
});

server.on("error", (error) => {
  if (error.code === "EADDRINUSE") {
    console.error(`\n端口 ${port} 已被占用（可能上次的 node 没退干净，或另一个终端在跑）。`);
    console.error(`  换个端口： node serve.mjs 8081`);
    console.error(`  Windows 查占用： netstat -ano | findstr :${port}   然后 taskkill /PID <PID> /F`);
    console.error(`  macOS/Linux 查占用： lsof -i :${port}\n`);
    process.exit(1);
  }
  throw error;
});

server.listen(port, () => {
  console.log(`AgentCareer Console → http://localhost:${port}`);
  console.log(`/api/** → ${apiTarget.origin}`);
});
