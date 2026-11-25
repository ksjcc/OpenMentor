/**
 * 全局设置与当前会话状态。
 *
 * 只持久化"用户偏好"，不持久化正在进行的面试 —— 面试状态的真身在服务端
 * （RuntimeContext + Session），前端重复存一份必然不一致。
 *
 * 面向 To C 用户：这里不保存任何连接信息（后端地址 / 演示开关），
 * 所有请求都打同源接口。
 */

const STORAGE_KEY = "agentcareer.console.v1";

const DEFAULTS = {
  // 当前用户。接入登录后由会话颁发，不再由用户手填。
  userId: 1,

  // 外观
  theme: "light",
  density: "comfortable",

  // 面试表单的草稿
  interviewType: "TECH",
  targetRole: "Java 后端开发",
  topic: "JAVA八股拷打",
  projectSummary: "",
  // 是否把这次面试记入长期记忆（画像/记忆片段/复习卡/图谱）
  remember: true,
};

/** 只保留 DEFAULTS 里认识的键，历史版本残留的 baseUrl / demo 会被丢掉。 */
function sanitize(raw) {
  if (!raw || typeof raw !== "object") return { ...DEFAULTS };
  const clean = { ...DEFAULTS };
  for (const key of Object.keys(DEFAULTS)) {
    if (raw[key] !== undefined && raw[key] !== null) clean[key] = raw[key];
  }
  return clean;
}

function load() {
  try {
    return sanitize(JSON.parse(localStorage.getItem(STORAGE_KEY) || "null"));
  } catch {
    return { ...DEFAULTS };
  }
}

const listeners = new Set();

export const store = {
  settings: load(),

  /** 当前这一场面试的运行时状态（不持久化）。 */
  session: {
    sessionId: null,
    topic: "",
    targetRole: "",
    interviewType: "",
    remember: true,
    status: "idle", // idle | running | finished | error
    turnIndex: 0,
    action: null,
    score: null,
    transcript: [],
    startedAt: null,
    finalAction: null,
    report: null,
    toolTrail: [],
  },

  get(key) {
    return this.settings[key];
  },

  set(patch) {
    this.settings = { ...this.settings, ...patch };
    this.persist();
    this.emit();
  },

  reset() {
    this.settings = { ...DEFAULTS };
    this.persist();
    this.emit();
  },

  persist() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.settings));
    } catch {
      /* 隐私模式下 localStorage 可能不可写，静默降级 */
    }
  },

  subscribe(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },

  emit() {
    for (const fn of listeners) fn(this.settings);
  },

  /** 重置当前面试（新建一场时调用）。 */
  resetSession(patch = {}) {
    this.session = {
      ...this.session,
      sessionId: null,
      status: "idle",
      turnIndex: 0,
      action: null,
      score: null,
      transcript: [],
      startedAt: null,
      finalAction: null,
      report: null,
      toolTrail: [],
      ...patch,
    };
  },
};

/** 把主题/密度写到 <html>，供 CSS 变量使用。 */
export function applyAppearance() {
  const root = document.documentElement;
  root.dataset.theme = store.settings.theme;
  root.dataset.density = store.settings.density;
}
