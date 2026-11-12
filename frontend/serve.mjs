/**
 * 零依赖静态服务器。仅用于本地预览前端。
 *
 * 用法：
 *   node serve.mjs            # http://localhost:5173
 *   node serve.mjs 8081       # 指定端口
 *
 * 生产环境请把整个 frontend 目录交给 Nginx，或复制到
 * src/main/resources/static/ 由 Spring Boot 托管。
 */
import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { extname, join, normalize, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(fileURLToPath(new URL(".", import.meta.url)));
const port = Number(process.argv[2] || process.env.PORT || 5173);

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

server.listen(port, () => {
  console.log(`AgentCareer Console → http://localhost:${port}`);
});
