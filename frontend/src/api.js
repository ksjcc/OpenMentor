/**
 * 后端接口层。
 *
 * 约定：
 * 1. 所有请求都带超时；SSE 走 fetch + ReadableStream 手动解析，因为
 *    EventSource 不支持 POST + 查询参数。
 * 2. 后端统一返回 { code, message, data } 结构（com.common.Result），
 *    这里拆包成 data；非 0 code 统一抛错。
 * 3. 全部走同源相对路径：前端与接口由同一个域名提供，不暴露、也不允许
 *    用户配置后端地址。
 */

import { store } from "./store.js";

export class ApiError extends Error {
  constructor(message, { status = 0, path = "" } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.path = path;
  }
}

const TIMEOUT_MS = 20000;

function url(path, params) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params || {})) {
    if (value == null || value === "") continue;
    query.set(key, String(value));
  }
  const qs = query.toString();
  return `${path}${qs ? `?${qs}` : ""}`;
}

/**
 * 统一请求。
 *
 * body 三态：
 * - 普通对象 / 数组 → JSON
 * - FormData     → 原样发送（不设 content-type，交给浏览器带上 boundary）
 * - 传了 contentType → 原样发送（用于 text/plain 的纯文本接口）
 */
async function request(
  path,
  { method = "GET", params, body, contentType, raw = false, signal, headers, timeoutMs = TIMEOUT_MS } = {},
) {
  const hasBody = body != null;
  const isForm = hasBody && typeof FormData !== "undefined" && body instanceof FormData;
  const sendAsText = hasBody && (isForm || Boolean(contentType));

  const controller = new AbortController();
  // SSE 场景必须传 timeoutMs: 0 —— 首字节要等模型返回，固定 20s 会把正常请求误杀
  const timer = timeoutMs > 0 ? setTimeout(() => controller.abort(), timeoutMs) : null;
  if (signal) signal.addEventListener("abort", () => controller.abort(), { once: true });

  let response;
  try {
    response = await fetch(url(path, params), {
      method,
      headers: {
        ...(hasBody && !isForm ? { "content-type": contentType || "application/json" } : {}),
        ...(headers || {}),
      },
      body: hasBody ? (sendAsText ? body : JSON.stringify(body)) : undefined,
      signal: controller.signal,
    });
  } catch (error) {
    clearTimeout(timer);
    if (error.name === "AbortError") {
      throw new ApiError("请求超时，请稍后重试", { path });
    }
    throw new ApiError("网络异常，请稍后重试", { path });
  } finally {
    clearTimeout(timer);
  }

  if (raw) return response;

  const text = await response.text();
  if (!response.ok) {
    throw new ApiError(`${response.status} ${response.statusText} · ${truncate(text)}`, {
      status: response.status,
      path,
    });
  }
  if (!text) return null;

  let payload;
  try {
    payload = JSON.parse(text);
  } catch {
    return text;
  }
  // 兼容 com.common.Result
  if (payload && typeof payload === "object" && "code" in payload) {
    if (payload.code !== 0 && payload.code !== 200) {
      throw new ApiError(payload.message || `业务失败（code=${payload.code}）`, { path });
    }
    return payload.data ?? null;
  }
  return payload;
}

function truncate(text, max = 180) {
  const clean = String(text ?? "").replace(/\s+/g, " ").trim();
  return clean.length > max ? `${clean.slice(0, max)}…` : clean;
}

/* ------------------------------- SSE ---------------------------------- */

/**
 * 解析 SSE 流。Spring 的 SseEmitter 输出形如：
 *   event:action\ndata:NEXT\n\n
 * 支持多行 data（用 \n 拼接）。
 */
