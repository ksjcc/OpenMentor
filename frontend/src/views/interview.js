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
import { mockInterviewScript, mockReport } from "../mock.js";
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

function setupHtml() {
  const s = store.settings;
  return `
    <div class="grid grid-1-2">
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
            <label class="field-label" for="f-topic">面试主题</label>
            <input class="input" id="f-topic" value="${esc(s.topic)}" placeholder="例如 Redis 持久化" />
            <div class="field-hint">一个具体主题比"技术面"更能逼出深度追问。</div>
          </div>
          <div class="field">
            <label class="field-label" for="f-summary">项目摘要（可选）</label>
            <textarea class="textarea" id="f-summary" placeholder="贴一段简历或项目描述，面试官会围绕它追问">${esc(
              s.projectSummary,
            )}</textarea>
          </div>
          <button class="btn btn-primary btn-block" id="start">${icon("play", 15)}开始面试</button>
          <div class="hint">开始后会立即消耗一次模型调用用于出第一题。</div>
        </div>
      </div>

      <div class="card">
        <div class="card-head"><div>
          <div class="card-title">这一场会怎么进行</div>
          <div class="card-desc">没有固定题库，也没有固定流程</div>
        </div></div>
        <div class="card-body stack" style="gap:18px">
          <div class="list" style="border:1px solid var(--line);border-radius:12px;overflow:hidden">
            ${[
              ["读取上下文", "面试官按需调用工具：查历史、查记忆、评分。不需要就不调。"],
              ["决定动作", "深挖 / 换题 / 提难度 / 结束，由模型根据你的回答判断。"],
              ["给出问题", "最终回复只包含一个问题，不会一次抛好几个。"],
            ]
              .map(
                ([title, desc], index) => `<div class="list-item">
                  <div class="icon-box accent">${index + 1}</div>
                  <div class="li-main">
                    <div class="li-title">${esc(title)}</div>
                    <div class="li-meta" style="white-space:normal">${esc(desc)}</div>
                  </div>
                </div>`,
              )
              .join("")}
          </div>
          <div class="hint">
            提示：回答得越具体，被追问的价值越高。只说"背过答案"通常会被连续追问三层。
          </div>
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
      return `<div class="msg ${isAi ? "ai" : "user"}" data-msg="${msg.id}">
        <div class="msg-avatar">${isAi ? "AI" : "你"}</div>
        <div>
          <div class="bubble">${esc(msg.text)}${
            msg.streaming ? '<span class="caret"></span>' : ""
          }</div>
          <div class="msg-meta">
            ${actionBadge}
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
          <dt>主题</dt><dd>${esc(s.topic || "—")}</dd>
          <dt>目标岗位</dt><dd>${esc(s.targetRole || "—")}</dd>
          <dt>轮次</dt><dd class="num">${s.turnIndex}</dd>
          <dt>当前动作</dt><dd>${badge(action, tone)}</dd>
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

/* ------------------------------ 演示模式 ------------------------------- */

async function runDemoScript() {
  const script = mockInterviewScript(store.session.topic || "Redis");
  store.session.sessionId = `demo-${Date.now()}`;
  store.session.turnIndex = 1;

  const aiMessage = pushMessage("ai", "", { streaming: true });
  repaintChat();

  for (const step of script) {
    await new Promise((resolve) => setTimeout(resolve, step.delay));
    if (step.event === "action") {
      store.session.action = step.data;
      aiMessage.action = step.data;
      repaintPanel();
    } else if (step.event === "chunk") {
      aiMessage.text = step.data;
      aiMessage.streaming = false;
      repaintChat();
    } else if (step.event === "score") {
      store.session.score = step.data;
      repaintPanel();
    } else if (step.event === "session") {
      store.session.sessionId = step.data;
    }
  }
  store.session.toolTrail = [
    { tool: "score_answer", detail: "总分 82；薄弱点：RDB fork 代价", ok: true },
    { tool: "mark_turn_action", detail: "NEXT", ok: true },
  ];
  repaintPanel();
}

/* ------------------------------ 真实调用 ------------------------------- */

function handleEvent(message) {
  return ({ event, data }) => {
    if (event === "session") {
      store.session.sessionId = data;
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

async function startInterview(root) {
  const topic = root.querySelector("#f-topic").value.trim();
  const targetRole = root.querySelector("#f-role").value.trim();
  const interviewType = root.querySelector("#f-type").value;
  const projectSummary = root.querySelector("#f-summary").value.trim();

  if (!topic || !targetRole) {
    toast("主题和目标岗位都要填", "warn");
    return;
  }

  store.set({ topic, targetRole, interviewType, projectSummary });
  store.resetSession({ topic, targetRole, interviewType, status: "running", startedAt: new Date().toISOString() });
  await renderRouting();

  if (store.settings.demo) {
    await runDemoScript();
    return;
  }

  const message = pushMessage("ai", "", { streaming: true });
  repaintChat();
  abortController = new AbortController();
  try {
    await api.startInterview({
      userId: store.settings.userId,
      interviewType,
      targetRole,
      topic,
      projectSummary,
      signal: abortController.signal,
      onEvent: handleEvent(message),
    });
  } catch (error) {
    message.streaming = false;
    message.text = `开局失败：${error.message}`;
    repaintChat();
    toast(error.message, "err");
    store.session.status = "error";
  } finally {
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

  if (store.settings.demo) {
    await new Promise((resolve) => setTimeout(resolve, 700));
    aiMessage.text =
      "你提到用 AOF 兜底，但没说 fsync 策略。如果这台机器盘很慢，always 会把写延迟打到多少？" +
      "在这个前提下，你会把 appendfsync 设成什么，为什么？";
    aiMessage.action = "FOLLOW_UP";
    aiMessage.streaming = false;
    store.session.action = "FOLLOW_UP";
    store.session.turnIndex += 1;
    store.session.toolTrail = [
      ...store.session.toolTrail,
      { tool: "read_history", detail: "最近 2 轮", ok: true },
      { tool: "mark_turn_action", detail: "FOLLOW_UP", ok: true },
    ];
    repaintChat();
    repaintPanel();
    sendButton.disabled = false;
    return;
  }

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
  if (store.settings.demo) {
    store.session.report = mockReport;
    store.session.status = "finished";
    location.hash = "#/report";
    return;
  }
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

async function renderRouting() {
  const host = document.getElementById("view");
  if (!host) return;
  const running = store.session.status === "running" || store.session.status === "error";
  host.innerHTML = `<div class="view">${pageHead()}${
    running ? runningHtml() : setupHtml()
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
    store.resetSession();
    renderRouting();
  });
}

export default {
  id: "interview",
  title: "模拟面试",

  async render() {
    const running = store.session.status === "running" || store.session.status === "error";
    return {
      html: `${pageHead()}${running ? runningHtml() : setupHtml()}`,
      after(root) {
        if (running) {
          bindRunning(root);
          scrollChat();
        } else {
          bindSetup(root);
        }
      },
    };
  },

  unmount() {
    abortController?.abort();
    abortController = null;
  },
};
