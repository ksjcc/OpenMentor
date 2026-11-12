/**
 * 应用入口：外壳、路由、主题。
 *
 * 路由用 hash，不依赖任何 router 库；每个视图模块导出 { title, subtitle, render }。
 */

import { store, applyAppearance } from "./store.js";
import { api } from "./api.js";
import { icon, esc, toast } from "./ui.js";

import overview from "./views/overview.js";
import interview from "./views/interview.js";
import report from "./views/report.js";
import knowledge from "./views/knowledge.js";
import memory from "./views/memory.js";
import settings from "./views/settings.js";

const VIEWS = { overview, interview, report, knowledge, memory, settings };

const NAV = [
  { group: "工作台", items: [{ id: "overview", label: "概览" }] },
  { group: "面试", items: [{ id: "interview", label: "模拟面试" }, { id: "report", label: "面试报告" }] },
  { group: "知识与记忆", items: [{ id: "knowledge", label: "知识图谱" }, { id: "memory", label: "记忆与画像" }] },
];

const NAV_ICONS = {
  overview: "overview",
  interview: "interview",
  report: "report",
  knowledge: "knowledge",
  memory: "memory",
  settings: "settings",
};

let currentView = null;
let healthTimer = null;

/* ------------------------------- shell -------------------------------- */

function shellHtml() {
  const nav = NAV.map(
    (group) => `
      <div class="nav-label">${esc(group.group)}</div>
      ${group.items
        .map(
          (item) => `
        <button class="nav-item" data-route="#/${item.id}" type="button">
          ${icon(NAV_ICONS[item.id])}
          <span>${esc(item.label)}</span>
        </button>`,
        )
        .join("")}`,
  ).join("");

  return `
    <div class="app">
      <aside class="sidebar">
        <div class="brand">
          <div class="brand-mark">AC</div>
          <div class="brand-text">
            <div class="brand-name">AgentCareer</div>
            <div class="brand-sub">Harness Console</div>
          </div>
        </div>
        ${nav}
        <div class="sidebar-foot">
          <div class="health" id="health">
            <span class="dot" id="health-dot"></span>
            <span id="health-text">正在检测后端…</span>
          </div>
          <button class="nav-item" data-route="#/settings" type="button">
            ${icon(NAV_ICONS.settings)}
            <span>设置</span>
          </button>
        </div>
      </aside>

      <div class="main">
        <header class="topbar">
          <div class="crumbs">
            <span>控制台</span>
            ${icon("chevron", 13)}
            <strong id="crumb">概览</strong>
          </div>
          <div class="topbar-right">
            <span class="badge badge-warn" id="demo-badge" hidden>演示数据</span>
            <button class="btn btn-ghost btn-sm" id="theme-toggle" type="button"
              title="切换主题" aria-label="切换主题">${icon("moon", 15)}</button>
            <div class="user-chip">
              <span class="avatar" id="user-avatar">候</span>
              <span id="user-label">候选人</span>
            </div>
          </div>
        </header>
        <main id="view"></main>
      </div>
    </div>`;
}

/* ------------------------------- router ------------------------------- */

function parseRoute() {
  const raw = location.hash.replace(/^#\/?/, "").split("?")[0];
  const id = raw || "overview";
  return VIEWS[id] ? id : "overview";
}

async function mountRoute() {
  const id = parseRoute();
  const view = VIEWS[id];

  if (currentView?.unmount) {
    try {
      currentView.unmount();
    } catch {
      /* 视图卸载不应阻塞路由 */
    }
  }
  currentView = view;

  document.querySelectorAll(".nav-item[data-route]").forEach((node) => {
    const target = node.dataset.route.replace(/^#\//, "");
    if (target === id) node.setAttribute("aria-current", "page");
    else node.removeAttribute("aria-current");
  });

  const crumb = document.getElementById("crumb");
  if (crumb) crumb.textContent = view.title || "";

  const host = document.getElementById("view");
  host.innerHTML = `<div class="view"><div class="skeleton" style="height:26px;width:180px;margin-bottom:14px"></div><div class="skeleton" style="height:220px"></div></div>`;

  try {
    const result = await view.render();
    host.innerHTML = `<div class="view">${result.html}</div>`;
    document.title = `${view.title} · AgentCareer`;
    if (typeof result.after === "function") {
      result.after(host);
    }
  } catch (error) {
    host.innerHTML = `<div class="view"><div class="card"><div class="card-body">
      <div class="empty">
        <div class="empty-title">页面渲染失败</div>
        <div class="empty-desc">${esc(error?.message || error)}</div>
      </div></div></div></div>`;
    console.error(error);
  }
}

/* ------------------------------ health -------------------------------- */

async function refreshHealth() {
  const dot = document.getElementById("health-dot");
  const text = document.getElementById("health-text");
  const badge = document.getElementById("demo-badge");
  if (!dot || !text) return;

  if (store.settings.demo) {
    dot.className = "dot warn";
    text.textContent = "演示模式";
    badge.hidden = false;
    return;
  }

  badge.hidden = true;
  try {
    await api.ping();
    dot.className = "dot ok";
    text.textContent = "后端已连接";
  } catch {
    dot.className = "dot err";
    text.textContent = "后端未连接";
  }
}

/* ------------------------------- boot --------------------------------- */

function boot() {
  applyAppearance();
  document.getElementById("app").innerHTML = shellHtml();

  const themeButton = document.getElementById("theme-toggle");
  const syncThemeButton = () => {
    themeButton.innerHTML = icon(store.settings.theme === "dark" ? "sun" : "moon", 15);
  };
  syncThemeButton();

  themeButton.addEventListener("click", () => {
    const next = store.settings.theme === "dark" ? "light" : "dark";
    store.set({ theme: next });
    applyAppearance();
    syncThemeButton();
  });

  document.querySelector(".sidebar").addEventListener("click", (event) => {
    const route = event.target.closest("[data-route]")?.dataset.route;
    if (route) location.hash = route;
  });

  document.getElementById("user-avatar").textContent = "候";
  document.getElementById("user-label").textContent = `候选人 · #${store.settings.userId}`;

  store.subscribe(() => {
    document.getElementById("user-label").textContent = `候选人 · #${store.settings.userId}`;
    refreshHealth();
  });

  window.addEventListener("hashchange", mountRoute);
  if (!location.hash) location.hash = "#/overview";

  refreshHealth();
  healthTimer = setInterval(refreshHealth, 30000);
  window.addEventListener("beforeunload", () => clearInterval(healthTimer));

  mountRoute().catch((error) => {
    console.error(error);
    toast("应用启动失败", "err");
  });
}

boot();
