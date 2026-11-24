/**
 * 设置页。只放用户自己的偏好：外观与面试默认值。
 * 所有改动立刻写入 localStorage 并生效。
 */

import { store } from "../store.js";
import { esc, icon, toast } from "../ui.js";

function appearanceCard() {
  const s = store.settings;
  return `<div class="card">
    <div class="card-head">
      <div class="icon-box accent">${icon("sun", 15)}</div>
      <div><div class="card-title">外观</div></div>
    </div>
    <div class="card-body stack" style="gap:14px">
      <div class="field">
        <label class="field-label" for="s-theme">主题</label>
        <select class="select" id="s-theme">
          <option value="light" ${s.theme === "light" ? "selected" : ""}>浅色</option>
          <option value="dark" ${s.theme === "dark" ? "selected" : ""}>深色</option>
        </select>
      </div>
      <div class="field">
        <label class="field-label" for="s-density">信息密度</label>
        <select class="select" id="s-density">
          <option value="comfortable" ${s.density === "comfortable" ? "selected" : ""}>舒适</option>
          <option value="compact" ${s.density === "compact" ? "selected" : ""}>紧凑</option>
        </select>
      </div>
    </div>
  </div>`;
}

function defaultsCard() {
  const s = store.settings;
  return `<div class="card">
    <div class="card-head">
      <div class="icon-box accent">${icon("interview", 15)}</div>
      <div>
        <div class="card-title">面试默认值</div>
        <div class="card-desc">新建面试时预填，不影响已有会话</div>
      </div>
    </div>
    <div class="card-body stack" style="gap:14px">
      <div class="field">
        <label class="field-label" for="s-type">默认面试类型</label>
        <select class="select" id="s-type">
          <option value="TECH" ${s.interviewType === "TECH" ? "selected" : ""}>技术面试</option>
          <option value="PROJECT" ${s.interviewType === "PROJECT" ? "selected" : ""}>项目面试</option>
          <option value="HR" ${s.interviewType === "HR" ? "selected" : ""}>HR 面试</option>
        </select>
      </div>
      <div class="field">
        <label class="field-label" for="s-role">默认目标岗位</label>
        <input class="input" id="s-role" value="${esc(s.targetRole)}" />
      </div>
      <div class="field">
        <label class="field-label" for="s-topic">默认主题</label>
        <input class="input" id="s-topic" value="${esc(s.topic)}" />
      </div>
      <div class="field">
        <label class="field-label" for="s-summary">默认项目摘要</label>
        <textarea class="textarea" id="s-summary" style="min-height:76px">${esc(s.projectSummary)}</textarea>
      </div>
    </div>
  </div>`;
}

export default {
  id: "settings",
  title: "设置",

  async render() {
    const html = `
      <div class="page-head">
        <div>
          <h1 class="page-title">设置</h1>
          <p class="page-sub">外观与面试默认参数。所有配置只保存在本机浏览器。</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-danger" id="s-reset">${icon("refresh", 15)}恢复默认</button>
        </div>
      </div>

      <div class="grid grid-2">
        ${appearanceCard()}
        ${defaultsCard()}
      </div>
    `;

    return {
      html,
      after(root) {
        const bind = (selector, key, transform = (value) => value) => {
          const node = root.querySelector(selector);
          if (!node) return;
          node.addEventListener("change", () => {
            store.set({ [key]: transform(node.type === "checkbox" ? node.checked : node.value) });
            if (key === "theme" || key === "density") {
              document.documentElement.dataset[key] = store.settings[key];
            }
          });
        };

        bind("#s-theme", "theme");
        bind("#s-density", "density");
        bind("#s-type", "interviewType");
        bind("#s-role", "targetRole");
        bind("#s-topic", "topic");
        bind("#s-summary", "projectSummary");

        root.querySelector("#s-reset").addEventListener("click", () => {
          store.reset();
          toast("已恢复默认配置", "ok");
          location.reload();
        });
      },
    };
  },
};
