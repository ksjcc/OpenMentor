/**
 * 模拟面试页。
 *
 * 交互契约（与后端一致）：
 *   POST /api/interview/start            → SSE: session / action / chunk
 *   POST /api/interview/{id}/answer      → SSE: action / chunk / score
 *   POST /api/interview/{id}/finish      → Result<String>（知识提炼 JSON）
 *
 * 前端只负责呈现，不决定"下一步该问什么" —— 那是 harness 里模型的职责。
 */

import { api } from "../api.js";
import { store } from "../store.js";
import { badge, esc, fmt, icon, toast } from "../ui.js";

const ACTION_LABEL = {
  FOLLOW_UP: "继续深挖",
  NEXT: "换一题",
  HARDER: "提升难度",
  FINISH: "结束面试",
  CLARIFY: "请求展开",
};

const ACTION_TONE = {
  FOLLOW_UP: "accent",
  NEXT: "neutral",
  HARDER: "warn",
  FINISH: "ok",
  CLARIFY: "warn",
};

let abortController = null;

/* ------------------------------ 设置表单 ------------------------------ */

function resumeOptionsHtml(resumes) {
  const options = resumes
    .map(
      (resume) =>
        `<option value="${resume.id}" ${resume.isDefault === 1 ? "selected" : ""}>${esc(
          resume.title,
        )}${resume.isDefault === 1 ? "（默认）" : ""}</option>`,
    )
    .join("");
  return `<option value="0">不关联简历（通用技术面）</option>${options}`;
}

function setupHtml(resumes = []) {
  const s = store.settings;
  const hasResume = resumes.length > 0;
  return `
    <div class="grid" style="grid-template-columns:minmax(0, 640px)">
      <div class="card">
        <div class="card-head"><div>
          <div class="card-title">面试设置</div>
          <div class="card-desc">一场面试 = 一个隔离上下文</div>
        </div></div>
        <div class="card-body stack">
          <div class="field">
            <label class="field-label" for="f-type">面试类型</label>
            <select class="select" id="f-type">
              <option value="TECH" ${s.interviewType === "TECH" ? "selected" : ""}>技术面试</option>
              <option value="PROJECT" ${s.interviewType === "PROJECT" ? "selected" : ""}>项目面试</option>
              <option value="HR" ${s.interviewType === "HR" ? "selected" : ""}>HR 面试</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" for="f-role">目标岗位</label>
            <input class="input" id="f-role" value="${esc(s.targetRole)}" placeholder="例如 Java 后端开发" />
          </div>
          <div class="field">
            <label class="field-label" for="f-topic">面试主题（可选）</label>
            <input class="input" id="f-topic" value="${esc(s.topic)}" placeholder="例如 Java 八股拷打，留空则不限定主题" />
          </div>
          <div class="field">
            <label class="field-label" for="f-resume">关联简历</label>
            ${
              hasResume
                ? `<select class="select" id="f-resume">${resumeOptionsHtml(resumes)}</select>
                   <div class="field-hint">面试官会读到这份简历，并围绕里面的项目追问。</div>`
                : `<div class="row" style="gap:8px">
                     <span class="hint">还没有简历，面试官只能按通用技术面提问。</span>
                     <button class="btn btn-sm" id="go-resume" type="button">去添加</button>
                   </div>`
            }
          </div>
          <div class="field">
            <label class="field-label" for="f-summary">补充项目摘要（可选）</label>
            <textarea class="textarea" id="f-summary" placeholder="简历之外想重点聊的项目，写在这里">${esc(
              s.projectSummary,
            )}</textarea>
          </div>
          <div class="field">
            <label class="switch">
              <input type="checkbox" id="f-remember" ${s.remember === false ? "" : "checked"} />
              <span class="switch-track"></span>
              <span>把这次面试记入长期记忆</span>
            </label>
            <div class="field-hint">关闭后本场只生成复盘，不更新画像、记忆片段和复习卡。</div>
          </div>
          <button class="btn btn-primary btn-block" id="start">${icon("play", 15)}开始面试</button>
        </div>
      </div>
    </div>`;
}

/* -------------------------------- 会话 --------------------------------- */