export async function streamSSE(path, { method = "POST", params, body, onEvent, signal, headers } = {}) {
  // timeoutMs: 0 —— 流式响应的首字节要等模型返回，超时交给服务端控制
  const response = await request(path, { method, params, body, raw: true, signal, headers, timeoutMs: 0 });
  if (!response.ok || !response.body) {
    const detail = await response.text().catch(() => "");
    throw new ApiError(`${response.status} ${response.statusText} · ${truncate(detail)}`, {
      status: response.status,
      path,
    });
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let boundary;
    while ((boundary = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      const event = parseBlock(block);
      if (event) onEvent?.(event);
    }
  }
  if (buffer.trim()) {
    const event = parseBlock(buffer);
    if (event) onEvent?.(event);
  }
}

function parseBlock(block) {
  let name = "message";
  const data = [];
  for (const line of block.split(/\r?\n/)) {
    if (!line || line.startsWith(":")) continue;
    const index = line.indexOf(":");
    const field = index === -1 ? line : line.slice(0, index);
    let value = index === -1 ? "" : line.slice(index + 1);
    if (value.startsWith(" ")) value = value.slice(1);
    if (field === "event") name = value;
    else if (field === "data") data.push(value);
  }
  if (!data.length) return null;
  return { event: name, data: data.join("\n") };
}

/* ------------------------------ endpoints ----------------------------- */

export const api = {
  dashboard(userId = store.settings.userId) {
    return request("/api/dashboard", { params: { userId } });
  },

  /* ------------------------------ 简历库 ------------------------------ */

  resumeList(userId = store.settings.userId) {
    return request("/api/resume", { params: { userId } });
  },

  resumeGet(resumeId, userId = store.settings.userId) {
    return request(`/api/resume/${resumeId}`, { params: { userId } });
  },

  resumeCreate({ userId, title, content, targetRole, parse = false }) {
    return request("/api/resume", {
      method: "POST",
      body: { userId, title, content, targetRole, parse },
    });
  },

  resumeUpdate({ resumeId, userId, title, content, targetRole }) {
    return request(`/api/resume/${resumeId}`, {
      method: "PUT",
      body: { userId, title, content, targetRole },
    });
  },

  /** 上传 pdf/docx/txt，由后端抽取文本并（可选）解析成结构化画像。 */
  resumeUpload({ userId, file, title, targetRole, parse = true }) {
    const form = new FormData();
    form.append("file", file);
    if (title) form.append("title", title);
    if (targetRole) form.append("targetRole", targetRole);
    form.append("parse", String(parse));
    return request("/api/resume/upload", {
      method: "POST",
      params: { userId },
      body: form,
      timeoutMs: 120000, // 上传 + 模型解析可能超过 20s
    });
  },

  resumeParse({ resumeId, userId }) {
    return request(`/api/resume/${resumeId}/parse`, {
      method: "POST",
      params: { userId },
      timeoutMs: 120000,
    });
  },

  resumeSetDefault({ resumeId, userId }) {
    return request(`/api/resume/${resumeId}/default`, {
      method: "POST",
      params: { userId },
    });
  },

  resumeDelete({ resumeId, userId }) {
    return request(`/api/resume/${resumeId}`, { method: "DELETE", params: { userId } });
  },

  /* ------------------------------ 面试 ------------------------------ */

  startInterview({
    userId,
    interviewType,
    targetRole,
    topic,
    projectSummary,
    resumeId,
    idempotencyKey,
    onEvent,
    signal,
  }) {
    return streamSSE("/api/interview/start", {
      method: "POST",
      params: { userId, interviewType, targetRole, topic, projectSummary, resumeId },
      // 同一次提交重发时这个键不变，后端据此重放到同一场面试
      headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
      onEvent,
      signal,
    });
  },

  answerInterview({ userId, sessionId, topic, answer, onEvent, signal }) {
    return streamSSE(`/api/interview/${sessionId}/answer`, {
      method: "POST",
      params: { userId, topic },
      body: answer,
      contentType: "text/plain;charset=utf-8",
      onEvent,
      signal,
    });
  },

  finishInterview({ userId, sessionId }) {
    return request(`/api/interview/${sessionId}/finish`, {
      method: "POST",
      params: { userId },
      timeoutMs: 120000, // 结束面试要跑一次知识提炼
    });
  },

  stopInterview({ userId, sessionId }) {
    return request(`/api/interview/${sessionId}/stop`, {
      method: "POST",
      params: { userId },
    });
  },

  interviewList(userId = store.settings.userId, limit = 20) {
    return request("/api/interview", { params: { userId, limit } });
  },

  interviewReport(sessionId, userId = store.settings.userId) {
    return request(`/api/interview/${sessionId}`, { params: { userId } });
  },

  /* --------------------------- 画像 / 记忆 --------------------------- */

  profile(userId = store.settings.userId) {
    return request("/api/profile", { params: { userId } });
  },

  profileStatus() {
    return request("/api/profile/status");
  },

  /** 初始化 / 重新解析用户画像。 */
  profileInit({ userId, targetRole, resumeText }) {
    return request("/api/profile/init", {
      method: "POST",
      body: { userId, targetRole, resumeText },
      timeoutMs: 120000,
    });
  },

  memorySearch({ userId, keyword }) {
    return request("/api/memory/search", { params: { userId, keyword } });
  },

  memoryRecent(userId = store.settings.userId, limit = 5) {
    return request("/api/memory/recent", { params: { userId, limit } });
  },

  memoryProfileJson(userId = store.settings.userId) {
    return request("/api/memory/profile-json", { params: { userId } });
  },

  memorySummary(userId = store.settings.userId, summaryType = "overall") {
    return request("/api/memory/summary-md", { params: { userId, summaryType } });
  },

  graph(userId = store.settings.userId) {
    return request("/api/graph", { params: { userId } });
  },

  graphStatus() {
    return request("/api/graph/status");
  },

  reviewPlan(userId = store.settings.userId) {
    return request("/api/review/plan", { params: { userId } });
  },

  reviewToday(userId = store.settings.userId) {
    return request("/api/review/today", { params: { userId } });
  },

  /** 提交一张闪卡的复习结果，遗忘曲线据此调整下次到期时间。 */
  submitReview({ cardId, correct }) {
    return request("/api/review/submit", {
      method: "POST",
      params: { cardId, correct },
    });
  },

  /** 知识问答（非面试语气）。 */
  ask({ userId, question }) {
    return request("/api/knowledge/ask", {
      method: "POST",
      params: { userId },
      body: question,
      contentType: "text/plain;charset=utf-8",
      timeoutMs: 120000,
    });
  },
};
