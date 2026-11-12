/**
 * 全局设置与当前会话状态。
 *
 * 只持久化"用户偏好"，不持久化正在进行的面试 —— 面试状态的真身在服务端
 * （RuntimeContext + Session），前端重复存一份必然不一致。
 */

const STORAGE_KEY = "agentcareer.console.v1";

const DEFAULTS = {
  // 连接
  baseUrl: "http://localhost:8080",
  userId: 1,
  demo: true,

  // 外观
  theme: "light",
  density: "comfortable",

  // 面试表单的草稿
  interviewType: "TECH",
  targetRole: "Java 后端开发",
  topic: "Redis",
  projectSummary: "",
};

function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULTS };
    return { ...DEFAULTS, ...JSON.parse(raw) };
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