function messagesHtml() {
  const list = store.session.transcript;
  if (!list.length) {
    return `<div class="empty" style="min-height:280px">
      <div class="empty-title">等待第一句</div>
      <div class="empty-desc">面试官正在生成开场题…</div>
    </div>`;
  }
  return list
    .map((msg) => {
      const isAi = msg.role === "ai";
      const actionBadge =
        isAi && msg.action
          ? `<span class="badge badge-${ACTION_TONE[msg.action] || "neutral"}">${
              ACTION_LABEL[msg.action] || msg.action
            }</span>`
          : "";
      // 降级回复不是模型输出：标注出来，避免用户以为面试官"真的这么问"
      const degradedBadge =
        isAi && msg.degraded ? `<span class="badge badge-warn">降级</span>` : "";
      return `<div class="msg ${isAi ? "ai" : "user"}" data-msg="${msg.id}">
        <div class="msg-avatar">${isAi ? "AI" : "你"}</div>
        <div>
          <div class="bubble">${esc(msg.text)}${
            msg.streaming ? '<span class="caret"></span>' : ""
          }</div>
          <div class="msg-meta">
            ${actionBadge}${degradedBadge}
            <span>${esc(fmt.time(msg.at))}</span>
          </div>
        </div>
      </div>`;
    })
    .join("");
}

function sessionPanelHtml() {
  const s = store.session;
  const action = s.action ? ACTION_LABEL[s.action] || s.action : "—";
  const tone = s.action ? ACTION_TONE[s.action] : "neutral";
  const scoreText = s.score || "尚未评分";

  return `
    <div class="card">
      <div class="card-head"><div><div class="card-title">会话状态</div></div></div>
      <div class="card-body">
        <dl class="kv">
          <dt>主题</dt><dd>${esc(s.topic || "不限主题")}</dd>
          <dt>目标岗位</dt><dd>${esc(s.targetRole || "—")}</dd>
          <dt>轮次</dt><dd class="num">${s.turnIndex}</dd>
          <dt>当前动作</dt><dd>${badge(action, tone)}</dd>
          <dt>记忆沉淀</dt><dd>${
            s.remember === false ? badge("本场不记入", "neutral") : badge("已开启", "ok")
          }</dd>
          <dt>服务状态</dt><dd>${
            s.degraded ? badge("降级兜底", "warn") : badge("正常", "ok")
          }</dd>
          <dt>会话 ID</dt><dd class="mono">${esc(s.sessionId || "—")}</dd>
        </dl>
      </div>
    </div>

    <div class="card">
      <div class="card-head"><div><div class="card-title">本轮评分</div></div></div>
      <div class="card-body">
        <div class="hint" style="line-height:1.8">${esc(scoreText)}</div>
      </div>
    </div>

    <div class="card">
      <div class="card-head"><div>
        <div class="card-title">工具轨迹</div>
        <div class="card-desc">模型这一轮调用了什么</div>
      </div></div>
      <div class="card-body tight">
        ${
          s.toolTrail.length
            ? `<div class="list">${s.toolTrail
                .map(
                  (item) => `<div class="list-item">
                    <span class="dot ${item.ok ? "ok" : "err"}"></span>
                    <div class="li-main">
                      <div class="li-title mono">${esc(item.tool)}</div>
                      <div class="li-meta">${esc(item.detail)}</div>
                    </div>
                  </div>`,
                )
                .join("")}</div>`
            : `<div class="empty" style="padding:26px 16px">
                <div class="empty-title">暂无调用</div>
                <div class="empty-desc">模型可能直接给出问题，没有使用工具。</div>
              </div>`
        }
      </div>
    </div>`;
}

function runningHtml() {
  const s = store.session;
  return `
    <div class="grid grid-2-1">
      <div class="card" style="display:flex;flex-direction:column">
        <div class="card-head">
          <div>
            <div class="card-title">${esc(s.topic || "面试进行中")}</div>
            <div class="card-desc">${esc(s.targetRole)} · 第 ${s.turnIndex} 轮</div>
          </div>
          <div class="card-head-actions">
            <button class="btn btn-sm" id="reset">${icon("refresh", 13)}重开</button>
            <button class="btn btn-sm btn-danger" id="finish">${icon("stop", 13)}结束面试</button>
          </div>
        </div>
        <div class="chat" id="chat">${messagesHtml()}</div>
        <div class="composer">
          <textarea class="textarea" id="answer" placeholder="用具体细节回答，Ctrl/⌘ + Enter 发送"></textarea>
          <div class="composer-foot">
            <span>回答越具体，追问越有价值</span>
            <span class="spacer"></span>
            <button class="btn btn-primary" id="send">${icon("send", 14)}发送回答</button>
          </div>
        </div>
      </div>
      <div class="stack">${sessionPanelHtml()}</div>
    </div>`;
}

