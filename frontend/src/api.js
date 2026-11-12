/**
 * 后端接口层。
 *
 * 约定：
 * 1. 所有请求都带超时；SSE 走 fetch + ReadableStream 手动解析，因为
 *    EventSource 不支持 POST + 查询参数。
 * 2. 后端统一返回 { code, message, data } 结构（com.common.Result），
 *    这里拆包成 data；非 0 code 统一抛错。
 * 3. 演示模式（demo）下不真正发请求，由调用方改用 mock 数据。
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

function baseUrl() {
  return (store.settings.baseUrl || "").replace(/\/+$/, "");
}

function url(path, params) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params || {})) {
    if (value == null || value === "") continue;
    query.set(key, String(value));
  }
  const qs = query.toString();
  return `${baseUrl()}${path}${qs ? `?${qs}` : ""}`;
}

async function request(path, { method = "GET", params, body, raw = false, signal } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  if (signal) signal.addEventListener("abort", () => controller.abort(), { once: true });

  let response;
  try {
    response = await fetch(url(path, params), {
      method,
      headers: body != null ? { "content-type": "application/json" } : undefined,
      body: body != null ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
  } catch (error) {
    clearTimeout(timer);
    if (error.name === "AbortError") {
      throw new ApiError("请求超时，后端可能没有启动", { path });
    }
    throw new ApiError(`无法连接后端：${error.message}`, { path });
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
export async function streamSSE(path, { method = "POST", params, body, onEvent, signal } = {}) {
  const response = await request(path, { method, params, body, raw: true, signal });
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
  /** 健康探针：Dashboard 状态接口，用来点侧栏的连接指示。 */
  async ping() {
    return request("/api/dashboard", { params: { userId: store.settings.userId } });
  },

  dashboard(userId = store.settings.userId) {
    return request("/api/dashboard", { params: { userId } });
  },

  startInterview({ userId, interviewType, targetRole, topic, projectSummary, onEvent, signal }) {
    return streamSSE("/api/interview/start", {
      method: "POST",
      params: { userId, interviewType, targetRole, topic, projectSummary },
      onEvent,
      signal,
    });
  },

  answerInterview({ userId, sessionId, topic, answer, onEvent, signal }) {
    return streamSSE(`/api/interview/${sessionId}/answer`, {
      method: "POST",
      params: { userId, topic },
      body: answer,
      onEvent,
      signal,
    });
  },

  finishInterview({ userId, sessionId }) {
    return request(`/api/interview/${sessionId}/finish`, {
      method: "POST",
      params: { userId },
    });
  },

  stopInterview({ userId, sessionId }) {
    return request(`/api/interview/${sessionId}/stop`, {
      method: "POST",
      params: { userId },
    });
  },

  profile(userId = store.settings.userId) {
    return request("/api/profile", { params: { userId } });
  },

  profileStatus() {
    return request("/api/profile/status");
  },

  memorySearch({ userId, keyword }) {
    return request("/api/memory/search", { params: { userId, keyword } });
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

  ask({ userId, question }) {
    return request("/api/knowledge/ask", { method: "POST", params: { userId, question } });
  },
};
