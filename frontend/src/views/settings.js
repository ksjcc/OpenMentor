/**
 * 设置页。所有改动立刻写入 localStorage 并生效。
 */

import { api } from "../api.js";
import { store } from "../store.js";
import { esc, icon, toast } from "../ui.js";

function connectionCard() {
  const s = store.settings;
  return `<div class="card">
    <div class="card-head">
      <div class="icon-box accent">${icon("bolt", 15)}</div>
      <div>
        <div class="card-title">连接与身份</div>
        <div class="card-desc">前端直接调后端 REST + SSE</div>
      </div>
    </div>
    <div class="card-body stack" style="gap:14px">
      <div class="field">
        <label class="field-label" for="s-base">后端地址</label>
        <input class="input" id="s-base" value="${esc(s.baseUrl)}" placeholder="http://localhost:8080" />
        <div class="field-hint">跨域访问需要后端开启 CORS（仓库里已提供 WebCorsConfig）。</div>
      </div>
      <div class="field">
        <label class="field-label" for="s-user">当前用户 ID</label>
        <input class="input" id="s-user" type="number" min="1" value="${esc(s.userId)}" />
        <div class="field-hint">后端所有接口都以 userId 作为归属主键。</div>
      </div>
      <label class="switch">
        <input type="checkbox" id="s-demo" ${s.demo ? "checked" : ""} />
        <span class="switch-track"></span>
        <span>演示模式（不请求后端，使用内置样本数据）</span>
      </label>
      <div class="row">
        <button class="btn" id="s-test">${icon("refresh", 14)}测试连接</button>
        <span class="hint" id="s-test-result"></span>
      </div>
    </div>
  </div>`;
}

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
          <p class="page-sub">连接、外观与默认参数。所有配置只保存在本机浏览器。</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-danger" id="s-reset">${icon("refresh", 15)}恢复默认</button>
        </div>
      </div>

      <div class="grid grid-2" style="margin-bottom:16px">
        ${connectionCard()}
        <div class="stack">
          ${appearanceCard()}
          <div class="card">
            <div class="card-head">
              <div class="icon-box">${icon("layers", 15)}</div>
              <div><div class="card-title">关于这套前端</div></div>
            </div>
            <div class="card-body stack" style="gap:10px">
              <div class="hint" style="line-height:1.8">
                零构建静态 SPA：原生 ES Module + 手写设计系统，没有 npm 依赖。
                可以直接丢进 Spring Boot 的 <span class="mono">src/main/resources/static/</span>，
                或者交给 Nginx 托管。
              </div>
              <dl class="kv" style="font-size:12.5px">
                <dt>面试链路</dt><dd class="mono">/api/interview/*</dd>
                <dt>概览</dt><dd class="mono">/api/dashboard</dd>
                <dt>记忆</dt><dd class="mono">/api/memory/*</dd>
                <dt>图谱</dt><dd class="mono">/api/graph</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      ${defaultsCard()}
    `;

    return {
      html,
      after(root) {
        const s = store.settings;

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

        bind("#s-base", "baseUrl");
        bind("#s-user", "userId", (value) => Number(value) || 1);
        bind("#s-demo", "demo");
        bind("#s-theme", "theme");
        bind("#s-density", "density");
        bind("#s-type", "interviewType");
        bind("#s-role", "targetRole");
        bind("#s-topic", "topic");
        bind("#s-summary", "projectSummary");

        root.querySelector("#s-test").addEventListener("click", async () => {
          const output = root.querySelector("#s-test-result");
          output.textContent = "正在请求…";
          try {
            await api.ping();
            output.textContent = "连接成功";
            output.style.color = "var(--ok)";
            toast("后端连接正常", "ok");
          } catch (error) {
            output.textContent = `连接失败：${error.message}`;
            output.style.color = "var(--danger)";
            toast("后端连接失败", "err");
          }
        });

        root.querySelector("#s-reset").addEventListener("click", () => {
          store.reset();
          toast("已恢复默认配置", "ok");
          location.reload();
        });

        // 让"测试连接"立刻反映当前输入，而不是等 change 事件
        root.querySelector("#s-base").addEventListener("input", (event) => {
          store.settings.baseUrl = event.target.value;
        });
        void s;
      },
    };
  },
};