/* ------------------------------ 渲染逻辑 ------------------------------- */

function scrollChat() {
  const chat = document.getElementById("chat");
  if (chat) chat.scrollTop = chat.scrollHeight;
}

function repaintChat() {
  const chat = document.getElementById("chat");
  if (!chat) return;
  chat.innerHTML = messagesHtml();
  scrollChat();
}

function repaintPanel() {
  const panel = document.querySelector(".grid-2-1 > .stack");
  if (panel) panel.innerHTML = sessionPanelHtml();
}

let messageSeq = 0;
function pushMessage(role, text, extra = {}) {
  const msg = { id: `m${++messageSeq}`, role, text, at: new Date().toISOString(), ...extra };
  store.session.transcript.push(msg);
  return msg;
}

/* ------------------------------ 真实调用 ------------------------------- */

function handleEvent(message) {
  return ({ event, data }) => {
    if (event === "session") {
      store.session.sessionId = data;
      repaintPanel();
      return;
    }
    if (event === "duplicate") {
      // 后端幂等命中：这次没有新建，而是接回了同一场
      store.session.duplicate = true;
      toast("这场面试刚才已经创建过，已为你接回原会话", "warn");
      repaintPanel();
      return;
    }
    if (event === "degraded") {
      // 模型不可用，后端给了确定性兜底回复
      message.degraded = true;
      store.session.degraded = true;
      repaintChat();
      repaintPanel();
      return;
    }
    if (event === "action") {
      store.session.action = data;
      message.action = data;
      repaintPanel();
      return;
    }
    if (event === "chunk") {
      message.text = data;
      message.streaming = false;
      repaintChat();
      return;
    }
    if (event === "score") {
      store.session.score = data;
      repaintPanel();
      return;
    }
    if (event === "complete") {
      message.streaming = false;
      repaintChat();
    }
  };
}

/* 创建面试的幂等状态：同一次提交重发时复用同一个键，创建成功后才作废 */
let startInFlight = false;
let pendingIdempotencyKey = null;

function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `idem-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

async function startInterview(root) {
  // 双保险：DOM 还没被替换掉的极短窗口里，挡住第二次触发
  if (startInFlight) {
    return;
  }

  const topic = root.querySelector("#f-topic").value.trim();
  const targetRole = root.querySelector("#f-role").value.trim();
  const interviewType = root.querySelector("#f-type").value;
  const projectSummary = root.querySelector("#f-summary").value.trim();
  const resumeSelect = root.querySelector("#f-resume");
  // 0 = 用户显式选择「不关联简历」；null = 没有选择控件，交给后端用默认简历
  const resumeId = resumeSelect && resumeSelect.value !== "" ? Number(resumeSelect.value) : null;
  const rememberBox = root.querySelector("#f-remember");
  const remember = rememberBox ? rememberBox.checked : true;

  if (!targetRole) {
    toast("目标岗位要填", "warn");
    return;
  }

  const button = root.querySelector("#start");
  startInFlight = true;
  if (button) {
    button.disabled = true;
    button.innerHTML = "正在创建…";
  }

  // 失败重试沿用同一个键（后端据此重放到同一场），成功后才换新键
  const idempotencyKey = pendingIdempotencyKey || newIdempotencyKey();
  pendingIdempotencyKey = idempotencyKey;

  let message = null;
  try {
    store.set({ topic, targetRole, interviewType, projectSummary, remember });
    store.resetSession({
      topic,
      targetRole,
      interviewType,
      remember,
      status: "running",
      startedAt: new Date().toISOString(),
    });
    await renderRouting();

    message = pushMessage("ai", "", { streaming: true });
    repaintChat();
    abortController = new AbortController();
    await api.startInterview({
      userId: store.settings.userId,
      interviewType,
      targetRole,
      topic,
      projectSummary,
      resumeId,
      remember,
      idempotencyKey,
      signal: abortController.signal,
      onEvent: handleEvent(message),
    });

    // 只有真正创建成功才作废键：下一次点击才是全新一场
    pendingIdempotencyKey = null;
  } catch (error) {
    if (message) {
      message.streaming = false;
      message.text = `开局失败：${error.message}`;
      repaintChat();
    }
    toast(error.message, "err");
    store.session.status = "error";
    // 故意保留 pendingIdempotencyKey：用户点「重开」再试时后端会重放同一场，
    // 不会因为网络抖动多建一场面试
  } finally {
    startInFlight = false;
    abortController = null;
  }
}

async function sendAnswer(root) {
  const box = root.querySelector("#answer");
  const answer = box.value.trim();
  if (!answer) {
    toast("先写点内容", "warn");
    return;
  }
  const sendButton = root.querySelector("#send");
  sendButton.disabled = true;

  pushMessage("user", answer);
  box.value = "";
  const aiMessage = pushMessage("ai", "", { streaming: true });
  repaintChat();

  abortController = new AbortController();
  try {
    await api.answerInterview({
      userId: store.settings.userId,
      sessionId: store.session.sessionId,
      topic: store.session.topic,
      answer,
      signal: abortController.signal,
      onEvent: handleEvent(aiMessage),
    });
    store.session.turnIndex += 1;
    repaintPanel();
  } catch (error) {
    aiMessage.streaming = false;
    aiMessage.text = `发送失败：${error.message}`;
    repaintChat();
    toast(error.message, "err");
  } finally {
    abortController = null;
    sendButton.disabled = false;
  }
}

async function finishInterview() {
  try {
    const raw = await api.finishInterview({
      userId: store.settings.userId,
      sessionId: store.session.sessionId,
    });
    store.session.report = raw;
    store.session.status = "finished";
    toast("面试已结束，复盘已生成", "ok");
    location.hash = "#/report";
  } catch (error) {
    toast(`结束失败：${error.message}`, "err");
  }
}

async function loadResumes() {
  try {
    return (await api.resumeList(store.settings.userId)) || [];
  } catch {
    // 简历库拉不到不应该挡住宿主流程：退化为"不关联简历"
    return [];
  }
}

async function renderRouting() {
  const host = document.getElementById("view");
  if (!host) return;
  const running = store.session.status === "running" || store.session.status === "error";
  const resumes = running ? [] : await loadResumes();
  host.innerHTML = `<div class="view">${pageHead()}${
    running ? runningHtml() : setupHtml(resumes)
  }</div>`;
  if (running) {
    bindRunning(host);
    scrollChat();
  } else {
    bindSetup(host);
  }
}

function pageHead() {
  const s = store.session;
  const status =
    s.status === "running"
      ? badge("进行中", "accent")
      : s.status === "finished"
        ? badge("已结束", "ok")
        : badge("未开始", "neutral");
  return `<div class="page-head">
    <div>
      <h1 class="page-title">模拟面试</h1>
      <p class="page-sub">一场面试一个隔离上下文；流程由模型驱动，没有固定题库。</p>
    </div>
    <div class="page-actions">${status}</div>
  </div>`;
}

function bindSetup(root) {
  root.querySelector("#start")?.addEventListener("click", () => startInterview(root));
  root.querySelector("#go-resume")?.addEventListener("click", () => {
    location.hash = "#/resume";
  });
}

function bindRunning(root) {
  const send = () => sendAnswer(root);
  root.querySelector("#send")?.addEventListener("click", send);
  root.querySelector("#answer")?.addEventListener("keydown", (event) => {
    if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
      event.preventDefault();
      send();
    }
  });
  root.querySelector("#finish")?.addEventListener("click", finishInterview);
  root.querySelector("#reset")?.addEventListener("click", () => {
    abortController?.abort();
    // 显式「重开」= 用户想要一场新的面试，作废幂等键，避免被重放回旧会话
    pendingIdempotencyKey = null;
    startInFlight = false;
    store.resetSession();
    renderRouting();
  });
}

export default {
  id: "interview",
  title: "模拟面试",

  async render() {
    const running = store.session.status === "running" || store.session.status === "error";
    if (running) {
      return {
        html: `${pageHead()}${runningHtml()}`,
        after(root) {
          bindRunning(root);
          scrollChat();
        },
      };
    }
    const resumes = await loadResumes();
    return {
      html: `${pageHead()}${setupHtml(resumes)}`,
      after(root) {
        bindSetup(root);
      },
    };
  },

  unmount() {
    abortController?.abort();
    abortController = null;
  },
};
